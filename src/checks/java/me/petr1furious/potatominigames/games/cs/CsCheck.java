package me.petr1furious.potatominigames.games.cs;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.lang.reflect.*;
import java.util.*;

import javax.imageio.ImageIO;

/** Server-free behavioral checks: objective deadlines, economy, equipment and imported geometry. */
public final class CsCheck {
    private static final UUID T = new UUID(0, 1),
            T2 = new UUID(0, 2),
            CT = new UUID(0, 3),
            CT2 = new UUID(0, 4);
    private static final Map<UUID, Integer> TEAMS = Map.of(T, 0, T2, 0, CT, 1, CT2, 1);
    private static final Random RNG = new Random(87234);
    private static int checks;

    public static void main(String[] args) throws Exception {
        checks += CsDeathmatchCheck.run();
        checks += CsWingmanCheck.run();
        hudTranslations();
        killMessages();
        buying();
        cs2Economy();
        sizedEconomy();
        grenadeFlight();
        distantBullets();
        tracerGeometry();
        ammunition();
        pistolTrigger();
        firingAccuracy();
        movementAccuracy();
        weaponMovementSpeeds();
        reloadHud();
        scopedControls();
        expandedArsenal();
        damageAndRewards();
        objectives();
        rounds();
        mr12();
        lifecycle();
        spawnTraversal();
        importedMap();
        smokeGeometry();
        bombAudioGeometry();
        System.out.println("CsCheck: " + checks + " checks passed");
    }

    private static void tracerGeometry() {
        for (Vector direction :
                List.of(
                        new Vector(0, 0, 1),
                        new Vector(0, 0, -1),
                        new Vector(1, 0, 0),
                        new Vector(0, 1, 0),
                        new Vector(0, -1, 0),
                        new Vector(2, -3, 4).normalize())) {
            for (double distance : new double[] {.6, 2, 64, 256}) {
                double length = distance - CsTracers.START;
                var matrix = CsTracers.transform(direction, length);
                var first = matrix.transformPosition(new org.joml.Vector3f(.5f, .5f, 0));
                var last = matrix.transformPosition(new org.joml.Vector3f(.5f, .5f, 1));
                Vector center = direction.clone().multiply(CsTracers.START + length / 2);
                Vector start = center.clone().add(new Vector(first.x, first.y, first.z));
                Vector end = center.clone().add(new Vector(last.x, last.y, last.z));
                check(
                        start.distance(direction.clone().multiply(CsTracers.START)) < .0001
                                && end.distance(direction.clone().multiply(distance)) < .0001,
                        "display endpoints match the hitscan path, including vertical/reversed"
                            + " rays");
                var width = matrix.transformDirection(new org.joml.Vector3f(1, 0, 0));
                check(
                        Math.abs(width.length() - CsTracers.WIDTH) < .00001,
                        "beam thickness stays constant regardless of shot direction and distance");
            }
        }
    }

    private static void hudTranslations() {
        me.petr1furious.potatominigames.i18n.I18n.register();
        try {
            for (String state : List.of("yes", "no")) {
                var label = me.petr1furious.potatominigames.i18n.I18n.tr("cs." + state);
                var health =
                        me.petr1furious.potatominigames.i18n.I18n.tr(
                                "cs.hud.health", 100, 100, label);
                for (Locale locale : List.of(Locale.ENGLISH, Locale.forLanguageTag("ru"))) {
                    String plain = me.petr1furious.potatominigames.i18n.I18n.plain(health, locale);
                    check(
                            plain.contains("100") && !plain.contains("potatominigames."),
                            "start-round health HUD renders with and without helmet");
                }
            }
            for (Locale locale : List.of(Locale.ENGLISH, Locale.forLanguageTag("ru"))) {
                for (String status : List.of("alive", "dead", "offline")) {
                    var label = me.petr1furious.potatominigames.i18n.I18n.tr("cs.status." + status);
                    var line =
                            me.petr1furious.potatominigames.i18n.I18n.tr("cs.score.status", label);
                    check(
                            !me.petr1furious.potatominigames.i18n.I18n.plain(line, locale)
                                    .contains("potatominigames."),
                            "all roster states resolve in both languages");
                }
                String message = "<red>literal</red> <arg:0>";
                var line =
                        me.petr1furious.potatominigames.i18n.I18n.tr(
                                "cs.chat.team",
                                net.kyori.adventure.text.Component.text("Tester"),
                                net.kyori.adventure.text.Component.text(message));
                check(
                        me.petr1furious.potatominigames.i18n.I18n.plain(line, locale)
                                .contains(message),
                        "private team messages preserve literal markup and placeholders");
            }
        } finally {
            me.petr1furious.potatominigames.i18n.I18n.unregister();
        }
    }

    private static void killMessages() {
        me.petr1furious.potatominigames.i18n.I18n.register();
        try {
            for (boolean head : new boolean[] {false, true}) {
                CsMatch m = live();
                m.hurt(CT, T, 5, !head, false, CsWeapon.AWP, false);
                check(
                        m.actor(CT).killInfo == null,
                        "nonlethal damage does not become kill metadata");
                m.hurt(CT, T, 1000, head, false, CsWeapon.AK47, false);
                var victim = m.actor(CT);
                m.actor(T).primary = new CsGun(CsWeapon.AWP);
                victim.loseEquipment();
                check(
                        victim.killInfo.weapon() == CsWeapon.AK47
                                && victim.killInfo.headshot() == head,
                        "kill attribution snapshots the lethal weapon and hit, surviving equipment"
                                + " changes");
                // Repeated damage after elimination cannot rewrite the original kill.
                m.hurt(CT, CT2, 1000, !head, false, CsWeapon.USP, false);
                for (Locale locale : List.of(Locale.ENGLISH, Locale.forLanguageTag("ru"))) {
                    String line =
                            me.petr1furious.potatominigames.i18n.I18n.plain(
                                    CsItems.killMessage(
                                            victim,
                                            net.kyori.adventure.text.Component.text("Killer"),
                                            net.kyori.adventure.text.Component.text("Victim")),
                                    locale);
                    String hit =
                            me.petr1furious.potatominigames.i18n.I18n.plain(
                                    CsItems.tr("kill.headshot"),
                                    locale);
                    check(
                            line.contains("AK-47")
                                    && !line.contains("AWP")
                                    && line.contains(hit) == head
                                    && line.endsWith(head ? hit : "AK-47")
                                    && line.contains("Killer")
                                    && line.contains("Victim"),
                            "localized kill feed includes both players and the lethal gun, with a"
                                    + " headshot suffix only for headshots");
                }
            }
            for (var cause :
                    List.of(
                            CsMatch.KillCause.KNIFE,
                            CsMatch.KillCause.HE,
                            CsMatch.KillCause.FIRE,
                            CsMatch.KillCause.C4)) {
                CsMatch m = live();
                m.hurt(
                        CT,
                        cause == CsMatch.KillCause.C4 ? null : T,
                        1000,
                        true,
                        false,
                        null,
                        cause);
                check(
                        !m.actor(CT).killInfo.headshot() && m.actor(CT).killInfo.cause() == cause,
                        "knife, utility and bomb deaths retain their actual source without a"
                                + " headshot");
                for (Locale locale : List.of(Locale.ENGLISH, Locale.forLanguageTag("ru"))) {
                    String source =
                            me.petr1furious.potatominigames.i18n.I18n.plain(
                                    CsItems.tr(
                                            "kill.source." + cause.name().toLowerCase(Locale.ROOT)),
                                    locale);
                    String line =
                            me.petr1furious.potatominigames.i18n.I18n.plain(
                                    CsItems.killMessage(
                                            m.actor(CT),
                                            net.kyori.adventure.text.Component.text("Killer"),
                                            net.kyori.adventure.text.Component.text("Victim")),
                                    locale);
                    check(
                            line.contains(source) && !line.contains("potatominigames."),
                            "non-gun kill sources resolve in both languages");
                }
            }
        } finally {
            me.petr1furious.potatominigames.i18n.I18n.unregister();
        }
    }

