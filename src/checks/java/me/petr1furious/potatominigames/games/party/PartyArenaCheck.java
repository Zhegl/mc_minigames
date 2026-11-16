package me.petr1furious.potatominigames.games.party;

import static me.petr1furious.potatominigames.games.party.PartyScoringCheck.check;

import org.bukkit.*;
import org.bukkit.block.Block;

import java.lang.reflect.Proxy;
import java.util.*;

/** Exercises actual arena mutations, not copies of the generators. */
public final class PartyArenaCheck {
    public static void main(String[] args) {
        installDataFactory();
        WorldMap w = new WorldMap();
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 12; i++) ids.add(new UUID(0, i + 1));
        BuildMemoryArena memory = new BuildMemoryArena(w.world, ids);
        memory.build();
        for (UUID id : ids) {
            var s = memory.spawn(id);
            int cx = s.getBlockX() - 2, cz = s.getBlockZ() + 3;
            for (int x = 0; x < 5; x++)
                for (int z = 0; z < 5; z++)
                    check(
                            w.world.getBlockAt(cx + x, 80, cz + z).getType()
                                    == Material.LIGHT_BLUE_CONCRETE,
                            "Whole 5x5 footprint is marked");
            check(
                    w.world.getBlockAt(cx, 80, cz - 1).getType() == Material.SMOOTH_QUARTZ,
                    "No isolated gold orientation marker");
        }
        var pattern = BuildMemoryPattern.generate(8, new Random(5));
        for (UUID id : ids) memory.show(id, pattern);
        for (UUID id : ids)
            check(
                    memory.capture(id).equals(pattern.blocks()),
                    "Twelve independent build workspaces");
        memory.clear(ids.getFirst());
        check(memory.capture(ids.getFirst()).isEmpty(), "Model disappears");
        check(
                memory.capture(ids.get(1)).equals(pattern.blocks()),
                "Clearing own model preserves neighbor");
        memory.resize(7);
        for (UUID id : ids) {
            var center = memory.reviewCenter(id);
            int cx = center.getBlockX() - 3, cz = center.getBlockZ() - 3;
            for (int x = 0; x < 7; x++)
                for (int z = 0; z < 7; z++)
                    check(
                            w.world.getBlockAt(cx + x, 80, cz + z).getType()
                                    == Material.LIGHT_BLUE_CONCRETE,
                            "Expanded 7x7 floor is marked");
            check(
                    memory.localCell(id, w.block(cx + 6, 84, cz + 6)) != null,
                    "Far corner is editable in expanded plot");
            check(
                    memory.localCell(id, w.block(cx + 7, 84, cz + 6)) == null,
                    "Expanded plot still rejects outside edits");
        }
        for (UUID id : ids) memory.show(id, pattern);
        memory.clear(ids.getFirst());
        check(memory.capture(ids.getFirst()).isEmpty(), "Expanded model clears all cells");
        check(
                memory.capture(ids.get(1)).equals(pattern.blocks()),
                "Expanded plots remain isolated");
        memory.resize(5);
        for (UUID id : ids) {
            check(memory.capture(id).isEmpty(), "Resizing clears previous build");
            var center = memory.reviewCenter(id);
            int cx = center.getBlockX() - 2, cz = center.getBlockZ() - 2;
            check(
                    w.world.getBlockAt(cx + 6, 80, cz + 6).getType() == Material.SMOOTH_QUARTZ,
                    "Shrinking restores outer floor");
            check(
                    w.world.getBlockAt(cx + 6, 81, cz + 6).getType() == Material.AIR,
                    "Shrinking removes blocks outside the smaller capture bounds");
        }
        var owner = ids.getFirst();
        var anchor = memory.spawn(owner);
        int ax = anchor.getBlockX() - 2, az = anchor.getBlockZ() + 3;
        memory.show(
                owner,
                new BuildMemoryPattern(
                        net.kyori.adventure.text.Component.text("Support"),
                        Map.of(
                                new BuildMemoryPattern.Cell(2, 0, 2),
                                        MemoryBlock.of(Material.OAK_PLANKS),
                                new BuildMemoryPattern.Cell(2, 1, 2),
                                        MemoryBlock.of(Material.OAK_SIGN))));
        check(
                memory.remove(owner, w.block(ax + 2, 81, az + 2), refund -> false).isEmpty(),
                "Full inventory rejects correction atomically");
        check(
                memory.capture(owner).size() == 2,
                "Support and attached sign stay intact if refund cannot fit");
        var refund = memory.remove(owner, w.block(ax + 2, 81, az + 2), blocks -> true);
        check(
                refund.size() == 2 && refund.stream().anyMatch(b -> b.item() == Material.OAK_SIGN),
                "Removing support refunds sign as well");
        check(memory.capture(owner).isEmpty(), "No floating attachment or dropped item remains");
        for (int model : BuildMemoryPattern.smallModels()) {
            var small = BuildMemoryPattern.generate(1, model, new Random(model));
            memory.show(owner, small);
            check(
                    small.matches(memory.capture(owner)),
                    "Every small sample survives arena rendering: " + model);
        }
        w = new WorldMap();
        ToolRaceTrack track = new ToolRaceTrack(ids, ToolRaceTrack.generate(new Random(7)));
        ToolRaceArena shafts = new ToolRaceArena(w.world, ids);
        shafts.build(track);
        for (UUID id : ids) {
            var spawn = shafts.spawn(id);
            for (int d = 0; d < 50; d++) {
                Block b = w.world.getBlockAt(spawn.getBlockX(), 80 - d, 0);
                check(b.getType() == track.block(d), "Actual identical fifty-block shafts");
                check(shafts.depth(id, b) == d, "Only own depth");
            }
            check(
                    w.world.getBlockAt(spawn.getBlockX(), 30, 0).getType() == Material.AIR,
                    "Finisher falls out of the shaft");
            check(
                    w.world.getBlockAt(spawn.getBlockX(), 25, 0).getType()
                            == Material.SMOOTH_QUARTZ,
                    "Landing platform beneath the finish");
            check(
                    w.world.getBlockAt(spawn.getBlockX() + 1, 60, 0).getType() == Material.GLASS,
                    "Can see neighboring racers through the shaft wall");
        }
        check(
                shafts.depth(ids.getFirst(), w.world.getBlockAt(4, 80, 0)) == -1,
                "Cannot mine neighbor");
        w = new WorldMap();
        var arena = new PvpArena(w.world);
        arena.build();
        for (int count = 2; count <= 12; count++)
            for (int i = 0; i < count; i++) {
                var s = arena.spawn(i, count);
                check(
                        w.world.getBlockAt(s.getBlockX(), 81, s.getBlockZ()).getType()
                                == Material.AIR,
                        "FFA spawn cannot be inside cover");
            }
        System.out.println("Party arena geometry passed");
    }

    static final class WorldMap {
        final Map<String, Material> blocks = new HashMap<>();
        final Map<String, org.bukkit.block.data.BlockData> data = new HashMap<>();
        final World world =
                (World)
                        Proxy.newProxyInstance(
                                World.class.getClassLoader(),
                                new Class[] {World.class},
                                (proxy, m, a) ->
                                        switch (m.getName()) {
                                            case "getBlockAt" ->
                                                    block((int) a[0], (int) a[1], (int) a[2]);
                                            case "setSpawnLocation" -> true;
                                            case "equals" -> proxy == a[0];
                                            case "hashCode" -> System.identityHashCode(proxy);
                                            default ->
                                                    throw new AssertionError(
                                                            "World: " + m.getName());
                                        });

        Block block(int x, int y, int z) {
            String key = x + ":" + y + ":" + z;
            return (Block)
                    Proxy.newProxyInstance(
                            Block.class.getClassLoader(),
                            new Class[] {Block.class},
                            (proxy, m, a) ->
                                    switch (m.getName()) {
                                        case "setType" -> {
                                            blocks.put(key, (Material) a[0]);
                                            data.remove(key);
                                            yield null;
                                        }
                                        case "setBlockData" -> {
                                            var state = (org.bukkit.block.data.BlockData) a[0];
                                            blocks.put(key, state.getMaterial());
                                            data.put(key, state);
                                            yield null;
                                        }
                                        case "getBlockData" ->
                                                data.getOrDefault(
                                                        key,
                                                        blockData(
                                                                MemoryBlock.of(
                                                                                blocks.getOrDefault(
                                                                                        key,
                                                                                        Material
                                                                                                .AIR))
                                                                        .dataString()));
                                        case "getType" -> blocks.getOrDefault(key, Material.AIR);
                                        case "getWorld" -> world;
                                        case "getX" -> x;
                                        case "getY" -> y;
                                        case "getZ" -> z;
                                        default ->
                                                throw new AssertionError("Block: " + m.getName());
                                    });
        }
    }

    // A narrow BlockData serialization boundary; production equality/rotation stays real.
    static void installDataFactory() {
        try {
            var server = Bukkit.class.getDeclaredField("server");
            server.setAccessible(true);
            server.set(
                    null,
                    Proxy.newProxyInstance(
                            Server.class.getClassLoader(),
                            new Class[] {Server.class},
                            (o, m, a) -> {
                                if (m.getName().equals("createBlockData") && a.length == 1)
                                    return blockData((String) a[0]);
                                throw new AssertionError("Server: " + m.getName());
                            }));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    static org.bukkit.block.data.BlockData blockData(String text) {
        Material material = Material.valueOf(text.split("[\\[:]", 3)[1].toUpperCase(Locale.ROOT));
        return (org.bukkit.block.data.BlockData)
                Proxy.newProxyInstance(
                        org.bukkit.block.data.BlockData.class.getClassLoader(),
                        new Class[] {org.bukkit.block.data.BlockData.class},
                        (o, m, a) ->
                                switch (m.getName()) {
                                    case "getMaterial" -> material;
                                    case "getAsString" -> text;
                                    default ->
                                            throw new AssertionError("BlockData: " + m.getName());
                                });
    }
}
