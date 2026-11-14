package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/** Per-session, per-viewer class menu with the same centered seven-column layout as game menus. */
final class PvpKitMenu {
    private static final class Holder implements InventoryHolder {
        final PvpKitMenu owner;
        final UUID player;
        final Map<Integer, PvpClass> choices = new HashMap<>();
        Inventory inventory;

        Holder(PvpKitMenu owner, UUID player) {
            this.owner = owner;
            this.player = player;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    boolean owns(Inventory inventory) {
        return inventory.getHolder() instanceof Holder holder && holder.owner == this;
    }

    PvpClass choice(Inventory inventory, Player player, int slot) {
        return inventory.getHolder() instanceof Holder holder
                        && holder.owner == this
                        && holder.player.equals(player.getUniqueId())
                ? holder.choices.get(slot)
                : null;
    }

    void open(Player player, PvpClass selected) {
        Holder holder = new Holder(this, player.getUniqueId());
        holder.inventory =
                Bukkit.createInventory(
                        holder, 45, I18n.render(I18n.tr("party.duels.pvp.choice.title"), player));
        PvpClass[] classes = PvpClass.values();
        for (int i = 0; i < classes.length; i++) {
            PvpClass kit = classes[i];
            int row = i / 7, count = Math.min(7, classes.length - row * 7), index = i % 7;
            int column = count <= 4 ? 4 - (count - 1) + index * 2 : 1 + (7 - count) / 2 + index;
            int slot = (row + 1) * 9 + column;
            Material material =
                    switch (kit) {
                        case GUARDIAN -> Material.SHIELD;
                        case RANGER -> Material.BOW;
                        case ALCHEMIST -> Material.SPLASH_POTION;
                        case ENGINEER -> Material.CROSSBOW;
                        case CRYOMANCER -> Material.SNOWBALL;
                        case PYROMANCER -> Material.BLAZE_POWDER;
                        case THUNDERER -> Material.LIGHTNING_ROD;
                        case DEMOLITIONIST -> Material.TNT;
                        default -> kit.primaryWeapon();
                    };
            ItemStack icon = new ItemStack(material);
            icon.editMeta(meta -> {
                meta.displayName(kit.title.color(NamedTextColor.GOLD));
                meta.lore(
                        List.of(
                                kit.description.color(NamedTextColor.GRAY),
                                kit.passiveDescription().color(NamedTextColor.AQUA),
                                I18n.tr(
                                                "party.duels.pvp.ability_details",
                                                kit.ability,
                                                kit.abilityDescription(),
                                                kit.cooldown)
                                        .color(NamedTextColor.YELLOW),
                                I18n.tr("party.duels.pvp.choice.click")
                                        .color(NamedTextColor.GREEN)));
                meta.setEnchantmentGlintOverride(kit == selected);
            });
            holder.choices.put(slot, kit);
            holder.inventory.setItem(slot, I18n.item(icon, player));
        }
        ItemStack help = new ItemStack(Material.BOOK);
        help.editMeta(meta -> {
            meta.displayName(I18n.tr("party.duels.pvp.choice.title"));
            meta.lore(List.of(I18n.tr("party.duels.pvp.choice.help", PvpKitChoices.SECONDS)));
        });
        holder.inventory.setItem(40, I18n.item(help, player));
        player.openInventory(holder.inventory);
    }

    ItemStack opener(Player viewer) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        item.editMeta(meta -> {
            meta.displayName(I18n.tr("party.duels.pvp.choice.title").color(NamedTextColor.GOLD));
            meta.lore(List.of(I18n.tr("party.duels.pvp.choice.reopen")));
        });
        return I18n.item(item, viewer);
    }

    void close(Player player) {
        if (owns(player.getOpenInventory().getTopInventory())) player.closeInventory();
    }
}