    private static void cs2Economy() {
        CsMatch m = live();
        var ct = m.actor(CT);
        var t = m.actor(T);
        check(
                CsWeapon.M4A4.price == 2900
                        && CsWeapon.FAMAS.price == 1950
                        && CsWeapon.MP7.price == 1400,
                "verified CS2 budget and standard rifle/SMG prices");
        CsMatch.credit(ct, 50000);
        CsMatch.credit(t, 50000);
        int cash = ct.money;
        check(
                m.gear(ct, "fire", 3) == CsMatch.Buy.OK && cash - ct.money == 500,
                "CT incendiary costs $500");
        cash = t.money;
        check(
                m.gear(t, "fire", 3) == CsMatch.Buy.OK && cash - t.money == 400,
                "T Molotov remains $400");
        check(m.gear(ct, "helmet", 3) == CsMatch.Buy.OK, "buy initial vest and helmet");
        ct.armour = 42;
        cash = ct.money;
        int quoted = m.gearPrice(ct, "helmet");
        check(
                m.gear(ct, "helmet", 3) == CsMatch.Buy.OK
                        && cash - ct.money == quoted
                        && quoted == 650
                        && ct.armour == 100
                        && ct.helmet,
                "damaged vest refill retains helmet and never charges for a second helmet");
        int[] expected = {1900, 2400, 2900, 3400, 3400};
        for (int payout : expected) {
            m.actor(CT).money = 0;
            win(m, 0, m.liveAt + 1);
            check(
                    m.actor(CT).money == payout,
                    "2v2 loss streak pays the full per-player competitive economy ladder");
            m.tick(m.until, RNG);
            m.tick(m.until, RNG);
        }
        m = live(1);
        win(m, 0, 3);
        m.tick(103, RNG);
        m.tick(403, RNG);
        ct = m.actor(CT);
        t = m.actor(T);
        check(
                m.gearPrice(ct, "fire") == 400 && m.gearPrice(t, "fire") == 500,
                "equipment quotes follow the halftime side swap");
    }

    private static void sizedEconomy() {
        for (int left = 1; left <= 5; left++)
            for (int right = 1; right <= 5; right++) {
                Map<UUID, Integer> teams = new LinkedHashMap<>();
                for (int i = 0; i < left; i++) teams.put(new UUID(1, i), 0);
                for (int i = 0; i < right; i++) teams.put(new UUID(2, i), 1);
                CsMatch match = new CsMatch(teams, 12, 2, 100, 20, 10, false);
                match.start(0, RNG);
                int largest = Math.max(left, right);
                check(
                        match.incomeScale(left >= right ? 0 : 1) == 1,
                        "the full-sized team always receives the full baseline payout per person");
                check(
                        Math.abs(match.incomeScale(0) * left - match.incomeScale(1) * right)
                                < .00001,
                        "equal base rewards produce equal team budgets for every 1–5 player"
                                + " roster");
                for (var actor : match.actors()) {
                    int size = actor.team == 0 ? left : right;
                    check(
                            actor.money == Math.round(800.0 * largest / size),
                            "pistol cash compensates only the numeric disadvantage");
                }
                match.tick(2, RNG);
                var shooter = match.actor(new UUID(1, 0));
                var victim = match.actor(new UUID(2, 0));
                int before = shooter.money;
                double scale = (double) largest / left;
                match.hurt(victim.id, shooter.id, 10000, false, false, CsWeapon.AK47, false);
                check(
                        shooter.money - before == Math.round(300 * scale),
                        "actual kill payout applies the round's roster scale");
                double locked = match.incomeScale(1);
                match.disconnect(victim.id);
                check(
                        match.incomeScale(1) == locked,
                        "death and disconnect never increase the remaining players' rewards");
                for (var actor : match.actors())
                    if (actor.team == 1 && actor.alive) match.suicide(actor.id);
                before = shooter.money;
                match.tick(3, RNG);
                check(
                        shooter.money == Math.min(16000, before + Math.round(3250 * scale)),
                        "actual round win payout uses the same scale as kills");
                if (right > 1) {
                    match.tick(match.until, RNG);
                    check(
                            Math.abs(
                                            match.incomeScale(1)
                                                    - (double) Math.max(left, right - 1)
                                                            / (right - 1))
                                    < .00001,
                            "the next round updates its roster snapshot after a disconnect");
                }
            }
    }

    private static void grenadeFlight() {
        Vector look = new Vector(0, 0, 1);
        Vector shortThrow =
                CsGrenadePhysics.launch(look, new Vector(), true, CsGrenadePhysics.ThrowMode.SHORT);
        check(
                Math.abs(shortThrow.length() - .5) < .000001
                        && shortThrow.getY() > 0
                        && shortThrow.getZ() < .5
                        && look.equals(new Vector(0, 0, 1)),
                "short throws use potion speed and upward arc without modifying the aim");
        Vector falling = new Vector(0, 0, 1);
        CsGrenadePhysics.airTick(falling, CsGrenadePhysics.ThrowMode.SHORT);
        check(Math.abs(falling.getY() + .05 * .99) < .000001, "short throws use potion gravity");
        for (var mode : CsGrenadePhysics.ThrowMode.values()) {
            Vector standing = CsGrenadePhysics.launch(look, new Vector(), true, mode);
            Vector movingGround = CsGrenadePhysics.launch(look, new Vector(.2, .4, .3), true, mode);
            Vector movingAir = CsGrenadePhysics.launch(look, new Vector(.2, .4, .3), false, mode);
            check(
                    movingGround.distance(standing.clone().add(new Vector(.2, 0, .3))) < .000001
                            && movingAir.distance(standing.clone().add(new Vector(.2, .4, .3)))
                                    < .000001,
                    "both throws inherit horizontal motion and airborne vertical motion");
        }
        Vector longThrow =
                CsGrenadePhysics.launch(look, new Vector(), true, CsGrenadePhysics.ThrowMode.LONG);
        double shortDistance = 0, longDistance = 0;
        for (int tick = 0; tick < 30; tick++) {
            CsGrenadePhysics.airTick(shortThrow, CsGrenadePhysics.ThrowMode.SHORT);
            CsGrenadePhysics.airTick(longThrow, CsGrenadePhysics.ThrowMode.LONG);
            shortDistance += shortThrow.getZ();
            longDistance += longThrow.getZ();
        }
        check(
                longDistance > shortDistance * 3,
                "the long throw covers substantially more distance over the same utility fuse");
        Vector launch =
                CsGrenadePhysics.launch(look, new Vector(), true, CsGrenadePhysics.ThrowMode.LONG);
        check(
                launch.getZ() == 1.5 && launch.getY() == 0 && look.getZ() == 1,
                "level throws follow the crosshair without an upward boost or mutating aim");
        Vector moving =
                CsGrenadePhysics.launch(
                        look, new Vector(.2, .4, .3), true, CsGrenadePhysics.ThrowMode.LONG);
        check(
                moving.equals(new Vector(.2, 0, 1.8)),
                "grounded throws inherit only horizontal motion");
        moving =
                CsGrenadePhysics.launch(
                        look, new Vector(.2, .4, .3), false, CsGrenadePhysics.ThrowMode.LONG);
        check(
                moving.equals(new Vector(.2, .4, 1.8)),
                "airborne throws inherit vertical motion too");
        double travelled = 0;
        for (int tick = 0; tick < 20; tick++) {
            CsGrenadePhysics.airTick(launch, CsGrenadePhysics.ThrowMode.LONG);
            travelled += launch.getZ();
        }
        check(
                Math.abs(launch.getZ() - 1.5 * Math.pow(.99, 20)) < .000001
                        && Math.abs(launch.getY() + .03 * .99 * (1 - Math.pow(.99, 20)) / .01)
                                < .000001
                        && travelled > 27
                        && travelled < 28,
                "twenty ticks retain snowball drag, gravity and horizontal travel");
    }

