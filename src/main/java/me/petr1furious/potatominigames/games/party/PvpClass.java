package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.i18n.I18n;
import java.util.List;
import java.util.Random;
import org.bukkit.Color;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
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
    GUARDIAN(22),
    RANGER(16),
    BERSERKER(22),
    DUELIST(14),
    ALCHEMIST(24),
    ENGINEER(24),
    AERONAUT(20),
    PALADIN(24),
    CRYOMANCER(20),
    PYROMANCER(20),
    LANCER(10),
    SHADOW(20),
    HARPOONER(20),
    THUNDERER(3),
    DEMOLITIONIST(18);

    private static final NamespacedKey SHADOW_WAND = new NamespacedKey("potatominigames", "shadow_wand");
    private static final int ARROW_STACK = 64;
    static final int FOOD_SLOT = 8;
    record PotionSlot(PvpPotion kind, int amount, int slot) { }
    record SupplySlot(Material material, int amount, int slot) { }
    record Armor(Material helmet, Material chestplate, Material leggings, Material boots) { }
    final Component title;
    final Component description;
    final Component ability;
    final int cooldown;

    PvpClass(int cooldown) {
        this.title = label("title");
        this.description = label("description");
        this.ability = label("ability");
        this.cooldown = cooldown;
    }
    private Component label(String field) {
        return I18n.tr("party.duels.class." + name().toLowerCase(java.util.Locale.ROOT) + "." + field);
    }
    static PvpClass random(Random random) { return values()[random.nextInt(values().length)]; }
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
            case LANCER -> Material.NETHERITE_SPEAR;
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
            case SHADOW -> new Armor(Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS);
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
        return this == ALCHEMIST ? List.of(PvpPotion.HARMING, PvpPotion.POISON, PvpPotion.SLOWNESS, PvpPotion.NAUSEA) : List.of();
    }
    Component abilityDescription() {
        return label("ability_description");
    }
    Component passiveDescription() {
        return label("passive_description");
    }
    List<PotionSlot> potions() {
        return switch (this) {
            case GUARDIAN -> List.of(new PotionSlot(PvpPotion.SLOWNESS, 1, 3), new PotionSlot(PvpPotion.HEALING, 2, 6));
            case RANGER -> List.of(new PotionSlot(PvpPotion.SLOWNESS, 1, 4), new PotionSlot(PvpPotion.HEALING, 1, 6));
            case BERSERKER -> List.of(new PotionSlot(PvpPotion.SPEED, 2, 4), new PotionSlot(PvpPotion.HEALING, 1, 6));
            case DUELIST -> List.of(new PotionSlot(PvpPotion.SPEED, 1, 4), new PotionSlot(PvpPotion.HEALING, 1, 6));
            case ALCHEMIST -> List.of(new PotionSlot(PvpPotion.HARMING, 2, 3), new PotionSlot(PvpPotion.POISON, 2, 4),
                    new PotionSlot(PvpPotion.SLOWNESS, 2, 5), new PotionSlot(PvpPotion.HEALING, 3, 6),
                    new PotionSlot(PvpPotion.NAUSEA, 2, 9), new PotionSlot(PvpPotion.WEAKNESS, 2, 10), new PotionSlot(PvpPotion.SPEED, 1, 11));
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
        if (this == THUNDERER) weapon.editMeta(meta -> meta.displayName(I18n.tr("party.duels.item.thunder_axe").color(NamedTextColor.AQUA)));
        if (this == HARPOONER) {
            enchant(weapon, Enchantment.LOYALTY, 3);
            // Preserve every native modifier, including attack speed; only the held melee weapon is stronger.
            var modifiers = ItemAttributeModifiers.itemAttributes();
            var original = weapon.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
            if (original != null) for (var entry : original.modifiers())
                modifiers.addModifier(entry.attribute(), entry.modifier(), entry.getGroup(), entry.display());
            modifiers.addModifier(Attribute.ATTACK_DAMAGE,
                    new AttributeModifier(new NamespacedKey("potatominigames", "harpoon_melee"), 2,
                            AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
            weapon.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, modifiers);
        }
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
                weapon.editMeta(meta -> meta.displayName(I18n.tr("party.duels.item.ritual_blade").color(NamedTextColor.LIGHT_PURPLE)));
            }
            ItemStack robe = inv.getChestplate();
            robe.editMeta(LeatherArmorMeta.class, meta -> meta.setColor(Color.fromRGB(this == ALCHEMIST ? 0x793DA8 : 0x59CCED)));
            enchant(robe, Enchantment.PROTECTION, 2);
            inv.setChestplate(robe);
        }
        if (this == SHADOW) {
            ItemStack[] shadowPieces = inv.getArmorContents();
            for (ItemStack piece : shadowPieces)
                piece.editMeta(LeatherArmorMeta.class, meta -> meta.setColor(Color.fromRGB(0x20202D)));
            inv.setArmorContents(shadowPieces);
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
        inv.setItem(0, I18n.item(weapon, player));
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
            inv.setItem(supply.slot(), I18n.item(stack, player));
        }
        for (PvpArrow arrows : specialArrows()) inv.setItem(arrows.slot(), I18n.item(arrows.item(), player));
        for (PotionSlot potion : potions()) inv.setItem(potion.slot(), I18n.item(potion.kind().item(potion.amount()), player));
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
            meta.displayName((this == THUNDERER ? I18n.tr("party.duels.item.thunder_wand") : ability).color(NamedTextColor.GOLD));
            meta.lore(List.of(I18n.tr(this == SHADOW ? "party.duels.item.ability_hit" : "party.duels.item.ability_use", cooldown).color(NamedTextColor.GRAY),
                    abilityDescription().color(NamedTextColor.GRAY),
                    (this == THUNDERER ? I18n.tr("party.duels.item.thunder_preview", passiveDescription()) : passiveDescription()).color(NamedTextColor.DARK_GRAY)));
            meta.getPersistentDataContainer().set(this == SHADOW ? SHADOW_WAND : abilityKey, PersistentDataType.BYTE, (byte) 1);
        });
        inv.setItem(7, I18n.item(active, player));
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
            case SHADOW, HARPOONER, THUNDERER, DEMOLITIONIST -> { return false; } // These abilities are handled by the session.
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
                    player.sendActionBar(I18n.tr("party.duels.ability.fury_health").color(NamedTextColor.RED));
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
                    player.sendActionBar(I18n.tr("party.duels.ability.potions_full").color(NamedTextColor.YELLOW));
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
                player.removePotionEffect(PotionEffectType.NAUSEA);
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
                    player.sendActionBar(I18n.tr("party.duels.ability.visible_target").color(NamedTextColor.YELLOW));
                    return false;
                }
                opponent.setFireTicks(Math.max(opponent.getFireTicks(), 100));
            }
        }
        return true;
    }
    static boolean visibleTarget(Player player, Player target, double range) {
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
