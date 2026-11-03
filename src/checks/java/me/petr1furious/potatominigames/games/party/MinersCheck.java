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
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;

/** Exercise real generated geometry/mutations, loot draws and match rules without a server. */
public final class MinersCheck {
    public static void main(String[] args) {
        integration(); quotas(); geometry(); loot(); clock(); gold(); drops();
        System.out.println("Party Miners: menu/queue/config, fair private cells and sliced walls, random physical loot pools/ammo, finite gold health, phase protection and bounded ground drops passed");
    }
    private static void integration() {
        PartyGame game = new PartyGame((PartyConfig) null);
        check(game.id().equals("party") && game.modes().containsAll(List.of("pvp", "sumo", "arms_race", "tnt_run", "mob_count", "rhino", "miners")),
                "Miners is another Party mode; all upstream modes remain available");
        check(game.createSession(null, "miners") instanceof MinersSession && game.maxPlayers("miners") == 12, "The miners queue creates its own FFA session with twelve slots");
        check(game.modeTitle("miners").equals("Битва шахтёров") && game.icon("miners") == Material.DIAMOND_PICKAXE, "Menu exposes a Russian label and pickaxe icon");
        var queueStream = MinersCheck.class.getResourceAsStream("/config.yml");
        var timingsStream = MinersCheck.class.getResourceAsStream("/games/party.yml");
        check(queueStream != null && timingsStream != null, "Shipped configuration resources exist");
        var queues = YamlConfiguration.loadConfiguration(new InputStreamReader(queueStream, StandardCharsets.UTF_8));
        var timings = YamlConfiguration.loadConfiguration(new InputStreamReader(timingsStream, StandardCharsets.UTF_8));
        check(queues.getBoolean("queues.party.miners.enabled") && queues.getInt("queues.party.miners.minPlayers") == 2
                && queues.getInt("queues.party.miners.maxPlayers") == 12, "Party miners has an enabled public queue");
        check(timings.getInt("miners.preparationSeconds") == 120 && timings.getInt("miners.combatSeconds") == 180
                && timings.getInt("miners.finaleSeconds") == 60, "Defaults give two-minute mining, three-minute combat and a one-minute finale");
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
        check(arena.contains(partition) && world.at(17, 72, 8) == Material.AIR, "PvP opens passages between active neighbouring cells");
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
                if (ore == MinersOre.GOLD) { check(result.isEmpty(), "Extra hearts never masquerade as a physical loot item"); continue; }
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
            if (ore != MinersOre.GOLD) check(variants.size() > 3, "Each ore has varied rewards instead of a deterministic item sequence");
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
        check(round.phase() == MinersRound.Phase.FINALE && round.pressure(7400) == 0, "Players get the full final minute before anti-stalemate damage");
        check(round.pressure(7401) == 2 && round.pressure(7501) == 4, "Final pressure increases to prevent indefinite healing/camping");
        round.result(7502); round.advance(10000, true);
        check(round.phase() == MinersRound.Phase.RESULT && !round.mining() && round.deadline() == 7562, "Results hold combat for three seconds without resuming another phase");
        round.done(); round.ready(20000);
        check(round.phase() == MinersRound.Phase.DONE, "An ended round cannot restart through a delayed build callback");
    }
    private static void gold() {
        MinersGold gold = new MinersGold(); UUID a = new UUID(0, 1), b = new UUID(0, 2);
        for (int i = 1; i <= 8; i++) {
            check(gold.collect(a) && gold.count(a) == i && gold.level(a) == i / 4, "Gold grants exactly two finite four-ore health thresholds");
            check(gold.level(b) == 0, "Gold progress is personal");
        }
        check(!gold.collect(a) && gold.level(a) == 2 && gold.count(a) == 8, "At fourteen hearts, more gold cannot generate healing");
        gold.clear(); check(gold.count(a) == 0, "Ending the match clears gold progress");
    }
    private static void drops() {
        MinersDrops drops = new MinersDrops(); List<FakeItem> all = new ArrayList<>();
        for (int i = 0; i < 300; i++) { FakeItem item = new FakeItem(i); all.add(item); drops.track(item.item); }
        for (int i = 0; i < 300; i++) check(all.get(i).removed == (i < 44 ? 1 : 0), "Ground loot evicts oldest abandoned stacks at a bounded count");
        all.get(299).valid = false; drops.prune(); drops.clear(); drops.clear();
        for (int i = 0; i < 299; i++) check(all.get(i).removed == 1, "Every live loot entity is removed exactly once on stop");
        check(all.get(299).removed == 0, "Already picked-up/despawned items are not removed a second time");
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
