package me.petr1furious.potatominigames.games.pillars;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.LingeringPotion;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SplashPotion;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.LingeringPotionSplashEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import me.petr1furious.potatominigames.i18n.I18n;

final class PillarsBots implements Listener, AutoCloseable {
    private static final BlockFace[] DIRECTIONS = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
    private static final int INVENTORY_SIZE = 18;
    private static final int[] UPHILL = {1, 0, -1};
    private static final int[] LEVEL = {0, -1, 1};

    private static final class Bot {
        final Player owner;
        final Mannequin body;
        final List<ItemStack> items = new ArrayList<>();
        long nextAttack;
        long nextItem;
        long shieldUntil;

        Bot(Player owner, Mannequin body) {
            this.owner = owner;
            this.body = body;
        }
    }

    private record Shot(Entity entity, Bot bot, boolean harmful, long until) { }
    private record Step(Block feet, boolean build, boolean jump, double score) { }

    private final PillarsPerks perks;
    private final PillarsSession session;
    private final World world;
    private final PerkTerrain terrain;
    private final Map<UUID, Bot> bots = new HashMap<>();
    private final Map<UUID, Shot> shots = new HashMap<>();
    private long tick;

    PillarsBots(PillarsPerks perks, PillarsSession session, Plugin plugin, World world, PerkTerrain terrain) {
        this.perks = perks;
        this.session = session;
        this.world = world;
        this.terrain = terrain;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    boolean summon(Player owner) {
        if (bots.values().stream().anyMatch(bot -> bot.owner.equals(owner) && bot.body.isValid())) return false;
        Location at = owner.getLocation();
        at.setY(session.pillarTop() + 1);
        Location spawn = terrain.pillarSpot(at, session.pillarMaterial(), ThreadLocalRandom.current());
        if (spawn == null) return false;
        Mannequin body = world.spawn(spawn, Mannequin.class, npc -> {
            npc.customName(I18n.tr("pillars.effects.agi.name", owner.name()));
            npc.setCustomNameVisible(true);
            npc.setDescription(null);
            npc.setImmovable(false);
            npc.setGravity(true);
            npc.setCanPickupItems(false);
            npc.setPersistent(false);
            npc.setRemoveWhenFarAway(false);
            npc.getCollidableExemptions().add(owner.getUniqueId());
        });
        if (!body.isValid()) return false;
        Bot bot = new Bot(owner, body);
        bots.put(body.getUniqueId(), bot);
        addItems(bot, perks.roll(session.lootRow(3)));
        return true;
    }

    private void addItems(Bot bot, List<ItemStack> items) {
        for (ItemStack item : items) {
            if (bot.items.size() >= INVENTORY_SIZE) bot.items.removeFirst();
            bot.items.add(item.clone());
        }
    }

    void draw(int count, boolean replace) {
        for (Bot bot : bots.values()) {
            if (replace) bot.items.clear();
            addItems(bot, perks.roll(session.lootRow(count)));
        }
    }

    void shuffle() {
        for (Bot bot : bots.values()) {
            bot.items.replaceAll(perks::replacement);
            bot.body.getEquipment().setItemInMainHand(null);
        }
    }

    void tick(long now) {
        tick = now;
        shots.values().removeIf(shot -> {
            if (tick < shot.until() && shot.entity().isValid()) return false;
            shot.entity().remove();
            return true;
        });
        for (Bot bot : List.copyOf(bots.values())) {
            if (!bot.body.isValid() || bot.body.getY() < session.deathHeight() || !perks.alive(bot.owner)) {
                remove(bot);
                continue;
            }
            if (bot.shieldUntil != 0 && tick >= bot.shieldUntil) {
                bot.body.setInvulnerable(false);
                bot.shieldUntil = 0;
            }
            if (tick % 4 == 0) think(bot);
        }
    }

    private void think(Bot bot) {
        Mannequin body = bot.body;
        Location from = body.getLocation();
        Player target = session.perkPlayers().stream().filter(perks::active)
                .filter(player -> !bot.owner.equals(player))
                .min(Comparator.comparingDouble(player -> from.distanceSquared(player.getLocation()))).orElse(null);
        if (target == null) return;
        Location to = target.getLocation();
        Vector aim = target.getEyeLocation().toVector().subtract(body.getEyeLocation().toVector());
        if (aim.lengthSquared() > 1e-6) {
            Location look = from.clone().setDirection(aim);
            body.setRotation(look.getYaw(), look.getPitch());
        }

        boolean visible = from.distanceSquared(to) <= 16 * 16 && body.hasLineOfSight(target);
        if (tick >= bot.nextItem && useItem(bot, target, visible)) bot.nextItem = tick + 50;
        if (visible && from.distanceSquared(to) <= 2.7 * 2.7) {
            if (tick >= bot.nextAttack) {
                bot.nextAttack = tick + 20;
                body.swingMainHand();
                target.damage(3, body);
            }
            return;
        }
        if (body.isOnGround() && Math.abs(to.getY() - from.getY()) <= 16) walk(bot, from, to);
    }

    private boolean useItem(Bot bot, Player target, boolean visible) {
        Mannequin body = bot.body;
        Location eye = body.getEyeLocation();
        double distance = eye.distanceSquared(target.getEyeLocation());
        boolean hurt = body.getHealth() <= body.getAttribute(Attribute.MAX_HEALTH).getValue() - 4;
        boolean safeShot = visible && distance > 3 * 3 && distance < 12 * 12
                && bot.owner.getLocation().distanceSquared(target.getLocation()) > 6 * 6;
        for (int slot = 0; slot < bot.items.size(); slot++) {
            ItemStack item = bot.items.get(slot);
            PerkType perk = PerkItems.type(item);
            boolean used = false;
            if (perk != null) {
                used = switch (perk) {
                    case FIREBALL -> safeShot && shootFireball(bot, target, eye);
                    case BLESSING -> hurt && PillarsPerks.blessing(body);
                    case SUPER_APPLE -> hurt && PillarsPerks.superApple(body);
                    case INVULNERABILITY -> {
                        if (!visible || distance > 5 * 5 || bot.shieldUntil > tick) yield false;
                        body.setInvulnerable(true);
                        bot.shieldUntil = tick + 100;
                        yield true;
                    }
                    default -> false;
                };
            } else if (item.getItemMeta() instanceof PotionMeta potion && potion.getBasePotionType() != null) {
                List<PotionEffect> effects = potion.getBasePotionType().getPotionEffects();
                boolean harmful = effects.stream().anyMatch(effect -> effect.getType().getEffectCategory() == PotionEffectType.Category.HARMFUL);
                if (item.getType() == Material.SPLASH_POTION || item.getType() == Material.LINGERING_POTION) {
                    if (harmful && safeShot && distance <= 9 * 9) {
                        used = throwPotion(bot, item, target.getLocation().add(0, .5, 0), true);
                    } else if (!harmful && hurt && !effects.isEmpty()) {
                        used = throwPotion(bot, item, body.getLocation(), false);
                    }
                } else if (item.getType() == Material.POTION && !harmful && hurt && !effects.isEmpty()) {
                    body.addPotionEffects(effects);
                    used = true;
                }
            }
            if (!used) continue;
            body.getEquipment().setItemInMainHand(item.asOne());
            body.swingMainHand();
            spend(bot, slot);
            return true;
        }
        return false;
    }

    private boolean shootFireball(Bot bot, Player target, Location eye) {
        Vector direction = target.getEyeLocation().toVector().subtract(eye.toVector()).normalize();
        if (world.rayTraceEntities(eye, direction, eye.distance(target.getEyeLocation()), .35,
                entity -> entity.equals(bot.owner)) != null) return false;
        Location at = eye.clone().add(direction.clone().multiply(.8));
        if (world.rayTraceBlocks(eye, direction, .8, FluidCollisionMode.NEVER, true) != null) return false;
        return track(PerkOrdnance.fireball(at, bot.body, direction), bot, true, 120);
    }

    private boolean throwPotion(Bot bot, ItemStack item, Location target, boolean harmful) {
        Vector aim = target.toVector().subtract(bot.body.getEyeLocation().toVector());
        double horizontal = Math.hypot(aim.getX(), aim.getZ());
        aim.setY(aim.getY() + horizontal * .25);
        if (aim.lengthSquared() < 1e-6) return false;
        Vector velocity = aim.normalize().multiply(.75);
        Class<? extends ThrownPotion> type = item.getType() == Material.LINGERING_POTION ? LingeringPotion.class : SplashPotion.class;
        ThrownPotion potion = bot.body.launchProjectile(type, velocity, p -> p.setItem(item.asOne()));
        return track(potion, bot, harmful, 120);
    }

    private boolean track(Entity entity, Bot bot, boolean harmful, long lifetime) {
        if (!entity.isValid()) return false;
        entity.setPersistent(false);
        shots.put(entity.getUniqueId(), new Shot(entity, bot, harmful, tick + lifetime));
        return true;
    }

    private void walk(Bot bot, Location from, Location target) {
        Block start = from.getBlock();
        int supply = -1;
        for (int i = 0; i < bot.items.size(); i++) {
            ItemStack item = bot.items.get(i);
            Material material = item.getType();
            if (PerkItems.type(item) == null && material.isBlock() && material.isOccluding()
                    && !material.hasGravity() && material != Material.TNT) {
                supply = i;
                break;
            }
        }
        Step best = null;
        for (BlockFace direction : DIRECTIONS) {
            Block side = start.getRelative(direction);
            boolean uphill = target.getBlockY() > start.getY();
            for (int dy : uphill ? UPHILL : LEVEL) {
                Block feet = side.getRelative(0, dy, 0);
                Block head = feet.getRelative(BlockFace.UP), floor = feet.getRelative(BlockFace.DOWN);
                if (feet.getY() < session.deathHeight() || !terrain.inside(head) || !standableSpace(feet) || !standableSpace(head)) continue;
                if (dy > 0 && !standableSpace(start.getRelative(0, 2, 0))) continue;
                boolean build = !floor.getType().isOccluding();
                if (build && (supply < 0 || !PerkTerrain.air(floor) || terrain.leased(floor) || (dy > 0 && !uphill))) continue;
                double score = feet.getLocation().add(.5, 0, .5).distanceSquared(target) + (build ? 2 : 0);
                if (best == null || score < best.score()) best = new Step(feet, build, dy > 0, score);
            }
        }
        if (best == null || best.score() > from.distanceSquared(target) + 1) return;
        Location destination = best.feet().getLocation().add(.5, 0, .5);
        if (best.build()) {
            ItemStack item = bot.items.get(supply);
            best.feet().getRelative(BlockFace.DOWN).setType(item.getType(), false);
            bot.body.getEquipment().setItemInMainHand(item.asOne());
            bot.body.swingMainHand();
            spend(bot, supply);
        }
        Vector motion = destination.toVector().subtract(from.toVector()).setY(0);
        if (motion.lengthSquared() > .001) bot.body.setVelocity(motion.normalize().multiply(.23).setY(best.jump() ? .42 : 0));
    }

    private static boolean standableSpace(Block block) {
        return block.isPassable() && !block.isLiquid();
    }

    private static void spend(Bot bot, int slot) {
        ItemStack item = bot.items.get(slot);
        if (PerkItems.charges(item) > 1) PerkItems.charges(item, PerkItems.charges(item) - 1);
        else if (item.getAmount() > 1) item.subtract();
        else bot.items.remove(slot);
    }

    Player ownerOf(Entity entity) {
        Bot bot = source(entity);
        return bot == null ? null : bot.owner;
    }

    private Bot source(Entity entity) {
        if (entity == null) return null;
        Shot shot = shots.get(entity.getUniqueId());
        if (shot != null) return shot.bot();
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) entity = shooter;
        return bots.get(entity.getUniqueId());
    }

