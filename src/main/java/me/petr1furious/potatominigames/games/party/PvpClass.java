package me.petr1furious.potatominigames.games.party;

import java.util.List;
import java.util.Random;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

enum PvpClass {
    GUARDIAN("Страж", "Тяжёлая броня, щит, 2 лечения и замедление; Бастион на 4 с", "Бастион", 22),
    RANGER("Стрелок", "Лук Сила I, особые стрелы по приоритету и бесконечные обычные; отскок назад", "Отскок", 16),
    BERSERKER("Берсерк", "Топор и меч, вампиризм; ярость на 4 с стоит одно сердце", "Кровавая ярость", 22),
    DUELIST("Дуэлянт", "Острый меч, 2 жемчуга, удар в спину; рывок вперёд", "Выпад", 14),
    ALCHEMIST("Маг", "Лёгкая броня, клинок Отдача I и 12 зелий; восполнение боевых зелий", "Магический запас", 24),
    ENGINEER("Инженер", "Арбалет с перезарядкой III и пробиванием I, 64 блока; укрепление", "Укрепление", 24),
    AERONAUT("Воздушный рыцарь", "Булава Плотность I, 8 зарядов ветра и запасной меч; воздушный скачок", "Воздушный скачок", 20),
    PALADIN("Паладин", "Железный меч, щит и лёгкая броня; лечение и очищение", "Очищение", 24),
    CRYOMANCER("Криомант", "Острый меч и 16 ледяных снежков; пополнение снежков и мороз", "Стужа", 20),
    PYROMANCER("Пиромант", "Огненные меч и лук, бесконечные стрелы; огненная вспышка", "Вспышка", 20),
    LANCER("Всадник", "Железное копьё, запасной меч и конь на один раунд; лёгкая броня", "Пришпорить", 20),
    SHADOW("Тень", "Без брони: алмазный меч, 2 зелья невидимости по 20 с и палка мрака", "Палка мрака", 20),
    HARPOONER("Гарпунщик", "Трезубец Верность III, запасной меч и средняя броня; Тягун без воды", "Тягун", 20),
    THUNDERER("Громовержец", "Грозовой жезл до 48 блоков, громовой топор и средняя броня; статика подсвечивает врага", "Грозовой разряд", 6),
    DEMOLITIONIST("Подрывник", "Каменный меч, 12 TNT: ПКМ — поставить, ЛКМ — бросить; взрывозащита II", "Боезапас", 18);

    private static final NamespacedKey SHADOW_WAND = new NamespacedKey("potatominigames", "shadow_wand");
    private static final int ARROW_STACK = 64;
    static final int FOOD_SLOT = 8;
    record PotionSlot(PvpPotion kind, int amount, int slot) { }
    record SupplySlot(Material material, int amount, int slot) { }
    record Armor(Material helmet, Material chestplate, Material leggings, Material boots) { }
    final String title;
    final String description;
    final String ability;
    final int cooldown;

