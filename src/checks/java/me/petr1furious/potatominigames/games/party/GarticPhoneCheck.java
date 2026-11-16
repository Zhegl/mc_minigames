package me.petr1furious.potatominigames.games.party;

import static me.petr1furious.potatominigames.games.party.PartyScoringCheck.check;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class GarticPhoneCheck {
  public static void main(String[] args) throws Exception {
    chains();
    arenas();
    interactions();
    PartyGame game = new PartyGame((PartyConfig) null);
    check(game.modes().contains("gartic_phone"), "Gartic Phone is independently selectable");
    check(game.maxPlayers("gartic_phone") == 12, "Build rooms support at most twelve players");
    check(
        !PartyGamesState.MODES.contains("gartic_phone"), "Gartic Phone stays out of the rotation");
    var queues =
        org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
            new java.io.InputStreamReader(
                GarticPhoneCheck.class.getResourceAsStream("/config.yml"),
                java.nio.charset.StandardCharsets.UTF_8));
    check(
        queues.getBoolean("queues.party.gartic_phone.enabled")
            && queues.getInt("queues.party.gartic_phone.maxPlayers") == 12,
        "Ship an enabled standalone queue");
    check(
        me.petr1furious.potatominigames.i18n.I18n.plain(
                game.modeTitle("gartic_phone"), java.util.Locale.ENGLISH)
            .equals("Gartic Phone"),
        "The menu displays the localized title");
    try {
      new PartyGamesState(
          List.of(new UUID(0, 1), new UUID(0, 2)), List.of("gartic_phone"), new Random(1));
      throw new AssertionError("Explicit pool configuration accepted Gartic Phone");
    } catch (IllegalArgumentException expected) {
    }
    System.out.println(
        "Gartic Phone chains, private rooms, editing controls, queue and pool exclusion passed");
  }

  private static void interactions() throws Exception {
    UUID id = new UUID(0, 1);
    var world = new PartyArenaCheck.WorldMap();
    var arena = new GarticPhoneArena(world.world, List.of(id));
    var state =
        new GarticPhoneState<java.util.Map<GarticPhoneArena.Cell, org.bukkit.Material>>(
            List.of(id));
    state.submitText(id, 0, "a cat");
    state.endTurn(ignored -> java.util.Map.of());
    var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
    unsafeField.setAccessible(true);
    var session =
        (GarticPhoneSession)
            ((sun.misc.Unsafe) unsafeField.get(null)).allocateInstance(GarticPhoneSession.class);
    inject(session, "playing", true);
    inject(session, "connected", new HashSet<>(Set.of(id)));
    inject(session, "standings", new SurvivalStandings(List.of(id)));
    inject(session, "state", state);
    inject(session, "world", world.world);
    inject(session, "arena", arena);
    inject(session, "deadline", 1000L);
    int[] heldSlot = {0};
    var inventory =
        (org.bukkit.inventory.PlayerInventory)
            java.lang.reflect.Proxy.newProxyInstance(
                GarticPhoneCheck.class.getClassLoader(),
                new Class[] {org.bukkit.inventory.PlayerInventory.class},
                (o, m, a) -> {
                  if (m.getName().equals("getHeldItemSlot")) return heldSlot[0];
                  throw new AssertionError("Unexpected inventory operation: " + m.getName());
                });
    var player =
        (org.bukkit.entity.Player)
            java.lang.reflect.Proxy.newProxyInstance(
                GarticPhoneCheck.class.getClassLoader(),
                new Class[] {org.bukkit.entity.Player.class},
                (o, m, a) ->
                    switch (m.getName()) {
                      case "getUniqueId" -> id;
                      case "isDead" -> false;
                      case "getInventory" -> inventory;
                      case "getWorld" -> world.world;
                      case "closeInventory", "sendMessage" -> null;
                      default ->
                          throw new AssertionError("Unexpected player operation: " + m.getName());
                    });
    var floor = world.block(0, 80, 0);
    var block = world.block(0, 81, 0);
    var click =
        new org.bukkit.event.player.PlayerInteractEvent(
            player,
            org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,
            null,
            floor,
            org.bukkit.block.BlockFace.UP,
            org.bukkit.inventory.EquipmentSlot.HAND);
    session.onPlayerInteract(click);
    check(
        click.useInteractedBlock() != org.bukkit.event.Event.Result.DENY
            && click.useItemInHand() != org.bukkit.event.Event.Result.DENY,
        "Right clicks must reach native block placement");
    block.setType(org.bukkit.Material.RED_CONCRETE);
    var place =
        new org.bukkit.event.block.BlockPlaceEvent(
            block, null, floor, null, player, true, org.bukkit.inventory.EquipmentSlot.HAND);
    session.onBlockPlace(place);
    check(!place.isCancelled(), "Accept palette blocks inside the player's plot");
    block.setType(org.bukkit.Material.TNT);
    session.onBlockPlace(place);
    check(place.isCancelled(), "Reject dangerous blocks even if injected into inventory");
    block.setType(org.bukkit.Material.BLUE_CONCRETE);
    var broken = new org.bukkit.event.block.BlockBreakEvent(block, player);
    session.onBlockBreak(broken);
    check(
        broken.isCancelled() && block.getType() == org.bukkit.Material.AIR,
        "Erase build blocks without vanilla drops");
    block.setType(org.bukkit.Material.GOLD_BLOCK);
    var erase =
        new org.bukkit.event.player.PlayerInteractEvent(
            player,
            org.bukkit.event.block.Action.LEFT_CLICK_BLOCK,
            null,
            block,
            org.bukkit.block.BlockFace.UP,
            org.bukkit.inventory.EquipmentSlot.HAND);
    session.onPlayerInteract(erase);
    check(
        erase.isCancelled() && block.getType() == org.bukkit.Material.AIR,
        "Instantly erase even hard blocks while flying, without waiting for vanilla mining");
    var move =
        new org.bukkit.event.player.PlayerMoveEvent(
            player, arena.spawn(id), new org.bukkit.Location(world.world, 40, 82, 0));
    session.handleMove(move);
    check(arena.inside(id, move.getTo()), "Confine builders to their private assignment");
    heldSlot[0] = 8;
    click =
        new org.bukkit.event.player.PlayerInteractEvent(
            player,
            org.bukkit.event.block.Action.RIGHT_CLICK_AIR,
            null,
            null,
            org.bukkit.block.BlockFace.SELF,
            org.bukkit.inventory.EquipmentSlot.HAND);
    session.onPlayerInteract(click);
    check(state.ready(id), "Emerald marks a build ready");
    block.setType(org.bukkit.Material.RED_CONCRETE);
    session.onBlockPlace(place);
    check(place.isCancelled(), "Ready builders cannot change their submission");
    session.onBlockBreak(broken);
    check(block.getType() == org.bukkit.Material.RED_CONCRETE, "Ready builds cannot be erased");
    check(session.roundResult().awards().isEmpty(), "Cooperative game awards no survival points");
    departedChat(session, player, id);
  }

  private static void departedChat(
      GarticPhoneSession session, org.bukkit.entity.Player player, UUID id) throws Exception {
    inject(session, "palettes", new java.util.HashMap<>());
    inject(session, "chatPlayers", Set.of(id));
    var chatClass = Class.forName(GarticPhoneSession.class.getName() + "$ChatTurn");
    var constructor = chatClass.getDeclaredConstructors()[0];
    constructor.setAccessible(true);
    inject(session, "chatTurn", constructor.newInstance(1, GarticPhoneState.Phase.BUILD));
    var serverField = org.bukkit.Bukkit.class.getDeclaredField("server");
    serverField.setAccessible(true);
    Object previousServer = serverField.get(null);
    var scheduler =
        java.lang.reflect.Proxy.newProxyInstance(
            GarticPhoneCheck.class.getClassLoader(),
            new Class[] {org.bukkit.scheduler.BukkitScheduler.class},
            (o, m, a) -> null);
    serverField.set(
        null,
        java.lang.reflect.Proxy.newProxyInstance(
            GarticPhoneCheck.class.getClassLoader(),
            new Class[] {org.bukkit.Server.class},
            (o, m, a) -> {
              if (m.getName().equals("getScheduler")) return scheduler;
              throw new AssertionError(m.getName());
            }));
    try {
      session.playerOut(player);
      var message = net.kyori.adventure.text.Component.text("Hello lobby");
      var chat =
          new io.papermc.paper.event.player.AsyncChatEvent(
              true,
              player,
              new HashSet<>(),
              io.papermc.paper.chat.ChatRenderer.defaultRenderer(),
              message,
              message,
              null);
      session.onPrivateChat(chat);
      check(!chat.isCancelled(), "Departed players keep public chat, including in another game");
    } finally {
      serverField.set(null, previousServer);
    }
  }

  private static void inject(Object object, String name, Object value) throws Exception {
    for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
      try {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
        return;
      } catch (NoSuchFieldException ignored) {
      }
    }
    throw new NoSuchFieldException(name);
  }

  private static void arenas() {
    var world = new PartyArenaCheck.WorldMap();
    UUID a = new UUID(0, 1), b = new UUID(0, 2);
    var arena = new GarticPhoneArena(world.world, List.of(a, b));
    arena.build();
    var first = arena.spawn(a);
    var second = arena.spawn(b);
    check(first.distance(second) > 20, "Private rooms are separated");
    check(
        world.block(first.getBlockX(), 80, first.getBlockZ()).getType()
            == org.bukkit.Material.SMOOTH_QUARTZ,
        "Spawn has a safe floor outside the build area");
    check(!arena.inside(a, second), "Players cannot enter a neighbor's room");
    check(!arena.editable(a, world.block(0, 80, 0)), "Protect the room floor");
    check(!arena.editable(a, world.block(0, 89, 0)), "Protect the room ceiling");
    check(arena.editable(a, world.block(0, 81, 0)), "Allow the first build cell");
    check(arena.editable(a, world.block(10, 88, 10)), "Allow the far top build cell");
    check(!arena.editable(a, world.block(11, 81, 0)), "Reject outside-volume placement");
    world.block(0, 81, 0).setType(org.bukkit.Material.RED_CONCRETE);
    world.block(10, 88, 10).setType(org.bukkit.Material.GLASS);
    var snapshot = arena.capture(a);
    check(snapshot.size() == 2, "Snapshot preserves unfinished builds");
    arena.show(b, snapshot);
    check(arena.capture(b).equals(snapshot), "Rotate complete builds into another private room");
    arena.show(a, java.util.Map.of());
    check(
        arena.capture(a).isEmpty() && arena.capture(b).equals(snapshot),
        "Clear without losing history");
    check(
        world.block(-3, 84, 0).getType() == org.bukkit.Material.WHITE_CONCRETE,
        "Opaque walls hide other assignments");
  }

  private static void chains() {
    for (int count = 1; count <= 12; count++) {
      List<UUID> roster = new ArrayList<>();
      for (int i = 0; i < count; i++) roster.add(new UUID(0, i + 1));
      var state = new GarticPhoneState<String>(roster);
      check(state.phase() == GarticPhoneState.Phase.PROMPT, "Start with private prompts");
      check(!state.submitText(new UUID(9, 9), 0, "intruder"), "Reject nonparticipants");
      check(!state.submitText(roster.getFirst(), 0, "  "), "Reject blank entries");
      int turns = Math.max(3, count);
      List<Set<UUID>> authors = new ArrayList<>();
      for (int i = 0; i < count; i++) authors.add(new HashSet<>());
      for (int turn = 0; turn < turns; turn++) {
        check(state.turn() == turn, "Advance exactly one turn");
        Set<Integer> assigned = new HashSet<>();
        for (int p = 0; p < count; p++) {
          UUID id = roster.get(p);
          int chain = Math.floorMod(p - turn, count);
          check(state.chainIndex(id) == chain, "Rotate each chain to the next player");
          assigned.add(state.chainIndex(id));
          if (turn < count) check(authors.get(chain).add(id), "No repeated author in a circuit");
          if (turn > 0) {
            var previous = state.previous(id);
            check(
                previous.author().equals(roster.get((chain + turn - 1) % count)),
                "See only the previous author's contribution");
            check(
                (turn % 2 == 0) == (previous.build() != null),
                "Guesses see builds; builders see text");
          }
          if (turn % 2 == 1) {
            check(!state.submitText(id, turn, "wrong phase"), "No text during building");
            check(state.readyBuild(id, turn), "Builder can lock their work");
          } else {
            check(!state.readyBuild(id, turn), "No build-ready during text turns");
            check(state.submitText(id, turn, "  turn " + turn + "  "), "Accept text");
            check(!state.submitText(id, turn, "overwrite"), "Submitted entries are locked");
          }
          check(!state.submitText(id, turn - 1, "late"), "Reject stale chat callbacks");
        }
        check(assigned.size() == count, "Each chain has exactly one author per turn");
        check(state.allReady(roster), "All submitted authors finish early");
        state.endTurn(id -> "build by " + id);
      }
      check(state.phase() == GarticPhoneState.Phase.REVEAL, "Reveal after the full circuit");
      for (int chain = 0; chain < count; chain++) {
        check(state.chain(chain).size() == turns, "Preserve the whole chain");
        check(state.chain(chain).getFirst().text().equals("turn 0"), "Normalize text");
      }
    }
    UUID a = new UUID(0, 1), b = new UUID(0, 2), c = new UUID(0, 3);
    var missing = new GarticPhoneState<String>(List.of(a, b, c));
    missing.submitText(a, 0, "a".repeat(150));
    check(missing.allReady(List.of(a)), "Departures do not block early completion");
    check(!missing.allReady(List.of()), "Empty audience does not auto-advance");
    missing.endTurn(id -> "");
    check(missing.chain(0).getFirst().text().length() == 120, "Bound prompt length");
    check(missing.chain(1).getFirst().text().isEmpty(), "Missing text gets a fallback marker");
    missing.endTurn(id -> "snapshot");
    check(missing.chain(0).get(1).build().equals("snapshot"), "Timeout captures unfinished builds");
    missing.endTurn(id -> "unused");
    check(
        missing.phase() == GarticPhoneState.Phase.REVEAL,
        "Missing players cannot stall the circuit");
  }
}
