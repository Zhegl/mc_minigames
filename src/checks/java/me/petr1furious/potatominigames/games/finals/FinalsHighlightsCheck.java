package me.petr1furious.potatominigames.games.finals;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Location;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

/** Server-independent objective and teammate visibility regressions. */
public final class FinalsHighlightsCheck {
    private static Method markers;
    private static int checks;

    public static void main(String[] args) throws Exception {
        Class<?> highlights;
        try {
            highlights =
                    Class.forName("me.petr1furious.potatominigames.games.finals.FinalsHighlights");
        } catch (ClassNotFoundException missing) {
            throw new AssertionError(
                    "Finals must provide per-viewer objective and teammate highlight descriptors",
                    missing);
        }
        markers =
                highlights.getDeclaredMethod(
                        "markers",
                        FinalsMapArena.class,
                        FinalsState.Phase.class,
                        int.class,
                        Player.class,
                        Collection.class,
                        Map.class,
                        Function.class);
        markers.setAccessible(true);
        phaseVisibility();
        viewerAndTeammateVisibility();
        movingGeometryAndSourceSafety();
        fractionalCashoutOutlineAlignment();
        timedObjectiveLabels();
        downedTeammateLabels();
        distantProjection();
        System.out.println("FinalsHighlightsCheck: " + checks + " checks passed");
    }

