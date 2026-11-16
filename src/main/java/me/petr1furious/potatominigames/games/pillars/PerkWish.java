package me.petr1furious.potatominigames.games.pillars;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import org.bukkit.Material;

/** English registry names, stable alphabetical tie-breaking, one ordinary item per wish. */
final class PerkWish {
    static String normalize(String text) {
        return text.trim().toLowerCase(Locale.ROOT).replace("minecraft:", "").replace(' ', '_').replace('-', '_');
    }
    static Material closest(String text) {
        return closest(text, Arrays.stream(Material.values()).filter(m -> !m.isLegacy() && m != Material.AIR && m != Material.CAVE_AIR && m != Material.VOID_AIR && m.isItem()).toList());
    }
    static Material closest(String text, java.util.Collection<Material> candidates) {
        String name = normalize(text);
        return candidates.stream().filter(m -> m != Material.AIR && m != Material.CAVE_AIR && m != Material.VOID_AIR)
                .min(Comparator.<Material>comparingInt(m -> distance(name, m.name().toLowerCase(Locale.ROOT)))
                        .thenComparing(Material::name)).orElse(Material.STONE);
    }
    static int distance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) previous[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            int[] current = new int[b.length() + 1]; current[0] = i;
            for (int j = 1; j <= b.length(); j++) current[j] = Math.min(Math.min(previous[j] + 1, current[j - 1] + 1),
                    previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
            previous = current;
        }
        return previous[b.length()];
    }
}
