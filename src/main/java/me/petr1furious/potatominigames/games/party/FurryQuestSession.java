package me.petr1furious.potatominigames.games.party;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import me.petr1furious.potatominigames.core.game.SessionServices;
import io.papermc.paper.event.player.PlayerStopUsingItemEvent;
import me.petr1furious.potatominigames.i18n.I18n;
import net.kyori.adventure.text.Component;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameRules;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Bell;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

final class FurryQuestSession extends PartySurvivalSession {
    static final String ID = "furry_quest";
    private static final int RANGE = 72;

    private FurryQuestArena arena;
    private FurryQuest quest;
    private final Map<UUID, FurryQuestDisguise> agents = new HashMap<>();
    private final Map<UUID, FurryQuestDisguise> owners = new HashMap<>();
    private long nextShot;
    private boolean aiming;

    FurryQuestSession(SessionServices services, PartyConfig config, PartyRoundContext context) {
        super(services, config, ID, context);
    }

    @Override
    void buildArena() {
        world.setGameRule(GameRules.LOCATOR_BAR, false);
        world.setGameRule(GameRules.MOB_GRIEFING, false);
        arena = new FurryQuestArena(world, random);
        arena.build();
    }

    @Override Location spawn(int index, int count) { return arena.staging(index, count); }
    @Override Location viewpoint() { return arena.lookout(); }
    @Override double outHeight() { return FurryQuestArena.FLOOR - 3; }
    @Override long arenaTime() { return 6000; }
    @Override int durationSeconds() { return FurryQuest.SECONDS; }
    @Override boolean survivalAutoFinish() { return false; }
    @Override boolean podiumAwards() { return true; }
    @Override Component instructions() { return message("instructions"); }

    @Override
    void beginPlay() {
        var players = alivePlayers();
        if (players.size() < 2) {
            say(message("need_players"));
            finish(List.of());
            return;
        }
        quest = new FurryQuest(players.stream().map(Player::getUniqueId).toList(), random);
        var animals = arena.shuffledHerd().iterator();
        for (Player player : players) {
            UUID id = player.getUniqueId();
            if (id.equals(quest.sniper())) {
                teleport(player, viewpoint());
                player.getInventory().setItem(0, new ItemStack(Material.SPYGLASS));
                player.sendMessage(message("sniper"));
            } else {
                FurryQuestDisguise agent = new FurryQuestDisguise(player, animals.next());
                agents.put(id, agent);
                owners.put(agent.animal.getUniqueId(), agent);
                arena.claim(agent.animal);
                agent.apply(services.plugin());
                teleport(player, agent.animal.getLocation());
                player.sendMessage(message("agent", Component.translatable(agent.animal.getType().translationKey())));
                player.sendMessage(mission(id));
            }
            player.getInventory().setHeldItemSlot(0);
        }
    }

    @Override
    void playTick(long elapsed) {
        if (!playing() || quest == null) return;
        if (elapsed >= FurryQuest.SECONDS * 20L) quest.timeout();
        if (quest.outcome() == null) {
            arena.tick(elapsed);
            for (var entry : agents.entrySet()) {
                UUID id = entry.getKey();
                FurryQuestDisguise agent = entry.getValue();
                if (!world.equals(agent.player.getWorld())) continue;
                agent.sync();
                Input input = agent.player.getCurrentInput();
                FurryQuest.Site site = quest.site(id);
                if (site == null) continue;
                Location target = arena.landmark(site);
                Location body = agent.player.getLocation();
                if (elapsed % 10 == 0) {
                    agent.player.spawnParticle(Particle.HAPPY_VILLAGER, target.clone().add(0, 2.5, 0),
                            8, .45, .3, .45, 0);
                }
                if (quest.act(id, body.getX() - target.getX(), body.getZ() - target.getZ(),
                        Math.abs(body.getY() - target.getY()) <= 2, input.isSneak(), input.isJump())) {
                    playTaskEffect(agent, site);
                    agent.player.playSound(body, Sound.BLOCK_NOTE_BLOCK_CHIME, .7f, 1.4f);
                    agent.player.sendMessage(message("complete", quest.completed(id), FurryQuest.TASKS));
                    if (quest.outcome() != null) break;
                    agent.player.sendMessage(mission(id));
                }
            }
        }
        if (quest.outcome() != null) {
            say(message("end." + key(quest.outcome())));
            finish(List.of());
        }
    }

