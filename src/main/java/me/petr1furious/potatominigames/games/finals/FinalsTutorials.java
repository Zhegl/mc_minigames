package me.petr1furious.potatominigames.games.finals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import me.petr1furious.potatominigames.core.command.CommandFailure;
import me.petr1furious.potatominigames.core.command.GameCommands;
import me.petr1furious.potatominigames.core.game.GameSession;
import me.petr1furious.potatominigames.core.game.Minigame;
import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.core.queue.QueueService;
import me.petr1furious.potatominigames.core.session.SessionManager;
import me.petr1furious.potatominigames.core.team.TeamColor;
import me.petr1furious.potatominigames.i18n.I18n;

/** Public tutorial entry, private sessions and a persistent, optional first-queue suggestion. */
final class FinalsTutorials implements Listener {
    private final JavaPlugin plugin;
    private final FinalsConfig config;
    private final FinalsLoadouts loadouts;
    private final NamespacedKey seen;
    private final Map<UUID, FinalsSession> active = new HashMap<>();
    private SessionManager sessions;
    private QueueService queues;

    FinalsTutorials(JavaPlugin plugin, FinalsConfig config, FinalsLoadouts loadouts) {
        this.plugin = plugin;
        this.config = config;
        this.loadouts = loadouts;
        seen = new NamespacedKey(plugin, "finals_tutorial_prompt_seen");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    void configure(SessionManager sessions, QueueService queues) {
        this.sessions = sessions;
        this.queues = queues;
        sessions.addEndListener(session -> {
            if (session.game() instanceof Practice practice) {
                active.remove(practice.owner);
                loadouts.endPractice(practice.owner);
            }
        });
    }

    void loadoutChanged(Player player) {
        FinalsSession session = active.get(player.getUniqueId());
        if (session != null) session.loadoutChanged(player);
    }

    void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("tutorial")
                .executes(ctx -> GameCommands.run(ctx, c -> start(GameCommands.requirePlayer(c))))
                .then(Commands.literal("help").executes(ctx -> GameCommands.run(ctx, c -> {
                    Player player = GameCommands.requirePlayer(c);
                    FinalsSession current = active.get(player.getUniqueId());
                    if (current != null) current.tutorialHelp(player);
                    else player.sendMessage(tr("replay"));
                }))));
    }

    boolean suggest(Player player, Runnable continuation) {
        if (sessions == null || player.getPersistentDataContainer().has(seen, PersistentDataType.BYTE)) return false;
        Prompt menu = new Prompt(player.getUniqueId(), continuation);
        menu.inventory = Bukkit.createInventory(menu, 27, I18n.render(tr("prompt.title"), player));
        menu.inventory.setItem(11, button(player, Material.KNOWLEDGE_BOOK, "prompt.play", "prompt.play_lore"));
        menu.inventory.setItem(15, button(player, Material.GOLD_BLOCK, "prompt.skip", "prompt.skip_lore"));
        menu.inventory.setItem(22, button(player, Material.OAK_SIGN, "replay", "prompt.close"));
        player.openInventory(menu.inventory);
        player.getPersistentDataContainer().set(seen, PersistentDataType.BYTE, (byte) 1);
        return true;
    }

    void start(Player player) throws CommandFailure {
        if (sessions == null) throw new CommandFailure(tr("unavailable"));
        if (sessions.isPlaying(player)) throw new CommandFailure(tr("busy"));
        if (queues.queueOf(player).isPresent()) queues.leave(player);
        player.closeInventory();
        UUID id = player.getUniqueId();
        loadouts.beginPractice(id);
        try {
            sessions.start(new Practice(id), "default", List.of(player), Map.of(id, TeamColor.RED), false);
            player.getPersistentDataContainer().set(seen, PersistentDataType.BYTE, (byte) 1);
        } catch (CommandFailure | RuntimeException error) {
            loadouts.endPractice(id);
            active.remove(id);
            throw error;
        }
    }

    private final class Practice implements Minigame {
        private final UUID owner;
        Practice(UUID owner) { this.owner = owner; }
        public String id() { return "finals_tutorial_" + owner; }
        public Component displayName() { return tr("name"); }
        public Component modeTitle(String mode) { return tr("name"); }
        public String replayCommand(String mode) { return "/finals tutorial"; }
        public boolean usesTeams() { return true; }
        public List<TeamColor> teamColors(String mode) { return List.of(TeamColor.RED, TeamColor.BLUE); }
        public GameSession createSession(SessionServices services, String mode) {
            FinalsSession session = new FinalsSession(services, config, null, loadouts, true);
            active.put(owner, session);
            return session;
        }
    }

    private static final class Prompt implements InventoryHolder {
        private final UUID owner;
        private final Runnable continuation;
        private Inventory inventory;
        private boolean selected;
        Prompt(UUID owner, Runnable continuation) { this.owner = owner; this.continuation = continuation; }
        public Inventory getInventory() { return inventory; }
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Prompt menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !menu.owner.equals(player.getUniqueId())
                || menu.selected || (event.getRawSlot() != 11 && event.getRawSlot() != 15)) return;
        menu.selected = true;
        boolean tutorial = event.getRawSlot() == 11;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() != menu.inventory) return;
            player.closeInventory();
            try {
                if (tutorial) start(player);
                else menu.continuation.run();
            } catch (CommandFailure failure) {
                player.sendMessage(failure.text());
            }
        });
    }

    @EventHandler
    public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Prompt) event.setCancelled(true);
    }

    private static Component tr(String key) { return I18n.tr("finals.tutorial." + key); }

    private static ItemStack button(Player player, Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(I18n.render(tr(name), player));
            meta.lore(List.of(I18n.render(tr(lore), player)));
        });
        return item;
    }
}
