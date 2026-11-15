package me.petr1furious.potatominigames.games.party;

import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.GameMode;
import org.bukkit.ExplosionResult;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.Action;
import org.bukkit.block.BlockFace;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

/** Exercise real generated geometry/mutations, loot draws and match rules without a server. */
public final class MinersCheck {
    public static void main(String[] args) throws ReflectiveOperationException {
        integration(); quotas(); geometry(); loot(); clock(); zone(); gold(); drops(); visibility(); explosions(); drillInteraction(); MinersPerksCheck.run();
        System.out.println("Party Miners: menu/queue/config, fair private cells and sliced walls, random physical loot pools/ammo, finite gold health, phase protection and bounded ground drops passed");
    }
    private static void integration() {
        PartyGame game = new PartyGame((PartyConfig) null);
        check(game.id().equals("party") && game.modes().containsAll(List.of("pvp", "sumo", "arms_race", "tnt_run", "mob_count", "rhino", "miners")),
                "Miners is another Party mode; all upstream modes remain available");
        check(game.createSession(null, "miners") instanceof MinersSession && game.maxPlayers("miners") == 12, "The miners queue creates its own FFA session with twelve slots");
        check(game.icon("miners") == Material.DIAMOND_PICKAXE, "Menu exposes a pickaxe icon");
        var queueStream = MinersCheck.class.getResourceAsStream("/config.yml");
        var timingsStream = MinersCheck.class.getResourceAsStream("/games/party.yml");
        check(queueStream != null && timingsStream != null, "Shipped configuration resources exist");
        var queues = YamlConfiguration.loadConfiguration(new InputStreamReader(queueStream, StandardCharsets.UTF_8));
        var timings = YamlConfiguration.loadConfiguration(new InputStreamReader(timingsStream, StandardCharsets.UTF_8));
        check(queues.getBoolean("queues.party.miners.enabled") && queues.getInt("queues.party.miners.minPlayers") == 2
                && queues.getInt("queues.party.miners.maxPlayers") == 12, "Party miners has an enabled public queue");
        check(timings.getInt("miners.preparationSeconds") == 25 && timings.getInt("miners.combatSeconds") == 180
                && timings.getInt("miners.finaleSeconds") == 120, "Defaults give 25-second mining, three-minute combat and a two-minute finale");
        check(timings.getInt("partyGames.minersPreparationSeconds") == 25
                && timings.getInt("partyGames.minersCombatSeconds") == 120
                && timings.getInt("partyGames.minersFinaleSeconds") == 60,
                "Mixed Party Games uses 25-second mining, 120-second combat and a 60-second finale");
        var saved = new YamlConfiguration();
        saved.set("miners.preparationSeconds", 15);
        saved.set("miners.finaleSeconds", 60);
        saved.set("partyGames.minersPreparationSeconds", 40);
        saved.set("partyGames.minersCombatSeconds", 90);
        saved.set("partyGames.minersFinaleSeconds", 30);
        check(PartyConfig.migrateMinersTimings(saved)
                && saved.getInt("miners.preparationSeconds") == 25
                && saved.getInt("miners.finaleSeconds") == 120
                && saved.getInt("partyGames.minersPreparationSeconds") == 40
                && saved.getInt("partyGames.minersCombatSeconds") == 120
                && saved.getInt("partyGames.minersFinaleSeconds") == 60,
                "Existing defaults upgrade while custom mining durations are preserved");
        saved.set("miners.finaleSeconds", 60);
        check(!PartyConfig.migrateMinersTimings(saved) && saved.getInt("miners.finaleSeconds") == 60,
                "An administrator can restore old timings after the one-time upgrade");
    }
    private static void quotas() {
        for (int players = 1; players <= 12; players++) {
            FakeWorld world = new FakeWorld();
            MinersArena arena = new MinersArena(world.world, players, new Random(players));
            Set<String> starts = new HashSet<>();
            for (int cell = 0; cell < players; cell++) {
                for (MinersOre ore : MinersOre.values()) check(arena.oreCount(cell, ore) == ore.perCell, "Every private cell has the exact same ore quotas");
                Location at = arena.spawn(cell);
                check(arena.owns(at, cell) && !arena.owns(at, (cell + 1) % (players + 1)), "Spawns belong only to their assigned cell");
                starts.add(at.getX() + ":" + at.getZ());
            }
            check(starts.size() == players, "Every participant gets a distinct personal mining box");
        }
        FakeWorld world = new FakeWorld();
        MinersArena first = new MinersArena(world.world, 1, new Random(1)), second = new MinersArena(world.world, 1, new Random(2));
        boolean different = false;
        for (int x = 1; x <= 16; x++) for (int y = 64; y < 80; y++) for (int z = 1; z <= 16; z++)
            different |= first.generated(x, y, z) != second.generated(x, y, z);
        check(different, "Different seeds shuffle ore locations instead of replaying a fixed layout");
    }
    private static void geometry() {
        FakeWorld world = new FakeWorld();
        MinersArena arena = new MinersArena(world.world, 5, new Random(42));
        boolean built = false;
        while (!built) {
            int before = world.writes;
            built = arena.buildSlice(257);
            check(world.writes - before <= 257, "Arena construction respects its per-tick block budget");
        }
        check(arena.progress() == 1, "The build reports completion only after every block");
        for (int cell = 0; cell < 5; cell++) {
            Location spawn = arena.spawn(cell);
            check(world.at(spawn.getBlockX(), spawn.getBlockY(), spawn.getBlockZ()) == Material.AIR
                    && world.at(spawn.getBlockX(), spawn.getBlockY() + 1, spawn.getBlockZ()) == Material.AIR,
                    "Every spawn has clear feet/head space");
            check(world.at(spawn.getBlockX(), spawn.getBlockY() - 1, spawn.getBlockZ()) != Material.AIR, "Every spawn has solid support");
            for (MinersOre ore : MinersOre.values()) check(arena.oreCount(cell, ore) == ore.perCell, "Spawn cavities never overwrite ore rewards");
        }
        Location partition = new Location(world.world, 17.5, 72, 8.5);
        check(!arena.contains(partition) && world.at(17, 72, 8) == Material.BEDROCK, "Private boxes are separated by protected solid walls");
        check(arena.chargeFits(arena.spawn(0)), "TNT can be thrown inside the initial cavity");
        check(!arena.chargeFits(new Location(world.world, 7.1, 72, 8.5)), "TNT cannot be created through the side of a cave wall");
        check(!arena.chargeFits(new Location(world.world, 8.5, 74.5, 8.5)), "A TNT charge needs enough headroom, not just an air centre");
        boolean opened = false;
        while (!opened) {
            int before = world.writes; opened = arena.openSlice(113);
            check(world.writes - before <= 113, "Opening partitions also respects its per-tick budget");
        }
        check(arena.contains(partition) && arena.editable(world.block(17, 72, 8))
                && world.at(17, 72, 8) == Material.DIORITE,
                "PvP turns shared bedrock walls into mineable diorite that still marks cell boundaries");
        check(world.at(34, 72, 25) == Material.BEDROCK && !arena.contains(new Location(world.world, 42.5, 72, 25.5)),
                "Unused grid cells remain inaccessible instead of containing extra ore");
        check(!arena.contains(new Location(world.world, 0, 72, 8)) && !arena.contains(new Location(world.world, 8, 63, 8))
                && !arena.contains(new Location(world.world, 8, 80, 8)), "Outer walls, floor and roof remain protected after opening");
        int consumed = 0;
        for (int x = 1; x <= 16; x++) for (int y = 64; y < 80; y++) for (int z = 1; z <= 16; z++) {
            Block block = world.block(x, y, z);
            MinersOre ore = arena.takeOre(block);
            if (ore != null) {
                consumed++;
                check(arena.takeOre(block) == null, "One generated ore position cannot reward twice even before the block is replaced");
                block.setType(Material.COBBLESTONE, false);
                check(arena.takeOre(block) == null, "Placed building blocks never recreate ore rewards");
            }
        }
        check(consumed == java.util.Arrays.stream(MinersOre.values()).mapToInt(ore -> ore.perCell).sum(), "All generated ores have exactly one reward provenance entry");
    }
    private static void loot() {
        int[] tiers = new int[3];
        for (int value = 0; value < 100; value++) {
            int draw = value;
            var q = MinersLoot.quality(new Random(0) { @Override public int nextInt(int bound) { check(bound == 100, "Rarity draw uses the full percentage range"); return draw; } });
            tiers[q.ordinal()]++;
        }
        check(tiers[0] == 70 && tiers[1] == 25 && tiers[2] == 5, "Default rarities are exactly 70/25/5 without unreachable grades");
        Random random = new Random(351);
        for (MinersOre ore : MinersOre.values()) {
            Set<MinersLoot.Drop> variants = new HashSet<>();
            for (int i = 0; i < 4000; i++) {
                var result = MinersLoot.roll(ore, random);
                if (ore == MinersOre.GOLD || ore == MinersOre.PERKS) { check(result.isEmpty(), "Gold and perk blocks use their own rewards instead of ordinary loot"); continue; }
                check(!result.isEmpty(), "Every other ore produces physical loot");
                variants.addAll(result);
                for (var drop : result) {
                    check(drop.amount() > 0 && drop.amount() <= 64, "Every loot stack has a valid amount");
                    check(drop.level() >= 0 && drop.level() <= ("efficiency".equals(drop.enchant()) ? 5 : 2), "Combat enchantments stay bounded while mining tools can reach Efficiency V");
                    if (drop.potion() != null) check(drop.amount() <= 8 && drop.potion() != PvpPotion.INVISIBILITY, "Potions fit their stack limit and keep the intended combat pool");
                    if (drop.material() == Material.BOW || drop.material() == Material.CROSSBOW)
                        check(result.stream().anyMatch(ammo -> ammo.material() == Material.ARROW && ammo.amount() >= 32), "Every ranged weapon comes with usable ammunition");
                    if (ore == MinersOre.EMERALD) check(drop.quality() != MinersLoot.Quality.COMMON, "Emeralds draw from the stronger loot grades");
                }
            }
            if (ore != MinersOre.GOLD && ore != MinersOre.PERKS) check(variants.size() > 3, "Each ore has varied rewards instead of a deterministic item sequence");
        }
    }
    private static void clock() {
        MinersRound round = new MinersRound(5, 120, 180, 60);
        round.advance(100, true);
        check(round.phase() == MinersRound.Phase.BUILDING && !round.mining(), "Players cannot mine before the arena is ready");
        round.ready(100); round.advance(199, false);
        check(round.phase() == MinersRound.Phase.COUNTDOWN && !round.mining(), "Countdown freezes gameplay for the full duration");
        round.advance(200, false);
        check(round.phase() == MinersRound.Phase.MINING && round.mining() && !round.fighting(), "The private preparation phase enables mining but no PvP");
        round.advance(2600, false);
        check(round.phase() == MinersRound.Phase.MINING, "PvP waits until sliced partition removal is complete");
        round.advance(2601, true);
        check(round.phase() == MinersRound.Phase.FIGHT && round.fighting(), "Opening boxes starts the combat phase exactly once");
        round.advance(6201, true);
        check(round.phase() == MinersRound.Phase.FINALE && round.pressure(7400) == 2, "The storm damages outside players during the shrinking minute");
        check(round.pressure(7401) == 2 && round.pressure(7501) == 4, "Outside damage increases after shrinking stops");
        round.result(7502); round.advance(10000, true);
        check(round.phase() == MinersRound.Phase.RESULT && !round.mining() && round.deadline() == 7562, "Results hold combat for three seconds without resuming another phase");
        round.done(); round.ready(20000);
        check(round.phase() == MinersRound.Phase.DONE, "An ended round cannot restart through a delayed build callback");
    }
    private static void zone() {
        for (int players = 1; players <= 12; players++) {
            FakeWorld world = new FakeWorld();
            MinersArena arena = new MinersArena(world.world, players, new Random(players));
            Location center = arena.center();
            double size = arena.borderSize();
            double last = size;
            for (long tick : new long[] {0, 1, 600, 1199, 1200, 100000}) {
                var bounds = MinersZone.bounds(center.getX(), center.getZ(), size, tick, 1200);
                check(bounds.size() <= last && bounds.size() >= 4, "The storm shrinks monotonically and never below 4 by 4");
                check(!bounds.outside(center.getX(), center.getZ()), "The safe centre never takes zone damage, even after the final minute");
                double edge = bounds.size() / 2;
                check(!bounds.outside(center.getX() + edge, center.getZ())
                        && bounds.outside(center.getX() + edge + .001, center.getZ())
                        && bounds.outside(center.getX() - edge - .001, center.getZ())
                        && bounds.outside(center.getX(), center.getZ() + edge + .001)
                        && bounds.outside(center.getX(), center.getZ() - edge - .001),
                        "Damage starts only past the visible boundary on all four sides");
                if (tick >= 1200) check(bounds.size() == 4, "The final 4 by 4 area remains stable indefinitely");
                last = bounds.size();
            }
            while (!arena.openSlice(4096)) { }
            Location target = new Location(world.world, 1.5, MinersArena.BASE, 1.5);
            var finalBounds = MinersZone.bounds(center.getX(), center.getZ(), size, 1200, 1200);
            check(finalBounds.outside(target.getX(), target.getZ())
                    && arena.editable(world.block(target.getBlockX(), MinersArena.BASE, target.getBlockZ())),
                    "Blocks beyond the final storm remain inside the editable mining arena");
        }
    }
    private static void gold() {
        int[] rolls = new int[3];
        for (int i = 0; i < 100; i++) {
            int draw = i;
            rolls[MinersGold.roll(new Random(0) {
                @Override public int nextInt(int bound) { check(bound == 100, "Gold uses percent odds"); return draw; }
            })]++;
        }
        check(rolls[1] == 80 && rolls[2] == 20, "Gold gives one heart 80% and two hearts 20% of the time");
        MinersGold gold = new MinersGold(); UUID a = new UUID(0, 1), b = new UUID(0, 2);
        Random single = new Random(0) { @Override public int nextInt(int bound) { return 99; } };
        Random doubleHeart = new Random(0) { @Override public int nextInt(int bound) { return 0; } };
        for (int i = 1; i <= 3; i++)
            check(gold.collect(a, single) == 1 && gold.bonus(a) == i, "Each ore immediately adds a heart");
        check(gold.bonus(b) == 0, "Gold bonuses are personal");
        check(gold.collect(a, doubleHeart) == 1 && gold.bonus(a) == 4, "A lucky roll cannot exceed fourteen hearts");
        check(gold.collect(a, new Random(0) {
            @Override public int nextInt(int bound) { throw new AssertionError("Capped gold must not roll or heal"); }
        }) == 0, "Extra gold at the cap cannot become repeatable healing");
        check(gold.collect(b, doubleHeart) == 2 && gold.bonus(b) == 2, "Lucky ore immediately grants two hearts");
        gold.clear(); check(gold.bonus(a) == 0 && gold.bonus(b) == 0, "Ending clears all bonuses");
    }
    private static void drops() {
        MinersDrops drops = new MinersDrops(); List<FakeItem> all = new ArrayList<>();
        for (int i = 0; i < 300; i++) { FakeItem item = new FakeItem(i); all.add(item); drops.track(item.item); }
        for (int i = 0; i < 300; i++) check(all.get(i).removed == (i < 44 ? 1 : 0), "Ground loot evicts oldest abandoned stacks at a bounded count");
        all.get(299).valid = false; drops.prune(); drops.clear(); drops.clear();
        for (int i = 0; i < 299; i++) check(all.get(i).removed == 1, "Every live loot entity is removed exactly once on stop");
        check(all.get(299).removed == 0, "Already picked-up/despawned items are not removed a second time");
    }
    private static void visibility() {
        boolean[] glowing = {false};
        UUID id = new UUID(3, 1);
        Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "isSneaking" -> true;
                    case "isGlowing" -> glowing[0];
                    case "setGlowing" -> { glowing[0] = (boolean) args[0]; yield null; }
                    default -> throw new AssertionError("Unexpected reveal call: " + method.getName());
                });
        MinersVisibility visibility = new MinersVisibility();
        visibility.reveal(List.of(player));
        check(player.isSneaking() && glowing[0], "Finale outline applies to a sneaking opponent");
        glowing[0] = false;
        visibility.reveal(List.of(player));
        check(glowing[0], "Finale refresh restores the outline independently of potion state");
        visibility.stopReveal(player);
        check(!glowing[0], "Elimination releases the forced outline");
        visibility.reveal(List.of(player)); visibility.clear();
        check(!glowing[0], "Round cleanup releases forced outlines");
        glowing[0] = true;
        visibility.reveal(List.of(player)); visibility.clear();
        check(glowing[0], "Cleanup preserves an outline that existed before the finale");
    }

    @SuppressWarnings("unchecked")
    private static void explosions() throws ReflectiveOperationException {
        FakeWorld world = new FakeWorld();
        MinersArena arena = new MinersArena(world.world, 2, new Random(42));
        while (!arena.buildSlice(4096)) { }
        while (!arena.openSlice(4096)) { }
        UUID ownerId = new UUID(4, 1), bombId = new UUID(4, 2);
        boolean[] dead = {false};
        Player owner = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> ownerId;
                    case "isDead" -> dead[0];
                    case "getGameMode" -> GameMode.SURVIVAL;
                    default -> throw new AssertionError("Unexpected blast owner call: " + method.getName());
                });
        TNTPrimed tnt = (TNTPrimed) Proxy.newProxyInstance(TNTPrimed.class.getClassLoader(), new Class<?>[] {TNTPrimed.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> bombId;
                    case "getWorld" -> world.world;
                    case "isValid" -> false; // Native TNT is discarded before its blast callbacks.
                    default -> throw new AssertionError("Unexpected TNT call: " + method.getName());
                });
        MinersSession session = new MinersSession(null, null);
        MinersRound round = new MinersRound(0, 0, 30, 15);
        round.ready(0); round.advance(0, true);
        SurvivalStandings standings = new SurvivalStandings(List.of(ownerId, new UUID(4, 3)));
        minerField("world").set(session, world.world); minerField("arena").set(session, arena);
        minerField("round").set(session, round); minerField("standings").set(session, standings);
        ((Set<UUID>) minerField("connected").get(session)).add(ownerId);
        Block web = world.block(8, 72, 8), shell = world.block(0, 72, 8);
        web.setType(Material.COBWEB, false);
        EntityExplodeEvent foreign = blast(tnt, web, shell);
        session.onEntityExplode(foreign);
        check(foreign.blockList().isEmpty() && web.getType() == Material.COBWEB, "Untracked explosions cannot mine the arena");
        ((PvpBombs) minerField("bombs").get(session)).track(owner, tnt);
        EntityExplodeEvent preparation = blast(tnt, web, shell);
        session.onEntityExplode(preparation);
        check(preparation.blockList().isEmpty() && web.getType() == Material.COBWEB, "Preparation blocks tracked TNT mining too");
        round.advance(0, true);
        EntityExplodeEvent combat = blast(tnt, web, shell);
        session.onEntityExplode(combat);
        check(web.getType() == Material.AIR && shell.getType() == Material.BEDROCK,
                "Tracked combat TNT mines editable blocks but protects the shell");
        check(combat.blockList().isEmpty() && combat.getYield() == 0, "Manual mining suppresses duplicate native block drops");
        var orePos = world.blocks.entrySet().stream().filter(e -> e.getValue() == Material.COAL_ORE).findFirst().orElseThrow().getKey();
        Block ore = world.block(orePos.x(), orePos.y(), orePos.z()), partition = world.block(17, 72, 8);
        check(arena.oreAt(ore) == MinersOre.COAL && partition.getType() == Material.DIORITE, "Blast fixture has real ore and an opened divider");
        minerField("tick").set(session, 7L); standings.eliminate(ownerId, 7); dead[0] = true;
        session.onEntityExplode(blast(tnt, ore, partition, shell));
        check(ore.getType() == Material.AIR && partition.getType() == Material.AIR && shell.getType() == Material.BEDROCK,
                "A lethal final blast still breaks ore and diorite without opening the outer shell");
        ore.setType(Material.COAL_ORE, false);
        check(arena.oreAt(ore) == null, "Destroyed ore provenance cannot reward again after replacement");
        web.setType(Material.COBWEB, false); minerField("tick").set(session, 8L);
        session.onEntityExplode(blast(tnt, web));
        check(web.getType() == Material.COBWEB, "An eliminated owner's stale charge cannot mine on a later tick");
    }

    private static java.lang.reflect.Field minerField(String name) throws ReflectiveOperationException {
        var field = MinersSession.class.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static EntityExplodeEvent blast(TNTPrimed tnt, Block... blocks) {
        return new EntityExplodeEvent(tnt, blocks[0].getLocation(), new ArrayList<>(List.of(blocks)), 1, ExplosionResult.DESTROY);
    }

    @SuppressWarnings("unchecked")
    private static void drillInteraction() throws ReflectiveOperationException {
        UUID id = new UUID(5, 1);
        Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "isDead" -> false;
                    case "getGameMode" -> GameMode.SURVIVAL;
                    default -> throw new AssertionError("Unexpected drill interaction call: " + method.getName());
                });
        PersistentDataContainer data = (PersistentDataContainer) Proxy.newProxyInstance(
                PersistentDataContainer.class.getClassLoader(), new Class<?>[] {PersistentDataContainer.class},
                (proxy, method, args) -> {
                    if (!method.getName().equals("get")) throw new AssertionError("Unexpected drill data call: " + method.getName());
                    return switch (((org.bukkit.NamespacedKey) args[0]).getKey()) {
                        case "miners_perk" -> "DRILL";
                        case "miners_perk_charges" -> 3;
                        default -> null;
                    };
                });
        ItemMeta meta = (ItemMeta) Proxy.newProxyInstance(ItemMeta.class.getClassLoader(), new Class<?>[] {ItemMeta.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getPersistentDataContainer")) return data;
                    throw new AssertionError("Unexpected drill metadata call: " + method.getName());
                });
        ItemStack drill = new ItemStack() {
            @Override public Material getType() { return Material.DIAMOND_PICKAXE; }
            @Override public boolean hasItemMeta() { return true; }
            @Override public ItemMeta getItemMeta() { return meta; }
        };
        MinersSession session = new MinersSession(null, null);
        MinersRound round = new MinersRound(0, 15, 30, 15); round.ready(0);
        minerField("round").set(session, round);
        minerField("standings").set(session, new SurvivalStandings(List.of(id, new UUID(5, 2))));
        ((Set<UUID>) minerField("connected").get(session)).add(id);
        Block block = new FakeWorld().block(8, 72, 8);
        PlayerInteractEvent countdown = new PlayerInteractEvent(player, Action.LEFT_CLICK_BLOCK, drill, block, BlockFace.UP, EquipmentSlot.HAND);
        session.onPlayerInteract(countdown);
        check(countdown.isCancelled(), "Drill mining remains locked during countdown");
        round.advance(0, false);
        PlayerInteractEvent mining = new PlayerInteractEvent(player, Action.LEFT_CLICK_BLOCK, drill, block, BlockFace.UP, EquipmentSlot.HAND);
        session.onPlayerInteract(mining);
        check(!mining.isCancelled() && MinersPerk.charges(drill) == 3, "Drill left-click must reach native mining without spending a charge early");
        PlayerInteractEvent right = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, drill, block, BlockFace.UP, EquipmentSlot.HAND);
        session.onPlayerInteract(right);
        check(right.isCancelled(), "Drill right-click cannot activate a second mining path");
    }

    private static final class FakeItem {
        boolean valid = true; int removed; final Item item;
        FakeItem(int id) {
            item = (Item) Proxy.newProxyInstance(Item.class.getClassLoader(), new Class<?>[] {Item.class}, (p, m, a) -> switch (m.getName()) {
                case "getUniqueId" -> new UUID(1, id);
                case "isValid" -> valid;
                case "remove" -> { valid = false; removed++; yield null; }
                default -> throw new AssertionError("Unexpected item call: " + m.getName());
            });
        }
    }
    private static final class FakeWorld {
        final Map<MinersArena.Pos, Material> blocks = new HashMap<>(); int writes;
        final World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class}, (p, m, a) -> switch (m.getName()) {
            case "getBlockAt" -> block((int) a[0], (int) a[1], (int) a[2]);
            case "equals" -> p == a[0];
            case "hashCode" -> System.identityHashCode(p);
            default -> throw new AssertionError("Unexpected world call: " + m.getName());
        });
        Material at(int x, int y, int z) { return blocks.getOrDefault(new MinersArena.Pos(x, y, z), Material.AIR); }
        Block block(int x, int y, int z) {
            return (Block) Proxy.newProxyInstance(Block.class.getClassLoader(), new Class<?>[] {Block.class}, (p, m, a) -> switch (m.getName()) {
                case "getX" -> x; case "getY" -> y; case "getZ" -> z;
                case "getType" -> at(x, y, z);
                case "getLocation" -> new Location(world, x, y, z);
                case "setType" -> { check(a.length == 2 && !(boolean) a[1], "Generation/mining does not schedule block physics"); blocks.put(new MinersArena.Pos(x, y, z), (Material) a[0]); writes++; yield null; }
                default -> throw new AssertionError("Unexpected block call: " + m.getName());
            });
        }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
