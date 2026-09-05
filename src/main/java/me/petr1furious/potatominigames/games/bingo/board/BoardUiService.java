package me.petr1furious.potatominigames.games.bingo.board;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntUnaryOperator;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import me.petr1furious.potatominigames.games.bingo.model.BoardEntry;
import me.petr1furious.potatominigames.games.bingo.model.GameDifficulty;
import me.petr1furious.potatominigames.games.bingo.model.GameMode;
import me.petr1furious.potatominigames.games.bingo.model.TeamState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public final class BoardUiService {
    public enum ClickActionType {
        NONE,
        TOGGLE_MODE,
        CLAIM
    }

    public record ClickAction(ClickActionType type, int boardIndex) {
        static ClickAction none() {
            return new ClickAction(ClickActionType.NONE, -1);
        }

        static ClickAction toggle() {
            return new ClickAction(ClickActionType.TOGGLE_MODE, -1);
        }

        static ClickAction claim(int boardIndex) {
            return new ClickAction(ClickActionType.CLAIM, boardIndex);
        }
    }

    private static final String BOARD_TITLE = "Bingo Board";
    private static final int[] BOARD_SLOTS = {
            2, 3, 4, 5, 6,
            11, 12, 13, 14, 15,
            20, 21, 22, 23, 24,
            29, 30, 31, 32, 33,
            38, 39, 40, 41, 42
    };
    private static final int TOGGLE_SLOT = 8;
    private static final int STATUS_SLOT = 17;

    private final NamespacedKey bingoCardKey;
    private final Map<UUID, Integer> displayModes = new HashMap<>();

    public BoardUiService(JavaPlugin plugin) {
        this.bingoCardKey = new NamespacedKey(plugin, "bingo_card");
    }

    public ItemStack createCard() {
        ItemStack card = new ItemStack(Material.PAPER, 1);
        ItemMeta meta = card.getItemMeta();
        meta.displayName(Component.text("Bingo Card", NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.addEnchant(org.bukkit.enchantments.Enchantment.INFINITY, 1, true);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(bingoCardKey, PersistentDataType.BYTE, (byte) 1);
        card.setItemMeta(meta);
        return card;
    }

    public boolean isBingoCard(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        Byte marker = meta.getPersistentDataContainer().get(bingoCardKey, PersistentDataType.BYTE);
        return marker != null && marker == 1;
    }

    public void openBoard(Player player, BoardService.Board board, TeamState team, GameMode mode,
            GameDifficulty difficulty,
            IntUnaryOperator rewardForIndex) {
        Inventory inventory = Bukkit.createInventory(new BoardViewHolder(), 45,
                Component.text(BOARD_TITLE, NamedTextColor.GOLD));
        fillBoard(inventory, player, board, team, mode, difficulty, rewardForIndex);
        player.openInventory(inventory);
    }

    public void refreshOpenBoard(Player player, BoardService.Board board, TeamState team, GameMode mode,
            GameDifficulty difficulty,
            IntUnaryOperator rewardForIndex) {
        Inventory inventory = player.getOpenInventory().getTopInventory();
        if (!isBoardView(inventory.getHolder())) {
            return;
        }
        fillBoard(inventory, player, board, team, mode, difficulty, rewardForIndex);
        player.updateInventory();
    }

    public boolean isBoardView(InventoryHolder holder) {
        return holder instanceof BoardViewHolder;
    }

    public ClickAction mapClick(Player player, int rawSlot) {
        if (rawSlot == TOGGLE_SLOT) {
            int next = (displayModes.getOrDefault(player.getUniqueId(), 0) + 1) % 4;
            displayModes.put(player.getUniqueId(), next);
            return ClickAction.toggle();
        }
        for (int i = 0; i < BOARD_SLOTS.length; i++) {
            if (BOARD_SLOTS[i] == rawSlot) {
                return ClickAction.claim(i);
            }
        }
        return ClickAction.none();
    }

    private void fillBoard(Inventory inventory, Player player, BoardService.Board board, TeamState team, GameMode mode,
            GameDifficulty difficulty,
            IntUnaryOperator rewardForIndex) {
        int displayMode = displayModes.getOrDefault(player.getUniqueId(), 0);
        for (int i = 0; i < 25; i++) {
            BoardEntry entry = board.entries()[i];
            int slot = BOARD_SLOTS[i];
            if (entry == null) {
                inventory.setItem(slot, null);
                continue;
            }
            boolean collected = team.collected()[i];
            inventory.setItem(slot, boardCell(entry, collected, board.effectiveDifficulties()[i], displayMode,
                    mode, rewardForIndex.applyAsInt(i)));
        }
        inventory.setItem(TOGGLE_SLOT, toggleItem(displayMode));
        inventory.setItem(STATUS_SLOT, statusItem(mode, difficulty));
    }

    private static ItemStack toggleItem(int displayMode) {
        ItemStack stack = new ItemStack(Material.DEBUG_STICK, 1);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("Change display mode", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Current: " + displayModeName(displayMode), NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        stack.setItemMeta(meta);
        return stack;
    }

    private static ItemStack statusItem(GameMode mode, GameDifficulty difficulty) {
        Material icon = switch (mode) {
            case DEFAULT -> Material.NAME_TAG;
            case BUTFAST -> Material.DIAMOND;
            case COLLECTALL -> Material.NETHER_STAR;
        };
        ItemStack stack = new ItemStack(icon, 1);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("Mode: " + mode.key(), NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Difficulty: " + difficulty.key(), NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false)));
        stack.setItemMeta(meta);
        return stack;
    }

    private static ItemStack boardCell(BoardEntry entry, boolean collected, int effectiveDifficulty, int displayMode,
            GameMode mode, int rewardPoints) {
        if (collected && displayMode != 3) {
            ItemStack stack = new ItemStack(Material.BARRIER, 1);
            ItemMeta meta = stack.getItemMeta();
            meta.displayName(Component.text("Completed", NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
            stack.setItemMeta(meta);
            return stack;
        }

        Material icon = entry.displayMaterial() == null ? Material.PAPER : entry.displayMaterial();
        ItemStack stack = new ItemStack(icon, 1);
        ItemMeta meta = stack.getItemMeta();
        if (displayMode == 3) {
            meta.displayName(
                    Component.text(prettify(entry.id()), collected ? NamedTextColor.GREEN : NamedTextColor.WHITE)
                            .decoration(TextDecoration.ITALIC, false));
            if (collected) {
                meta.lore(List.of(Component.text("Completed", NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false)));
            }
            stack.setItemMeta(meta);
            return stack;
        }
        if (displayMode == 0) {
            stack.setItemMeta(meta);
            return stack;
        }
        if (displayMode == 1) {
            meta.displayName(
                    Component.text(prettify(entry.id()) + " [" + effectiveDifficulty + "]", NamedTextColor.WHITE)
                            .decoration(TextDecoration.ITALIC, false));
        } else if (displayMode == 2) {
            int shown = mode == GameMode.BUTFAST ? rewardPoints : effectiveDifficulty;
            meta.displayName(Component.text(prettify(entry.id()) + " (" + shown + ")", NamedTextColor.WHITE)
                    .decoration(TextDecoration.ITALIC, false));
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private static String displayModeName(int mode) {
        return switch (mode) {
            case 1 -> "difficulty";
            case 2 -> "reward";
            case 3 -> "all items";
            default -> "name";
        };
    }

    private static String prettify(String raw) {
        return raw.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static final class BoardViewHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
