import java.lang.reflect.*;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import me.petr1furious.potatominigames.games.finals.*;

/** Native display metadata/lifecycle checks; only player transport is simulated. */
public final class FinalsHighlightsSmoke {
    private int assertions;

    public static int run(JavaPlugin smoke, JavaPlugin main) throws Exception {
        return new FinalsHighlightsSmoke().check(smoke, main);
    }

    private int check(JavaPlugin smoke, JavaPlugin main) throws Exception {
        World world = Bukkit.getWorlds().getFirst();
        FixtureArena arena = new FixtureArena(world);
        for (int x = 29; x <= 33; x++) for (int z = 30; z <= 33; z++)
            world.getChunkAt(x, z).load();
        FakePlayer alice = new FakePlayer(world, "HighlightAlice", 500, 500);
        FakePlayer bob = new FakePlayer(world, "HighlightBob", 505, 500);
        bob.locale = Locale.forLanguageTag("ru");
        FakePlayer eve = new FakePlayer(world, "HighlightEve", 500, 505);
        FakePlayer frank = new FakePlayer(world, "HighlightFrank", 505, 505);
        FakePlayer dead = new FakePlayer(world, "HighlightDead", 510, 505);
        dead.dead = true;
        FakePlayer outsider = new FakePlayer(world, "HighlightOutsider", 510, 500);
        List<FakePlayer> fixtures = List.of(alice, bob, eve, frank, dead, outsider);
        List<Player> players = fixtures.stream().map(fake -> fake.player).toList();
        Map<UUID, Integer> teams = Map.of(alice.id, 0, bob.id, 0, dead.id, 0, eve.id, 1, frank.id, 1);
        Map<UUID, Location> deathLocations = new HashMap<>();
        Function<Player, Location> positions = player -> deathLocations.getOrDefault(
                player.getUniqueId(), player.getLocation());
        Class<?> type = Class.forName("me.petr1furious.potatominigames.games.finals.FinalsHighlights", true,
                main.getClass().getClassLoader());
        Constructor<?> constructor = type.getDeclaredConstructor(JavaPlugin.class);
        constructor.setAccessible(true);
        Object highlights = constructor.newInstance(main);
        Method update = method(type, "update", FinalsMapArena.class, FinalsState.Phase.class,
                int.class, Collection.class, Map.class, Function.class);
        Method timed = method(type, "update", FinalsMapArena.class, FinalsState.Phase.class,
                int.class, long.class, long.class, Collection.class, Map.class, Function.class);
        Method downedUpdate = method(type, "update", FinalsMapArena.class, FinalsState.Phase.class,
                int.class, long.class, long.class, Collection.class, Map.class, Function.class,
                Predicate.class);
        Method remove = method(type, "remove", UUID.class);
        Method clear = method(type, "clear");
        SpawnObserver observer = new SpawnObserver();
        Bukkit.getPluginManager().registerEvents(observer, smoke);
        try {
            List<Location> sources = arena.sources();
            invoke(update, highlights, arena, null, -1, players, teams, positions);
            require(alice.active().size() == 4 && bob.active().size() == 4 && dead.active().size() == 4,
                    "Countdown gives red viewers one vault pair and both teammate labels");
            require(eve.active().size() == 3 && outsider.active().isEmpty(),
                    "Blue countdown has its single ally label; outsiders receive no markers");
            require(Collections.disjoint(alice.active(), bob.active())
                    && Collections.disjoint(alice.active(), eve.active()),
                    "Native displays belong exclusively to their individual viewer");
            require(alice.blocks().size() == 1 && alice.texts().size() == 3,
                    "Countdown contains no cashout displays or body-shaped proxy blocks");
            require(hasLabel(alice, "Vault") && !hasLabel(alice, "Cashout"),
                    "Only the vault is highlighted before a cashbox exists");
            require(hasLabel(bob, "Хранилище") && !hasLabel(bob, "Касса"),
                    "Countdown objectives render in the viewer's language");
            require(hasLabel(alice, bob.name) && hasLabel(alice, dead.name)
                    && !hasLabel(alice, alice.name) && !hasLabel(alice, eve.name)
                    && !hasLabel(alice, frank.name) && !hasLabel(alice, outsider.name),
                    "Private teammate labels retain all allies and never reveal enemies or self");
            require(hasColor(label(alice, bob.name).text(), NamedTextColor.RED)
                    && hasColor(label(eve, frank.name).text(), NamedTextColor.BLUE),
                    "Ally labels use their red or blue team color");
            for (FakePlayer viewer : fixtures) for (Display display : viewer.active()) {
                require(!display.isVisibleByDefault() && !display.isPersistent() && display.isInvulnerable(),
                        "Native markers are private, transient and invulnerable");
                Display.Brightness brightness = display.getBrightness();
                require(brightness != null && brightness.getBlockLight() == 15 && brightness.getSkyLight() == 15,
                        "Covered markers remain fully bright");
                SpawnMetadata initial = observer.spawned.get(display.getUniqueId());
                if (initial != null)
                    require(!initial.visible && !initial.persistent && initial.invulnerable,
                            "Privacy is applied before the native spawn event");
                if (display instanceof TextDisplay text)
                    require(text.isSeeThrough() && text.isShadowed()
                            && text.getBillboard() == Display.Billboard.CENTER,
                            "Labels remain readable through terrain and face the viewer");
                else if (display instanceof BlockDisplay block)
                    require(block.isGlowing() && block.getBlock().getMaterial() == Material.EMERALD_BLOCK
                            && Color.fromRGB(NamedTextColor.GREEN.value()).equals(block.getGlowColorOverride()),
                            "The vault has a real green native block outline");
            }
            require(sameLocations(sources, arena.sources()), "Rendering never mutates source geometry");
            Set<Display> first = allActive(fixtures);
            int spawned = observer.spawned.size();
            invoke(update, highlights, arena, null, -1, players, teams, positions);
            require(first.equals(allActive(fixtures)) && observer.spawned.size() == spawned,
                    "Unchanged refreshes reuse every native display");
            bob.locale = Locale.ENGLISH;
            invoke(update, highlights, arena, null, -1, players, teams, positions);
            require(first.equals(allActive(fixtures)) && hasLabel(bob, "Vault"),
                    "Language changes update labels without replacing native entities");
            TextDisplay vault = label(alice, "Vault"), ally = label(alice, bob.name);
            BlockDisplay vaultBody = alice.blocks().getFirst();
            Location oldVault = vault.getLocation(), oldBody = vaultBody.getLocation(), oldAlly = ally.getLocation();
            arena.nextVault();
            bob.location.add(3, 1, -2);
            invoke(update, highlights, arena, FinalsState.Phase.VAULT_OPENING, -1, players, teams, positions);
            moved(vault, oldVault, 8, 0, 4, "Vault labels follow objective rotation");
            moved(vaultBody, oldBody, 8, 0, 4, "Vault outline follows objective rotation");
            moved(ally, oldAlly, 3, 1, -2, "Ally labels follow movement during countdown and opening");
            bob.locale = Locale.forLanguageTag("ru");
            Set<Display> timedBefore = allActive(fixtures);
            invoke(timed, highlights, arena, FinalsState.Phase.VAULT_OPENING, -1, 17L, 40L, players, teams, positions);
            require(label(alice, "Vault") == vault && plain(vault).contains("17"),
                    "Native opening-vault text shows remaining seconds without replacing its display");
            require(hasLabel(bob, "Хранилище") && plain(label(bob, "Хранилище")).contains("17"),
                    "Russian native vault labels include the same remaining time");
            invoke(timed, highlights, arena, FinalsState.Phase.VAULT_OPENING, -1, 16L, 40L, players, teams, positions);
            require(plain(vault).contains("16") && timedBefore.equals(allActive(fixtures)),
                    "Countdown refreshes update native label text while reusing every marker entity");
            bob.locale = Locale.ENGLISH;
            invoke(update, highlights, arena, FinalsState.Phase.CASHBOX_AVAILABLE, -1, players, teams, positions);
            require(alice.active().size() == 6 && alice.blocks().size() == 2
                    && hasLabel(alice, "Cashout A") && hasLabel(alice, "Cashout B") && !hasLabel(alice, "Vault"),
                    "A loose cashbox reveals both cashouts and removes the vault highlight");
            require(!vault.isValid() && !vaultBody.isValid(), "Opening removes both native vault entities");
            Set<Display> bothCashouts = alice.active();
            invoke(timed, highlights, arena, FinalsState.Phase.CASHBOX_AVAILABLE, -1, 5L, 40L, players, teams, positions);
            require(plain(label(alice, "Cashout A")).contains("40")
                            && plain(label(alice, "Cashout B")).contains("40"),
                    "Both available cashouts show the configured duration above their native bodies");
            invoke(update, highlights, arena, FinalsState.Phase.CARRIED, -1, players, teams, positions);
            require(bothCashouts.equals(alice.active()), "Carrying preserves both cashout display pairs");
            TextDisplay cashoutA = label(alice, "Cashout A"), cashoutB = label(alice, "Cashout B");
            BlockDisplay bodyA = nearestBlock(alice, arena.station(0));
            Location oldA = cashoutA.getLocation(), oldABody = bodyA.getLocation();
            arena.nextCashout(0);
            invoke(update, highlights, arena, FinalsState.Phase.CARRIED, -1, players, teams, positions);
            moved(cashoutA, oldA, -6, 0, 8, "Cashout labels follow replacement candidates");
            moved(bodyA, oldABody, -6, 0, 8, "Cashout outlines follow replacement candidates");
            Location fractional = new Location(world, 508.2, 65.5, 495.9, 37, 80);
            Location fractionalBefore = fractional.clone();
            arena.stations[0] = fractional;
            invoke(update, highlights, arena, FinalsState.Phase.CARRIED, -1, players, teams, positions);
            close(bodyA.getLocation().getX(), 507.7,
                    "Native cashout outline retains the body's fractional X anchor");
            close(bodyA.getLocation().getY(), 65.5,
                    "Native cashout outline retains its exact falling height");
            close(bodyA.getLocation().getZ(), 495.4,
                    "Native cashout outline retains the body's fractional Z anchor");
            require(bodyA.getLocation().getYaw() == 0 && bodyA.getLocation().getPitch() == 0,
                    "Native cashout outline clears saved spawn yaw and pitch");
            var transform = bodyA.getTransformation();
            close(bodyA.getLocation().getX() + transform.getTranslation().x + transform.getScale().x / 2d,
                    fractional.getX(), "Native outlined model bounds stay centered on the actual cashout body");
            close(bodyA.getLocation().getZ() + transform.getTranslation().z + transform.getScale().z / 2d,
                    fractional.getZ(), "Native outlined model depth stays centered on the actual cashout body");
            require(fractional.equals(fractionalBefore),
                    "Native highlight updates preserve fractional objective location and saved facing");
            invoke(update, highlights, arena, FinalsState.Phase.CASHOUT, 1, players, teams, positions);
            require(alice.active().size() == 4 && alice.blocks().size() == 1
                    && !hasLabel(alice, "Cashout A") && hasLabel(alice, "Cashout B"),
                    "An ongoing deposit highlights only its active station B");
            require(!cashoutA.isValid() && !bodyA.isValid() && cashoutB.isValid(),
                    "Inactive station displays are removed while active station displays are reused");
            invoke(timed, highlights, arena, FinalsState.Phase.CASHOUT, 1, 5L, 9L, players, teams, positions);
            require(label(alice, "Cashout B") == cashoutB && plain(cashoutB).contains("9")
                            && !hasLabel(alice, "Cashout A"),
                    "Only the active cashout receives its live remaining-time label");
            invoke(update, highlights, arena, FinalsState.Phase.CASHOUT, 0, players, teams, positions);
            require(hasLabel(alice, "Cashout A") && !hasLabel(alice, "Cashout B") && !cashoutB.isValid(),
                    "Changing the active deposit switches the single highlighted station");
            TextDisplay projectedCashout = label(alice, "Cashout A");
            BlockDisplay projectedBody = alice.blocks().getFirst();
            Location savedCashout = arena.stations[0].clone(), savedBob = bob.location.clone();
            arena.stations[0] = new Location(world, 900, 130, 900);
            bob.location = new Location(world, 1000, 125, 1000);
            Location distantCashout = arena.stations[0].clone(), distantBob = bob.location.clone();
            int beforeProjectionSpawns = observer.spawned.size();
            invoke(downedUpdate, highlights, arena, FinalsState.Phase.CASHOUT, 0, 5L, 11L,
                    players, teams, positions, (Predicate<Player>) player -> player.getUniqueId().equals(bob.id));
            require(label(alice, "Cashout A") == projectedCashout && label(alice, bob.name) == ally,
                    "Distant objectives and allies reuse their existing private native displays");
            require(alice.player.getEyeLocation().distance(projectedCashout.getLocation()) <= 24.001
                            && alice.player.getEyeLocation().distance(ally.getLocation()) < 27,
                    "Native objective and downed teammate labels remain near the viewer");
            require(plain(projectedCashout).contains("11s")
                            && plain(projectedCashout).contains(Math.round(alice.location.distance(distantCashout)) + " blocks")
                            && plain(ally).contains("Downed")
                            && plain(ally).contains(Math.round(alice.location.distance(distantBob)) + " blocks"),
                    "Projected native labels retain objective timers, downed status and true distances");
            close(projectedBody.getTransformation().getScale().x, .25,
                    "Distant objective outline becomes a compact glowing direction proxy");
            require(projectedBody.isGlowing() && projectedCashout.isSeeThrough() && ally.isSeeThrough()
                            && projectedCashout.getLocation().distance(ally.getLocation()) >= .749,
                    "Projected outlines remain glowing and close bearings get separated see-through labels");
            require(!hasLabel(alice, eve.name) && !hasLabel(eve, bob.name) && outsider.active().isEmpty(),
                    "Distant projection never exposes enemy or outsider teammate labels");
            require(arena.stations[0].equals(distantCashout) && bob.location.equals(distantBob)
                            && observer.spawned.size() == beforeProjectionSpawns,
                    "Projection never changes real objective/player positions or allocates replacement displays");
            alice.sendDistance = 1;
            invoke(update, highlights, arena, FinalsState.Phase.CASHOUT, 0, players, teams, positions);
            require(alice.player.getEyeLocation().distance(projectedCashout.getLocation()) <= 8.001,
                    "Native markers move closer for a low send distance");
            alice.sendDistance = 10;
            alice.sentChunk = org.bukkit.Chunk.getChunkKey(alice.location);
            invoke(update, highlights, arena, FinalsState.Phase.CASHOUT, 0, players, teams, positions);
            require(org.bukkit.Chunk.getChunkKey(projectedCashout.getLocation()) == alice.sentChunk,
                    "Native proxy remains inside chunks the individual viewer has received");
            alice.sentChunk = null;
            arena.stations[0] = savedCashout;
            bob.location = savedBob;
            invoke(update, highlights, arena, FinalsState.Phase.CASHOUT, 0, players, teams, positions);
            close(projectedBody.getTransformation().getScale().x, 1.02,
                    "Returning nearby restores the objective's full-sized outline");
            close(projectedBody.getLocation().getX(), savedCashout.getX() - .5,
                    "Returning nearby restores the actual fractional objective geometry");
            require(!plain(projectedCashout).contains("blocks") && !plain(ally).contains("blocks"),
                    "Nearby native labels return to their ordinary text without being replaced");
            Location death = bob.location.clone();
            deathLocations.put(bob.id, death);
            bob.dead = true;
            bob.mode = GameMode.SPECTATOR;
            bob.location.add(0, 10, 0);
            Predicate<Player> downed = player -> player.getUniqueId().equals(bob.id)
                    || player.getUniqueId().equals(eve.id);
            Set<Display> beforeDowned = allActive(fixtures);
            int beforeDownedSpawns = observer.spawned.size();
            invoke(downedUpdate, highlights, arena, FinalsState.Phase.CASHOUT, 0,
                    -1L, -1L, players, teams, positions, downed);
            require(hasLabel(alice, bob.name) && label(alice, bob.name) == ally,
                    "A teammate keeps its existing private label throughout death and respawn");
            require(plain(ally).equals("✚ " + bob.name + " • Downed"),
                    "Native English downed text contains the allied name and status without health");
            require(!hasLabel(alice, eve.name) && !hasLabel(eve, bob.name)
                            && !hasLabel(frank, bob.name) && outsider.active().isEmpty(),
                    "Downed status never reveals opposing-team names or health to enemies or outsiders");
            close(ally.getLocation().getY(), death.getY() + 2.4,
                    "A respawning teammate's label stays at the death site, not the spectator camera");
            Location beforeStatueMove = ally.getLocation();
            death.add(2.5, -.5, -3.25);
            alice.locale = Locale.forLanguageTag("ru");
            invoke(downedUpdate, highlights, arena, FinalsState.Phase.CASHOUT, 0,
                    -1L, -1L, players, teams, positions, downed);
            require(label(alice, bob.name) == ally
                            && plain(ally).equals("✚ " + bob.name + " • Повержен"),
                    "Native Russian downed text changes language while reusing the same private display");
            moved(ally, beforeStatueMove, 2.5, -.5, -3.25,
                    "The native downed label follows the moving statue rather than its owner's camera");
            require(beforeDowned.equals(allActive(fixtures))
                            && observer.spawned.size() == beforeDownedSpawns,
                    "Downed status, statue movement and locale changes never allocate replacement entities");
            alice.locale = Locale.ENGLISH;
            deathLocations.remove(bob.id);
            bob.dead = false;
            bob.mode = GameMode.SURVIVAL;
            invoke(update, highlights, arena, FinalsState.Phase.CARRIED, -1, players, teams, positions);
            require(label(alice, bob.name) == ally && plain(ally).equals("◆ " + bob.name),
                    "Revival removes the downed status and resumes the same ordinary allied name display");
            close(ally.getLocation().getY(), bob.location.getY() + 2.4,
                    "Respawn resumes tracking the teammate's live position");
            bob.dead = true;
            bob.mode = GameMode.SPECTATOR;
            deathLocations.put(bob.id, bob.location.clone());
            invoke(downedUpdate, highlights, arena, FinalsState.Phase.CARRIED, -1,
                    -1L, -1L, players, teams, positions, downed);
            require(label(alice, bob.name) == ally && plain(ally).contains("Downed"),
                    "A later elimination reuses the same marker before downed-player departure cleanup");
            Set<Display> bobDisplays = bob.active(), eveDisplays = eve.active();
            invoke(remove, highlights, bob.id);
            require(bob.active().isEmpty() && bobDisplays.stream().noneMatch(Entity::isValid),
                    "Departure removes all viewer-owned displays");
            require(!ally.isValid() && !hasLabel(alice, bob.name) && alice.active().size() == 5,
                    "Departure also removes this ally from the remaining viewers");
            require(eveDisplays.equals(eve.active()), "Red departures leave blue displays untouched");
            List<Player> remaining = players.stream().filter(player -> !player.getUniqueId().equals(bob.id)).toList();
            invoke(update, highlights, arena, FinalsState.Phase.FINISHED, -1, remaining, teams, positions);
            require(allActive(fixtures).isEmpty(), "Finished matches remove objectives and teammate markers");
            invoke(update, highlights, arena, null, -1, remaining, teams, positions);
            Set<Display> beforeClear = allActive(fixtures);
            invoke(clear, highlights);
            require(allActive(fixtures).isEmpty() && beforeClear.stream().noneMatch(Entity::isValid),
                    "Explicit cleanup removes every real native marker");
            require(observer.spawned.values().stream().noneMatch(initial -> initial.entity.isValid()),
                    "Native lifecycle leaves no orphan marker entities");
            Method cashboxUpdate = method(type, "update", FinalsMapArena.class, FinalsState.Phase.class,
                    int.class, long.class, long.class, Collection.class, Map.class, Function.class,
                    Predicate.class, Location.class, UUID.class);
            Location box = new Location(world, 1500.5, 100, 1500.5);
            bob.locale = Locale.forLanguageTag("ru");
            invoke(cashboxUpdate, highlights, arena, FinalsState.Phase.CASHBOX_AVAILABLE, -1, 0L, 90L,
                    players, teams, positions, (Predicate<Player>) p -> false, box, null);
            require(hasLabel(alice, "Cashbox") && hasLabel(eve, "Cashbox"),
                    "Both teams get a far cashbox label through the full update path");
            require(hasLabel(bob, "Кэшбокс"), "Cashbox labels use the viewer's locale");
            require(alice.blocks().size() == 3, "A distant cashbox adds a native glowing block proxy");
            invoke(cashboxUpdate, highlights, arena, FinalsState.Phase.CARRIED, -1, 0L, 90L,
                    players, teams, positions, (Predicate<Player>) p -> false, box, alice.id);
            require(!hasLabel(alice, "Cashbox") && hasLabel(eve, "Cashbox"),
                    "The carrier avoids an own-body marker while other players can track the cashbox");
            invoke(cashboxUpdate, highlights, arena, FinalsState.Phase.CASHOUT, 0, 0L, 90L,
                    players, teams, positions, (Predicate<Player>) p -> false, box, null);
            require(!hasLabel(eve, "Cashbox"), "Depositing cleans up native cashbox markers");
            Method hitUpdate = method(type, "update", FinalsMapArena.class, FinalsState.Phase.class,
                    int.class, long.class, long.class, Collection.class, Map.class, Function.class,
                    Predicate.class, Location.class, UUID.class, Predicate.class);
            Predicate<Player> hit = p -> p.getUniqueId().equals(eve.id);
            invoke(hitUpdate, highlights, arena, FinalsState.Phase.CASHOUT, 0, 0L, 90L,
                    players, teams, positions, (Predicate<Player>) p -> false, null, null, hit);
            require(hasLabel(alice, "17/25 HP") && hasLabel(bob, "17/25 ОЗ"),
                    "A hit exposes the victim's HP to every opposing teammate");
            require(!hasLabel(eve, "17/25 HP") && !hasLabel(frank, "17/25 HP") && outsider.active().isEmpty(),
                    "Victim, allies and game master do not get an enemy HP display");
            TextDisplay hp = alice.texts().stream().filter(t -> PlainTextComponentSerializer.plainText()
                    .serialize(t.text()).contains("17/25 HP")).findFirst().orElseThrow();
            require(!hp.isSeeThrough() && !hp.isVisibleByDefault(),
                    "Enemy HP is private and cannot be read through walls");
            close(hp.getLocation().distance(eve.location.clone().add(0, 2.1, 0)), 0,
                    "Native HP display is above the player's scaled head");
            eve.health = 7.1;
            invoke(hitUpdate, highlights, arena, FinalsState.Phase.CASHOUT, 0, 0L, 90L,
                    players, teams, positions, (Predicate<Player>) p -> false, null, null, hit);
            require(hasLabel(alice, "8/25 HP") && hp.isValid(), "HP updates live in the existing display");
            invoke(hitUpdate, highlights, arena, FinalsState.Phase.CASHOUT, 0, 0L, 90L,
                    players, teams, positions, (Predicate<Player>) p -> false, null, null, (Predicate<Player>) p -> false);
            require(!hp.isValid() && !hasLabel(alice, "8/25 HP"), "Expiry removes the native enemy HP display");
            invoke(clear, highlights);
            smoke.getLogger().info("FINALS_HIGHLIGHTS_SMOKE_PASS: " + assertions + " native assertions");
            return assertions;
        } finally {
            try { invoke(clear, highlights); }
            finally { HandlerList.unregisterAll(observer); }
        }
    }

