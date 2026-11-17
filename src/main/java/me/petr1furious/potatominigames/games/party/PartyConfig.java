package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.core.config.ConfigRegistry;
import me.petr1furious.potatominigames.core.config.Property;
import me.petr1furious.potatominigames.core.config.PropertyBag;
import me.petr1furious.potatominigames.core.config.PropertyCodec;

import org.bukkit.configuration.ConfigurationSection;

public final class PartyConfig {
    private final PropertyBag bag = new PropertyBag("party");
    public final Property<Integer> garticPromptSeconds =
            bag.define("garticPhone.promptSeconds", PropertyCodec.INT, 30).validate(n -> n >= 10 && n <= 120);
    public final Property<Integer> garticBuildSeconds =
            bag.define("garticPhone.buildSeconds", PropertyCodec.INT, 90).validate(n -> n >= 20 && n <= 300);
    public final Property<Integer> garticGuessSeconds =
            bag.define("garticPhone.guessSeconds", PropertyCodec.INT, 30).validate(n -> n >= 10 && n <= 120);
    public final Property<Integer> garticRevealSeconds =
            bag.define("garticPhone.revealSeconds", PropertyCodec.INT, 6).validate(n -> n >= 3 && n <= 20);
    public final Property<Integer> roundSeconds =
            bag.define("pvp.roundSeconds", PropertyCodec.INT, 90)
                    .validate(n -> n >= 20 && n <= 300);
    public final Property<Integer> countdownSeconds =
            bag.define("pvp.countdownSeconds", PropertyCodec.INT, 5)
                    .validate(n -> n >= 3 && n <= 15);
    public final Property<Integer> intermissionSeconds =
            bag.define("pvp.intermissionSeconds", PropertyCodec.INT, 5)
                    .validate(n -> n >= 2 && n <= 30);
    public final Property<Integer> sumoRoundSeconds =
            bag.define("sumo.roundSeconds", PropertyCodec.INT, 45)
                    .validate(n -> n >= 20 && n <= 300);
    public final Property<Integer> sumoCountdownSeconds =
            bag.define("sumo.countdownSeconds", PropertyCodec.INT, 5)
                    .validate(n -> n >= 3 && n <= 15);
    public final Property<Integer> sumoIntermissionSeconds =
            bag.define("sumo.intermissionSeconds", PropertyCodec.INT, 5)
                    .validate(n -> n >= 2 && n <= 30);
    public final Property<Integer> oneShotSeconds =
            bag.define("oneShot.durationSeconds", PropertyCodec.INT, 120)
                    .validate(n -> n >= 60 && n <= 1800);
    public final Property<Integer> oneShotBonusSeconds =
            bag.define("oneShot.bonusSeconds", PropertyCodec.INT, 6)
                    .validate(n -> n >= 2 && n <= 30);
    public final Property<Integer> armsRaceSeconds =
            bag.define("armsRace.durationSeconds", PropertyCodec.INT, 600)
                    .validate(n -> n >= 60 && n <= 1800);
    public final Property<Integer> armsRaceCountdownSeconds =
            bag.define("armsRace.countdownSeconds", PropertyCodec.INT, 5)
                    .validate(n -> n >= 3 && n <= 15);
    public final Property<Integer> armsRaceRespawnSeconds =
            bag.define("armsRace.respawnSeconds", PropertyCodec.INT, 3)
                    .validate(n -> n >= 1 && n <= 10);
    public final Property<Integer> armsRaceProtectionSeconds =
            bag.define("armsRace.spawnProtectionSeconds", PropertyCodec.INT, 2)
                    .validate(n -> n >= 1 && n <= 5);
    public final Property<Integer> survivalCountdownSeconds =
            bag.define("survival.countdownSeconds", PropertyCodec.INT, 5)
                    .validate(n -> n >= 3 && n <= 15);
    public final Property<Integer> tntRunSeconds =
            bag.define("tntRun.durationSeconds", PropertyCodec.INT, 180)
                    .validate(n -> n >= 30 && n <= 600);
    public final Property<Integer> tntRunCollapseTicks =
            bag.define("tntRun.collapseTicks", PropertyCodec.INT, 10)
                    .validate(n -> n >= 3 && n <= 40);
    public final Property<Integer> mobCountSeconds =
            bag.define("mobCount.countSeconds", PropertyCodec.INT, 10)
                    .validate(n -> n >= 6 && n <= 30);
    public final Property<Integer> mobCountAnswerSeconds =
            bag.define("mobCount.answerSeconds", PropertyCodec.INT, 10)
                    .validate(n -> n >= 4 && n <= 20);
    public final Property<Integer> mobCountRounds =
            bag.define("mobCount.rounds", PropertyCodec.INT, 12).validate(n -> n >= 2 && n <= 30);
    public final Property<Integer> rhinoSeconds =
            bag.define("rhino.durationSeconds", PropertyCodec.INT, 240)
                    .validate(n -> n >= 30 && n <= 600);
    public final Property<Integer> rhinoCount =
            bag.define("rhino.ravagers", PropertyCodec.INT, 2).validate(n -> n >= 1 && n <= 6);
    public final Property<Integer> rhinoMineFuseSeconds =
            bag.define("rhino.mineFuseSeconds", PropertyCodec.INT, 8)
                    .validate(n -> n >= 2 && n <= 15);
    public final Property<Integer> rhinoMineSpawnSeconds =
            bag.define("rhino.mineSpawnSeconds", PropertyCodec.INT, 4)
                    .validate(n -> n >= 1 && n <= 10);

