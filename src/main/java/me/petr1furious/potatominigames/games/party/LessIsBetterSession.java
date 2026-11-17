package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.games.party.LessIsBetterVotes.Side;
import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/** Score a point by choosing the less popular of two hidden votes. */
final class LessIsBetterSession extends PartySurvivalSession {
    private static final String TEXT = "party.less_is_better.";
    private static final int VOTES = 6;
    private static final int PICK_TICKS = 160;
    private static final int REVEAL_TICKS = 60;
    private static final int BALLOT_TICKS = PICK_TICKS + REVEAL_TICKS;
    private static final int FLOOR = 80;
    private static final int RED_SLOT = 3;
    private static final int BLUE_SLOT = 5;

    private final class BallotMenu implements InventoryHolder {
        private final Inventory inventory;

        BallotMenu(Player player) {
            inventory = Bukkit.createInventory(this, 9, I18n.render(I18n.tr(TEXT + "menu"), player));
            render(player);
        }

        void render(Player player) {
            inventory.setItem(RED_SLOT, voteItem(player, Material.RED_DYE, Side.RED));
            inventory.setItem(BLUE_SLOT, voteItem(player, Material.BLUE_DYE, Side.BLUE));
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final LessIsBetterVotes votes = new LessIsBetterVotes();
    private LessIsBetterVotes.Reveal reveal;
    private int neutralVotes;

    LessIsBetterSession(SessionServices services, PartyConfig config, PartyRoundContext context) {
        super(services, config, "less_is_better", context);
    }

    @Override
    void buildArena() {
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                boolean edge = Math.abs(x) == 10 || Math.abs(z) == 10;
                Material floor = edge ? Material.SMOOTH_QUARTZ : Material.POLISHED_DEEPSLATE;
                world.getBlockAt(x, FLOOR, z).setType(floor, false);
                if (edge) {
                    world.getBlockAt(x, FLOOR + 1, z).setType(Material.LIGHT_GRAY_STAINED_GLASS, false);
                    world.getBlockAt(x, FLOOR + 2, z).setType(Material.LIGHT_GRAY_STAINED_GLASS, false);
                }
            }
        }
        for (int x = -6; x <= 6; x++) {
            for (int y = FLOOR + 1; y <= FLOOR + 3; y++) {
                if (x == 0) continue;
                Material panel = x < 0 ? Material.RED_CONCRETE : Material.BLUE_CONCRETE;
                world.getBlockAt(x, y, 9).setType(panel, false);
            }
        }
    }

    @Override
    Location spawn(int index, int count) {
        return new Location(world, (index % 6 - 2.5) * 2.4, FLOOR + 1, -4 + index / 6 * 3, 0, 0);
    }

    @Override
    Location viewpoint() {
        return new Location(world, .5, FLOOR + 12, -9, 0, 52);
    }

    @Override
    double outHeight() {
        return FLOOR - 4;
    }

    @Override
    int durationSeconds() {
        return VOTES * BALLOT_TICKS / 20;
    }

    @Override
    long arenaTime() {
        return 6000L;
    }

    @Override
    boolean survivalAutoFinish() {
        return false;
    }

    @Override
    boolean podiumAwards() {
        return true;
    }

    @Override
    Component instructions() {
        return I18n.tr(TEXT + "instructions");
    }

    @Override
    void preparePlayer(Player player) {
        ItemStack item = new ItemStack(Material.PAPER);
        item.editMeta(meta -> meta.displayName(I18n.render(I18n.tr(TEXT + "paper"), player)));
        player.getInventory().setItem(0, item);
        player.getInventory().setHeldItemSlot(0);
    }

    private ItemStack voteItem(Player player, Material material, Side side) {
        ItemStack item = new ItemStack(material);
        boolean selected = votes.choice(player.getUniqueId()) == side;
        item.editMeta(meta -> {
            meta.displayName(I18n.render(I18n.tr(TEXT + (selected ? "selected" : "choose"), sideName(side)), player));
            meta.setEnchantmentGlintOverride(selected);
        });
        return item;
    }

    private Component sideName(Side side) {
        return side == Side.RED
                ? I18n.tr(TEXT + "red").color(NamedTextColor.RED)
                : I18n.tr(TEXT + "blue").color(NamedTextColor.AQUA);
    }

