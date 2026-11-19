package me.petr1furious.potatominigames.games.party;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.*;
import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.i18n.I18n;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.structure.Structure;

/** A standalone, cooperative telephone game; deliberately absent from PartyRoundFactory.create. */
final class GarticPhoneSession extends PartySurvivalSession {
  private record ChatTurn(int turn, GarticPhoneState.Phase phase) {}

  private final int promptSeconds, buildSeconds, guessSeconds, revealSeconds;
  private GarticPhoneArena arena;
  private GarticPhoneState<Structure> state;
  private List<UUID> roster;
  private final Map<UUID, Structure> submittedBuilds = new HashMap<>();
  // Only immutable snapshots cross the asynchronous chat boundary.
  private volatile Set<UUID> chatPlayers = Set.of();
  private volatile ChatTurn chatTurn;
  private long deadline;
  private int revealChain, revealStep;

  GarticPhoneSession(SessionServices services, PartyConfig config) {
    super(services, config, "gartic_phone", PartyRoundContext.standalone(services));
    promptSeconds = config.garticPromptSeconds.get();
    buildSeconds = config.garticBuildSeconds.get();
    guessSeconds = config.garticGuessSeconds.get();
    revealSeconds = config.garticRevealSeconds.get();
  }

  @Override
  void buildArena() {
    roster = List.copyOf(names.keySet());
    state = new GarticPhoneState<>(roster);
    arena = new GarticPhoneArena(world, roster);
    arena.build();
    chatPlayers = Set.copyOf(roster);
    world.setDifficulty(Difficulty.PEACEFUL);
    world.setGameRule(GameRules.ADVANCE_TIME, false);
  }

  @Override
  Location spawn(int index, int count) {
    return arena.spawn(roster.get(index));
  }

  @Override
  Location viewpoint() {
    return arena.spawn(roster.getFirst());
  }

  @Override
  double outHeight() {
    return 70;
  }

  @Override
  long arenaTime() {
    return 6000;
  }

  @Override
  boolean survivalAutoFinish() {
    return false;
  }

  // No competition or point awards: completion is an empty podium rather than survival scoring.
  @Override
  boolean podiumAwards() {
    return true;
  }

  @Override
  public PartyRoundResult roundResult() {
    return PartyRoundResult.empty();
  }

  @Override
  int durationSeconds() {
    int turns = Math.max(3, names.size());
    return promptSeconds
        + turns * Math.max(buildSeconds, guessSeconds)
        + names.size() * turns * revealSeconds
        + 30;
  }

  @Override
  Component instructions() {
    return tr("instructions");
  }

  @Override
  void preparePlayer(Player player) {
    player.setAllowFlight(true);
    player.setFlying(true);
  }

  @Override
  void beginPlay() {
    beginTurn();
  }

  private void beginTurn() {
    chatTurn = new ChatTurn(state.turn(), state.phase());
    int seconds =
        switch (state.phase()) {
          case PROMPT -> promptSeconds;
          case BUILD -> buildSeconds;
          case GUESS -> guessSeconds;
          case REVEAL -> throw new IllegalStateException("Reveal has its own timer");
        };
    deadline = elapsed() + seconds * 20L;
    submittedBuilds.clear();
    for (UUID id : roster) {
      Structure snapshot =
          state.phase() == GarticPhoneState.Phase.GUESS ? state.previous(id).build() : null;
      arena.show(id, snapshot);
    }
    for (Player player : alivePlayers()) {
      UUID id = player.getUniqueId();
      resetPlayer(player, state.phase() == GarticPhoneState.Phase.BUILD);
      Component instruction =
          switch (state.phase()) {
            case PROMPT -> tr("write_prompt");
            case BUILD ->
                tr(
                    "build_prompt",
                    stepText(state.previous(id), state.chainIndex(id), state.turn() - 1));
            case GUESS -> tr("write_guess");
            default -> Component.empty();
          };
      player.sendMessage(text(instruction));
      player.showTitle(
          net.kyori.adventure.title.Title.title(
              text(tr("turn", state.turn() + 1, state.turns())), instruction));
    }
  }

