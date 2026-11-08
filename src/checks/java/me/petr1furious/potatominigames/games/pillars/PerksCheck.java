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
        catalogue(); atomicBomb(); decoys(); shapes(); gravity(); phantoms(); history(); channel(); reroll(); terrain(); transmute(); illusions(); integration();
        System.out.println("Pillars perks: 25 weighted items, atomic TNT, enemy-triggered decoys with owner/terrain immunity and capped damage, shuffled 3x3 anvil rain, complete connected transmutation, personal gravity cooldown, opponent-only phantom sounds/timing, bounded rewind/channel/reroll rules, temporary terrain and queue/preset integration passed");
    }
    private static void catalogue() {
        check(PerkType.values().length == 25, "All twenty-five perks are present");
        Map<PerkType, Integer> counts = new HashMap<>();
        for (int roll = 0; roll < PerkType.totalWeight(); roll++) counts.merge(PerkType.atWeight(roll), 1, Integer::sum);
        check(counts.size() == 25 && PerkType.totalWeight() == 94, "No unreachable outcomes or gaps in the weighted draw");
        check(Arrays.stream(PerkType.values()).filter(t -> t.legendary).count() == 2, "Exactly the bow and TNT crossbow are legendary");
        for (PerkType type : PerkType.values()) {
            check(counts.get(type) == (type.legendary ? 1 : 4), "Each legendary is four times rarer than each ordinary perk");
            check(type.instructions.length > 0 && type.charges > 0, "Every item has player-facing instructions and finite charges");
        }
        check(PerkType.REROLL.charges == 3 && PerkType.ANCHOR.charges == 3 && PerkType.JUMP.charges == 3, "Three-use items retain exactly three charges");
        check(PerkType.BOW.charges == 1 && PerkType.FREEZE.charges == 1, "One-shot bow and one-attempt ice ray are finite");
        check(Arrays.stream(PerkType.values()).map(t -> t.material).distinct().count() == 25, "All twenty-five perk icons are distinct");
        check(PerkType.PUMPKIN.charges == 1 && PerkType.PUMPKIN.material == Material.CARVED_PUMPKIN,
                "The throwable pumpkin is a finite, distinct perk");
        check(PerkType.ATOMIC_BOMB.charges == 1 && PerkType.ATOMIC_BOMB.material == Material.TNT,
                "Atomic bombs use one TNT item per charge");
        check(PerkType.FAKE_BLOCK.charges == 4, "One fake-block item builds exactly four blocks");
        check(PerkRules.BLESSING_TICKS == 1200, "Blessing lasts a full minute");
        check(PerkType.PILLS.charges == 1 && PerkType.PILLS.material == Material.SUGAR, "Pills use one charge and their own icon");
        check(PerkType.DECOY.charges == 1 && PerkType.DECOY.material == Material.ZOMBIE_SPAWN_EGG, "One decoy egg summons one copy");
    }
    private static void decoys() {
        UUID ownerId = new UUID(0, 1), enemyId = new UUID(0, 2);
        var trigger = new PerkRules.DecoyTrigger(ownerId);
        check(!trigger.hit(ownerId, true), "The owner's melee or projectile hits never trigger their decoy");
        check(!trigger.hit(null, true) && !trigger.hit(enemyId, false), "Environmental damage and blast chains cannot trigger a decoy");
        check(trigger.hit(enemyId, true) && !trigger.hit(enemyId, true), "The first opponent hit triggers exactly one blast");
        for (double damage : new double[] {0, 1, 4, 10, 100})
            check(PerkRules.decoyDamage(damage) <= 4 && PerkRules.decoyDamage(damage) <= damage,
                    "Decoy damage is capped at two hearts and never increases a weaker hit");
        var owner = (org.bukkit.entity.Player) Proxy.newProxyInstance(org.bukkit.entity.Player.class.getClassLoader(),
                new Class<?>[] {org.bukkit.entity.Player.class}, (p, m, a) -> { throw new AssertionError(m.getName()); });
        Object[][] blast = {null};
        World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class}, (p, m, a) -> {
            if (!m.getName().equals("createExplosion")) throw new AssertionError(m.getName());
            blast[0] = a.clone(); return true;
        });
        Location at = new Location(world, .5, 200, .5);
        check(PerkOrdnance.burstDecoy(world, at, owner), "The decoy creates a native explosion");
        check(blast[0].length == 6 && blast[0][0] == owner && blast[0][1] == at, "Blast source preserves owner attribution");
        check(blast[0][2].equals(1.5f), "Decoy power stays well below ordinary TNT");
        check(blast[0][3].equals(false) && blast[0][4].equals(false) && blast[0][5].equals(true),
                "The blast starts no fires, breaks no blocks and excludes its owner from damage");
    }
    private static void phantoms() {
        UUID user = new UUID(0, 1), a = new UUID(0, 2), b = new UUID(0, 3);
        var random = new java.util.Random(17);
        var selected = new HashSet<UUID>();
        for (int i = 0; i < 200; i++) {
            UUID victim = PerkRules.randomOpponent(List.of(a, user, b), user, random);
            check(victim != null && !victim.equals(user), "Pills never select the user");
            selected.add(victim);
        }
        check(selected.equals(java.util.Set.of(a, b)), "Every eligible opponent is reachable");
        check(PerkRules.randomOpponent(List.of(user), user, random) == null
                && PerkRules.randomOpponent(List.<UUID>of(), user, random) == null, "No opponent means no effect and no spent charge");
        for (int seed = 0; seed < 50; seed++) {
            random = new java.util.Random(seed);
            var noises = new PerkRules.PhantomNoises(50, random);
            long last = 50; int heard = 0;
            for (long now = 50; now < 1250; now++) {
                check(!noises.expired(now), "The sound effect lasts the full minute");
                if (!noises.due(now, random)) continue;
                check(now - last >= 80 && now - last <= 120, "Each phantom sound is four to six seconds after the previous one");
                check(!noises.due(now, random), "A sound cannot fire twice in the same tick");
                last = now; heard++;
            }
            check(heard >= 9 && heard <= 14, "The effect stays periodic rather than silent or spammy");
            check(noises.expired(1250) && !noises.due(1250, random) && !noises.due(5000, random), "No sounds play at or after the sixty-second expiry");
        }
        var delayed = new PerkRules.PhantomNoises(0, random);
        check(delayed.due(1000, random) && !delayed.due(1001, random), "A paused player hears no burst of overdue sounds on resuming");
    }
    private static void shapes() {
        var center = new PerkShapes.Cell(12, 220, -8);
        var expected = new HashSet<PerkShapes.Cell>();
        for (int x = 11; x <= 13; x++) for (int z = -9; z <= -7; z++) expected.add(new PerkShapes.Cell(x, 220, z));
        var orders = new HashSet<List<PerkShapes.Cell>>();
        for (int seed = 0; seed < 50; seed++) {
            var rain = PerkShapes.anvilRain(center.x(), center.y(), center.z(), new java.util.Random(seed));
            check(rain.size() == 9 && new HashSet<>(rain).equals(expected), "Nine unique anvils cover exactly the targeted 3x3 square");
            orders.add(rain);
        }
        check(orders.size() > 1, "Anvil order is shuffled rather than always following rows");
        var connected = java.util.Set.of(center, new PerkShapes.Cell(13, 220, -8), new PerkShapes.Cell(13, 221, -8));
        var diagonal = new PerkShapes.Cell(14, 222, -7);
        var blocks = new HashSet<>(connected); blocks.add(diagonal);
        check(new HashSet<>(PerkShapes.connected(center, blocks::contains, 3)).equals(connected),
                "Flood fill includes vertical/side neighbours but not diagonal-only contact");
        check(PerkShapes.connected(center, blocks::contains, 2) == null, "Oversize structures fail without returning a partial prefix");
        check(PerkShapes.connected(new PerkShapes.Cell(50, 50, 50), blocks::contains, 3).isEmpty(), "An empty target has no connected blocks");
    }
    private static void gravity() {
        var cycle = new PerkRules.GravityCycle();
        var other = new PerkRules.GravityCycle();
        check(cycle.start(100) && cycle.active(100) && cycle.active(159), "Gravity reverses immediately for all sixty ticks");
        check(!cycle.active(160) && !cycle.start(160), "Normal gravity returns before cooldown ends");
        check(cycle.remaining(160) == 300 && !cycle.start(459), "A full fifteen-second cooldown follows the effect");
        check(other.start(160), "One player's cooldown does not block a different player");
        check(cycle.start(460) && cycle.active(519) && !cycle.active(520), "The reusable item activates again at the exact deadline");
    }
    private static void atomicBomb() {
        var owner = (org.bukkit.entity.Player) Proxy.newProxyInstance(org.bukkit.entity.Player.class.getClassLoader(),
                new Class<?>[] {org.bukkit.entity.Player.class}, (p, m, a) -> { throw new AssertionError(m.getName()); });
        Map<String, Object> properties = new HashMap<>();
        var bomb = (org.bukkit.entity.TNTPrimed) Proxy.newProxyInstance(org.bukkit.entity.TNTPrimed.class.getClassLoader(),
                new Class<?>[] {org.bukkit.entity.TNTPrimed.class}, (p, m, a) -> {
                    switch (m.getName()) {
                        case "setSource", "setFuseTicks", "setYield", "setIsIncendiary" -> properties.put(m.getName(), a[0]);
                        default -> throw new AssertionError(m.getName());
                    }
                    return null;
                });
        PerkOrdnance.armAtomic(bomb, owner);
        check(properties.get("setSource") == owner, "Atomic TNT retains its owner for kill credit");
        check(properties.get("setFuseTicks").equals(80), "Atomic TNT has the ordinary four-second fuse");
        check(properties.get("setYield").equals(16f), "Atomic power is four times vanilla TNT's power of four");
        check(properties.get("setIsIncendiary").equals(false), "Atomic explosions do not start fires");
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
    private static void transmute() {
        FakeWorld f = new FakeWorld(); PerkTerrain terrain = new PerkTerrain(f.world);
        f.set(0, 200, 0, Material.BEDROCK); f.set(1, 200, 0, Material.OAK_PLANKS); f.set(1, 201, 0, Material.GLASS);
        f.set(2, 202, 1, Material.STONE);
        check(terrain.transmute(f.block(0, 200, 0), 0, new java.util.Random(7)) == 3, "Mixed connected materials all participate in transmutation");
        check(f.get(0, 200, 0) == f.get(1, 200, 0) && f.get(1, 200, 0) == f.get(1, 201, 0),
                "The whole component becomes one shared random material");
        check(f.get(2, 202, 1) == Material.STONE, "Diagonal and disconnected blocks remain unchanged");
        FakeWorld huge = new FakeWorld(); PerkTerrain hugeTerrain = new PerkTerrain(huge.world);
        for (int x = 0; x <= PerkRules.CONNECTED_LIMIT; x++) huge.set(x, 200, 0, Material.STONE);
        int before = huge.writes;
        check(hugeTerrain.transmute(huge.block(0, 200, 0), 0, new java.util.Random(1)) == -1 && huge.writes == before,
                "Oversize components refuse atomically without spending blocks or partially changing the structure");
    }
    private static void illusions() {
        FakeWorld f = new FakeWorld();
        f.set(0, 200, 0, Material.BEDROCK);
        UUID owner = new UUID(0, 10);
        var player = (org.bukkit.entity.Player) Proxy.newProxyInstance(org.bukkit.entity.Player.class.getClassLoader(),
                new Class<?>[] {org.bukkit.entity.Player.class}, (p, m, a) -> switch (m.getName()) {
                    case "getUniqueId" -> owner;
                    case "getEyeLocation" -> new Location(f.world, .5, 202, -2.5, 0, 18.434948f);
                    case "rayTraceBlocks" -> new org.bukkit.util.RayTraceResult(new org.bukkit.util.Vector(.5, 201, .5),
                            f.block(0, 200, 0), org.bukkit.block.BlockFace.UP);
                    default -> throw new AssertionError(m.getName());
                });
        PerkIllusions illusions = new PerkIllusions(f.world, f.data(Material.OAK_PLANKS));
        int before = f.writes;
        check(illusions.place(player) && illusions.place(player), "Fake blocks can extend from another fake's visible face");
        check(f.displays.size() == 2 && f.displays.get(0).at.getBlockZ() == 0 && f.displays.get(1).at.getBlockZ() == -1,
                "The nearer illusion takes priority over a real block behind it");
        check(f.writes == before && f.get(0, 201, 0) == Material.AIR && f.get(0, 201, -1) == Material.AIR,
                "Rendering fake blocks never creates server or client collision blocks");
        check(illusions.breakAt(player) && !f.displays.get(1).valid, "A left click removes the nearest fake without block drops");
        illusions.playerGone(owner);
        check(f.displays.stream().noneMatch(d -> d.valid) && f.writes == before, "Leaving removes all the owner's visuals without touching terrain");
        check(illusions.place(player), "Removing visuals frees their positions");
        f.set(0, 201, 0, Material.STONE);
        illusions.tick();
        check(!f.displays.getLast().valid && f.get(0, 201, 0) == Material.STONE, "Real construction replaces a fake without getting removed later");
        illusions.close(); illusions.close();
    }
    private static final class FakeDisplay {
        final Location at;
        boolean valid = true;
        FakeDisplay(Location at) { this.at = at; }
        final org.bukkit.entity.BlockDisplay entity = (org.bukkit.entity.BlockDisplay) Proxy.newProxyInstance(
                org.bukkit.entity.BlockDisplay.class.getClassLoader(), new Class<?>[] {org.bukkit.entity.BlockDisplay.class}, (p, m, a) -> switch (m.getName()) {
                    case "isValid" -> valid;
                    case "remove" -> { valid = false; yield null; }
                    case "setBlock", "setPersistent", "setInvulnerable", "setViewRange" -> null;
                    default -> throw new AssertionError(m.getName());
                });
    }
    private static final class FakeWorld {
        final Map<PerkTerrain.Pos, Material> blocks = new HashMap<>(); int writes;
        final java.util.List<FakeDisplay> displays = new java.util.ArrayList<>();
        final WorldBorder border = (WorldBorder) Proxy.newProxyInstance(WorldBorder.class.getClassLoader(), new Class<?>[] {WorldBorder.class},
                (p, m, a) -> { if (m.getName().equals("isInside")) return true; throw new AssertionError(m.getName()); });
        final World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class}, (p, m, a) -> switch (m.getName()) {
            case "getBlockAt" -> block((int) a[0], (int) a[1], (int) a[2]);
            case "getMinHeight" -> -64; case "getMaxHeight" -> 320; case "getWorldBorder" -> border;
            case "isChunkLoaded" -> true;
            case "spawn" -> spawn((Location) a[0], (Class<?>) a[1], a[2]);
            case "playSound" -> null;
            case "equals" -> p == a[0]; case "hashCode" -> System.identityHashCode(p);
            default -> throw new AssertionError("Unexpected world operation: " + m.getName());
        });
        @SuppressWarnings("unchecked")
        Object spawn(Location at, Class<?> type, Object initializer) {
            check(type == org.bukkit.entity.BlockDisplay.class, "Illusions only spawn non-colliding display entities");
            FakeDisplay display = new FakeDisplay(at); displays.add(display);
            ((java.util.function.Consumer<org.bukkit.entity.BlockDisplay>) initializer).accept(display.entity);
            return display.entity;
        }
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
                case "getRelative" -> { var face = (org.bukkit.block.BlockFace) a[0]; yield block(x + face.getModX(), y + face.getModY(), z + face.getModZ()); }
                case "getType" -> get(x, y, z); case "getLocation" -> new Location(world, x, y, z);
                case "getBlockData" -> data(get(x, y, z));
                case "isLiquid" -> false; case "getState" -> null;
                case "setType" -> { check(a.length == 2 && !(boolean) a[1], "Perk block writes do not enable physics"); set(x, y, z, (Material) a[0]); yield null; }
                case "setBlockData" -> { check(a.length == 2 && !(boolean) a[1], "Restoring terrain does not enable physics"); set(x, y, z, ((BlockData) a[0]).getMaterial()); yield null; }
                default -> throw new AssertionError("Unexpected block operation: " + m.getName());
            });
        }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