    PvpClass(String title, String description, String ability, int cooldown) {
        this.title = title; this.description = description; this.ability = ability; this.cooldown = cooldown;
    }
    /** Swap the random matchup, then use an identical random kit for the deciding round. */
    static List<PvpClass> opening(Random random) {
        int first = random.nextInt(values().length);
        int other = random.nextInt(values().length - 1);
        return List.of(values()[first], values()[other >= first ? other + 1 : other]);
    }
    Material primaryWeapon() {
        return switch (this) {
            case BERSERKER, THUNDERER -> Material.IRON_AXE;
            case DUELIST, PALADIN -> Material.IRON_SWORD;
            case RANGER, ALCHEMIST, PYROMANCER -> Material.WOODEN_SWORD;
            case AERONAUT -> Material.MACE;
            case LANCER -> Material.IRON_SPEAR;
            case SHADOW -> Material.DIAMOND_SWORD;
            case HARPOONER -> Material.TRIDENT;
            default -> Material.STONE_SWORD;
        };
    }
    Armor armor() {
        return switch (this) {
            case GUARDIAN -> new Armor(Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.NETHERITE_BOOTS);
            case RANGER -> new Armor(Material.IRON_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS);
            case BERSERKER, DUELIST, HARPOONER, THUNDERER -> new Armor(Material.IRON_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.IRON_BOOTS);
            case ALCHEMIST -> new Armor(Material.GOLDEN_HELMET, Material.LEATHER_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.IRON_BOOTS);
            case ENGINEER -> new Armor(Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.IRON_BOOTS);
            case AERONAUT -> new Armor(Material.IRON_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.LEATHER_BOOTS);
            case PALADIN, PYROMANCER -> new Armor(Material.IRON_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.IRON_BOOTS);
            case LANCER -> new Armor(Material.CHAINMAIL_HELMET, Material.LEATHER_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.IRON_BOOTS);
            case SHADOW -> new Armor(Material.AIR, Material.AIR, Material.AIR, Material.AIR);
            case DEMOLITIONIST -> new Armor(Material.IRON_HELMET, Material.LEATHER_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.IRON_BOOTS);
            case CRYOMANCER -> new Armor(Material.IRON_HELMET, Material.LEATHER_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS);
        };
    }
    int blocks() { return this == ENGINEER ? 64 : 32; }
    boolean unlimitedArrows() { return this == RANGER || this == ENGINEER || this == PYROMANCER; }
    List<SupplySlot> supplies() {
        return switch (this) {
            case GUARDIAN, ALCHEMIST, PALADIN, SHADOW -> List.of();
            case RANGER -> List.of(new SupplySlot(Material.BOW, 1, 3), new SupplySlot(Material.ARROW, ARROW_STACK, PvpArrow.NORMAL_SLOT));
            case PYROMANCER -> List.of(new SupplySlot(Material.BOW, 1, 3), new SupplySlot(Material.ARROW, ARROW_STACK, 9));
            case BERSERKER, LANCER, HARPOONER, THUNDERER -> List.of(new SupplySlot(Material.STONE_SWORD, 1, 3));
            case DUELIST -> List.of(new SupplySlot(Material.ENDER_PEARL, 2, 3));
            case ENGINEER -> List.of(new SupplySlot(Material.CROSSBOW, 1, 3), new SupplySlot(Material.ARROW, ARROW_STACK, 9));
            case AERONAUT -> List.of(new SupplySlot(Material.WIND_CHARGE, 8, 3), new SupplySlot(Material.STONE_SWORD, 1, 4));
            case DEMOLITIONIST -> List.of(new SupplySlot(Material.TNT, 12, 3));
            case CRYOMANCER -> List.of(new SupplySlot(Material.SNOWBALL, 16, 3));
        };
    }
    List<PvpArrow> specialArrows() { return this == RANGER ? List.of(PvpArrow.values()) : List.of(); }
    List<PvpPotion> focusPotions() {
        return this == ALCHEMIST ? List.of(PvpPotion.HARMING, PvpPotion.POISON, PvpPotion.SLOWNESS) : List.of();
    }
    String abilityDescription() {
        return switch (this) {
            case DEMOLITIONIST -> "+4 TNT, запас до 12; бросок ЛКМ раз в 2 с, установка ПКМ раз в 0,3 с";
            case SHADOW -> "Удар палкой: слепота на 2 с и замедление II на 3 с; без урона, щит блокирует";
            case THUNDERER -> "Жезл показывает цель до 48 блоков; ПКМ — удар через 1 с, радиус 3 блока, от 4 сердец в центре до 1,5 на краю до брони";
            case HARPOONER -> "Рывок с вращением на 0,6 с без воды; до 3 сердец до брони и отбрасывание, одно попадание по цели";
            case LANCER -> "Ускоряет твоего коня на 3 с; пешком даёт скорость I на 3 с";
            case GUARDIAN -> "Сопротивление II на 4 с, но замедляет тебя на 4 с";
            case RANGER -> "Отскок назад и скорость I на 4 с";
            case BERSERKER -> "Сила I и скорость I на 4 с; цена — одно сердце";
            case DUELIST -> "Рывок вперёд и скорость I на 3 с";
            case ALCHEMIST -> "+1 урон, яд и замедление; не больше двух каждого";
            case ENGINEER -> "4 сердца щита на 6 с; +16 блоков, запас до 64";
            case AERONAUT -> "Прыжок вверх и +4 заряда ветра, запас до 8";
            case PALADIN -> "Лечит 1,5 сердца, снимает яд, слабость, замедление, слепоту и огонь; защита на 2 с";
            case CRYOMANCER -> "+8 снежков, скорость на 3 с и замедление видимой цели рядом";
            case PYROMANCER -> "Поджигает видимую цель до 8 блоков на 5 с; 2 сердца щита на 4 с";
        };
    }
    String passiveDescription() {
        return switch (this) {
            case DEMOLITIONIST -> "Фитиль 2,5 с, до 4 активных TNT; взрывозащита II, свои взрывы тоже ранят; карта не разрушается";
            case SHADOW -> "2 взрывных зелья невидимости по 20 с без пополнения; удары не раскрывают, предметы в руках видны";
            case THUNDERER -> "Статика: попадание топором подсвечивает врага на 3 с, не чаще раза в 8 с; от молнии можно уйти или укрыться";
            case HARPOONER -> "Трезубец возвращается с Верностью III; щит блокирует рывок, бессмертия нет, нужен трезубец в инвентаре";
            case LANCER -> "Конь: 8 сердец, без брони и возрождения; копьё — до 4 сердец до брони цели";
            case GUARDIAN -> "Тяжёлая броня, устойчивые сапоги и защита от стрел";
            case RANGER -> "Стрелы слева направо: замедление → яд → слабость → подсветка → обычные; выбор — левой рукой";
            case BERSERKER -> "Удар в ближнем бою лечит полсердца, не чаще раза в 3 с";
            case DUELIST -> "Удар мечом в спину: +2 базового урона, не чаще раза в 5 с";
            case ALCHEMIST -> "Полезные зелья действуют только на тебя, вредные — на соперника";
            case ENGINEER -> "Арбалет: Быстрая перезарядка III, пробивание I; 64 блока для укрытий";
            case AERONAUT -> "Бей булавой при падении; Невесомость III на сапогах смягчает промах";
            case PALADIN -> "Меч и щит для ближнего боя; очищение снимает контроль и горение";
            case CRYOMANCER -> "Снежки замедляют на 2 с, не чаще раза в 2 с; карту не замораживают";
            case PYROMANCER -> "Не получает урон от огня; огненные атаки не поджигают карту";
        };
    }
    List<PotionSlot> potions() {
        return switch (this) {
            case GUARDIAN -> List.of(new PotionSlot(PvpPotion.SLOWNESS, 1, 3), new PotionSlot(PvpPotion.HEALING, 2, 6));
            case RANGER -> List.of(new PotionSlot(PvpPotion.SLOWNESS, 1, 4), new PotionSlot(PvpPotion.HEALING, 1, 6));
            case BERSERKER -> List.of(new PotionSlot(PvpPotion.SPEED, 2, 4), new PotionSlot(PvpPotion.HEALING, 1, 6));
            case DUELIST -> List.of(new PotionSlot(PvpPotion.SPEED, 1, 4), new PotionSlot(PvpPotion.HEALING, 1, 6));
            case ALCHEMIST -> List.of(new PotionSlot(PvpPotion.HARMING, 2, 3), new PotionSlot(PvpPotion.POISON, 2, 4),
                    new PotionSlot(PvpPotion.SLOWNESS, 2, 5), new PotionSlot(PvpPotion.HEALING, 3, 6),
                    new PotionSlot(PvpPotion.WEAKNESS, 2, 10), new PotionSlot(PvpPotion.SPEED, 1, 11));
            case ENGINEER -> List.of(new PotionSlot(PvpPotion.SLOWNESS, 2, 4), new PotionSlot(PvpPotion.HEALING, 1, 6));
            case AERONAUT -> List.of(new PotionSlot(PvpPotion.SPEED, 1, 5), new PotionSlot(PvpPotion.HEALING, 1, 6));
            case PALADIN, LANCER, HARPOONER, THUNDERER, DEMOLITIONIST -> List.of(new PotionSlot(PvpPotion.SPEED, 1, 4), new PotionSlot(PvpPotion.HEALING, 1, 6));
            case SHADOW -> List.of(new PotionSlot(PvpPotion.INVISIBILITY, 2, 3), new PotionSlot(PvpPotion.SPEED, 1, 4), new PotionSlot(PvpPotion.HEALING, 1, 6));
            case CRYOMANCER -> List.of(new PotionSlot(PvpPotion.SLOWNESS, 1, 4), new PotionSlot(PvpPotion.HEALING, 2, 6));
            case PYROMANCER -> List.of(new PotionSlot(PvpPotion.WEAKNESS, 1, 4), new PotionSlot(PvpPotion.HEALING, 1, 6));
        };
    }
    void equip(Player player, NamespacedKey abilityKey) {
        var inv = player.getInventory();
        Armor armor = armor();
        inv.setHelmet(armor.helmet() == Material.AIR ? null : item(armor.helmet()));
        inv.setChestplate(armor.chestplate() == Material.AIR ? null : item(armor.chestplate()));
        inv.setLeggings(armor.leggings() == Material.AIR ? null : item(armor.leggings()));
        inv.setBoots(armor.boots() == Material.AIR ? null : item(armor.boots()));
        ItemStack weapon = item(primaryWeapon());
        if (this == THUNDERER) weapon.editMeta(meta -> meta.displayName(Component.text("Громовой топор", NamedTextColor.AQUA)));
        if (this == HARPOONER) enchant(weapon, Enchantment.LOYALTY, 3);
        if (this == DUELIST || this == CRYOMANCER) enchant(weapon, Enchantment.SHARPNESS, 1);
        if (this == AERONAUT) {
            enchant(weapon, Enchantment.DENSITY, 1);
            ItemStack boots = inv.getBoots();
            enchant(boots, Enchantment.FEATHER_FALLING, 3);
            inv.setBoots(boots);
        }
        if (this == PYROMANCER) {
            enchant(weapon, Enchantment.FIRE_ASPECT, 1);
            effect(player, PotionEffectType.FIRE_RESISTANCE, 600, 0);
        }
        if (this == ALCHEMIST || this == CRYOMANCER) {
            if (this == ALCHEMIST) {
                enchant(weapon, Enchantment.KNOCKBACK, 1);
                weapon.editMeta(meta -> meta.displayName(Component.text("Ритуальный клинок", NamedTextColor.LIGHT_PURPLE)));
            }
            ItemStack robe = inv.getChestplate();
            robe.editMeta(LeatherArmorMeta.class, meta -> meta.setColor(Color.fromRGB(this == ALCHEMIST ? 0x793DA8 : 0x59CCED)));
            enchant(robe, Enchantment.PROTECTION, 2);
            inv.setChestplate(robe);
        }
        if (this == DEMOLITIONIST) {
            ItemStack chestplate = inv.getChestplate();
            chestplate.editMeta(LeatherArmorMeta.class, meta -> meta.setColor(Color.fromRGB(0xD16C32)));
            enchant(chestplate, Enchantment.BLAST_PROTECTION, 2);
            inv.setChestplate(chestplate);
        }
        if (this == GUARDIAN) {
            ItemStack chestplate = inv.getChestplate();
            enchant(chestplate, Enchantment.PROJECTILE_PROTECTION, 1);
            inv.setChestplate(chestplate);
        }
        inv.setItem(0, weapon);
        inv.setItem(1, item(Material.SHEARS));
        inv.setItem(2, new ItemStack(Material.WHITE_WOOL, blocks()));
        inv.setItem(FOOD_SLOT, new ItemStack(Material.COOKED_BEEF, 16));
        if (this == GUARDIAN || this == PALADIN) inv.setItemInOffHand(item(Material.SHIELD));
        for (SupplySlot supply : supplies()) {
            ItemStack stack = supply.material() == Material.TNT ? PvpBombs.item(supply.amount())
                    : supply.amount() == 1 ? item(supply.material()) : new ItemStack(supply.material(), supply.amount());
            if (supply.material() == Material.BOW) enchant(stack, this == PYROMANCER ? Enchantment.FLAME : Enchantment.POWER, 1);
            if (supply.material() == Material.CROSSBOW) {
                enchant(stack, Enchantment.QUICK_CHARGE, 3);
                enchant(stack, Enchantment.PIERCING, 1);
            }
            inv.setItem(supply.slot(), stack);
        }
        for (PvpArrow arrows : specialArrows()) inv.setItem(arrows.slot(), arrows.item());
        for (PotionSlot potion : potions()) inv.setItem(potion.slot(), potion.kind().item(potion.amount()));
        ItemStack active = new ItemStack(switch (this) {
            case GUARDIAN -> Material.IRON_INGOT;
            case RANGER -> Material.FEATHER;
            case BERSERKER -> Material.REDSTONE;
            case DUELIST -> Material.RABBIT_FOOT;
            case ALCHEMIST -> Material.AMETHYST_SHARD;
            case ENGINEER -> Material.COPPER_INGOT;
            case AERONAUT -> Material.BREEZE_ROD;
            case PALADIN -> Material.GOLD_NUGGET;
            case CRYOMANCER -> Material.PRISMARINE_CRYSTALS;
            case PYROMANCER -> Material.BLAZE_POWDER;
            case LANCER -> Material.SADDLE;
            case SHADOW -> Material.STICK;
            case HARPOONER -> Material.NAUTILUS_SHELL;
            case THUNDERER -> Material.LIGHTNING_ROD;
            case DEMOLITIONIST -> Material.GUNPOWDER;
        });
        active.editMeta(meta -> {
            meta.displayName(Component.text(this == THUNDERER ? "Грозовой жезл" : ability, NamedTextColor.GOLD));
            meta.lore(List.of(Component.text((this == SHADOW ? "Удар по сопернику" : "ПКМ") + " • перезарядка " + cooldown + " с", NamedTextColor.GRAY),
                    Component.text(abilityDescription(), NamedTextColor.GRAY),
                    Component.text(passiveDescription() + (this == THUNDERER ? "; золотая цель — готово, красная — откат" : ""), NamedTextColor.DARK_GRAY)));
            meta.getPersistentDataContainer().set(this == SHADOW ? SHADOW_WAND : abilityKey, PersistentDataType.BYTE, (byte) 1);
        });
        inv.setItem(7, active);
        inv.setHeldItemSlot(0);
    }
    static boolean shadowWand(ItemStack item) {
        return item != null && item.getType() == Material.STICK && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(SHADOW_WAND, PersistentDataType.BYTE);
    }
    /** Refill the existing stack even if moved, without overwriting another inventory slot. */
    void replenishArrows(Player player) {
        if (!unlimitedArrows()) return;
        var inventory = player.getInventory();
        int slot = inventory.first(Material.ARROW);
        if (slot < 0) {
            if (this == RANGER) {
                int free = PvpArrow.normalRefillSlot(index -> {
                    ItemStack stack = inventory.getItem(index);
                    return stack == null || stack.getType() == Material.AIR;
                });
                if (free >= 0) inventory.setItem(free, new ItemStack(Material.ARROW, ARROW_STACK));
                return;
            }
            inventory.addItem(new ItemStack(Material.ARROW, ARROW_STACK));
            return;
        }
        ItemStack arrows = inventory.getItem(slot);
        if (arrows != null && arrows.getAmount() < ARROW_STACK) {
            arrows.setAmount(ARROW_STACK);
            inventory.setItem(slot, arrows);
        }
    }
    static ItemStack item(Material material) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> meta.setUnbreakable(true));
        return item;
    }
    private static void enchant(ItemStack item, Enchantment enchantment, int level) {
        item.editMeta(meta -> meta.addEnchant(enchantment, level, true));
    }
    /** Failed resource-based activations do not consume the ability cooldown. */
    boolean activate(Player player, Player opponent) {
        switch (this) {
            case SHADOW, HARPOONER, THUNDERER -> { return false; } // These abilities are handled by the session.
            case DEMOLITIONIST -> {
                if (PvpBombs.replenish(player) == 0) {
                    player.sendActionBar(Component.text("Запас TNT полон или нет места", NamedTextColor.YELLOW));
                    return false;
                }
            }
            case LANCER -> {
                // Mounted activation is handled by the session; the same cooldown applies on foot.
                effect(player, PotionEffectType.SPEED, 3, 0);
            }
            case GUARDIAN -> {
                effect(player, PotionEffectType.RESISTANCE, 4, 1);
                effect(player, PotionEffectType.SLOWNESS, 4, 0);
            }
            case RANGER -> {
                dash(player, true);
                effect(player, PotionEffectType.SPEED, 4, 0);
            }
            case BERSERKER -> {
                if (!PvpCombat.canPayFury(player.getHealth())) {
                    player.sendActionBar(Component.text("Для ярости нужно больше одного сердца", NamedTextColor.RED));
                    return false;
                }
                player.setHealth(player.getHealth() - 2);
                effect(player, PotionEffectType.STRENGTH, 4, 0);
                effect(player, PotionEffectType.SPEED, 4, 0);
            }
            case DUELIST -> {
                dash(player, false);
                effect(player, PotionEffectType.SPEED, 3, 0);
            }
            case ALCHEMIST -> {
                int supplied = 0;
                for (PvpPotion potion : focusPotions()) supplied += potion.replenish(player, 1, 2);
                if (supplied == 0) {
                    player.sendActionBar(Component.text("Боевые зелья уже полны или нет места", NamedTextColor.YELLOW));
                    return false;
                }
            }
            case ENGINEER -> {
                effect(player, PotionEffectType.ABSORPTION, 6, 1);
                supply(player, Material.WHITE_WOOL, 16, blocks());
            }
            case AERONAUT -> {
                player.setVelocity(PvpCombat.launch(player.getVelocity()));
                supply(player, Material.WIND_CHARGE, 4, 8);
            }
            case PALADIN -> {
                PvpCombat.heal(player, 3);
                player.removePotionEffect(PotionEffectType.POISON);
                player.removePotionEffect(PotionEffectType.WEAKNESS);
                player.removePotionEffect(PotionEffectType.SLOWNESS);
                player.removePotionEffect(PotionEffectType.BLINDNESS);
                player.setFireTicks(0);
                effect(player, PotionEffectType.RESISTANCE, 2, 0);
            }
            case CRYOMANCER -> {
                supply(player, Material.SNOWBALL, 8, 16);
                effect(player, PotionEffectType.SPEED, 3, 0);
                if (visibleTarget(player, opponent, 6)) effect(opponent, PotionEffectType.SLOWNESS, 3, 0);
            }
            case PYROMANCER -> {
                if (!visibleTarget(player, opponent, 8)) {
                    player.sendActionBar(Component.text("Нужна видимая цель не дальше 8 блоков", NamedTextColor.YELLOW));
                    return false;
                }
                opponent.setFireTicks(Math.max(opponent.getFireTicks(), 100));
                effect(player, PotionEffectType.ABSORPTION, 4, 0);
            }
        }
        return true;
    }
    private static boolean visibleTarget(Player player, Player target, double range) {
        return target != null && player.getWorld().equals(target.getWorld())
                && player.getLocation().distanceSquared(target.getLocation()) <= range * range && player.hasLineOfSight(target);
    }
    private static void supply(Player player, Material material, int amount, int cap) {
        int owned = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == material) owned += stack.getAmount();
        }
        int added = PvpPotion.refillAmount(owned, amount, cap);
        if (added > 0) player.getInventory().addItem(new ItemStack(material, added));
    }
    private static void dash(Player player, boolean backwards) {
        var facing = player.getLocation();
        facing.setPitch(0);
        player.setVelocity(PvpCombat.dash(facing.getDirection(), backwards));
    }
    private static void effect(Player player, PotionEffectType type, int seconds, int amplifier) {
        player.addPotionEffect(new PotionEffect(type, seconds * 20, amplifier));
    }
}