  private void resetPlayer(Player player, boolean building) {
    player.closeInventory();
    player.setItemOnCursor(null);
    services.playerState().resetForRound(player, building ? GameMode.CREATIVE : GameMode.ADVENTURE);
    player.setAllowFlight(true);
    player.setFlying(true);
    teleport(player, arena.spawn(player.getUniqueId()));
  }

  @Override
  void playTick(long elapsed) {
    if (state.phase() == GarticPhoneState.Phase.REVEAL) {
      if (elapsed >= deadline) revealNext();
      return;
    }
    List<UUID> active = alivePlayers().stream().map(Player::getUniqueId).toList();
    if (elapsed >= deadline || state.allReady(active)) {
      state.endTurn(
          id -> submittedBuilds.containsKey(id) ? submittedBuilds.get(id) : arena.capture(id));
      if (state.phase() == GarticPhoneState.Phase.REVEAL) {
        chatTurn = new ChatTurn(state.turn(), state.phase());
        submittedBuilds.clear();
        for (Player player : alivePlayers()) resetPlayer(player, false);
        revealNext();
      } else beginTurn();
      return;
    }
    if (elapsed % 20 == 0 && state.phase() == GarticPhoneState.Phase.BUILD)
      for (Player player : alivePlayers()) {
        player.sendActionBar(
            tr(
                "build_prompt",
                stepText(
                    state.previous(player.getUniqueId()),
                    state.chainIndex(player.getUniqueId()),
                    state.turn() - 1)));
      }
  }

  private void revealNext() {
    if (revealChain >= roster.size()) {
      say(tr("finished"));
      finish(List.of());
      return;
    }
    if (revealStep == 0) {
      say(tr("chain", revealChain + 1, roster.size(), names.get(roster.get(revealChain))));
      for (UUID id : roster) arena.show(id, null);
    }
    var step = state.chain(revealChain).get(revealStep);
    if (step.build() != null) {
      for (UUID id : roster) arena.show(id, step.build());
      say(tr("reveal_build", revealStep + 1, names.get(step.author())));
    } else
      say(
          tr(
              "reveal_text",
              revealStep + 1,
              names.get(step.author()),
              stepText(step, revealChain, revealStep)));
    announceTitle(
        tr("chain", revealChain + 1, roster.size(), names.get(roster.get(revealChain))),
        step.build() == null
            ? stepText(step, revealChain, revealStep)
            : tr("built_by", names.get(step.author())),
        3);
    PartySounds.play(services.session().audiencePlayers(), Sound.BLOCK_NOTE_BLOCK_BELL, .6f, 1.2f);
    if (++revealStep >= state.chain(revealChain).size()) {
      revealStep = 0;
      revealChain++;
    }
    deadline = elapsed() + revealSeconds * 20L;
  }

