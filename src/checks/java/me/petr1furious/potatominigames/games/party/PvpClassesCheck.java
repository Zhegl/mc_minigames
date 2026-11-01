package me.petr1furious.potatominigames.games.party;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.util.Vector;

/** Kit layout, potion targeting/caps, random eligibility and combat-passive rules. */
public final class PvpClassesCheck {
    public static void main(String[] args) {
        loadouts();
        randomEligibility();
        combatRules();
        System.out.println("PvP classes: " + PvpClass.values().length + " equally eligible kits, mage potions, wind charges, slot safety, refill caps, frost/backstab/leech cooldowns and safe movement passed");
    }
    private static void loadouts() {
        for (PvpClass kit : PvpClass.values()) {
            Set<Integer> occupied = new HashSet<>(List.of(0, 1, 2, 7, PvpClass.FOOD_SLOT));
            for (var supply : kit.supplies()) {
                check(supply.slot() >= 0 && supply.slot() < 36 && occupied.add(supply.slot()), "Supplies cannot overwrite common kit items: " + kit);
                check(supply.amount() > 0 && supply.amount() <= 64, "Supply stack has a valid size");
            }
            boolean bow = kit.supplies().stream().anyMatch(slot -> slot.material() == Material.BOW || slot.material() == Material.CROSSBOW);
            check(bow == kit.unlimitedArrows(), "Every bow/crossbow class has unlimited ammo");
            if (bow) check(kit.supplies().stream().anyMatch(slot -> slot.material() == Material.ARROW && slot.amount() == 64), "Every ranged kit starts with a full arrow stack");
            int healing = 0;
            for (var potion : kit.potions()) {
                check(potion.slot() >= 0 && potion.slot() < 36 && occupied.add(potion.slot()), "Potions cannot overwrite weapons, shears, blocks, food, ammo or ability: " + kit);
                check(potion.amount() > 0 && potion.amount() <= 8, "Potion stack fits its custom stack limit");
                if (potion.kind() == PvpPotion.HEALING) healing += potion.amount();
            }
            check(healing > 0 && healing <= 3, "Each kit has limited healing");
            check(kit.armor().helmet() != null && kit.armor().chestplate() != null && kit.armor().leggings() != null && kit.armor().boots() != null, "All four armour slots assigned");
            check(kit.primaryWeapon() != null && kit.cooldown >= 14 && kit.cooldown <= 24, "Every class has a weapon and bounded ability cooldown");
        }
        check(PvpClass.ALCHEMIST.potions().stream().mapToInt(PvpClass.PotionSlot::amount).sum() == 12, "Mage has twelve starting potions");
        check(PvpClass.ALCHEMIST.potions().stream().map(PvpClass.PotionSlot::kind).distinct().count() == 6, "Mage has six potion types");
        check(PvpClass.ENGINEER.blocks() > PvpClass.GUARDIAN.blocks(), "Engineer has a larger building reserve");
        check(PvpClass.GUARDIAN.armor().boots() == Material.NETHERITE_BOOTS, "Guardian retains its heavier armour identity");
        check(PvpClass.ALCHEMIST.armor().chestplate() == Material.LEATHER_CHESTPLATE, "Mage's potion arsenal has a lighter-armour tradeoff");
        check(PvpClass.AERONAUT.primaryWeapon() == Material.MACE, "Aerial knight gets a real mace");
        check(PvpClass.AERONAUT.supplies().stream().anyMatch(slot -> slot.material() == Material.WIND_CHARGE && slot.amount() == 8), "Aerial knight has eight native wind charges");
        check(PvpClass.AERONAUT.supplies().stream().anyMatch(slot -> slot.material() == Material.STONE_SWORD), "Aerial knight can fight on the ground");
        check(PvpClass.CRYOMANCER.supplies().stream().anyMatch(slot -> slot.material() == Material.SNOWBALL && slot.amount() == 16), "Cryomancer starts with sixteen projectiles");
        check(PvpClass.LANCER.primaryWeapon() == Material.IRON_SPEAR, "Lancer uses the native iron spear");
        check(PvpClass.LANCER.supplies().stream().anyMatch(slot -> slot.material() == Material.STONE_SWORD), "Lancer retains a backup after losing their horse");
        check(PvpClass.LANCER.armor().chestplate() == Material.LEATHER_CHESTPLATE, "Mounted mobility has a light-armour tradeoff");
        for (PvpPotion potion : PvpClass.ALCHEMIST.focusPotions()) {
            check(potion.harmful, "Focus cannot regenerate healing or speed potions");
            check(PvpClass.ALCHEMIST.potions().stream().anyMatch(slot -> slot.kind() == potion), "Focus only supplies existing mage spells");
        }
        for (PvpPotion potion : PvpPotion.values()) {
            check(!potion.affects(false, false) && !potion.affects(true, false), "Potions never affect spectators or eliminated players");
            check(potion.affects(true, true) != potion.affects(false, true), "Beneficial and harmful splashes have separate targets");
            check(potion.affects(false, true) == potion.harmful, "Only harmful potions affect opponents");
        }
        for (int owned = 0; owned <= 8; owned++) for (int amount = 0; amount <= 3; amount++) for (int cap = 0; cap <= 4; cap++) {
            int added = PvpPotion.refillAmount(owned, amount, cap);
            check(added >= 0 && added <= amount, "Refill cannot exceed request or become negative");
            if (owned <= cap) check(owned + added <= cap, "Refill never exceeds stock cap");
            else check(added == 0, "Over-cap inventory cannot be refilled");
        }
    }
    private static void randomEligibility() {
        PvpClass[] classes = PvpClass.values();
        int n = classes.length;
        check(n == 11, "Mounted lancer joins the existing ten classes");
        int[][] pairs = new int[n][n];
        for (int first = 0; first < n; first++) for (int second = 0; second < n - 1; second++) {
            int a = first, b = second;
            var drawn = PvpClass.opening(new Random(0) {
                int calls;
                @Override public int nextInt(int bound) {
                    if (calls++ == 0) { check(bound == n, "First draw covers all classes"); return a; }
                    check(bound == n - 1, "Second draw excludes exactly one class"); return b;
                }
            });
            pairs[drawn.getFirst().ordinal()][drawn.getLast().ordinal()]++;
        }
        for (int a = 0; a < n; a++) for (int b = 0; b < n; b++) check(pairs[a][b] == (a == b ? 0 : 1), "Every distinct matchup has equal probability");
    }
    private static void combatRules() {
        PvpCombat combat = new PvpCombat();
        UUID a = new UUID(0, 1), b = new UUID(0, 2);
        Vector forward = new Vector(0, 0, 1), behind = new Vector(0, 4, -2);
        check(!PvpCombat.isBehind(forward, forward) && !PvpCombat.isBehind(forward, new Vector(1, 0, 0)), "Front and side hits are not backstabs");
        check(PvpCombat.isBehind(forward, behind), "Behind calculation ignores height differences");
        check(!PvpCombat.isBehind(forward, new Vector()), "Overlapping players cannot fabricate a backstab");
        check(!combat.backstab(a, 0, forward, forward), "Invalid angle does not spend passive cooldown");
        check(combat.backstab(a, 0, forward, behind) && !combat.backstab(a, 4, forward, behind), "Backstab has a five-second cooldown");
        check(combat.backstab(a, 5, forward, behind) && combat.backstab(b, 5, forward, behind), "Backstab is per player");
        check(combat.leech(a, 0) && !combat.leech(a, 2) && combat.leech(a, 3), "Lifesteal has a three-second cooldown");
        check(combat.frost(a, 0) && !combat.frost(a, 1) && combat.frost(a, 2), "Frost projectiles cannot continuously refresh slow effects");
        combat.forget(a);
        check(combat.leech(a, 3) && combat.backstab(a, 5, forward, behind) && combat.frost(a, 2), "Leaving clears personal passive state");
        combat.reset();
        check(combat.leech(a, 0) && combat.backstab(a, 0, forward, behind) && combat.frost(a, 0), "Each new round resets passives");
        check(!PvpCombat.canPayFury(.5) && !PvpCombat.canPayFury(2) && PvpCombat.canPayFury(2.01), "Fury cannot kill its caster or grant health at low HP");
        Vector retreat = PvpCombat.dash(forward, true), advance = PvpCombat.dash(forward, false);
        check(retreat.getZ() < 0 && advance.getZ() > 0 && advance.getY() == .2 && retreat.getY() == .2, "Ranger retreats while duelist advances");
        check(Double.isFinite(PvpCombat.dash(new Vector(0, 1, 0), false).getZ()), "Looking vertically never creates NaN dash velocity");
        Vector momentum = new Vector(4, -3, 4);
        Vector launched = PvpCombat.launch(momentum);
        check(launched.getY() == .9 && launched.clone().setY(0).length() <= .600001, "Wind leap gains height without runaway horizontal velocity");
        check(momentum.equals(new Vector(4, -3, 4)), "Wind leap does not mutate input velocity");
        check(forward.equals(new Vector(0, 0, 1)) && behind.equals(new Vector(0, 4, -2)), "Combat math does not mutate input locations");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