    private static void distantBullets() throws Exception {
        CsMatch match = live();
        World[] world = new World[1];
        Player target = player(CT, world);
        Player shooter = player(T, world);
        int[] mode = {0}; // distant player, solid cover, then an unobstructed miss
        Block cover =
                (Block)
                        Proxy.newProxyInstance(
                                Block.class.getClassLoader(),
                                new Class<?>[] {Block.class},
                                (proxy, method, args) ->
                                        method.getName().equals("getType")
                                                ? Material.BEDROCK
                                                : defaultValue(method.getReturnType()));
        world[0] =
                (World)
                        Proxy.newProxyInstance(
                                World.class.getClassLoader(),
                                new Class<?>[] {World.class},
                                (proxy, method, args) -> {
                                    if (method.getName().equals("equals")) return proxy == args[0];
                                    if (method.getName().equals("rayTrace")) {
                                        Location start = (Location) args[0];
                                        Vector direction = (Vector) args[1];
                                        double range = (double) args[2];
                                        if (mode[0] == 2) return null;
                                        double distance = mode[0] == 0 ? 150 : 40;
                                        if (range < distance) return null;
                                        Vector hit =
                                                start.toVector()
                                                        .add(direction.clone().multiply(distance));
                                        return mode[0] == 0
                                                ? new RayTraceResult(hit, target)
                                                : new RayTraceResult(hit, cover, BlockFace.NORTH);
                                    }
                                    return defaultValue(method.getReturnType());
                                });
        CsSession session = new CsSession(null, null, null);
        set(session, "world", world[0]);
        set(session, "match", match);
        set(session, "built", true);
        CsCombat combat = new CsCombat(session);
        Method trace =
                CsCombat.class.getDeclaredMethod(
                        "trace",
                        Player.class,
                        Location.class,
                        Vector.class,
                        double.class,
                        CsWeapon.class);
        trace.setAccessible(true);
        for (CsWeapon weapon : CsWeapon.values()) {
            check(weapon.range() >= 220, "all guns span the imported map diagonal");
            check(
                    Math.abs(weapon.damageScale(64) - Math.pow(weapon.rangeFalloff(), 100.0 / 16))
                                    < .000001
                            && weapon.damageScale(0) == 1
                            && weapon.damageScale(100) < weapon.damageScale(64),
                    "the old 100-block damage curve is reached at 64 blocks, without reducing base"
                            + " damage");
        }
        for (CsWeapon weapon :
                List.of(CsWeapon.FAMAS, CsWeapon.AK47, CsWeapon.M4A4, CsWeapon.AWP)) {
            Object shot =
                    trace.invoke(
                            combat,
                            shooter,
                            new Location(world[0], 0, 79, 0),
                            new Vector(0, 0, 1),
                            weapon.range(),
                            weapon);
            Object impact = get(shot, "impact");
            check(
                    impact != null
                            && get(impact, "target") == target
                            && (double) get(impact, "damage") > 0,
                    "real hitscan reaches a player 150 blocks away");
            check((double) get(shot, "distance") == 150, "trail ends at the distant target");
            check(
                    Math.abs(
                                    (double) get(impact, "damage")
                                            - weapon.damage * weapon.damageScale(150))
                            < .000001,
                    "actual distant hits apply the strengthened distance curve");
        }
        mode[0] = 1;
        Object shot =
                trace.invoke(
                        combat,
                        shooter,
                        new Location(world[0], 0, 79, 0),
                        new Vector(0, 0, 1),
                        256.0,
                        CsWeapon.FAMAS);
        check(
                get(shot, "impact") == null && (double) get(shot, "distance") == 40,
                "cover stops both the actual bullet and its visible trail");
        mode[0] = 2;
        shot =
                trace.invoke(
                        combat,
                        shooter,
                        new Location(world[0], 0, 79, 0),
                        new Vector(0, 0, 1),
                        256.0,
                        CsWeapon.FAMAS);
        check(
                get(shot, "impact") == null && (double) get(shot, "distance") == 256,
                "unobstructed miss retains full range");
    }

    private static Player player(UUID id, World[] world) {
        return (Player)
                Proxy.newProxyInstance(
                        Player.class.getClassLoader(),
                        new Class<?>[] {Player.class},
                        (proxy, method, args) ->
                                switch (method.getName()) {
                                    case "getUniqueId" -> id;
                                    case "getWorld" -> world[0];
                                    case "getGameMode" -> GameMode.SURVIVAL;
                                    case "getHeight" -> 1.8;
                                    case "getLocation" -> new Location(world[0], 0, 78, 150);
                                    default -> defaultValue(method.getReturnType());
                                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == double.class) return 0.0;
        if (type == float.class) return 0f;
        if (type == long.class) return 0L;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == char.class) return (char) 0;
        return 0;
    }

    private static void set(Object object, String name, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }

