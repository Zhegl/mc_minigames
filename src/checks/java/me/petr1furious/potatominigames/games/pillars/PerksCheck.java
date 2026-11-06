package me.petr1furious.potatominigames.games.pillars;

import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;

/** Exercises the production weighted pool, timers, provenance counts and actual temporary block writes. */
public final class PerksCheck {
    public static void main(String[] args) throws Exception {
        catalogue(); history(); channel(); reroll(); terrain(); integration();
        System.out.println("Pillars perks: 16 reachable weighted items, bounded rewind history, uninterrupted six-second channel, complete reroll counts, non-destructive temporary terrain/cage expiry, tiny house and queue/preset integration passed");
    }
    private static void catalogue() {
        check(PerkType.values().length == 16, "All sixteen agreed perks are present");
        Map<PerkType, Integer> counts = new HashMap<>();
        for (int roll = 0; roll < PerkType.totalWeight(); roll++) counts.merge(PerkType.atWeight(roll), 1, Integer::sum);
        check(counts.size() == 16 && PerkType.totalWeight() == 58, "No unreachable outcomes or gaps in the weighted draw");
        check(Arrays.stream(PerkType.values()).filter(t -> t.legendary).count() == 2, "Exactly the bow and TNT crossbow are legendary");
        for (PerkType type : PerkType.values()) {
            check(counts.get(type) == (type.legendary ? 1 : 4), "Each legendary is four times rarer than each ordinary perk");
            check(type.instructions.length > 0 && type.charges > 0, "Every item has player-facing instructions and finite charges");
        }
        check(PerkType.REROLL.charges == 3 && PerkType.ANCHOR.charges == 3 && PerkType.JUMP.charges == 3, "Three-use items retain exactly three charges");
        check(PerkType.BOW.charges == 1 && PerkType.FREEZE.charges == 1, "One-shot bow and one-attempt ice ray are finite");
        check(Arrays.stream(PerkType.values()).map(t -> t.material).distinct().count() == 16, "All sixteen perk icons are distinct");
    }
    private static void history() {
        var history = new PerkRules.History<Integer>();
        for (int tick = 0; tick < 60; tick++) { history.add(tick, tick * 2); check(history.ago(tick) == null, "Do not invent a position before three seconds of history"); }
        history.add(60, 120); check(history.ago(60) == 0, "First rewind uses the position exactly sixty ticks earlier");
        for (int tick = 61; tick < 10000; tick++) {
            history.add(tick, tick * 2);
            check(history.ago(tick) == (tick - 60) * 2, "Rewind follows the real moving position without rolling back other state");
            check(history.size() <= 63, "History remains bounded during a long match");
        }
        check(PerkRules.PLATFORM_TICKS == 200 && PerkRules.PLANK_TICKS == 100, "Platform lasts ten seconds and plank boots five");
        check(PerkRules.GHOST_TICKS == 80 && PerkRules.FREEZE_TICKS == 80 && PerkRules.SMOKE_TICKS == 100,
                "Spectator/freeze last four seconds; smoke lasts five");
    }
    private static void channel() {
        var channel = new PerkRules.Channel<String>();
        for (int tick = 100; tick < 220; tick++) check(!channel.tick("bedrock", tick, true), "A held block cannot break before six full seconds");
        check(channel.tick("bedrock", 220, true), "The same uninterrupted target completes at six seconds");
        channel.clear(); channel.tick("bedrock", 1000, true);
        check(!channel.tick("bedrock", 1119, false), "Releasing the button just before completion cancels the cast");
        check(!channel.tick("bedrock", 1120, true) && !channel.tick("bedrock", 1239, true), "Resuming cannot reuse earlier progress");
        check(channel.tick("bedrock", 1240, true), "A fresh complete channel succeeds");
        check(!channel.tick("other", 1241, true), "Switching blocks resets progress");
        check(!channel.tick(null, 1300, true), "Looking into air resets progress");
        check(!channel.tick("other", 1400, true), "Time spent looking into air does not count");
    }
    private static void reroll() {
        check(PerkRules.complete(new int[] {32}, new int[] {32}), "A full unspent stack can be replaced");
        check(!PerkRules.complete(new int[] {32}, new int[] {31}), "One used/placed/transferred item invalidates the whole stack");
        check(!PerkRules.complete(new int[] {1, 1}, new int[] {1, 0}), "A bow bundle without its spent ammunition cannot be duplicated");
        check(!PerkRules.complete(new int[] {1}, new int[] {2}), "Duplicated provenance is rejected rather than producing a reward");
        check(!PerkRules.complete(new int[0], new int[0]), "An empty or lost gift cannot generate free loot");
    }
    private static void terrain() {
        FakeWorld f = new FakeWorld(); PerkTerrain terrain = new PerkTerrain(f.world);
        Location at = new Location(f.world, .5, 200, .5);
        f.set(0, 198, 0, Material.BEDROCK);
        check(terrain.platform(at, 200), "A feather creates a real floor under the falling player");
        check(f.get(0, 198, 0) == Material.BEDROCK, "Platforms never overwrite an existing pillar");
        check(f.blocks.values().stream().filter(m -> m == Material.SLIME_BLOCK).count() == 8, "Only the other eight cells of the 3x3 platform are added");
        terrain.tick(199); check(f.get(1, 198, 1) == Material.SLIME_BLOCK, "The floor remains for the full lifetime");
        f.set(1, 198, 1, Material.OAK_PLANKS);
        terrain.changed(f.block(-1, 198, -1)); f.set(-1, 198, -1, Material.SLIME_BLOCK);
        terrain.tick(200);
        check(f.get(1, 198, 1) == Material.OAK_PLANKS, "Expiry does not remove a player's replacement block");
        check(f.get(-1, 198, -1) == Material.SLIME_BLOCK, "A newly placed identical material is not confused with the expired original");
        check(f.get(1, 198, 0) == Material.AIR && f.get(0, 198, 0) == Material.BEDROCK, "Expiry restores original air, preserving permanent terrain");
        Location other = new Location(f.world, 10.5, 200, 10.5);
        terrain.platform(other, 400); terrain.platform(other, 500);
        terrain.tick(400); check(f.get(10, 198, 10) == Material.SLIME_BLOCK, "Overlapping platforms retain the newer lease");
        terrain.tick(500); check(f.get(10, 198, 10) == Material.AIR, "Overlapping platforms ultimately restore the original air");
        Location victim = new Location(f.world, 20.5, 200, 20.5);
        terrain.cage(victim, 580);
        check(f.get(20, 200, 20) == Material.AIR && f.get(20, 201, 20) == Material.AIR, "Ice cage leaves the victim's body clear");
        check(f.get(20, 199, 20) == Material.ICE && f.get(20, 202, 20) == Material.ICE, "Ice cage has floor and roof even in the air");
        check(terrain.locked(f.block(19, 200, 20), 579), "The ice is protected throughout the four-second freeze");
        terrain.tick(580);
        check(!terrain.locked(f.block(19, 200, 20), 580) && f.get(19, 200, 20) == Material.AIR, "Freeze expiry releases its blocks without lingering terrain");
        terrain.platform(other, 1000); terrain.close(); terrain.close();
        check(f.get(10, 198, 10) == Material.AIR, "Stopping restores pending terrain and is safe to repeat");
        var plan = PerkTerrain.housePlan();
        check(plan.keySet().stream().allMatch(p -> Math.abs(p.x()) <= 1 && Math.abs(p.z()) <= 1), "The entire house really fits in a 3x3 footprint");
        check(plan.keySet().stream().filter(p -> p.y() == -1).count() == 9 && plan.keySet().stream().filter(p -> p.y() == 2).count() == 9,
                "The tiny house has a complete floor and roof");
        check(plan.get(new PerkTerrain.Pos(0, 0, 0)) == null && plan.get(new PerkTerrain.Pos(0, 1, 0)) == Material.WALL_TORCH,
                "One-player interior has two blocks of headroom with passable wall lighting");
        check(plan.values().stream().filter(m -> m == Material.OAK_DOOR).count() == 2
                && plan.values().stream().anyMatch(m -> m == Material.GLASS_PANE), "The tiny house includes a door and windows");
        int before = f.writes;
        f.set(11, 200, 10, Material.STONE); before++;
        check(!terrain.house(other) && f.writes == before, "A blocked house is rejected atomically without destroying existing blocks");
    }
    private static void integration() throws Exception {
        check(PillarsConfig.MODES.contains("perks") && PillarsPresets.MODIFIERS.contains("perks"), "Perks is both a public mode and a composable modifier");
        var timings = YamlConfiguration.loadConfiguration(new InputStreamReader(PerksCheck.class.getResourceAsStream("/games/pillars.yml"), StandardCharsets.UTF_8));
        var queue = YamlConfiguration.loadConfiguration(new InputStreamReader(PerksCheck.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
        check(timings.getBoolean("modes.perks.perks") && timings.getDouble("perks.dropChance") == .20, "Shipped mode enables a configurable 20% perk draw");
        check(queue.getBoolean("queues.pillars.perks.enabled") && queue.getInt("queues.pillars.perks.maxPlayers") == 16, "The mode has a public sixteen-player queue");
        var dir = Files.createTempDirectory("pillars-perks-check");
        try {
            var path = dir.resolve("presets.yml");
            var presets = new PillarsPresets(path, PillarsConfig.MODES);
            presets.save(new UUID(0, 1), "Player", "perky", List.of("perks", "shuffle", "rave", "tactical"));
            check(new PillarsPresets(path, PillarsConfig.MODES).find("perky").modifiers().containsAll(List.of("perks", "shuffle", "rave", "tactical")),
                    "The new modifier saves and reloads alongside existing modifiers");
            Files.deleteIfExists(path);
        } finally { Files.deleteIfExists(dir); }
    }
    private static final class FakeWorld {
        final Map<PerkTerrain.Pos, Material> blocks = new HashMap<>(); int writes;
        final WorldBorder border = (WorldBorder) Proxy.newProxyInstance(WorldBorder.class.getClassLoader(), new Class<?>[] {WorldBorder.class},
                (p, m, a) -> { if (m.getName().equals("isInside")) return true; throw new AssertionError(m.getName()); });
        final World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class}, (p, m, a) -> switch (m.getName()) {
            case "getBlockAt" -> block((int) a[0], (int) a[1], (int) a[2]);
            case "getMinHeight" -> -64; case "getMaxHeight" -> 320; case "getWorldBorder" -> border;
            case "equals" -> p == a[0]; case "hashCode" -> System.identityHashCode(p);
            default -> throw new AssertionError("Unexpected world operation: " + m.getName());
        });
        void set(int x, int y, int z, Material material) { blocks.put(new PerkTerrain.Pos(x, y, z), material); writes++; }
        Material get(int x, int y, int z) { return blocks.getOrDefault(new PerkTerrain.Pos(x, y, z), Material.AIR); }
        BlockData data(Material material) {
            return (BlockData) Proxy.newProxyInstance(BlockData.class.getClassLoader(), new Class<?>[] {BlockData.class}, (p, m, a) -> switch (m.getName()) {
                case "getMaterial" -> material; case "clone" -> data(material);
                case "equals" -> a[0] instanceof BlockData d && d.getMaterial() == material; case "hashCode" -> material.hashCode();
                default -> throw new AssertionError("Unexpected data operation: " + m.getName());
            });
        }
        Block block(int x, int y, int z) {
            return (Block) Proxy.newProxyInstance(Block.class.getClassLoader(), new Class<?>[] {Block.class}, (p, m, a) -> switch (m.getName()) {
                case "getX" -> x; case "getY" -> y; case "getZ" -> z; case "getWorld" -> world;
                case "getType" -> get(x, y, z); case "getLocation" -> new Location(world, x, y, z);
                case "getBlockData" -> data(get(x, y, z));
                case "setType" -> { check(a.length == 2 && !(boolean) a[1], "Perk block writes do not enable physics"); set(x, y, z, (Material) a[0]); yield null; }
                case "setBlockData" -> { check(a.length == 2 && !(boolean) a[1], "Restoring terrain does not enable physics"); set(x, y, z, ((BlockData) a[0]).getMaterial()); yield null; }
                default -> throw new AssertionError("Unexpected block operation: " + m.getName());
            });
        }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
