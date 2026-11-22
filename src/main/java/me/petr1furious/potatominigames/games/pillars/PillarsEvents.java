package me.petr1furious.potatominigames.games.pillars;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

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
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import io.papermc.paper.math.Angle;
import io.papermc.paper.event.player.AsyncChatEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;

import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.i18n.I18n;

/**
 * Random events of the party and rave modes. Every second an event starts with the same chance,
 * so the wait between them averages the supplied mean. Casino modes roll the category first.
 * Swap starts immediately; others are announced five seconds ahead.
 */
final class PillarsEvents implements Listener {
    enum Casino {
        LUCKY(75), NEUTRAL(50), UNLUCKY(25);

        final int goodPercent;

        Casino(int goodPercent) {
            this.goodPercent = goodPercent;
        }

        static Casino fromMode(String mode) {
            return switch (mode) {
                case "casino_lucky" -> LUCKY;
                case "casino" -> NEUTRAL;
                case "casino_unlucky" -> UNLUCKY;
                default -> null;
            };
        }

        Kind pick(List<Kind> eligible, RandomGenerator random) {
            boolean good = random.nextInt(100) < goodPercent;
            List<Kind> pool = eligible.stream().filter(kind -> kind.good == good).toList();
            if (pool.isEmpty()) pool = eligible;
            return pool.get(random.nextInt(pool.size()));
        }
    }

    enum Kind {
        BOATS(15, true),
        DISCO(30),
        BLOCK_BAN(7),
        STIFF_NECK(20),
        SWAP(0),
        LOW_GRAVITY(10, true),
        GIANTS(20),
        SLIPPERY(20),
        SPOTLIGHT(15),
        HANGOVER(15),
        WIND(60),
        KNOCKBACK(20, true),
        FRENZY(15, true),
        HOT_POTATO(20),
        DARKNESS(15),
        VANISHING(-1),
        SUPERHOT(30),
        AIRDROP(0, true),
        CONTRACT_AD(5),
        MATH(10),
        SECOND_WIND(0, true, true),
        SPEED(12, true, true),
        WEAKNESS(12, false, true);

        final Component title;
        final Component description;
        /** Seconds; 0 happens at once, -1 lasts until the end of the game. */
        final int seconds;
        final boolean good;
        final boolean casinoOnly;

        Kind(int seconds) {
            this(seconds, false);
        }

        Kind(int seconds, boolean good) {
            this(seconds, good, false);
        }

        Kind(int seconds, boolean good, boolean casinoOnly) {
            this.good = good;
            this.casinoOnly = casinoOnly;
            String key = "pillars.effects.event." + name().toLowerCase(Locale.ROOT);
            this.title = I18n.tr(key + ".title");
            this.description = I18n.tr(key + ".description");
            this.seconds = seconds;
        }
    }

    private static final int WARNING_TICKS = 100;
    /** "Moving" means having moved within this many ticks, which lets laggy players through. */
    private static final int MOVE_WINDOW_TICKS = 10;
    private static final double WIND_PUSH = 0.1;
    private static final int VANISH_TICKS = 300;
    private static final int POTATO_TICKS = 400;
    private static final double MATH_DAMAGE = 6.0;
    private static final NamedTextColor[] AD_COLORS = {NamedTextColor.RED, NamedTextColor.YELLOW,
            NamedTextColor.AQUA, NamedTextColor.LIGHT_PURPLE};
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
    private static final String[] COMPASS = {"south", "south-west", "west", "north-west", "north", "north-east",
            "east", "south-east"};
    private static final List<EntityType> BOAT_TYPES = List.of(EntityType.OAK_BOAT, EntityType.SPRUCE_BOAT,
            EntityType.BIRCH_BOAT, EntityType.JUNGLE_BOAT, EntityType.ACACIA_BOAT, EntityType.DARK_OAK_BOAT,
            EntityType.MANGROVE_BOAT, EntityType.CHERRY_BOAT, EntityType.BAMBOO_RAFT);

    private static final class Placement {
        Block block;
        Material material;
        final UUID owner;