    private static Object get(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static CsMatch live() {
        return live(CsConfig.DEFAULT_HALF);
    }

    private static CsMatch live(int half) {
        CsMatch m = new CsMatch(TEAMS, half, 2, 100, 20, 10, false);
        m.start(0, RNG);
        check(
                m.phase == CsMatch.Phase.FREEZE
                        && !m.hurt(CT, T, 500, false, false, CsWeapon.AK47, false).killed(),
                "freeze blocks damage");
        m.tick(1, RNG);
        check(m.phase == CsMatch.Phase.FREEZE, "freeze lasts full duration");
        m.tick(2, RNG);
        return m;
    }

    private static void buying() {
        CsMatch m = live();
        var t = m.actor(T);
        var ct = m.actor(CT);
        check(t.money == 800 && ct.money == 800, "pistol round starts with $800");
        check(
                t.secondary.weapon == CsWeapon.GLOCK && ct.secondary.weapon == CsWeapon.USP,
                "side-specific default pistols");
        check(
                m.weapon(t, CsWeapon.AK47, 3) == CsMatch.Buy.MONEY && t.primary == null,
                "unaffordable buy has no effect");
        CsMatch.credit(t, 50000);
        CsMatch.credit(ct, 50000);
        check(t.money == 16000, "cash is capped at $16000");
        check(
                m.weapon(t, CsWeapon.M4A4, 3) == CsMatch.Buy.SIDE && t.money == 16000,
                "opposing primary cannot be bought");
        check(
                m.weapon(t, CsWeapon.AK47, 3) == CsMatch.Buy.OK && t.money == 13300,
                "AK purchase charges exact price");
        check(
                m.weapon(ct, CsWeapon.AWP, 3) == CsMatch.Buy.OK && ct.money == 11250,
                "AWP available to CT");
        check(m.gear(t, "kit", 3) == CsMatch.Buy.SIDE, "defuse kit is CT-only");
        check(m.gear(t, "vest", 3) == CsMatch.Buy.OK && t.armour == 100, "kevlar gives 100 armor");
        int cash = t.money;
        check(
                m.gear(t, "helmet", 3) == CsMatch.Buy.OK && cash - t.money == 350 && t.helmet,
                "helmet upgrade costs $350 with intact vest");
        check(m.gear(t, "vest", 3) == CsMatch.Buy.LIMIT, "cannot buy redundant vest");
        for (String id : List.of("he", "flash", "flash", "smoke"))
            check(m.gear(t, id, 3) == CsMatch.Buy.OK, "legal grenade purchase");
        check(
                m.gear(t, "fire", 3) == CsMatch.Buy.LIMIT && t.grenades() == 4,
                "total grenade limit");
        check(
                m.consume(t, CsMatch.Utility.FLASH) && t.utility.get(CsMatch.Utility.FLASH) == 1,
                "throw consumes one flash");
        check(m.gear(t, "he", 3) == CsMatch.Buy.LIMIT, "HE limited to one");
        check(m.canBuy(t, 12) && !m.canBuy(t, 13), "buy deadline is enforced");
        cash = t.money;
        check(
                m.weapon(t, CsWeapon.DEAGLE, 13) == CsMatch.Buy.CLOSED && t.money == cash,
                "expired shop does not charge");
        CsMatch.credit(t, -50000);
        check(t.money == 0, "penalty cannot make money negative");
    }

    private static void ammunition() {
        for (CsWeapon weapon : CsWeapon.values()) {
            CsGun g = new CsGun(weapon);
            long now = 0;
            for (int i = 0; i < weapon.magazine; i++, now += 100)
                check(g.fire(now), "loaded weapon fires: " + weapon);
            check(g.magazine == 0 && !g.fire(now), "empty gun cannot fire: " + weapon);
            check(g.reload(now), "empty gun reloads: " + weapon);
            g.tick(now + 200);
            check(
                    g.magazine == weapon.magazine && g.reserve == weapon.reserve - weapon.magazine,
                    "finite reserve used by reload: " + weapon);
            g.magazine = 0;
            g.reserve = 0;
            check(!g.reload(now + 201), "no ammunition creation: " + weapon);
        }
        CsGun ak = new CsGun(CsWeapon.AK47);
        check(ak.fire(0) && !ak.fire(1) && ak.fire(2), "AK fire cadence");
        ak.reload(3);
        ak.switchAway();
        ak.tick(100);
        check(
                ak.magazine == 28 && ak.reserve == 90 && ak.reloadAt < 0,
                "switching cancels reload without free rounds");
        ak.deploy(100);
        check(!ak.fire(111) && ak.fire(112), "weapon draw delay enforced");
        ak.restock();
        check(
                ak.magazine == 30 && ak.reserve == 90 && !ak.scoped,
                "round restock resets transient gun state");
        CsGun nova = new CsGun(CsWeapon.NOVA);
        nova.magazine = 0;
        nova.reserve = 3;
        nova.reload(0);
        nova.tick(19);
        check(nova.magazine == 0, "shotgun first shell waits");
        nova.tick(20);
        check(nova.magazine == 1 && nova.reserve == 2, "shotgun reloads one shell");
        check(
                nova.fire(21) && nova.reloadAt < 0 && nova.magazine == 0,
                "loaded shell can interrupt shotgun reload");
    }

    private static void pistolTrigger() {
        for (var weapon :
                List.of(
                        CsWeapon.GLOCK, CsWeapon.USP, CsWeapon.P250, CsWeapon.DUAL_BERETTAS,
                        CsWeapon.FIVE_SEVEN, CsWeapon.TEC9, CsWeapon.DEAGLE)) {
            check(weapon.requiresTriggerRelease(), "semi-automatic pistols require release");
            CsGun gun = new CsGun(weapon);
            for (long now = 0; now < 100; now += 4) if (gun.pressTrigger()) gun.fire(now);
            check(
                    gun.magazine == weapon.magazine - 1,
                    "repeated native use packets during one hold spend only one pistol round");
            gun.releaseTrigger();
            check(
                    gun.pressTrigger() && gun.fire(100),
                    "release followed by a new press permits a second shot");
            check(gun.reload(101), "a partly empty pistol can reload while its trigger is held");
            gun.tick(1000);
            check(
                    !gun.pressTrigger() && gun.magazine == weapon.magazine,
                    "completing a reload cannot turn a held pistol trigger into automatic fire");
            gun.releaseTrigger();
            check(gun.pressTrigger() && gun.fire(1001), "a fresh press fires after reload");
            gun.switchAway();
            check(!gun.triggerHeld(), "switching or dropping releases the old trigger state");
            gun.pressTrigger();
            gun.restock();
            check(!gun.triggerHeld(), "a new round starts with a released trigger");
        }
        check(
                CsWeapon.CZ75.automatic() && !CsWeapon.CZ75.requiresTriggerRelease(),
                "the automatic CZ75 retains held fire");
    }

    private static void firingAccuracy() {
        for (var weapon :
                List.of(
                        CsWeapon.GLOCK, CsWeapon.DEAGLE, CsWeapon.CZ75, CsWeapon.MP9,
                        CsWeapon.AK47, CsWeapon.M4A4, CsWeapon.AUG, CsWeapon.SG553)) {
            CsGun fast = new CsGun(weapon);
            fast.scoped = weapon.scope();
            check(fast.fire(0), "a settled weapon can fire its first accurate shot");
            double first = fast.shotSpread();
            long cadence = (long) Math.ceil(weapon.cadence);
            for (int shot = 1; shot < 6; shot++)
                check(fast.fire(shot * cadence), "rapid accepted shots follow weapon cadence");
            check(
                    fast.shotSpread() > first,
                    "rapid taps and automatic fire accumulate inaccuracy, including while scoped");
            check(fast.fire(5 * cadence + 200), "a paused weapon can fire again");
            check(
                    Math.abs(fast.shotSpread() - first) < 1e-10,
                    "pausing restores the original first-shot spread");
            CsGun slow = new CsGun(weapon);
            slow.scoped = weapon.scope();
            for (int shot = 0; shot < 4; shot++) {
                check(slow.fire(shot * 40L), "paced single shots are accepted");
                check(
                        Math.abs(slow.shotSpread() - first) < 1e-10,
                        "paced shots do not accumulate the rapid-fire penalty");
            }
        }
        CsGun clean = new CsGun(CsWeapon.GLOCK), repeated = new CsGun(CsWeapon.GLOCK);
        clean.fire(0);
        repeated.fire(0);
        check(!repeated.fire(1) && !repeated.fire(2), "too-early clicks remain blocked");
        clean.fire(3);
        repeated.fire(3);
        check(
                Math.abs(clean.shotSpread() - repeated.shotSpread()) < 1e-10,
                "rejected trigger attempts do not add an accuracy penalty");
        clean.switchAway();
        clean.fire(6);
        repeated.fire(6);
        check(
                Math.abs(clean.shotSpread() - repeated.shotSpread()) < 1e-10,
                "a quick weapon switch cannot instantly clear accumulated inaccuracy");
        clean.restock();
        clean.fire(0);
        check(
                Math.abs(clean.shotSpread() - CsWeapon.GLOCK.shotSpread(false, 1)) < 1e-10,
                "new-round restocking resets accumulated inaccuracy");
    }

    private static void movementAccuracy() {
        for (CsWeapon weapon : CsWeapon.values()) {
            double runSpeed = .28 * weapon.mobility;
            double slow = weapon.movementSpread(runSpeed * .3);
            double running = weapon.movementSpread(runSpeed);
            check(
                    weapon.movementSpread(0) == 0 && slow > 0 && running > slow,
                    "every weapon distinguishes standing, slow movement and full-speed running");
            check(
                    weapon.movementSpread(runSpeed * 2) == running,
                    "knockback or excess speed cannot produce an unbounded movement penalty");
        }
        check(
                CsWeapon.MP9.movementSpread(.2) < CsWeapon.AK47.movementSpread(.2)
                        && CsWeapon.TEC9.movementSpread(.2) < CsWeapon.DEAGLE.movementSpread(.2)
                        && CsWeapon.SSG08.movementSpread(.2) < CsWeapon.AWP.movementSpread(.2),
                "moving-fire roles differ between individual weapons, not just categories");
        CsMovement motion = new CsMovement();
        for (int packet = 0; packet < 4; packet++) motion.move(10, .04, 0);
        check(
                Math.abs(motion.speed(10) - .16) < 1e-10,
                "several short movement packets count as the full tick's horizontal travel");
        motion.move(11, .01, 0);
        check(
                Math.abs(motion.speed(11) - .16) < 1e-10,
                "a partial new tick does not instantly hide the previous movement speed");
        check(
                Math.abs(motion.speed(12) - .01) < 1e-10 && motion.speed(13) == 0,
                "movement accuracy recovers promptly after the player stops");
        motion.move(30, .03, .04);
        check(
                Math.abs(motion.speed(30) - .05) < 1e-10,
                "a new movement sequence ignores stale travel and measures diagonal speed");
        CsMovement vertical = new CsMovement();
        vertical.move(10, 0, 0);
        check(vertical.speed(10) == 0, "vertical-only movement is not horizontal running");
    }

    private static void weaponMovementSpeeds() {
        Map<Integer, List<CsWeapon>> speeds =
                Map.of(
                        240,
                                List.of(
                                        CsWeapon.GLOCK,
                                        CsWeapon.USP,
                                        CsWeapon.P250,
                                        CsWeapon.DUAL_BERETTAS,
                                        CsWeapon.FIVE_SEVEN,
                                        CsWeapon.TEC9,
                                        CsWeapon.CZ75,
                                        CsWeapon.MAC10,
                                        CsWeapon.MP9),
                        230, List.of(CsWeapon.DEAGLE, CsWeapon.UMP45, CsWeapon.P90, CsWeapon.SSG08),
                        225, List.of(CsWeapon.M4A1S, CsWeapon.M4A4, CsWeapon.MAG7),
                        220, List.of(CsWeapon.MP7, CsWeapon.FAMAS, CsWeapon.AUG, CsWeapon.NOVA),
                        215, List.of(CsWeapon.AK47, CsWeapon.GALIL, CsWeapon.XM1014),
                        210, List.of(CsWeapon.SG553, CsWeapon.SAWED_OFF),
                        200, List.of(CsWeapon.AWP),
                        150, List.of(CsWeapon.NEGEV));
        Set<CsWeapon> checked = EnumSet.noneOf(CsWeapon.class);
        speeds.forEach((speed, weapons) -> {
            for (CsWeapon weapon : weapons) {
                checked.add(weapon);
                check(
                        weapon.maxSpeed == speed
                                && Math.abs(weapon.mobility - speed / 250.0) < 1e-10,
                        "held-weapon speed follows the CS2 table relative to a 250-speed knife");
            }
        });
        check(checked.size() == CsWeapon.values().length, "the speed table covers every gun");
        check(
                CsWeapon.AWP.scopedSpeed() == 100
                        && CsWeapon.SSG08.scopedSpeed() == 230
                        && CsWeapon.AUG.scopedSpeed() == 150
                        && CsWeapon.SG553.scopedSpeed() == 150,
                "scoped speeds distinguish AWP, Scout and the scoped automatic rifles");
        check(
                CsWeapon.NEGEV.attackMobility() == .5 && CsWeapon.AK47.attackMobility() == 1,
                "Negev has its extra firing slowdown without slowing all automatic weapons");
    }

    private static void reloadHud() {
        me.petr1furious.potatominigames.i18n.I18n.register();
        try {
            for (var weapon : CsWeapon.values()) {
                for (int reserve : new int[] {2, weapon.reserve}) {
                    CsGun gun = new CsGun(weapon);
                    gun.magazine = weapon.magazine - 3;
                    gun.reserve = reserve;
                    check(gun.reload(100), "partial magazine starts the reload countdown");
                    long total = gun.reloadTicksRemaining(100);
                    long now = 100;
                    while (gun.reloadAt >= 0 && now < 1100) {
                        check(
                                gun.reloadTicksRemaining(now) == total - (now - 100),
                                "full-reload countdown stays continuous while individual shells"
                                        + " load");
                        now++;
                        gun.tick(now);
                    }
                    check(
                            gun.reloadAt < 0
                                    && now - 100 == total
                                    && gun.reloadTicksRemaining(now) == 0,
                            "countdown ends when the real reload finishes, including reserve"
                                    + " exhaustion");
                    for (Locale locale : List.of(Locale.ENGLISH, Locale.forLanguageTag("ru"))) {
                        String ready =
                                me.petr1furious.potatominigames.i18n.I18n.plain(
                                        CsItems.ammoHud(gun, now), locale);
                        check(
                                ready.contains("F")
                                        && ready.contains(Integer.toString(gun.magazine)),
                                "ready HUD includes ammunition and reload key in both languages");
                    }
                    gun.magazine--;
                    gun.reserve = 2;
                    gun.reload(now);
                    long lastTick = now + gun.reloadTicksRemaining(now) - 1;
                    for (Locale locale : List.of(Locale.ENGLISH, Locale.forLanguageTag("ru"))) {
                        String reloading =
                                me.petr1furious.potatominigames.i18n.I18n.plain(
                                        CsItems.ammoHud(gun, lastTick), locale);
                        check(
                                reloading.contains("0.1")
                                        && !reloading.contains("potatominigames."),
                                "reload HUD rounds up its last tenth and resolves in both locales");
                    }
                    gun.switchAway();
                    check(
                            gun.reloadTicksRemaining(now) == 0,
                            "switching weapons immediately cancels its countdown");
                }
            }
        } finally {
            me.petr1furious.potatominigames.i18n.I18n.unregister();
        }
    }

    private static void scopedControls() {
        for (var weapon : CsWeapon.values()) {
            CsGun gun = new CsGun(weapon);
            if (!weapon.scope()) {
                check(
                        !gun.toggleScope(0) && !gun.scoped,
                        "guns without optics cannot enter scope mode");
                continue;
            }
            check(
                    weapon.material != Material.SPYGLASS,
                    "right-click firing never activates native held-use spyglass behavior");
            check(gun.toggleScope(0) && gun.scoped, "first left click enables scope");
            check(
                    !gun.toggleScope(0) && !gun.toggleScope(1) && gun.scoped,
                    "overlapping interact, attack and swing events cannot immediately undo scope");
            check(gun.toggleScope(4) && !gun.scoped, "a later left click disables scope");
            check(
                    gun.toggleScope(8) && gun.fire(8) && gun.scoped,
                    "right-click shot preserves scope until a deliberate exit or reload");
            check(
                    gun.reload(9) && !gun.scoped && !gun.toggleScope(10),
                    "reload exits scope and cannot be scoped through");
            long ready = gun.reloadAt;
            check(
                    gun.toggleScope(ready) && gun.scoped,
                    "scope can be enabled after reload completes");
            gun.deploy(200);
            check(
                    !gun.scoped
                            && !gun.toggleScope(200)
                            && gun.toggleScope(200 + weapon.deployTicks()),
                    "switching clears scope and its draw time is respected");
            for (double tagged : new double[] {1, .45}) {
                double normal = .26 * weapon.mobility * tagged;
                float compensated = CsScope.walkSpeed(normal, weapon);
                double effective = compensated * CsScope.speedFactor(weapon);
                check(
                        compensated > 0
                                && compensated <= 1
                                && Math.abs(effective - normal * weapon.scopedMobility()) < .000001,
                        "FOV compensation retains intended scoped speed including hit tagging");
            }
            double fov = (CsScope.speedFactor(weapon) + 1) / 2;
            double expectedFov =
                    switch (weapon) {
                        case AWP -> .555;
                        case SSG08 -> .6196;
                        case AUG, SG553 -> .578;
                        default -> throw new AssertionError("Unexpected scoped weapon");
                    };
            check(
                    Math.abs(fov - expectedFov) < .000001,
                    "scope preserves zoom while allowing each weapon's actual scoped speed");
        }
    }

    private static void expandedArsenal() {
        Set<CsWeapon> offered = EnumSet.noneOf(CsWeapon.class);
        for (int side = 0; side < 2; side++) {
            var choices = CsShop.weapons(side);
            offered.addAll(choices);
            Set<CsWeapon> grouped = EnumSet.noneOf(CsWeapon.class);
            Map<Integer, CsWeapon> placed = CsShop.slots(side);
            Set<Integer> columns = new HashSet<>();
            for (var category : CsWeapon.Category.values()) {
                var weapons = CsShop.weapons(side, category);
                check(
                        weapons.size() <= CsShop.group(category).width() * CsShop.WEAPON_ROWS,
                        "every category fits its assigned columns");
                var group = CsShop.group(category);
                for (int column = group.column(); column < group.column() + group.width(); column++)
                    check(columns.add(column), "category columns never overlap");
                for (int index = 0; index < weapons.size(); index++) {
                    int slot = group.slot(index);
                    check(
                            slot >= 9
                                    && slot < CsShop.GEAR_START
                                    && slot % 9 >= group.column()
                                    && slot % 9 < group.column() + group.width()
                                    && placed.get(slot) == weapons.get(index),
                            "each visible weapon stays below its header and above the gear row");
                }
                int previous = -1;
                for (var weapon : weapons) {
                    check(
                            weapon.category == category
                                    && weapon.allowed(side)
                                    && weapon.price >= previous,
                            "category contains only legal weapons ordered from cheapest to most"
                                    + " expensive");
                    previous = weapon.price;
                    check(grouped.add(weapon), "each weapon appears in exactly one category");
                }
            }
            check(
                    grouped.equals(new HashSet<>(choices))
                            && columns.size() == 9
                            && new HashSet<>(placed.values()).equals(grouped)
                            && placed.size() == grouped.size(),
                    "one screen exposes every legal weapon once in distinct categorized columns");
            check(
                    choices.size() <= CsShop.GEAR_START && CsShop.GEAR_START + 7 <= CsShop.SIZE,
                    "all guns fit without replacing equipment buttons");
            check(
                    choices.contains(side == 0 ? CsWeapon.TEC9 : CsWeapon.FIVE_SEVEN),
                    "side-specific armor-piercing pistol is offered");
            check(
                    !choices.contains(side == 0 ? CsWeapon.AUG : CsWeapon.SG553),
                    "opposite side's scoped rifle is not offered");
        }
        check(
                offered.equals(EnumSet.allOf(CsWeapon.class)),
                "every weapon is obtainable in a faction's shop");
        CsMatch m = live();
        var t = m.actor(T);
        var ct = m.actor(CT);
        CsMatch.credit(t, 16000);
        CsMatch.credit(ct, 16000);
        m.weapon(t, CsWeapon.GALIL, 3);
        var primary = t.primary;
        check(
                m.weapon(t, CsWeapon.P250, 3) == CsMatch.Buy.OK
                        && t.primary == primary
                        && t.secondary.weapon == CsWeapon.P250,
                "new pistol replaces only the pistol slot");
        var secondary = t.secondary;
        check(
                m.weapon(t, CsWeapon.SG553, 3) == CsMatch.Buy.OK
                        && t.secondary == secondary
                        && t.primary.weapon == CsWeapon.SG553,
                "new rifle replaces only the primary slot");
        check(
                m.weapon(t, CsWeapon.FIVE_SEVEN, 3) == CsMatch.Buy.SIDE
                        && m.weapon(ct, CsWeapon.TEC9, 3) == CsMatch.Buy.SIDE,
                "pistol faction restrictions enforced by economy");
        ct.armour = 100;
        ct.helmet = true;
        check(
                !m.hurt(CT, T, CsWeapon.SSG08.damage, false, false, CsWeapon.SSG08, false).killed(),
                "Scout body hit cannot replace AWP one-shot");
        m = live();
        m.hurt(CT, T, 500, false, false, CsWeapon.CZ75, false);
        check(m.actor(T).money == 900, "2v2 automatic pistol kill pays the full $100");
        m = live();
        m.hurt(CT, T, 500, false, false, CsWeapon.P90, false);
        check(m.actor(T).money == 1100, "P90 receives the normal $300 kill reward in 2v2");
        CsGun cz = new CsGun(CsWeapon.CZ75);
        cz.deploy(0);
        check(
                !cz.fire(34) && cz.fire(35) && cz.weapon.automatic() && cz.reserve == 12,
                "CZ automatic fire trades against reserve and long draw time");
        for (CsWeapon w : List.of(CsWeapon.XM1014, CsWeapon.SAWED_OFF)) {
            CsGun g = new CsGun(w);
            g.magazine = 0;
            g.reserve = 2;
            g.reload(0);
            g.tick(20);
            check(
                    g.magazine == 1 && g.reserve == 1 && g.reloadAt >= 0,
                    "additional tube shotgun loads one shell");
            check(
                    g.fire(21) && g.reloadAt < 0,
                    "additional tube shotgun can interrupt reload to fire");
        }
        CsGun mag = new CsGun(CsWeapon.MAG7);
        mag.magazine = 0;
        mag.reload(0);
        mag.tick(20);
        check(mag.magazine == 0, "MAG-7 cannot fire before its magazine replacement finishes");
        mag.tick(CsWeapon.MAG7.reload);
        check(mag.magazine == 5 && mag.reserve == 27, "MAG-7 replaces its entire magazine");
        check(
                CsWeapon.XM1014.automatic() && !CsWeapon.MAG7.automatic(),
                "automatic shotgun and pump shotgun have distinct firing controls");
        CsGun negev = new CsGun(CsWeapon.NEGEV);
        for (int now = 0; negev.burst < 12; now++) negev.fire(now);
        check(
                negev.weapon.shotSpread(false, negev.burst) < negev.weapon.shotSpread(false, 1),
                "sustained belt fire stabilizes spread");
        negev.fire(negev.lastShot + 10);
        check(
                negev.burst == 1 && negev.weapon.shotSpread(false, negev.burst) > .02,
                "idle gap resets machine gun stabilization");
        check(
                CsWeapon.AUG.shotSpread(true, 1) < CsWeapon.AUG.shotSpread(false, 1)
                        && CsWeapon.AUG.range() == CsWeapon.SSG08.range(),
                "scoped assault rifle gains accuracy while retaining full-map hitscan range");
        check(
                CsWeapon.XM1014.rangeFalloff() < CsWeapon.AK47.rangeFalloff(),
                "shotgun pellets lose more damage at distance than rifle bullets");
        me.petr1furious.potatominigames.i18n.I18n.register();
        try {
            for (CsWeapon w : CsWeapon.values()) {
                var name = me.petr1furious.potatominigames.i18n.I18n.tr("cs.weapon." + w.id);
                List<net.kyori.adventure.text.Component> text =
                        new ArrayList<>(CsItems.shopLore(w));
                text.add(name);
                for (var line : text)
                    for (Locale locale : List.of(Locale.ENGLISH, Locale.forLanguageTag("ru")))
                        check(
                                !me.petr1furious.potatominigames.i18n.I18n.plain(line, locale)
                                        .contains("potatominigames."),
                                "each weapon name and shop trait renders in both languages");
            }
        } finally {
            me.petr1furious.potatominigames.i18n.I18n.unregister();
        }
    }

    private static void damageAndRewards() {
        CsMatch m = live();
        var ct = m.actor(CT);
        ct.armour = 100;
        ct.helmet = true;
        var hit = m.hurt(CT, T, 144, true, false, CsWeapon.AK47, false);
        check(
                hit.killed() && m.actor(T).kills == 1 && m.actor(T).money == 1100,
                "AK headshot kills through helmet and rewards the full $300 once in 2v2");
        check(
                m.hurt(CT, T, 144, true, false, CsWeapon.AK47, false).health() == 0
                        && ct.deaths == 1,
                "duplicate death cannot score");
        m = live();
        ct = m.actor(CT);
        ct.armour = 100;
        ct.helmet = true;
        check(
                !m.hurt(CT, T, 132, true, false, CsWeapon.M4A4, false).killed() && ct.health > 7,
                "M4 helmet headshot is survivable");
        m = live();
        ct = m.actor(CT);
        ct.armour = 100;
        check(
                m.hurt(CT, T, 140, true, false, CsWeapon.USP, false).killed(),
                "vest alone does not protect head");
        m = live();
        ct = m.actor(CT);
        ct.armour = 100;
        double armor = ct.armour;
        m.hurt(CT, T, 86.25, false, true, CsWeapon.AWP, false);
        check(ct.health > 13 && ct.armour == armor, "AWP leg hit survives and bypasses armor");
        m = live();
        m.hurt(CT, T, 45, false, true, CsWeapon.GLOCK, false);
        m.hurt(CT, T2, 100, false, true, CsWeapon.MP9, false);
        check(
                m.actor(T).assists == 1 && m.actor(T2).money == 1400,
                "40-damage assist and SMG kill bonus");
        m = live();
        m.hurt(T2, T, 100, false, true, CsWeapon.AK47, false);
        check(m.actor(T2).health == 67, "friendly bullets have reduced damage");
        m.hurt(T2, T, 1000, false, true, CsWeapon.AK47, false);
        check(
                m.actor(T).kills == -1 && m.actor(T).money == 500,
                "teamkill penalizes kills and money");
        m = live();
        m.hurt(CT, T, 180, false, false, null, false);
        check(m.actor(T).money == 2300, "2v2 knife reward is the full $1500");
        m = live();
        m.suicide(T);
        m.hurt(CT, T, 500, false, false, null, true);
        check(
                m.actor(T).kills == 1 && m.actor(CT).deaths == 1,
                "a grenade remains attributable after its thrower dies");
    }

    private static void objectives() {
        CsMatch m = live();
        UUID carrier = m.carrier;
        check(
                !m.plant(CT, 0, 3) && !m.plant(carrier, -1, 3),
                "only T carrier at a valid site can plant");
        check(m.dropBomb(carrier) && !m.dropBomb(carrier), "C4 drop is single-use");
        check(!m.takeBomb(CT) && m.takeBomb(T), "only alive T may recover dropped C4");
        check(
                m.plant(T, 1, 5) && m.carrier == null && m.detonateAt == 25,
                "plant consumes carrier and starts full fuse");
        check(!m.plant(T, 0, 6), "cannot plant twice");
        check(m.actor(T).money == 1100, "the full $300 plant bonus is credited once in 2v2");
        m.hurt(T, CT, 1000, false, false, CsWeapon.AWP, false);
        m.hurt(T2, CT, 1000, false, false, CsWeapon.AWP, false);
        m.tick(6, RNG);
        check(m.phase == CsMatch.Phase.LIVE, "plant keeps round alive after all T die");
        check(
                !m.defuse(T, 7) && m.defuse(CT, 24),
                "only alive CT can finish defuse before deadline");
        check(
                m.lastRound.reason() == CsMatch.Reason.DEFUSE
                        && m.score[1] == 1
                        && m.actor(CT).mvps == 1,
                "defuse wins round and awards MVP");
        check(m.actor(T).money == 3600, "2v2 losing T receive full loss and $600 plant bonuses");
        int score = m.score[1];
        check(!m.defuse(CT2, 24) && m.score[1] == score, "defuse scores once");
        m = live();
        carrier = m.carrier;
        m.plant(carrier, 0, 5);
        check(!m.defuse(CT, 25), "defuse at detonation deadline is too late");
        m.tick(25, RNG);
        check(
                m.lastRound.reason() == CsMatch.Reason.EXPLOSION && m.actor(carrier).mvps == 1,
                "explosion wins and planter gets MVP");
        m = live();
        check(!m.plant(m.carrier, 0, 102), "plant completion at round deadline rejected");
        m.tick(102, RNG);
        check(
                m.lastRound.reason() == CsMatch.Reason.TIMEOUT && m.actor(T).money == 800,
                "alive T saving on timeout get no loss income");
    }

    private static void win(CsMatch m, int winner, long now) {
        UUID source = winner == 0 ? T : CT;
        for (var a : m.actors())
            if (a.team != winner && a.alive)
                m.hurt(a.id, source, 10000, false, false, CsWeapon.AK47, false);
        m.tick(now, RNG);
    }

    private static void rounds() {
        CsMatch m = live();
        var survivor = m.actor(T);
        survivor.primary = new CsGun(CsWeapon.AK47);
        survivor.primary.magazine = 4;
        survivor.armour = 42;
        survivor.health = 20;
        win(m, 0, 3);
        check(m.score[0] == 1 && m.actor(CT).money == 2700, "2v2 first loss pays the full $1900");
        check(
                m.losses[0] == 0 && m.losses[1] == 2,
                "loss counter decreases on win and increases on loss");
        check(
                m.phase == CsMatch.Phase.ROUND_END && m.until == 103,
                "round-end loot window lasts five seconds after the round result");
        m.tick(102, RNG);
        check(
                m.phase == CsMatch.Phase.ROUND_END && m.number == 1 && m.score[0] == 1,
                "the result stays fixed and round reset waits for the entire loot window");
        m.tick(103, RNG);
        check(
                m.number == 2
                        && m.phase == CsMatch.Phase.FREEZE
                        && survivor.health == 100
                        && survivor.primary.magazine == 30
                        && survivor.armour == 42,
                "survivor keeps gear and armor, heals and restocks next round");
        check(
                m.actor(CT).primary == null
                        && m.actor(CT).armour == 0
                        && m.actor(CT).secondary.weapon == CsWeapon.USP,
                "dead player receives default pistol only");
        m.tick(105, RNG);
        win(m, 0, 106);
        check(m.actor(CT).money == 5100, "2v2 consecutive second loss pays the full $2400");
        m = live(1);
        m.actor(T).primary = new CsGun(CsWeapon.AK47);
        win(m, 0, 3);
        m.tick(103, RNG);
        check(m.phase == CsMatch.Phase.HALFTIME, "halftime occurs after configured half");
        m.tick(403, RNG);
        check(
                m.number == 2 && m.side(m.actor(T)) == 1 && m.side(m.actor(CT)) == 0,
                "stable teams exchange factions");
        check(
                m.actor(T).money == 800
                        && m.actor(T).primary == null
                        && m.actor(T).secondary.weapon == CsWeapon.USP,
                "halftime resets economy and equipment");
        m.tick(405, RNG);
        win(m, 0, 406);
        m.tick(506, RNG);
        check(m.phase == CsMatch.Phase.FINISHED && m.score[0] == 2, "first to half-plus-one wins");
        m = live(1);
        win(m, 0, 3);
        m.tick(103, RNG);
        m.tick(403, RNG);
        m.tick(405, RNG);
        win(m, 1, 406);
        m.tick(506, RNG);
        check(
                m.phase == CsMatch.Phase.FINISHED && m.score[0] == m.score[1],
                "full-length tied match ends as draw");
    }

    private static void mr12() throws Exception {
        var config = new org.bukkit.configuration.file.YamlConfiguration();
        config.set("roundsPerHalf", 15);
        config.set("freezeSeconds", 21);
        check(
                CsConfig.migrateFormat(config)
                        && config.getInt("roundsPerHalf") == 12
                        && config.getInt("freezeSeconds") == 21,
                "legacy default migrates to MR12 without changing other settings");
        config.set("roundsPerHalf", 15);
        check(
                !CsConfig.migrateFormat(config) && config.getInt("roundsPerHalf") == 15,
                "later explicit admin changes survive the one-time migration");
        config = new org.bukkit.configuration.file.YamlConfiguration();
        config.set("roundsPerHalf", 5);
        check(
                CsConfig.migrateFormat(config) && config.getInt("roundsPerHalf") == 5,
                "custom short matches retain their half length");
        try (var resource = CsCheck.class.getResourceAsStream("/games/cs.yml")) {
            check(resource != null, "CS default configuration is bundled");
            var bundled =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                            new java.io.InputStreamReader(
                                    resource, java.nio.charset.StandardCharsets.UTF_8));
            check(
                    bundled.getInt("roundsPerHalf") == 12
                            && CsConfig.DEFAULT_HALF == 12
                            && !CsConfig.migrateFormat(bundled),
                    "fresh servers start with the MR12 defaults and current migration version");
        }
        for (boolean draw : List.of(false, true)) {
            CsMatch match = live();
            int total = draw ? 24 : 13;
            for (int round = 1; round <= total; round++) {
                check(
                        match.phase == CsMatch.Phase.LIVE && match.number == round,
                        "MR12 starts each scheduled round");
                win(match, draw && round > 12 ? 1 : 0, match.liveAt + 1);
                match.tick(match.until, RNG);
                if (round == 12) {
                    check(
                            match.phase == CsMatch.Phase.HALFTIME,
                            "MR12 changes sides after exactly twelve rounds");
                    match.tick(match.until, RNG);
                    check(
                            match.number == 13
                                    && match.side(match.actor(T)) == 1
                                    && match.actor(T).money == 800,
                            "thirteenth round swaps sides and resets cash");
                }
                if (round < total) match.tick(match.until, RNG);
            }
            check(
                    match.phase == CsMatch.Phase.FINISHED
                            && match.score[0] == (draw ? 12 : 13)
                            && match.score[1] == (draw ? 12 : 0),
                    "MR12 ends at thirteen wins or a 12:12 draw, without an extra round");
        }
    }

