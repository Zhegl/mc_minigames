package me.petr1furious.potatominigames.games.party;

import static me.petr1furious.potatominigames.games.party.PartyScoringCheck.check;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class PartyPolishCheck {
    public static void main(String[] args) throws Exception {
        var yaml =
                YamlConfiguration.loadConfiguration(
                        new InputStreamReader(
                                Objects.requireNonNull(
                                        PartyPolishCheck.class.getResourceAsStream(
                                                "/games/party.yml")),
                                StandardCharsets.UTF_8));
        check(yaml.getInt("partyGames.rounds") == 6, "Six games per match by default");
        check(yaml.getInt("partyGames.intermissionSeconds") == 4, "Four-second intermission");
        var pool = Arrays.asList(yaml.getString("partyGames.modes").split(","));
        Set<List<String>> selections = new HashSet<>();
        for (int seed = 0; seed < 30; seed++) {
            var state = new PartyGamesState(List.of(new UUID(0, 1)), pool, new Random(seed));
            var selected = state.schedule().subList(0, yaml.getInt("partyGames.rounds"));
            check(new HashSet<>(selected).size() == 6, "No repeat games");
            selections.add(selected);
        }
        check(
                pool.size() == PartyGamesState.MODES.size()
                        && new HashSet<>(pool).equals(new HashSet<>(PartyGamesState.MODES))
                        && pool.contains("furry_quest")
                        && pool.contains("hole_in_the_wall")
                        && pool.contains("one_shot")
                        && pool.contains("parkour_race")
                        && selections.size() > 20,
                "Random six selected from the full pool");
        check(
                PartyRoundOptions.party().forRound(0).countdownSeconds() == 4,
                "Initial countdown is four seconds");
        for (int round = 1; round < PartyGamesState.MODES.size(); round++)
            check(
                    PartyRoundOptions.party().forRound(round).countdownSeconds() == 4,
                    "Every later arena has its own four-second countdown");
        var shortChain = ArmsRaceWeapon.progression(true);
        var fullChain = ArmsRaceWeapon.progression(false);
        check(
                shortChain.size() == 5 && fullChain.size() == 12,
                "Five steps only inside Party Games; standalone keeps twelve");
        check(
                shortChain.getLast() == ArmsRaceWeapon.BOW
                        && !shortChain.contains(ArmsRaceWeapon.CROSSBOW)
                        && new HashSet<>(shortChain).size() == 5,
                "Short progression excludes crossbow and ends with bow");
        UUID a = new UUID(0, 1), b = new UUID(0, 2);
        var race = new ArmsRaceState(List.of(a, b), shortChain.size());
        for (int stage = 0; stage < shortChain.size(); stage++) {
            race.setAlive(b, true);
            race.death(b, a, stage);
            check((race.winner() != null) == (stage == shortChain.size() - 1),
                    "Exactly five kills to win the short progression");
        }
        var session = new PartyGamesSession(null, null);
        int[] updates = {0};
        var context =
                new PartyRoundContext(null, List.of(), PartyRoundOptions.party(), (r, s) -> {});
        set(session, "context", context);
        set(session, "state", new PartyGamesState(List.of(a), pool, new Random(1)));
        set(
                session,
                "child",
                new PartyRoundSession() {
                    public void onStart() {}

                    public PartyRoundResult roundResult() {
                        return PartyRoundResult.empty();
                    }

                    public void onSecondTick(long t) {
                        updates[0]++;
                    }
                });
        Field phase = PartyGamesSession.class.getDeclaredField("phase");
        phase.setAccessible(true);
        @SuppressWarnings({"unchecked", "rawtypes"})
        Object active = Enum.valueOf((Class) phase.getType(), "ACTIVE");
        phase.set(session, active);
        // No parent HUD service is supplied: touching it while the child owns the screen fails.
        session.onSecondTick(1);
        session.onSecondTick(2);
        check(updates[0] == 2, "Only active child updates the scoreboard");
        check(
                yaml.getInt("anvils.waveSeconds") == 6 && yaml.getInt("anvils.moveSeconds") == 10,
                "Short rain, sufficient relocation");
        System.out.println(
                "Party match length, countdown, HUD ownership and short weapon chain passed");
    }

    private static void set(Object target, String name, Object value) throws Exception {
        var f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