    private void close(double actual, double expected, String message) {
        require(Math.abs(actual - expected) < .0001, message + ": expected " + expected + ", got " + actual);
    }
    private void moved(Entity entity, Location before, double dx, double dy, double dz, String message) {
        Location after = entity.getLocation();
        require(after.getWorld().equals(before.getWorld()) && Math.abs(after.getX() - before.getX() - dx) < .0001
                && Math.abs(after.getY() - before.getY() - dy) < .0001
                && Math.abs(after.getZ() - before.getZ() - dz) < .0001, message);
    }

    private static BlockDisplay nearestBlock(FakePlayer viewer, Location point) {
        return viewer.blocks().stream().min(Comparator.comparingDouble(block -> block.getLocation().distanceSquared(point)))
                .orElseThrow();
    }

    private static boolean sameLocations(List<Location> first, List<Location> second) { return first.equals(second); }

    private static Set<Display> allActive(Collection<FakePlayer> players) {
        Set<Display> result = new LinkedHashSet<>();
        for (FakePlayer player : players) result.addAll(player.active());
        return result;
    }

    private static String plain(TextDisplay display) {
        return PlainTextComponentSerializer.plainText().serialize(display.text());
    }

    private static boolean hasLabel(FakePlayer viewer, String fragment) {
        return viewer.texts().stream().anyMatch(text -> plain(text).contains(fragment));
    }

