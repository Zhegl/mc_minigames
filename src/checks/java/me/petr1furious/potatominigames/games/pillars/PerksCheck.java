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
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/** Exercises the production weighted pool, timers, provenance counts and actual temporary block writes. */
public final class PerksCheck {
    public static void main(String[] args) throws Exception {
        catalogue(); immunity(); layouts(); wishes(); atomicBomb(); decoys(); shapes(); gravity(); phantoms(); history(); channel(); reroll(); terrain(); transmute(); illusions(); integration();
        construction(); protractor(); casino();
        System.out.println("Pillars perks: 55 weighted items, timed effects, terrain, transposition and queue/preset integration passed");
    }
    private static void catalogue() {
        check(PerkType.values().length == 55, "All fifty-five perks are present");
        Map<PerkType, Integer> counts = new HashMap<>();
        for (int roll = 0; roll < PerkType.totalWeight(); roll++) counts.merge(PerkType.atWeight(roll), 1, Integer::sum);
        check(counts.size() == 55 && PerkType.totalWeight() == 208, "No unreachable outcomes or gaps in the weighted draw");
        check(Arrays.stream(PerkType.values()).filter(t -> t.legendary).count() == 4, "The bow, TNT crossbow, AGI and super apple are legendary");
        for (PerkType type : PerkType.values()) {
            check(counts.get(type) == (type.legendary ? 1 : 4), "Each legendary is four times rarer than each ordinary perk");
            check(type.instructions.length > 0 && type.charges > 0, "Every item has player-facing instructions and finite charges");
        }
        check(PerkType.REROLL.charges == 3 && PerkType.ANCHOR.charges == 3 && PerkType.JUMP.charges == 3, "Three-use items retain exactly three charges");
        check(PerkType.BOW.charges == 1 && PerkType.FREEZE.charges == 1, "One-shot bow and one-attempt ice ray are finite");
        check(PerkType.DRONE.material == Material.BOW && PerkType.PUPPET.material == Material.BOW,
                "Special bows share the native bow material and use persistent tags for identity");
        check(PerkType.PUMPKIN.charges == 1 && PerkType.PUMPKIN.material == Material.CARVED_PUMPKIN,
                "The throwable pumpkin is a finite, distinct perk");
        check(PerkType.ATOMIC_BOMB.charges == 1 && PerkType.ATOMIC_BOMB.material == Material.TNT,
                "Atomic bombs use one TNT item per charge");
        check(PerkType.FAKE_BLOCK.charges == 4, "One fake-block item builds exactly four blocks");
        check(PerkRules.BLESSING_TICKS == 1200, "Blessing lasts a full minute");
        check(PerkType.PILLS.charges == 1 && PerkType.PILLS.material == Material.SUGAR, "Pills use one charge and their own icon");
        check(PerkType.DECOY.charges == 1 && PerkType.DECOY.material == Material.ZOMBIE_SPAWN_EGG, "One decoy egg summons one copy");
    }
    private static void construction() {
        var bunker = PerkTerrain.bunkerPlan();
        check(bunker.values().stream().filter(m -> m == Material.IRON_DOOR).count() == 2
                && bunker.values().stream().filter(m -> m == Material.LEVER).count() == 2,
                "Obsidian bunker has a complete iron door and a lever on each side");
        check(bunker.get(new PerkTerrain.Pos(0, 2, 0)) == Material.OBSIDIAN
                && bunker.get(new PerkTerrain.Pos(0, 0, 0)) == Material.AIR, "Bunker has shelter and room to stand");
        var garden = PerkLayouts.garden(org.bukkit.block.BlockFace.EAST);
        check(garden.get(new PerkTerrain.Pos(4, -1, 0)) == Material.WATER
                && garden.get(new PerkTerrain.Pos(4, -1, 1)) == Material.FARMLAND
                && garden.get(new PerkTerrain.Pos(4, 0, 1)) == Material.CARROTS, "Garden faces the player and irrigates its crops");
        var maze = PerkLayouts.maze();
        var mazePaths = PerkShapes.connected(new PerkShapes.Cell(0, 0, 0),
                cell -> maze.get(new PerkTerrain.Pos(cell.x(), cell.y(), cell.z())) == Material.AIR, 100);
        check(mazePaths != null && mazePaths.contains(new PerkShapes.Cell(0, 0, -3))
                && mazePaths.contains(new PerkShapes.Cell(2, 0, 3)), "The maze can be exited from its central starting point");

        FakeWorld fake = new FakeWorld();
        Location at = new Location(fake.world, .5, 200, .5);
        check(new PerkTerrain(fake.world).pillar(at, Material.BEDROCK, new java.util.Random(42)),
                "An empty arena has room to build a pillar");
        var top = fake.blocks.keySet().stream().filter(pos -> pos.y() == 199).findFirst().orElseThrow();
        double distance = Math.hypot(top.x(), top.z());
        check(distance >= 5 && distance <= 8 && fake.get(top.x(), -64, top.z()) == Material.BEDROCK,
                "The new pillar is 5–8 blocks away and rises from world bottom to the player's level");
    }
    private static void protractor() {
        var origin = new PerkTerrain.Pos(-11, 200, 9);
        var before = new PerkTerrain.Pos(-13, 201, 10);
        var after = new PerkTerrain.Pos(-10, 201, 7);
        check(PerkProtractor.transpose(before, origin).equals(after) && PerkProtractor.transpose(after, origin).equals(before),
                "Transposing relative X and Z is reversible and preserves Y");
        var entity = new Location(null, -12.75, 201.5, 10.125, 45, 15);
        var moved = PerkProtractor.transpose(entity, origin);
        check(moved.getX() == -9.875 && moved.getY() == 201.5 && moved.getZ() == 7.25
                && moved.getYaw() == entity.getYaw() && moved.getPitch() == entity.getPitch(),
                "Entities transpose the same axes as blocks, keeping sub-block coordinates and view direction");

        FakeWorld fake = new FakeWorld();
        PerkTerrain terrain = new PerkTerrain(fake.world);
        Location[] victim = {new Location(fake.world, 40.25, 200, .6)};
        fake.target = player(fake, victim);
        fake.set(40, 199, 0, Material.BEDROCK);
        fake.set(51, 199, 0, Material.OBSIDIAN);
        var center = new PerkTerrain.Pos(0, 200, 0);
        check(PerkProtractor.cast(fake.world, center, terrain, e -> true) != null, "A pillar and a player 40 blocks away transpose together");
        check(fake.get(0, 199, 40) == Material.BEDROCK && fake.get(51, 199, 0) == Material.OBSIDIAN
                && fake.get(40, 199, 0) == Material.AIR, "Only terrain inside the radius transposes");
        check(Math.abs(victim[0].getX() - .6) < .001 && Math.abs(victim[0].getZ() - 40.25) < .001
                && victim[0].getY() == 200, "The victim moves horizontally and stays on the same level");

        terrain.put(fake.block(2, 200, 0), Material.ICE, 100, true);
        int writes = fake.writes;
        Location at = victim[0].clone();
        check(PerkProtractor.cast(fake.world, center, terrain, e -> true) == null && fake.writes == writes && at.equals(victim[0]),
                "A protected cell prevents the entire transposition");
    }

