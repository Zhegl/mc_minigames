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
import org.bukkit.Bukkit;
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
        if (remainingRoundTicks() == 0) quest.timeout();
        if (quest.outcome() == null) {
            returnFallenSniper();
            arena.tick(elapsed);
            for (var entry : agents.entrySet()) {
                UUID id = entry.getKey();
                FurryQuestDisguise agent = entry.getValue();
                if (!world.equals(agent.player.getWorld())) continue;
                agent.sync();
                Input input = agent.player.getCurrentInput();
                FurryQuest.Site site = quest.site(id);
                if (site == null) continue;
                var station = quest.station(id);
                Location target = station == null ? arena.landmark(site) : arena.station(station);
                Location body = agent.player.getLocation();
                if (elapsed % 10 == 0) {
                    if (station == null) showTaskMarker(agent.player, site, target);
                    else agent.player.spawnParticle(Particle.HAPPY_VILLAGER, target.clone().add(0, 1.35, 0),
                            4, .18, .12, .18, 0);
                }
                if (site == FurryQuest.Site.FISH && quest.biteTick(id) == elapsed) {
                    Location splash = target.clone().add(-1, -.08, 0);
                    world.spawnParticle(Particle.SPLASH, splash, 10, .16, .04, .16, .04);
                    agent.player.playSound(splash, Sound.ENTITY_FISHING_BOBBER_SPLASH, .7f, 1);
                    agent.player.sendActionBar(message("bite"));
                }
                if (quest.act(id, body.getX() - target.getX(), body.getZ() - target.getZ(),
                        Math.abs(body.getY() - target.getY()) <= 2, input.isSneak(), input.isJump())) {
                    completeTask(agent, site);
                }
            }
        }
        if (quest.outcome() != null) {
            say(message("end." + key(quest.outcome())));
            finish(List.of());
        }
    }

    private void completeTask(FurryQuestDisguise agent, FurryQuest.Site site) {
        Player player = agent.player;
        UUID id = player.getUniqueId();
        playTaskEffect(agent, site);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, .7f, 1.4f);
        player.sendMessage(message("complete", quest.completed(id), FurryQuest.TASKS));
        player.sendMessage(mission(id));
        checkFinalHunt();
    }

    private void checkFinalHunt() {
        if (quest.startFinalHuntIfReady()) {
            restartRoundTimer(FurryQuest.FINAL_SECONDS);
            say(message("final_start", FurryQuest.FINAL_SECONDS));
        }
    }

    private void showTaskMarker(Player player, FurryQuest.Site site, Location target) {
        if (site == FurryQuest.Site.TREE) {
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4;
                player.spawnParticle(Particle.HAPPY_VILLAGER, target.getX() + 1.2 * Math.cos(angle),
                        target.getY() + .4, target.getZ() + 1.2 * Math.sin(angle), 1, 0, 0, 0, 0);
            }
        } else {
            player.spawnParticle(Particle.HAPPY_VILLAGER, target.clone().add(0, 2.5, 0), 8, .45, .3, .45, 0);
        }
    }

    private void returnFallenSniper() {
        Player sniper = Bukkit.getPlayer(quest.sniper());
        if (sniper == null || !world.equals(sniper.getWorld()) || !alive(sniper)) return;
        Location feet = sniper.getLocation();
        if (arena.belowLookout(feet)) {
            Location lookout = arena.lookout();
            lookout.setYaw(feet.getYaw());
            lookout.setPitch(feet.getPitch());
            teleport(sniper, lookout);
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
        } else if (site == FurryQuest.Site.SWAP) {
            var hat = arena.station(FurryQuest.Station.SCARECROW).getBlock().getRelative(0, 1, 0);
            hat.setType(hat.getType() == Material.SPRUCE_SLAB ? Material.CRIMSON_SLAB : Material.SPRUCE_SLAB, false);
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
        } else if (event.getAction().isRightClick() && event.getClickedBlock() != null) {
            var agent = agents.get(player.getUniqueId());
            if (agent != null) interactAtStation(agent, arena.stationAt(event.getClickedBlock()));
        }
    }

    private void interactAtStation(FurryQuestDisguise agent, FurryQuest.Station station) {
        if (station == null) return;
        Player player = agent.player;
        UUID id = player.getUniqueId();
        var site = quest.site(id);
        var result = quest.click(id, station, player.isSneaking(), elapsed());
        if (result == FurryQuest.Interaction.IGNORED) return;
        if (station.chime()) {
            float pitch = switch (station) {
                case BLUE -> .75f;
                case GOLD -> 1f;
                default -> 1.25f;
            };
            world.playSound(arena.station(station), Sound.BLOCK_NOTE_BLOCK_CHIME, .55f, pitch);
        }
        switch (result) {
            case COMPLETE -> completeTask(agent, site);
            case NEXT, CAST -> player.sendMessage(mission(id));
            case EARLY -> player.sendMessage(message("early"));
            case WRONG_NOTE -> player.sendMessage(message("wrong_note"));
            case IGNORED -> { }
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
        return message(quest != null && quest.finalHunt() ? "final_time" : "time", (remainingRoundTicks() + 19) / 20);
    }

    private Component mission(UUID id) {
        FurryQuest.Site site = quest.site(id);
        if (site == null) return message("finished");
        var station = quest.station(id);
        if (station == null) return message("task." + key(site));
        if (site == FurryQuest.Site.FISH && quest.biteTick(id) >= 0
                && elapsed() < quest.biteTick(id) + FurryQuest.BITE_WINDOW) return message("waiting");
        return message("task." + key(station));
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
        if (quest != null) {
            quest.out(player.getUniqueId());
            checkFinalHunt();
        }
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