    private static TextDisplay label(FakePlayer viewer, String fragment) {
        List<TextDisplay> matches = viewer.texts().stream().filter(text -> plain(text).contains(fragment)).toList();
        if (matches.size() != 1) throw new AssertionError("Expected one label containing " + fragment + ", got " + matches.size());
        return matches.getFirst();
    }

    private static boolean hasColor(Component component, TextColor color) {
        return color.equals(component.color()) || component.children().stream().anyMatch(child -> hasColor(child, color));
    }

    private static Method method(Class<?> type, String name, Class<?>... parameters) throws Exception {
        Method method = type.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }

    private static Object invoke(Method method, Object receiver, Object... arguments) throws Exception {
        try { return method.invoke(receiver, arguments); }
        catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Exception exception) throw exception;
            if (failure.getCause() instanceof Error error) throw error;
            throw failure;
        }
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }

    public static final class SpawnObserver implements Listener {
        final Map<UUID, SpawnMetadata> spawned = new HashMap<>();
        @EventHandler(priority = EventPriority.MONITOR)
        public void onSpawn(EntitySpawnEvent event) {
            if (event.getEntity() instanceof Display display)
                spawned.put(display.getUniqueId(), new SpawnMetadata(display, display.isVisibleByDefault(),
                        display.isPersistent(), display.isInvulnerable()));
        }
    }

    private record SpawnMetadata(Display entity, boolean visible, boolean persistent, boolean invulnerable) {}

    private static final class FakePlayer {
        final UUID id = UUID.randomUUID();
        final String name;
        final Player player;
        final Set<Display> shown = new LinkedHashSet<>();
        Location location;
        Locale locale = Locale.ENGLISH;
        boolean dead;
        double health = 16.2;
        int sendDistance = 10;
        Long sentChunk;
        GameMode mode = GameMode.SURVIVAL;

        FakePlayer(World world, String name, double x, double z) {
            this.name = name;
            location = new Location(world, x, 65, z);
            player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getUniqueId" -> id;
                        case "getName" -> name;
                        case "getHealth" -> health;
                        case "getMaxHealth" -> 25d;
                        case "getHeight" -> 1.8;
                        case "displayName" -> Component.text(name);
                        case "getWorld" -> location.getWorld();
                        case "getLocation" -> location.clone();
                        case "getEyeLocation" -> location.clone().add(0, 1.62, 0);
                        case "getClientViewDistance" -> 10;
                        case "getSendViewDistance" -> sendDistance;
                        case "isChunkSent" -> sentChunk == null || sentChunk.equals(args[0]);
                        case "locale" -> locale;
                        case "isOnline", "isValid" -> true;
                        case "isDead" -> dead;
                        case "getGameMode" -> mode;
                        case "canSee" -> args[0] instanceof Player || shown.contains(args[0]);
                        case "showEntity" -> {
                            if (!(args[1] instanceof Display display))
                                throw new AssertionError("Only private display entities may be shown to a viewer");
                            shown.add(display);
                            yield null;
                        }
                        case "hideEntity" -> { shown.remove(args[1]); yield null; }
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> id.hashCode();
                        case "toString" -> "HighlightPlayer[" + name + "]";
                        default -> throw new AssertionError("Unexpected player transport call: " + method.getName());
                    });
        }

        Set<Display> active() {
            Set<Display> active = new LinkedHashSet<>();
            for (Display display : shown) if (display.isValid()) active.add(display);
            return active;
        }
        List<TextDisplay> texts() { return active().stream().filter(TextDisplay.class::isInstance).map(TextDisplay.class::cast).toList(); }
        List<BlockDisplay> blocks() { return active().stream().filter(BlockDisplay.class::isInstance).map(BlockDisplay.class::cast).toList(); }
        String describe() {
            return name + "[shown=" + shown.size() + ",valid=" + active().size() + ",text=" + texts().size()
                    + ",block=" + blocks().size() + ",invalid="
                    + shown.stream().filter(display -> !display.isValid()).map(display -> display.getType()
                            + "@" + display.getLocation().getBlockX() + "," + display.getLocation().getBlockZ()
                            + "(chunkLoaded=" + display.getWorld().isChunkLoaded(display.getLocation().getBlockX() >> 4,
                                    display.getLocation().getBlockZ() >> 4) + ")").toList() + "]";
        }
    }

    /** Deliberately exposes mutable source Locations to catch accidental add()/subtract() mutation. */
    private static final class FixtureArena implements FinalsMapArena {
        final World world;
        final Location vault;
        final Location[] stations;
        FixtureArena(World world) {
            this.world = world;
            vault = new Location(world, 500, 65, 500);
            stations = new Location[]{new Location(world, 484, 65, 500), new Location(world, 516, 65, 500)};
        }
        List<Location> sources() { return List.of(vault.clone(), stations[0].clone(), stations[1].clone()); }
        public World world() { return world; }
        public void build() {}
        public Location center() { return new Location(world, 500, 65, 500); }
        public Location spawn(int team, int index) { return new Location(world, 500, 65, 500 + team * 5); }
        public Location vault() { return vault; }
        public List<Location> vaultLocations() { return List.of(vault); }
        public void nextVault() { vault.add(8, 0, 4); }
        public Location station(int station) { return stations[station]; }
        public List<Location> cashoutLocations() { return List.of(stations); }
        public void nextCashout(int station) { stations[station].add(-6, 0, 8); }
        public boolean contains(Location point) { return world.equals(point.getWorld()); }
        public boolean isCover(Block block) { return false; }
        public int stationAt(Block block) { return -1; }
        public boolean isVault(Block block) { return false; }
        public double borderSize() { return 100; }
    }
}