    private static void lifecycle() {
        CsMatch m = live();
        UUID c = m.carrier;
        m.disconnect(c);
        check(!m.live(c) && m.actor(c).deaths == 1, "disconnect removes combatant once");
        m.disconnect(c);
        m.reconnect(c);
        check(!m.live(c) && m.actor(c).deaths == 1, "rejoin cannot respawn in same round");
        check(
                m.dropBomb(c) && m.takeBomb(c.equals(T) ? T2 : T),
                "disconnect permits recovery of dropped C4");
        check(m.pause(0) && !m.pause(0), "one queued timeout per team");
        win(m, 1, 3);
        m.tick(103, RNG);
        check(
                m.until == 705 && m.timeouts[0] == 3,
                "queued timeout extends next freeze by 30 seconds");
        check(m.live(c), "rejoined player returns next round");
        m.forfeit(1);
        check(
                m.phase == CsMatch.Phase.FINISHED && m.score[1] >= 13,
                "forfeit ends match at the MR12 winning score");
        CsMatch solo = new CsMatch(Map.of(T, 0), CsConfig.DEFAULT_HALF, 2, 100, 20, 10, true);
        solo.start(0, RNG);
        solo.tick(2, RNG);
        solo.tick(3, RNG);
        check(
                solo.phase == CsMatch.Phase.LIVE && solo.actor(T).money == 16000,
                "practice does not end from missing opponents");
    }

