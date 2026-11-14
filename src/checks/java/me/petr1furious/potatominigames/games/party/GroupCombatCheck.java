package me.petr1furious.potatominigames.games.party;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Group-mode integration, real generated spawns, uniform kits and one-life elimination. */
public final class GroupCombatCheck {
    public static void main(String[] args) {
        integration();
        selection();
        spawns(false);
        spawns(true);
        survival();
        kits();
        System.out.println("Party group combat: PvP/sumo menu and queues, safe circular spawns for 1–16 players, all random classes, one life, first-death continuation and simultaneous/withdrawal outcomes passed");
    }
    private static void integration() {
        PartyGame game = new PartyGame((PartyConfig) null);
        check(
                game.modeGroups().size() == 1
                        && game.modeGroups()
                                .getFirst()
                                .modes()
                                .equals(List.of("pvp", "pvp_ffa", "pvp_select")),
                "the Party menu groups exactly three PvP variants behind one entry");
        check(
                game.createSession(null, "pvp_select") instanceof DuelSession
                        && game.minPlayers("pvp_select") == 2
                        && game.maxPlayers("pvp_select") == 16,
                "kit-selection PvP is a playable standalone group-combat mode");
        check(game.modes().containsAll(List.of("pvp", "sumo", "pvp_ffa", "sumo_ffa", "arms_race", "miners")), "Group variants coexist with the original tournament modes");
        for (String mode : List.of("pvp_ffa", "sumo_ffa")) {
            check(game.createSession(null, mode) instanceof DuelSession, "Group modes share the tested class/combat engine");
            check(game.minPlayers(mode) == 2 && game.maxPlayers(mode) == 16, "Public group battles accept two to sixteen players");
            check(me.petr1furious.potatominigames.i18n.I18n.plain(game.modeTitle(mode), java.util.Locale.forLanguageTag("ru")).contains("Массовое"), "Russian group-mode menu is localized");
            check(me.petr1furious.potatominigames.i18n.I18n.plain(game.modeTitle(mode), java.util.Locale.ENGLISH).contains("Group"), "English group-mode menu is localized");
        }
        check(game.maxPlayers("pvp") == 12 && game.maxPlayers("sumo") == 12, "Tournament limits remain intact");
        var queues = YamlConfiguration.loadConfiguration(new InputStreamReader(GroupCombatCheck.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
        for (String mode : List.of("pvp_ffa", "sumo_ffa", "pvp_select")) {
            String path = "queues.party." + mode;
            check(queues.getBoolean(path + ".enabled") && queues.getInt(path + ".minPlayers") == 2
                    && queues.getInt(path + ".maxPlayers") == 16, "Group queues ship enabled with the advertised limits");
        }
        var timings = YamlConfiguration.loadConfiguration(new InputStreamReader(GroupCombatCheck.class.getResourceAsStream("/games/party.yml"), StandardCharsets.UTF_8));
        check(timings.getInt("pvpFfa.overtimeSeconds") == 180 && timings.getInt("sumoFfa.overtimeSeconds") == 45, "Overtime has separate group-mode settings");
    }

    private static void selection() {
        List<UUID> ids = ids(3);
        PvpKitChoices picks = new PvpKitChoices(ids);
        check(
                !picks.ready() && picks.picked() == 0 && picks.size() == 3,
                "no fighter starts with an implicitly selected random kit");
        for (PvpClass kit : PvpClass.values())
            check(
                    picks.choose(ids.getFirst(), kit) && picks.selected(ids.getFirst()) == kit,
                    "every existing class can be selected or changed during the choice phase");
        check(
                !picks.choose(new UUID(8, 8), PvpClass.GUARDIAN)
                        && !picks.choose(ids.get(1), null)
                        && picks.picked() == 1,
                "spectators and empty clicks cannot change class choices");
        picks.choose(ids.getFirst(), PvpClass.SHADOW);
        picks.choose(ids.get(1), PvpClass.SHADOW);
        check(
                !picks.ready(),
                "duplicate class choices are allowed without skipping an undecided fighter");
        picks.withdraw(ids.get(2));
        check(
                picks.ready() && picks.size() == 2 && picks.picked() == 2,
                "a withdrawn fighter never holds the selection phase open");
        Map<UUID, PvpClass> locked = picks.finish(new Random(42));
        check(
                locked.equals(Map.of(ids.getFirst(), PvpClass.SHADOW, ids.get(1), PvpClass.SHADOW))
                        && !picks.choose(ids.getFirst(), PvpClass.GUARDIAN),
                "the countdown locks the chosen classes without rerolling them");
        PvpKitChoices timeout = new PvpKitChoices(ids);
        timeout.choose(ids.getFirst(), PvpClass.LANCER);
        Map<UUID, PvpClass> fallback = timeout.finish(new Random(42));
        check(
                fallback.size() == 3
                        && fallback.get(ids.getFirst()) == PvpClass.LANCER
                        && fallback.values().stream().allMatch(java.util.Objects::nonNull)
                        && timeout.finish(new Random(99)).equals(fallback),
                "timeout fills only missing choices, once, without overwriting a selected kit");
        PvpKitChoices empty = new PvpKitChoices(ids.subList(0, 1));
        empty.withdraw(ids.getFirst());
        check(
                !empty.ready() && empty.finish(new Random()).isEmpty(),
                "an empty roster cannot start a fight or invent a random fighter");
    }

    private static void spawns(boolean sumo) {
        if (sumo) spawns(true, PvpArena.Layout.COURTYARD);
        else for (var layout : PvpArena.Layout.values()) spawns(false, layout);
    }
    private static void spawns(boolean sumo, PvpArena.Layout layout) {
        FakeWorld world = new FakeWorld();
        DuelArena arena = sumo ? new SumoArena(world.world) : new PvpArena(world.world, layout);
        arena.build();
        for (int count = 1; count <= 16; count++) {
            List<Location> positions = new ArrayList<>();
            Set<String> unique = new HashSet<>();
            for (int index = 0; index < count; index++) {
                Location at = arena.spawn(index, count);
                check(arena.contains(at), "Every group spawn lies inside the arena");
                check(world.at(at.getBlockX(), at.getBlockY() - 1, at.getBlockZ()) != Material.AIR, "Players spawn on a solid floor");
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                        int x = (int) Math.floor(at.getX() + dx * .3), z = (int) Math.floor(at.getZ() + dz * .3);
                        check(world.at(x, at.getBlockY(), z) == Material.AIR && world.at(x, at.getBlockY() + 1, z) == Material.AIR,
                                "The full player hitbox, not just its centre, is clear of cover");
                    }
                check(unique.add(at.getX() + ":" + at.getZ()), "No two group participants share a spawn");
                check(at.getDirection().dot(arena.center().toVector().subtract(at.toVector()).normalize()) > .999,
                        "Participants face the centre of the battle");
                for (Location other : positions) check(at.distanceSquared(other) > 4, "Even sixteen sumo players start over two blocks apart");
                positions.add(at);
                if (sumo) check(SumoArena.onPlatform(at.getBlockX(), at.getBlockZ(), SumoArena.RADIUS - 2), "Sumo spawns leave room to recover before the rim");
            }
        }
        check(arena.spawn(false).getY() == 81 && arena.spawn(true).getY() == 81, "Original duel spawn API remains available");
        expectFailure(() -> arena.spawn(0, 17));
        expectFailure(() -> arena.spawn(-1, 3));
        expectFailure(() -> arena.spawn(3, 3));
    }
    private static void survival() {
        for (int count = 2; count <= 16; count++) {
            List<UUID> roster = ids(count);
            SurvivalStandings match = new SurvivalStandings(roster);
            for (int i = 0; i < count - 1; i++) {
                long tick = 100 + i * 10;
                check(match.eliminate(roster.get(i), tick), "One life is removed by death, ring-out or withdrawal");
                check(!match.eliminate(roster.get(i), tick + 1), "Respawn/reconnect cannot eliminate twice or restore a life");
                check(!match.decided(false, tick), "A result is not committed during a simultaneous-death window");
                check(match.decided(false, tick + 2) == (i == count - 2), "The first death never stops battles with multiple survivors");
            }
            check(match.survivors().equals(List.of(roster.getLast())), "Exactly the last survivor wins");
            check(match.ranking(roster, 1000).getFirst().equals(roster.getLast()), "The winner leads the survival standings");
        }
        List<UUID> ids = ids(3);
        SurvivalStandings trade = new SurvivalStandings(ids);
        for (UUID id : ids) trade.eliminate(id, 100);
        check(trade.survivors().isEmpty() && trade.decided(false, 102), "Simultaneous final deaths produce a draw without a replay or invented winner");
        SurvivalStandings closeDeaths = new SurvivalStandings(ids);
        closeDeaths.eliminate(ids.get(0), 100);
        closeDeaths.eliminate(ids.get(1), 101);
        check(!closeDeaths.decided(false, 102) && closeDeaths.decided(false, 103), "A second close death extends the settling window");
        SurvivalStandings test = new SurvivalStandings(ids.subList(0, 1));
        check(!test.decided(true, 1000), "Solo training does not instantly win itself");
        test.eliminate(ids.getFirst(), 1001);
        check(test.decided(true, 1003), "Solo training ends on its single elimination instead of starting a BO3");
    }
    private static void kits() {
        PvpClass[] all = PvpClass.values();
        for (int i = 0; i < all.length; i++) {
            int choice = i;
            check(PvpClass.random(new Random(0) {
                @Override public int nextInt(int bound) { check(bound == all.length, "FFA draws from the whole class pool"); return choice; }
            }) == all[i], "Every class is eligible in a group battle");
        }
        Random same = new Random(0) { @Override public int nextInt(int bound) { return bound - 1; } };
        check(PvpClass.random(same) == PvpClass.random(same), "Group classes are independent draws and may repeat");
    }
    private static List<UUID> ids(int count) {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) ids.add(new UUID(0, i + 1));
        return ids;
    }
    private static final class FakeWorld {
        final Map<PvpArena.Position, Material> blocks = new HashMap<>();
        final World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class}, (p, m, a) -> {
            return switch (m.getName()) {
                case "getBlockAt" -> block((int) a[0], (int) a[1], (int) a[2]);
                case "setSpawnLocation" -> true;
                case "getEntities" -> List.of();
                case "equals" -> p == a[0];
                case "hashCode" -> System.identityHashCode(p);
                default -> throw new AssertionError("Unexpected world call: " + m.getName());
            };
        });
        Material at(int x, int y, int z) { return blocks.getOrDefault(new PvpArena.Position(x, y, z), Material.AIR); }
        Block block(int x, int y, int z) {
            var pos = new PvpArena.Position(x, y, z);
            return (Block) Proxy.newProxyInstance(Block.class.getClassLoader(), new Class<?>[] {Block.class}, (p, m, a) -> {
                return switch (m.getName()) {
                    case "getWorld" -> world;
                    case "getX" -> x;
                    case "getY" -> y;
                    case "getZ" -> z;
                    case "getType" -> blocks.getOrDefault(pos, Material.AIR);
                    case "setType" -> { blocks.put(pos, (Material) a[0]); yield null; }
                    default -> throw new AssertionError("Unexpected block call: " + m.getName());
                };
            });
        }
    }
    private static void expectFailure(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid group spawn accepted");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