    public final Property<Integer> minersPreparationSeconds =
            bag.define("miners.preparationSeconds", PropertyCodec.INT, 25)
                    .validate(n -> n >= 10 && n <= 300);
    public final Property<Integer> minersCombatSeconds =
            bag.define("miners.combatSeconds", PropertyCodec.INT, 180)
                    .validate(n -> n >= 30 && n <= 600);
    public final Property<Integer> minersFinaleSeconds =
            bag.define("miners.finaleSeconds", PropertyCodec.INT, 120)
                    .validate(n -> n >= 15 && n <= 120);

    public final Property<Integer> holeWallSeconds =
            bag.define("holeInTheWall.durationSeconds", PropertyCodec.INT, 120)
                    .validate(n -> n >= 45 && n <= 300);
    public final Property<Integer> colorFloorStages =
            bag.define("colorFloor.stages", PropertyCodec.INT, 12).validate(n -> n >= 2 && n <= 24);
    public final Property<Integer> anvilWaveSeconds =
            bag.define("anvils.waveSeconds", PropertyCodec.INT, 6).validate(n -> n >= 5 && n <= 30);
    public final Property<Integer> anvilMoveSeconds =
            bag.define("anvils.moveSeconds", PropertyCodec.INT, 10)
                    .validate(n -> n >= 4 && n <= 15);
    public final Property<Integer> anvilWaves =
            bag.define("anvils.waves", PropertyCodec.INT, 10).validate(n -> n >= 2 && n <= 20);
    public final Property<Integer> anvilPvpWave =
            bag.define("anvils.pvpWave", PropertyCodec.INT, 1).validate(n -> n >= 1 && n <= 20);
    public final Property<Integer> buildMemoryStages =
            bag.define("buildMemory.stages", PropertyCodec.INT, 8).validate(n -> n >= 1 && n <= 8);
    public final Property<Integer> toolRaceSeconds =
            bag.define("toolRace.durationSeconds", PropertyCodec.INT, 180)
                    .validate(n -> n >= 30 && n <= 600);
    public final Property<String> partyModes =
            bag.define(
                    "partyGames.modes",
                    PropertyCodec.STRING,
                    String.join(",", PartyGamesState.MODES));
    public final Property<Integer> partyRounds =
            bag.define("partyGames.rounds", PropertyCodec.INT, 6)
                    .validate(n -> n >= 1 && n <= PartyGamesState.MODES.size());
    public final Property<Integer> partyIntermission =
            bag.define("partyGames.intermissionSeconds", PropertyCodec.INT, 4)
                    .validate(n -> n >= 3 && n <= 30);
    public final Property<Integer> partyArmsRaceSeconds =
            bag.define("partyGames.armsRaceSeconds", PropertyCodec.INT, 180)
                    .validate(n -> n >= 60 && n <= 600);
    public final Property<Integer> partyMinersPreparation =
            bag.define("partyGames.minersPreparationSeconds", PropertyCodec.INT, 25)
                    .validate(n -> n >= 10 && n <= 300);
    public final Property<Integer> partyMinersCombat =
            bag.define("partyGames.minersCombatSeconds", PropertyCodec.INT, 120)
                    .validate(n -> n >= 30 && n <= 600);
    public final Property<Integer> partyMinersFinale =
            bag.define("partyGames.minersFinaleSeconds", PropertyCodec.INT, 60)
                    .validate(n -> n >= 15 && n <= 120);

    public final Property<Integer> pvpFfaOvertimeSeconds =
            bag.define("pvpFfa.overtimeSeconds", PropertyCodec.INT, 180)
                    .validate(n -> n >= 20 && n <= 600);
    public final Property<Integer> sumoFfaOvertimeSeconds =
            bag.define("sumoFfa.overtimeSeconds", PropertyCodec.INT, 45)
                    .validate(n -> n >= 20 && n <= 300);

    public final Property<Integer> magicSeconds = bag.define("magicBattle.roundSeconds", PropertyCodec.INT, 180)
            .validate(n -> n >= 60 && n <= 600);
    public final Property<Integer> parkourRaceSeconds = bag.define("parkourRace.durationSeconds", PropertyCodec.INT, 240)
            .validate(n -> n >= 60 && n <= 600);
    public final Property<Integer> magicCountdown = bag.define("magicBattle.countdownSeconds", PropertyCodec.INT, 5)
            .validate(n -> n >= 3 && n <= 15);
    public final Property<Integer> magicChestSeconds = bag.define("magicBattle.chestRechargeSeconds", PropertyCodec.INT, 45)
            .validate(n -> n >= 10 && n <= 120);

    public PartyConfig(ConfigRegistry configs) {
        if (migrateMinersTimings(configs.file("games/party.yml")))
            configs.saveFile("games/party.yml");
        configs.hide("games/party.yml", "miners.timingVersion");
        configs.register(bag, "games/party.yml", "");
    }

    /** Upgrade the previous defaults once; preserve custom server timings. */
    static boolean migrateMinersTimings(ConfigurationSection config) {
        if (config.getInt("miners.timingVersion", 0) >= 1) return false;
        upgradeTiming(config, "miners.preparationSeconds", 15, 25);
        upgradeTiming(config, "miners.finaleSeconds", 60, 120);
        upgradeTiming(config, "partyGames.minersPreparationSeconds", 15, 25);
        upgradeTiming(config, "partyGames.minersCombatSeconds", 90, 120);
        upgradeTiming(config, "partyGames.minersFinaleSeconds", 30, 60);
        config.set("miners.timingVersion", 1);
        return true;
    }

    private static void upgradeTiming(ConfigurationSection config, String key, int oldValue, int newValue) {
        if (config.getInt(key, newValue) == oldValue) config.set(key, newValue);
    }
}
