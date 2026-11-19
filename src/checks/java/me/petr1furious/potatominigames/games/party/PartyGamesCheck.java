package me.petr1furious.potatominigames.games.party;

import static me.petr1furious.potatominigames.games.party.PartyScoringCheck.check;

import me.petr1furious.potatominigames.i18n.I18n;

import java.util.*;

public final class PartyGamesCheck {
    public static void main(String[] args) {
        var game = new PartyGame((PartyConfig) null);
        check(!game.ballots("party_games").isEmpty(), "Tournament length is selectable");
        check(game.ballots("pvp").isEmpty(), "Standalone games have no tournament length vote");
        check(PartyGamesSession.roundCount("small", 12, 15) == 4, "Small overrides configured length with four rounds");
        check(PartyGamesSession.roundCount("medium", 12, 15) == 6, "Medium has six rounds");
        check(PartyGamesSession.roundCount("large", 4, 15) == 9, "Large has nine rounds");
        check(PartyGamesSession.roundCount("marathon", 6, 20) == 20, "Marathon plays every eligible game");
        check(PartyGamesSession.roundCount("marathon", 6, 7) == 7, "Marathon follows the configured pool");
        check(PartyGamesSession.roundCount("large", 6, 3) == 3, "Short pools never repeat games to fill a length");
        check(PartyGamesSession.roundCount(null, 11, 15) == 11, "Direct starts without a length preserve admin configuration");
        try {
            PartyGamesSession.roundCount("unknown", 6, 15);
            throw new AssertionError("Unknown match length accepted");
        } catch (IllegalArgumentException expected) {
        }
        UUID a = new UUID(0, 1), b = new UUID(0, 2), c = new UUID(0, 3);
        var s = new PartyGamesState(List.of(a, b, c), PartyGamesState.MODES, new Random(1));
        check(
                s.schedule().size() == PartyGamesState.MODES.size()
                        && new HashSet<>(s.schedule()).equals(new HashSet<>(PartyGamesState.MODES)),
                "Every eligible game is scheduled exactly once");
        var newModes = List.of(
                "hot_potato", "jump_rope", "drop_tower", "chicken_shooter", "less_is_better", "wait_for_it");
        for (String mode : newModes) {
            check(game.modes().contains(mode) && game.maxPlayers(mode) == 12,
                    "Quick round is available as a public queue: " + mode);
            var standalone = game.createSession(null, mode);
            check(standalone instanceof PartySurvivalSession quick
                            && quick.id.equals(mode) && !quick.context.embedded(),
                    "Direct play starts the standalone quick round: " + mode);
            var rules = ((PartySurvivalSession) standalone).instructions();
            String english = I18n.plain(rules, Locale.ENGLISH);
            String russian = I18n.plain(rules, Locale.forLanguageTag("ru"));
            check(!english.isBlank() && !russian.isBlank() && !english.equals(russian),
                    "First-play instructions resolve in English and Russian: " + mode);
            var context = new PartyRoundContext(null, List.of(), PartyRoundOptions.party(), (reason, result) -> {});
            check(PartyRoundFactory.create(mode, null, null, context) instanceof PartySurvivalSession quick
                            && quick.id.equals(mode) && quick.context == context,
                    "The tournament creates the same quick round inside its round context: " + mode);
        }
        check(I18n.has("party.tag_and_timing.hot_potato.dummy"), "Solo practice dummy has a localized label");
        var previousDefault = List.of(
                "pvp", "sumo", "arms_race", "one_shot", "tnt_run", "mob_count", "rhino", "miners",
                "color_floor", "anvils", "build_memory", "tool_race", "hole_in_the_wall", "parkour_race");
        var oldPool = new ArrayList<>(previousDefault);
        oldPool.add(oldPool.indexOf("parkour_race"), "magic_battle");
        var migrated = new PartyGamesState(List.of(a, b), oldPool, new Random(2));
        check(migrated.schedule().size() == PartyGamesState.MODES.size()
                        && new HashSet<>(migrated.schedule()).equals(new HashSet<>(PartyGamesState.MODES))
                        && migrated.schedule().containsAll(newModes)
                        && !migrated.schedule().contains("magic_battle"),
                "The previous bundled pool picks up the new rounds and still skips Magic Battle");
        var curated = new PartyGamesState(List.of(a, b), List.of("sumo", "jump_rope"), new Random(2));
        check(new HashSet<>(curated.schedule()).equals(Set.of("sumo", "jump_rope")),
                "Customized pools keep their selected rounds");
        var reordered = new PartyGamesState(List.of(a, b), previousDefault.reversed(), new Random(2));
        check(reordered.schedule().size() == previousDefault.size(),
                "A reordered pool is a custom pool even if it has the same modes as the previous default");
        s.beginRound(0);
        s.disconnect(b);
        s.reconnect(b);
        check(
                s.acceptResult(
                        0, new PartyRoundResult(List.of(List.of(b), List.of(a), List.of(c)))),
                "Current generation result");
        check(
                s.score().points(b) == 0 && s.score().points(a) == 3,
                "Disconnect then rejoin cannot earn this round");
        check(!s.acceptResult(0, new PartyRoundResult(List.of(List.of(a)))), "No duplicate result");
        s.beginRound(1);
        check(!s.acceptResult(0, PartyRoundResult.empty()), "Old callback ignored");
        check(s.nextRoster().contains(b), "Rejoin plays next round");
        s.withdraw(c);
        s.reconnect(c);
        check(!s.nextRoster().contains(c), "Explicit withdrawal permanent");
        s.acceptResult(1, new PartyRoundResult(List.of(List.of(a, b))));
        s.disconnect(a);
        check(s.score().points(a) == 6, "Closed round survives subsequent disconnect");
        for (var modes :
                List.of(
                        List.<String>of(),
                        List.of("party_games"),
                        List.of("magic_battle"),
                        List.of("unknown"),
                        List.of("anvils", "anvils"))) {
            try {
                new PartyGamesState(List.of(a, b), modes, new Random(1));
                throw new AssertionError("Invalid pool accepted");
            } catch (IllegalArgumentException expected) {
            }
        }
        System.out.println("Party Games state passed");
    }
}
