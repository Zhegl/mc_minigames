package me.petr1furious.potatominigames.games.party;

import static me.petr1furious.potatominigames.games.party.PartyScoringCheck.check;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;

import me.petr1furious.potatominigames.core.game.EndReason;
import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.core.player.Participant;
import me.petr1furious.potatominigames.core.player.ParticipantRegistry;
import me.petr1furious.potatominigames.core.session.ActiveSession;
import me.petr1furious.potatominigames.core.text.Messages;

import net.kyori.adventure.audience.Audience;

import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.*;

/** Drives the actual Mob Count phase transitions and movement handlers. */
final class MobCountSessionCheck {
    static void run() throws Exception {
        Field server = Bukkit.class.getDeclaredField("server");
        server.setAccessible(true);
        Object original = server.get(null);
        try {
            scenario(false, false);
            scenario(true, false);
            scenario(true, true);
        } finally {
            server.set(null, original);
        }
        System.out.println(
                "Mob Count: three-second movement/jump lock, full answer timer, physical floor"
                        + " drop, rescue and freeze cleanup passed");
    }

    private static void scenario(boolean embedded, boolean abort) throws Exception {
        var blocks = new PartyArenaCheck.WorldMap();
        World world =
                (World)
                        Proxy.newProxyInstance(
                                World.class.getClassLoader(),
                                new Class<?>[] {World.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "getBlockAt" ->
                                                    blocks.block(
                                                            (int) args[0],
                                                            (int) args[1],
                                                            (int) args[2]);
                                            case "setSpawnLocation" -> true;
                                            case "spawn" ->
                                                    Proxy.newProxyInstance(
                                                            TextDisplay.class.getClassLoader(),
                                                            new Class<?>[] {TextDisplay.class},
                                                            (p, m, a) -> null);
                                            case "equals" -> proxy == args[0];
                                            case "hashCode" -> System.identityHashCode(proxy);
                                            default ->
                                                    throw new AssertionError(
                                                            "World: " + method.getName());
                                        });
        MobCountArena arena = new MobCountArena(world);
        MobCountRound round = MobCountRound.generate(1, 36, new Random(3));
        arena.prepare(round);
        FakePlayer first = new FakePlayer(new UUID(0, 501), new Location(world, -8.5, 81, -8.5));
        FakePlayer second = new FakePlayer(new UUID(0, 502), arena.spawn(1));
        Map<UUID, Player> players = Map.of(first.id, first.player, second.id, second.player);
        Field server = Bukkit.class.getDeclaredField("server");
        server.setAccessible(true);
        server.set(
                null,
                Proxy.newProxyInstance(
                        Server.class.getClassLoader(),
                        new Class<?>[] {Server.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("getPlayer")) return players.get(args[0]);
                            throw new AssertionError("Server: " + method.getName());
                        }));
        ActiveSession active = bare(ActiveSession.class);
        set(active, "participants", new ParticipantRegistry());
        set(active, "spectators", Set.of());
        set(active, "test", true);
        SessionServices services =
                new SessionServices(
                        null,
                        active,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new Messages(Audience::empty, null),
                        null);
        PartyRoundContext context =
                new PartyRoundContext(
                        null,
                        List.of(new Participant(first.player), new Participant(second.player)),
                        embedded ? PartyRoundOptions.party() : PartyRoundOptions.standalone(),
                        (reason, result) -> {});
        MobCountSession session = bare(MobCountSession.class);
        set(session, "services", services);
        set(session, "context", context);
        set(session, "connected", new HashSet<>(players.keySet()));
        set(session, "names", Map.of(first.id, "First", second.id, "Second"));
        set(session, "standings", new SurvivalStandings(players.keySet()));
        set(session, "countdownFreeze", new CountdownFreeze());
        set(session, "playing", true);
        set(session, "world", world);
        set(session, "arena", arena);
        set(session, "round", round);
        set(session, "answerSeconds", 10);
        set(session, "rounds", 1);
        set(session, "phaseEnd", 100L);
        set(session, "phase", enumValue(session, "phase", "COUNT"));
        int[] removed = {0};
        Entity mob =
                (Entity)
                        Proxy.newProxyInstance(
                                Entity.class.getClassLoader(),
                                new Class<?>[] {Entity.class},
                                (proxy, method, args) -> {
                                    if (method.getName().equals("remove")) {
                                        removed[0]++;
                                        return null;
                                    }
                                    throw new AssertionError(method.getName());
                                });
        set(session, "mobs", new ArrayList<>(List.of(mob)));
        advance(session, 100);
        check(removed[0] == 1, "Mobs disappear before the pause");
        check(
                field(session, "phase").get(session).toString().equals("READY"),
                "Mob disappearance starts a separate three-second freeze");
        check(first.walk == 0 && first.fly == 0, "Freeze disables walking and flight");
        check(
                blocks.block(-9, 80, -11).getType() == Material.LIGHT_BLUE_STAINED_GLASS,
                "Floor stays intact during the freeze");
        Location anchor = first.location.clone();
        var move = new PlayerMoveEvent(first.player, anchor.clone(), anchor.clone().add(3, 1, 2));
        move.getTo().setYaw(90);
        session.onPlayerMove(move);
        check(
                move.getTo().getX() == anchor.getX()
                        && move.getTo().getY() == anchor.getY()
                        && move.getTo().getYaw() == 90,
                "Players stay on their safe anchor while looking around");
        var jump = new PlayerJumpEvent(first.player, anchor.clone(), anchor.clone().add(0, .42, 0));
        session.onPlayerJump(jump);
        check(jump.isCancelled(), "Jumping is locked during the pause");
        if (abort) {
            session.onParticipantQuit(first.player);
            check(
                    first.walk == .2f && first.fly == .1f,
                    "Leaving during the pause restores movement");
            session.onEnd(EndReason.ABORTED);
            check(
                    second.walk == .2f && second.fly == .1f,
                    "Aborting during the pause restores remaining players");
            return;
        }
        advance(session, 159);
        check(first.walk == 0, "Freeze lasts all sixty ticks");
        advance(session, 160);
        check(first.walk == .2f && first.fly == .1f, "Answer phase restores original speeds");
        jump = new PlayerJumpEvent(first.player, anchor.clone(), anchor.clone().add(0, .42, 0));
        session.onPlayerJump(jump);
        check(!jump.isCancelled(), "Jumping is enabled for platform choice");
        check(
                (long) field(session, "phaseEnd").get(session) == 360,
                "Freeze does not consume any answer time");
        check(
                blocks.block(-9, 80, -11).getType() == Material.AIR,
                "Glass disappears only when choices open");
        int correct = round.platforms().indexOf(round.answer());
        int wrong = 0;
        while (round.platforms().get(wrong) == round.answer()) wrong++;
        first.location = arena.spawn(wrong);
        second.location = arena.spawn(correct).add(0, 1, 0);
        advance(session, 359);
        check(
                blocks.block(MobCountArena.x(wrong), 80, MobCountArena.z(wrong)).getType()
                        != Material.AIR,
                "Wrong platforms remain for the entire answer window");
        advance(session, 360);
        check(
                session.standings.survivors().size() == 2,
                "Reveal never instantly eliminates wrong or airborne players");
        check(
                blocks.block(MobCountArena.x(wrong), 80, MobCountArena.z(wrong)).getType()
                        == Material.AIR,
                "Wrong answer removes the physical floor");
        check(
                blocks.block(MobCountArena.x(correct), 80, MobCountArena.z(correct)).getType()
                        == Material.SMOOTH_QUARTZ,
                "Correct answer retains its floor");
        check(session.outHeight() < MobCountArena.FLOOR, "Elimination plane lies below the floor");
        if (embedded) {
            first.location.setY(77);
            advance(session, 420);
            check(
                    !context.ended() && first.location.getY() == 77,
                    "A falling player is not teleported out of danger when the drop timer ends");
            first.location = arena.spawn(correct);
            second.location = arena.spawn(correct);
            advance(session, 421);
            check(
                    context.ended() && session.standings.survivors().size() == 2,
                    "Reaching surviving ground rescues the player instead of a delayed forced"
                            + " death");
        }
    }

    private static void advance(MobCountSession session, long tick) throws Exception {
        set(session, "tick", tick);
        session.playTick(tick);
    }

    private static <T> T bare(Class<T> type) throws Exception {
        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return type.cast(((sun.misc.Unsafe) f.get(null)).allocateInstance(type));
    }

    private static Field field(Object target, String name) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field f = type.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException missing) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        field(target, name).set(target, value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValue(Object target, String name, String value) throws Exception {
        return Enum.valueOf((Class) field(target, name).getType(), value);
    }

    private static final class FakePlayer {
        final UUID id;
        final Player player;
        Location location;
        float walk = .2f, fly = .1f;
        Vector velocity = new Vector();

        FakePlayer(UUID id, Location location) {
            this.id = id;
            this.location = location;
            player =
                    (Player)
                            Proxy.newProxyInstance(
                                    Player.class.getClassLoader(),
                                    new Class<?>[] {Player.class},
                                    (proxy, method, args) ->
                                            switch (method.getName()) {
                                                case "getUniqueId" -> id;
                                                case "getName" -> "Player" + id;
                                                case "getWorld" -> this.location.getWorld();
                                                case "getLocation" -> this.location.clone();
                                                case "isDead" -> false;
                                                case "isOnline" -> true;
                                                case "getWalkSpeed" -> walk;
                                                case "getFlySpeed" -> fly;
                                                case "getVelocity" -> velocity.clone();
                                                case "setWalkSpeed" -> {
                                                    walk = (float) args[0];
                                                    yield null;
                                                }
                                                case "setFlySpeed" -> {
                                                    fly = (float) args[0];
                                                    yield null;
                                                }
                                                case "setVelocity" -> {
                                                    velocity = ((Vector) args[0]).clone();
                                                    yield null;
                                                }
                                                case "teleport" -> {
                                                    this.location = ((Location) args[0]).clone();
                                                    yield true;
                                                }
                                                case "setFallDistance",
                                                        "playSound",
                                                        "showTitle",
                                                        "sendMessage" ->
                                                        null;
                                                default ->
                                                        throw new AssertionError(
                                                                "Player: " + method.getName());
                                            });
        }
    }
}
