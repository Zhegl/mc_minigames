package me.petr1furious.potatominigames.games.party;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.core.hud.PrivatePlayerGlow;
import me.petr1furious.potatominigames.i18n.I18n;
import net.kyori.adventure.text.Component;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

final class FurryQuestSession extends PartySurvivalSession {
    static final String ID = "furry_quest";
    private static final double SITE_RADIUS_SQUARED = 16;
    private static final int RANGE = 72;

    private static final class Agent {
        final Player player;
        final Animals animal;
        final boolean wasVisible;
        boolean visiting;

        Agent(Player player, Animals animal) {
            this.player = player;
            this.animal = animal;
            wasVisible = player.isVisibleByDefault();
        }
    }

    private FurryQuestArena arena;
    private FurryQuest quest;
    private PrivatePlayerGlow outlines;
    private final Map<UUID, Agent> agents = new HashMap<>();
    private final Map<UUID, Agent> owners = new HashMap<>();
    private long nextShot;

    FurryQuestSession(SessionServices services, PartyConfig config, PartyRoundContext context) {
        super(services, config, ID, context);
    }

    @Override
    void buildArena() {
        world.setGameRule(GameRules.LOCATOR_BAR, false);
        world.setGameRule(GameRules.MOB_GRIEFING, false);
        arena = new FurryQuestArena(world, random);
        arena.build();
        outlines = new PrivatePlayerGlow(services.plugin());
    }

    @Override Location spawn(int index, int count) { return arena.lookout(index, false); }
    @Override Location viewpoint() { return arena.lookout(0, true); }
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
                Agent agent = new Agent(player, animals.next());
                agents.put(id, agent);
                owners.put(agent.animal.getUniqueId(), agent);
                player.setVisibleByDefault(false);
                player.getInventory().setItem(0, new ItemStack(Material.STICK));
                player.sendMessage(message("agent", Component.translatable(agent.animal.getType().translationKey())));
                player.sendMessage(mission(id));
            }
            player.getInventory().setHeldItemSlot(0);
            freezeMovement(player);
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
                Agent agent = entry.getValue();
                Location target = arena.landmark(quest.site(id));
                Location body = agent.animal.getLocation();
                if (elapsed % 10 == 0) {
                    if (agent.animal.getTrackedBy().contains(agent.player))
                        outlines.updateTargets(agent.player, List.of(agent.animal));
                    agent.player.spawnParticle(Particle.HAPPY_VILLAGER, target.clone().add(0, 2.5, 0),
                            8, .45, .3, .45, 0);
                    agent.player.spawnParticle(Particle.END_ROD, body.clone().add(0, agent.animal.getHeight() + .4, 0),
                            1, 0, 0, 0, 0);
                }
                if (quest.linger(id, agent.visiting && body.distanceSquared(target) <= SITE_RADIUS_SQUARED)) {
                    agent.visiting = false;
                    agent.player.playSound(agent.player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, .7f, 1.4f);
                    agent.player.sendMessage(message("complete", quest.completed(), quest.required()));
                    if (quest.outcome() == null) agent.player.sendMessage(mission(id));
                    else break;
                }
            }
        }
        if (quest.outcome() != null) {
            say(message("end." + key(quest.outcome())));
            finish(List.of());
        }
    }

    @Override
    public void onPlayerInteract(PlayerInteractEvent event) {
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND || !playing() || quest == null || quest.outcome() != null
                || !alive(player) || !world.equals(player.getWorld())) return;
        UUID id = player.getUniqueId();
        if (id.equals(quest.sniper()) && player.getInventory().getItemInMainHand().getType() == Material.SPYGLASS) {
            if (event.getAction().isRightClick()) event.setUseItemInHand(Event.Result.ALLOW);
            if (event.getAction().isLeftClick()) fire(player);
        } else if (event.getAction().isRightClick() && agents.containsKey(id)) {
            var hit = player.rayTraceBlocks(RANGE, FluidCollisionMode.ALWAYS);
            Location dest = hit == null || hit.getHitBlock() == null ? null : arena.destination(hit.getHitBlock());
            if (dest == null) {
                player.sendActionBar(message("aim"));
                return;
            }
            Agent agent = agents.get(id);
            arena.direct(agent.animal, dest, elapsed());
            agent.visiting = dest.distanceSquared(arena.landmark(quest.site(id))) <= SITE_RADIUS_SQUARED;
            player.spawnParticle(Particle.END_ROD, dest.clone().add(0, .4, 0), 7, .2, .15, .2, 0);
        }
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
        Agent agent = owners.get(hit.getHitEntity().getUniqueId());
        if (agent == null) quest.wrongShot();
        else eliminate(agent.player);
    }

    @Override
    Component status() {
        return message("time", Math.max(0, FurryQuest.SECONDS - elapsed() / 20));
    }

    private Component mission(UUID id) {
        return message("mission", message("site." + key(quest.site(id))));
    }

    @Override
    List<Component> sidebarLines(Player player, Component line) {
        var lines = new ArrayList<>(List.of(line));
        if (quest == null) return lines;
        UUID id = player.getUniqueId();
        if (quest.sniper().equals(id)) lines.add(message("remaining", quest.remaining()));
        else if (agents.containsKey(id)) {
            lines.add(mission(id));
            lines.add(message("progress", quest.completed(), quest.required()));
        }
        return lines;
    }

    @Override
    void playerOut(Player player) {
        if (quest != null) quest.out(player.getUniqueId());
        removeAgent(player.getUniqueId());
    }

    private void removeAgent(UUID id) {
        Agent agent = agents.remove(id);
        if (agent == null) return;
        outlines.remove(id);
        owners.remove(agent.animal.getUniqueId());
        arena.remove(agent.animal);
        agent.player.setVisibleByDefault(agent.wasVisible);
    }

    @Override
    void cleanup() {
        for (UUID id : List.copyOf(agents.keySet())) removeAgent(id);
        if (outlines != null) outlines.clear();
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
