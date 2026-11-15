package me.petr1furious.potatominigames.games.pillars;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.random.RandomGenerator;

/** Pure profile checks: no Bukkit server, native classes, timing, or statistical tolerance. */
public final class PillarsSkyCheck {
    public static void main(String[] args) {
        for (int branch = 0; branch < 3; branch++) {
            Scripted random = new Scripted(branch, 173);
            PillarsSky.Profile profile = PillarsSky.roll(random).profile();
            check(profile.mode().ordinal() == branch, "Each result of nextInt(3) selects exactly one sky mode");
            check(random.calls == (branch == 2 ? 2 : 1), "Only custom skies consume a second palette draw");
            check(profile.time() == (branch == 0 ? 6000 : 18000), "Day is noon and both night modes are midnight");
            check(profile.id().equals(branch == 2 ? "potatominigames:pillars_sky_173" : "minecraft:overworld"),
                    "Profiles refer to registered vanilla or generated dimension identities");
        }
        Set<Integer> hues = new HashSet<>();
        Set<Float> intensities = new HashSet<>();
        Set<PillarsSky.Profile> profiles = new HashSet<>();
        for (int index = 0; index < PillarsSky.PALETTE_SIZE; index++) {
            PillarsSky.Profile profile = PillarsSky.palette(index);
            check(profile.mode() == PillarsSky.Mode.CUSTOM && profile.paletteIndex() == index,
                    "Every palette entry identifies itself as a custom profile");
            check(profile.time() == 18000, "Custom skies remain at fixed night time");
            int red = profile.rgb() >> 16 & 255, green = profile.rgb() >> 8 & 255, blue = profile.rgb() & 255;
            check(Math.max(red, Math.max(green, blue)) == 230 && Math.min(red, Math.min(green, blue)) == 46,
                    "Random hues remain saturated and visible rather than near-black or grey");
            check(Float.isFinite(profile.starBrightness()) && profile.starBrightness() > 0
                            && profile.starBrightness() <= 1 && profile.starBrightness() != 0.5f,
                    "Stars have finite positive bounded intensities distinct from vanilla midnight");
            hues.add(profile.rgb());
            intensities.add(profile.starBrightness());
            profiles.add(profile);
            Scripted random = new Scripted(2, index);
            check(PillarsSky.roll(random).profile().equals(profile), "Every palette entry is selectable");
        }
        check(hues.size() == 64 && intensities.size() == 4 && profiles.size() == 256,
                "The complete palette spans sixty-four hues and four star intensities");
        Random first = new Random(61732), second = new Random(61732);
        for (int i = 0; i < 1000; i++)
            check(PillarsSky.roll(first).profile().equals(PillarsSky.roll(second).profile()),
                    "Equal random seeds reproduce complete sky profiles");
        System.out.println("Pillars sky: exact three-way draw, complete palette, night time and deterministic generation verified");
    }

    private static final class Scripted implements RandomGenerator {
        private final int branch, palette;
        private int calls;
        private Scripted(int branch, int palette) { this.branch = branch; this.palette = palette; }
        @Override public long nextLong() { throw new AssertionError("Sky selection must use bounded integer draws"); }
        @Override public int nextInt(int bound) {
            calls++;
            check(bound == (calls == 1 ? 3 : PillarsSky.PALETTE_SIZE), "The first draw has exactly three equal outcomes");
            check(calls <= 2, "Sky selection performs no extra random draws");
            return calls == 1 ? branch : palette;
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
