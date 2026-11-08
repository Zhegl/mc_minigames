package me.petr1furious.potatominigames.games.pillars;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LargeFireball;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import io.papermc.paper.event.player.PlayerStopUsingItemEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import me.petr1furious.potatominigames.core.game.SessionServices;

/** One match owns every effect, entity and listener. No static player state survives a session. */
final class PillarsPerks implements Listener, AutoCloseable {
    private record Gift(String id, List<ItemStack> expected) { }
    private record Frozen(Location at, long until) { }
    private record Ghost(long until, GameMode mode, boolean allowFlight, boolean flying, float flySpeed, Location fallback) { }
    private record Tracked(Entity entity, UUID owner, long until) { }
    private record SheepBomb(Sheep sheep, TNTPrimed tnt, UUID owner) { }
    private record Smoke(Location at, UUID owner, long until) { }
    private record BlockTarget(PerkTerrain.Pos position, String data) { }
    private record Channel(String serial, PerkRules.Channel<BlockTarget> timer) { }
    private record Anchor(long until, Vector velocity) { }
    private record Gravity(boolean original) { }
    private record AnvilDrop(UUID owner, Location at, long when) { }
    private record Decoy(Mannequin entity, UUID owner, PerkRules.DecoyTrigger trigger, long until) { }
    private static final Sound[] PHANTOM_SOUNDS = {
            Sound.BLOCK_STONE_STEP, Sound.BLOCK_GRASS_STEP, Sound.ENTITY_PLAYER_SMALL_FALL,
            Sound.ENTITY_PLAYER_ATTACK_SWEEP, Sound.ENTITY_PLAYER_ATTACK_STRONG,
            Sound.ENTITY_ARROW_SHOOT, Sound.ENTITY_TNT_PRIMED
    };
    private final PillarsSession session;
    private final SessionServices services;
    private final World world;
    private final double dropChance;
    private final Supplier<List<ItemStack>> ordinaryDraw;
    private final PerkTerrain terrain;
    private final PerkIllusions illusions;
    private final Map<UUID, Gift> gifts = new HashMap<>();
    private final Map<UUID, PerkRules.History<Location>> history = new HashMap<>();
    private final Map<UUID, Frozen> frozen = new HashMap<>();
    private final Map<UUID, Ghost> ghosts = new HashMap<>();
    private final Map<UUID, Channel> channels = new HashMap<>();
    private final Map<UUID, Anchor> anchors = new HashMap<>();
    private final Map<UUID, Long> usedAt = new HashMap<>();
    private final Map<UUID, Long> jumpAt = new HashMap<>();
    private final Map<UUID, Gravity> gravity = new HashMap<>();
    private final Map<UUID, PerkRules.GravityCycle> gravityCycles = new HashMap<>();
    private final Map<UUID, PerkRules.PhantomNoises> phantomNoises = new HashMap<>();
    private final Map<UUID, Decoy> decoys = new HashMap<>();
    private final Set<UUID> jumping = new HashSet<>();
    private final List<Tracked> entities = new ArrayList<>();
    private final Set<UUID> pumpkins = new HashSet<>();
    private final List<SheepBomb> sheep = new ArrayList<>();
    private final List<Smoke> smoke = new ArrayList<>();
    private final List<AnvilDrop> pendingAnvils = new ArrayList<>();
    private final BukkitTask ticker;
    private final String match = UUID.randomUUID().toString();
    private long tick;
    private boolean closed, internalTeleport;
    private UUID decoyBlastOwner;