    private static void casino() {
        var good = PillarsEvents.Kind.SECOND_WIND;
        var bad = PillarsEvents.Kind.WEAKNESS;
        for (var casino : PillarsEvents.Casino.values()) {
            int goodPicks = 0;
            var random = new java.util.Random(42);
            for (int i = 0; i < 10_000; i++) {
                if (casino.pick(List.of(good, bad), random) == good) goodPicks++;
            }
            check(Math.abs(goodPicks - casino.goodPercent * 100) < 200, "Casino rolls the category with its configured odds");
            check(casino.pick(List.of(good), random) == good, "One eligible category remains playable");
        }
    }

    private static Player player(FakeWorld fake, Location[] at) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, (p, m, a) -> switch (m.getName()) {
            case "getWorld" -> fake.world;
            case "getLocation", "getEyeLocation" -> at[0].clone();
            case "getBoundingBox" -> new BoundingBox(-.3, 0, -.3, .3, 1.8, .3).shift(at[0].toVector());
            case "getVelocity" -> new Vector();
            case "getFallDistance" -> 0f;
            case "isInsideVehicle" -> false;
            case "getPassengers" -> List.of();
            case "teleport" -> { at[0] = ((Location) a[0]).clone(); yield true; }
            case "setVelocity", "setFallDistance" -> null;
            default -> throw new AssertionError(m.getName());
        });
    }
    private static void immunity() {
        FakeWorld f = new FakeWorld(); UUID id = UUID.randomUUID();
        var player = (org.bukkit.entity.Player) Proxy.newProxyInstance(org.bukkit.entity.Player.class.getClassLoader(),
                new Class<?>[]{org.bukkit.entity.Player.class}, (p, m, a) -> switch (m.getName()) {
                    case "getUniqueId" -> id; case "getWorld" -> f.world;
                    default -> throw new AssertionError(m.getName());
                });
        var source = (org.bukkit.damage.DamageSource) Proxy.newProxyInstance(org.bukkit.damage.DamageSource.class.getClassLoader(),
                new Class<?>[]{org.bukkit.damage.DamageSource.class}, (p, m, a) -> null);
        var effects = new PerkExpansion(null, null, f.world, List::of, new PerkTerrain(f.world), listener -> {});
        check(!effects.invulnerable(player), "Players start without shield immunity");
        check(effects.use(player, PerkType.INVULNERABILITY, null), "Shield activates on use");
        check(effects.invulnerable(player) && !effects.blocked(player), "Invulnerability protects without disabling player actions");
        for (var cause : List.of(org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL,
                org.bukkit.event.entity.EntityDamageEvent.DamageCause.VOID,
                org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_EXPLOSION)) {
            var event = new org.bukkit.event.entity.EntityDamageEvent(player, cause, source, 20);
            effects.damage(event); check(event.isCancelled(), "Shield cancels environmental and explosive damage");
        }
        effects.tick(200);
        check(!effects.invulnerable(player), "Shield expires exactly at ten seconds");
        var event = new org.bukkit.event.entity.EntityDamageEvent(player, org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL, source, 20);
        effects.damage(event); check(!event.isCancelled(), "Damage resumes after expiry");
        effects.close(); effects.close();
    }
    private static void layouts() {
        var tree = PerkLayouts.tree();
        for (int y = 0; y <= 8; y++) check(tree.get(new PerkShapes.Cell(0, y, 0)) == Material.OAK_LOG, "Tree trunk is continuous");
        check(tree.size() > 150 && tree.values().contains(Material.OAK_LEAVES), "Tree has a broad usable canopy");
        check(tree.keySet().stream().allMatch(c -> c.y() >= 0 && c.y() <= 9 && Math.abs(c.x()) <= 4 && Math.abs(c.z()) <= 4),
                "Tree placement stays in its bounded footprint");
        var faces = List.of(org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH,
                org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.WEST);
        for (var face : faces) {
            var tracks = PerkLayouts.metro(face);
            check(tracks.size() == 20 && tracks.stream().map(PerkLayouts.Track::base).distinct().count() == 20,
                    "Every rotated metro fills exactly twenty unique cells");
            check(tracks.stream().filter(PerkLayouts.Track::powered).count() == 16, "Sixteen straight rails are powered, four corners curve");
            Map<PerkShapes.Cell, PerkLayouts.Track> cells = new HashMap<>();
            for (var track : tracks) cells.put(track.base(), track);
            var visited = new HashSet<PerkShapes.Cell>();
            var current = tracks.getFirst(); org.bukkit.block.BlockFace entering = null;
            do {
                check(visited.add(current.base()), "The rail loop has no premature cycles");
                var exits = railExits(current.shape());
                var exit = entering == exits.get(0) ? exits.get(1) : exits.get(0);
                var at = current.base();
                var next = cells.get(new PerkShapes.Cell(at.x() + exit.getModX(), at.y(), at.z() + exit.getModZ()));
                check(next != null && railExits(next.shape()).contains(exit.getOppositeFace()), "Every rail connects reciprocally to its neighbour");
                entering = exit.getOppositeFace(); current = next;
            } while (!current.base().equals(tracks.getFirst().base()));
            check(visited.size() == 20, "Minecarts can traverse the complete 2x10 loop in every orientation");
        }
        var rain = PerkLayouts.zombieRain();
        check(rain.size() == 36 && new HashSet<>(rain).size() == 36, "Zombie rain has exactly 36 distinct spawn columns");
        check(rain.stream().allMatch(c -> c.y() == 24 && c.x() >= -3 && c.x() < 3 && c.z() >= -3 && c.z() < 3),
                "Zombie rain occupies a 6x6 area 24 blocks above the target");
    }
    private static List<org.bukkit.block.BlockFace> railExits(org.bukkit.block.data.Rail.Shape shape) {
        var n = org.bukkit.block.BlockFace.NORTH; var s = org.bukkit.block.BlockFace.SOUTH;
        var e = org.bukkit.block.BlockFace.EAST; var w = org.bukkit.block.BlockFace.WEST;
        return switch (shape) {
            case NORTH_SOUTH -> List.of(n, s); case EAST_WEST -> List.of(e, w);
            case NORTH_EAST -> List.of(n, e); case NORTH_WEST -> List.of(n, w);
            case SOUTH_EAST -> List.of(s, e); case SOUTH_WEST -> List.of(s, w);
            default -> throw new AssertionError("Unexpected sloping metro track");
        };
    }
    private static void wishes() {
        check(PerkWish.distance("kitten", "sitting") == 3, "Levenshtein counts substitutions and insertions");
        check(PerkWish.distance("", "bow") == 3 && PerkWish.distance("bow", "bow") == 0, "Empty and exact names");
        var candidates = List.of(Material.AIR, Material.STONE, Material.DIAMOND_SWORD, Material.IRON_SWORD, Material.BOW);
        check(PerkWish.closest("minecraft:diamond sword", candidates) == Material.DIAMOND_SWORD, "English names and namespace normalize");
        check(PerkWish.closest("diamnd_sword", candidates) == Material.DIAMOND_SWORD, "Misspellings select the closest item");
        check(PerkWish.closest("air", candidates) != Material.AIR, "Genie only grants real inventory items");
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
        check(PerkRules.GHOST_TICKS == 54 && PerkRules.FREEZE_TICKS == 80 && PerkRules.SMOKE_TICKS == 100,
                "Spectator lasts 2.7 seconds; freeze stays four seconds and smoke five");
    }
    private static void channel() {
        var channel = new PerkRules.Channel<String>();
        for (int tick = 100; tick < 160; tick++) check(!channel.tick("bedrock", tick, true), "A held block cannot break before three full seconds");
        check(channel.tick("bedrock", 160, true), "The same uninterrupted target completes at three seconds");
        channel.clear(); channel.tick("bedrock", 1000, true);
        check(!channel.tick("bedrock", 1059, false), "Releasing the button just before completion cancels the cast");
        check(!channel.tick("bedrock", 1060, true) && !channel.tick("bedrock", 1119, true), "Resuming cannot reuse earlier progress");
        check(channel.tick("bedrock", 1120, true), "A fresh complete channel succeeds");
        check(!channel.tick("other", 1121, true), "Switching blocks resets progress");
        check(!channel.tick(null, 1180, true), "Looking into air resets progress");
        check(!channel.tick("other", 1280, true), "Time spent looking into air does not count");
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
        check(terrain.transmute(f.block(0, 200, 0), 0, new java.util.Random(7), Material.BLUE_ICE) == 3,
                "An offhand block explicitly chooses the transmutation material");
        check(f.get(0, 200, 0) == Material.BLUE_ICE && f.get(1, 201, 0) == Material.BLUE_ICE,
                "Every connected block uses the selected material");
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
        Player target;
        final java.util.List<FakeDisplay> displays = new java.util.ArrayList<>();
        final WorldBorder border = (WorldBorder) Proxy.newProxyInstance(WorldBorder.class.getClassLoader(), new Class<?>[] {WorldBorder.class},
                (p, m, a) -> { if (m.getName().equals("isInside")) return true; throw new AssertionError(m.getName()); });
        final World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class}, (p, m, a) -> switch (m.getName()) {
            case "getBlockAt" -> a[0] instanceof Location at
                    ? block(at.getBlockX(), at.getBlockY(), at.getBlockZ())
                    : block((int) a[0], (int) a[1], (int) a[2]);
            case "getChunkAt" -> chunk((int) a[0], (int) a[1]);
            case "getMinHeight" -> -64; case "getMaxHeight" -> 320; case "getWorldBorder" -> border;
            case "isChunkLoaded" -> true;
            case "rayTrace" -> new RayTraceResult(target.getLocation().toVector(), target);
            case "getNearbyEntities" -> List.of(target);
            case "spawn" -> spawn((Location) a[0], (Class<?>) a[1], a[2]);
            case "playSound" -> null;
            case "equals" -> p == a[0]; case "hashCode" -> System.identityHashCode(p);
            default -> throw new AssertionError("Unexpected world operation: " + m.getName());
        });
        org.bukkit.Chunk chunk(int chunkX, int chunkZ) {
            return (org.bukkit.Chunk) Proxy.newProxyInstance(org.bukkit.Chunk.class.getClassLoader(),
                    new Class<?>[]{org.bukkit.Chunk.class}, (p, method, args) -> {
                        if (!method.getName().equals("getChunkSnapshot")) throw new AssertionError(method.getName());
                        return (org.bukkit.ChunkSnapshot) Proxy.newProxyInstance(org.bukkit.ChunkSnapshot.class.getClassLoader(),
                                new Class<?>[]{org.bukkit.ChunkSnapshot.class}, (snapshot, query, coordinates) -> switch (query.getName()) {
                                    case "isSectionEmpty" -> false;
                                    case "getBlockType" -> get(chunkX * 16 + (int) coordinates[0], (int) coordinates[1], chunkZ * 16 + (int) coordinates[2]);
                                    default -> throw new AssertionError(query.getName());
                                });
                    });
        }
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
        BlockState state(Location at, Material material) {
            return (BlockState) Proxy.newProxyInstance(BlockState.class.getClassLoader(),
                    new Class<?>[]{BlockState.class}, (p, m, a) -> switch (m.getName()) {
                        case "getBlockData" -> data(material);
                        case "copy" -> state((Location) a[0], material);
                        case "isCollidable" -> material != Material.AIR;
                        case "update" -> { set(at.getBlockX(), at.getBlockY(), at.getBlockZ(), material); yield true; }
                        default -> throw new AssertionError(m.getName());
                    });
        }
        Block block(int x, int y, int z) {
            return (Block) Proxy.newProxyInstance(Block.class.getClassLoader(), new Class<?>[] {Block.class}, (p, m, a) -> switch (m.getName()) {
                case "getX" -> x; case "getY" -> y; case "getZ" -> z; case "getWorld" -> world;
                case "getRelative" -> { var face = (org.bukkit.block.BlockFace) a[0]; yield block(x + face.getModX(), y + face.getModY(), z + face.getModZ()); }
                case "getType" -> get(x, y, z); case "getLocation" -> new Location(world, x, y, z);
                case "getBlockData" -> data(get(x, y, z));
                case "isLiquid" -> false;
                case "isCollidable" -> get(x, y, z) != Material.AIR;
                case "getState" -> state(new Location(world, x, y, z), get(x, y, z));
                case "setType" -> { check(a.length == 2 && !(boolean) a[1], "Perk block writes do not enable physics"); set(x, y, z, (Material) a[0]); yield null; }
                case "setBlockData" -> { check(a.length == 2 && !(boolean) a[1], "Restoring terrain does not enable physics"); set(x, y, z, ((BlockData) a[0]).getMaterial()); yield null; }
                default -> throw new AssertionError("Unexpected block operation: " + m.getName());
            });
        }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