    @Override
    public void onPlayerInteract(PlayerInteractEvent event) {
        event.setCancelled(true);
        Player player = event.getPlayer();
        Action action = event.getAction();
        if (!playing() || reveal != null || !alive(player) || !player.getWorld().equals(world)
                || event.getHand() != EquipmentSlot.HAND
                || (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK)) return;
        if (event.getMaterial() == Material.PAPER) player.openInventory(new BallotMenu(player).inventory);
    }

    @Override
    protected void handleInventoryClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !alive(player) || !playing()
                || reveal != null || !(event.getView().getTopInventory().getHolder() instanceof BallotMenu menu)) return;
        Side side = switch (event.getRawSlot()) {
            case RED_SLOT -> Side.RED;
            case BLUE_SLOT -> Side.BLUE;
            default -> null;
        };
        if (side != null && votes.choice(player.getUniqueId()) != side) {
            votes.pick(player.getUniqueId(), side);
            menu.render(player);
            player.sendActionBar(choicePrompt(player.getUniqueId()));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, .7f, 1.25f);
        }
    }

    @Override
    void playTick(long elapsed) {
        int ballot = (int) (elapsed / BALLOT_TICKS);
        if (ballot >= VOTES) {
            finish(List.of());
            return;
        }
        int offset = (int) (elapsed % BALLOT_TICKS);
        if (offset == 0) {
            reveal = null;
            List<Player> players = alivePlayers();
            neutralVotes = Math.max(0, 3 - players.size());
            if (neutralVotes > 0) say(I18n.tr(TEXT + "neutral", neutralVotes));
            // The shared Go title already announces the first ballot.
            if (ballot > 0) announceTitle(I18n.tr(TEXT + "ballot", ballot + 1, VOTES), I18n.tr(TEXT + "pick"), 2);
        }
        if (offset == PICK_TICKS) reveal();
        if (offset < PICK_TICKS && offset % 20 == 0) {
            for (Player player : alivePlayers()) player.sendActionBar(choicePrompt(player.getUniqueId()));
        }
    }

    private void reveal() {
        int neutralRed = 0;
        for (int i = 0; i < neutralVotes; i++) {
            if (random.nextBoolean()) neutralRed++;
        }
        reveal = votes.reveal(context.eligible(), neutralRed, neutralVotes - neutralRed);
        Component result = reveal.winner() == null
                ? I18n.tr(TEXT + (reveal.red() == reveal.blue() ? "tie" : "empty"))
                : I18n.tr(TEXT + "wins", sideName(reveal.winner()));
        announceTitle(result, tally(), 2);
        for (Player player : alivePlayers()) {
            closeBallot(player);
            Side choice = reveal.choices().get(player.getUniqueId());
            boolean scored = choice != null && choice == reveal.winner();
            player.sendActionBar(I18n.tr(TEXT + (scored ? "point" : choice == null ? "no_pick" : "no_point"))
                    .color(scored ? NamedTextColor.GREEN : NamedTextColor.GRAY));
            player.playSound(player.getLocation(),
                    scored ? Sound.ENTITY_EXPERIENCE_ORB_PICKUP : Sound.BLOCK_NOTE_BLOCK_HAT, .7f, 1.1f);
        }
    }

    @Override
    void cleanup() {
        alivePlayers().forEach(this::closeBallot);
    }

    private void closeBallot(Player player) {
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof BallotMenu)
            player.closeInventory();
    }

    private Component tally() {
        return I18n.tr(TEXT + "tally", reveal.red(), reveal.blue());
    }

    private Component choicePrompt(UUID player) {
        Side side = votes.choice(player);
        return side == null ? I18n.tr(TEXT + "pick") : I18n.tr(TEXT + "picked", sideName(side));
    }

    @Override
    Component status() {
        if (reveal != null) return tally();
        long offset = elapsed() % BALLOT_TICKS;
        return I18n.tr(TEXT + "status", elapsed() / BALLOT_TICKS + 1, VOTES,
                (PICK_TICKS - offset + 19) / 20);
    }

    @Override
    List<Component> sidebarLines(Player player, Component line) {
        UUID id = player.getUniqueId();
        if (!alive(player)) return List.of(text(line));
        Component hint = !playing() ? I18n.tr(TEXT + "pick")
                : reveal == null ? choicePrompt(id) : tally();
        return List.of(text(line), I18n.tr(TEXT + "score", votes.score(id)), hint);
    }

    @Override
    public PartyRoundResult roundResult() {
        return votes.result(context.eligible());
    }
}
