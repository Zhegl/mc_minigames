package me.petr1furious.potatominigames.core.queue;

import java.lang.reflect.Field;
import java.util.Map;
import me.petr1furious.potatominigames.core.config.ConfigRegistry;
import me.petr1furious.potatominigames.core.game.GameSession;
import me.petr1furious.potatominigames.core.lobby.LobbyService;
import me.petr1furious.potatominigames.core.player.PlayerStateService;
import me.petr1furious.potatominigames.core.session.ActiveSession;
import me.petr1furious.potatominigames.core.session.GamePhase;
import me.petr1furious.potatominigames.core.session.SessionManager;
import me.petr1furious.potatominigames.games.party.PartyGame;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** Real queue membership and menu actions, using the optional Paper registry fixture. */
public final class PartyMenuMockCheck {
    public static void main(String[] args) throws Exception {
        var server = MockBukkit.mock();
        try {
            var plugin = MockBukkit.createMockPlugin();
            var configs = new ConfigRegistry(plugin);
            var states = new PlayerStateService(plugin);
            var sessions = new SessionManager(plugin, states, null, null, null, null, null);
            var lobby = new LobbyService(plugin, states, sessions);
            var game = new PartyGame(configs);
            sessions.registerGame(game);
            var queues = new QueueService(plugin, sessions, lobby);
            queues.createQueues(configs);
            var menu = new QueueMenu(plugin, sessions, queues);
            var player = server.addPlayer("MenuPlayer");

            menu.open(player, game);
            check(player.getOpenInventory().getTopInventory().getSize() == 54,
                    "All categories and navigation fit in one screen");
            click(menu, player, 11); // Duel row, first game.
            check(queues.queueOf(player).orElseThrow().mode().equals("pvp"),
                    "Duel item joins its own queue");
            menu.open(player, game);
            click(menu, player, 45);
            check(queues.queueOf(player).orElseThrow().mode().equals("pvp"),
                    "Back navigates without leaving the queue");
            menu.open(player, game);
            click(menu, player, 53);
            check(queues.queueOf(player).isEmpty(), "Leave Queue removes membership");
            check(player.getOpenInventory().getTopInventory().getItem(53) == null,
                    "Leave action disappears immediately after leaving");

            configs.set("queues.party.pvp", "enabled", "false");
            server.getScheduler().performTicks(20);
            check(player.getOpenInventory().getTopInventory().getItem(11) == null,
                    "Disabled modes are removed from an already-open menu");
            click(menu, player, 12);
            check(queues.queueOf(player).orElseThrow().mode().equals("sumo"),
                    "Disabling the preceding mode preserves the next mode's slot and action");
            click(menu, player, 26); // Seventh group-combat slot: choose PvP classes.
            check(queues.queueOf(player).orElseThrow().mode().equals("pvp_select"),
                    "The class-selection PvP mode from main remains directly reachable");
            menu.open(player, game);
            click(menu, player, 4);
            check(queues.queueOf(player).orElseThrow().mode().equals("party_games"),
                    "Featured tournament joins the tournament queue");
            var tournament = queues.queueOf(player).orElseThrow();
            check(!tournament.ballots().isEmpty(), "Tournament joining opens match-length selection");
            var length = tournament.ballot("length");
            check(tournament.decide(length).equals("medium"), "No votes select Medium");
            click(menu, player, 16); // Content row, fourth choice: Marathon.
            check("marathon".equals(tournament.vote("length", player.getUniqueId()))
                            && tournament.decide(length).equals("marathon"),
                    "Marathon click records the vote used by the queue's start decision");
            menu.open(player, game);
            queues.leave(player); // The view still contains an old Leave action.
            click(menu, player, 53);
            check(queues.queueOf(player).isEmpty(), "Stale leave actions tolerate lost membership");

            click(menu, player, 6);
            check(player.getOpenInventory().getTopInventory().getSize() == 27,
                    "Quick Games opens its own board");
            for (int slot = 11; slot <= 16; slot++) {
                check(player.getOpenInventory().getTopInventory().getItem(slot) != null,
                        "All six quick games have a queue icon");
            }
            click(menu, player, 11);
            check(queues.queueOf(player).orElseThrow().mode().equals("hot_potato"),
                    "The quick game icon joins its standalone queue");
            click(menu, player, 18);
            check(player.getOpenInventory().getTopInventory().getSize() == 54,
                    "Quick Games Back returns to Party");
            queues.leave(player);

            var ctor = ActiveSession.class.getDeclaredConstructors()[0];
            ctor.setAccessible(true);
            var active = (ActiveSession) ctor.newInstance(plugin, sessions, game, "sumo",
                    Map.of(), states, lobby, null, null);
            @SuppressWarnings("unchecked")
            var running = (Map<String, ActiveSession>) field(sessions, "sessions");
            running.put("party", active);
            server.getScheduler().performTicks(20);
            check(player.getOpenInventory().getTopInventory().getItem(4).getType() == Material.ENDER_EYE,
                    "Starting a shared match replaces the featured action in the open menu");
            click(menu, player, 12);
            check(queues.queueOf(player).isEmpty() && sessions.sessionOf(player).isEmpty(),
                    "A different mode cannot silently join or spectate the running match");
            running.clear();
            server.getScheduler().performTicks(20);
            click(menu, player, 12);
            check(queues.queueOf(player).orElseThrow().mode().equals("sumo"),
                    "Ending a match restores mode joining without reopening the menu");

            queues.leave(player);
            running.put("party", active);
            set(active, "phase", GamePhase.RUNNING);
            set(active, "session", (GameSession) () -> {});
            for (String mode : game.modes()) configs.set("queues.party." + mode, "enabled", "false");
            menu.open(player, game);
            click(menu, player, 4);
            check(active.isSpectator(player) && sessions.sessionOf(player).orElseThrow() == active,
                    "Disabling all queues must not block spectating the active match");
            // MockBukkit represents a closed view with a null top inventory.
            menu.open(player, game);

            var participant = server.addPlayer("Participant");
            active.participants().add(participant);
            @SuppressWarnings("unchecked")
            var indexed = (Map<java.util.UUID, ActiveSession>) field(sessions, "byPlayer");
            indexed.put(participant.getUniqueId(), active);
            menu.open(participant, game);
            server.getScheduler().performTicks(20);
            var participantView = participant.getOpenInventory().getTopInventory();
            check(participantView == null || participantView.getSize() != 54,
                    "The browsing inventory closes when its viewer becomes an active participant");
            System.out.println("Party menu: join, Quick Games, Back, leave, stale membership, disabled slots and live session transitions passed");
        } finally {
            MockBukkit.unmock();
        }
    }

    private static void click(QueueMenu menu, PlayerMock player, int slot) {
        menu.onClick(new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                slot, ClickType.LEFT, InventoryAction.PICKUP_ALL));
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
