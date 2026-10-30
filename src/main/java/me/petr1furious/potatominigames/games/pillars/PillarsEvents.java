package me.petr1furious.potatominigames.games.pillars;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import io.papermc.paper.math.Angle;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;

import me.petr1furious.potatominigames.core.game.SessionServices;

/**
 * Random events of the party and rave modes. Every second an event starts with the same chance,
 * so the wait between them averages {@code eventMeanSeconds}; it is picked evenly among the events
 * not already running or announced. Swap starts immediately; others are announced five seconds ahead.
 */
final class PillarsEvents {
    enum Kind {
        BOATS("Boat Rain", "Boats fall from the sky: hop in!", 15),
        DISCO("Disco", "Keep moving, or get hurt, more every time", 30),
        BLOCK_BAN("Block Ban", "One placed block type vanishes for good", 7),
        STIFF_NECK("Stiff Neck", "You cannot turn left or right", 20),
        SWAP("Swap", "Players trade places", 0),
        LOW_GRAVITY("Low Gravity", "Everything floats", 10),
        GIANTS("Giants & Gnomes", "Everyone turns huge or tiny", 20),
        SLIPPERY("Slippery Floor", "Placed blocks turn to ice", 20),
        SPOTLIGHT("Spotlight", "Everyone glows through walls", 15),
        HANGOVER("Hangover", "Nausea and slowness", 15),
        WIND("Wind", "A steady wind pushes you; sneak to hold on", 60),
        KNOCKBACK("Knockback Party", "Everyone gets a knockback stick", 20),
        FRENZY("Item Frenzy", "Items come three times as fast", 15),
        HOT_POTATO("Hot Potato", "Hit someone to pass the TNT before it blows", 20),
        DARKNESS("Darkness", "You can barely see", 15),
        VANISHING("Vanishing Blocks", "Placed blocks vanish after 15s, until the end", -1),
        SUPERHOT("SUPERHOT", "Stand still and your items freeze", 30),
        AIRDROP("Airdrop", "A chest of loot appears above the middle", 0);

        final String title;
        final String description;
        /** Seconds; 0 happens at once, -1 lasts until the end of the game. */
        final int seconds;

        Kind(String title, String description, int seconds) {
            this.title = title;
            this.description = description;
            this.seconds = seconds;
        }
    }

    private static final int WARNING_TICKS = 100;
    /** "Moving" means having moved within this many ticks, which lets laggy players through. */
    private static final int MOVE_WINDOW_TICKS = 10;
    private static final double WIND_PUSH = 0.1;
    private static final int VANISH_TICKS = 300;
    private static final int POTATO_TICKS = 400;
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
    private static final String[] COMPASS = {"south", "south-west", "west", "north-west", "north", "north-east",
            "east", "south-east"};
    private static final List<EntityType> BOAT_TYPES = List.of(EntityType.OAK_BOAT, EntityType.SPRUCE_BOAT,
            EntityType.BIRCH_BOAT, EntityType.JUNGLE_BOAT, EntityType.ACACIA_BOAT, EntityType.DARK_OAK_BOAT,
            EntityType.MANGROVE_BOAT, EntityType.CHERRY_BOAT, EntityType.BAMBOO_RAFT);

    private record Vanish(int dueTick, Block block) {
    }

    private record Frozen(ItemStack[] storage, ItemStack offHand) {
    }

    private final PillarsSession session;
    private final SessionServices services;
    private final double chancePerSecond;
    private final NamespacedKey gravityKey;
    private final NamespacedKey scaleKey;
    private final NamespacedKey itemKey;

    private final Set<Kind> pending = EnumSet.noneOf(Kind.class);
    private final Set<Kind> running = EnumSet.noneOf(Kind.class);
    private final List<BukkitTask> tasks = new ArrayList<>();
    private final BukkitTask ticker;
    private int tick;
    private boolean closed;

