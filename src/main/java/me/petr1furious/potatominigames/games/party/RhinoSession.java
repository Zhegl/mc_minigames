package me.petr1furious.potatominigames.games.party;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Ravager;
import org.bukkit.entity.SplashPotion;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import me.petr1furious.potatominigames.core.game.SessionServices;

final class RhinoSession extends PartySurvivalSession {
    private enum Charge { IDLE, WINDUP, DASH }
    private static final class Rhino {
        final Ravager entity;
        final Set<UUID> hit = new HashSet<>();
        Charge phase = Charge.IDLE;
        long until;
        UUID target;
        Vector direction = new Vector(1, 0, 0);
        double travelled;
        Rhino(Ravager entity, long until) { this.entity = entity; this.until = until; }
    }
    private static final class Mine {
        final TimedMine state;
        Location location;
        Item ground;
        SplashPotion thrown;
        Mine(TimedMine state, Location location) { this.state = state; this.location = location; }
    }
    private RhinoArena arena;
    private int radius = RhinoArena.RADIUS;
    private final List<Rhino> rhinos = new ArrayList<>();
    private final Map<Long, Mine> mines = new LinkedHashMap<>();
    private final Map<UUID, Long> groundIds = new LinkedHashMap<>();
    private final NamespacedKey mineKey;
    private long nextMine;
    private long nextId;
    private final int duration, initialRhinos, fuseSeconds, mineSpawnSeconds;

