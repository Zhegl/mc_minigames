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
    installStructures();
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
    var state = new GarticPhoneState<org.bukkit.structure.Structure>(List.of(id));
    state.submitText(id, 0, "a cat");
    state.endTurn(ignored -> null);
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
    inject(session, "chatPlayers", Set.of(id));
    var lockedBuilds = new java.util.HashMap<UUID, org.bukkit.structure.Structure>();
    inject(session, "submittedBuilds", lockedBuilds);
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
                      case "isSneaking" -> true;
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
    var offhand =
        new org.bukkit.event.player.PlayerInteractEvent(
            player,
            org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,
            null,
            floor,
            org.bukkit.block.BlockFace.UP,
            org.bukkit.inventory.EquipmentSlot.OFF_HAND);
    session.onPlayerInteract(offhand);
    check(
        offhand.useItemInHand() != org.bukkit.event.Event.Result.DENY,
        "Offhand blocks can also be placed");
    block.setType(org.bukkit.Material.RED_CONCRETE);
    var place =
        new org.bukkit.event.block.BlockPlaceEvent(
            block, null, floor, null, player, true, org.bukkit.inventory.EquipmentSlot.HAND);
    session.onBlockPlace(place);
    check(!place.isCancelled(), "Accept palette blocks inside the player's plot");
    block.setType(org.bukkit.Material.TNT);
    session.onBlockPlace(place);
    check(!place.isCancelled(), "Any Creative block can be placed inside the plot, including TNT");
    for (var material :
        List.of(
            org.bukkit.Material.OAK_STAIRS,
            org.bukkit.Material.CHEST,
            org.bukkit.Material.REDSTONE_WIRE,
            org.bukkit.Material.SAND,
            org.bukkit.Material.OAK_DOOR)) {
      block.setType(material);
      session.onBlockPlace(place);
      check(!place.isCancelled(), "Creative block is not excluded by a palette: " + material);
    }
    block.setType(org.bukkit.Material.BLUE_CONCRETE);
    var broken = new org.bukkit.event.block.BlockBreakEvent(block, player);
    session.onBlockBreak(broken);
    check(
        !broken.isCancelled() && !broken.isDropItems(),
        "Allow native Creative breaking without vanilla drops");
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
    check(!erase.isCancelled(), "Let Creative mode perform instant native block breaking");
    var move =
        new org.bukkit.event.player.PlayerMoveEvent(
            player, arena.spawn(id), new org.bukkit.Location(world.world, 40, 82, 0));
    session.handleMove(move);
    check(arena.inside(id, move.getTo()), "Confine builders to their private assignment");
    var insideFlow =
        new org.bukkit.event.block.BlockFromToEvent(
            world.block(5, 81, 5), org.bukkit.block.BlockFace.EAST);
    session.onFluidFlow(insideFlow);
    check(!insideFlow.isCancelled(), "Water and lava can flow inside the build volume");
    var outsideFlow =
        new org.bukkit.event.block.BlockFromToEvent(
            world.block(10, 81, 5), org.bukkit.block.BlockFace.EAST);
    session.onFluidFlow(outsideFlow);
    check(outsideFlow.isCancelled(), "Fluids cannot leave the build volume");
    var insidePiston =
        new org.bukkit.event.block.BlockPistonExtendEvent(
            world.block(5, 81, 5), List.of(world.block(6, 81, 5)), org.bukkit.block.BlockFace.EAST);
    session.onPistonExtend(insidePiston);
    check(!insidePiston.isCancelled(), "Pistons work inside the build volume");
    var outsidePiston =
        new org.bukkit.event.block.BlockPistonExtendEvent(
            world.block(9, 81, 5),
            List.of(world.block(10, 81, 5)),
            org.bukkit.block.BlockFace.EAST);
    session.onPistonExtend(outsidePiston);
    check(outsidePiston.isCancelled(), "Pistons cannot push blocks outside the build volume");
    var dispenserData =
        (org.bukkit.block.data.BlockData)
            java.lang.reflect.Proxy.newProxyInstance(
                GarticPhoneCheck.class.getClassLoader(),
                new Class[] {org.bukkit.block.data.Directional.class},
                (o, m, args) ->
                    switch (m.getName()) {
                      case "getMaterial" -> org.bukkit.Material.DISPENSER;
                      case "getFacing" -> org.bukkit.block.BlockFace.EAST;
                      case "getAsString" -> "minecraft:dispenser[facing=east,triggered=false]";
                      default -> throw new AssertionError(m.getName());
                    });
    var insideDispenser = world.block(5, 81, 5);
    insideDispenser.setBlockData(dispenserData, false);
    var dispense =
        new org.bukkit.event.block.BlockDispenseEvent(
            insideDispenser,
            new MenuBlock(org.bukkit.Material.WATER_BUCKET),
            new org.bukkit.util.Vector());
    session.onDispense(dispense);
    check(!dispense.isCancelled(), "Dispensers can place fluids and blocks inside the plot");
    var edgeDispenser = world.block(10, 81, 5);
    edgeDispenser.setBlockData(dispenserData, false);
    var outsideDispense =
        new org.bukkit.event.block.BlockDispenseEvent(
            edgeDispenser,
            new MenuBlock(org.bukkit.Material.WATER_BUCKET),
            new org.bukkit.util.Vector());
    session.onDispense(outsideDispense);
    check(outsideDispense.isCancelled(), "Dispenser bucket sources cannot appear outside the plot");
    var view =
        (org.bukkit.inventory.InventoryView)
            java.lang.reflect.Proxy.newProxyInstance(
                GarticPhoneCheck.class.getClassLoader(),
                new Class[] {org.bukkit.inventory.InventoryView.class},
                (o, m, a) ->
                    switch (m.getName()) {
                      case "getPlayer" -> player;
                      case "getTopInventory", "getBottomInventory", "getInventory" -> inventory;
                      case "convertSlot" -> (int) a[0];
                      case "getType" -> org.bukkit.event.inventory.InventoryType.CREATIVE;
                      default -> throw new AssertionError("Inventory view: " + m.getName());
                    });
    var creativePick =
        new org.bukkit.event.inventory.InventoryCreativeEvent(
            view,
            org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,
            8,
            new MenuBlock(org.bukkit.Material.TNT));
    session.onInventoryClick(creativePick);
    check(!creativePick.isCancelled(), "Creative menu can put any block in any hotbar slot");
    var drag =
        new org.bukkit.event.inventory.InventoryDragEvent(
            view,
            null,
            null,
            false,
            java.util.Map.of(8, new MenuBlock(org.bukkit.Material.OAK_STAIRS)));
    session.onInventoryDrag(drag);
    check(!drag.isCancelled(), "Allow native Creative inventory dragging");
    var ready = new org.bukkit.event.player.PlayerSwapHandItemsEvent(player, null, null);
    session.onSwapHandItems(ready);
    check(
        ready.isCancelled() && state.ready(id),
        "Shift+F marks a build ready without reserving a hotbar slot");
    session.onFluidFlow(insideFlow);
    check(insideFlow.isCancelled(), "Ready builds stop further fluid propagation");
    session.onInventoryClick(creativePick);
    check(creativePick.isCancelled(), "Ready players cannot obtain more Creative items");
    session.onInventoryDrag(drag);
    check(drag.isCancelled(), "Ready players cannot rearrange inventory");
    block.setType(org.bukkit.Material.RED_CONCRETE);
    session.onBlockPlace(place);
    check(place.isCancelled(), "Ready builders cannot change their submission");
    session.onBlockBreak(broken);
    check(block.getType() == org.bukkit.Material.RED_CONCRETE, "Ready builds cannot be erased");
    check(session.roundResult().awards().isEmpty(), "Cooperative game awards no survival points");
    var lockedSnapshot = lockedBuilds.get(id);
    check(lockedSnapshot != null, "Capture the full submission immediately on Shift+F");
    session.playerOut(player);
    check(
        lockedBuilds.get(id) == lockedSnapshot,
        "Leaving preserves an already submitted build instead of recapturing live terrain");
    departedChat(session, player, id);
  }

  private static void departedChat(
      GarticPhoneSession session, org.bukkit.entity.Player player, UUID id) throws Exception {

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

    arena.show(b, snapshot);
    check(
        world.block(32, 81, 0).getType() == org.bukkit.Material.RED_CONCRETE
            && world.block(42, 88, 10).getType() == org.bukkit.Material.GLASS,
        "Rotate complete builds into another private room");
    arena.show(a, null);
    check(
        world.block(0, 81, 0).getType() == org.bukkit.Material.AIR
            && world.block(32, 81, 0).getType() == org.bukkit.Material.RED_CONCRETE,
        "Clear without losing history");
    var stairs =
        PartyArenaCheck.blockData(
            "minecraft:oak_stairs[facing=east,half=top,shape=straight,waterlogged=false]");
    world.block(2, 81, 2).setBlockData(stairs, false);
    var detailed = arena.capture(a);
    arena.show(b, detailed);
    check(
        world.block(34, 81, 2).getBlockData().getAsString().equals(stairs.getAsString()),
        "Snapshots preserve orientation and slab/stair properties");
    check(
        world.block(-3, 84, 0).getType() == org.bukkit.Material.WHITE_CONCRETE,
        "Opaque walls hide other assignments");
  }

  private static final class MenuBlock extends org.bukkit.inventory.ItemStack {
    private final org.bukkit.Material type;

    MenuBlock(org.bukkit.Material type) {
      this.type = type;
    }

    @Override
    public org.bukkit.Material getType() {
      return type;
    }

    @Override
    public int getAmount() {
      return 64;
    }

    @Override
    public org.bukkit.inventory.ItemStack clone() {
      return new MenuBlock(type);
    }
  }

  private static void installStructures() throws Exception {
    PartyArenaCheck.installDataFactory();
    var serverField = org.bukkit.Bukkit.class.getDeclaredField("server");
    serverField.setAccessible(true);
    Object original = serverField.get(null);
    var manager =
        java.lang.reflect.Proxy.newProxyInstance(
            GarticPhoneCheck.class.getClassLoader(),
            new Class[] {org.bukkit.structure.StructureManager.class},
            (o, m, a) -> {
              if (!m.getName().equals("createStructure")) throw new AssertionError(m.getName());
              var saved = new java.util.HashMap<String, org.bukkit.block.data.BlockData>();
              return java.lang.reflect.Proxy.newProxyInstance(
                  GarticPhoneCheck.class.getClassLoader(),
                  new Class[] {org.bukkit.structure.Structure.class},
                  (structure, method, args) -> {
                    if (method.getName().equals("fill")) {
                      var origin = (org.bukkit.Location) args[0];
                      var size = (org.bukkit.util.BlockVector) args[1];
                      check(
                          size.getBlockX() == 11
                              && size.getBlockY() == 8
                              && size.getBlockZ() == 11
                              && Boolean.FALSE.equals(args[2]),
                          "Capture exactly the plot, without entities");
                      for (int x = 0; x < 11; x++)
                        for (int y = 0; y < 8; y++)
                          for (int z = 0; z < 11; z++)
                            saved.put(
                                x + ":" + y + ":" + z,
                                origin
                                    .getWorld()
                                    .getBlockAt(
                                        origin.getBlockX() + x,
                                        origin.getBlockY() + y,
                                        origin.getBlockZ() + z)
                                    .getBlockData());
                      return null;
                    }
                    if (method.getName().equals("place")) {
                      var origin = (org.bukkit.Location) args[0];
                      check(
                          Boolean.FALSE.equals(args[1])
                              && args[2] == org.bukkit.block.structure.StructureRotation.NONE
                              && args[3] == org.bukkit.block.structure.Mirror.NONE
                              && args[5].equals(1f),
                          "Restore full snapshots without rotation, omissions or entities");
                      saved.forEach((cell, data) -> {
                        String[] c = cell.split(":");
                        origin
                            .getWorld()
                            .getBlockAt(
                                origin.getBlockX() + Integer.parseInt(c[0]),
                                origin.getBlockY() + Integer.parseInt(c[1]),
                                origin.getBlockZ() + Integer.parseInt(c[2]))
                            .setBlockData(data, false);
                      });
                      return null;
                    }
                    throw new AssertionError(method.getName());
                  });
            });
    serverField.set(
        null,
        java.lang.reflect.Proxy.newProxyInstance(
            GarticPhoneCheck.class.getClassLoader(),
            new Class[] {org.bukkit.Server.class},
            (o, m, a) ->
                m.getName().equals("getStructureManager") ? manager : m.invoke(original, a)));
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
