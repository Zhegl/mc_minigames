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

    public PartyConfig(ConfigRegistry configs) { configs.register(bag, "games/party.yml", ""); }
}