    private boolean allied(Bot bot, Entity entity) {
        Bot other = bots.get(entity.getUniqueId());
        return entity.equals(bot.owner) || (other != null && other.owner.equals(bot.owner));
    }

    private boolean eligible(Bot bot, LivingEntity entity, boolean harmful) {
        if (entity instanceof Player player && !perks.active(player)) return false;
        return harmful != allied(bot, entity);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void damage(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        Bot attacking = source(event.getDamager());
        Bot defending = bots.get(victim.getUniqueId());
        Entity cause = event.getDamageSource().getCausingEntity();
        if ((attacking != null && allied(attacking, victim))
                || (defending != null && (allied(defending, event.getDamager()) || cause != null && allied(defending, cause)))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void credit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || event.getFinalDamage() <= 0) return;
        Bot bot = source(event.getDamager());
        if (bot != null) victim.setKiller(bot.owner);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void splash(PotionSplashEvent event) {
        Shot shot = shots.get(event.getEntity().getUniqueId());
        if (shot == null) return;
        for (LivingEntity entity : event.getAffectedEntities()) {
            if (!eligible(shot.bot(), entity, shot.harmful())) event.setIntensity(entity, 0);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void linger(LingeringPotionSplashEvent event) {
        Shot shot = shots.get(event.getEntity().getUniqueId());
        if (shot != null) {
            var cloud = event.getAreaEffectCloud();
            shots.put(cloud.getUniqueId(), new Shot(cloud, shot.bot(), shot.harmful(), tick + cloud.getDuration() + 20));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void cloud(AreaEffectCloudApplyEvent event) {
        Shot shot = shots.get(event.getEntity().getUniqueId());
        if (shot != null) event.getAffectedEntities().removeIf(entity -> !eligible(shot.bot(), entity, shot.harmful()));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void interact(PlayerInteractEntityEvent event) {
        if (bots.containsKey(event.getRightClicked().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler
    public void death(EntityDeathEvent event) {
        if (!bots.containsKey(event.getEntity().getUniqueId())) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
    }

    void ownerGone(Player player) {
        for (Bot bot : List.copyOf(bots.values())) if (bot.owner.equals(player)) remove(bot);
    }

    private void remove(Bot bot) {
        bots.remove(bot.body.getUniqueId());
        bot.body.remove();
        shots.values().removeIf(shot -> {
            if (shot.bot() != bot) return false;
            shot.entity().remove();
            return true;
        });
    }

    @Override
    public void close() {
        HandlerList.unregisterAll(this);
        for (Bot bot : List.copyOf(bots.values())) remove(bot);
    }
}