        Placement(Block block, UUID owner) {
            this.block = block;
            this.material = block.getType();
            this.owner = owner;
        }
    }

    private record Vanish(int dueTick, Placement placement) {
    }

    private record Frozen(ItemStack[] storage, ItemStack offHand) {
    }

    private final PillarsSession session;
    private final SessionServices services;
    private final Casino casino;
    private final NamespacedKey gravityKey;
    private final NamespacedKey scaleKey;
    private final NamespacedKey itemKey;

    private final Set<Kind> pending = EnumSet.noneOf(Kind.class);
    private final Set<Kind> running = EnumSet.noneOf(Kind.class);
    private final EventTasks<Kind> eventTasks = new EventTasks<>();
    private long burstEndsAt;
    private final BukkitTask ticker;
    private int tick;
    private boolean closed;

    private final Map<UUID, Location> lastPosition = new HashMap<>();
    private final Map<UUID, Integer> lastMoved = new HashMap<>();
    /** Blocks players placed, by the type they were; checked again before use. */
    private final Map<Block, Placement> placed = new LinkedHashMap<>();

    private final List<Entity> boats = new ArrayList<>();
    private final Map<UUID, Integer> discoHits = new HashMap<>();
    private final Map<UUID, Integer> discoPunishedAt = new HashMap<>();
    private final Map<UUID, Float> lockedYaw = new HashMap<>();
    private Vector wind;
    private float windYaw;
    private UUID potatoHolder;
    private int potatoPassedAt;
    private int potatoEndsAt;
    private boolean potatoExplosion;
    private final Deque<Vanish> vanishing = new ArrayDeque<>();
    private final Map<UUID, Frozen> frozen = new HashMap<>();
    private int adStartedAt;
    private int mathEndsAt;
    /** The async chat handler only snapshots this reference; all state is read on the server thread. */
    private volatile MathChallenge mathChallenge;