  private Component stepText(GarticPhoneState.Step<?> step, int chain, int turn) {
    if (!step.text().isEmpty()) return Component.text(step.text());
    return turn == 0 ? tr("fallback." + chain % 8) : tr("missing");
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onPrivateChat(AsyncChatEvent event) {
    UUID id = event.getPlayer().getUniqueId();
    if (!chatPlayers.contains(id)) return;
    ChatTurn expected = chatTurn;
    if (expected == null || expected.phase() == GarticPhoneState.Phase.REVEAL) return;
    event.setCancelled(true);
    String value = PlainTextComponentSerializer.plainText().serialize(event.message());
    Bukkit.getScheduler()
        .runTask(
            services.plugin(),
            () -> {
              if (chatTurn != expected || context.ended() || elapsed() >= deadline) return;
              Player player = Bukkit.getPlayer(id);
              if (player == null || !alive(player)) return;
              if (state.submitText(id, expected.turn(), value))
                player.sendMessage(text(tr("submitted")));
              else player.sendMessage(text(tr(state.ready(id) ? "locked" : "chat_hint")));
            });
  }

  private boolean building(Player player) {
    return playing()
        && state.phase() == GarticPhoneState.Phase.BUILD
        && alive(player)
        && !state.ready(player.getUniqueId())
        && elapsed() < deadline;
  }

  @Override
  protected void handleBlockPlace(BlockPlaceEvent event) {
    event.setCancelled(
        !building(event.getPlayer())
            || !arena.editable(event.getPlayer().getUniqueId(), event.getBlockPlaced()));
    if (!event.isCancelled() && event instanceof BlockMultiPlaceEvent multi)
      for (var replaced : multi.getReplacedBlockStates())
        if (!arena.editable(event.getPlayer().getUniqueId(), replaced.getBlock()))
          event.setCancelled(true);
  }

  @Override
  protected void handleBlockBreak(BlockBreakEvent event) {
    event.setCancelled(
        !building(event.getPlayer())
            || !arena.editable(event.getPlayer().getUniqueId(), event.getBlock()));
    event.setDropItems(false);
    event.setExpToDrop(0);
  }

  @Override
  protected void handleMove(PlayerMoveEvent event) {
    if (event.getTo() != null && !arena.inside(event.getPlayer().getUniqueId(), event.getTo()))
      event.setTo(arena.spawn(event.getPlayer().getUniqueId()));
  }

  @Override
  public void onPlayerInteract(PlayerInteractEvent event) {
    boolean canBuild = building(event.getPlayer());
    event.setCancelled(!canBuild);
    if (canBuild && event.getClickedBlock() != null) {
      UUID id = event.getPlayer().getUniqueId();
      if (!arena.editable(id, event.getClickedBlock())
          && !(event.getAction().isRightClick()
              && arena.editable(id, event.getClickedBlock().getRelative(event.getBlockFace()))))
        event.setCancelled(true);
    }
  }

  @Override
  protected void handleInventoryClick(InventoryClickEvent event) {
    event.setCancelled(!(event.getWhoClicked() instanceof Player player) || !building(player));
  }

  @Override
  protected void handleInventoryDrag(InventoryDragEvent event) {
    event.setCancelled(!(event.getWhoClicked() instanceof Player player) || !building(player));
  }

  @Override
  protected void handleSwapHandItems(PlayerSwapHandItemsEvent event) {
    Player player = event.getPlayer();
    event.setCancelled(!building(player) || player.isSneaking());
    if (building(player) && player.isSneaking()) {
      submittedBuilds.put(player.getUniqueId(), arena.capture(player.getUniqueId()));
      state.readyBuild(player.getUniqueId(), state.turn());
      player.closeInventory();
      player.sendMessage(text(tr("submitted")));
    }
  }

  private boolean editableNow(Block block) {
    UUID owner = arena.owner(block);
    return playing()
        && state.phase() == GarticPhoneState.Phase.BUILD
        && owner != null
        && connected.contains(owner)
        && !state.ready(owner)
        && elapsed() < deadline;
  }

  @EventHandler
  public void onBucketEmpty(org.bukkit.event.player.PlayerBucketEmptyEvent event) {
    if (event.getPlayer().getWorld().equals(world))
      event.setCancelled(
          !building(event.getPlayer())
              || !arena.editable(event.getPlayer().getUniqueId(), event.getBlock()));
  }

  @EventHandler
  public void onBucketFill(org.bukkit.event.player.PlayerBucketFillEvent event) {
    if (event.getPlayer().getWorld().equals(world))
      event.setCancelled(
          !building(event.getPlayer())
              || !arena.editable(event.getPlayer().getUniqueId(), event.getBlock()));
  }

  @EventHandler
  public void onTreeGrow(org.bukkit.event.world.StructureGrowEvent event) {
    if (!event.getWorld().equals(world)) return;
    Block origin = world.getBlockAt(event.getLocation());
    UUID owner = arena.owner(origin);
    if (owner == null || !editableNow(origin)) {
      event.setCancelled(true);
      return;
    }
    for (var block : event.getBlocks())
      if (!arena.editable(owner, block.getBlock())) event.setCancelled(true);
  }

  @EventHandler
  public void onFluidFlow(BlockFromToEvent event) {
    if (!event.getBlock().getWorld().equals(world)) return;
    UUID owner = arena.owner(event.getBlock());
    if (owner == null
        || !editableNow(event.getBlock())
        || !arena.editable(owner, event.getToBlock())) event.setCancelled(true);
  }

  @EventHandler
  public void onPistonExtend(BlockPistonExtendEvent event) {
    if (!event.getBlock().getWorld().equals(world)) return;
    UUID owner = arena.owner(event.getBlock());
    if (owner == null
        || !editableNow(event.getBlock())
        || !arena.editable(owner, event.getBlock().getRelative(event.getDirection()))) {
      event.setCancelled(true);
      return;
    }
    for (Block block : event.getBlocks())
      if (!arena.editable(owner, block)
          || !arena.editable(owner, block.getRelative(event.getDirection())))
        event.setCancelled(true);
  }

  @EventHandler
  public void onDispense(BlockDispenseEvent event) {
    Block source = event.getBlock();
    if (!source.getWorld().equals(world)) return;
    UUID owner = arena.owner(source);
    if (owner == null
        || !editableNow(source)
        || !(source.getBlockData() instanceof org.bukkit.block.data.Directional directional)
        || !arena.editable(owner, source.getRelative(directional.getFacing())))
      event.setCancelled(true);
  }

  @EventHandler
  public void onBlockForm(BlockFormEvent event) {
    if (event.getBlock().getWorld().equals(world) && !editableNow(event.getBlock()))
      event.setCancelled(true);
  }

  @EventHandler
  public void onBlockGrow(BlockGrowEvent event) {
    if (event.getBlock().getWorld().equals(world) && !editableNow(event.getBlock()))
      event.setCancelled(true);
  }

  @EventHandler
  public void onBlockSpread(BlockSpreadEvent event) {
    if (!event.getBlock().getWorld().equals(world)) return;
    UUID owner = arena.owner(event.getSource());
    if (owner == null
        || !editableNow(event.getSource())
        || !arena.editable(owner, event.getBlock())) event.setCancelled(true);
  }

  @EventHandler
  public void onBlockIgnite(BlockIgniteEvent event) {
    if (event.getBlock().getWorld().equals(world) && !editableNow(event.getBlock()))
      event.setCancelled(true);
  }

  @EventHandler
  public void onPistonRetract(BlockPistonRetractEvent event) {
    if (!event.getBlock().getWorld().equals(world)) return;
    UUID owner = arena.owner(event.getBlock());
    if (owner == null || !editableNow(event.getBlock())) {
      event.setCancelled(true);
      return;
    }
    for (Block block : event.getBlocks())
      if (!arena.editable(owner, block)) event.setCancelled(true);
  }

  @Override
  Component status() {
    if (state.phase() == GarticPhoneState.Phase.REVEAL) return tr("revealing");
    return tr(
        "status",
        state.turn() + 1,
        state.turns(),
        tr("phase." + state.phase().name().toLowerCase(Locale.ROOT)),
        Math.max(0, (deadline - elapsed() + 19) / 20));
  }

  @Override
  List<Component> sidebarLines(Player player, Component line) {
    return List.of(line, tr("cooperative"));
  }

  @Override
  void playerOut(Player player) {
    UUID id = player.getUniqueId();
    Set<UUID> remaining = new HashSet<>(chatPlayers);
    remaining.remove(id);
    chatPlayers = Set.copyOf(remaining);
  }

  @Override
  void cleanup() {
    chatPlayers = Set.of();
    chatTurn = null;
    submittedBuilds.clear();
  }

  private static Component tr(String key, Object... args) {
    return I18n.tr("party.rounds.gartic_phone." + key, args);
  }
}
