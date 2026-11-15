package me.petr1furious.potatominigames.games.finals;

import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;

/** Physical, movable teammate statues and cancelable revive channels, owned by one match. */
final class FinalsRevives implements Listener {
    interface Context {
        JavaPlugin plugin();

        World world();

        long tick();

        Collection<Player> players();

        boolean canAct(Player player);

        boolean downed(UUID target);

        boolean friendly(Player player, UUID target);

        boolean inside(Location point);

        Location fallback(UUID target);

        default Location recovery(UUID target) { return fallback(target); }

        default boolean safeRecovery(Location point) { return point != null && inside(point); }

        void begin(Player player);

        boolean complete(UUID target, Player reviver, Location point, boolean defibrillator);
    }

    private static final NamespacedKey TROPHY =
            new NamespacedKey("potatominigames", "finals_revive_statue");
    private static final long MANUAL_TICKS = 80, DEFIB_CHARGE_TICKS = 20, REBUILD_TICKS = 60;
    private final Context context;
    private final Map<UUID, Statue> statues = new LinkedHashMap<>();
    private final Map<UUID, Channel> channels = new LinkedHashMap<>();
    private final Map<UUID, Rebuild> rebuilds = new LinkedHashMap<>();
    private final Map<UUID, Long> handledAt = new HashMap<>();
    private boolean listening;

    private static final class Statue {
        final UUID target;
        final String name;
        Location point;
        Location recovery;
        Item item;
        Interaction interaction;
        Player carrier;
        ItemStack previousOffhand;

        Statue(UUID target, String name, Location point) {
            this.target = target;
            this.name = name;
            this.point = point.clone();
        }

        void removeEntities() {
            if (item != null) item.remove();
            if (interaction != null) interaction.remove();
            item = null;
            interaction = null;
        }
    }

    private record Channel(
            Player actor, UUID target, boolean defibrillator, int slot, long deadline) {}

    private record Rebuild(Player reviver, long deadline) {}

    FinalsRevives(Context context) {
        this.context = context;
        listen();
    }

    private void listen() {
        if (listening || context.plugin() == null) return;
        context.plugin().getServer().getPluginManager().registerEvents(this, context.plugin());
        listening = true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryPickup(InventoryPickupItemEvent event) {
        if (matches(event.getItem())) event.setCancelled(true);
    }

    void down(Player player, Location point) {
        listen();
        remove(player.getUniqueId());
        Location safe = safePoint(player.getUniqueId(), point);
        if (safe == null) return;
        Statue statue = new Statue(player.getUniqueId(), player.getName(), safe);
        statue.recovery = context.recovery(player.getUniqueId());
        statues.put(statue.target, statue);
        spawn(statue, new Vector());
    }

    private ItemStack trophy(Statue statue, Player viewer) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        stack.editMeta(meta -> {
            meta.displayName(
                    I18n.tr("finals.revive.statue", statue.name).color(NamedTextColor.GOLD));
            meta.getPersistentDataContainer()
                    .set(TROPHY, PersistentDataType.STRING, statue.target.toString());
        });
        return viewer == null ? stack : I18n.item(stack, viewer);
    }

    private void spawn(Statue statue, Vector velocity) {
        statue.removeEntities();
        statue.item = context.world().dropItem(statue.point, trophy(statue, null));
        statue.item.setPersistent(false);
        statue.item.setInvulnerable(true);
        statue.item.setGravity(true);
        statue.item.setCanPlayerPickup(false);
        statue.item.setCanMobPickup(false);
        statue.item.setUnlimitedLifetime(true);
        statue.item.setVelocity(velocity);
        try {
            statue.interaction =
                    context.world()
                            .spawn(
                                    statue.point,
                                    Interaction.class,
                                    entity -> {
                                        entity.setPersistent(false);
                                        entity.setInvulnerable(true);
                                        entity.setGravity(false);
                                        entity.setInteractionWidth(.8f);
                                        entity.setInteractionHeight(.9f);
                                        entity.setResponsive(true);
                                    });
        } catch (RuntimeException error) {
            statue.removeEntities();
            throw error;
        }
    }

    Location location(UUID target) {
        Statue statue = statues.get(target);
        if (statue == null) return null;
        if (statue.carrier != null && statue.carrier.isOnline())
            return statue.carrier.getLocation().clone().add(0, .8, 0);
        if (statue.item != null && statue.item.isValid()) return statue.item.getLocation().clone();
        return statue.point.clone();
    }

