package me.petr1furious.potatominigames.games.pillars;

import java.util.Locale;
import java.util.random.RandomGenerator;
import org.bukkit.Material;
import me.petr1furious.potatominigames.i18n.I18n;
import net.kyori.adventure.text.Component;

/** The weight is conditional on rolling a perk, not on the entire ordinary item registry. */
enum PerkType {
    SAVE(Material.FEATHER, "save", 1, false, 1),
    REROLL(Material.QUARTZ_BLOCK, "reroll", 3, false, 1),
    STEAL(Material.FISHING_ROD, "steal", 1, false, 1),
    GHOST(Material.GHAST_TEAR, "ghost", 1, false, 1),
    ANCHOR(Material.IRON_BOOTS, "anchor", 3, false, 1),
    JUMP(Material.RABBIT_FOOT, "jump", 3, false, 1),
    REWIND(Material.CLOCK, "rewind", 1, false, 1),
    HOUSE(Material.BRICK, "house", 1, false, 1),
    FREEZE(Material.BLUE_ICE, "freeze", 1, false, 2),
    BOW(Material.BOW, "bow", 1, true, 1),
    CROSSBOW(Material.CROSSBOW, "crossbow", 3, true, 1),
    PLANKS(Material.LEATHER_BOOTS, "planks", 1, false, 2),
    BREAK(Material.BLAZE_ROD, "break", 1, false, 1),
    SHEEP(Material.SHEEP_SPAWN_EGG, "sheep", 1, false, 1),
    SMOKE(Material.INK_SAC, "smoke", 1, false, 1),
    FIREBALL(Material.FIRE_CHARGE, "fireball", 1, false, 1),
    PUMPKIN(Material.CARVED_PUMPKIN, "pumpkin", 1, false, 2),
    ATOMIC_BOMB(Material.TNT, "atomic_bomb", 1, false, 2),
    ANVIL_RAIN(Material.STICK, "anvil_rain", 1, false, 2),
    TRANSMUTE(Material.AMETHYST_SHARD, "transmute", 1, false, 2),
    BLESSING(Material.NETHER_STAR, "blessing", 1, false, 2),
    GRAVITY(Material.ENDER_EYE, "gravity", 1, false, 2),
    FAKE_BLOCK(Material.OAK_PLANKS, "fake_block", 4, false, 2),
    PILLS(Material.SUGAR, "pills", 1, false, 2),
    DECOY(Material.ZOMBIE_SPAWN_EGG, "decoy", 1, false, 3),
    TREE(Material.OAK_SAPLING, "tree", 1, false, 1),
    AQUARIUM(Material.PUFFERFISH_BUCKET, "aquarium", 1, false, 1),
    DRONE(Material.BOW, "drone", 1, false, 2),
    AURA(Material.ECHO_SHARD, "aura", 1, false, 1),
    INVULNERABILITY(Material.TOTEM_OF_UNDYING, "invulnerability", 1, false, 1),
    METRO(Material.POWERED_RAIL, "metro", 1, false, 1),
    PUPPET(Material.BOW, "puppet", 1, false, 2),
    GENIE(Material.OAK_SIGN, "genie", 1, false, 2),
    INFECTION(Material.SLIME_BLOCK, "infection", 1, false, 2),
    LIGHTNING(Material.LIGHTNING_ROD, "lightning", 1, false, 1),
    APOCALYPSE(Material.ROTTEN_FLESH, "apocalypse", 1, false, 2),
    BUNKER(Material.OBSIDIAN, "bunker", 1, false, 1),
    BOUNCER(Material.SLIME_BALL, "bouncer", 1, false, 1),
    AIR(Material.LIGHT_GRAY_DYE, "air", 1, false, 1),
    PROTRACTOR(Material.COMPASS, "protractor", 1, false, 1),
    PAUSE(Material.RECOVERY_COMPASS, "pause", 1, false, 1),
    TIME(Material.CLOCK, "time", 1, false, 1),
    WEATHER(Material.LIGHTNING_ROD, "weather", 1, false, 1),
    GARDEN(Material.WHEAT_SEEDS, "garden", 1, false, 1),
    LONG_ARM(Material.BAMBOO, "long_arm", 1, false, 1),
    HAT(Material.LEATHER_HELMET, "hat", 1, false, 1),
    PILLAR(Material.BEDROCK, "pillar", 1, false, 1),
    MAZE(Material.MOSSY_STONE_BRICKS, "maze", 1, false, 1),
    SHUFFLER(Material.SHULKER_SHELL, "shuffler", 1, false, 1),
    AGI(Material.ARMOR_STAND, "agi", 1, true, 1),
    SUPER_APPLE(Material.ENCHANTED_GOLDEN_APPLE, "super_apple", 1, true, 1),
    DANCE_FLOOR(Material.MUSIC_DISC_LAVA_CHICKEN, "dance_floor", 1, false, 1),
    MILK(Material.MILK_BUCKET, "milk", 1, false, 1),
    SWAPPER(Material.ENDER_PEARL, "swapper", 1, false, 1),
    SCAFFOLD(Material.SCAFFOLDING, "scaffold", 1, false, 1);

    final Material material;
    final Component title;
    final int charges;
    final boolean legendary;
    final Component[] instructions;
    PerkType(Material material, String key, int charges, boolean legendary, int instructionCount) {
        this.material = material;
        this.title = I18n.tr("pillars.effects.perk." + key + ".title");
        this.charges = charges;
        this.legendary = legendary;
        this.instructions = new Component[instructionCount];
        for (int i = 0; i < instructionCount; i++)
            this.instructions[i] = I18n.tr("pillars.effects.perk." + key + ".instruction." + (i + 1));
    }
    String id() { return name().toLowerCase(Locale.ROOT); }
    static int totalWeight() { return java.util.Arrays.stream(values()).mapToInt(t -> t.legendary ? 1 : 4).sum(); }
    static PerkType atWeight(int roll) {
        if (roll < 0 || roll >= totalWeight()) throw new IllegalArgumentException("Invalid perk roll");
        for (PerkType type : values()) { roll -= type.legendary ? 1 : 4; if (roll < 0) return type; }
        throw new AssertionError();
    }
    static PerkType roll(RandomGenerator random) { return atWeight(random.nextInt(totalWeight())); }
}