    private static void distantProjection() throws Exception {
        Fixture fixture = new Fixture();
        fixture.arena.vault = new Location(fixture.arena.world, 1001, 90, 2002);
        Object marker = keyed(fixture.view(null)).get("vault");
        Method placement;
        try {
            placement = markers.getDeclaringClass().getDeclaredMethod("placement", Player.class, marker.getClass());
        } catch (NoSuchMethodException missing) {
            throw new AssertionError("Distant highlights must project into the viewer's rendered chunks", missing);
        }
        placement.setAccessible(true);
        Location source = fixture.arena.vault.clone();
        Location eye = fixture.viewer.getEyeLocation();
        Object distant = placement.invoke(null, fixture.viewer, marker);
        Location label = (Location) value(distant, "label");
        require((boolean) value(distant, "projected"), "Distant objective uses a projected marker");
        require(Math.abs(eye.distance(label) - 24) < .000001, "Projection stays 24 blocks from the eye");
        require(label.toVector().subtract(eye.toVector()).normalize().distance(
                        source.clone().add(0, 2, 0).toVector().subtract(eye.toVector()).normalize()) < .000001,
                "Projected label points toward the actual objective, including vertical direction");
        Component text = (Component) value(distant, "text");
        require(plain(text, Locale.ENGLISH).contains("Vault")
                        && plain(text, Locale.ENGLISH).contains("2236 blocks"),
                "Distant label retains its objective and reports true distance, not projected distance");
        require(plain(text, Locale.forLanguageTag("ru")).contains("2236 блоков"),
                "Distant marker distance is localized");
        Location block = (Location) value(distant, "block");
        require(block.getX() == label.getX() && block.getZ() == label.getZ()
                        && Math.abs(label.getY() - block.getY() - .5) < .000001,
                "Compact objective proxy sits below the projected label");
        require(fixture.arena.vault.equals(source) && fixture.viewer.getEyeLocation().equals(eye),
                "Projection never moves the real objective or viewer");
        Method separate = markers.getDeclaringClass().getDeclaredMethod("separate", distant.getClass(), List.class);
        separate.setAccessible(true);
        List<Location> occupied = new java.util.ArrayList<>();
        separate.invoke(null, distant, occupied);
        Object sameBearing = placement.invoke(null, fixture.viewer, marker);
        separate.invoke(null, sameBearing, occupied);
        require(((Location) value(sameBearing, "label")).distance(label) >= .75,
                "Markers with the same distant bearing get separate readable label positions");
        require(((Location) value(sameBearing, "block")).distance(block) >= .75,
                "Compact objective proxies follow their separated labels");

        fixture.clientDistance = 2;
        Object low = placement.invoke(null, fixture.viewer, marker);
        require(Math.abs(eye.distance((Location) value(low, "label")) - 16) < .000001,
                "Two-chunk clients get a nearer safe projection");
        fixture.clientDistance = 10;
        fixture.sendDistance = 1;
        low = placement.invoke(null, fixture.viewer, marker);
        require(Math.abs(eye.distance((Location) value(low, "label")) - 8) < .000001,
                "A low server send distance also constrains the marker");
        fixture.sendDistance = 10;

        fixture.arena.vault = eye.clone().add(24, -2, 0);
        marker = keyed(fixture.view(null)).get("vault");
        Object edge = placement.invoke(null, fixture.viewer, marker);
        require(!(boolean) value(edge, "projected"), "An objective at the exact projection boundary stays real");
        fixture.arena.vault.add(.001, 0, 0);
        marker = keyed(fixture.view(null)).get("vault");
        require((boolean) value(placement.invoke(null, fixture.viewer, marker), "projected"),
                "An objective just outside the boundary is projected");

        fixture.locations.get(fixture.viewer.getUniqueId()).setX(15.75);
        fixture.locations.get(fixture.viewer.getUniqueId()).setZ(15.75);
        long viewerChunk = Chunk.getChunkKey(fixture.viewer.getLocation());
        fixture.sent = key -> key == viewerChunk;
        fixture.arena.vault = new Location(fixture.arena.world, 1000, 65, 15.75);
        marker = keyed(fixture.view(null)).get("vault");
        Object waiting = placement.invoke(null, fixture.viewer, marker);
        require(Chunk.getChunkKey((Location) value(waiting, "label")) == viewerChunk,
                "A marker never depends on a chunk the client has not received");
        fixture.sent = ignored -> false;
        waiting = placement.invoke(null, fixture.viewer, marker);
        require(value(waiting, "label").equals(fixture.viewer.getEyeLocation()),
                "Initial chunk delivery falls back to the viewer until nearby chunks arrive");
        fixture.sent = ignored -> true;

        fixture.arena.vault = fixture.viewer.getLocation().clone().add(3, 0, 4);
        marker = keyed(fixture.view(null)).get("vault");
        Object near = placement.invoke(null, fixture.viewer, marker);
        require(!(boolean) value(near, "projected")
                        && value(near, "label").equals(fixture.arena.vault.clone().add(0, 2, 0)),
                "Nearby objective labels retain their real world position");
        require(plain((Component) value(near, "text"), Locale.ENGLISH).equals("◆ Vault"),
                "Nearby labels retain their existing compact text");
        fixture.arena.stations[0] = new Location(fixture.arena.world, 1000, 150, 150);
        marker = keyed(fixture.view(FinalsState.Phase.CASHOUT)).get("cashout-0");
        Object beforeFall = placement.invoke(null, fixture.viewer, marker);
        fixture.arena.stations[0].subtract(0, 70, 0);
        marker = keyed(fixture.view(FinalsState.Phase.CASHOUT)).get("cashout-0");
        Object afterFall = placement.invoke(null, fixture.viewer, marker);
        require(((Location) value(afterFall, "label")).getY() < ((Location) value(beforeFall, "label")).getY(),
                "Distant cashout projection follows its falling direction");
        fixture.arena.stations[1] = new Location(fixture.arena.world, -2000, 80, 150);
        Map<String, Object> stations = keyed(fixture.view(FinalsState.Phase.CARRIED));
        for (int station = 0; station < 2; station++) {
            Object projected = placement.invoke(null, fixture.viewer, stations.get("cashout-" + station));
            Component caption = (Component) value(projected, "text");
            String letter = station == 0 ? "A" : "B";
            require(plain(caption, Locale.ENGLISH).startsWith(letter + "\n"),
                    "Distant cashouts display their identifying letter on its own line");
            require(plain(caption, Locale.forLanguageTag("ru")).startsWith(letter + "\n")
                            && plain(caption, Locale.forLanguageTag("ru")).contains("блоков"),
                    "The station letter is retained alongside localized distance text");
            require(fixture.viewer.getEyeLocation().distance((Location) value(projected, "label")) <= 8.000001,
                    "Far cashout letters stay close enough to read regardless of objective distance");
        }
    }