    private static void spawnTraversal() {
        var from = new CsArena.Pos(0, 82, 0);
        var belowDeck = new CsArena.Pos(1, 79, 0);
        check(
                !CsArena.walkStepClear(Set.of(new CsArena.Pos(1, 81, 0)), from, belowDeck),
                "a clear foundation cavity cannot be reached through the deck above it");
        check(
                CsArena.walkStepClear(Set.of(), from, belowDeck),
                "an unobstructed drop remains traversable");
        var upstairs = new CsArena.Pos(1, 83, 0);
        check(CsArena.walkStepClear(Set.of(), from, upstairs), "an open step remains traversable");
        check(
                !CsArena.walkStepClear(Set.of(new CsArena.Pos(0, 84, 0)), from, upstairs),
                "ascending requires clearance above the starting player");
    }

    private static void importedMap() throws Exception {
        var bp = CsArena.Blueprint.load();
        check(
                bp.cells().size() > 190000 && bp.palette().size() > 700,
                "full imported architecture, not a generated substitute");
        Map<CsArena.Pos, String> blocks = new HashMap<>();
        for (var cell : bp.cells()) blocks.put(cell.pos(), bp.palette().get(cell.state()));
        for (int side = 0; side < 2; side++) {
            var points = CsArena.spawnPoints(side);
            check(points.size() == (side == 0 ? 15 : 5), "CS2-style T and CT spawn pools");
            for (var point : points) {
                int x = (int) Math.floor(point.x()),
                        y = (int) Math.ceil(point.y()),
                        z = (int) Math.floor(point.z());
                check(
                        point.y() == (side == 0 ? 82 : 75.5),
                        "T starts on the street and CT in the intended underpass below Short");
                if (side == 1) {
                    check(
                            blocks.containsKey(new CsArena.Pos(x, 80, z)),
                            "the CT spawn has the original underpass roof above it");
                    check(point.x() >= 102.5 && point.x() <= 103.5 && point.yaw() == 90,
                            "CT starts inside the recess facing out, not in the through-passage");
                    check(blocks.get(new CsArena.Pos(x, 75, z)).contains("type=bottom"),
                            "CT feet sit exactly on the original lower slab surface");
                    check(
                            !CsArena.buyPosition(side, point.x(), 82, point.z()),
                            "Short above CT spawn is outside the CT buy zone");
                }
                check(
                        !blocks.containsKey(new CsArena.Pos(x, y, z))
                                && !blocks.containsKey(new CsArena.Pos(x, y + 1, z)),
                        "every spawn in both full pools has headroom in the imported map");
                check(
                        blocks.containsKey(new CsArena.Pos(x, y - 1, z)),
                        "every spawn has original floor support");
                check(
                        CsArena.buyPosition(side, point.x(), point.y(), point.z())
                                && !CsArena.buyPosition(1 - side, point.x(), point.y(), point.z()),
                        "all possible round starts belong to only their own buy area");
                for (var other : points)
                    if (other != point)
                        check(
                                Math.hypot(point.x() - other.x(), point.z() - other.z()) >= .7,
                                "randomly selected spawn slots cannot overlap player hitboxes");
            }
        }
        check(
                !CsArena.buyPosition(0, .5, 78, 50.5) && !CsArena.buyPosition(1, 93.5, 75, 39.5),
                "old out-of-spawn road/mid positions no longer extend the buy areas");
        Set<CsArena.Spawn> teamSpawns = new HashSet<>(CsArena.spawnPoints(0));
        teamSpawns.addAll(CsArena.spawnPoints(1));
        for (var point : CsArena.deathmatchSpawns()) {
            int x = (int) Math.floor(point.x()),
                    y = (int) Math.ceil(point.y()),
                    z = (int) Math.floor(point.z());
            check(
                    y >= 79 || CsArena.spawnPoints(1).contains(point),
                    "only curated CT underpass starts may bypass the foundation-height filter");
            if (!teamSpawns.contains(point))
                check(
                        blocks.keySet().stream()
                                .noneMatch(block ->
                                        block.x() == x && block.z() == z && block.y() >= y),
                        "distributed respawns are above the imported deck, never below it");
            check(
                    !blocks.containsKey(new CsArena.Pos(x, y, z))
                            && !blocks.containsKey(new CsArena.Pos(x, y + 1, z))
                            && blocks.containsKey(new CsArena.Pos(x, y - 1, z)),
                    "every distributed deathmatch candidate has two clear body blocks and an"
                            + " imported floor");
        }
        check(
                blocks.get(new CsArena.Pos(103, 81, 88)).startsWith("minecraft:red_concrete"),
                "A marker matches imported raised platform");
        check(
                blocks.get(new CsArena.Pos(114, 78, 6)).startsWith("minecraft:blue_concrete"),
                "B marker matches imported platform");
        check(
                bp.palette().stream()
                        .noneMatch(
                                s -> s.contains("command_block") || s.contains("structure_block")),
                "no executable source world blocks");
        try (var in = CsCheck.class.getResourceAsStream("/maps/cs/radar.png")) {
            var image = ImageIO.read(Objects.requireNonNull(in));
            check(image.getWidth() == 128 && image.getHeight() == 128, "native map radar size");
        }
    }

