package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.games.party.LessIsBetterVotes.Side;
import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The smaller occupied platform scores when the vote timer expires. */
final class LessIsBetterSession extends PartySurvivalSession {
    private static final String TEXT = "party.less_is_better.";
    private static final int VOTES = 6;
    private static final int PICK_TICKS = 160;
    private static final int REVEAL_TICKS = 60;
    private static final int BALLOT_TICKS = PICK_TICKS + REVEAL_TICKS;
    private static final int FLOOR = 80;
    private static final int PAD_HEIGHT = FLOOR + 1;
    private static final int BLUE_START = 4;
    private static final int BLUE_END = 12;
    private static final int RED_START = 1 - BLUE_END;
    private static final int RED_END = 1 - BLUE_START;
    private static final int PAD_Z_START = -7;
    private static final int PAD_Z_END = 8;

    private final LessIsBetterVotes votes = new LessIsBetterVotes();
    private LessIsBetterVotes.Reveal reveal;
    private LessIsBetterVisibility visibility;

    LessIsBetterSession(SessionServices services, PartyConfig config, PartyRoundContext context) {
        super(services, config, "less_is_better", context);
    }

    @Override
    void buildArena() {
        for (int x = -14; x <= 14; x++) {
            for (int z = -10; z <= 10; z++) {
                boolean edge = Math.abs(x) == 14 || Math.abs(z) == 10;
                Material floor = edge || x == 0 ? Material.SMOOTH_QUARTZ : Material.POLISHED_DEEPSLATE;
                world.getBlockAt(x, FLOOR, z).setType(floor, false);
                if (edge) {
                    Side side = sideAtX(x + .5);
                    Material wall = switch (side) {
                        case RED -> Material.RED_STAINED_GLASS;
                        case BLUE -> Material.BLUE_STAINED_GLASS;
                        case null -> Material.LIGHT_GRAY_STAINED_GLASS;
                    };
                    for (int y = FLOOR + 1; y <= FLOOR + 3; y++)
                        world.getBlockAt(x, y, z).setType(wall, false);
                }
            }
        }
        buildPad(RED_START, RED_END, Material.RED_CONCRETE, Material.RED_TERRACOTTA);
        buildPad(BLUE_START, BLUE_END, Material.BLUE_CONCRETE, Material.BLUE_TERRACOTTA);
        var redStep = (Stairs) Material.SMOOTH_QUARTZ_STAIRS.createBlockData();
        var blueStep = (Stairs) redStep.clone();
        redStep.setFacing(BlockFace.WEST);
        blueStep.setFacing(BlockFace.EAST);
        for (int z = PAD_Z_START; z < PAD_Z_END; z++) {
            world.getBlockAt(RED_END, PAD_HEIGHT, z).setBlockData(redStep, false);
            world.getBlockAt(BLUE_START - 1, PAD_HEIGHT, z).setBlockData(blueStep, false);
        }
    }

    private void buildPad(int start, int end, Material surface, Material border) {
        for (int x = start; x < end; x++) {
            for (int z = PAD_Z_START; z < PAD_Z_END; z++) {
                boolean edge = x == start || x == end - 1 || z == PAD_Z_START || z == PAD_Z_END - 1;
                world.getBlockAt(x, PAD_HEIGHT, z).setType(edge ? border : surface, false);
            }
        }
    }

    static Side platformAt(double x, double y, double z) {
        if (y < PAD_HEIGHT + 1 || y > PAD_HEIGHT + 2.5 || z < PAD_Z_START || z >= PAD_Z_END) return null;
        return sideAtX(x);
    }

    private static Side sideAtX(double x) {
        if (x >= RED_START && x < RED_END) return Side.RED;
        if (x >= BLUE_START && x < BLUE_END) return Side.BLUE;
        return null;
    }

    private Side platformAt(Location location) {
        return world.equals(location.getWorld()) ? platformAt(location.getX(), location.getY(), location.getZ()) : null;
    }

    @Override
    Location spawn(int index, int count) {
        return new Location(world, .5, FLOOR + 1, .5 + (index - (count - 1) / 2.0) * 1.25, 0, 0);
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
        if (visibility == null) hideVoters(services.participants().onlinePlayers());
    }