    @Override
    protected void handleMove(PlayerMoveEvent event) {
        if (quest == null || !quest.sniper().equals(event.getPlayer().getUniqueId())) return;
        arena.constrainSniper(event);
    }

    private void playTaskEffect(FurryQuestDisguise agent, FurryQuest.Site site) {
        if (site == FurryQuest.Site.HAY) {
            world.spawnParticle(Particle.BLOCK, agent.animal.getEyeLocation(), 28, .45, .3, .45, .08,
                    Material.HAY_BLOCK.createBlockData());
        } else if (site == FurryQuest.Site.BELL) {
            if (arena.landmark(site).getBlock().getRelative(0, 1, 0).getState() instanceof Bell bell)
                bell.ring();
        }
    }

    @Override
    public void onPlayerInteract(PlayerInteractEvent event) {
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND || !playing() || quest == null || quest.outcome() != null
                || !alive(player) || !world.equals(player.getWorld())) return;
        if (player.getUniqueId().equals(quest.sniper()) && event.getAction().isRightClick()
                && player.getInventory().getItemInMainHand().getType() == Material.SPYGLASS) {
            event.setUseItemInHand(Event.Result.ALLOW);
            aiming = true;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChangeSlot(PlayerItemHeldEvent event) {
        if (quest != null && event.getPlayer().getUniqueId().equals(quest.sniper())) aiming = false;
    }

    @EventHandler
    public void onRelease(PlayerStopUsingItemEvent event) {
        Player player = event.getPlayer();
        if (!aiming || !playing() || quest == null || !player.getUniqueId().equals(quest.sniper())) return;
        aiming = false;
        if (quest.outcome() == null && alive(player) && world.equals(player.getWorld())
                && event.getItem().getType() == Material.SPYGLASS
                && player.getInventory().getItemInMainHand().getType() == Material.SPYGLASS) fire(player);
    }

    private void fire(Player player) {
        if (elapsed() < nextShot) return;
        nextShot = elapsed() + 30;
        Location eye = player.getEyeLocation();
        var hit = world.rayTrace(eye, eye.getDirection(), RANGE, FluidCollisionMode.NEVER, true, .05,
                entity -> arena.contains(entity.getUniqueId()));
        world.playSound(eye, Sound.ENTITY_FIREWORK_ROCKET_BLAST, .8f, .8f);
        if (hit == null || hit.getHitEntity() == null) {
            player.sendActionBar(message("miss"));
            return;
        }
        FurryQuestDisguise agent = owners.get(hit.getHitEntity().getUniqueId());
        if (agent == null) quest.wrongShot();
        else eliminate(agent.player);
    }

    @Override
    Component status() {
        return message("time", Math.max(0, FurryQuest.SECONDS - elapsed() / 20));
    }

    private Component mission(UUID id) {
        FurryQuest.Site site = quest.site(id);
        return site == null ? message("finished") : message("task." + key(site));
    }

    @Override
    List<Component> sidebarLines(Player player, Component line) {
        var lines = new ArrayList<>(List.of(line));
        if (quest == null) return lines;
        UUID id = player.getUniqueId();
        if (quest.sniper().equals(id)) lines.add(message("remaining", quest.remaining()));
        else if (agents.containsKey(id)) {
            lines.add(mission(id));
            lines.add(message("progress", quest.completed(id), FurryQuest.TASKS));
        }
        return lines;
    }

    @Override
    void playerOut(Player player) {
        if (quest != null) quest.out(player.getUniqueId());
        removeAgent(player.getUniqueId());
    }

    private void removeAgent(UUID id) {
        FurryQuestDisguise agent = agents.remove(id);
        if (agent == null) return;
        agent.restore(services.plugin());
        owners.remove(agent.animal.getUniqueId());
        arena.remove(agent.animal);
    }

    @Override
    void cleanup() {
        for (UUID id : List.copyOf(agents.keySet())) removeAgent(id);
        if (arena != null) arena.cleanup();
    }

    @Override
    public PartyRoundResult roundResult() {
        return quest == null ? PartyRoundResult.empty() : quest.result(context.eligible());
    }

    private static String key(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private static Component message(String key, Object... args) {
        return I18n.tr("party.rounds.furry_quest." + key, args);
    }
}
