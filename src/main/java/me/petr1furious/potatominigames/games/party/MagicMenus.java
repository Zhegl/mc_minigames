package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.i18n.I18n;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import java.util.List;
import java.util.UUID;

/** Owner-bound projections: shared loot never shares a viewer's translated item metadata. */
final class MagicMenus {
    private static final int[] STARTER_SLOTS = {10, 11, 12, 14, 15, 16};
    private final class Holder implements InventoryHolder {
        final UUID viewer;
        final MagicBattleArena.Pos chest;
        final MagicChestLoot loot;
        Inventory inventory;
        Holder(Player player, MagicBattleArena.Pos chest, MagicChestLoot loot) {
            viewer = player.getUniqueId(); this.chest = chest; this.loot = loot;
        }
        MagicMenus owner() { return MagicMenus.this; }
        public Inventory getInventory() { return inventory; }
    }
    boolean owns(Inventory inventory) {
        return inventory.getHolder() instanceof MagicMenus.Holder h && h.owner() == this;
    }
    boolean viewer(Inventory inventory, Player player) {
        return owns(inventory) && ((Holder) inventory.getHolder()).viewer.equals(player.getUniqueId());
    }
    boolean starter(Inventory inventory) { return owns(inventory) && ((Holder) inventory.getHolder()).chest == null; }
    MagicBattleArena.Pos chest(Inventory inventory) { return owns(inventory) ? ((Holder) inventory.getHolder()).chest : null; }
    MagicChestLoot loot(Inventory inventory) { return owns(inventory) ? ((Holder) inventory.getHolder()).loot : null; }
    MagicSpell starterChoice(int slot) {
        for (int i = 0; i < STARTER_SLOTS.length; i++) if (STARTER_SLOTS[i] == slot) return MagicWand.STARTERS.get(i);
        return null;
    }
    void openStarter(Player player, List<MagicSpell> selected) {
        Holder h = new Holder(player, null, null);
        h.inventory = Bukkit.createInventory(h, 27, I18n.render(I18n.tr("party.magic.starter.title"), player));
        for (int i = 0; i < STARTER_SLOTS.length; i++) {
            MagicSpell spell = MagicWand.STARTERS.get(i);
            ItemStack item = MagicItems.wand(MagicWand.single(spell), player);
            item.editMeta(meta -> {
                var lore = new java.util.ArrayList<>(meta.lore());
                lore.add(I18n.render(I18n.tr(selected.contains(spell) ? "party.magic.starter.selected" : "party.magic.starter.choose")
                        .color(selected.contains(spell) ? NamedTextColor.GREEN : NamedTextColor.YELLOW), player));
                meta.lore(lore);
            });
            h.inventory.setItem(STARTER_SLOTS[i], item);
        }
        h.inventory.setItem(22, label(Material.LIME_DYE, "party.magic.starter.confirm", player, selected.size()));
        player.openInventory(h.inventory);
    }
    void openLoot(Player player, MagicBattleArena.Pos pos, MagicChestLoot loot, long tick) {
        Holder h = new Holder(player, pos, loot);
        h.inventory = Bukkit.createInventory(h, 27, I18n.render(I18n.tr("party.magic.loot.title"), player));
        for (int i = 0; i < 2; i++)
            if (loot.wand(i) != null) h.inventory.setItem(i == 0 ? 11 : 15, MagicItems.wand(loot.wand(i), player));
        if (loot.wool()) h.inventory.setItem(13, new ItemStack(Material.WHITE_WOOL, 16));
        h.inventory.setItem(22, label(Material.CLOCK, "party.magic.loot.refill", player,
                Math.max(0, (loot.refillAt - tick + 19) / 20)));
        player.openInventory(h.inventory);
    }
    private ItemStack label(Material material, String key, Player player, Object... args) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> meta.displayName(I18n.tr(key, args).color(NamedTextColor.GREEN)));
        return I18n.item(item, player);
    }
    void close(Player player) {
        if (owns(player.getOpenInventory().getTopInventory())) player.closeInventory();
    }
}
