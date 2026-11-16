package me.petr1furious.potatominigames.games.party;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.*;
import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.i18n.I18n;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** A standalone, cooperative telephone game; deliberately absent from PartyRoundFactory.create. */
final class GarticPhoneSession extends PartySurvivalSession {
  // Inert full blocks only: no containers, fluids, gravity, explosives or entities.
  static final List<Material> PALETTE =
      List.of(
          Material.WHITE_CONCRETE,
          Material.LIGHT_GRAY_CONCRETE,
          Material.GRAY_CONCRETE,
          Material.BLACK_CONCRETE,
          Material.BROWN_CONCRETE,
          Material.RED_CONCRETE,
          Material.ORANGE_CONCRETE,
          Material.YELLOW_CONCRETE,
          Material.LIME_CONCRETE,
          Material.GREEN_CONCRETE,
          Material.CYAN_CONCRETE,
          Material.LIGHT_BLUE_CONCRETE,
          Material.BLUE_CONCRETE,
          Material.PURPLE_CONCRETE,
          Material.MAGENTA_CONCRETE,
          Material.PINK_CONCRETE,
          Material.WHITE_WOOL,
          Material.LIGHT_GRAY_WOOL,
          Material.GRAY_WOOL,
          Material.BLACK_WOOL,
          Material.BROWN_WOOL,
          Material.RED_WOOL,
          Material.ORANGE_WOOL,
          Material.YELLOW_WOOL,
          Material.LIME_WOOL,
          Material.GREEN_WOOL,
          Material.CYAN_WOOL,
          Material.LIGHT_BLUE_WOOL,
          Material.BLUE_WOOL,
          Material.PURPLE_WOOL,
          Material.MAGENTA_WOOL,
          Material.PINK_WOOL,
          Material.GLASS,
          Material.WHITE_STAINED_GLASS,
          Material.LIGHT_BLUE_STAINED_GLASS,
          Material.OAK_PLANKS,
          Material.SPRUCE_PLANKS,
          Material.BIRCH_PLANKS,
          Material.STONE,
          Material.COBBLESTONE,
          Material.STONE_BRICKS,
          Material.BRICKS,
          Material.SMOOTH_QUARTZ,
          Material.SANDSTONE,
          Material.TERRACOTTA,
          Material.GOLD_BLOCK,
          Material.IRON_BLOCK,
          Material.DIAMOND_BLOCK,
          Material.EMERALD_BLOCK,
          Material.SEA_LANTERN);

  private record ChatTurn(int turn, GarticPhoneState.Phase phase) {}

  private final int promptSeconds, buildSeconds, guessSeconds, revealSeconds;
  private GarticPhoneArena arena;
  private GarticPhoneState<Map<GarticPhoneArena.Cell, Material>> state;
  private List<UUID> roster;
  private final Map<UUID, Inventory> palettes = new HashMap<>();
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
    palettes.clear();
    for (UUID id : roster) {
      Map<GarticPhoneArena.Cell, Material> snapshot =
          state.phase() == GarticPhoneState.Phase.GUESS ? state.previous(id).build() : Map.of();
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
    if (building) {
      for (int i = 0; i < 7; i++)
        player.getInventory().setItem(i, new ItemStack(PALETTE.get(i * 2), 64));
      player.getInventory().setItem(7, item(player, Material.CHEST, "palette"));
      player.getInventory().setItem(8, item(player, Material.EMERALD, "done"));
    }
  }

