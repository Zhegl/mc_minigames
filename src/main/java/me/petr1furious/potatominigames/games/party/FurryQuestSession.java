package me.petr1furious.potatominigames.games.party;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.core.hud.PrivatePlayerGlow;
import io.papermc.paper.event.player.PlayerStopUsingItemEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
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
import org.bukkit.entity.Animals;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

final class FurryQuestSession extends PartySurvivalSession {
    static final String ID = "furry_quest";
    private static final int RANGE = 72;
    private static final Material HIGHLIGHTER = Material.BLAZE_ROD;
    private static final Material LOWLIGHTER = Material.CHARCOAL;
    private static final Material CHASE_AXE = Material.IRON_AXE;
    private static final int CHASE_TICKS = 15 * 20;
    private static final int CHASE_COOLDOWN_TICKS = 15 * 20;

    private FurryQuestArena arena;
    private FurryQuest quest;
    private final Map<UUID, FurryQuestDisguise> agents = new HashMap<>();
    private final Map<UUID, FurryQuestDisguise> owners = new HashMap<>();
    private PrivatePlayerGlow privateGlow;
    private FurryQuestHunterMarks hunterMarks;
    private long lastMarkTick = -1;
    private long nextShot;
    private boolean aiming;
    private long chaseEndsAt;
    private long chaseReadyAt;
    private long lastAxeUseTick = -1;

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
        privateGlow = new PrivatePlayerGlow(services.plugin());
        quest = new FurryQuest(players.stream().map(Player::getUniqueId).toList(), arena.doubleAgent().getUniqueId(), random);
        var animals = arena.shuffledDisguises().iterator();
        for (Player player : players) {
            UUID id = player.getUniqueId();
            if (id.equals(quest.sniper())) {
                teleport(player, viewpoint());
                player.getInventory().setItem(0, new ItemStack(Material.SPYGLASS));
                player.getInventory().setItem(1, hunterTool(HIGHLIGHTER, "highlighter", player));
                player.getInventory().setItem(2, hunterTool(LOWLIGHTER, "lowlighter", player));
                player.getInventory().setItem(3, hunterTool(CHASE_AXE, "chase_axe", player));
                player.sendMessage(message("sniper"));
                player.sendMessage(message("mark_guide"));
                player.sendMessage(message("chase_guide"));
            } else {
                FurryQuestDisguise agent = new FurryQuestDisguise(player, animals.next());
                agents.put(id, agent);
                owners.put(agent.animal.getUniqueId(), agent);
                arena.claim(agent.animal);
                agent.apply(services.plugin());
                teleport(player, agent.animal.getLocation());
                player.sendMessage(message("agent", Component.translatable(agent.animal.getType().translationKey())));
                player.sendMessage(message("task_guide"));
                for (var task : quest.assignments(id)) player.sendMessage(mission(id, task));
            }
            player.getInventory().setHeldItemSlot(0);
        }
        refreshTeammateOutlines();
    }

    private void refreshTeammateOutlines() {
        for (var viewer : agents.values()) {
            var teammates = agents.values().stream()
                    .filter(teammate -> teammate != viewer)
                    .map(teammate -> teammate.animal)
                    .toList();
            privateGlow.updateTargets(viewer.player, teammates);
        }
    }

    @Override
    void playTick(long elapsed) {
        if (!playing() || quest == null) return;
        if (remainingRoundTicks() == 0) quest.timeout();
        if (quest.outcome() == null) {
            tickHunter(elapsed);
            arena.tick(elapsed);
            if (elapsed % 10 == 0) {
                refreshTeammateOutlines();
                if (hunterMarks != null) hunterMarks.refresh();
            }
            for (var entry : agents.entrySet()) {
                UUID id = entry.getKey();
                FurryQuestDisguise agent = entry.getValue();
                if (!world.equals(agent.player.getWorld())) continue;
                agent.sync();
                Input input = agent.player.getCurrentInput();
                FurryQuest.Site site = quest.site(id);
                if (site == null) continue;
                Location body = agent.player.getLocation();
                if (elapsed % 10 == 0) {
                    var station = quest.station(id, site);
                    Location target = station == null ? arena.landmark(site) : arena.station(station);
                    if (station == null) showTaskMarker(agent.player, site, target);
                    else agent.player.spawnParticle(Particle.HAPPY_VILLAGER, target.clone().add(0, 1.35, 0),
                            4, .18, .12, .18, 0);
                }
                if (quest.biteTick(id) == elapsed) {
                    Location splash = arena.station(FurryQuest.Station.DOCK_POST).add(-1, -.08, 0);
                    world.spawnParticle(Particle.SPLASH, splash, 10, .16, .04, .16, .04);
                    agent.player.playSound(splash, Sound.ENTITY_FISHING_BOBBER_SPLASH, .7f, 1);
                    agent.player.sendActionBar(message("bite"));
                }
                for (var task : quest.assignments(id)) {
                    if (task.plot == null || !task.stops.isEmpty()) continue;
                    if (quest.act(id, task, body.getX() - (task.plot.x() + .5), body.getZ() - (task.plot.z() + .5),
                            Math.abs(body.getY() - (FurryQuestArena.FLOOR + 1)) <= 2, input.isSneak(), input.isJump()))
                        completeTask(agent, task);
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
        player.sendMessage(mission(id, quest.site(id)));
        checkFinalHunt();
    }

    private void checkFinalHunt() {
        if (quest.startFinalHuntIfReady()) {
            restartRoundTimer(FurryQuest.FINAL_SECONDS);
            say(message("final_start", FurryQuest.FINAL_SECONDS));
        }
    }

    private void showTaskMarker(Player player, FurryQuest.Site site, Location target) {
        if (site == FurryQuest.Site.CONTACT) {
            player.spawnParticle(Particle.HAPPY_VILLAGER, target.clone().add(0, arena.doubleAgent().getHeight() + .25, 0),
                    3, .1, .06, .1, 0);
        } else if (site == FurryQuest.Site.TREE) {
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4;
                player.spawnParticle(Particle.HAPPY_VILLAGER, target.getX() + 1.2 * Math.cos(angle),
                        target.getY() + .4, target.getZ() + 1.2 * Math.sin(angle), 1, 0, 0, 0, 0);
            }
        } else {
            player.spawnParticle(Particle.HAPPY_VILLAGER, target.clone().add(0, 2.5, 0), 8, .45, .3, .45, 0);
        }
    }

    private void tickHunter(long elapsed) {
        Player sniper = Bukkit.getPlayer(quest.sniper());
        if (sniper == null || !world.equals(sniper.getWorld()) || !alive(sniper)) return;
        if (chaseEndsAt != 0) {
            long left = chaseEndsAt - elapsed;
            if (left <= 0 || sniper.getY() < outHeight()) {
                recallHunter(sniper);
            } else if (left <= 60 && left % 20 == 0) {
                sniper.playSound(sniper.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, .8f, 1.4f);
            }
        } else {
            if (arena.belowLookout(sniper.getLocation())) returnToTower(sniper);
            if (chaseReadyAt != 0 && elapsed == chaseReadyAt)
                sniper.playSound(sniper.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, .7f, 1.4f);
        }
    }

    private void toggleChase(Player hunter) {
        // A single right-click on an animal can reach both interaction handlers.
        if (lastAxeUseTick == elapsed()) return;
        lastAxeUseTick = elapsed();
        if (chaseEndsAt != 0) {
            recallHunter(hunter);
            return;
        }
        if (elapsed() < chaseReadyAt) {
            hunter.sendActionBar(message("chase_cooldown", (chaseReadyAt - elapsed() + 19) / 20));
            return;
        }
        aiming = false;
        chaseEndsAt = elapsed() + CHASE_TICKS;
        Location facing = hunter.getLocation();
        facing.setPitch(0);
        hunter.setVelocity(facing.getDirection().multiply(1.1).setY(.42));
        world.playSound(facing, Sound.EVENT_RAID_HORN, 2.2f, 1.3f);
        hunter.sendActionBar(message("chase_active", CHASE_TICKS / 20));
    }

    private void recallHunter(Player hunter) {
        chaseEndsAt = 0;
        chaseReadyAt = elapsed() + CHASE_COOLDOWN_TICKS;
        world.playSound(hunter.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 2f, 1.2f);
        returnToTower(hunter);
        hunter.sendActionBar(message("chase_cooldown", CHASE_COOLDOWN_TICKS / 20));
    }

    private void returnToTower(Player hunter) {
        Location lookout = arena.lookout();
        Location facing = hunter.getLocation();
        lookout.setYaw(facing.getYaw());
        lookout.setPitch(facing.getPitch());
        teleport(hunter, lookout);
    }

    @Override
    protected void handleMove(PlayerMoveEvent event) {
        if (quest == null || chaseEndsAt != 0 || !quest.sniper().equals(event.getPlayer().getUniqueId())) return;
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
        } else if (site == FurryQuest.Site.CONTACT) {
            var contact = arena.doubleAgent();
            Sound call = contact.getAmbientSound();
            if (call != null) world.playSound(contact.getLocation(), call, org.bukkit.SoundCategory.NEUTRAL, 1, 1);
        }
    }

    @Override
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!playing() || quest == null || quest.outcome() != null
                || !alive(player) || !world.equals(player.getWorld())) return;
        var animal = event.getRightClicked();
        if (quest.sniper().equals(player.getUniqueId()) && event.getHand() == EquipmentSlot.HAND
                && player.getInventory().getItemInMainHand().getType() == CHASE_AXE) {
            event.setCancelled(true);
            toggleChase(player);
            return;
        }
        if (!arena.contains(animal.getUniqueId())) return;
        event.setCancelled(true);
        if (quest.sniper().equals(player.getUniqueId())) {
            if (event.getHand() != EquipmentSlot.HAND || chaseEndsAt != 0) return;
            Material held = player.getInventory().getItemInMainHand().getType();
            if (held == HIGHLIGHTER)
                markAnimal(player, false);
            return;
        }
        if (!agents.containsKey(player.getUniqueId())) return;
        if (event.getHand() == EquipmentSlot.HAND && player.getLocation().distanceSquared(animal.getLocation()) <= 16
                && player.hasLineOfSight(animal) && quest.contact(player.getUniqueId(), animal.getUniqueId()))
            completeTask(agents.get(player.getUniqueId()), FurryQuest.Site.CONTACT);
    }

    @Override
    public void onPlayerInteract(PlayerInteractEvent event) {
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND || !playing() || quest == null || quest.outcome() != null
                || !alive(player) || !world.equals(player.getWorld())) return;
        if (player.getUniqueId().equals(quest.sniper())) {
            Material held = player.getInventory().getItemInMainHand().getType();
            if (event.getAction().isRightClick() && held == CHASE_AXE) {
                toggleChase(player);
                return;
            }
            if (chaseEndsAt != 0) return;
            if (event.getAction().isRightClick() && held == Material.SPYGLASS) {
                event.setUseItemInHand(Event.Result.ALLOW);
                aiming = true;
            } else if (event.getAction().isRightClick() && held == HIGHLIGHTER) {
                markAnimal(player, false);
            } else if (event.getAction().isLeftClick() && held == LOWLIGHTER) {
                markAnimal(player, true);
            }
        } else if (event.getAction().isRightClick() && event.getClickedBlock() != null) {
            var agent = agents.get(player.getUniqueId());
            if (agent != null) interactAtStation(agent, arena.stationAt(event.getClickedBlock()));
        }
    }

    @Override
    public void onPrePlayerAttackEntity(PrePlayerAttackEntityEvent event) {
        Player player = event.getPlayer();
        if (!playing() || quest == null || quest.outcome() != null || !quest.sniper().equals(player.getUniqueId())
                || !alive(player) || !world.equals(player.getWorld())
                || !(event.getAttacked() instanceof Animals animal) || !arena.contains(animal.getUniqueId())) return;
        Material held = player.getInventory().getItemInMainHand().getType();
        if (held == CHASE_AXE) {
            event.setCancelled(true);
            if (elapsed() < chaseEndsAt && player.hasLineOfSight(animal)) {
                world.playSound(animal.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, .85f);
                hitAnimal(animal);
            }
        } else if (held == LOWLIGHTER && chaseEndsAt == 0) {
            event.setCancelled(true);
            markAnimal(player, true);
        }
    }

    private void interactAtStation(FurryQuestDisguise agent, FurryQuest.Station station) {
        if (station == null) return;
        Player player = agent.player;
        UUID id = player.getUniqueId();
        var site = station.task();
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
            case NEXT, CAST -> player.sendMessage(mission(id, site));
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
        if (quest.outcome() == null && chaseEndsAt == 0 && alive(player) && world.equals(player.getWorld())
                && event.getItem().getType() == Material.SPYGLASS
                && player.getInventory().getItemInMainHand().getType() == Material.SPYGLASS) fire(player);
    }

    private Animals sightedAnimal(Player player) {
        Location eye = player.getEyeLocation();
        var hit = world.rayTrace(eye, eye.getDirection(), RANGE, FluidCollisionMode.NEVER, true, .05,
                entity -> arena.contains(entity.getUniqueId()));
        return hit != null && hit.getHitEntity() instanceof Animals animal ? animal : null;
    }

    private void fire(Player player) {
        if (elapsed() < nextShot) return;
        nextShot = elapsed() + 30;
        Animals animal = sightedAnimal(player);
        world.playSound(player.getEyeLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, .8f, .8f);
        if (animal == null) {
            player.sendActionBar(message("miss"));
            return;
        }
        hitAnimal(animal);
    }

    private void hitAnimal(Animals animal) {
        FurryQuestDisguise agent = owners.get(animal.getUniqueId());
        if (agent == null) quest.wrongShot();
        else eliminate(agent.player);
    }

    @Override
    Component status() {
        return message(quest != null && quest.finalHunt() ? "final_time" : "time", (remainingRoundTicks() + 19) / 20);
    }

    @Override
    protected void handleSwapHandItems(PlayerSwapHandItemsEvent event) {
        super.handleSwapHandItems(event);
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (!playing() || quest == null || quest.outcome() != null) return;
        if (!agents.containsKey(id) || quest.site(id) == null) return;
        quest.cycle(id);
        player.sendActionBar(mission(id, quest.site(id)));
    }

    private ItemStack hunterTool(Material material, String key, Player hunter) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> meta.itemName(I18n.render(message(key), hunter)));
        return item;
    }

    private void markAnimal(Player hunter, boolean dim) {
        if (lastMarkTick == elapsed()) return;
        lastMarkTick = elapsed();
        Animals animal = sightedAnimal(hunter);
        if (animal == null) {
            hunter.sendActionBar(message("mark_no_target"));
            return;
        }
        if (hunterMarks == null) hunterMarks = new FurryQuestHunterMarks(hunter, privateGlow);
        boolean applied = hunterMarks.toggle(animal, dim);
        String key = applied ? (dim ? "mark_lowlight" : "mark_highlight") : "mark_clear";
        hunter.sendActionBar(message(key, Component.translatable(animal.getType().translationKey())));
    }

    private Component mission(UUID id, FurryQuest.Site site) {
        if (site == null) return message("finished");
        return message("rated_task", message("difficulty." + key(site.difficulty)), taskInstructions(id, site));
    }

    private Component taskInstructions(UUID id, FurryQuest.Site site) {
        if (site == FurryQuest.Site.CONTACT)
            return message("task.contact", Component.translatable(arena.doubleAgent().getType().translationKey()));
        var station = quest.station(id, site);
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
        if (quest.sniper().equals(id)) {
            lines.add(message("remaining", quest.remaining()));
            if (chaseEndsAt != 0) lines.add(message("chase_active", (chaseEndsAt - elapsed() + 19) / 20));
            else if (elapsed() < chaseReadyAt) lines.add(message("chase_cooldown", (chaseReadyAt - elapsed() + 19) / 20));
            else lines.add(message("chase_ready"));
        } else if (agents.containsKey(id)) {
            lines.add(mission(id, quest.site(id)));
            lines.add(message("progress", quest.completed(id), FurryQuest.TASKS));
        }
        return lines;
    }

    @Override
    void playerOut(Player player) {
        if (quest != null) {
            if (quest.sniper().equals(player.getUniqueId())) clearHunterMarks();
            quest.out(player.getUniqueId());
            checkFinalHunt();
        }
        removeAgent(player.getUniqueId());
    }

    private void removeAgent(UUID id) {
        FurryQuestDisguise agent = agents.remove(id);
        if (agent == null) return;
        if (hunterMarks != null) hunterMarks.remove(agent.animal);
        privateGlow.remove(id);
        privateGlow.remove(agent.animal.getUniqueId());
        agent.restore(services.plugin());
        owners.remove(agent.animal.getUniqueId());
        arena.remove(agent.animal);
    }

    @Override
    void cleanup() {
        clearHunterMarks();
        for (UUID id : List.copyOf(agents.keySet())) removeAgent(id);
        if (arena != null) arena.cleanup();
    }

    private void clearHunterMarks() {
        if (hunterMarks != null) {
            hunterMarks.clear();
            hunterMarks = null;
        }
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
