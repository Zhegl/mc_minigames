package me.petr1furious.potatominigames.games.pillars;

import me.petr1furious.potatominigames.core.hud.EntityCamera;

import java.util.*;
import java.util.function.Supplier;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Rail;
import org.bukkit.block.data.Powerable;
import org.bukkit.entity.*;
import org.bukkit.entity.minecart.RideableMinecart;
import org.bukkit.event.*;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.*;
import org.bukkit.util.Vector;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import me.petr1furious.potatominigames.i18n.I18n;

/** Match-local expansion effects. All delayed work is driven by the owning perk ticker. */
final class PerkExpansion implements Listener, AutoCloseable {
    private record Infection(UUID owner, List<Block> cells, long until) { }
    private record ZombieDrop(UUID owner, Location at, long when) { }
    private record Remote(Player owner, Entity target, Location origin, long until, boolean puppet) { }
    private record Wish(UUID owner, Block block) { }
    private final PillarsPerks perks;
    private final Plugin plugin;
    private final World world;
    private final Supplier<List<Player>> players;
    private final PerkTerrain terrain;
    private final Map<UUID, Long> auras = new HashMap<>(), shields = new HashMap<>(), droneReady = new HashMap<>();
    private final Map<UUID, Remote> remotes = new HashMap<>();
    private final Map<UUID, PerkType> arrows = new HashMap<>();
    private final Map<PerkTerrain.Pos, Wish> wishes = new HashMap<>();
    private final List<Infection> infections = new ArrayList<>();
    private final List<ZombieDrop> drops = new ArrayList<>();
    private final List<Zombie> zombies = new ArrayList<>();
    private final Map<UUID, Long> zombieExpiry = new HashMap<>();
    private final List<UUID> pendingCamera = new ArrayList<>();
    private final Map<UUID, Vector> punches = new HashMap<>();
    private final Set<UUID> anchorHits = new HashSet<>();
    private EntityCamera camera;
    private UUID lightningTarget;
    private long tick;
    private boolean moving, closed;
    PerkExpansion(PillarsPerks perks, Plugin plugin, World world, Supplier<List<Player>> players, PerkTerrain terrain) {
        this(perks, plugin, world, players, terrain, listener -> Bukkit.getPluginManager().registerEvents(listener, plugin));
    }
    PerkExpansion(PillarsPerks perks, Plugin plugin, World world, Supplier<List<Player>> players, PerkTerrain terrain,
            java.util.function.Consumer<Listener> subscribe) {
        this.perks = perks; this.plugin = plugin; this.world = world; this.players = players; this.terrain = terrain;
        subscribe.accept(this);
    }
    boolean invulnerable(Player p) { return shields.getOrDefault(p.getUniqueId(), 0L) > tick; }
    boolean controlProjectile(Entity entity) { return remotes.values().stream().anyMatch(r -> !r.puppet() && r.target().equals(entity)); }
    boolean blocked(Player p) {
        return remotes.containsKey(p.getUniqueId()) || remotes.values().stream().anyMatch(r -> r.puppet() && r.target().equals(p));
    }
    private boolean available(Player p) { return perks.alive(p) && p.getGameMode() == GameMode.SURVIVAL; }
    private void view(Player p, Entity target) {
        if (camera == null) camera = new EntityCamera();
        camera.point(p, target);
    }
    private boolean cameraReady(Player p) {
        try { view(p, p); return true; }
        catch (RuntimeException error) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Remote perk camera unavailable", error);
            PillarsPerks.message(p, "camera_unavailable"); return false;
        }
    }
    boolean use(Player p, PerkType type, PlayerInteractEvent event) {
        return switch (type) {
            case TREE -> tree(p, event);
            case AQUARIUM -> aquarium(p);
            case AURA -> { auras.put(p.getUniqueId(), tick + PerkRules.AURA_TICKS); yield true; }
            case INVULNERABILITY -> { shields.put(p.getUniqueId(), tick + 200); yield true; }
            case METRO -> metro(p);
            case GENIE -> genie(p, event);
            case INFECTION -> infection(p, event);
            case LIGHTNING -> lightning(p);
            case APOCALYPSE -> apocalypse(p);
            default -> false;
        };
    }
    private Block placement(Player p, PlayerInteractEvent e) {
        if (e.getClickedBlock() == null || e.getClickedBlock().getLocation().distanceSquared(p.getEyeLocation()) > 49) {
            PillarsPerks.message(p, "expansion_target"); return null;
        }
        return e.getClickedBlock().getRelative(e.getBlockFace());
    }
    private boolean empty(Block b) {
        return b.getY() >= world.getMinHeight() && b.getY() < world.getMaxHeight()
                && world.getWorldBorder().isInside(b.getLocation()) && world.isChunkLoaded(b.getX() >> 4, b.getZ() >> 4)
                && PerkTerrain.air(b);
    }
    private boolean tree(Player p, PlayerInteractEvent e) {
        Block root = placement(p, e); if (root == null) return false;
        Map<Block, Material> plan = new LinkedHashMap<>();
        PerkLayouts.tree().forEach((cell, material) -> plan.put(root.getRelative(cell.x(), cell.y(), cell.z()), material));
        // Trees grow around existing builds, but the trunk must have space.
        for (int y = 0; y <= 8; y++) if (!empty(root.getRelative(0, y, 0))) {
            PillarsPerks.message(p, "structure_space"); return false;
        }
        plan.forEach((b, m) -> { if (empty(b)) {
            b.setType(m, false);
            if (b.getBlockData() instanceof org.bukkit.block.data.type.Leaves leaves) {
                leaves.setPersistent(true); b.setBlockData(leaves, false);
            }
        }});
        world.playSound(root.getLocation(), Sound.ITEM_BONE_MEAL_USE, 1, .6f); return true;
    }
    private boolean aquarium(Player p) {
        Player target = perks.rayTarget(p, 32);
        if (target == null) { PillarsPerks.message(p, "opponent_target"); return false; }
        Block center = target.getLocation().getBlock();
        // Preserve both occupied terrain and protected perk blocks.
        int count = 0;
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) for (int y = -1; y <= 3; y++) {
            Block b = center.getRelative(x, y, z);
            if (!empty(b)) continue;
            Material m = Math.abs(x) == 2 || Math.abs(z) == 2 || y == -1 || y == 3 ? Material.GLASS : Material.WATER;
            if (terrain.put(b, m, tick + 400, false)) count++;
        }
        if (count == 0) return false;
        for (int x : new int[]{-1, 1}) {
            Location at = center.getRelative(x, 0, 0).getLocation().add(.5, .3, .5);
            if (at.getBlock().getType() != Material.WATER) continue;
            PufferFish fish = world.spawn(at, PufferFish.class); fish.setPuffState(2); perks.track(fish, p, 400);
        }
        return true;
    }
    private boolean metro(Player p) {
        Block start = p.getLocation().getBlock().getRelative(0, -1, 0);
        BlockFace forward = Math.abs(p.getLocation().getDirection().getX()) > Math.abs(p.getLocation().getDirection().getZ())
                ? (p.getLocation().getDirection().getX() > 0 ? BlockFace.EAST : BlockFace.WEST)
                : (p.getLocation().getDirection().getZ() > 0 ? BlockFace.SOUTH : BlockFace.NORTH);
        List<PerkLayouts.Track> plan = PerkLayouts.metro(forward);
        for (var cell : plan) {
            Block base = start.getRelative(cell.base().x(), 0, cell.base().z()), rail = base.getRelative(BlockFace.UP);
            if ((!base.equals(start) && !empty(base)) || !empty(rail) || !empty(rail.getRelative(BlockFace.UP))) {
                PillarsPerks.message(p, "structure_space"); return false;
            }
        }
        for (var cell : plan) {
            Block base = start.getRelative(cell.base().x(), 0, cell.base().z()), rail = base.getRelative(BlockFace.UP);
            if (!base.equals(start)) base.setType(Material.REDSTONE_BLOCK, false);
            rail.setType(cell.powered() ? Material.POWERED_RAIL : Material.RAIL, false);
            Rail data = (Rail) rail.getBlockData(); data.setShape(cell.shape());
            if (data instanceof Powerable power) power.setPowered(true);
            rail.setBlockData(data, false);
        }
        for (int z : new int[]{2, 5, 7}) {
            RideableMinecart cart = world.spawn(start.getRelative(forward, z).getRelative(BlockFace.UP).getLocation().add(.5, .1, .5), RideableMinecart.class);
            cart.setVelocity(new Vector(forward.getModX() * .35, 0, forward.getModZ() * .35)); perks.track(cart, p, 1200);
        }
        return true;
    }
    private boolean genie(Player p, PlayerInteractEvent e) {
        Block b = placement(p, e); if (b == null || !empty(b)) return false;
        b.setType(Material.OAK_SIGN, false);
        if (!(b.getState() instanceof Sign sign)) return false;
        wishes.put(PerkTerrain.Pos.of(b), new Wish(p.getUniqueId(), b));
        p.openSign(sign, org.bukkit.block.sign.Side.FRONT); return true;
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void wish(SignChangeEvent e) {
        if (!e.getBlock().getWorld().equals(world)) return;
        Wish wish = wishes.get(PerkTerrain.Pos.of(e.getBlock())); if (wish == null) return;
        if (!wish.owner().equals(e.getPlayer().getUniqueId()) || !perks.active(e.getPlayer())) { e.setCancelled(true); return; }
        String text = e.lines().stream().map(PlainTextComponentSerializer.plainText()::serialize).reduce("", (a, b) -> a + " " + b).trim();
        if (text.isBlank()) { PillarsPerks.message(e.getPlayer(), "wish_empty"); return; }
        Material item = PerkWish.closest(text);
        wishes.remove(PerkTerrain.Pos.of(e.getBlock()));
        ItemStack gift = new ItemStack(item);
        for (ItemStack left : e.getPlayer().getInventory().addItem(gift).values()) world.dropItem(e.getPlayer().getLocation(), left);
        e.line(0, Component.text(item.name().toLowerCase(Locale.ROOT)));
        PillarsPerks.message(e.getPlayer(), "wish_granted", Component.translatable(item.translationKey()));
    }
    private boolean infection(Player p, PlayerInteractEvent e) {
        Block b = placement(p, e); if (b == null || !empty(b)) return false;
        if (infections.size() >= 8 || infections.stream().anyMatch(i -> i.owner().equals(p.getUniqueId()))) {
            PillarsPerks.message(p, "infection_limit"); return false;
        }
        if (!terrain.put(b, Material.SLIME_BLOCK, tick + 1200, false)) return false;
        infections.add(new Infection(p.getUniqueId(), new ArrayList<>(List.of(b)), tick + 1200)); return true;
    }
    private boolean lightning(Player p) {
        Player target = perks.rayTarget(p, 48);
        if (target == null) { PillarsPerks.message(p, "opponent_target"); return false; }
        world.strikeLightningEffect(target.getLocation());
        lightningTarget = target.getUniqueId();
        try { target.damage(target.getAttribute(Attribute.MAX_HEALTH).getValue() / 2, p); }
        finally { lightningTarget = null; }
        return true;
    }
    private boolean apocalypse(Player p) {
        var hit = p.rayTraceBlocks(192, FluidCollisionMode.NEVER);
        if (hit == null || hit.getHitBlock() == null) { PillarsPerks.message(p, "expansion_target"); return false; }
        if (drops.size() + zombies.size() + 36 > 144) { PillarsPerks.message(p, "zombie_limit"); return false; }
        Block target = hit.getHitBlock();
        for (var cell : PerkLayouts.zombieRain()) {
            Block b = target.getRelative(cell.x(), cell.y(), cell.z());
            if (!empty(b)) { PillarsPerks.message(p, "structure_space"); return false; }
        }
        for (var cell : PerkLayouts.zombieRain())
            drops.add(new ZombieDrop(p.getUniqueId(), target.getRelative(cell.x(), cell.y(), cell.z()).getLocation().add(.5, 0, .5),
                    tick + ThreadLocalRandom.current().nextInt(10, 101)));
        return true;
    }
    void bow(EntityShootBowEvent e, Player p, PerkType type) {
        if (!perks.active(p) || !(e.getProjectile() instanceof AbstractArrow arrow)) { e.setCancelled(true); return; }
        if (type == PerkType.DRONE && droneReady.getOrDefault(p.getUniqueId(), 0L) > tick) {
            PillarsPerks.message(p, "drone_cooldown", (droneReady.get(p.getUniqueId()) - tick + 19) / 20); e.setCancelled(true); return;
        }
        if (!cameraReady(p)) { e.setCancelled(true); return; }
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED); arrow.setWeapon(e.getBow().clone());
        arrow.setPersistent(false); arrows.put(arrow.getUniqueId(), type); perks.track(arrow, p, 200);
        String serial = PerkItems.string(e.getBow(), PerkItems.SERIAL);
        if (type == PerkType.DRONE) {
            arrow.setGravity(false); arrow.setCritical(false); arrow.setDamage(4);
            arrow.setVelocity(p.getEyeLocation().getDirection().multiply(.45));
            droneReady.put(p.getUniqueId(), tick + 100);
            remotes.put(p.getUniqueId(), new Remote(p, arrow, p.getLocation().clone(), tick + 160, false));
            p.closeInventory(); pendingCamera.add(p.getUniqueId());
        } else Bukkit.getScheduler().runTask(plugin, () -> { if (!closed) perks.consume(p, e.getHand(), serial); });
    }
    private void endRemote(UUID id) {
        Remote r = remotes.remove(id); if (r == null) return;
        if (camera != null) view(r.owner(), r.owner());
        if (!r.puppet()) { arrows.remove(r.target().getUniqueId()); r.target().remove(); }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void hit(ProjectileHitEvent e) {
        PerkType type = arrows.remove(e.getEntity().getUniqueId());
        if (type == PerkType.DRONE && e.getEntity().getShooter() instanceof Player p) {
            Remote r = remotes.remove(p.getUniqueId());
            if (r != null && camera != null) view(p, p);
            Bukkit.getScheduler().runTask(plugin, () -> e.getEntity().remove());
        }
        if (type != PerkType.PUPPET || !(e.getEntity().getShooter() instanceof Player p)) return;
        e.setCancelled(true); e.getEntity().remove();
        Player victim = e.getHitEntity() instanceof Player player ? player : null;
        if (!perks.active(p) || victim == null || victim.equals(p) || !perks.active(victim)
                || invulnerable(victim) || !cameraReady(p)) return;
        remotes.put(p.getUniqueId(), new Remote(p, victim, p.getLocation().clone(), tick + 100, true));
        victim.closeInventory(); victim.clearActiveItem(); p.closeInventory();
        pendingCamera.add(p.getUniqueId());
        PillarsPerks.message(victim, "puppeted");
    }
    void hold(PlayerMoveEvent e) {
        if (moving) return;
        Remote own = remotes.get(e.getPlayer().getUniqueId());
        if (own != null) {
            Location at = own.origin().clone(); at.setYaw(e.getTo().getYaw()); at.setPitch(e.getTo().getPitch()); e.setTo(at);
        } else if (blocked(e.getPlayer())) e.setTo(e.getFrom());
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void remoteTeleport(PlayerTeleportEvent e) { if (!moving && blocked(e.getPlayer())) e.setCancelled(true); }
    @EventHandler public void sneak(PlayerToggleSneakEvent e) {
        Remote r = remotes.get(e.getPlayer().getUniqueId());
        if (e.isSneaking() && r != null && !r.puppet()) endRemote(e.getPlayer().getUniqueId());
    }
    @SuppressWarnings("removal")
    @EventHandler(priority = EventPriority.HIGHEST)
    public void damage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || !world.equals(p.getWorld())) return;
        if (invulnerable(p)) { e.setCancelled(true); return; }
        if (p.getUniqueId().equals(lightningTarget) && !e.isCancelled()) {
            // Lightning takes half of maximum health, independent of armour and resistance.
            for (EntityDamageEvent.DamageModifier modifier : EntityDamageEvent.DamageModifier.values())
                if (modifier != EntityDamageEvent.DamageModifier.BASE && modifier != EntityDamageEvent.DamageModifier.ABSORPTION && e.isApplicable(modifier)) e.setDamage(modifier, 0);
        }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void damaged(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || !world.equals(p.getWorld()) || e.getFinalDamage() <= 0) return;
        endRemote(p.getUniqueId());
        if (!(e instanceof EntityDamageByEntityEvent hit) || !(hit.getDamager() instanceof AbstractArrow arrow)
                || PerkItems.type(arrow.getWeapon()) != PerkType.BOW || !available(p)) return;
        ItemStack boots = p.getInventory().getBoots();
        if (PerkItems.type(boots) == PerkType.ANCHOR && PerkItems.charges(boots) > 0) { anchorHits.add(p.getUniqueId()); return; }
        Vector direction = arrow.getVelocity().clone().setY(0);
        if (direction.lengthSquared() < .0001 && arrow.getShooter() instanceof Player source)
            direction = p.getLocation().toVector().subtract(source.getLocation().toVector()).setY(0);
        if (direction.lengthSquared() > .0001) punches.put(p.getUniqueId(), direction.normalize().multiply(3.5).setY(1.1));
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void death(EntityDeathEvent e) {
        if (zombieExpiry.containsKey(e.getEntity().getUniqueId())) {
            e.getDrops().clear(); e.setDroppedExp(0);
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void target(EntityTargetLivingEntityEvent e) {
        if (zombieExpiry.containsKey(e.getEntity().getUniqueId()) && (!(e.getTarget() instanceof Player p) || !perks.active(p))) e.setCancelled(true);
    }
    void tick(long now) {
        tick = now;
        for (var entry : Map.copyOf(punches).entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null && perks.active(p) && !invulnerable(p) && !anchorHits.contains(entry.getKey())) {
                p.leaveVehicle(); p.setVelocity(entry.getValue());
            }
        }
        punches.clear(); anchorHits.clear();
        shields.entrySet().removeIf(e -> e.getValue() <= tick || !present(e.getKey()));
        auras.entrySet().removeIf(e -> e.getValue() <= tick || !present(e.getKey()));
        for (UUID id : List.copyOf(remotes.keySet())) {
            Remote r = remotes.get(id);
            if (!available(r.owner()) || !r.target().isValid() || tick >= r.until()
                    || (r.puppet() && !available((Player) r.target())) || r.target().getLocation().distanceSquared(r.origin()) > 128 * 128) {
                endRemote(id); continue;
            }
            r.owner().setVelocity(new Vector());
            if (!r.puppet()) {
                AbstractArrow arrow = (AbstractArrow) r.target();
                if (arrow.isInBlock()) { endRemote(id); continue; }
                Vector direction = r.owner().getEyeLocation().getDirection();
                arrow.setVelocity(direction.multiply(.45)); arrow.setRotation(r.owner().getYaw(), r.owner().getPitch());
            } else drive(r);
        }
        for (UUID id : List.copyOf(pendingCamera)) {
            Remote r = remotes.get(id); if (r != null) view(r.owner(), r.target());
        }
        pendingCamera.clear();
        if (tick % 4 == 0) for (var aura : auras.entrySet()) {
            Player owner = Bukkit.getPlayer(aura.getKey()); if (owner == null || !perks.active(owner)) continue;
            Location center = owner.getLocation();
            for (int i = 0; i < 32; i++) {
                double angle = i * Math.PI / 16;
                world.spawnParticle(Particle.END_ROD, center.clone().add(Math.cos(angle) * PerkRules.AURA_RADIUS,
                        .7, Math.sin(angle) * PerkRules.AURA_RADIUS), 1, 0, 0, 0, 0);
            }
            for (Player victim : players.get()) if (!victim.equals(owner) && perks.active(victim) && !invulnerable(victim)
                    && victim.getLocation().distanceSquared(center) <= PerkRules.AURA_RADIUS * PerkRules.AURA_RADIUS) {
                Vector delta = victim.getLocation().toVector().subtract(center.toVector()).setY(0);
                if (delta.lengthSquared() < .001) delta = new Vector(1, 0, 0);
                victim.setVelocity(delta.normalize().multiply(.6).setY(.225));
            }
        }
        if (tick % 20 == 0) spread();
        for (var it = drops.iterator(); it.hasNext();) {
            ZombieDrop drop = it.next(); if (drop.when() > tick) continue; it.remove();
            Player owner = Bukkit.getPlayer(drop.owner()); if (owner == null || !perks.alive(owner)) continue;
            Zombie zombie = world.spawn(drop.at(), Zombie.class, z -> {
                z.setAdult(); z.setShouldBurnInDay(false); z.setCanPickupItems(false); z.setPersistent(false);
                if (z.getAttribute(Attribute.SPAWN_REINFORCEMENTS) != null) z.getAttribute(Attribute.SPAWN_REINFORCEMENTS).setBaseValue(0);
                z.getEquipment().setItemInMainHand(new ItemStack(Material.AIR));
            });
            zombies.add(zombie); zombieExpiry.put(zombie.getUniqueId(), tick + 1200); perks.track(zombie, owner, 1200);
        }
        for (var it = zombies.iterator(); it.hasNext();) {
            Zombie zombie = it.next();
            if (!zombie.isValid() || zombieExpiry.getOrDefault(zombie.getUniqueId(), 0L) <= tick) {
                zombieExpiry.remove(zombie.getUniqueId()); zombie.remove(); it.remove(); continue;
            }
            if (tick % 4 == 0 && !zombie.isOnGround() && zombie.getVelocity().getY() < 0 && nearBlock(zombie.getLocation()))
                zombie.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 30, 0, false, false));
            if (tick % 10 == 0) zombie.setTarget(players.get().stream().filter(perks::active)
                    .min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(zombie.getLocation()))).orElse(null));
        }
        arrows.keySet().removeIf(id -> world.getEntity(id) == null);
        wishes.entrySet().removeIf(e -> e.getValue().block().getType() != Material.OAK_SIGN);
    }
    private boolean present(UUID id) { Player p = Bukkit.getPlayer(id); return p != null && perks.alive(p); }
    private void drive(Remote r) {
        Player victim = (Player) r.target(); var input = r.owner().getCurrentInput();
        double forward = (input.isForward() ? 1 : 0) - (input.isBackward() ? 1 : 0);
        double side = (input.isRight() ? 1 : 0) - (input.isLeft() ? 1 : 0);
        double yaw = Math.toRadians(r.owner().getYaw());
        Vector step = new Vector(-Math.sin(yaw) * forward - Math.cos(yaw) * side, 0, Math.cos(yaw) * forward - Math.sin(yaw) * side);
        if (step.lengthSquared() > 0) step.normalize().multiply(input.isSprint() ? .28 : .2);
        Location at = victim.getLocation();
        boolean grounded = !perks.fits(at.clone().subtract(0, .06, 0));
        double vy = grounded ? (input.isJump() ? .42 : 0) : Math.max(-1.5, (victim.getVelocity().getY() - .08) * .98);
        // Axis-wise collision permits sliding along a wall without clipping through it.
        for (Vector axis : List.of(new Vector(step.getX(), 0, 0), new Vector(0, 0, step.getZ()), new Vector(0, vy, 0))) {
            int steps = Math.max(1, (int) Math.ceil(axis.length() / .2));
            Vector part = axis.clone().multiply(1.0 / steps);
            for (int i = 0; i < steps; i++) {
                Location next = at.clone().add(part);
                if (next.getY() < perks.deathHeight()) { victim.setHealth(0); return; }
                if (!perks.fits(next)) {
                    if (part.getY() < 0) {
                        double landing = Math.ceil(next.getY());
                        Location snapped = at.clone(); snapped.setY(landing);
                        if (landing <= at.getY() && perks.fits(snapped)) at = snapped;
                        vy = 0;
                    }
                    break;
                }
                at = next;
            }
        }
        at.setYaw(r.owner().getYaw()); at.setPitch(r.owner().getPitch());
        moving = true;
        try { victim.teleport(at); victim.setVelocity(new Vector(0, vy, 0)); }
        finally { moving = false; }
    }
    private boolean nearBlock(Location at) {
        // Check a five-block sphere, including walls beside a falling zombie.
        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) for (int y = -5; y <= 5; y++)
            if (x*x + y*y + z*z <= 25 && !at.getBlock().getRelative(x, y, z).isPassable()) return true;
        return false;
    }
    private void spread() {
        for (var it = infections.iterator(); it.hasNext();) {
            Infection infection = it.next();
            if (tick >= infection.until() || !present(infection.owner())) { it.remove(); continue; }
            infection.cells().removeIf(b -> b.getType() != Material.SLIME_BLOCK);
            List<Block> next = new ArrayList<>();
            for (Block source : List.copyOf(infection.cells())) {
                if (infection.cells().size() + next.size() >= 512) break;
                List<BlockFace> faces = new ArrayList<>(List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN));
                Collections.shuffle(faces);
                for (BlockFace face : faces) {
                    Block b = source.getRelative(face);
                    if (empty(b) && terrain.put(b, Material.SLIME_BLOCK, infection.until(), false)) { next.add(b); break; }
                }
            }
            infection.cells().addAll(next);
        }
    }
    void playerGone(Player p) {
        UUID id = p.getUniqueId(); endRemote(id);
        for (Remote r : List.copyOf(remotes.values())) if (r.target().equals(p)) endRemote(r.owner().getUniqueId());
        auras.remove(id); shields.remove(id); punches.remove(id); anchorHits.remove(id);
        drops.removeIf(d -> d.owner().equals(id)); infections.removeIf(i -> i.owner().equals(id));
        wishes.entrySet().removeIf(e -> e.getValue().owner().equals(id));
    }
    @Override public void close() {
        closed = true;
        for (UUID id : List.copyOf(remotes.keySet())) endRemote(id);
        HandlerList.unregisterAll(this);
        auras.clear(); shields.clear(); droneReady.clear(); arrows.clear(); wishes.clear(); infections.clear(); drops.clear();
        zombies.forEach(Entity::remove); zombies.clear(); zombieExpiry.clear(); pendingCamera.clear(); punches.clear(); anchorHits.clear();
    }
}