  @Override
  void playTick(long elapsed) {
    if (state.phase() == GarticPhoneState.Phase.REVEAL) {
      if (elapsed >= deadline) revealNext();
      return;
    }
    List<UUID> active = alivePlayers().stream().map(Player::getUniqueId).toList();
    if (elapsed >= deadline || state.allReady(active)) {
      state.endTurn(arena::capture);
      if (state.phase() == GarticPhoneState.Phase.REVEAL) {
        chatTurn = new ChatTurn(state.turn(), state.phase());
        palettes.clear();
        for (Player player : alivePlayers()) resetPlayer(player, false);
        revealNext();
      } else beginTurn();
      return;
    }
    if (elapsed % 20 == 0 && state.phase() == GarticPhoneState.Phase.BUILD)
      for (Player player : alivePlayers()) {
        for (int slot = 0; slot < 7; slot++) {
          ItemStack stack = player.getInventory().getItem(slot);
          if (stack != null && PALETTE.contains(stack.getType())) stack.setAmount(64);
        }
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
      for (UUID id : roster) arena.show(id, Map.of());
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
            || !arena.editable(event.getPlayer().getUniqueId(), event.getBlockPlaced())
            || !PALETTE.contains(event.getBlockPlaced().getType()));
  }

  @Override
  protected void handleBlockBreak(BlockBreakEvent event) {
    event.setCancelled(true);
    if (building(event.getPlayer())
        && arena.editable(event.getPlayer().getUniqueId(), event.getBlock())) {
      event.setDropItems(false);
      event.setExpToDrop(0);
      event.getBlock().setType(Material.AIR, false);
    }
  }

  @Override
  protected void handleMove(PlayerMoveEvent event) {
    if (event.getTo() != null && !arena.inside(event.getPlayer().getUniqueId(), event.getTo()))
      event.setTo(arena.spawn(event.getPlayer().getUniqueId()));
  }

  @Override
  public void onPlayerInteract(PlayerInteractEvent event) {
    event.setCancelled(true);
    Player player = event.getPlayer();
    if (!building(player) || event.getHand() != EquipmentSlot.HAND) return;
    int slot = player.getInventory().getHeldItemSlot();
    if (event.getAction() == org.bukkit.event.block.Action.LEFT_CLICK_BLOCK) {
      if (event.getClickedBlock() != null
          && arena.editable(player.getUniqueId(), event.getClickedBlock()))
        event.getClickedBlock().setType(Material.AIR, false);
    } else if (event.getAction().isRightClick() && slot == 8) {
      state.readyBuild(player.getUniqueId(), state.turn());
      player.closeInventory();
      player.sendMessage(text(tr("submitted")));
    } else if (event.getAction().isRightClick() && slot == 7) openPalette(player);
    else if (slot < 7 && event.getAction().isRightClick()) event.setCancelled(false);
  }

  private void openPalette(Player player) {
    Inventory menu = Bukkit.createInventory(null, 54, I18n.render(tr("palette"), player));
    for (int i = 0; i < PALETTE.size(); i++) menu.setItem(i, new ItemStack(PALETTE.get(i)));
    palettes.put(player.getUniqueId(), menu);
    player.openInventory(menu);
  }

  @Override
  protected void handleInventoryClick(InventoryClickEvent event) {
    event.setCancelled(true);
    if (!(event.getWhoClicked() instanceof Player player) || !building(player)) return;
    Inventory menu = palettes.get(player.getUniqueId());
    if (menu != null && event.getView().getTopInventory() == menu) {
      int slot = event.getRawSlot();
      if (slot >= 0 && slot < PALETTE.size()) {
        // Keep the six most recently used colors beside the newly selected block.
        for (int i = 6; i > 0; i--)
          player.getInventory().setItem(i, player.getInventory().getItem(i - 1));
        player.getInventory().setItem(0, new ItemStack(PALETTE.get(slot), 64));
        player.getInventory().setHeldItemSlot(0);
        player.closeInventory();
      }
    }
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
    palettes.remove(id);
    Set<UUID> remaining = new HashSet<>(chatPlayers);
    remaining.remove(id);
    chatPlayers = Set.copyOf(remaining);
  }

  @Override
  void cleanup() {
    chatPlayers = Set.of();
    chatTurn = null;
    palettes.clear();
  }

  private static Component tr(String key, Object... args) {
    return I18n.tr("party.rounds.gartic_phone." + key, args);
  }

  private static ItemStack item(Player player, Material material, String key) {
    ItemStack item = new ItemStack(material);
    item.editMeta(meta -> meta.displayName(text(tr(key))));
    return I18n.item(item, player);
  }
}