    PillarsEvents(PillarsSession session, SessionServices services, Casino casino) {
        this.session = session;
        this.services = services;
        this.casino = casino;
        var plugin = services.plugin();
        this.gravityKey = new NamespacedKey(plugin, "pillars_event_gravity");
        this.scaleKey = new NamespacedKey(plugin, "pillars_event_scale");
        this.itemKey = new NamespacedKey(plugin, "pillars_event_item");
        Bukkit.getPluginManager().registerEvents(this, plugin);
        this.ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::onTick, 1L, 1L);
    }

    // Rolling and lifecycle.

    /** Once a second while the game runs. */
    void onSecond(double meanSeconds) {
        if (closed || meanSeconds <= 0 || ThreadLocalRandom.current().nextDouble() >= 1.0 / Math.max(1.0, meanSeconds)) {
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
        var random = ThreadLocalRandom.current();
        Kind kind = casino == null ? candidates.get(random.nextInt(candidates.size())) : casino.pick(candidates, random);
        eventTasks.selected(kind);
        if (kind == Kind.SWAP) {
            begin(kind);
            return;
        }
        pending.add(kind);
        Title warning = Title.title(
                kind.title.color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                kind.description.color(NamedTextColor.YELLOW),
                Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(4500), Duration.ofMillis(500)));
        for (Player player : audience()) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.2f);
        }
        showEventTitle(kind, warning);
        services.messages().broadcast(I18n.tr("pillars.effects.event.warning",
                kind.title.color(NamedTextColor.GOLD), kind.description).color(NamedTextColor.GRAY));
        later(kind, WARNING_TICKS, () -> begin(kind));
    }

    void startBurst(long durationTicks) {
        eventTasks.startBurst();
        burstEndsAt = tick + durationTicks;
    }

    /** Cancel warnings, delayed block bans and temporary effects belonging to this burst. */
    void stopBurst() {
        for (Kind kind : eventTasks.stopBurst()) {
            finish(kind, false);
        }
    }

    private boolean eligible(Kind kind) {
        if (casino == null && kind.casinoOnly) return false;
        int participants = players(kind).size();
        if (participants == 0) return false;
        return switch (kind) {
            case SWAP, HOT_POTATO -> participants >= 2;
            case BLOCK_BAN -> !banCandidates().isEmpty();
            case FRENZY -> !session.shuffles();
            default -> true;
        };
    }

    private void begin(Kind kind) {
        pending.remove(kind);
        if (closed || !eligible(kind)) {
            eventTasks.finish(kind);
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
            later(kind, kind.seconds * 20L, () -> finish(kind, true));
        }
    }

    /** @param natural false when the game is over, so nothing should still happen (no explosion) */
    private void finish(Kind kind, boolean natural) {
        pending.remove(kind);
        eventTasks.finish(kind);
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
            case VANISHING -> vanishing.clear();
            case CONTRACT_AD -> {
                for (Player player : audience(kind)) {
                    player.clearTitle();
                }
            }
            case MATH -> finishMath(natural);
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
            services.messages().broadcast(I18n.tr("pillars.effects.event.finished", kind.title).color(NamedTextColor.GRAY));
        }
    }

    /** At the end of the game: undo everything that would outlive it. */
    void close() {
        if (closed) {
            return;
        }
        closed = true;
        HandlerList.unregisterAll(this);
        ticker.cancel();
        eventTasks.close();
        pending.clear();
        for (Kind kind : List.copyOf(running)) {
            finish(kind, false);
        }
        vanishing.clear();
    }

    private void later(Kind kind, long delay, Runnable action) {
        eventTasks.add(kind, tick + delay, action);
    }

    // Starting each event.

    private void start(Kind kind) {
        List<Player> alive = players(kind);
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
                    player.sendActionBar(I18n.tr(giant ? "pillars.effects.event.giant" : "pillars.effects.event.gnome")
                            .color(NamedTextColor.LIGHT_PURPLE));
                }
            }
            case SPOTLIGHT -> effect(alive, PotionEffectType.GLOWING, 0, effectTicks(kind, kind.seconds * 20));
            case HANGOVER -> {
                effect(alive, PotionEffectType.NAUSEA, 2, effectTicks(kind, kind.seconds * 20));
                effect(alive, PotionEffectType.SLOWNESS, 1, effectTicks(kind, kind.seconds * 20));
                effect(alive, PotionEffectType.WITHER, 0, effectTicks(kind, 20));
            }
            case DARKNESS -> effect(alive, PotionEffectType.DARKNESS, 0, effectTicks(kind, kind.seconds * 20));
            case WIND -> {
                windYaw = ThreadLocalRandom.current().nextFloat() * 360f;
                double radians = Math.toRadians(windYaw);
                wind = new Vector(-Math.sin(radians), 0, Math.cos(radians)).multiply(WIND_PUSH);
                Component towards = I18n.tr("pillars.effects.direction." + COMPASS[Math.floorMod(Math.round(windYaw / 45f), 8)]);
                Title title = Title.title(Kind.WIND.title.color(NamedTextColor.AQUA).decorate(TextDecoration.BOLD),
                        I18n.tr("pillars.effects.wind.instructions", towards).color(NamedTextColor.WHITE));
                showEventTitle(kind, title);
            }
            case SPEED -> effect(alive, PotionEffectType.SPEED, 0, effectTicks(kind, kind.seconds * 20));
            case WEAKNESS -> effect(alive, PotionEffectType.WEAKNESS, 0, effectTicks(kind, kind.seconds * 20));
            case SECOND_WIND -> {
                for (Player player : alive) {
                    double max = player.getAttribute(Attribute.MAX_HEALTH).getValue();
                    player.setHealth(Math.min(max, player.getHealth() + 4));
                }
            }
            case KNOCKBACK -> {
                for (Player player : alive) {
                    ItemStack stick = eventItem(Material.STICK, Kind.KNOCKBACK,
                            I18n.tr("pillars.effects.item.knockback_stick"), NamedTextColor.AQUA);
                    stick.addUnsafeEnchantment(Enchantment.KNOCKBACK, 2);
                    give(player, stick);
                }
            }
            case FRENZY -> session.setItemSpeed(3);
            case AIRDROP -> airdrop();
            case CONTRACT_AD -> {
                adStartedAt = tick;
                showContractAd();
            }
            case MATH -> startMath(alive);
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
        if (closed || session.paused()) {
            return;
        }
        eventTasks.tick(++tick);
        List<Player> alive = session.aliveOnline();
        List<Player> unprotected = alive.stream().filter(player -> !protectedPlayer(player)).toList();
        trackMovement(alive);
        if (running.contains(Kind.CONTRACT_AD) && (tick - adStartedAt) % 5 == 0) {
            showContractAd();
        }
        if (running.contains(Kind.MATH) && tick % 20 == 0) {
            showMathReminder(unprotected);
        }
        if (running.contains(Kind.DISCO)) {
            tickDisco(unprotected);
        }
        if (running.contains(Kind.SUPERHOT)) {
            tickSuperhot(unprotected);
        }
        if (running.contains(Kind.STIFF_NECK)) {
            tickStiffNeck(unprotected);
        }
        if (running.contains(Kind.BOATS) && tick % 10 == 0) {
            dropBoats(alive.size());
        }
        if (running.contains(Kind.WIND) && tick % 10 == 0) {
            tickWind(unprotected);
        }
        if (running.contains(Kind.HOT_POTATO) && tick % 10 == 0) {
            tickPotato(unprotected);
        }
        while (!vanishing.isEmpty() && vanishing.peekFirst().dueTick() <= tick) {
            Vanish expiry = vanishing.removeFirst();
            Block block = expiry.placement().block;
            if (stillPlaced(block, expiry.placement()) && !session.protectsFromEvents(expiry.placement().owner)) {
                block.setType(Material.AIR);
                placed.remove(block);
            }
        }
    }

    // Screen events. Keep the ad visible even when another event announces itself.

    private void showEventTitle(Kind kind, Title title) {
        for (Player player : audience(kind)) {
            if (!running.contains(Kind.CONTRACT_AD) || protectedPlayer(player)) player.showTitle(title);
        }
    }

    private void showContractAd() {
        int frame = (tick - adStartedAt) / 5;
        NamedTextColor color = AD_COLORS[frame % AD_COLORS.length];
        Title title = Title.title(
                I18n.tr(frame % 2 == 0 ? "pillars.effects.ad.title_starred" : "pillars.effects.ad.title")
                        .color(color).decorate(TextDecoration.BOLD),
                I18n.tr("pillars.effects.ad.subtitle").color(AD_COLORS[(frame + 1) % AD_COLORS.length])
                        .decorate(TextDecoration.BOLD),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(300), Duration.ZERO));
        for (Player player : audience(Kind.CONTRACT_AD)) {
            player.showTitle(title);
        }
    }

    private void startMath(List<Player> alive) {
        MathChallenge challenge = new MathChallenge(MathChallenge.Problem.random(ThreadLocalRandom.current()),
                alive.stream().map(Player::getUniqueId).toList());
        mathEndsAt = tick + Kind.MATH.seconds * 20;
        mathChallenge = challenge;
        Component question = I18n.tr("pillars.effects.math.question", challenge.problem().expression())
                .color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD);
        showEventTitle(Kind.MATH, Title.title(question,
                I18n.tr("pillars.effects.math.instructions").color(NamedTextColor.WHITE),
                Title.Times.times(Duration.ZERO, Duration.ofSeconds(3), Duration.ofMillis(250))));
        for (Player player : alive) {
            player.sendMessage(I18n.tr("pillars.effects.math.announcement", question)
                    .color(NamedTextColor.WHITE));
        }
        showMathReminder(alive);
    }

    private void showMathReminder(List<Player> alive) {
        MathChallenge challenge = mathChallenge;
        if (challenge == null) {
            return;
        }
        int seconds = Math.max(1, (mathEndsAt - tick + 19) / 20);
        for (Player player : alive) {
            if (challenge.isWaiting(player.getUniqueId())) {
                player.sendActionBar(I18n.tr("pillars.effects.math.reminder", challenge.problem().expression(), seconds)
                        .color(NamedTextColor.YELLOW));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        MathChallenge expected = mathChallenge;
        if (expected == null) {
            return;
        }
        UUID playerId = event.getPlayer().getUniqueId();
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        // Never inspect session collections or touch players from the asynchronous chat thread.
        Bukkit.getScheduler().runTask(services.plugin(), () -> {
            if (closed || session.paused() || mathChallenge != expected || tick >= mathEndsAt) {
                return;
            }
            Player player = session.aliveOnline().stream()
                    .filter(candidate -> candidate.getUniqueId().equals(playerId)).findFirst().orElse(null);
            if (player == null) {
                return;
            }
            switch (expected.answer(playerId, message)) {
                case CORRECT -> player.sendMessage(I18n.tr("pillars.effects.math.correct").color(NamedTextColor.GREEN));
                case WRONG -> player.sendMessage(I18n.tr("pillars.effects.math.wrong").color(NamedTextColor.RED));
                default -> { }
            }
        });
    }

    private void finishMath(boolean natural) {
        MathChallenge challenge = mathChallenge;
        mathChallenge = null;
        if (challenge == null) {
            return;
        }
        Set<UUID> failures = challenge.finish();
        if (!natural) {
            return;
        }
        for (Player player : session.aliveOnline()) {
            if (closed) {
                break;
            }
            if (!protectedPlayer(player) && failures.contains(player.getUniqueId())) {
                player.sendMessage(I18n.tr("pillars.effects.math.timeout", challenge.problem().expression(),
                        challenge.problem().result()).color(NamedTextColor.RED));
                player.sendActionBar(Component.empty());
                player.damage(MATH_DAMAGE);
            }
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
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, effectTicks(Kind.DISCO, 60), 0,
                    false, false, false));
            player.sendActionBar(I18n.tr("pillars.effects.disco.keep_moving", hits).color(NamedTextColor.RED));
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
                        ? I18n.tr("pillars.effects.superhot.frozen").color(NamedTextColor.RED)
                        : I18n.tr("pillars.effects.superhot.moving").color(NamedTextColor.WHITE));
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

    /** SUPERHOT must not return items from a previous shuffle when the player moves again. */
    void onItemShuffle() {
        frozen.clear();
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
            player.sendActionBar(I18n.tr(holding ? "pillars.effects.wind.holding" : "pillars.effects.wind.moving", arrow)
                    .color(NamedTextColor.AQUA));
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
        services.messages().broadcast(I18n.tr("pillars.effects.swap.players",
                String.join(" → ", chosen.stream().map(Player::getName).toList()) + " → "
                        + chosen.getFirst().getName()).color(NamedTextColor.LIGHT_PURPLE));
    }

    // Hot potato.

    private void handPotato(Player holder) {
        potatoHolder = holder.getUniqueId();
        potatoPassedAt = tick;
        give(holder, eventItem(Material.TNT, Kind.HOT_POTATO, Kind.HOT_POTATO.title, NamedTextColor.RED));
        holder.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, effectTicks(Kind.HOT_POTATO, POTATO_TICKS),
                0, false, false, false));
        holder.playSound(holder.getLocation(), Sound.ENTITY_TNT_PRIMED, 1f, 1.2f);
    }

    private void tickPotato(List<Player> alive) {
        Player holder = potatoHolder == null ? null : Bukkit.getPlayer(potatoHolder);
        if (holder == null || !alive.contains(holder)) {
            passPotatoAway(holder);
            return;
        }
        int left = Math.max(0, (potatoEndsAt - tick + 19) / 20);
        Component bar = I18n.tr("pillars.effects.hot_potato.holder", holder.name(), left).color(NamedTextColor.RED);
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
        List<Player> others = new ArrayList<>(players(Kind.HOT_POTATO));
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
            if (natural && players(Kind.HOT_POTATO).contains(holder)) {
                potatoExplosion = true;
                try {
                    holder.getWorld().createExplosion(holder, holder.getLocation(), 3.0f, false, true);
                } finally {
                    potatoExplosion = false;
                }
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
        services.messages().broadcast(I18n.tr("pillars.effects.airdrop.appeared", block.getY() - session.pillarTop())
                .color(NamedTextColor.GOLD));
    }

    // Block ban.

    /** Placed types still on the map, by weight; the pillars' own material is rarely picked. */
    private Map<Material, Double> banCandidates() {
        Map<Material, Double> weights = new LinkedHashMap<>();
        placed.entrySet().removeIf(entry -> entry.getKey().getType() != entry.getValue().material);
        for (Placement placement : placed.values()) {
            if (!session.protectsFromEvents(placement.owner)) weights.put(placement.material, 1.0);
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
            later(Kind.BLOCK_BAN, at, () -> {
                Material shown = names.get(ThreadLocalRandom.current().nextInt(names.size()));
                if (names.size() > 1 && shown == last[0]) {
                    shown = names.get((names.indexOf(shown) + 1) % names.size());
                }
                last[0] = shown;
                showBanName(pretty(shown).color(NamedTextColor.YELLOW), 15);
                for (Player player : audience()) {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.0f + index * 0.03f);
                }
            });
            at += 2 + step / 3;
            step++;
        }
        Material chosen = pick(candidates);
        later(Kind.BLOCK_BAN, WARNING_TICKS, () -> {
            showBanName(I18n.tr("pillars.effects.block_ban.selected", pretty(chosen))
                    .color(NamedTextColor.RED).decorate(TextDecoration.BOLD), 45);
            for (Player player : audience()) {
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.6f, 1.0f);
            }
        });
        later(Kind.BLOCK_BAN, WARNING_TICKS + 40, () -> {
            int removed = 0;
            for (var entry : List.copyOf(placed.entrySet())) {
                if (entry.getValue().material == chosen && entry.getKey().getType() == chosen
                        && !session.protectsFromEvents(entry.getValue().owner)) {
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
            services.messages().broadcast(I18n.tr("pillars.effects.block_ban.removed", pretty(chosen), removed)
                    .color(NamedTextColor.RED));
            finish(Kind.BLOCK_BAN, true);
        });
    }

    /** A subtitle only, so it does not cover the view. */
    private void showBanName(Component name, int stayTicks) {
        Title title = Title.title(Component.empty(), name,
                Title.Times.times(Duration.ZERO, Duration.ofMillis(stayTicks * 50L), Duration.ofMillis(150)));
        showEventTitle(Kind.BLOCK_BAN, title);
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

    private static Component pretty(Material material) {
        return Component.translatable(material.translationKey());
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
        Placement placement = new Placement(block, event.getPlayer().getUniqueId());
        placed.put(block, placement);
        if (running.contains(Kind.SLIPPERY)) {
            later(Kind.SLIPPERY, 1L, () -> {
                if (stillPlaced(placement.block, placement) && !session.protectsFromEvents(placement.owner)) {
                    placement.block.setType(Material.PACKED_ICE);
                    placement.material = Material.PACKED_ICE;
                }
            });
        }
        if (running.contains(Kind.VANISHING) && !protectedPlayer(event.getPlayer())) {
            vanishing.addLast(new Vanish(tick + VANISH_TICKS, placement));
        }
    }

    void onTranspose(PerkTerrain.Pos center, Set<PerkTerrain.Pos> changed) {
        var moved = new ArrayList<Placement>();
        var iterator = placed.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var pos = PerkTerrain.Pos.of(entry.getKey());
            if (!changed.contains(pos)) continue;
            var destination = PerkProtractor.transpose(pos, center);
            Placement placement = entry.getValue();
            placement.block = session.world().getBlockAt(destination.x(), destination.y(), destination.z());
            moved.add(placement);
            iterator.remove();
        }
        for (Placement placement : moved) placed.put(placement.block, placement);
    }

    private boolean stillPlaced(Block block, Placement placement) {
        return placed.get(block) == placement && block.getType() == placement.material;
    }

    void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (closed || !running.contains(Kind.HOT_POTATO) || potatoHolder == null) {
            return;
        }
        if (!(event.getDamager() instanceof Player hitter) || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        List<Player> alive = players(Kind.HOT_POTATO);
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
        if (mathChallenge != null) {
            mathChallenge.remove(player.getUniqueId());
        }
        if (running.contains(Kind.HOT_POTATO) && player.getUniqueId().equals(potatoHolder)) {
            passPotatoAway(player);
        }
    }

    void onProtection(Player player) {
        UUID id = player.getUniqueId();
        unfreeze(player);
        lockedYaw.remove(id);
        discoHits.remove(id);
        discoPunishedAt.remove(id);
        if (running.contains(Kind.CONTRACT_AD)) player.clearTitle();
        var scale = player.getAttribute(Attribute.SCALE);
        if (scale != null) scale.removeModifier(scaleKey);
        if (mathChallenge != null) mathChallenge.remove(id);
        if (id.equals(potatoHolder)) passPotatoAway(player);
    }

    void restoreFrozenItems(Player player) {
        unfreeze(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPotatoDamage(EntityDamageEvent event) {
        if (potatoExplosion && event.getEntity() instanceof Player player && protectedPlayer(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPotatoExplosion(EntityExplodeEvent event) {
        if (!potatoExplosion) return;
        event.blockList().removeIf(block -> {
            Placement placement = placed.get(block);
            return placement != null && stillPlaced(block, placement) && session.protectsFromEvents(placement.owner)
                    || session.isProtectedPillarBlock(block);
        });
    }

    private boolean protectedPlayer(Player player) {
        return session.protectsFromEvents(player.getUniqueId());
    }

    private List<Player> players(Kind kind) {
        List<Player> alive = session.aliveOnline();
        return kind.good ? alive : alive.stream().filter(player -> !protectedPlayer(player)).toList();
    }

    private List<Player> audience(Kind kind) {
        List<Player> players = audience();
        return kind.good ? players : players.stream().filter(player -> !protectedPlayer(player)).toList();
    }

    // Helpers.

    private List<Player> audience() {
        return services.session().audiencePlayers();
    }

    private int effectTicks(Kind kind, int normalTicks) {
        return eventTasks.isBurst(kind) ? (int) Math.max(0, Math.min(normalTicks, burstEndsAt - tick)) : normalTicks;
    }

    /** A warning, its effect and its callbacks belong to the same event lifetime. */
    static final class EventTasks<K> {
        private record Pending<K>(K kind, long due, long order, Runnable action) { }
        private final java.util.PriorityQueue<Pending<K>> tasks = new java.util.PriorityQueue<>(
                java.util.Comparator.<Pending<K>>comparingLong(Pending::due).thenComparingLong(Pending::order));
        private final Set<K> burstEvents = new HashSet<>();
        private boolean burst;
        private long nextOrder;

        void startBurst() { burst = true; }
        void selected(K kind) { if (burst) burstEvents.add(kind); }
        boolean isBurst(K kind) { return burstEvents.contains(kind); }
        void add(K kind, long due, Runnable action) { tasks.add(new Pending<>(kind, due, nextOrder++, action)); }

        void tick(long now) {
            while (!tasks.isEmpty() && tasks.peek().due() <= now) tasks.remove().action().run();
        }

        Set<K> stopBurst() {
            burst = false;
            return Set.copyOf(burstEvents);
        }

        void finish(K kind) {
            burstEvents.remove(kind);
            tasks.removeIf(task -> task.kind().equals(kind));
        }

        void close() {
            tasks.clear();
            burstEvents.clear();
            burst = false;
        }
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

    private ItemStack eventItem(Material material, Kind kind, Component name, NamedTextColor color) {
        ItemStack stack = new ItemStack(material);
        stack.editMeta(meta -> {
            meta.displayName(name.color(color).decoration(TextDecoration.ITALIC, false));
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
        for (ItemStack left : player.getInventory().addItem(I18n.item(stack.clone(), player)).values()) {
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
