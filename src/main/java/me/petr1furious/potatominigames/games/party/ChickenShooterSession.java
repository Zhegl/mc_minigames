package me.petr1furious.potatominigames.games.party;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;

import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.core.text.TimeFormat;
import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class ChickenShooterSession extends PartySurvivalSession {
    private static final String KEY = "party.drop_and_shoot.chicken.";
    private static final int FLOOR = 80;
    private static final int WIDTH = 8;
    private static final int BACKSTOP = 22;
    private final Map<UUID, Lane> lanes = new LinkedHashMap<>();
    private final Map<UUID, Target> targets = new HashMap<>();
    private final Map<UUID, Shot> shots = new HashMap<>();
    private ChickenGallery gallery;
    private int wave = -1;
    private long waveStartedAt;

    ChickenShooterSession(SessionServices services, PartyConfig config, PartyRoundContext context) {
        super(services, config, "chicken_shooter", context);
    }

    @Override
    void buildArena() {
        gallery = new ChickenGallery(names.keySet());
        int index = 0;
        for (UUID id : names.keySet()) {
            Lane lane = lane(index++);
            lanes.put(id, lane);
            buildLane(lane);
        }
    }

    private void buildLane(Lane lane) {
        for (int x = -WIDTH; x <= WIDTH; x++) {
            for (int z = -4; z <= BACKSTOP; z++) {
                Material floor = z <= 2 ? Material.SPRUCE_PLANKS : Material.GREEN_TERRACOTTA;
                if (z == 2) floor = Material.STRIPPED_BIRCH_WOOD;
                if (z > 2 && Math.abs(x) == 4 && z % 3 == 0) floor = Material.MOSS_BLOCK;
                if (x == 0 && z == 0) floor = Material.SEA_LANTERN;
                world.getBlockAt(lane.x() + x, FLOOR, lane.z() + z).setType(floor, false);
                if (Math.abs(x) == WIDTH || z == BACKSTOP || z == -4) {
                    int height = z == -4 ? 2 : 7;
                    for (int y = 1; y <= height; y++) {
                        Material wall =
                                y == 1 || y == height || z % 5 == 0
                                        ? Material.DARK_OAK_PLANKS
                                        : Material.GREEN_TERRACOTTA;
                        world.getBlockAt(lane.x() + x, FLOOR + y, lane.z() + z)
                                .setType(wall, false);
                    }
                }
            }
        }
    }

    private record Lane(int x, int z) {
        boolean onDeck(Location at) {
            return Math.abs(at.getX() - x - .5) < WIDTH - 1
                    && at.getZ() > z - 3
                    && at.getZ() < z + 2.7;
        }
    }

    private record Target(UUID owner, Lane lane, ChickenGallery.Bird path, Chicken chicken) {}

    private record Shot(UUID owner, AbstractArrow arrow, long expires) {}

    private static Lane lane(int index) {
        return new Lane(index % 3 * 20, index / 3 * 34);
    }

    @Override
    Location spawn(int index, int count) {
        UUID id = services.participants().onlinePlayers().get(index).getUniqueId();
        Lane lane = lanes.get(id);
        return new Location(world, lane.x() + .5, FLOOR + 1, lane.z() + .5, 0, 1);
    }

    @Override
    Location viewpoint() {
        return new Location(world, .5, FLOOR + 13, -1.5).setDirection(new Vector(0, -9, 12));
    }

    @Override
    double outHeight() {
        return FLOOR - 4;
    }

    @Override
    long arenaTime() {
        return 6000;
    }

    @Override
    int durationSeconds() {
        return ChickenGallery.DURATION_TICKS / 20;
    }

    @Override
    boolean survivalAutoFinish() {
        return false;
    }

    @Override
    boolean podiumAwards() {
        return true;
    }

    @Override
    void preparePlayer(Player player) {
        ItemStack bow = new ItemStack(Material.BOW);
        bow.addEnchantment(Enchantment.INFINITY, 1);
        bow.editMeta(meta -> {
            meta.displayName(I18n.tr(KEY + "bow").color(NamedTextColor.GOLD));
            meta.setUnbreakable(true);
        });
        player.getInventory().setItem(0, I18n.item(bow, player));
        player.getInventory().setItem(9, new ItemStack(Material.ARROW));
        player.getInventory().setHeldItemSlot(0);
    }

    @Override
    Component instructions() {
        return I18n.tr(KEY + "instructions");
    }

    private boolean shooting(Player player) {
        return playing()
                && !context.ended()
                && alive(player)
                && player.getGameMode() == GameMode.SURVIVAL
                && player.getWorld().equals(world);
    }

    @Override
    void playTick(long elapsed) {
        if (elapsed >= ChickenGallery.DURATION_TICKS) {
            for (Player player : alivePlayers())
                player.sendMessage(
                        text(I18n.tr(KEY + "final_score", gallery.score(player.getUniqueId()))));
            return;
        }
        int currentWave = ChickenGallery.wave(elapsed);
        if (currentWave != wave) restock(currentWave, elapsed);
        else if (wave > 0) {
            long age = elapsed - waveStartedAt;
            for (Target target : targets.values())
                target.chicken().teleport(birdAt(target.lane(), target.path(), age));
        }
        shots.values()
                .removeIf(shot -> {
                    if (shot.arrow().isValid() && elapsed < shot.expires()) return false;
                    shot.arrow().remove();
                    return true;
                });
    }

    private void restock(int nextWave, long elapsed) {
        clearTargets();
        wave = nextWave;
        waveStartedAt = elapsed;
        var pattern = ChickenGallery.WAVES.get(wave);
        for (Player player : alivePlayers()) {
            if (!shooting(player)) continue;
            UUID owner = player.getUniqueId();
            Lane lane = lanes.get(owner);
            for (ChickenGallery.Bird bird : pattern.birds()) {
                Chicken chicken =
                        world.spawn(
                                birdAt(lane, bird, 0),
                                Chicken.class,
                                entity -> {
                                    entity.setAdult();
                                    entity.setAgeLock(true);
                                    entity.setAI(false);
                                    entity.setGravity(false);
                                    // Paper's projectile raycast needs a pickable collision box.
                                    entity.setCollidable(true);
                                    entity.setInvulnerable(true);
                                    entity.setSilent(true);
                                    entity.setPersistent(false);
                                    entity.setRemoveWhenFarAway(false);
                                    entity.setEggLayTime(Integer.MAX_VALUE);
                                    var scale = entity.getAttribute(Attribute.SCALE);
                                    if (scale != null) scale.setBaseValue(pattern.scale());
                                });
                targets.put(chicken.getUniqueId(), new Target(owner, lane, bird, chicken));
                gallery.addBird(chicken.getUniqueId(), owner);
            }
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, .5f, 1.2f);
        }
    }

    private Location birdAt(Lane lane, ChickenGallery.Bird bird, long age) {
        double x = bird.x(age);
        double z = bird.z(age);
        float yaw =
                wave == 0
                        ? 180
                        : (float)
                                Math.toDegrees(
                                        Math.atan2(x - bird.x(age + 1), bird.z(age + 1) - z));
        return new Location(
                world, lane.x() + .5 + x, FLOOR + 1.85 + bird.y(age), lane.z() + .5 + z, yaw, 0);
    }

    @Override
    protected void handleMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (shooting(player) && !lanes.get(player.getUniqueId()).onDeck(event.getTo()))
            event.setCancelled(true);
    }

    @Override
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!event.getPlayer().getWorld().equals(world)) return;
        if (!shooting(event.getPlayer())
                || event.getHand() != EquipmentSlot.HAND
                || !event.getAction().isRightClick()
                || event.getMaterial() != Material.BOW) {
            event.setCancelled(true);
            return;
        }
        event.setUseInteractedBlock(Event.Result.DENY);
    }

    @Override
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (event.isCancelled() || !event.getEntity().getWorld().equals(world)) return;
        if (!(event.getEntity() instanceof Player player)
                || !shooting(player)
                || event.getBow() == null
                || event.getBow().getType() != Material.BOW
                || !(event.getProjectile() instanceof AbstractArrow arrow)) {
            event.setCancelled(true);
            return;
        }
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        shots.put(arrow.getUniqueId(), new Shot(player.getUniqueId(), arrow, elapsed() + 60));
    }

    @Override
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity().getWorld().equals(world)
                && !shots.containsKey(event.getEntity().getUniqueId())) event.setCancelled(true);
    }

    @Override
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!event.getEntity().getWorld().equals(world)) return;
        Shot shot = shots.remove(event.getEntity().getUniqueId());
        var shooter = event.getEntity().getShooter();
        event.setCancelled(true);
        event.getEntity().remove();
        Entity hit = event.getHitEntity();
        if (shot == null
                || hit == null
                || elapsed() >= shot.expires()
                || !(shooter instanceof Player player)
                || !shooting(player)
                || !shot.owner().equals(player.getUniqueId())
                || !gallery.hit(shot.owner(), hit.getUniqueId())) return;
        targets.remove(hit.getUniqueId());
        Location at = hit.getLocation();
        hit.remove();
        world.spawnParticle(Particle.POOF, at.add(0, .5, 0), 8, .25, .2, .25, .02);
        player.playSound(player.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, .7f, 1.6f);
        player.sendActionBar(I18n.tr(KEY + "hit", gallery.score(shot.owner())));
    }

    @Override
    public void onPrePlayerAttackEntity(PrePlayerAttackEntityEvent event) {
        if (event.getAttacked().getWorld().equals(world)) event.setCancelled(true);
    }

    @Override
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getRightClicked().getWorld().equals(world)) event.setCancelled(true);
    }

    @Override
    public void onEntityMount(EntityMountEvent event) {
        if (event.getMount().getWorld().equals(world)) event.setCancelled(true);
    }

    @Override
    Component status() {
        return I18n.tr(KEY + "status", wave + 1, ChickenGallery.WAVES.size(), timeLeft());
    }

    private String timeLeft() {
        return TimeFormat.clock(Math.max(0, durationSeconds() - elapsed() / 20));
    }

    @Override
    List<Component> sidebarLines(Player player, Component line) {
        UUID id = player.getUniqueId();
        if (!context.eligible().contains(id)) return List.of(line);
        Component score = I18n.tr(KEY + "score", gallery.score(id));
        Component time = I18n.tr(KEY + "time", timeLeft());
        if (wave < 0) return List.of(score, time);
        long ticksLeft = ChickenGallery.WAVES.get(wave).ticks() - (elapsed() - waveStartedAt);
        return List.of(score, time, I18n.tr(KEY + "wave_left", Math.max(0, (ticksLeft + 19) / 20)));
    }

    @Override
    public PartyRoundResult roundResult() {
        return gallery == null ? PartyRoundResult.empty() : gallery.result(context.eligible());
    }

    @Override
    void playerOut(Player player) {
        UUID id = player.getUniqueId();
        targets.values()
                .removeIf(target -> {
                    if (!target.owner().equals(id)) return false;
                    removeTarget(target);
                    return true;
                });
        shots.values()
                .removeIf(shot -> {
                    if (!shot.owner().equals(id)) return false;
                    shot.arrow().remove();
                    return true;
                });
    }

    private void removeTarget(Target target) {
        gallery.removeBird(target.chicken().getUniqueId());
        target.chicken().remove();
    }

    private void clearTargets() {
        targets.values().forEach(this::removeTarget);
        targets.clear();
    }

    @Override
    void cleanup() {
        clearTargets();
        shots.values().forEach(shot -> shot.arrow().remove());
        shots.clear();
        lanes.clear();
    }
}