    private final Map<UUID, Location> lastPosition = new HashMap<>();
    private final Map<UUID, Integer> lastMoved = new HashMap<>();
    /** Blocks players placed, by the type they were; checked again before use. */
    private final Map<Block, Material> placed = new LinkedHashMap<>();

    private final List<Entity> boats = new ArrayList<>();
    private final Map<UUID, Integer> discoHits = new HashMap<>();
    private final Map<UUID, Integer> discoPunishedAt = new HashMap<>();
    private final Map<UUID, Float> lockedYaw = new HashMap<>();
    private Vector wind;
    private float windYaw;
    private UUID potatoHolder;
    private int potatoPassedAt;
    private int potatoEndsAt;
    private final Deque<Vanish> vanishing = new ArrayDeque<>();
    private final Map<UUID, Frozen> frozen = new HashMap<>();

    PillarsEvents(PillarsSession session, SessionServices services, double meanSeconds) {
        this.session = session;
        this.services = services;
        this.chancePerSecond = 1.0 / Math.max(1.0, meanSeconds);
        var plugin = services.plugin();
        this.gravityKey = new NamespacedKey(plugin, "pillars_event_gravity");
        this.scaleKey = new NamespacedKey(plugin, "pillars_event_scale");
        this.itemKey = new NamespacedKey(plugin, "pillars_event_item");
        this.ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::onTick, 1L, 1L);
    }

    // Rolling and lifecycle.

    /** Once a second while the game runs. */
    void onSecond() {
        if (closed || ThreadLocalRandom.current().nextDouble() >= chancePerSecond) {
            return;
        }
        List<Kind> candidates = new ArrayList<>();
        for (Kind kind : Kind.values()) {
            if (!pending.contains(kind) && !running.contains(kind) && eligible(kind)) {
                candidates.add(kind);
            }
        }
        if (candidates.isEmpty()) {
            return;
        }
        Kind kind = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        if (kind == Kind.SWAP) {
            begin(kind);
            return;
        }
        pending.add(kind);
        Title warning = Title.title(
                Component.text(kind.title, NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                Component.text(kind.description, NamedTextColor.YELLOW),
                Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(4500), Duration.ofMillis(500)));
        for (Player player : audience()) {
            player.showTitle(warning);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.2f);
        }
        services.messages().broadcast(Component.text()
                .append(Component.text(kind.title, NamedTextColor.GOLD))
                .append(Component.text(" in 5s: " + kind.description, NamedTextColor.GRAY))
                .build());
        later(WARNING_TICKS, () -> begin(kind));
    }

    private boolean eligible(Kind kind) {
        return switch (kind) {
            case SWAP, HOT_POTATO -> session.aliveOnline().size() >= 2;
            case BLOCK_BAN -> !banCandidates().isEmpty();
            case FRENZY -> !session.shuffles();
            default -> true;
        };
    }

    private void begin(Kind kind) {
        pending.remove(kind);
        if (closed) {
            return;
        }
        running.add(kind);
        for (Player player : audience()) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
        }
        start(kind);
        if (kind.seconds == 0) {
            finish(kind, true);
        } else if (kind.seconds > 0 && kind != Kind.BLOCK_BAN) {
            later(kind.seconds * 20L, () -> finish(kind, true));
        }
    }

    /** @param natural false when the game is over, so nothing should still happen (no explosion) */
    private void finish(Kind kind, boolean natural) {
        if (!running.remove(kind)) {
            return;
        }
        switch (kind) {
            case BOATS -> clearBoats();
            case DISCO -> {
                discoHits.clear();
                discoPunishedAt.clear();
            }
            case STIFF_NECK -> lockedYaw.clear();
            case LOW_GRAVITY -> removeModifiers(Attribute.GRAVITY, gravityKey);
            case GIANTS -> removeModifiers(Attribute.SCALE, scaleKey);
            case WIND -> wind = null;
            case KNOCKBACK -> removeEventItems(Kind.KNOCKBACK);
            case FRENZY -> session.setItemSpeed(1);
            case HOT_POTATO -> endPotato(natural);
            case SUPERHOT -> {
                for (UUID id : List.copyOf(frozen.keySet())) {
                    Player player = Bukkit.getPlayer(id);
                    if (player != null) {
                        unfreeze(player);
                    }
                }
                frozen.clear();
            }
            default -> {
            }
        }
        if (natural && kind.seconds > 0) {
            services.messages().broadcast(Component.text(kind.title + " is over", NamedTextColor.GRAY));
        }
    }

    /** At the end of the game: undo everything that would outlive it. */
    void close() {
        if (closed) {
            return;
        }
        closed = true;
        ticker.cancel();
        for (BukkitTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
        pending.clear();
        for (Kind kind : List.copyOf(running)) {
            finish(kind, false);
        }
        vanishing.clear();
    }

    private void later(long delay, Runnable action) {
        tasks.add(Bukkit.getScheduler().runTaskLater(services.plugin(), () -> {
            if (!closed) {
                action.run();
            }
        }, delay));
    }

    // Starting each event.

    private void start(Kind kind) {
        List<Player> alive = session.aliveOnline();
        switch (kind) {
            case DISCO, SUPERHOT -> {
                // A fresh grace period, so nobody is caught standing the moment it starts.
                for (Player player : alive) {
                    lastMoved.put(player.getUniqueId(), tick);
                }
            }
            case BLOCK_BAN -> startBlockBan();
            case STIFF_NECK -> {
                for (Player player : alive) {
                    lockedYaw.put(player.getUniqueId(), player.getYaw());
                }
            }
            case SWAP -> swap(alive);
            case LOW_GRAVITY -> {
                for (Player player : alive) {
                    addModifier(player, Attribute.GRAVITY, gravityKey, -0.75);
                }
            }
            case GIANTS -> {
                for (Player player : alive) {
                    boolean giant = ThreadLocalRandom.current().nextBoolean();
                    addModifier(player, Attribute.SCALE, scaleKey, giant ? 1.0 : -0.5);
                    player.sendActionBar(Component.text(giant ? "You are a giant!" : "You are a gnome!",
                            NamedTextColor.LIGHT_PURPLE));
                }
            }
            case SPOTLIGHT -> effect(alive, PotionEffectType.GLOWING, 0, kind.seconds * 20);
            case HANGOVER -> {
                effect(alive, PotionEffectType.NAUSEA, 2, kind.seconds * 20);
                effect(alive, PotionEffectType.SLOWNESS, 1, kind.seconds * 20);
                effect(alive, PotionEffectType.WITHER, 0, 20);
            }
            case DARKNESS -> effect(alive, PotionEffectType.DARKNESS, 0, kind.seconds * 20);
            case WIND -> {
                windYaw = ThreadLocalRandom.current().nextFloat() * 360f;
                double radians = Math.toRadians(windYaw);
                wind = new Vector(-Math.sin(radians), 0, Math.cos(radians)).multiply(WIND_PUSH);
                String towards = COMPASS[Math.floorMod(Math.round(windYaw / 45f), 8)];
                Title title = Title.title(Component.text("Wind", NamedTextColor.AQUA).decorate(TextDecoration.BOLD),
                        Component.text("blowing " + towards + "; sneak to hold on", NamedTextColor.WHITE));
                for (Player player : audience()) {
                    player.showTitle(title);
                }
            }
            case KNOCKBACK -> {
                for (Player player : alive) {
                    ItemStack stick = eventItem(Material.STICK, Kind.KNOCKBACK, "Knockback Stick",
                            NamedTextColor.AQUA);
                    stick.addUnsafeEnchantment(Enchantment.KNOCKBACK, 2);
                    give(player, stick);
                }
            }
            case FRENZY -> session.setItemSpeed(3);
            case AIRDROP -> airdrop();
            case HOT_POTATO -> {
                Player holder = alive.get(ThreadLocalRandom.current().nextInt(alive.size()));
                potatoEndsAt = tick + POTATO_TICKS;
                handPotato(holder);
            }
            default -> {
            }
        }
    }

    private void onTick() {
        tick++;
        if (closed) {
            return;
        }
        List<Player> alive = session.aliveOnline();
        trackMovement(alive);
        if (running.contains(Kind.DISCO)) {
            tickDisco(alive);
        }
        if (running.contains(Kind.SUPERHOT)) {
            tickSuperhot(alive);
        }
        if (running.contains(Kind.STIFF_NECK)) {
            tickStiffNeck(alive);
        }
        if (running.contains(Kind.BOATS) && tick % 10 == 0) {
            dropBoats(alive.size());
        }
        if (running.contains(Kind.WIND) && tick % 10 == 0) {
            tickWind(alive);
        }
        if (running.contains(Kind.HOT_POTATO) && tick % 10 == 0) {
            tickPotato(alive);
        }
        while (!vanishing.isEmpty() && vanishing.peekFirst().dueTick() <= tick) {
            Block block = vanishing.pollFirst().block();
            if (block.getType() != Material.AIR) {
                block.setType(Material.AIR);
            }
            placed.remove(block);
        }
    }

    // Movement.

    private void trackMovement(List<Player> alive) {
        for (Player player : alive) {
            UUID id = player.getUniqueId();
            Location now = player.getLocation();
            Location before = lastPosition.put(id, now);
            if (before == null || !before.getWorld().equals(now.getWorld()) || before.distanceSquared(now) > 1.0E-4) {
                lastMoved.put(id, tick);
            }
        }
    }

    private boolean movedRecently(Player player) {
        return tick - lastMoved.getOrDefault(player.getUniqueId(), tick) <= MOVE_WINDOW_TICKS;
    }

    private void tickDisco(List<Player> alive) {
        if (tick % 10 == 0) {
            float pitch = 0.5f + ThreadLocalRandom.current().nextFloat() * 1.5f;
            for (Player player : audience()) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.5f, pitch);
            }
        }
        for (Player player : alive) {
            UUID id = player.getUniqueId();
            if (movedRecently(player) || tick - discoPunishedAt.getOrDefault(id, -100) < 20) {
                continue;
            }
            discoPunishedAt.put(id, tick);
            int hits = discoHits.merge(id, 1, Integer::sum);
            // One more heart every time.
            player.damage(2.0 * hits);
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, false, false));
            player.sendActionBar(Component.text("Keep moving! (-" + hits + " ♥)", NamedTextColor.RED));
        }
    }

    private void tickSuperhot(List<Player> alive) {
        for (Player player : alive) {
            boolean isFrozen = frozen.containsKey(player.getUniqueId());
            if (movedRecently(player)) {
                if (isFrozen) {
                    unfreeze(player);
                }
            } else if (!isFrozen) {
                freeze(player);
            }
            if (tick % 10 == 0) {
                player.sendActionBar(frozen.containsKey(player.getUniqueId())
                        ? Component.text("SUPERHOT: move to get your items back", NamedTextColor.RED)
                        : Component.text("SUPERHOT: keep moving", NamedTextColor.WHITE));
            }
        }
    }

    /** Holds items aside while standing still; they come back, with anything gained meanwhile, on moving. */
    private void freeze(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] storage = inventory.getStorageContents();
        for (int i = 0; i < storage.length; i++) {
            storage[i] = storage[i] == null ? null : storage[i].clone();
        }
        ItemStack offHand = inventory.getItemInOffHand();
        frozen.put(player.getUniqueId(), new Frozen(storage, offHand.getType() == Material.AIR ? null : offHand.clone()));
        inventory.setStorageContents(new ItemStack[storage.length]);
        inventory.setItemInOffHand(null);
    }

    private void unfreeze(Player player) {
        Frozen stored = frozen.remove(player.getUniqueId());
        if (stored == null) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        List<ItemStack> gained = new ArrayList<>();
        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack != null && stack.getType() != Material.AIR) {
                gained.add(stack);
            }
        }
        ItemStack offHand = inventory.getItemInOffHand();
        if (offHand.getType() != Material.AIR) {
            gained.add(offHand);
        }
        inventory.setStorageContents(stored.storage());
        inventory.setItemInOffHand(stored.offHand());
        for (ItemStack stack : gained) {
            give(player, stack);
        }
    }

    // Camera.

    /**
     * Turns the head back left-right only: a rotation packet, so the position is untouched, with the
     * pitch relative (unchanged), so players can still look down to place blocks.
     */
    private void tickStiffNeck(List<Player> alive) {
        for (Player player : alive) {
            Float locked = lockedYaw.computeIfAbsent(player.getUniqueId(), id -> player.getYaw());
            if (player.isInsideVehicle()) {
                continue;
            }
            float drift = Math.abs(wrapDegrees(player.getYaw() - locked));
            if (drift < 0.5f) {
                continue;
            }
            player.setRotation(Angle.absolute(locked), Angle.relative(0f));
        }
    }

    private static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360f;
        if (wrapped >= 180f) {
            wrapped -= 360f;
        } else if (wrapped < -180f) {
            wrapped += 360f;
        }
        return wrapped;
    }

    // Wind.

    private void tickWind(List<Player> alive) {
        if (wind == null) {
            return;
        }
        for (Player player : alive) {
            boolean holding = player.isSneaking() || player.isInsideVehicle();
            if (!holding) {
                player.setVelocity(player.getVelocity().add(wind));
            }
            float relative = wrapDegrees(windYaw - player.getYaw());
            String arrow = ARROWS[Math.floorMod(Math.round(relative / 45f), 8)];
            player.sendActionBar(Component.text("Wind " + arrow + (holding ? "  (holding on)" : ""),
                    NamedTextColor.AQUA));
        }
    }

    // Boats.

    private void dropBoats(int aliveCount) {
        int count = Math.max(1, (aliveCount + 3) / 4);
        Location center = session.center();
        double spread = session.arenaRadius() + 6;
        for (int i = 0; i < count; i++) {
            double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
            double distance = Math.sqrt(ThreadLocalRandom.current().nextDouble()) * spread;
            Location at = center.clone().add(Math.cos(angle) * distance, 25, Math.sin(angle) * distance);
            EntityType type = BOAT_TYPES.get(ThreadLocalRandom.current().nextInt(BOAT_TYPES.size()));
            // Unbreakable, so no boat items get left behind.
            boats.add(center.getWorld().spawnEntity(at, type, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM,
                    boat -> boat.setInvulnerable(true)));
        }
    }

    /** Riders are let off with a brief slow fall, so the cleanup does not drop them. */
    private void clearBoats() {
        for (Entity boat : boats) {
            if (!boat.isValid()) {
                continue;
            }
            for (Entity passenger : List.copyOf(boat.getPassengers())) {
                boat.removePassenger(passenger);
                if (passenger instanceof Player player) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 100, 0, false, false, true));
                }
            }
            boat.remove();
        }
        boats.clear();
    }

    // Swap.

    private void swap(List<Player> alive) {
        List<Player> pool = new ArrayList<>(alive);
        java.util.Collections.shuffle(pool, ThreadLocalRandom.current());
        int count = pool.size() >= 3 && ThreadLocalRandom.current().nextBoolean() ? 3 : 2;
        if (pool.size() < count) {
            return;
        }
        List<Player> chosen = pool.subList(0, count);
        List<Location> spots = new ArrayList<>();
        for (Player player : chosen) {
            player.leaveVehicle();
            spots.add(player.getLocation());
        }
        for (int i = 0; i < count; i++) {
            Player player = chosen.get(i);
            Location target = spots.get((i + 1) % count).clone();
            target.setYaw(player.getYaw());
            target.setPitch(player.getPitch());
            player.teleport(target);
            player.setVelocity(new Vector());
            player.setFallDistance(0);
            player.playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.0f);
        }
        services.messages().broadcast(Component.text("Swapped: "
                + String.join(" → ", chosen.stream().map(Player::getName).toList()) + " → "
                + chosen.getFirst().getName(), NamedTextColor.LIGHT_PURPLE));
    }

    // Hot potato.

    private void handPotato(Player holder) {
        potatoHolder = holder.getUniqueId();
        potatoPassedAt = tick;
        give(holder, eventItem(Material.TNT, Kind.HOT_POTATO, "Hot Potato", NamedTextColor.RED));
        holder.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, POTATO_TICKS, 0, false, false, false));
        holder.playSound(holder.getLocation(), Sound.ENTITY_TNT_PRIMED, 1f, 1.2f);
    }

    private void tickPotato(List<Player> alive) {
        Player holder = potatoHolder == null ? null : Bukkit.getPlayer(potatoHolder);
        if (holder == null || !alive.contains(holder)) {
            passPotatoAway(holder);
            return;
        }
        int left = Math.max(0, (potatoEndsAt - tick + 19) / 20);
        Component bar = Component.text("Hot Potato: " + holder.getName() + " — " + left + "s", NamedTextColor.RED);
        for (Player player : alive) {
            player.sendActionBar(bar);
        }
    }

    /** The holder left the game: someone else gets it, or with nobody left it just goes away. */
    private void passPotatoAway(Player former) {
        if (former != null) {
            removeEventItems(former, Kind.HOT_POTATO);
            former.removePotionEffect(PotionEffectType.GLOWING);
        }
        List<Player> others = new ArrayList<>(session.aliveOnline());
        others.remove(former);
        if (others.isEmpty()) {
            potatoHolder = null;
            finish(Kind.HOT_POTATO, false);
            return;
        }
        handPotato(others.get(ThreadLocalRandom.current().nextInt(others.size())));
    }

    private void endPotato(boolean natural) {
        Player holder = potatoHolder == null ? null : Bukkit.getPlayer(potatoHolder);
        potatoHolder = null;
        removeEventItems(Kind.HOT_POTATO);
        if (holder != null) {
            holder.removePotionEffect(PotionEffectType.GLOWING);
            if (natural && session.aliveOnline().contains(holder)) {
                holder.getWorld().createExplosion(holder, holder.getLocation(), 3.0f, false, true);
            }
        }
    }

    // Airdrop.

    /**
     * A chest with one row of pool items, between the players still in (around the middle of their
     * pillars), 3 to 15 blocks above the pillar tops; higher if that spot is taken.
     */
    private void airdrop() {
        Location spot = session.middleOfAlive();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        spot.add(random.nextInt(-2, 3), session.pillarTop() + random.nextInt(3, 16) - spot.getBlockY(),
                random.nextInt(-2, 3));
        Block block = spot.getBlock();
        while (block.getType() != Material.AIR && block.getY() < block.getWorld().getMaxHeight() - 1) {
            block = block.getRelative(org.bukkit.block.BlockFace.UP);
        }
        block.setType(Material.CHEST);
        if (block.getState() instanceof org.bukkit.block.Chest chest) {
            List<ItemStack> loot = session.lootRow(9);
            for (int i = 0; i < loot.size(); i++) {
                chest.getBlockInventory().setItem(i, loot.get(i));
            }
        }
        Location at = block.getLocation().toCenterLocation();
        at.getWorld().spawnParticle(org.bukkit.Particle.FIREWORK, at, 60, 0.6, 0.6, 0.6, 0.05);
        for (Player player : audience()) {
            player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.8f, 1.0f);
        }
        services.messages().broadcast(Component.text("Airdrop: a chest appeared " + (block.getY() - session.pillarTop())
                + " blocks above the pillars", NamedTextColor.GOLD));
    }

    // Block ban.

    /** Placed types still on the map, by weight; the pillars' own material is rarely picked. */
    private Map<Material, Double> banCandidates() {
        Map<Material, Double> weights = new LinkedHashMap<>();
        placed.entrySet().removeIf(entry -> entry.getKey().getType() != entry.getValue());
        for (Material material : placed.values()) {
            weights.put(material, 1.0);
        }
        Material pillar = session.pillarMaterial();
        if (!session.pillarBlocks(pillar).isEmpty()) {
            weights.put(pillar, 0.25);
        }
        return weights;
    }

    /** Spins through the candidates for 5s, shows the pick for 2s, then removes it. */
    private void startBlockBan() {
        Map<Material, Double> candidates = banCandidates();
        if (candidates.isEmpty()) {
            finish(Kind.BLOCK_BAN, false);
            return;
        }
        List<Material> names = List.copyOf(candidates.keySet());
        int at = 0;
        int step = 0;
        Material[] last = {null};
        while (at < WARNING_TICKS) {
            int index = step;
            later(at, () -> {
                Material shown = names.get(ThreadLocalRandom.current().nextInt(names.size()));
                if (names.size() > 1 && shown == last[0]) {
                    shown = names.get((names.indexOf(shown) + 1) % names.size());
                }
                last[0] = shown;
                showBanName(Component.text(pretty(shown), NamedTextColor.YELLOW), 15);
                for (Player player : audience()) {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.0f + index * 0.03f);
                }
            });
            at += 2 + step / 3;
            step++;
        }
        Material chosen = pick(candidates);
        later(WARNING_TICKS, () -> {
            showBanName(Component.text("✖ " + pretty(chosen), NamedTextColor.RED).decorate(TextDecoration.BOLD), 45);
            for (Player player : audience()) {
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.6f, 1.0f);
            }
        });
        later(WARNING_TICKS + 40, () -> {
            int removed = 0;
            for (Map.Entry<Block, Material> entry : List.copyOf(placed.entrySet())) {
                if (entry.getValue() == chosen && entry.getKey().getType() == chosen) {
                    entry.getKey().setType(Material.AIR, false);
                    placed.remove(entry.getKey());
                    removed++;
                }
            }
            if (chosen == session.pillarMaterial()) {
                for (Block block : session.pillarBlocks(chosen)) {
                    block.setType(Material.AIR, false);
                    removed++;
                }
            }
            session.removeFromPool(chosen);
            for (Player player : audience()) {
                player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.2f);
            }
            services.messages().broadcast(Component.text("Block Ban: " + pretty(chosen) + " is gone ("
                    + removed + " block" + (removed == 1 ? "" : "s") + ") and no longer drops", NamedTextColor.RED));
            finish(Kind.BLOCK_BAN, true);
        });
    }

    /** A subtitle only, so it does not cover the view. */
    private void showBanName(Component name, int stayTicks) {
        Title title = Title.title(Component.empty(), name,
                Title.Times.times(Duration.ZERO, Duration.ofMillis(stayTicks * 50L), Duration.ofMillis(150)));
        for (Player player : audience()) {
            player.showTitle(title);
        }
    }

    private static Material pick(Map<Material, Double> weights) {
        double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        double roll = ThreadLocalRandom.current().nextDouble() * total;
        for (Map.Entry<Material, Double> entry : weights.entrySet()) {
            roll -= entry.getValue();
            if (roll < 0) {
                return entry.getKey();
            }
        }
        return weights.keySet().iterator().next();
    }

    private static String pretty(Material material) {
        String name = material.getKey().getKey().replace('_', ' ');
        return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
    }

    // Hooks from the session.

    void onBlockPlace(BlockPlaceEvent event) {
        if (closed || !session.aliveOnline().contains(event.getPlayer())) {
            return;
        }
        if (isEventItem(event.getItemInHand(), Kind.HOT_POTATO)) {
            event.setCancelled(true);
            return;
        }
        Block block = event.getBlockPlaced();
        placed.put(block, block.getType());
        if (running.contains(Kind.SLIPPERY)) {
            later(1L, () -> {
                if (block.getType() != Material.AIR) {
                    block.setType(Material.PACKED_ICE);
                    placed.put(block, Material.PACKED_ICE);
                }
            });
        }
        if (running.contains(Kind.VANISHING)) {
            vanishing.addLast(new Vanish(tick + VANISH_TICKS, block));
        }
    }

    void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (closed || !running.contains(Kind.HOT_POTATO) || potatoHolder == null) {
            return;
        }
        if (!(event.getDamager() instanceof Player hitter) || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        List<Player> alive = session.aliveOnline();
        if (!hitter.getUniqueId().equals(potatoHolder) || !alive.contains(victim) || tick - potatoPassedAt < 10) {
            return;
        }
        removeEventItems(hitter, Kind.HOT_POTATO);
        hitter.removePotionEffect(PotionEffectType.GLOWING);
        handPotato(victim);
    }

    void onDropItem(PlayerDropItemEvent event) {
        if (isEventItem(event.getItemDrop().getItemStack(), Kind.HOT_POTATO)) {
            event.setCancelled(true);
        }
    }

    /** Knocked out: give back frozen items and pass the potato on. */
    void onEliminated(Player player) {
        if (closed || player == null) {
            return;
        }
        unfreeze(player);
        if (running.contains(Kind.HOT_POTATO) && player.getUniqueId().equals(potatoHolder)) {
            passPotatoAway(player);
        }
    }

    // Helpers.

    private List<Player> audience() {
        return services.session().audiencePlayers();
    }

    private static void effect(List<Player> players, PotionEffectType type, int amplifier, int ticks) {
        for (Player player : players) {
            player.addPotionEffect(new PotionEffect(type, ticks, amplifier, false, true, true));
        }
    }

    private void addModifier(Player player, Attribute attribute, NamespacedKey key, double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(key);
        instance.addTransientModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
    }

    /** From every participant, the knocked-out included. */
    private void removeModifiers(Attribute attribute, NamespacedKey key) {
        for (Player player : services.participants().onlinePlayers()) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null) {
                instance.removeModifier(key);
            }
        }
    }

    private ItemStack eventItem(Material material, Kind kind, String name, NamedTextColor color) {
        ItemStack stack = new ItemStack(material);
        stack.editMeta(meta -> {
            meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, kind.name());
        });
        return stack;
    }

    private boolean isEventItem(ItemStack stack, Kind kind) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        return kind.name().equals(stack.getItemMeta().getPersistentDataContainer().get(itemKey,
                PersistentDataType.STRING));
    }

    /** Inventory first, the overflow at the player's feet. */
    private static void give(Player player, ItemStack stack) {
        for (ItemStack left : player.getInventory().addItem(stack).values()) {
            player.getWorld().dropItem(player.getLocation(), left);
        }
    }

    private void removeEventItems(Kind kind) {
        for (Player player : services.participants().onlinePlayers()) {
            removeEventItems(player, kind);
        }
        for (Frozen stored : frozen.values()) {
            for (int i = 0; i < stored.storage().length; i++) {
                if (isEventItem(stored.storage()[i], kind)) {
                    stored.storage()[i] = null;
                }
            }
        }
        for (Item item : session.world().getEntitiesByClass(Item.class)) {
            if (isEventItem(item.getItemStack(), kind)) {
                item.remove();
            }
        }
    }

    private void removeEventItems(Player player, Kind kind) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isEventItem(contents[i], kind)) {
                inventory.setItem(i, null);
            }
        }
        if (isEventItem(player.getItemOnCursor(), kind)) {
            player.setItemOnCursor(null);
        }
    }
}
