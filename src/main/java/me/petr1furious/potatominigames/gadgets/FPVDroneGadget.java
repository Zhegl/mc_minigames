package me.petr1furious.potatominigames.gadgets;

import me.petr1furious.potatominigames.core.config.Property;
import me.petr1furious.potatominigames.core.config.PropertyCodec;
import me.petr1furious.potatominigames.core.world.Locations;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.ArmorStand;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class FPVDroneGadget extends AbstractGadget {
    private static final double DRONE_PLAYER_SCALE = 0.5;
    private static final int DRONE_ACTIVE_SLOT = 0;
    private static final int DRONE_ABORT_SLOT = 4;

    private static final String TAG = "fpv_drone";
    private static final String ABORT_TAG = "fpv_drone_abort";

    private final Plugin plugin;
    private final Property<Integer> maxFlightSeconds;
    private final Property<Double> explosionPower;
    private final Property<Double> soundVolume;
    private final Map<UUID, DroneSession> sessions = new HashMap<>();
    private final NamespacedKey anchorOwnerKey;
    private final NamespacedKey droneDisplayKey;

    private static class DroneSession {
        final UUID playerId;
        final ArmorStand anchor;
        final BlockDisplay droneDisplay;
        final int startTick;
        final GameMode originalMode;
        final boolean originalAllowFlight;
        final ItemStack[] originalContents;
        final int originalHeldSlot;
        final double health;
        final int foodLevel;
        final float saturation;
        final Double originalScale;
        Location lastLocation;
        boolean ended = false;
        boolean appliedInvisPotion = false;

        DroneSession(UUID playerId, ArmorStand anchor, BlockDisplay display, Location origin, int startTick,
                GameMode originalMode, boolean originalAllowFlight, ItemStack[] originalContents, int originalHeldSlot,
                double health, int foodLevel, float saturation, Double originalScale) {
            this.playerId = playerId;
            this.anchor = anchor;
            this.droneDisplay = display;
            this.startTick = startTick;
            this.originalMode = originalMode;
            this.originalAllowFlight = originalAllowFlight;
            this.originalContents = originalContents;
            this.originalHeldSlot = originalHeldSlot;
            this.health = health;
            this.foodLevel = foodLevel;
            this.saturation = saturation;
            this.originalScale = originalScale;
            this.lastLocation = origin == null ? null : origin.clone();
        }
    }

    public FPVDroneGadget(GadgetContext context) {
        super("fpv-drone", context, true);
        this.plugin = context.plugin();
        maxFlightSeconds = config().define("maxFlightSeconds", PropertyCodec.INT, 20);
        explosionPower = config().define("explosionPower", PropertyCodec.DOUBLE, 4.0);
        soundVolume = config().define("soundVolume", PropertyCodec.DOUBLE, 6.0);
        this.anchorOwnerKey = new NamespacedKey(plugin, "fpv_anchor_owner");
        this.droneDisplayKey = new NamespacedKey(plugin, "fpv_drone_display");
        registerDamageListener();
        startTickTask();
    }

    private int getMaxFlightTicks() {
        return maxFlightSeconds.get() * 20;
    }

    private void registerDamageListener() {
        plugin.getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onAnchorDeath(EntityDeathEvent event) {
                Entity victim = event.getEntity();
                if (!(victim instanceof ArmorStand))
                    return;
                String ownerId = getAnchorOwnerId(victim.getPersistentDataContainer());
                if (ownerId == null)
                    return;

                event.getDrops().clear();
                event.setDroppedExp(0);

                UUID owner = UUID.fromString(ownerId);
                DroneSession session = sessions.get(owner);
                if (session == null || session.ended)
                    return;
                endSession(owner);

                Player ownerPlayer = plugin.getServer().getPlayer(owner);
                if (ownerPlayer != null && ownerPlayer.isOnline()) {
                    if (ownerPlayer.getGameMode() == GameMode.SPECTATOR) {
                        ownerPlayer.setGameMode(GameMode.SURVIVAL);
                    }

                    ownerPlayer.damage(1000.0, event.getDamageSource());
                }
            }

            @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
            public void onBlockBreak(BlockBreakEvent event) {
                Player p = event.getPlayer();
                DroneSession s = sessions.get(p.getUniqueId());
                if (s != null && !s.ended) {
                    event.setCancelled(true);
                }
            }

            @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
            public void onBlockPlace(BlockPlaceEvent event) {
                Player p = event.getPlayer();
                DroneSession s = sessions.get(p.getUniqueId());
                if (s != null && !s.ended) {
                    event.setCancelled(true);
                }
            }

            @EventHandler(priority = EventPriority.LOWEST)
            public void onPlayerDeath(PlayerDeathEvent event) {
                Player p = event.getEntity();
                DroneSession s = sessions.get(p.getUniqueId());
                if (s != null && !s.ended) {
                    event.setCancelled(true);
                    endSession(p.getUniqueId());
                }
            }

            @EventHandler(priority = EventPriority.HIGHEST)
            public void onPlayerQuit(PlayerQuitEvent event) {
                Player p = event.getPlayer();
                DroneSession s = sessions.get(p.getUniqueId());
                if (s != null && !s.ended) {
                    endSession(p.getUniqueId());
                }
            }

            @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
            public void onItemHeld(PlayerItemHeldEvent event) {
                Player p = event.getPlayer();
                DroneSession s = sessions.get(p.getUniqueId());
                if (s == null || s.ended) {
                    return;
                }
                ItemStack nextItem = p.getInventory().getItem(event.getNewSlot());
                if (!isAbortItem(nextItem)) {
                    return;
                }
                endSession(p.getUniqueId());
                p.sendActionBar(Component.text("Drone flight aborted").color(NamedTextColor.YELLOW));
            }

            @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
            public void onInventoryClick(InventoryClickEvent event) {
                if (!(event.getWhoClicked() instanceof Player p)) {
                    return;
                }
                DroneSession s = sessions.get(p.getUniqueId());
                if (s == null || s.ended) {
                    return;
                }
                event.setCancelled(true);
            }

            @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
            public void onInventoryDrag(InventoryDragEvent event) {
                if (!(event.getWhoClicked() instanceof Player p)) {
                    return;
                }
                DroneSession s = sessions.get(p.getUniqueId());
                if (s == null || s.ended) {
                    return;
                }
                event.setCancelled(true);
            }

            @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
            public void onDropItem(PlayerDropItemEvent event) {
                Player p = event.getPlayer();
                DroneSession s = sessions.get(p.getUniqueId());
                if (s == null || s.ended) {
                    return;
                }
                if (isAbortItem(event.getItemDrop().getItemStack())) {
                    event.setCancelled(true);
                }
            }

            @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
            public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
                Entity entity = event.getRightClicked();
                if (entity instanceof ArmorStand && isAnchor(entity.getPersistentDataContainer())) {
                    event.setCancelled(true);
                }
                Player p = event.getPlayer();
                DroneSession s = sessions.get(p.getUniqueId());
                if (s != null && !s.ended) {
                    event.setCancelled(true);
                }
            }

            @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
            public void onInteractEntity(PlayerInteractEntityEvent event) {
                Entity entity = event.getRightClicked();
                if (entity instanceof ArmorStand && isAnchor(entity.getPersistentDataContainer())) {
                    event.setCancelled(true);
                }
                Player p = event.getPlayer();
                DroneSession s = sessions.get(p.getUniqueId());
                if (s != null && !s.ended) {
                    event.setCancelled(true);
                }
            }

            @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
            public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
                ArmorStand as = event.getRightClicked();
                if (as != null && isAnchor(as.getPersistentDataContainer())) {
                    event.setCancelled(true);
                }
                Player p = event.getPlayer();
                DroneSession s = sessions.get(p.getUniqueId());
                if (s != null && !s.ended) {
                    event.setCancelled(true);
                }
            }
        }, plugin);
    }

    private void startTickTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (sessions.isEmpty())
                return;
            int tick = Bukkit.getCurrentTick();
            Set<UUID> toEnd = new LinkedHashSet<>();
            for (Map.Entry<UUID, DroneSession> e : new ArrayList<>(sessions.entrySet())) {
                DroneSession s = e.getValue();
                if (s.ended || toEnd.contains(s.playerId))
                    continue;
                Player p = plugin.getServer().getPlayer(s.playerId);
                if (p == null || !p.isOnline() || p.isDead()) {
                    toEnd.add(s.playerId);
                    continue;
                }

                if (!p.getAllowFlight())
                    p.setAllowFlight(true);
                p.setFlying(true);

                if (tick - s.startTick > getMaxFlightTicks()) {
                    explodeDrone(p, s.lastLocation);
                    toEnd.add(s.playerId);
                    continue;
                }

                if (s.lastLocation != null) {
                    Location prev = s.lastLocation;
                    Location curr = p.getLocation();
                    double dx = curr.getX() - prev.getX();
                    double dy = curr.getY() - prev.getY();
                    double dz = curr.getZ() - prev.getZ();
                    double distSq = dx * dx + dy * dy + dz * dz;
                    if (distSq > 1.0E-6) {
                        if (detectShiftedCollision(p, dx, dy, dz, 0.15)) {
                            explodeDrone(p, s.lastLocation);
                            toEnd.add(s.playerId);
                            continue;
                        }
                        Entity collidedEntity = detectEntityCollision(p, s);
                        if (collidedEntity != null) {
                            explodeDrone(p, s.lastLocation);
                            toEnd.add(s.playerId);
                            if (collidedEntity instanceof Player other) {
                                DroneSession otherSession = sessions.get(other.getUniqueId());
                                if (otherSession != null && !otherSession.ended) {
                                    toEnd.add(other.getUniqueId());
                                }
                            }
                            continue;
                        }
                    }
                }

                if (s.droneDisplay != null && !s.droneDisplay.isDead()) {
                    Location l = p.getLocation().clone();
                    l.setPitch(0f);
                    double angle = l.getYaw() / 180 * Math.PI;
                    double sin = Math.sin(angle);
                    double cos = Math.cos(angle);
                    s.droneDisplay.teleport(l.add(-0.5 * (cos - sin), 1, -0.5 * (sin + cos)));
                }

                int left = (int) Math.max(0, (getMaxFlightTicks() - (tick - s.startTick)) / 20.0);
                p.sendActionBar(Component.text("FPV: " + left + "s").color(NamedTextColor.AQUA));

                if (tick % 10 == 0) {
                    p.getWorld().playSound(p, Sound.ENTITY_BEE_LOOP, SoundCategory.PLAYERS,
                            (float) (double) soundVolume.get(), 2);
                }

                s.lastLocation = p.getLocation().clone();
            }

            for (UUID id : toEnd)
                endSession(id);
        }, 1L, 1L);
    }

    private boolean detectShiftedCollision(Player p, double dx, double dy, double dz, double epsScale) {
        double sx = dx * epsScale;
        double sy = dy * epsScale;
        double sz = dz * epsScale;
        var bb = p.getBoundingBox();
        return detectBlockCollisionBounds(p.getWorld(), bb.getMinX() + sx, bb.getMinY() + sy, bb.getMinZ() + sz,
                bb.getMaxX() + sx, bb.getMaxY() + sy, bb.getMaxZ() + sz);
    }

    private Entity detectEntityCollision(Player p, DroneSession session) {
        var bb = p.getBoundingBox();
        World w = p.getWorld();

        Collection<Entity> nearby = w.getNearbyEntities(p.getLocation(), 2, 2, 2, ent -> true);
        for (Entity ent : nearby) {
            if (ent == p)
                continue;
            if (session.anchor != null && ent.getUniqueId().equals(session.anchor.getUniqueId()))
                continue;
            if (session.droneDisplay != null && ent.getUniqueId().equals(session.droneDisplay.getUniqueId()))
                continue;
            if (ent instanceof Player other) {
                if (other.getGameMode() == GameMode.SPECTATOR)
                    continue;
            }

            var ebb = ent.getBoundingBox();
            if (bb.overlaps(ebb)) {
                return ent;
            }
        }
        return null;
    }

    private boolean detectBlockCollisionBounds(World world, double minX, double minY, double minZ, double maxX,
            double maxY, double maxZ) {
        int bMinX = (int) Math.floor(minX);
        int bMaxX = (int) Math.floor(maxX);
        int bMinY = (int) Math.floor(minY);
        int bMaxY = (int) Math.floor(maxY);
        int bMinZ = (int) Math.floor(minZ);
        int bMaxZ = (int) Math.floor(maxZ);
        for (int bx = bMinX; bx <= bMaxX; bx++) {
            for (int by = bMinY; by <= bMaxY; by++) {
                for (int bz = bMinZ; bz <= bMaxZ; bz++) {
                    Block block = world.getBlockAt(bx, by, bz);
                    if (!block.getType().isSolid())
                        continue;
                    if (block.isPassable())
                        continue;
                    var collisionShape = block.getCollisionShape();
                    for (var shapeBox : collisionShape.getBoundingBoxes()) {
                        double blMinX = bx + shapeBox.getMinX();
                        double blMaxX = bx + shapeBox.getMaxX();
                        double blMinY = by + shapeBox.getMinY();
                        double blMaxY = by + shapeBox.getMaxY();
                        double blMinZ = bz + shapeBox.getMinZ();
                        double blMaxZ = bz + shapeBox.getMaxZ();
                        if (blMaxX > minX && blMinX < maxX && blMaxY > minY && blMinY < maxY && blMaxZ > minZ
                                && blMinZ < maxZ) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!GadgetItems.isRightClick(event))
            return;
        Player player = event.getPlayer();
        DroneSession existing = sessions.get(player.getUniqueId());
        if (existing != null && !existing.ended) {
            event.setCancelled(true);
            return;
        }
        ItemStack item = event.getItem();
        if (!GadgetItems.checkForItem(item, TAG))
            return;
        event.setCancelled(true);

        if (!player.getLocation().clone().subtract(0, 0.05, 0).getBlock().getType().isSolid()) {
            player.sendActionBar(Component.text("Must be on ground to deploy drone").color(NamedTextColor.RED));
            return;
        }

        if (player.getGameMode() != GameMode.CREATIVE && event.getHand() != null) {
            ItemStack inHand = player.getInventory().getItem(event.getHand());
            if (inHand != null) {
                inHand.setAmount(inHand.getAmount() - 1);
            }
        }
        startSession(player);
    }

    private ItemStack cloneItem(ItemStack original) {
        if (original == null)
            return null;
        return original.clone();
    }

    private String getAnchorOwnerId(PersistentDataContainer container) {
        return container.get(anchorOwnerKey, PersistentDataType.STRING);
    }

    private boolean isAnchor(PersistentDataContainer container) {
        return container.has(anchorOwnerKey, PersistentDataType.STRING);
    }

    private void startSession(Player player) {
        Location origin = player.getLocation();

        ArmorStand anchor = origin.getWorld().spawn(origin, ArmorStand.class, as -> {
            as.setInvisible(false);
            as.setMarker(false);
            as.setSmall(false);
            as.setCustomNameVisible(false);
            as.setGravity(true);
            as.setArms(true);
            as.setBasePlate(false);
            as.getPersistentDataContainer().set(anchorOwnerKey, PersistentDataType.STRING,
                    player.getUniqueId().toString());
            var inv = player.getInventory();
            if (as.getEquipment() != null) {
                as.getEquipment().setHelmet(cloneItem(inv.getHelmet()));
                as.getEquipment().setChestplate(cloneItem(inv.getChestplate()));
                as.getEquipment().setLeggings(cloneItem(inv.getLeggings()));
                as.getEquipment().setBoots(cloneItem(inv.getBoots()));
                as.getEquipment().setItemInMainHand(cloneItem(inv.getItemInMainHand()));
                as.getEquipment().setItemInOffHand(cloneItem(inv.getItemInOffHand()));
            }
        });

        BlockDisplay display = origin.getWorld().spawn(origin, BlockDisplay.class, bd -> {
            bd.setBlock(Bukkit.createBlockData(Material.IRON_TRAPDOOR));
            bd.getPersistentDataContainer().set(droneDisplayKey, PersistentDataType.BYTE, (byte) 1);
            bd.setTeleportDuration(1);
        });
        GameMode originalMode = player.getGameMode();
        boolean originalAllowFlight = player.getAllowFlight();
        ItemStack[] originalContents = player.getInventory().getContents();
        int originalHeldSlot = player.getInventory().getHeldItemSlot();
        double health = player.getHealth();
        int food = player.getFoodLevel();
        float saturation = player.getSaturation();
        Double originalScale = null;
        AttributeInstance scaleAttribute = player.getAttribute(Attribute.SCALE);
        if (scaleAttribute != null) {
            originalScale = scaleAttribute.getBaseValue();
        }

        player.setGameMode(GameMode.SURVIVAL);
        if (!player.getAllowFlight())
            player.setAllowFlight(true);
        player.setFlying(true);
        if (scaleAttribute != null) {
            scaleAttribute.setBaseValue(DRONE_PLAYER_SCALE);
        }
        player.teleport(player.getLocation().add(0, 0.05, 0));
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[] { null, null, null, null });
        player.getInventory().setItem(DRONE_ABORT_SLOT, createAbortItem());
        player.getInventory().setHeldItemSlot(DRONE_ACTIVE_SLOT);
        boolean addedPotion = false;
        if (!player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            PotionEffect invis = new PotionEffect(PotionEffectType.INVISIBILITY, 20 * 60 * 30, 1, false, false, false);
            player.addPotionEffect(invis);
            addedPotion = true;
        }

        DroneSession session = new DroneSession(player.getUniqueId(), anchor, display, origin, Bukkit.getCurrentTick(),
                originalMode, originalAllowFlight, originalContents, originalHeldSlot, health, food, saturation,
                originalScale);
        session.appliedInvisPotion = addedPotion;
        sessions.put(player.getUniqueId(), session);
        player.sendActionBar(Component.text("Drone deployed: select the barrier to abort").color(NamedTextColor.GREEN));
    }

    public void endSession(UUID playerId) {
        DroneSession session = sessions.get(playerId);
        if (session == null || session.ended)
            return;
        session.ended = true;
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null && player.isOnline()) {
            player.setAllowFlight(session.originalAllowFlight);
            if (!session.originalAllowFlight) {
                player.setFlying(false);
            }
            player.setGameMode(session.originalMode);

            player.stopSound(Sound.ENTITY_BEE_LOOP, SoundCategory.PLAYERS);

            if (session.originalContents != null) {
                player.getInventory().setContents(session.originalContents.clone());
            }
            player.getInventory().setHeldItemSlot(session.originalHeldSlot);

            if (session.appliedInvisPotion) {
                player.removePotionEffect(PotionEffectType.INVISIBILITY);
            }
            AttributeInstance scaleAttribute = player.getAttribute(Attribute.SCALE);
            if (scaleAttribute != null && session.originalScale != null) {
                scaleAttribute.setBaseValue(session.originalScale);
            }
            if (session.anchor != null && !session.anchor.isDead()) {
                Location anchorLocation = session.anchor.getLocation();
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline() && !player.isDead()) {
                        player.teleport(anchorLocation);
                        double max = player.getAttribute(Attribute.MAX_HEALTH).getValue();
                        player.setHealth(Math.min(max, session.health));
                        player.setFoodLevel(session.foodLevel);
                        player.setSaturation(session.saturation);
                    }
                }, 1L);
            }
        }
        if (session.anchor != null && !session.anchor.isDead())
            session.anchor.remove();
        if (session.droneDisplay != null && !session.droneDisplay.isDead())
            session.droneDisplay.remove();
        sessions.remove(playerId);
    }

    public void endAllSessions() {
        for (UUID playerId : new ArrayList<>(sessions.keySet())) {
            endSession(playerId);
        }
    }

    @Override
    public void onGameEnd() {
        endAllSessions();
    }

    private void explodeDrone(Player player, Location previousTickLocation) {
        Location location = previousTickLocation != null ? previousTickLocation : player.getLocation();
        Locations.spawnExplosion(location, explosionPower.get(), player, true);
    }

    private ItemStack createAbortItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        GadgetItems.addTag(item, ABORT_TAG);
        var meta = item.getItemMeta();
        meta.displayName(
                Component.text("Abort Drone").color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isAbortItem(ItemStack item) {
        return GadgetItems.checkForItem(item, ABORT_TAG);
    }

    @Override
    public ItemStack createItem(int count) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        GadgetItems.addTag(item, TAG);
        var meta = item.getItemMeta();
        meta.displayName(
                Component.text("FPV Drone").color(NamedTextColor.DARK_AQUA).decoration(TextDecoration.ITALIC, false));
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        item.setAmount(count);
        return item;
    }

    public boolean isPlayerInDroneMode(Player player) {
        DroneSession s = sessions.get(player.getUniqueId());
        return s != null && !s.ended;
    }
}
