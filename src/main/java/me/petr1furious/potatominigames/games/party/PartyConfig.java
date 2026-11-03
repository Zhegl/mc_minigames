package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.core.config.ConfigRegistry;
import me.petr1furious.potatominigames.core.config.Property;
import me.petr1furious.potatominigames.core.config.PropertyBag;
import me.petr1furious.potatominigames.core.config.PropertyCodec;

public final class PartyConfig {
    private final PropertyBag bag = new PropertyBag("party");
    public final Property<Integer> roundSeconds = bag.define("pvp.roundSeconds", PropertyCodec.INT, 90)
            .validate(n -> n >= 20 && n <= 300);
    public final Property<Integer> countdownSeconds = bag.define("pvp.countdownSeconds", PropertyCodec.INT, 5)
            .validate(n -> n >= 3 && n <= 15);
    public final Property<Integer> intermissionSeconds = bag.define("pvp.intermissionSeconds", PropertyCodec.INT, 5)
            .validate(n -> n >= 2 && n <= 30);
    public final Property<Integer> sumoRoundSeconds = bag.define("sumo.roundSeconds", PropertyCodec.INT, 45)
            .validate(n -> n >= 20 && n <= 300);
    public final Property<Integer> sumoCountdownSeconds = bag.define("sumo.countdownSeconds", PropertyCodec.INT, 5)
            .validate(n -> n >= 3 && n <= 15);
    public final Property<Integer> sumoIntermissionSeconds = bag.define("sumo.intermissionSeconds", PropertyCodec.INT, 5)
            .validate(n -> n >= 2 && n <= 30);
    public final Property<Integer> armsRaceSeconds = bag.define("armsRace.durationSeconds", PropertyCodec.INT, 600)
            .validate(n -> n >= 60 && n <= 1800);
    public final Property<Integer> armsRaceCountdownSeconds = bag.define("armsRace.countdownSeconds", PropertyCodec.INT, 5)
            .validate(n -> n >= 3 && n <= 15);
    public final Property<Integer> armsRaceRespawnSeconds = bag.define("armsRace.respawnSeconds", PropertyCodec.INT, 3)
            .validate(n -> n >= 1 && n <= 10);
    public final Property<Integer> armsRaceProtectionSeconds = bag.define("armsRace.spawnProtectionSeconds", PropertyCodec.INT, 2)
            .validate(n -> n >= 1 && n <= 5);
    public final Property<Integer> survivalCountdownSeconds = bag.define("survival.countdownSeconds", PropertyCodec.INT, 5)
            .validate(n -> n >= 3 && n <= 15);
    public final Property<Integer> tntRunSeconds = bag.define("tntRun.durationSeconds", PropertyCodec.INT, 180)
            .validate(n -> n >= 30 && n <= 600);
    public final Property<Integer> tntRunCollapseTicks = bag.define("tntRun.collapseTicks", PropertyCodec.INT, 10)
            .validate(n -> n >= 3 && n <= 40);
    public final Property<Integer> mobCountSeconds = bag.define("mobCount.countSeconds", PropertyCodec.INT, 10)
            .validate(n -> n >= 6 && n <= 30);
    public final Property<Integer> mobCountAnswerSeconds = bag.define("mobCount.answerSeconds", PropertyCodec.INT, 10)
            .validate(n -> n >= 4 && n <= 20);
    public final Property<Integer> mobCountRounds = bag.define("mobCount.rounds", PropertyCodec.INT, 12)
            .validate(n -> n >= 2 && n <= 30);
    public final Property<Integer> rhinoSeconds = bag.define("rhino.durationSeconds", PropertyCodec.INT, 240)
            .validate(n -> n >= 30 && n <= 600);
    public final Property<Integer> rhinoCount = bag.define("rhino.ravagers", PropertyCodec.INT, 2)
            .validate(n -> n >= 1 && n <= 6);
    public final Property<Integer> rhinoMineFuseSeconds = bag.define("rhino.mineFuseSeconds", PropertyCodec.INT, 8)
            .validate(n -> n >= 2 && n <= 15);
    public final Property<Integer> rhinoMineSpawnSeconds = bag.define("rhino.mineSpawnSeconds", PropertyCodec.INT, 4)
            .validate(n -> n >= 1 && n <= 10);

    public final Property<Integer> minersPreparationSeconds = bag.define("miners.preparationSeconds", PropertyCodec.INT, 120)
            .validate(n -> n >= 10 && n <= 300);
    public final Property<Integer> minersCombatSeconds = bag.define("miners.combatSeconds", PropertyCodec.INT, 180)
            .validate(n -> n >= 30 && n <= 600);
    public final Property<Integer> minersFinaleSeconds = bag.define("miners.finaleSeconds", PropertyCodec.INT, 60)
            .validate(n -> n >= 15 && n <= 120);

    public PartyConfig(ConfigRegistry configs) { configs.register(bag, "games/party.yml", ""); }
}
