package me.petr1furious.potatominigames.games.party;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Distinct counts ensure that the announced number has exactly one correct species. */
record MobCountRound(int number, Map<Species, Integer> counts, List<Species> platforms, Species answer) {
    enum Species {
        ZOMBIE("Зомби", "ZOMBIE_HEAD"), SKELETON("Скелет", "SKELETON_SKULL"),
        CREEPER("Крипер", "CREEPER_HEAD"), PIGLIN("Пиглин", "PIGLIN_HEAD"),
        WITHER_SKELETON("Визер-скелет", "WITHER_SKELETON_SKULL");
        final String title;
        final String head;
        Species(String title, String head) { this.title = title; this.head = head; }
    }
    MobCountRound {
        counts = Map.copyOf(counts);
        platforms = List.copyOf(platforms);
    }
    static MobCountRound generate(int round, int platformCount, Random random) {
        int types = round < 4 ? 4 : 5;
        List<Species> species = new ArrayList<>(List.of(Species.values()));
        Collections.shuffle(species, random);
        species = new ArrayList<>(species.subList(0, types));
        int base = Math.min(10, 1 + Math.max(0, round - 1) / 2);
        List<Integer> quantities = new ArrayList<>();
        for (int i = 0; i < types; i++) quantities.add(base + i);
        Collections.shuffle(quantities, random);
        Map<Species, Integer> counts = new LinkedHashMap<>();
        for (int i = 0; i < types; i++) counts.put(species.get(i), quantities.get(i));
        List<Species> platforms = new ArrayList<>();
        for (int i = 0; i < platformCount; i++) platforms.add(species.get(i % types));
        Collections.shuffle(platforms, random);
        return new MobCountRound(round, counts, platforms, species.get(random.nextInt(types)));
    }
    int target() { return counts.get(answer); }
    int total() { return counts.values().stream().mapToInt(Integer::intValue).sum(); }
    static int countSeconds(int round, int initial) { return Math.max(6, initial - Math.max(0, round - 1) / 3); }
}