    boolean matches(Entity entity) {
        if (entity == null) return false;
        for (Statue statue : statues.values()) {
            if (statue.item != null && statue.item.getUniqueId().equals(entity.getUniqueId()))
                return true;
            if (statue.interaction != null
                    && statue.interaction.getUniqueId().equals(entity.getUniqueId())) return true;
        }
        return false;
    }

    private Statue carried(UUID actor) {
        for (Statue statue : statues.values())
            if (statue.carrier != null && statue.carrier.getUniqueId().equals(actor)) return statue;
        return null;
    }

    boolean carrying(UUID actor) {
        return carried(actor) != null;
    }

    boolean busy(UUID actor) {
        return channels.containsKey(actor);
    }

    boolean interact(Player player) {
        if (!context.canAct(player)) return false;
        if (handledAt.getOrDefault(player.getUniqueId(), Long.MIN_VALUE) == context.tick())
            return true;
        Statue held = carried(player.getUniqueId());
        if (held != null) {
            drop(held, false);
            return handled(player);
        }
        Statue target = aimed(player, 3);
        if (target == null) return false;
        if (!context.friendly(player, target.target)
                || !context.downed(target.target)
                || rebuilds.containsKey(target.target)
                || target.carrier != null) return true;
        if (busy(player.getUniqueId())) return true;
        if (player.isSneaking()) {
            if (begin(player, target, false)) handled(player);
            return true;
        }
        if (channels.values().stream().anyMatch(channel -> channel.target.equals(target.target)))
            return true;
        context.begin(player);
        target.point = location(target.target);
        target.removeEntities();
        target.carrier = player;
        target.recovery = context.recovery(player.getUniqueId());
        ItemStack offhand = player.getInventory().getItemInOffHand();
        target.previousOffhand = offhand == null ? null : offhand.clone();
        player.getInventory().setItemInOffHand(trophy(target, player));
        return handled(player);
    }

    private boolean handled(Player player) {
        handledAt.put(player.getUniqueId(), context.tick());
        return true;
    }

    boolean defibrillate(Player reviver, UUID target) {
        Statue statue = statues.get(target);
        return statue != null && begin(reviver, statue, true);
    }

    boolean defibrillateAimed(Player reviver) {
        Statue statue = aimed(reviver, 4, true);
        return statue != null && begin(reviver, statue, true);
    }

    private boolean begin(Player actor, Statue statue, boolean defibrillator) {
        if (!context.canAct(actor)
                || carrying(actor.getUniqueId())
                || busy(actor.getUniqueId())
                || !eligible(statue.target)
                || !context.friendly(actor, statue.target)
                || rebuilds.containsKey(statue.target)
                || statue.carrier != null && !defibrillator
                || channels.values().stream()
                        .anyMatch(channel -> channel.target.equals(statue.target))
                || !defibrillator && !actor.isSneaking()
                || aimed(actor, defibrillator ? 4 : 3, defibrillator) != statue) return false;
        Channel channel =
                new Channel(
                        actor,
                        statue.target,
                        defibrillator,
                        actor.getInventory().getHeldItemSlot(),
                        context.tick() + (defibrillator ? DEFIB_CHARGE_TICKS : MANUAL_TICKS));
        channels.put(actor.getUniqueId(), channel);
        context.begin(actor);
        return true;
    }

    private boolean eligible(UUID target) {
        return context.downed(target) && connected(target) != null;
    }

    private Player connected(UUID id) {
        for (Player player : context.players())
            if (player.getUniqueId().equals(id) && player.isOnline()) return player;
        return null;
    }

    private Statue aimed(Player player, double reach) {
        return aimed(player, reach, false);
    }

