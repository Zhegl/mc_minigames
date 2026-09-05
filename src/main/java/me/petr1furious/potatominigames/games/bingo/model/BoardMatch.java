package me.petr1furious.potatominigames.games.bingo.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;

public final class BoardMatch {
    private final BoardMatchType type;
    private final Material material;
    private final NamespacedKey tagKey;
    private final List<Material> materials;

    private BoardMatch(BoardMatchType type, Material material, NamespacedKey tagKey, List<Material> materials) {
        this.type = type;
        this.material = material;
        this.tagKey = tagKey;
        this.materials = materials;
    }

    public static BoardMatch forMaterial(Material material) {
        return new BoardMatch(BoardMatchType.MATERIAL, material, null, List.of(material));
    }

    public static BoardMatch forTag(NamespacedKey tagKey) {
        return new BoardMatch(BoardMatchType.TAG, null, tagKey, List.of());
    }

    public static BoardMatch forMaterials(List<Material> materials) {
        return new BoardMatch(BoardMatchType.LIST, null, null, List.copyOf(materials));
    }

    public BoardMatchType type() {
        return type;
    }

    public boolean matches(Material candidate) {
        return switch (type) {
            case MATERIAL -> candidate == material;
            case TAG -> {
                Tag<Material> tag = Bukkit.getTag("items", tagKey, Material.class);
                yield tag != null && tag.isTagged(candidate);
            }
            case LIST -> materials.contains(candidate);
        };
    }

    public List<Material> acceptedMaterials() {
        if (type == BoardMatchType.MATERIAL || type == BoardMatchType.LIST) {
            return materials;
        }
        Tag<Material> tag = Bukkit.getTag("items", tagKey, Material.class);
        if (tag == null) {
            return List.of();
        }
        List<Material> result = new ArrayList<>();
        for (Material material : Material.values()) {
            if (!material.isItem()) {
                continue;
            }
            if (tag.isTagged(material)) {
                result.add(material);
            }
        }
        return Collections.unmodifiableList(result);
    }
}
