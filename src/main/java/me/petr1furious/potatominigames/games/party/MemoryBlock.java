package me.petr1furious.potatominigames.games.party;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.*;

/** Only player-controlled geometry matters; connections and waterlogging are derived. */
record MemoryBlock(Material material, Map<String, String> properties) {
    MemoryBlock {
        properties = Map.copyOf(properties);
    }

    static MemoryBlock of(Material material, String... settings) {
        Map<String, String> properties = new TreeMap<>();
        String name = material.name();
        if (name.endsWith("_STAIRS")) {
            properties.put("facing", "north");
            properties.put("half", "bottom");
        } else if (name.endsWith("_SLAB")) properties.put("type", "bottom");
        else if (name.endsWith("WALL_TORCH")) properties.put("facing", "north");
        else if (name.endsWith("_WALL_SIGN")) properties.put("facing", "north");
        else if (name.endsWith("_SIGN")) properties.put("rotation", "0");
        else if (name.endsWith("_CHAIN") || name.endsWith("_LOG")) properties.put("axis", "y");
        else if (name.equals("LANTERN") || name.equals("SOUL_LANTERN"))
            properties.put("hanging", "false");
        for (String setting : settings) {
            String[] pair = setting.split("=", 2);
            if (properties.containsKey(pair[0])) properties.put(pair[0], pair[1]);
        }
        return new MemoryBlock(material, properties);
    }

    static MemoryBlock capture(BlockData data) {
        String serialized = data.getAsString();
        int start = serialized.indexOf('[');
        return of(
                data.getMaterial(),
                start < 0
                        ? new String[0]
                        : serialized.substring(start + 1, serialized.length() - 1).split(","));
    }

    /** Keep rendering/support state intact; only grading ignores sign direction. */
    static boolean sameForBuild(MemoryBlock expected, MemoryBlock actual) {
        if (expected == null || actual == null) return expected == actual;
        if (expected.material != actual.material) return false;
        if (!expected.material.name().endsWith("_SIGN")) return expected.equals(actual);
        var first = new HashMap<>(expected.properties);
        var second = new HashMap<>(actual.properties);
        for (var properties : List.of(first, second)) {
            properties.remove("facing");
            properties.remove("rotation");
        }
        return first.equals(second);
    }

    String dataString() {
        String name = "minecraft:" + material.name().toLowerCase(Locale.ROOT);
        return properties.isEmpty()
                ? name
                : name
                        + "["
                        + String.join(
                                ",",
                                new TreeMap<>(properties)
                                        .entrySet().stream()
                                                .map(e -> e.getKey() + "=" + e.getValue())
                                                .toList())
                        + "]";
    }

    Material item() {
        String name = material.name();
        if (name.endsWith("_WALL_SIGN"))
            return Material.valueOf(name.replace("_WALL_SIGN", "_SIGN"));
        if (name.equals("WALL_TORCH")) return Material.TORCH;
        if (name.equals("SOUL_WALL_TORCH")) return Material.SOUL_TORCH;
        return material;
    }

    int itemCount() {
        return "double".equals(properties.get("type")) ? 2 : 1;
    }

    /** Required attachment support; structural blocks can be placed against any neighbor. */
    BuildMemoryPattern.Cell support(BuildMemoryPattern.Cell c) {
        String name = material.name();
        if (name.endsWith("_WALL_SIGN") || name.endsWith("WALL_TORCH")) {
            return switch (properties.get("facing")) {
                case "north" -> new BuildMemoryPattern.Cell(c.x(), c.y(), c.z() + 1);
                case "south" -> new BuildMemoryPattern.Cell(c.x(), c.y(), c.z() - 1);
                case "east" -> new BuildMemoryPattern.Cell(c.x() - 1, c.y(), c.z());
                default -> new BuildMemoryPattern.Cell(c.x() + 1, c.y(), c.z());
            };
        }
        if (name.endsWith("_SIGN")
                || name.endsWith("TORCH")
                || name.equals("LANTERN")
                || name.equals("SOUL_LANTERN"))
            return new BuildMemoryPattern.Cell(
                    c.x(), c.y() + ("true".equals(properties.get("hanging")) ? 1 : -1), c.z());
        return null;
    }

    MemoryBlock rotate(int turns) {
        Map<String, String> rotated = new HashMap<>(properties);
        int steps = Math.floorMod(turns, 4);
        List<String> directions = List.of("north", "east", "south", "west");
        String facing = rotated.get("facing");
        if (facing != null)
            rotated.put("facing", directions.get((directions.indexOf(facing) + steps) % 4));
        if (rotated.containsKey("rotation"))
            rotated.put(
                    "rotation",
                    Integer.toString((Integer.parseInt(rotated.get("rotation")) + steps * 4) % 16));
        String axis = rotated.get("axis");
        if (steps % 2 != 0 && ("x".equals(axis) || "z".equals(axis)))
            rotated.put("axis", axis.equals("x") ? "z" : "x");
        return new MemoryBlock(material, rotated);
    }
}