    private void hideVoters(List<Player> players) {
        if (visibility == null) visibility = new LessIsBetterVisibility(services.plugin());
        services.sidebar().setHiddenNonCollidingPlayers(players.stream().map(Player::getName).toList());
        players.forEach(visibility::hide);
    }

    private void showVoters() {
        if (visibility == null) return;
        visibility.clear();
        services.sidebar().setHiddenNonCollidingPlayers(List.of());
    }

    @Override
    void playerOut(Player player) {
        if (visibility != null) visibility.remove(player);
    }

    @Override
    void cleanup() {
        showVoters();
    }

    private Component sideName(Side side) {
        return side == Side.RED
                ? I18n.tr(TEXT + "red").color(NamedTextColor.RED)
                : I18n.tr(TEXT + "blue").color(NamedTextColor.AQUA);
    }

    @Override
    protected void handleMove(PlayerMoveEvent event) {
        if (event.isCancelled() || !playing() || reveal != null) return;
        Side side = platformAt(event.getTo());
        if (side != platformAt(event.getFrom())) event.getPlayer().sendActionBar(choicePrompt(side));
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
            if (ballot == 0) {
                int neutralVotes = Math.max(0, 3 - players.size());
                if (neutralVotes > 0) say(I18n.tr(TEXT + "neutral", neutralVotes));
            } else {
                hideVoters(players);
                for (int i = 0; i < players.size(); i++) teleport(players.get(i), spawn(i, players.size()));
                announceTitle(I18n.tr(TEXT + "ballot", ballot + 1, VOTES), I18n.tr(TEXT + "pick"), 2);
            }
        }
        if (offset == PICK_TICKS) reveal();
        if (offset < PICK_TICKS && offset % 20 == 0) {
            List<Player> players = alivePlayers();
            PartySounds.countdown(players, PICK_TICKS - offset);
            for (Player player : players) player.sendActionBar(choicePrompt(platformAt(player.getLocation())));
        }
    }

    private void reveal() {
        var eligible = context.eligible();
        List<Player> players = alivePlayers().stream()
                .filter(player -> eligible.contains(player.getUniqueId()) && player.getWorld().equals(world))
                .toList();
        Map<UUID, Side> choices = new LinkedHashMap<>();
        for (Player player : players) {
            Side side = platformAt(player.getLocation());
            if (side != null) choices.put(player.getUniqueId(), side);
        }
        int neutralVotes = Math.max(0, 3 - players.size());
        int neutralRed = 0;
        for (int i = 0; i < neutralVotes; i++) {
            if (random.nextBoolean()) neutralRed++;
        }
        reveal = votes.reveal(choices, neutralRed, neutralVotes - neutralRed);
        showVoters();
        Component result = reveal.winner() == null
                ? I18n.tr(TEXT + (reveal.red() == reveal.blue() ? "tie" : "empty"))
                : I18n.tr(TEXT + "wins", sideName(reveal.winner()));
        announceTitle(result, tally(), 2);
        for (Player player : players) {
            Side choice = reveal.choices().get(player.getUniqueId());
            boolean scored = choice != null && choice == reveal.winner();
            player.sendActionBar(I18n.tr(TEXT + (scored ? "point" : choice == null ? "no_pick" : "no_point"))
                    .color(scored ? NamedTextColor.GREEN : NamedTextColor.GRAY));
            player.playSound(player.getLocation(),
                    scored ? Sound.ENTITY_EXPERIENCE_ORB_PICKUP : Sound.BLOCK_NOTE_BLOCK_HAT, .7f, 1.1f);
        }
    }

    private Component tally() {
        return I18n.tr(TEXT + "tally", reveal.red(), reveal.blue());
    }

    private Component choicePrompt(Side side) {
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
        Component hint = reveal == null ? choicePrompt(platformAt(player.getLocation())) : tally();
        return List.of(text(line), I18n.tr(TEXT + "score", votes.score(id)), hint);
    }

    @Override
    public PartyRoundResult roundResult() {
        return votes.result(context.eligible());
    }
}