    private static void timedObjectiveLabels() throws Exception {
        Method timed;
        try {
            timed = markers.getDeclaringClass().getDeclaredMethod("markers", FinalsMapArena.class,
                    FinalsState.Phase.class, int.class, long.class, long.class, Player.class,
                    Collection.class, Map.class, Function.class);
        } catch (NoSuchMethodException missing) {
            throw new AssertionError("Objective labels need remaining-time descriptors", missing);
        }
        timed.setAccessible(true);
        Fixture f = new Fixture();
        Function<Player, Location> positions = player -> f.locations.get(player.getUniqueId());
        var opening = keyed((List<?>) timed.invoke(null, f.arena, FinalsState.Phase.VAULT_OPENING,
                -1, 4L, 40L, f.viewer, f.players, f.teams, positions));
        Component vault = (Component) value(opening.get("vault"), "label");
        String english = plain(vault, Locale.ENGLISH);
        require(english.contains("Vault") && english.contains("4"), "Opening vault labels show their remaining seconds");
        require(plain(vault, Locale.forLanguageTag("ru")).contains("4")
                        && plain(vault, Locale.forLanguageTag("ru")).contains("Хранилище"),
                "Vault names and timers are rendered in Russian for Russian viewers");
        var available = keyed((List<?>) timed.invoke(null, f.arena, FinalsState.Phase.CASHBOX_AVAILABLE,
                -1, 5L, 40L, f.viewer, f.players, f.teams, positions));
        for (String key : List.of("cashout-0", "cashout-1"))
            require(plain((Component) value(available.get(key), "label"), Locale.ENGLISH).contains("40"),
                    "Both available cashout labels show the configured cashout duration");
        var active = keyed((List<?>) timed.invoke(null, f.arena, FinalsState.Phase.CASHOUT,
                1, 5L, 0L, f.viewer, f.players, f.teams, positions));
        require(active.keySet().equals(Set.of("cashout-1"))
                        && plain((Component) value(active.get("cashout-1"), "label"), Locale.ENGLISH).contains("0"),
                "The active station keeps the zero-second endpoint without revealing inactive objectives");
    }