    private static void bombAudioGeometry() {
        Location a = new Location(null, 104, 82.1, 89);
        Location b = new Location(null, 114, 79.1, 7);
        Location ctSpawn = new Location(null, 95.5, 76.62, 63.5);
        check(
                CsBombAudio.audible(a, ctSpawn, CsBombAudio.Cue.PLANT_START)
                        && CsBombAudio.audible(a, ctSpawn, CsBombAudio.Cue.DEFUSE_START),
                "A plant and fake-defuse starts can be heard from the covered CT spawn");
        check(
                !CsBombAudio.audible(b, ctSpawn, CsBombAudio.Cue.PLANT_KEYS)
                        && CsBombAudio.audible(b, ctSpawn, CsBombAudio.Cue.DEFUSE_START),
                "the CT rotation is outside B keypad range but inside defuse range");
        check(
                !CsBombAudio.audible(a, b, CsBombAudio.Cue.DEFUSE_START)
                        && !CsBombAudio.audible(a, b, CsBombAudio.Cue.BEEP)
                        && CsBombAudio.audible(a, b, CsBombAudio.Cue.PLANTED),
                "the other bombsite cannot hear taps or ticking, only the longer plant-finish cue");
        for (var cue : CsBombAudio.Cue.values()) {
            check(
                    CsBombAudio.audible(a, a.clone().add(cue.radius - .01, 0, 0), cue)
                            && !CsBombAudio.audible(a, a.clone().add(cue.radius + .01, 0, 0), cue),
                    "each cue has an explicit bounded audible range");
            check(
                    !CsBombAudio.audible(a, a.clone().add(0, cue.radius + 1, 0), cue),
                    "height counts toward the audible range");
        }
    }

    private static void smokeGeometry() {
        Location a = new Location(null, -10, 1, 0),
                b = new Location(null, 10, 1, 0),
                center = new Location(null, 0, 1, 0);
        check(CsGrenades.intersects(a, b, center, 3.7), "smoke between players obscures sight");
        check(
                !CsGrenades.intersects(a, b, new Location(null, 0, 10, 0), 3.7),
                "smoke on another level does not obscure");
        check(
                CsGrenades.intersects(center, center, center, 3.7),
                "inside smoke handles zero-length sightline");
    }

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