    private Statue aimed(Player player, double reach, boolean includeCarried) {
        Location eye = player.getEyeLocation();
        if (!context.world().equals(eye.getWorld())) return null;
        Vector direction = eye.getDirection();
        RayTraceResult wall =
                context.world()
                        .rayTraceBlocks(eye, direction, reach, FluidCollisionMode.NEVER, true);
        double limit = wall == null ? reach : eye.toVector().distance(wall.getHitPosition());
        Statue closest = null;
        for (Statue statue : statues.values()) {
            if (statue.carrier != null && !includeCarried || !context.downed(statue.target))
                continue;
            Location point = location(statue.target);
            if (point == null || !context.world().equals(point.getWorld())) continue;
            BoundingBox box =
                    new BoundingBox(
                            point.getX() - .4,
                            point.getY() - .05,
                            point.getZ() - .4,
                            point.getX() + .4,
                            point.getY() + .9,
                            point.getZ() + .4);
            RayTraceResult hit = box.rayTrace(eye.toVector(), direction, limit);
            if (hit != null) {
                limit = eye.toVector().distance(hit.getHitPosition());
                closest = statue;
            }
        }
        return closest;
    }

    boolean throwCarried(Player player) {
        Statue statue = carried(player.getUniqueId());
        if (statue == null || !context.canAct(player)) return false;
        drop(statue, true);
        return true;
    }

    private void releaseHand(Statue statue) {
        if (statue.carrier == null) return;
        ItemStack held = statue.carrier.getInventory().getItemInOffHand();
        if (held != null
                && held.hasItemMeta()
                && statue.target
                        .toString()
                        .equals(
                                held.getItemMeta()
                                        .getPersistentDataContainer()
                                        .get(TROPHY, PersistentDataType.STRING)))
            statue.carrier
                    .getInventory()
                    .setItemInOffHand(
                            statue.previousOffhand == null
                                    ? new ItemStack(Material.AIR)
                                    : statue.previousOffhand);
        statue.previousOffhand = null;
        statue.carrier = null;
    }

    private void drop(Statue statue, boolean thrown) {
        Player carrier = statue.carrier;
        if (carrier == null) return;
        statue.recovery = context.recovery(carrier.getUniqueId());
        Location point = carrier.getEyeLocation();
        Vector direction = point.getDirection().normalize();
        // Spawn before any wall. The native item then supplies swept terrain collision and gravity.
        RayTraceResult obstruction =
                context.world()
                        .rayTraceBlocks(point, direction, .7, FluidCollisionMode.NEVER, true);
        if (obstruction == null) point.add(direction.clone().multiply(.6));
        point.subtract(0, .2, 0);
        boolean recovered = !validPoint(point);
        Location safe = recovered ? returnPoint(statue) : point.clone();
        if (safe == null) safe = statue.point.clone();
        releaseHand(statue);
        statue.point = safe;
        spawn(statue, thrown && !recovered ? direction.multiply(1.25).add(new Vector(0, .12, 0)) : new Vector());
    }

    private boolean validPoint(Location point) {
        return point != null && context.world().equals(point.getWorld()) && context.inside(point)
                && point.getY() >= context.world().getMinHeight();
    }

    private Location returnPoint(Statue statue) {
        if (validPoint(statue.recovery) && context.safeRecovery(statue.recovery))
            return statue.recovery.clone().add(0, .25, 0);
        return safePoint(statue.target, context.recovery(statue.target));
    }

    private Location safePoint(UUID target, Location point) {
        if (validPoint(point)) return point.clone();
        Location recovery = context.recovery(target);
        if (validPoint(recovery) && context.safeRecovery(recovery)) return recovery.clone().add(0, .25, 0);
        Location fallback = context.fallback(target);
        return fallback != null
                        && context.world().equals(fallback.getWorld())
                        && context.inside(fallback)
                ? fallback.clone()
                : null;
    }

    void interrupt(UUID actor) {
        Channel channel = channels.remove(actor);
        if (channel != null && channel.actor.isOnline())
            channel.actor.sendActionBar(I18n.tr("finals.revive.interrupted"));
    }

