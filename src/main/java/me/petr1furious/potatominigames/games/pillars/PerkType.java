package me.petr1furious.potatominigames.games.pillars;

import java.util.Locale;
import java.util.random.RandomGenerator;
import org.bukkit.Material;

/** The weight is conditional on rolling a perk, not on the entire ordinary item registry. */
enum PerkType {
    SAVE(Material.FEATHER, "Спасатель", 1, false, "ПКМ • платформа 3×3 из слизи на 10 с"),
    REROLL(Material.QUARTZ_BLOCK, "Кубик шулера", 3, false, "ПКМ • заменить последнюю целую выдачу"),
    STEAL(Material.FISHING_ROD, "Воровской крюк", 1, false, "Поймай соперника крючком • украсть предмет из руки"),
    GHOST(Material.GHAST_TEAR, "Призрак", 1, false, "ПКМ • спектатор на 4 с"),
    ANCHOR(Material.IRON_BOOTS, "Якорь", 3, false, "Надень • 3 попадания без отталкивания, урон остаётся"),
    JUMP(Material.RABBIT_FOOT, "Воздушный шаг", 3, false, "Прыжок в воздухе • 3 дополнительных отталкивания"),
    REWIND(Material.CLOCK, "Отмотка", 1, false, "ПКМ • вернуться на 3 с назад, без отката здоровья и лута"),
    HOUSE(Material.BRICK, "Карманный домик", 1, false, "ПКМ • деревянный домик всего 3×3 снаружи"),
    FREEZE(Material.BLUE_ICE, "Ледяной луч", 1, false, "ПКМ • луч до 32 м, заморозка на 4 с", "Одна попытка • промах тоже расходует предмет"),
    BOW(Material.BOW, "До свидания", 1, true, "Один выстрел • Откидывание 100 • стрела в комплекте"),
    CROSSBOW(Material.CROSSBOW, "TNT-почта", 3, true, "ПКМ • бросок TNT, фитиль 2 с • 3 выстрела"),
    PLANKS(Material.LEATHER_BOOTS, "Плотник", 1, false, "Надень • доски под ногами 5 с", "Таймер запускается один раз, переодевание не помогает"),
    BREAK(Material.BLAZE_ROD, "Дезинтегратор", 1, false, "Держи ПКМ и прицел на блоке 6 с • ломает даже бедрок"),
    SHEEP(Material.SHEEP_SPAWN_EGG, "Камикадзе", 1, false, "ПКМ • овца с TNT преследует ближайшего соперника"),
    SMOKE(Material.INK_SAC, "Дымовая завеса", 1, false, "ПКМ • дым на 5 с, слепота только соперникам"),
    FIREBALL(Material.FIRE_CHARGE, "Фаербол", 1, false, "ПКМ • бросить взрывающийся огненный шар");

    final Material material;
    final String title;
    final int charges;
    final boolean legendary;
    final String[] instructions;
    PerkType(Material material, String title, int charges, boolean legendary, String... instructions) {
        this.material = material; this.title = title; this.charges = charges;
        this.legendary = legendary; this.instructions = instructions;
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