    PillarsPerks(PillarsSession session, SessionServices services, World world, double chance, Supplier<List<ItemStack>> draw) {
        this.session = session; this.services = services; this.world = world; dropChance = chance; ordinaryDraw = draw;
        terrain = new PerkTerrain(world);
        illusions = new PerkIllusions(world);
        Bukkit.getPluginManager().registerEvents(this, services.plugin());
        ticker = Bukkit.getScheduler().runTaskTimer(services.plugin(), this::tickSafely, 1, 1);
    }
    private void tickSafely() {
        try { tick(); }
        catch (RuntimeException error) {
            services.plugin().getLogger().log(java.util.logging.Level.SEVERE, "Pillars perks failed", error);
            services.session().endGame(me.petr1furious.potatominigames.core.game.EndReason.ABORTED);
        }
    }
    private boolean scoped(Player p) { return !closed && p.getWorld().equals(world) && session.isParticipant(p); }
    private boolean alive(Player p) { return scoped(p) && session.hasStarted() && session.isInGame(p) && !p.isDead() && p.isOnline(); }
    private boolean active(Player p) { return alive(p) && p.getGameMode() == GameMode.SURVIVAL && !blocked(p); }
    private boolean blocked(Player p) { return frozen.containsKey(p.getUniqueId()) || ghosts.containsKey(p.getUniqueId()); }
    boolean immuneToVoid(Player p) { return !closed && blocked(p); }
    boolean isGhost(Player p) { return ghosts.containsKey(p.getUniqueId()); }
    private static void message(Player p, String line) { p.sendActionBar(Component.text(line, NamedTextColor.LIGHT_PURPLE)); }
    private boolean fits(Location at) {
        if (!at.getWorld().equals(world) || at.getY() < session.deathHeight() || at.getY() >= world.getMaxHeight() - 2
                || !world.getWorldBorder().isInside(at)) return false;
        // Check the whole player body, not just the centre of its feet/head blocks.
        for (int x = (int) Math.floor(at.getX() - .3); x <= Math.floor(at.getX() + .3); x++)
            for (int y = at.getBlockY(); y <= Math.floor(at.getY() + 1.79); y++)
                for (int z = (int) Math.floor(at.getZ() - .3); z <= Math.floor(at.getZ() + .3); z++)
                    if (!world.getBlockAt(x, y, z).isPassable()) return false;
        return true;
    }
    private void teleport(Player p, Location at) {
        internalTeleport = true;
        try { p.teleport(at); } finally { internalTeleport = false; }
        p.setVelocity(new Vector()); p.setFallDistance(0);
    }
    List<ItemStack> roll(List<ItemStack> ordinary) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack stack : ordinary) {
            if (ThreadLocalRandom.current().nextDouble() < dropChance) result.addAll(PerkItems.bundle(PerkType.roll(ThreadLocalRandom.current())));
            else result.add(stack.clone());
        }
        return result;
    }
    void grant(Player p, List<ItemStack> stacks) { grant(p, stacks, false); }
    private void grant(Player p, List<ItemStack> stacks, boolean rerolled) {
        // One bundle (including the legendary bow's ammunition) is one indivisible reroll target.
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack template = stacks.get(i), item = template.clone();
            PerkType kind = PerkItems.type(item);
            List<ItemStack> bundle = new ArrayList<>();
            if (kind != null) PerkItems.tag(item, PerkItems.SERIAL, UUID.randomUUID().toString());
            bundle.add(item);
            if (kind == PerkType.BOW && i + 1 < stacks.size() && stacks.get(i + 1).getType() == Material.ARROW) bundle.add(stacks.get(++i).clone());
            String id = UUID.randomUUID().toString();
            for (ItemStack part : bundle) PerkItems.tag(part, PerkItems.GIFT, id);
            if (kind != PerkType.REROLL || rerolled) {
                releaseGift(p, gifts.get(p.getUniqueId()));
                gifts.put(p.getUniqueId(), new Gift(id, bundle.stream().map(ItemStack::clone).toList()));
            }
            for (ItemStack part : bundle) {
                for (ItemStack leftover : p.getInventory().addItem(part.clone()).values()) world.dropItem(p.getLocation(), leftover);
            }
            if (kind != null) p.sendMessage(Component.text("Перк: " + kind.title + " • " + String.join(" • ", kind.instructions),
                    kind.legendary ? NamedTextColor.GOLD : NamedTextColor.LIGHT_PURPLE));
        }
    }
    private void releaseGift(Player p, Gift previous) {
        if (previous == null) return;
        // Old ordinary rewards become normal stackable loot again; only the latest draw needs provenance.
        var inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (!previous.id().equals(PerkItems.string(item, PerkItems.GIFT))) continue;
            ItemStack clean = item.clone(); clean.editMeta(meta -> meta.getPersistentDataContainer().remove(PerkItems.GIFT));
            if (i < 36 && PerkItems.type(clean) == null) for (int j = 0; j < 36 && clean.getAmount() > 0; j++) {
                if (j == i) continue;
                ItemStack other = inv.getItem(j);
                if (other == null || !other.isSimilar(clean)) continue;
                int amount = Math.min(clean.getAmount(), Math.max(0, clean.getMaxStackSize() - other.getAmount()));
                if (amount > 0) { other = other.clone(); other.setAmount(other.getAmount() + amount); inv.setItem(j, other); clean.setAmount(clean.getAmount() - amount); }
            }
            inv.setItem(i, clean.getAmount() > 0 ? clean : null);
        }
        ItemStack cursor = p.getItemOnCursor();
        if (previous.id().equals(PerkItems.string(cursor, PerkItems.GIFT))) {
            ItemStack clean = cursor.clone(); clean.editMeta(meta -> meta.getPersistentDataContainer().remove(PerkItems.GIFT)); p.setItemOnCursor(clean);
        }
    }
    boolean give(Player p, PerkType type) { if (!active(p)) return false; grant(p, PerkItems.bundle(type)); return true; }
    private static boolean same(ItemStack a, String serial) { return serial != null && serial.equals(PerkItems.string(a, PerkItems.SERIAL)); }
    private void consume(Player p, EquipmentSlot slot, String serial) {
        ItemStack held = p.getInventory().getItem(slot);
        if (!same(held, serial)) return;
        int remaining = PerkItems.charges(held) - 1;
        if (remaining <= 0) p.getInventory().setItem(slot, null);
        else { ItemStack copy = held.clone(); PerkItems.charges(copy, remaining); p.getInventory().setItem(slot, copy); }
    }
    private void consumeHand(Player p, String serial) { consume(p, EquipmentSlot.HAND, serial); }
    private boolean reroll(Player p, String serial) {
        Gift gift = gifts.get(p.getUniqueId());
        if (gift == null) { message(p, "Пока нечего перекручивать"); return false; }
        if (gift.id().equals(PerkItems.string(p.getInventory().getItemInMainHand(), PerkItems.GIFT))) {
            message(p, "Нельзя перекрутить кубик, которым пользуешься"); return false;
        }
        ItemStack[] contents = p.getInventory().getContents();
        int[] wanted = gift.expected().stream().mapToInt(ItemStack::getAmount).toArray();
        int[] present = new int[wanted.length];
        for (ItemStack item : contents) {
            if (!gift.id().equals(PerkItems.string(item, PerkItems.GIFT))) continue;
            boolean matched = false;
            for (int i = 0; i < present.length; i++) if (item.isSimilar(gift.expected().get(i))) { present[i] += item.getAmount(); matched = true; break; }
            if (!matched) { message(p, "Выдача уже изменена или использована"); return false; }
        }
        if (!PerkRules.complete(wanted, present)) { message(p, "Нужна вся последняя выдача: непотраченная и в инвентаре"); return false; }
        List<ItemStack> next = roll(ordinaryDraw.get()); // Prepare before removing anything.
        consumeHand(p, serial);
        for (int i = 0; i < contents.length; i++) if (gift.id().equals(PerkItems.string(contents[i], PerkItems.GIFT))) p.getInventory().setItem(i, null);
        grant(p, next, true);
        return true;
    }
    private void tick() {
        if (closed) return;
        tick++;
        terrain.tick(tick);
        illusions.tick();
        decoys.values().removeIf(d -> {
            if (d.entity().isValid() && tick < d.until()) return false;
            d.entity().remove(); return true;
        });
        for (UUID id : List.copyOf(gravity.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !active(p) || !gravityCycles.get(id).active(tick)) { endGravity(id, p); continue; }
            Vector velocity = p.getVelocity();
            p.setVelocity(velocity.setY(Math.min(.65, velocity.getY() + .08))); p.setFallDistance(0);
        }
        gravityCycles.entrySet().removeIf(e -> e.getValue().remaining(tick) == 0 && !gravity.containsKey(e.getKey()));
        for (UUID id : List.copyOf(frozen.keySet())) if (tick >= frozen.get(id).until()) frozen.remove(id);
        for (UUID id : List.copyOf(ghosts.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (tick >= ghosts.get(id).until() || p == null || !alive(p)) endGhost(id);
        }
        anchors.values().removeIf(a -> tick > a.until());
        if (!session.hasStarted()) return;
        for (var it = phantomNoises.entrySet().iterator(); it.hasNext();) {
            var entry = it.next(); Player victim = Bukkit.getPlayer(entry.getKey());
            if (victim == null || !alive(victim) || entry.getValue().expired(tick)) { it.remove(); continue; }
            if (!active(victim) || !entry.getValue().due(tick, ThreadLocalRandom.current())) continue;
            var random = ThreadLocalRandom.current();
            double angle = random.nextDouble(Math.PI * 2), distance = random.nextDouble(2, 5);
            Location at = victim.getLocation().clone().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
            // Send one player's sound packet; there is no world sound or damaging entity.
            victim.playSound(at, PHANTOM_SOUNDS[random.nextInt(PHANTOM_SOUNDS.length)], .8f, random.nextFloat(.9f, 1.1f));
        }
        for (var it = pendingAnvils.iterator(); it.hasNext();) {
            AnvilDrop drop = it.next();
            if (tick < drop.when()) continue;
            it.remove();
            Player owner = Bukkit.getPlayer(drop.owner());
            if (owner == null || !alive(owner)) continue;
            FallingBlock anvil = world.spawnFallingBlock(drop.at(), Material.ANVIL.createBlockData());
            anvil.setDropItem(false); anvil.setHurtEntities(true); anvil.setDamagePerBlock(2); anvil.setMaxDamage(40);
            track(anvil, owner, 200);
        }
        for (Player p : session.perkPlayers()) {
            if (!alive(p)) continue;
            history.computeIfAbsent(p.getUniqueId(), id -> new PerkRules.History<>()).add(tick, p.getLocation().clone());
            if (frozen.containsKey(p.getUniqueId())) { p.setFallDistance(0); p.setVelocity(new Vector()); continue; }
            if (ghosts.containsKey(p.getUniqueId())) {
                Ghost ghost = ghosts.get(p.getUniqueId());
                if (p.getGameMode() != GameMode.SPECTATOR) { endGhost(p.getUniqueId()); continue; }
                if (p.getSpectatorTarget() != null) p.setSpectatorTarget(null);
                if (!ghostBounds(ghost, p.getLocation()))
                    teleport(p, ghost.fallback());
                continue;
            }
            if (active(p)) { boots(p); channel(p); }
        }
        channels.keySet().removeIf(id -> { Player p = Bukkit.getPlayer(id); return p == null || !active(p); });
        for (var it = entities.iterator(); it.hasNext();) {
            Tracked e = it.next();
            if (!e.entity().isValid() || tick >= e.until()) {
                pumpkins.remove(e.entity().getUniqueId());
                if (e.entity().isValid()) e.entity().remove(); it.remove();
            }
        }
        for (var it = sheep.iterator(); it.hasNext();) {
            SheepBomb bomb = it.next();
            if (!bomb.sheep().isValid() || !bomb.tnt().isValid()) { bomb.sheep().remove(); bomb.tnt().remove(); it.remove(); continue; }
            if (tick % 4 == 0) chase(bomb);
        }
        smoke.removeIf(s -> tick >= s.until());
        if (tick % 5 == 0) for (Smoke cloud : smoke) {
            world.spawnParticle(Particle.LARGE_SMOKE, cloud.at().clone().add(0, 1, 0), 35, 2, 1, 2, .01);
            for (Player p : session.perkPlayers()) if (active(p) && !p.getUniqueId().equals(cloud.owner())
                    && p.getLocation().distanceSquared(cloud.at()) <= 16)
                p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 10, 0, false, false));
        }
    }
    private void boots(Player p) {
        ItemStack item = p.getInventory().getBoots();
        if (PerkItems.type(item) != PerkType.PLANKS || PerkItems.charges(item) <= 0) return;
        if (PerkItems.number(item, PerkItems.STARTED, 0) == 0) {
            item = item.clone();
            PerkItems.setNumber(item, PerkItems.STARTED, 1);
            PerkItems.tag(item, PerkItems.EXPIRES, match + ":" + (tick + PerkRules.PLANK_TICKS));
            PerkItems.charges(item, 1); p.getInventory().setBoots(item);
        }
        String until = PerkItems.string(item, PerkItems.EXPIRES);
        long deadline = -1;
        if (until != null && until.startsWith(match + ":")) {
            try { deadline = Long.parseLong(until.substring(match.length() + 1)); } catch (NumberFormatException ignored) { }
        }
        if (tick >= deadline) { item = item.clone(); PerkItems.charges(item, 0); p.getInventory().setBoots(item); return; }
        Location at = p.getLocation();
        if (at.getBlockY() - 1 < world.getMinHeight() || at.getBlockY() - 1 >= world.getMaxHeight()) return;
        Block below = world.getBlockAt(at.getBlockX(), at.getBlockY() - 1, at.getBlockZ());
        if (below.getType().isAir() && world.getWorldBorder().isInside(below.getLocation())) {
            below.setType(Material.OAK_PLANKS, false);
            Vector velocity = p.getVelocity(); if (velocity.getY() < -.5) { velocity.setY(-.5); p.setVelocity(velocity); }
            p.setFallDistance(0);
        }
    }
    private void channel(Player p) {
        Channel c = channels.get(p.getUniqueId());
        if (c == null) return;
        ItemStack hand = p.getInventory().getItemInMainHand();
        boolean held = same(hand, c.serial()) && p.hasActiveItem() && same(p.getActiveItem(), c.serial());
        RayTraceResult ray = held ? p.rayTraceBlocks(12, FluidCollisionMode.NEVER) : null;
        Block target = ray == null ? null : ray.getHitBlock();
        BlockTarget position = target == null || terrain.locked(target, tick) ? null : new BlockTarget(PerkTerrain.Pos.of(target), target.getBlockData().getAsString());
        if (c.timer().tick(position, tick, held)) {
            // Break only a block in this match's world, with no inventory/tile drops.
            target.setType(Material.AIR, false); terrain.changed(target);
            channels.remove(p.getUniqueId()); p.clearActiveItem(); consumeHand(p, c.serial());
            world.playSound(target.getLocation(), Sound.BLOCK_GLASS_BREAK, 1, .6f);
        } else if (held && tick % 10 == 0) message(p, "Дезинтегратор: " + String.format(java.util.Locale.ROOT, "%.1f", c.timer().elapsed(tick) / 20.0) + "/6 с");
        if (!held) channels.remove(p.getUniqueId());
    }
    private void ghost(Player p) {
        ghosts.put(p.getUniqueId(), new Ghost(tick + PerkRules.GHOST_TICKS, p.getGameMode(), p.getAllowFlight(), p.isFlying(), p.getFlySpeed(), p.getLocation().clone()));
        p.closeInventory(); p.setGameMode(GameMode.SPECTATOR); p.setFlySpeed(.1f); p.setVelocity(new Vector());
    }
    private boolean ghostBounds(Ghost ghost, Location at) {
        Location origin = ghost.fallback();
        return at.getWorld().equals(world) && world.getWorldBorder().isInside(at)
                && Math.abs(at.getX() - origin.getX()) <= 32 && Math.abs(at.getZ() - origin.getZ()) <= 32
                && at.getY() >= session.deathHeight() && at.getY() < Math.min(world.getMaxHeight() - 2, origin.getY() + 32);
    }
    private void endGhost(UUID id) { endGhost(id, Bukkit.getPlayer(id)); }
    private void endGhost(UUID id, Player p) {
        Ghost state = ghosts.remove(id);
        if (state == null || p == null) return;
        if (p.getGameMode() == GameMode.SPECTATOR) p.setSpectatorTarget(null);
        Location destination = p.getLocation();
        if (!fits(destination) || !ghostBounds(state, destination)) {
            Location found = null;
            for (int r = 1; r <= 4 && found == null; r++)
                for (int x = -r; x <= r && found == null; x++) for (int y = -r; y <= r && found == null; y++)
                    for (int z = -r; z <= r; z++) {
                        Location at = destination.clone().add(x, y, z);
                        if (fits(at) && ghostBounds(state, at)) { found = at; break; }
                    }
            if (found == null) {
                for (int y = 0; y <= 32; y++) {
                    Location at = state.fallback().clone().add(0, y, 0);
                    if (fits(at)) { found = at; break; }
                }
            }
            destination = found == null ? state.fallback() : found;
        }
        p.setGameMode(state.mode()); p.setAllowFlight(state.allowFlight());
        p.setFlying(state.allowFlight() && state.flying()); p.setFlySpeed(state.flySpeed());
        teleport(p, destination);
    }
    private void freeze(Player p) {
        p.closeInventory(); p.clearActiveItem();
        Location at = p.getLocation().clone(); at.setX(at.getBlockX() + .5); at.setZ(at.getBlockZ() + .5);
        teleport(p, at);
        Frozen state = new Frozen(at, tick + PerkRules.FREEZE_TICKS);
        frozen.put(p.getUniqueId(), state); channels.remove(p.getUniqueId());
        p.setVelocity(new Vector()); p.setFallDistance(0);
        terrain.cage(state.at(), state.until());
        p.sendMessage(Component.text("Заморозка • 4 с • урон отключён", NamedTextColor.AQUA));
    }
    private Player rayTarget(Player p, double distance) {
        RayTraceResult ray = world.rayTrace(p.getEyeLocation(), p.getEyeLocation().getDirection(), distance,
                FluidCollisionMode.NEVER, true, .2, e -> e instanceof Player other && !other.equals(p) && active(other));
        return ray != null && ray.getHitEntity() instanceof Player target ? target : null;
    }
    private void beam(Player p) {
        Vector direction = p.getEyeLocation().getDirection();
        RayTraceResult hit = world.rayTrace(p.getEyeLocation(), direction, 32, FluidCollisionMode.NEVER, true, .2,
                e -> e instanceof Player other && !other.equals(p) && active(other));
        double length = hit == null ? 32 : hit.getHitPosition().distance(p.getEyeLocation().toVector());
        for (double d = 0; d <= length; d += .5) world.spawnParticle(Particle.DUST,
                p.getEyeLocation().add(direction.clone().multiply(d)), 1, new Particle.DustOptions(Color.AQUA, .9f));
        if (hit != null && hit.getHitEntity() instanceof Player target) freeze(target);
    }
    private boolean receive(Player p, ItemStack item) {
        int space = 0;
        for (ItemStack slot : p.getInventory().getStorageContents()) {
            if (slot == null || slot.getType().isAir()) space += item.getMaxStackSize();
            else if (slot.isSimilar(item)) space += Math.max(0, item.getMaxStackSize() - slot.getAmount());
        }
        return space >= item.getAmount();
    }
    private void steal(Player thief, Player victim, FishHook hook) {
        if (!active(thief) || !active(victim) || thief.equals(victim)) return;
        ItemStack rod = thief.getInventory().getItemInMainHand();
        if (PerkItems.type(rod) != PerkType.STEAL) return;
        ItemStack taken = victim.getInventory().getItemInMainHand().clone();
        if (taken.getType().isAir()) { message(thief, "В руке соперника пусто — заряд сохранён"); return; }
        // Consuming the rod frees a slot; otherwise reserve space before deleting the victim's item.
        if (!receive(thief, taken) && PerkItems.charges(rod) > 1) { message(thief, "Освободи место в инвентаре"); return; }
        String serial = PerkItems.string(rod, PerkItems.SERIAL);
        victim.getInventory().setItemInMainHand(null); consumeHand(thief, serial);
        for (ItemStack leftover : thief.getInventory().addItem(taken).values()) world.dropItem(thief.getLocation(), leftover);
        hook.remove(); message(thief, "Предмет украден!"); message(victim, "У тебя украли предмет из руки!");
    }
    private void track(Entity entity, Player p, long lifetime) {
        entity.setPersistent(false); entities.add(new Tracked(entity, p.getUniqueId(), tick + lifetime));
    }
    private boolean spawnRoom(Player p, int amount) {
        return spawnRoom(p, amount, 8);
    }
    private boolean spawnRoom(Player p, int amount, int ownerLimit) {
        long own = entities.stream().filter(e -> e.entity().isValid() && e.owner().equals(p.getUniqueId())).count();
        own += pendingAnvils.stream().filter(a -> a.owner().equals(p.getUniqueId())).count();
        if (own + amount > ownerLimit || entities.size() + pendingAnvils.size() + amount > 256) { message(p, "Дождись завершения предыдущих снарядов"); return false; }
        return true;
    }
    private boolean anvilRain(Player p) {
        RayTraceResult hit = p.rayTraceBlocks(PerkRules.ANVIL_RANGE, FluidCollisionMode.NEVER);
        if (hit == null || hit.getHitBlock() == null) { message(p, "Наведи палку на блок в пределах 192 м"); return false; }
        Block center = hit.getHitBlock();
        int y = Math.min(world.getMaxHeight() - 2, center.getY() + 24);
        if (y < center.getY() + 8) { message(p, "Над целью слишком мало места для наковален"); return false; }
        var plan = PerkShapes.anvilRain(center.getX(), y, center.getZ(), ThreadLocalRandom.current());
        for (var cell : plan) {
            Location at = new Location(world, cell.x() + .5, cell.y(), cell.z() + .5);
            if (!world.getWorldBorder().isInside(at) || !at.getBlock().getType().isAir()) {
                message(p, "Над квадратом 3×3 должно быть свободно"); return false;
            }
        }
        if (!spawnRoom(p, 9, 16)) return false;
        for (int i = 0; i < plan.size(); i++) {
            var cell = plan.get(i);
            pendingAnvils.add(new AnvilDrop(p.getUniqueId(), new Location(world, cell.x() + .5, cell.y(), cell.z() + .5), tick + 20 + i * 3L));
        }
        world.playSound(center.getLocation(), Sound.BLOCK_ANVIL_PLACE, 1, .7f);
        return true;
    }
    private boolean transmute(Player p) {
        RayTraceResult hit = p.rayTraceBlocks(PerkRules.BUILD_RANGE, FluidCollisionMode.NEVER);
        if (hit == null || hit.getHitBlock() == null) { message(p, "Алхимик действует только по блоку в пределах 6 м"); return false; }
        int count = terrain.transmute(hit.getHitBlock(), tick, ThreadLocalRandom.current());
        if (count <= 0) {
            message(p, count < 0 ? "Постройка слишком большая или выходит за загруженную арену — заряд сохранён" : "Этот блок защищён");
            return false;
        }
        world.playSound(hit.getHitBlock().getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1, .6f);
        message(p, "Алхимик: превращено блоков — " + count);
        return true;
    }
    private void blessing(Player p) {
        for (PotionEffect effect : List.of(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, PerkRules.BLESSING_TICKS, 0),
                new PotionEffect(PotionEffectType.RESISTANCE, PerkRules.BLESSING_TICKS, 0),
                new PotionEffect(PotionEffectType.STRENGTH, PerkRules.BLESSING_TICKS, 1),
                new PotionEffect(PotionEffectType.REGENERATION, PerkRules.BLESSING_TICKS, 1),
                new PotionEffect(PotionEffectType.ABSORPTION, PerkRules.BLESSING_TICKS, 3))) p.addPotionEffect(effect);
        world.playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, .7f, 1.3f);
    }
    private boolean reverseGravity(Player p) {
        var cycle = gravityCycles.computeIfAbsent(p.getUniqueId(), id -> new PerkRules.GravityCycle());
        if (!cycle.start(tick)) { message(p, "Перезарядка гравитации: " + ((cycle.remaining(tick) + 19) / 20) + " с"); return false; }
        gravity.put(p.getUniqueId(), new Gravity(p.hasGravity()));
        p.setGravity(false); p.setFallDistance(0);
        p.setVelocity(p.getVelocity().setY(.25));
        return true;
    }
    private boolean pills(Player p) {
        Player victim = PerkRules.randomOpponent(session.perkPlayers().stream().filter(this::active).toList(), p, ThreadLocalRandom.current());
        if (victim == null) { message(p, "Нет живого соперника — таблетка сохранена"); return false; }
        // Reapplication replaces the schedule instead of stacking several sound loops.
        phantomNoises.put(victim.getUniqueId(), new PerkRules.PhantomNoises(tick, ThreadLocalRandom.current()));
        message(p, "Таблетка подействовала на " + victim.getName());
        return true;
    }
    private boolean decoy(Player p, PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) { message(p, "Поставь двойника яйцом: ПКМ по блоку рядом"); return false; }
        Location at = e.getClickedBlock().getRelative(e.getBlockFace()).getLocation().add(.5, 0, .5);
        if (at.distanceSquared(p.getEyeLocation()) > PerkRules.BUILD_RANGE * PerkRules.BUILD_RANGE || !fits(at)) {
            message(p, "Для двойника нужно свободное место рядом"); return false;
        }
        if (decoys.values().stream().filter(d -> d.owner().equals(p.getUniqueId())).count() >= 2 || !spawnRoom(p, 1)) {
            message(p, "У тебя уже стоят двойники — дождись их исчезновения"); return false;
        }
        at.setYaw(p.getYaw()); at.setPitch(0);
        Mannequin body = world.spawn(at, Mannequin.class, m -> PerkDecoys.disguise(m, p));
        if (!body.isValid()) return false;
        decoys.put(body.getUniqueId(), new Decoy(body, p.getUniqueId(), new PerkRules.DecoyTrigger(p.getUniqueId()), tick + PerkRules.DECOY_TICKS));
        track(body, p, PerkRules.DECOY_TICKS);
        world.playSound(at, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, .7f, 1);
        return true;
    }
    private void hitDecoy(EntityDamageEvent e, Decoy decoy) {
        boolean permitted = !e.isCancelled();
        e.setCancelled(true); // Includes owner hits, fire, explosions and other environmental damage.
        if (!permitted || !(e instanceof EntityDamageByEntityEvent hit)) return;
        Player source = attacker(hit.getDamager());
        boolean combat = e.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || e.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK
                || e.getCause() == EntityDamageEvent.DamageCause.PROJECTILE;
        if (source == null || !active(source) || !decoy.trigger().hit(source.getUniqueId(), combat)) return;
        Location at = decoy.entity().getLocation().clone();
        decoys.remove(decoy.entity().getUniqueId()); decoy.entity().remove();
        Player owner = Bukkit.getPlayer(decoy.owner());
        if (owner == null || !alive(owner)) return;
        UUID previous = decoyBlastOwner; decoyBlastOwner = decoy.owner();
        try { PerkOrdnance.burstDecoy(world, at, owner); }
        finally { decoyBlastOwner = previous; }
    }
    private void endGravity(UUID id, Player p) {
        Gravity state = gravity.remove(id);
        if (state != null && p != null) { p.setGravity(state.original()); p.setFallDistance(0); }
    }
    private Location launchSpot(Player p) {
        Location at = p.getEyeLocation().subtract(0, .8, 0);
        for (int x = (int) Math.floor(at.getX() - .49); x <= Math.floor(at.getX() + .49); x++)
            for (int y = at.getBlockY(); y <= Math.floor(at.getY() + .98); y++)
                for (int z = (int) Math.floor(at.getZ() - .49); z <= Math.floor(at.getZ() + .49); z++)
                    if (!world.getBlockAt(x, y, z).isPassable()) { message(p, "Для снаряда нужно немного места у стены"); return null; }
        return at;
    }
    private boolean tnt(Player p) {
        Location at = launchSpot(p);
        if (at == null || !spawnRoom(p, 1)) return false;
        TNTPrimed bomb = world.spawn(at, TNTPrimed.class, t -> {
            t.setSource(p); t.setFuseTicks(40); t.setYield(2); t.setIsIncendiary(false);
            t.setVelocity(p.getEyeLocation().getDirection().multiply(1.4).add(new Vector(0, .12, 0)));
        });
        if (!bomb.isValid()) return false;
        track(bomb, p, 60); world.playSound(at, Sound.ITEM_CROSSBOW_SHOOT, 1, 1); return true;
    }
    private boolean pumpkin(Player p) {
        Location at = launchSpot(p);
        if (at == null || !spawnRoom(p, 1)) return false;
        Snowball ball = world.spawn(at, Snowball.class, b -> {
            b.setShooter(p); b.setItem(new ItemStack(Material.CARVED_PUMPKIN)); b.setGravity(false);
            b.setVelocity(p.getEyeLocation().getDirection().multiply(1.2));
        });
        if (!ball.isValid()) return false;
        pumpkins.add(ball.getUniqueId()); track(ball, p, 120);
        world.playSound(at, Sound.ENTITY_SNOWBALL_THROW, 1, .7f);
        return true;
    }
    private boolean atomicBomb(Player p, PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) { message(p, "Поставь атомную бомбу ПКМ по блоку"); return false; }
        Block space = e.getClickedBlock().getRelative(e.getBlockFace());
        Location at = space.getLocation().add(.5, 0, .5);
        if (space.getY() < world.getMinHeight() || space.getY() >= world.getMaxHeight()
                || !space.isPassable() || terrain.locked(space, tick) || !world.getWorldBorder().isInside(at)) {
            message(p, "Нет места для атомной бомбы"); return false;
        }
        if (!spawnRoom(p, 1)) return false;
        TNTPrimed bomb = world.spawn(at, TNTPrimed.class, t -> PerkOrdnance.armAtomic(t, p));
        if (!bomb.isValid()) return false;
        track(bomb, p, PerkRules.ATOMIC_FUSE_TICKS + 20);
        world.playSound(at, Sound.ENTITY_TNT_PRIMED, 1, 1);
        return true;
    }
    private void cursePumpkin(Player victim) {
        // Replacing a helmet must preserve its metadata, including another perk's charges.
        ItemStack helmet = victim.getInventory().getHelmet();
        ItemStack curse = PerkItems.cursedPumpkin();
        victim.getInventory().setHelmet(curse);
        if (helmet != null && !helmet.getType().isAir()) {
            for (ItemStack leftover : victim.getInventory().addItem(helmet.clone()).values())
                world.dropItem(victim.getLocation(), leftover);
        }
        message(victim, "Проклятая тыква! Её нельзя снять до смерти.");
        world.playSound(victim.getLocation(), Sound.BLOCK_PUMPKIN_CARVE, 1, .8f);
    }
    private boolean sheep(Player p) {
        if (!spawnRoom(p, 2)) return false;
        Vector facing = p.getLocation().getDirection().setY(0);
        if (facing.lengthSquared() < 1e-6) {
            double yaw = Math.toRadians(p.getYaw()); facing = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
        }
        Location at = p.getLocation().clone().add(facing.normalize().multiply(1.5));
        if (!fits(at)) at = p.getLocation();
        Sheep mob = world.spawn(at, Sheep.class, s -> { s.setAdult(); s.setColor(org.bukkit.DyeColor.RED); s.setRemoveWhenFarAway(false);
            s.customName(Component.text("Овца-камикадзе", NamedTextColor.RED)); s.setCustomNameVisible(true); });
        TNTPrimed bomb = world.spawn(at, TNTPrimed.class, t -> { t.setSource(p); t.setFuseTicks(100); t.setYield(2); t.setIsIncendiary(false); });
        if (!mob.isValid() || !bomb.isValid()) { mob.remove(); bomb.remove(); return false; }
        mob.addPassenger(bomb); sheep.add(new SheepBomb(mob, bomb, p.getUniqueId()));
        track(mob, p, 120); track(bomb, p, 120); return true;
    }
    private void chase(SheepBomb bomb) {
        Player target = session.perkPlayers().stream().filter(p -> active(p) && !p.getUniqueId().equals(bomb.owner()))
                .min(java.util.Comparator.comparingDouble(p -> p.getLocation().distanceSquared(bomb.sheep().getLocation()))).orElse(null);
        if (target == null) return;
        var mob = bomb.sheep(); mob.getPathfinder().moveTo(target, 1.5);
        Vector to = target.getLocation().toVector().subtract(mob.getLocation().toVector()).setY(0);
        if (to.lengthSquared() < .1) return;
        Vector velocity = to.normalize().multiply(.43);
        double vertical = mob.getVelocity().getY();
        if (mob.isOnGround() && (mob.getLocation().clone().add(velocity.clone().multiply(3)).subtract(0, 1, 0).getBlock().isPassable()
                || target.getLocation().getY() > mob.getLocation().getY() + .7)) vertical = .72;
        mob.setVelocity(velocity.setY(vertical));
    }
    void disconnected(Player p) { playerGone(p, true); }
    void playerGone(Player p) { playerGone(p, false); }
    private void playerGone(Player p, boolean keepFreeze) {
        UUID id = p.getUniqueId(); endGhost(id, p); if (!keepFreeze) frozen.remove(id); channels.remove(id); history.remove(id); gifts.remove(id);
        endGravity(id, p);
        phantomNoises.remove(id);
        decoys.values().removeIf(d -> d.owner().equals(id));
        if (!keepFreeze) gravityCycles.remove(id);
        illusions.playerGone(id); pendingAnvils.removeIf(a -> a.owner().equals(id));
        anchors.remove(id); jumping.remove(id); jumpAt.remove(id); usedAt.remove(id);
        for (Iterator<Tracked> it = entities.iterator(); it.hasNext();) {
            Tracked e = it.next();
            if (e.owner().equals(id)) { pumpkins.remove(e.entity().getUniqueId()); e.entity().remove(); it.remove(); }
        }
        sheep.removeIf(b -> { if (!b.owner().equals(id)) return false; b.sheep().remove(); b.tnt().remove(); return true; });
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void interact(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!scoped(p)) return;
        PerkType type = PerkItems.type(e.getItem());
        if (blocked(p)) { e.setCancelled(true); return; }
        if (active(p) && e.getAction().isLeftClick() && illusions.breakAt(p)) { e.setCancelled(true); return; }
        if (type == null) return;
        if (!active(p)) { e.setCancelled(true); return; }
        if (!e.getAction().isRightClick()) return;
        // Boots retain native right-click equipping; their effect starts only when actually worn.
        if (type == PerkType.ANCHOR || type == PerkType.PLANKS) {
            e.setUseInteractedBlock(Event.Result.DENY); e.setUseItemInHand(Event.Result.ALLOW); return;
        }
        if (e.getHand() != EquipmentSlot.HAND) { e.setCancelled(true); return; }
        if (PerkItems.charges(e.getItem()) <= 0) { e.setCancelled(true); return; }
        if (type == PerkType.BOW || type == PerkType.STEAL) {
            e.setUseInteractedBlock(Event.Result.DENY); e.setUseItemInHand(Event.Result.ALLOW); return;
        }
        String serial = PerkItems.string(e.getItem(), PerkItems.SERIAL);
        if (type == PerkType.BREAK) {
            e.setUseInteractedBlock(Event.Result.DENY); e.setUseItemInHand(Event.Result.ALLOW);
            Channel old = channels.get(p.getUniqueId());
            if (old == null || !old.serial().equals(serial)) channels.put(p.getUniqueId(), new Channel(serial, new PerkRules.Channel<>()));
            if (!p.hasActiveItem()) p.startUsingItem(EquipmentSlot.HAND);
            return;
        }
        e.setCancelled(true);
        if (tick - usedAt.getOrDefault(p.getUniqueId(), -100L) < 10) return;
        boolean success = switch (type) {
            case SAVE -> {
                Location at = p.getLocation().clone();
                at.setY(Math.max(at.getY(), Math.ceil(session.deathHeight()) + 1));
                boolean placed = terrain.platform(at, tick + PerkRules.PLATFORM_TICKS);
                if (placed && p.getLocation().getY() < at.getBlockY() - 1) {
                    Location saved = p.getLocation().clone(); saved.setY(at.getBlockY() - 1); teleport(p, saved);
                }
                if (placed) { Vector v = p.getVelocity(); p.setVelocity(v.setY(Math.max(-.5, v.getY()))); p.setFallDistance(0); }
                else message(p, "Нет места для платформы");
                yield placed;
            }
            case REROLL -> { boolean result = reroll(p, serial); if (result) usedAt.put(p.getUniqueId(), tick); yield false; }
            case GHOST -> { ghost(p); yield true; }
            case REWIND -> {
                var h = history.get(p.getUniqueId()); Location at = h == null ? null : h.ago(tick);
                if (at == null || !fits(at)) { message(p, "Позиция 3 секунды назад пока недоступна или занята"); yield false; }
                teleport(p, at); yield true;
            }
            case HOUSE -> { boolean built = terrain.house(p.getLocation()); if (!built) message(p, "Для домика нужно свободное место 3×3"); yield built; }
            case FREEZE -> { beam(p); yield true; }
            case CROSSBOW -> tnt(p);
            case PUMPKIN -> pumpkin(p);
            case ATOMIC_BOMB -> atomicBomb(p, e);
            case ANVIL_RAIN -> anvilRain(p);
            case TRANSMUTE -> transmute(p);
            case BLESSING -> { blessing(p); yield true; }
            case GRAVITY -> reverseGravity(p);
            case PILLS -> pills(p);
            case DECOY -> decoy(p, e);
            case FAKE_BLOCK -> {
                boolean placed = illusions.place(p);
                if (!placed) message(p, "Наведи на грань блока рядом: нужно свободное место для обманки");
                yield placed;
            }
            case SHEEP -> sheep(p);
            case SMOKE -> {
                if (smoke.size() >= 32 || smoke.stream().filter(s -> s.owner().equals(p.getUniqueId())).count() >= 2) {
                    message(p, "Подожди, пока рассеется предыдущий дым"); yield false;
                }
                smoke.add(new Smoke(p.getLocation().clone(), p.getUniqueId(), tick + PerkRules.SMOKE_TICKS)); yield true;
            }
            case FIREBALL -> {
                Location at = launchSpot(p);
                if (at == null || !spawnRoom(p, 1)) yield false;
                LargeFireball ball = world.spawn(at, LargeFireball.class, b -> {
                    b.setShooter(p); b.setDirection(p.getEyeLocation().getDirection());
                    b.setVelocity(p.getEyeLocation().getDirection().multiply(1.2)); b.setYield(2); b.setIsIncendiary(false);
                });
                if (!ball.isValid()) yield false;
                track(ball, p, 120); yield true;
            }
            default -> false;
        };
        if (success) { if (type != PerkType.GRAVITY) consumeHand(p, serial); usedAt.put(p.getUniqueId(), tick); }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void stopUsing(PlayerStopUsingItemEvent e) { if (scoped(e.getPlayer())) channels.remove(e.getPlayer().getUniqueId()); }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void input(PlayerInputEvent e) {
        Player p = e.getPlayer(); if (!scoped(p)) return;
        boolean pressed = e.getInput().isJump(); boolean before = jumping.contains(p.getUniqueId());
        if (pressed) jumping.add(p.getUniqueId()); else jumping.remove(p.getUniqueId());
        if (!pressed || before || !active(p) || p.isOnGround() || tick - jumpAt.getOrDefault(p.getUniqueId(), -100L) < 4) return;
        for (int slot = 0; slot < p.getInventory().getSize(); slot++) {
            ItemStack item = p.getInventory().getItem(slot);
            if (PerkItems.type(item) != PerkType.JUMP || PerkItems.charges(item) <= 0) continue;
            Vector velocity = p.getVelocity(); velocity.setY(.55); p.setVelocity(velocity); p.setFallDistance(0);
            if (PerkItems.charges(item) == 1) p.getInventory().setItem(slot, null);
            else { ItemStack copy = item.clone(); PerkItems.charges(copy, PerkItems.charges(copy) - 1); p.getInventory().setItem(slot, copy); }
            jumpAt.put(p.getUniqueId(), tick); p.playSound(p.getLocation(), Sound.ENTITY_BREEZE_JUMP, .6f, 1.3f); break;
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void fish(PlayerFishEvent e) {
        if (!scoped(e.getPlayer())) return;
        if (PerkItems.type(e.getPlayer().getInventory().getItemInMainHand()) != PerkType.STEAL) return;
        if (!active(e.getPlayer())) { e.setCancelled(true); e.getHook().remove(); return; }
        if (e.getState() == PlayerFishEvent.State.CAUGHT_ENTITY && e.getCaught() instanceof Player victim) {
            e.setCancelled(true); steal(e.getPlayer(), victim, e.getHook());
        } else if (e.getState() == PlayerFishEvent.State.CAUGHT_FISH) { e.setCancelled(true); }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void projectileHit(ProjectileHitEvent e) {
        if (pumpkins.remove(e.getEntity().getUniqueId())) {
            var shooter = e.getEntity().getShooter();
            e.setCancelled(true);
            e.getEntity().remove();
            if (shooter instanceof Player source && active(source)
                    && e.getHitEntity() instanceof Player victim && !victim.equals(source) && active(victim))
                cursePumpkin(victim);
            return;
        }
        if (e.getEntity() instanceof FishHook hook && hook.getShooter() instanceof Player p && scoped(p)
                && PerkItems.type(p.getInventory().getItemInMainHand()) == PerkType.STEAL && e.getHitEntity() instanceof Player victim) {
            e.setCancelled(true); steal(p, victim, hook);
        }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void launch(ProjectileLaunchEvent e) {
        if (e.getEntity() instanceof FishHook hook && hook.getShooter() instanceof Player p && scoped(p)
                && PerkItems.type(p.getInventory().getItemInMainHand()) == PerkType.STEAL) track(hook, p, 200);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void bow(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player p) || !scoped(p)) return;
        if (blocked(p)) { e.setCancelled(true); return; }
        PerkType type = PerkItems.type(e.getBow());
        if (type == PerkType.CROSSBOW) { e.setCancelled(true); return; }
        if (type != PerkType.BOW) return;
        if (!active(p)) { e.setCancelled(true); return; }
        if (!spawnRoom(p, 1)) { e.setCancelled(true); return; }
        if (e.getProjectile() instanceof AbstractArrow arrow) { arrow.setWeapon(e.getBow().clone()); arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED); }
        track(e.getProjectile(), p, 200);
        String serial = PerkItems.string(e.getBow(), PerkItems.SERIAL);
        // Native bow processing finishes before deleting the one-shot bow.
        Bukkit.getScheduler().runTask(services.plugin(), () -> { if (!closed) consume(p, e.getHand(), serial); });
    }
    private Player attacker(Entity e) {
        if (e instanceof Player p) return p;
        if (e instanceof Projectile projectile && projectile.getShooter() instanceof Player p) return p;
        if (e instanceof TNTPrimed bomb && bomb.getSource() instanceof Player p) return p;
        if (e instanceof FallingBlock) {
            for (Tracked tracked : entities) if (tracked.entity().equals(e)) return Bukkit.getPlayer(tracked.owner());
        }
        return null;
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void damage(EntityDamageEvent e) {
        Decoy decoy = decoys.get(e.getEntity().getUniqueId());
        if (decoy != null) { hitDecoy(e, decoy); return; }
        if (!(e.getEntity() instanceof Player p) || !scoped(p)) return;
        if (decoyBlastOwner != null && e.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            if (decoyBlastOwner.equals(p.getUniqueId())) e.setCancelled(true);
            else e.setDamage(PerkRules.decoyDamage(e.getDamage()));
        }
        if (blocked(p)) e.setCancelled(true);
        if (e instanceof EntityDamageByEntityEvent hit) { Player source = attacker(hit.getDamager()); if (source != null && scoped(source) && blocked(source)) e.setCancelled(true); }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void anchoredHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player p) || !active(p) || e.getFinalDamage() <= 0) return;
        Player source = attacker(e.getDamager());
        if (source == null || source.equals(p) || !alive(source)) return;
        ItemStack boots = p.getInventory().getBoots();
        if (PerkItems.type(boots) != PerkType.ANCHOR || PerkItems.charges(boots) <= 0) { anchors.remove(p.getUniqueId()); return; }
        anchors.put(p.getUniqueId(), new Anchor(tick + 1, p.getVelocity().clone()));
        consume(p, EquipmentSlot.FEET, PerkItems.string(boots, PerkItems.SERIAL));
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void knockback(io.papermc.paper.event.entity.EntityKnockbackEvent e) {
        if (e.getEntity() instanceof Player p && scoped(p) && (blocked(p) || anchors.containsKey(p.getUniqueId()))) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void legacyKnockback(org.bukkit.event.entity.EntityKnockbackEvent e) {
        if (e.getEntity() instanceof Player p && scoped(p) && (blocked(p) || anchors.containsKey(p.getUniqueId()))) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void velocity(PlayerVelocityEvent e) {
        if (!scoped(e.getPlayer())) return;
        Anchor anchor = anchors.get(e.getPlayer().getUniqueId());
        if (frozen.containsKey(e.getPlayer().getUniqueId())) e.setVelocity(new Vector());
        else if (anchor != null) e.setVelocity(anchor.velocity().clone());
    }
    void hold(org.bukkit.event.player.PlayerMoveEvent e) {
        Frozen state = frozen.get(e.getPlayer().getUniqueId());
        if (state != null) { Location at = state.at().clone(); at.setYaw(e.getTo().getYaw()); at.setPitch(e.getTo().getPitch()); e.setTo(at); }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void teleport(PlayerTeleportEvent e) {
        if (!scoped(e.getPlayer()) || internalTeleport) return;
        if (frozen.containsKey(e.getPlayer().getUniqueId())) e.setCancelled(true);
        if (ghosts.containsKey(e.getPlayer().getUniqueId())) {
            if (e.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE || !ghostBounds(ghosts.get(e.getPlayer().getUniqueId()), e.getTo())) e.setCancelled(true);
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void attack(PrePlayerAttackEntityEvent e) { if (scoped(e.getPlayer()) && blocked(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void interactEntity(PlayerInteractEntityEvent e) {
        if (decoys.containsKey(e.getRightClicked().getUniqueId()) || scoped(e.getPlayer()) &&
                (blocked(e.getPlayer()) || PerkItems.type(e.getPlayer().getInventory().getItem(e.getHand())) == PerkType.DECOY)) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void interactAtEntity(org.bukkit.event.player.PlayerInteractAtEntityEvent e) { interactEntity(e); }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void pickup(EntityPickupItemEvent e) { if (e.getEntity() instanceof Player p && scoped(p) && blocked(p)) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void drop(PlayerDropItemEvent e) { if (scoped(e.getPlayer()) && blocked(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void swap(PlayerSwapHandItemsEvent e) { if (scoped(e.getPlayer()) && blocked(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !scoped(p)) return;
        if (blocked(p)) { e.setCancelled(true); return; }
        if (e.getView().getTopInventory().getType() == org.bukkit.event.inventory.InventoryType.GRINDSTONE
                && (PerkItems.type(e.getCurrentItem()) != null || PerkItems.type(e.getCursor()) != null
                || e.getHotbarButton() >= 0 && PerkItems.type(p.getInventory().getItem(e.getHotbarButton())) != null)) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void drag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p && scoped(p) && (blocked(p)
                || e.getView().getTopInventory().getType() == org.bukkit.event.inventory.InventoryType.GRINDSTONE && PerkItems.type(e.getOldCursor()) != null)) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void craft(CraftItemEvent e) { if (!(e.getWhoClicked() instanceof Player p) || !scoped(p)) return; for (ItemStack item : e.getInventory().getMatrix()) if (PerkItems.type(item) != null) { e.setCancelled(true); return; } }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void anvil(PrepareAnvilEvent e) {
        if (e.getView().getPlayer() instanceof Player p && scoped(p))
            for (ItemStack item : e.getInventory().getContents()) if (PerkItems.type(item) != null) { e.setResult(null); break; }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void smith(PrepareSmithingEvent e) {
        if (e.getView().getPlayer() instanceof Player p && scoped(p))
            for (ItemStack item : e.getInventory().getContents()) if (PerkItems.type(item) != null) { e.setResult(null); break; }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void consume(PlayerItemConsumeEvent e) { if (scoped(e.getPlayer()) && (blocked(e.getPlayer()) || PerkItems.type(e.getItem()) != null)) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void breakBlock(BlockBreakEvent e) {
        if (!e.getBlock().getWorld().equals(world)) return;
        if (blocked(e.getPlayer()) || terrain.locked(e.getBlock(), tick)) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void broken(BlockBreakEvent e) { if (e.getBlock().getWorld().equals(world)) terrain.changed(e.getBlock()); }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void place(BlockPlaceEvent e) {
        if (!e.getBlock().getWorld().equals(world)) return;
        if (blocked(e.getPlayer()) || PerkItems.type(e.getItemInHand()) != null || terrain.locked(e.getBlock(), tick)) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void placed(BlockPlaceEvent e) { if (e.getBlock().getWorld().equals(world)) terrain.changed(e.getBlock()); }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void fade(BlockFadeEvent e) { if (e.getBlock().getWorld().equals(world) && terrain.locked(e.getBlock(), tick)) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void explosion(EntityExplodeEvent e) {
        if (!e.getLocation().getWorld().equals(world)) return;
        e.blockList().removeIf(b -> terrain.locked(b, tick));
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void blockExplosion(org.bukkit.event.block.BlockExplodeEvent e) {
        if (e.getBlock().getWorld().equals(world)) e.blockList().removeIf(b -> terrain.locked(b, tick));
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void fallingBlock(org.bukkit.event.entity.EntityChangeBlockEvent e) {
        if (e.getBlock().getWorld().equals(world) && terrain.locked(e.getBlock(), tick)) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void piston(org.bukkit.event.block.BlockPistonExtendEvent e) {
        if (e.getBlock().getWorld().equals(world) && e.getBlocks().stream().anyMatch(b -> terrain.locked(b, tick))) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void stickyPiston(org.bukkit.event.block.BlockPistonRetractEvent e) {
        if (e.getBlock().getWorld().equals(world) && e.getBlocks().stream().anyMatch(b -> terrain.locked(b, tick))) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void projectilePermission(ProjectileLaunchEvent e) {
        if (e.getEntity().getShooter() instanceof Player p && scoped(p) && blocked(p)) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void mobDeath(EntityDeathEvent e) {
        if (decoys.containsKey(e.getEntity().getUniqueId()) || sheep.stream().anyMatch(b -> b.sheep().equals(e.getEntity()))) {
            e.getDrops().clear(); e.setDroppedExp(0);
        }
    }
    @Override public void close() {
        if (closed) return;
        closed = true; ticker.cancel(); HandlerList.unregisterAll(this);
        for (UUID id : List.copyOf(gravity.keySet())) endGravity(id, Bukkit.getPlayer(id));
        for (UUID id : List.copyOf(ghosts.keySet())) endGhost(id);
        for (Tracked e : entities) e.entity().remove();
        entities.clear(); pumpkins.clear(); sheep.clear(); frozen.clear(); smoke.clear(); channels.clear(); terrain.close();
        pendingAnvils.clear(); gravityCycles.clear(); illusions.close();
        phantomNoises.clear();
        decoys.clear();
        gifts.clear(); history.clear(); anchors.clear(); jumping.clear(); jumpAt.clear(); usedAt.clear();
    }
}