    void tick() {
        for (Statue statue : List.copyOf(statues.values())) {
            if (!context.downed(statue.target)) {
                remove(statue.target);
                continue;
            }
            if (statue.carrier != null) {
                Player carrier = statue.carrier;
                if (!carrier.isOnline()
                        || connected(carrier.getUniqueId()) == null
                        || context.downed(carrier.getUniqueId())
                        || !context.world().equals(carrier.getWorld())) drop(statue, false);
                else statue.point = carrier.getLocation().clone().add(0, .8, 0);
                continue;
            }
            if (statue.item == null || !statue.item.isValid()) {
                Location safe = returnPoint(statue);
                if (safe != null) {
                    statue.point = safe;
                    spawn(statue, new Vector());
                }
                continue;
            }
            Location live = statue.item.getLocation();
            if (!context.world().equals(live.getWorld())
                    || !context.inside(live)
                    || live.getY() < context.world().getMinHeight()) {
                Location safe = returnPoint(statue);
                if (safe != null) {
                    statue.point = safe;
                    spawn(statue, new Vector());
                }
                continue;
            }
            statue.point = live.clone();
            if (statue.interaction == null || !statue.interaction.isValid()) {
                // Recreate both entities only if the actual interaction was removed, preserving the
                // live item position.
                Vector velocity = statue.item.getVelocity();
                spawn(statue, velocity);
            } else statue.interaction.teleport(live);
        }
        for (Channel channel : List.copyOf(channels.values())) {
            UUID actor = channel.actor.getUniqueId();
            if (channels.get(actor) != channel) continue;
            Statue statue = statues.get(channel.target);
            if (statue == null
                    || !eligible(channel.target)
                    || !channel.actor.isOnline()
                    || !context.canAct(channel.actor)
                    || !context.friendly(channel.actor, channel.target)
                    || channel.actor.getInventory().getHeldItemSlot() != channel.slot
                    || !channel.defibrillator && !channel.actor.isSneaking()
                    || aimed(channel.actor, channel.defibrillator ? 4 : 3, channel.defibrillator)
                            != statue) {
                interrupt(actor);
                continue;
            }
            if (context.tick() < channel.deadline) continue;
            channels.remove(actor);
            if (channel.defibrillator) {
                if (statue.carrier != null) drop(statue, false);
                rebuilds.put(
                        channel.target,
                        new Rebuild(channel.actor, channel.deadline + REBUILD_TICKS));
            } else if (context.complete(
                    channel.target, channel.actor, location(channel.target), false))
                remove(channel.target);
        }
        for (var entry : List.copyOf(rebuilds.entrySet())) {
            UUID target = entry.getKey();
            Rebuild rebuild = entry.getValue();
            if (!statues.containsKey(target) || !eligible(target)) {
                rebuilds.remove(target);
                continue;
            }
            if (context.tick() < rebuild.deadline) continue;
            rebuilds.remove(target);
            // The accepted defibrillator charge is committed: the actor may now be dead or
            // disconnected.
            if (context.complete(target, rebuild.reviver, location(target), true)) remove(target);
        }
    }

    void disconnected(UUID id, boolean permanent) {
        handledAt.remove(id);
        interrupt(id);
        for (Channel channel : List.copyOf(channels.values()))
            if (channel.target.equals(id)) interrupt(channel.actor.getUniqueId());
        rebuilds.remove(id);
        Statue carried = carried(id);
        if (carried != null) drop(carried, false);
        if (permanent) remove(id);
    }

    void remove(UUID id) {
        handledAt.remove(id);
        interrupt(id);
        for (Channel channel : List.copyOf(channels.values()))
            if (channel.target.equals(id)) interrupt(channel.actor.getUniqueId());
        rebuilds.remove(id);
        Statue carried = carried(id);
        if (carried != null) drop(carried, false);
        Statue statue = statues.remove(id);
        if (statue != null) {
            releaseHand(statue);
            statue.removeEntities();
        }
    }

    void clear() {
        if (listening) HandlerList.unregisterAll(this);
        listening = false;
        channels.clear();
        rebuilds.clear();
        handledAt.clear();
        for (Statue statue : statues.values()) {
            releaseHand(statue);
            statue.removeEntities();
        }
        statues.clear();
    }

    Component status(Player player) {
        Channel channel = channels.get(player.getUniqueId());
        if (channel != null) {
            Statue statue = statues.get(channel.target);
            if (statue != null)
                return I18n.tr(
                        channel.defibrillator
                                ? "finals.revive.defib_charge"
                                : "finals.revive.channel",
                        statue.name,
                        Math.max(0, (channel.deadline - context.tick() + 19) / 20));
        }
        Rebuild rebuild = rebuilds.get(player.getUniqueId());
        if (rebuild != null)
            return I18n.tr(
                    "finals.revive.rebuilding",
                    Math.max(0, (rebuild.deadline - context.tick() + 19) / 20));
        Statue carried = carried(player.getUniqueId());
        return carried == null ? null : I18n.tr("finals.revive.carrying", carried.name);
    }
}