    RhinoSession(SessionServices services, PartyConfig config) {
        super(services, config, "rhino", "Носорожий переполох");
        mineKey = new NamespacedKey(services.plugin(), "party_rhino_mine");
        duration = Math.clamp(config.rhinoSeconds.get(), 30, 600);
        initialRhinos = Math.clamp(config.rhinoCount.get(), 1, 6);
        fuseSeconds = Math.clamp(config.rhinoMineFuseSeconds.get(), 2, 15);
        mineSpawnSeconds = Math.clamp(config.rhinoMineSpawnSeconds.get(), 1, 10);
    }
    @Override void buildArena() {
        arena = new RhinoArena(world);
        arena.resize(radius, 0);
        world.setSpawnLocation(0, RhinoArena.FLOOR + 1, 0);
    }
    @Override Location spawn(int index, int count) { return arena.spawn(index, count); }
    @Override Location viewpoint() { return new Location(world, .5, RhinoArena.FLOOR + 15, .5, 0, 70); }
    @Override double outHeight() { return RhinoArena.FLOOR - 4; }
    @Override int durationSeconds() { return duration; }
    @Override String instructions() { return "Носорожий переполох: разоритель светится перед рывком — уклоняйся! Арена сужается, дыр становится больше. Подбери мину и брось ПКМ. Она взорвётся через " + fuseSeconds + " секунд от появления, даже в руках. Урон — только отбрасывание; падение означает вылет."; }
    @Override void beginPlay() {
        for (int i = 0; i < initialRhinos; i++) addRhino(i);
        nextMine = 20;
    }
    private void addRhino(int index) {
        double angle = index * 2.399963229728653;
        Location location = arena.spread(arena.safe(Math.cos(angle) * (radius - 4), Math.sin(angle) * (radius - 4)),
                otherRhinoLocations(null));
        Ravager entity = world.spawn(location, Ravager.class);
        entity.setAI(false); entity.setAware(false); entity.setGravity(false); entity.setInvulnerable(true);
        entity.setCollidable(false); entity.setSilent(true); entity.setPersistent(true); entity.setRemoveWhenFarAway(false);
        entity.customName(text("Носорог"));
        rhinos.add(new Rhino(entity, elapsed() + 20L + index * 20L));
    }
    private List<Location> otherRhinoLocations(Rhino excluded) {
        List<Location> locations = new ArrayList<>();
        for (Rhino other : rhinos) {
            if (other != excluded && other.entity.isValid()) locations.add(other.entity.getLocation());
        }
        return locations;
    }
    private Player chooseTarget(Rhino rhino, List<Player> players) {
        List<UUID> assigned = new ArrayList<>();
        for (Rhino other : rhinos) {
            if (other != rhino && other.entity.isValid() && other.target != null) assigned.add(other.target);
        }
        int index = RhinoHazards.targetIndex(players.stream().map(Player::getUniqueId).toList(), assigned, random);
        Player target = players.get(index);
        rhino.target = target.getUniqueId();
        return target;
    }
    @Override void playTick(long elapsed) {
        int stage = RhinoHazards.stage(elapsed);
        int nextRadius = RhinoHazards.radius(elapsed);
        arena.resize(nextRadius, stage);
        if (radius != nextRadius) {
            radius = nextRadius;
            say("Арена сужается! Радиус: " + radius);
        }
        int desired = Math.min(6, initialRhinos + stage / 2);
        while (rhinos.size() < desired) addRhino(rhinos.size());
        List<Player> players = alivePlayers();
        for (Rhino rhino : rhinos) tickRhino(rhino, players, elapsed, stage);
        if (elapsed >= nextMine) {
            for (int i = 0; i < RhinoHazards.mineBatch(stage) && mines.size() < 30; i++) spawnMine();
            nextMine = elapsed + RhinoHazards.mineInterval(mineSpawnSeconds, stage);
        }
        for (Mine mine : List.copyOf(mines.values())) tickMine(mine);
    }
    private void tickRhino(Rhino rhino, List<Player> players, long elapsed, int stage) {
        if (!rhino.entity.isValid() || players.isEmpty()) return;
        Location from = rhino.entity.getLocation();
        double distanceFromCenter = from.getX() * from.getX() + from.getZ() * from.getZ();
        if (rhino.phase == Charge.IDLE) {
            Location resting = arena.spread(from, otherRhinoLocations(rhino));
            if (resting.distanceSquared(from) > 1e-6) rhino.entity.teleport(resting);
            from = resting;
        } else if (rhino.phase != Charge.DASH && distanceFromCenter > (radius - 2) * (radius - 2)) {
            from = arena.safe(from.getX() * .5, from.getZ() * .5);
            rhino.entity.teleport(from);
        }
        if (rhino.phase == Charge.IDLE && elapsed >= rhino.until) {
            Player target = chooseTarget(rhino, players);
            Vector direction = target.getLocation().toVector().subtract(from.toVector()).setY(0);
            if (direction.lengthSquared() < 1e-6) direction = new Vector(1, 0, 0);
            rhino.direction = direction.normalize();
            rhino.phase = Charge.WINDUP;
            rhino.until = elapsed + RhinoHazards.windup(stage);
            rhino.hit.clear(); rhino.travelled = 0;
            rhino.entity.setGlowing(true);
            rhino.entity.setRotation((float) Math.toDegrees(Math.atan2(-rhino.direction.getX(), rhino.direction.getZ())), 0);
            world.playSound(from, Sound.ENTITY_RAVAGER_ROAR, .7f, 1.3f);
        }
        if (rhino.phase == Charge.WINDUP && elapsed >= rhino.until) {
            rhino.phase = Charge.DASH;
            rhino.entity.setGlowing(false);
        }
        if (rhino.phase != Charge.DASH) return;
        double speed = RhinoHazards.speed(stage);
        Location to = from.clone().add(rhino.direction.clone().multiply(speed));
        for (Player player : players) {
            Location at = player.getLocation();
            if (rhino.hit.contains(player.getUniqueId()) || at.getY() < RhinoArena.FLOOR || at.getY() > RhinoArena.FLOOR + 4) continue;
            if (RhinoHazards.segmentDistanceSquared(at.getX(), at.getZ(), from.getX(), from.getZ(), to.getX(), to.getZ()) <= 1.7 * 1.7) {
                rhino.hit.add(player.getUniqueId());
                player.setVelocity(rhino.direction.clone().multiply(1.4 + Math.min(.5, stage * .08)).setY(.55));
                player.playSound(at, Sound.ENTITY_RAVAGER_ATTACK, .8f, 1);
            }
        }
        rhino.entity.teleport(to);
        rhino.travelled += speed;
        if (elapsed % 3 == 0) world.spawnParticle(Particle.CLOUD, from, 5, .5, .1, .5, .02);
        if (to.getX() * to.getX() + to.getZ() * to.getZ() > (radius - 1) * (radius - 1) || rhino.travelled > radius * 2) {
            rhino.phase = Charge.IDLE;
            rhino.until = elapsed + RhinoHazards.cooldown(stage);
        }
    }
    private ItemStack item(Mine mine) {
        ItemStack item = new ItemStack(Material.SPLASH_POTION);
        item.editMeta(PotionMeta.class, meta -> {
            meta.setColor(Color.ORANGE);
            meta.displayName(Component.text("Мина • ПКМ — бросить", NamedTextColor.RED));
            meta.lore(List.of(Component.text("Таймер идёт от появления, подбор не продлевает его!", NamedTextColor.GRAY)));
            meta.getPersistentDataContainer().set(mineKey, PersistentDataType.LONG, mine.state.id);
        });
        return item;
    }
    private Long id(ItemStack item) {
        return item == null || !item.hasItemMeta() ? null : item.getItemMeta().getPersistentDataContainer().get(mineKey, PersistentDataType.LONG);
    }
    private void spawnMine() {
        Location location = arena.safe(random.nextInt(-radius + 2, radius - 1), random.nextInt(-radius + 2, radius - 1)).add(0, .15, 0);
        Mine mine = new Mine(new TimedMine(++nextId, tick, fuseSeconds * 20), location);
        mines.put(mine.state.id, mine);
        ground(mine);
    }
    private void ground(Mine mine) {
        mine.ground = world.dropItem(mine.location, item(mine));
        mine.ground.setVelocity(new Vector()); mine.ground.setGravity(false); mine.ground.setPickupDelay(0);
        mine.ground.setCanMobPickup(false); mine.ground.setInvulnerable(true); mine.ground.setGlowing(true);
        mine.ground.setCustomNameVisible(true);
        groundIds.put(mine.ground.getUniqueId(), mine.state.id);
    }
    private void clearGround(Mine mine) {
        if (mine.ground == null) return;
        groundIds.remove(mine.ground.getUniqueId());
        mine.ground.remove(); mine.ground = null;
    }
    private void clearCarried(Mine mine) {
        if (mine.state.carrier() == null) return;
        Player player = Bukkit.getPlayer(mine.state.carrier());
        if (player == null) return;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            Long id = id(inventory.getItem(slot));
            if (id != null && id == mine.state.id) inventory.setItem(slot, null);
        }
    }
    private void tickMine(Mine mine) {
        if (mine.state.state() == TimedMine.State.CARRIED) {
            Player carrier = Bukkit.getPlayer(mine.state.carrier());
            if (carrier != null && alive(carrier) && carrier.getWorld().equals(world)) {
                mine.location = carrier.getLocation().add(0, .5, 0);
                if (tick % 5 == 0) carrier.sendActionBar(Component.text("Мина: "
                        + String.format(java.util.Locale.ROOT, "%.1f", Math.max(0, mine.state.expiresAt - tick) / 20.0) + " с • ПКМ — бросить!", NamedTextColor.RED));
            }
        } else if (mine.thrown != null && mine.thrown.isValid()) mine.location = mine.thrown.getLocation();
        else if (mine.ground != null && mine.ground.isValid()) mine.location = mine.ground.getLocation();
        if (mine.ground != null && tick % 5 == 0) mine.ground.customName(Component.text("Мина: "
                + String.format(java.util.Locale.ROOT, "%.1f", Math.max(0, mine.state.expiresAt - tick) / 20.0) + " с", NamedTextColor.RED));
        if (!mine.state.expire(tick)) return;
        clearGround(mine); clearCarried(mine);
        if (mine.thrown != null) mine.thrown.remove();
        mines.remove(mine.state.id);
        world.spawnParticle(Particle.EXPLOSION, mine.location, 1);
        world.playSound(mine.location, Sound.ENTITY_GENERIC_EXPLODE, .8f, 1.2f);
        for (Player player : alivePlayers()) {
            Vector push = player.getLocation().add(0, .5, 0).toVector().subtract(mine.location.toVector());
            if (push.lengthSquared() > 25) continue;
            double strength = .4 + 1.6 * (1 - push.length() / 5);
            push.setY(0);
            if (push.lengthSquared() < 1e-6) push.setX(1);
            player.setVelocity(push.normalize().multiply(strength).setY(.65));
        }
    }
    @Override public void onPickupItem(EntityPickupItemEvent event) {
        event.setCancelled(true);
        Long id = groundIds.get(event.getItem().getUniqueId());
        if (!playing() || id == null || !(event.getEntity() instanceof Player player) || !alive(player)) return;
        if (mines.values().stream().anyMatch(m -> m.state.state() == TimedMine.State.CARRIED && player.getUniqueId().equals(m.state.carrier()))) return;
        Mine mine = mines.get(id);
        if (mine == null || !mine.state.pickup(player.getUniqueId(), tick)) return;
        clearGround(mine);
        player.getInventory().setItem(0, item(mine));
        player.getInventory().setHeldItemSlot(0);
    }
    @Override public void onPlayerInteract(PlayerInteractEvent event) {
        event.setCancelled(true);
        if (!playing() || !alive(event.getPlayer()) || event.getHand() != EquipmentSlot.HAND || !event.getAction().isRightClick()) return;
        Long id = id(event.getItem());
        Mine mine = id == null ? null : mines.get(id);
        if (mine == null) return;
        clearCarriedBeforeThrow(mine, event.getPlayer());
    }
    private void clearCarriedBeforeThrow(Mine mine, Player player) {
        if (mine.state.state() != TimedMine.State.CARRIED || !player.getUniqueId().equals(mine.state.carrier())) return;
        clearCarried(mine);
        if (!mine.state.throwBy(player.getUniqueId(), tick)) return;
        mine.location = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(.5));
        mine.thrown = world.spawn(mine.location, SplashPotion.class);
        mine.thrown.setItem(item(mine)); mine.thrown.setShooter(player);
        mine.thrown.getPersistentDataContainer().set(mineKey, PersistentDataType.LONG, mine.state.id);
        mine.thrown.setVelocity(player.getEyeLocation().getDirection().multiply(1.05).add(new Vector(0, .12, 0)));
        player.playSound(player.getLocation(), Sound.ENTITY_SPLASH_POTION_THROW, .7f, 1);
    }
    @Override public void onPotionSplash(PotionSplashEvent event) {
        if (!event.getEntity().getWorld().equals(world)) return;
        Long id = event.getEntity().getPersistentDataContainer().get(mineKey, PersistentDataType.LONG);
        if (id == null) return;
        event.setCancelled(true);
        Mine mine = mines.get(id);
        if (mine == null || mine.thrown != event.getEntity()) return;
        mine.location = event.getEntity().getLocation();
        event.getEntity().remove();
        mine.thrown = null;
        if (mine.state.land(tick)) ground(mine);
    }
    @Override void playerOut(Player player) {
        for (Mine mine : mines.values()) {
            if (mine.state.state() != TimedMine.State.CARRIED || !player.getUniqueId().equals(mine.state.carrier())) continue;
            clearCarried(mine);
            mine.location = player.getLocation().clone();
            mine.state.drop();
            ground(mine);
        }
    }
    @Override String status() { return "Радиус " + radius + " • носороги " + rhinos.size() + " • мины " + mines.size(); }
    @Override void cleanup() {
        for (Mine mine : mines.values()) { clearGround(mine); clearCarried(mine); if (mine.thrown != null) mine.thrown.remove(); }
        mines.clear(); groundIds.clear();
        rhinos.forEach(r -> r.entity.remove()); rhinos.clear();
    }
}