    private static String plain(Component text, Locale locale) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(me.petr1furious.potatominigames.i18n.I18n.render(text, locale));
    }

    private static void downedTeammateLabels() throws Exception {
        Method downedMarkers = markers.getDeclaringClass().getDeclaredMethod("markers",
                FinalsMapArena.class, FinalsState.Phase.class, int.class, long.class, long.class,
                Player.class, Collection.class, Map.class, Function.class, Predicate.class);
        downedMarkers.setAccessible(true);
        Fixture f = new Fixture();
        Player ally = f.player("fallen-ally", f.arena.world, 3, 4);
        Player enemy = f.player("fallen-enemy", f.arena.world, 5, 6);
        f.players = List.of(f.viewer, ally, enemy);
        f.teams.put(ally.getUniqueId(), 0);
        f.teams.put(enemy.getUniqueId(), 1);
        Location statue = new Location(f.arena.world, 30.25, 66.5, 40.75);
        Location source = statue.clone();
        Function<Player, Location> positions = player -> {
            require(player == ally, "Enemy and self positions are never queried for allied downed markers");
            return statue;
        };
        Set<UUID> downed = new HashSet<>(Set.of(ally.getUniqueId(), enemy.getUniqueId()));
        Predicate<Player> isDowned = player -> {
            require(player == ally, "Enemy and self downed states are never queried for private labels");
            return downed.contains(player.getUniqueId());
        };
        String allyKey = "teammate-" + ally.getUniqueId();
        var view = keyed((List<?>) downedMarkers.invoke(null, f.arena, null, -1, -1L, -1L,
                f.viewer, f.players, f.teams, positions, isDowned));
        require(view.keySet().equals(Set.of("vault", allyKey)),
                "Downed status never reveals an enemy or self marker");
        Object marker = view.get(allyKey);
        Component label = (Component) value(marker, "label");
        require(plain(label, Locale.ENGLISH).equals("✚ fallen-ally • Downed"),
                "English downed labels contain only the teammate name and localized status, without health");
        require(plain(label, Locale.forLanguageTag("ru")).equals("✚ fallen-ally • Повержен"),
                "Russian downed labels localize the status without exposing health");
        require(value(marker, "location").equals(source) && statue.equals(source),
                "Downed markers use the supplied statue position without mutating it");
        statue.add(2.5, -.5, -3.25);
        var moved = keyed((List<?>) downedMarkers.invoke(null, f.arena, null, -1, -1L, -1L,
                f.viewer, f.players, f.teams, positions, isDowned));
        require(value(moved.get(allyKey), "location").equals(statue),
                "Downed markers follow a carried or thrown statue instead of the spectator camera");
        require(value(marker, "location").equals(source),
                "An earlier marker retains an independent snapshot of the statue position");
        downed.clear();
        var revived = keyed((List<?>) downedMarkers.invoke(null, f.arena, null, -1, -1L, -1L,
                f.viewer, f.players, f.teams, positions, isDowned));
        require(plain((Component) value(revived.get(allyKey), "label"), Locale.ENGLISH)
                        .equals("◆ fallen-ally"),
                "Revival changes the existing teammate key back to the ordinary name-only label");
        f.players = List.of(f.viewer, enemy);
        require(keyed((List<?>) downedMarkers.invoke(null, f.arena, null, -1, -1L, -1L,
                        f.viewer, f.players, f.teams, positions, isDowned)).keySet().equals(Set.of("vault")),
                "Removing a downed contestant removes their descriptor while keeping enemies private");
    }

    private static void phaseVisibility() throws Exception {
        Fixture fixture = new Fixture();
        for (FinalsState.Phase phase : FinalsState.Phase.values()) {
            Map<String, Object> view = keyed(fixture.view(phase));
            if (phase == FinalsState.Phase.FINISHED) {
                require(view.isEmpty(), "Finished matches remove every highlight");
                continue;
            }
            boolean vault =
                    phase == FinalsState.Phase.VAULT_CLOSED
                            || phase == FinalsState.Phase.VAULT_OPENING;
            require(view.containsKey("vault") == vault, "Vault visibility follows phase " + phase);
            boolean box = phase == FinalsState.Phase.CASHBOX_AVAILABLE
                    || phase == FinalsState.Phase.CARRIED;
            boolean depositing = phase == FinalsState.Phase.CASHOUT;
            require(view.containsKey("cashout-0") == (box || depositing),
                    "Cashout A follows box availability or its active deposit: " + phase);
            require(view.containsKey("cashout-1") == box,
                    "Cashout B appears only while a cashbox can be deposited: " + phase);
            require(
                    view.size() == (vault || depositing ? 1 : 2),
                    "Only active objectives appear during " + phase);
            for (Object marker : view.values()) {
                require(
                        value(marker, "block") instanceof Material,
                        "Objective descriptors provide a block material");
                appearance(marker);
            }
        }
        require(
                keyed(fixture.view(null))
                        .keySet()
                        .equals(Set.of("vault")),
                "Countdown highlights only the vault before a cashbox exists");
        fixture.activeStation = 1;
        require(keyed(fixture.view(FinalsState.Phase.CASHOUT)).keySet().equals(Set.of("cashout-1")),
                "The highlighted depositing station follows the active cashout index");
        fixture.activeStation = -1;
        require(fixture.view(FinalsState.Phase.CASHOUT).isEmpty(),
                "A missing active deposit never reveals an unrelated cashout station");
    }

    private static void viewerAndTeammateVisibility() throws Exception {
        Fixture fixture = new Fixture();
        Player ally = fixture.player("ally", fixture.arena.world, 3, 4);
        Player enemy = fixture.player("enemy", fixture.arena.world, 5, 6);
        Player dead = fixture.player("dead", fixture.arena.world, 7, 8);
        Player elsewhere = fixture.player("elsewhere", world("elsewhere"), 9, 10);
        Player departed = fixture.player("departed", fixture.arena.world, 11, 12);
        fixture.players = List.of(fixture.viewer, ally, enemy, dead, elsewhere);
        for (Player teammate : List.of(ally, dead, elsewhere, departed)) {
            fixture.teams.put(teammate.getUniqueId(), 0);
            fixture.active.add(teammate.getUniqueId());
        }
        fixture.teams.put(enemy.getUniqueId(), 1);
        fixture.active.add(enemy.getUniqueId());
        fixture.active.remove(dead.getUniqueId());
        Map<String, Object> view = keyed(fixture.view(null));
        String allyKey = "teammate-" + ally.getUniqueId();
        String deadKey = "teammate-" + dead.getUniqueId();
        require(
                view.keySet().equals(Set.of("vault", allyKey, deadKey)),
                "Teammates include dead allies but exclude self, enemies, other-world and departed players");
        Object marker = view.get(allyKey);
        require(value(marker, "block") == null, "Teammates use labels without objective blocks");
        appearance(marker);
        Location location = (Location) value(marker, "location");
        require(
                location.getWorld() == fixture.arena.world,
                "Ally label belongs to the arena world");
        fixture.teams.put(fixture.viewer.getUniqueId(), 1);
        require(
                keyed(fixture.view(null))
                        .keySet()
                        .equals(
                                Set.of(
                                        "vault",
                                        "teammate-" + enemy.getUniqueId())),
                "The other team receives only its own teammates");
        fixture.teams.put(fixture.viewer.getUniqueId(), 0);
        fixture.active.remove(ally.getUniqueId());
        Location deathLocation = new Location(fixture.arena.world, 45, 65, 46);
        fixture.markerLocations.put(ally.getUniqueId(), deathLocation);
        fixture.locations.get(ally.getUniqueId()).add(0, 10, 0);
        require(
                keyed(fixture.view(null)).containsKey(allyKey),
                "Downed teammates remain visible during respawn");
        require(value(keyed(fixture.view(null)).get(allyKey), "location").equals(deathLocation),
                "Respawning allies use the supplied death position rather than the spectator camera");
        for (FinalsState.Phase phase : FinalsState.Phase.values()) {
            if (phase != FinalsState.Phase.FINISHED)
                require(keyed(fixture.view(phase)).containsKey(deadKey),
                        "Dead ally markers remain available throughout phase " + phase);
        }
        fixture.active.add(ally.getUniqueId());
        fixture.players = List.of(fixture.viewer);
        require(
                !keyed(fixture.view(null)).containsKey(allyKey),
                "Removing a contestant clears their marker even while roster entry remains");
        fixture.teams.remove(fixture.viewer.getUniqueId());
        require(fixture.view(null).isEmpty(), "Viewers outside the team roster receive no markers");
        fixture.teams.put(fixture.viewer.getUniqueId(), 0);
        fixture.locations.get(fixture.viewer.getUniqueId()).setWorld(world("spectator-world"));
        require(fixture.view(null).isEmpty(), "Viewers outside the arena world receive no markers");
    }

    private static void movingGeometryAndSourceSafety() throws Exception {
        Fixture fixture = new Fixture();
        Player ally = fixture.player("moving-ally", fixture.arena.world, 13, 14);
        fixture.players = List.of(fixture.viewer, ally);
        fixture.teams.put(ally.getUniqueId(), 0);
        fixture.active.add(ally.getUniqueId());
        Location vaultSource = fixture.arena.vault.clone();
        Location firstSource = fixture.arena.stations[0].clone();
        Location secondSource = fixture.arena.stations[1].clone();
        Location allySource = fixture.locations.get(ally.getUniqueId()).clone();
        Map<String, Object> before = allObjectives(fixture);
        require(fixture.arena.vault.equals(vaultSource), "Vault source location is never mutated");
        require(
                fixture.arena.stations[0].equals(firstSource)
                        && fixture.arena.stations[1].equals(secondSource),
                "Cashout source locations are never mutated");
        require(
                fixture.locations.get(ally.getUniqueId()).equals(allySource),
                "Teammate source location is never mutated");
        fixture.arena.nextVault();
        fixture.arena.nextCashout(0);
        fixture.locations.get(ally.getUniqueId()).add(4, 2, 6);
        Map<String, Object> after = allObjectives(fixture);
        moved(before, after, "vault", 20, 0, 30);
        moved(before, after, "cashout-0", -15, 0, 25);
        moved(before, after, "cashout-1", 0, 0, 0);
        moved(before, after, "teammate-" + ally.getUniqueId(), 4, 2, 6);
        require(after.size() == 4, "Inactive candidate locations never receive highlights");
        fixture.arena.nextCashout(1);
        Map<String, Object> secondRotation = allObjectives(fixture);
        moved(after, secondRotation, "cashout-0", 0, 0, 0);
        moved(after, secondRotation, "cashout-1", -15, 0, 25);
        Location exposed = (Location) value(after.get("vault"), "location");
        exposed.add(100, 100, 100);
        require(
                fixture.arena.vault.equals(vaultSource.clone().add(20, 0, 30)),
                "A returned marker location cannot mutate the arena geometry");
    }

    private static void fractionalCashoutOutlineAlignment() throws Exception {
        Fixture fixture = new Fixture();
        Location input = new Location(fixture.arena.world, 8.2, 65.5, -4.1, 37, 80);
        Location unchanged = input.clone();
        fixture.arena.stations[0] = input;
        fixture.arena.vault = input;
        Object cashout = keyed(fixture.view(FinalsState.Phase.CARRIED)).get("cashout-0");
        Method blockPosition = cashout.getClass().getEnclosingClass()
                .getDeclaredMethod("blockPosition", cashout.getClass());
        blockPosition.setAccessible(true);
        Location outline = (Location) blockPosition.invoke(null, cashout);
        require(Math.abs(outline.getX() - 7.7) < 0.000001
                        && Math.abs(outline.getY() - 65.5) < 0.000001
                        && Math.abs(outline.getZ() + 4.6) < 0.000001,
                "Cashout outlines use the exact body corner at center minus half a block");
        require(outline.getYaw() == 0 && outline.getPitch() == 0,
                "Saved player facing cannot rotate the cashout outline model");
        Object vault = keyed(fixture.view(null)).get("vault");
        Location vaultOutline = (Location) blockPosition.invoke(null, vault);
        require(vaultOutline.getX() == 8 && vaultOutline.getY() == 65.5 && vaultOutline.getZ() == -5,
                "Vault outlines retain block-cell anchoring independently of cashout geometry");
        require(input.equals(unchanged), "Outline anchoring never mutates saved objective position or facing");
    }

    private static Map<String, Object> allObjectives(Fixture fixture) throws Exception {
        Map<String, Object> result = keyed(fixture.view(null));
        result.putAll(keyed(fixture.view(FinalsState.Phase.CARRIED)));
        return result;
    }

    private static void moved(
            Map<String, Object> before,
            Map<String, Object> after,
            String key,
            double dx,
            double dy,
            double dz)
            throws Exception {
        Location first = (Location) value(before.get(key), "location");
        Location second = (Location) value(after.get(key), "location");
        require(
                Math.abs(second.getX() - first.getX() - dx) < 0.000001
                        && Math.abs(second.getY() - first.getY() - dy) < 0.000001
                        && Math.abs(second.getZ() - first.getZ() - dz) < 0.000001,
                "Fresh descriptors follow current geometry for " + key);
    }

    private static void appearance(Object marker) throws Exception {
        require(
                value(marker, "label") instanceof Component
                        && !value(marker, "label").equals(Component.empty()),
                "Highlight labels provide nonempty Adventure components");
        require(value(marker, "color") instanceof NamedTextColor, "Highlights expose team colors");
        require(
                value(marker, "location") instanceof Location,
                "Highlights expose a world location");
    }

    private static Map<String, Object> keyed(List<?> view) throws Exception {
        Map<String, Object> result = new HashMap<>();
        for (Object marker : view) {
            String key = (String) value(marker, "key");
            require(result.put(key, marker) == null, "Each marker has a unique stable key");
        }
        return result;
    }

    private static Object value(Object marker, String name) throws Exception {
        Method accessor = marker.getClass().getDeclaredMethod(name);
        accessor.setAccessible(true);
        return accessor.invoke(marker);
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static World world(String name) {
        return (World)
                Proxy.newProxyInstance(
                        World.class.getClassLoader(),
                        new Class<?>[] {World.class},
                        (proxy, method, args) ->
                                switch (method.getName()) {
                                    case "getName", "toString" -> name;
                                    case "getUID" ->
                                            UUID.nameUUIDFromBytes(
                                                    name.getBytes(
                                                            java.nio.charset.StandardCharsets
                                                                    .UTF_8));
                                    case "equals" -> proxy == args[0];
                                    case "hashCode" -> System.identityHashCode(proxy);
                                    default ->
                                            throw new AssertionError(
                                                    "Unexpected world call: " + method.getName());
                                });
    }

    private static final class Fixture {
        final Arena arena = new Arena();
        final Map<UUID, Location> locations = new HashMap<>();
        final Map<UUID, Location> markerLocations = new HashMap<>();
        final Map<UUID, Integer> teams = new HashMap<>();
        final Set<UUID> active = new HashSet<>();
        final Player viewer = player("viewer", arena.world, 1, 2);
        Collection<Player> players = List.of(viewer);
        int activeStation;
        int clientDistance = 10;
        int sendDistance = 10;
        Predicate<Long> sent = ignored -> true;

        Fixture() {
            teams.put(viewer.getUniqueId(), 0);
            active.add(viewer.getUniqueId());
        }

        Player player(String name, World world, double x, double z) {
            UUID id =
                    UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            locations.put(id, new Location(world, x, 70, z));
            return (Player)
                    Proxy.newProxyInstance(
                            Player.class.getClassLoader(),
                            new Class<?>[] {Player.class},
                            (proxy, method, args) ->
                                    switch (method.getName()) {
                                        case "getUniqueId" -> id;
                                        case "getName", "toString" -> name;
                                        case "displayName", "name" -> Component.text(name);
                                        case "getLocation" -> locations.get(id);
                                        case "getEyeLocation" ->
                                                locations.get(id).clone().add(0, 1.62, 0);
                                        case "getClientViewDistance" -> clientDistance;
                                        case "getSendViewDistance" -> sendDistance;
                                        case "isChunkSent" -> sent.test((Long) args[0]);
                                        case "getWorld" -> locations.get(id).getWorld();
                                        case "isOnline", "isValid" -> true;
                                        case "isDead" -> !active.contains(id);
                                        case "locale" -> Locale.ENGLISH;
                                        case "getLocale" -> "en_us";
                                        case "equals" -> proxy == args[0];
                                        case "hashCode" -> id.hashCode();
                                        default ->
                                                throw new AssertionError(
                                                        "Unexpected player call: "
                                                                + method.getName());
                                    });
        }

        List<?> view(FinalsState.Phase phase) throws Exception {
            try {
                return (List<?>)
                        markers.invoke(
                                null,
                                arena,
                                phase,
                                activeStation,
                                viewer,
                                players,
                                teams,
                                (Function<Player, Location>) player -> markerLocations.getOrDefault(
                                        player.getUniqueId(), locations.get(player.getUniqueId())));
            } catch (InvocationTargetException failure) {
                if (failure.getCause() instanceof Error error) throw error;
                if (failure.getCause() instanceof Exception exception) throw exception;
                throw failure;
            }
        }
    }

    /** Returns live source Locations intentionally, so accidental add() mutations are detected. */
    private static final class Arena implements FinalsMapArena {
        final World world = FinalsHighlightsCheck.world("arena");
        Location vault = new Location(world, 10, 65, 20);
        final Location[] stations = {
            new Location(world, -20, 65, 15), new Location(world, 25, 65, -15)
        };

        public World world() {
            return world;
        }

        public void build() {}

        public Location center() {
            return new Location(world, 0, 65, 0);
        }

        public Location spawn(int team, int memberIndex) {
            return center();
        }

        public Location vault() {
            return vault;
        }

        public List<Location> vaultLocations() {
            return List.of(vault, new Location(world, 100, 65, 100));
        }

        public void nextVault() {
            vault = vault.clone().add(20, 0, 30);
        }

        public Location station(int station) {
            return stations[station];
        }

        public List<Location> cashoutLocations() {
            return List.of(stations[0], stations[1], new Location(world, -100, 65, -100));
        }

        public void nextCashout(int station) {
            stations[station] = stations[station].clone().add(-15, 0, 25);
        }

        public boolean contains(Location location) {
            return location.getWorld() == world;
        }

        public boolean isCover(Block block) {
            return false;
        }

        public int stationAt(Block block) {
            return -1;
        }

        public boolean isVault(Block block) {
            return false;
        }

        public double borderSize() {
            return 256;
        }
    }
}
