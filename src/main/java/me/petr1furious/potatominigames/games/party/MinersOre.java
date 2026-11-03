package me.petr1furious.potatominigames.games.party;

import org.bukkit.Material;

enum MinersOre {
    COAL(Material.COAL_ORE, 80, "Уголь: еда и кирки"),
    IRON(Material.IRON_ORE, 64, "Железо: случайная броня"),
    DIAMOND(Material.DIAMOND_ORE, 24, "Алмазы: оружие и боеприпасы"),
    GOLD(Material.GOLD_ORE, 16, "Золото: каждые 4 руды +2 сердца, до 14"),
    LAPIS(Material.LAPIS_ORE, 24, "Лазурит: зелья"),
    REDSTONE(Material.REDSTONE_ORE, 32, "Редстоун: TNT, жемчуг и заряды ветра"),
    EMERALD(Material.EMERALD_ORE, 8, "Изумруды: редкий лут из разных категорий");
    final Material material;
    final int perCell;
    final String description;
    MinersOre(Material material, int perCell, String description) {
        this.material = material; this.perCell = perCell; this.description = description;
    }
}
