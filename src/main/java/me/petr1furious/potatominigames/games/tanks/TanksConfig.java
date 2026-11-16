package me.petr1furious.potatominigames.games.tanks;

import me.petr1furious.potatominigames.core.config.*;

final class TanksConfig {
    private final PropertyBag bag = new PropertyBag("tanks");
    final Property<Integer> preparation =
            bag.define("preparationSeconds", PropertyCodec.INT, 20)
                    .validate(n -> n >= 5 && n <= 60);
    final Property<Integer> battle =
            bag.define("battleSeconds", PropertyCodec.INT, 360).validate(n -> n >= 60 && n <= 900);
    final Property<Integer> capture =
            bag.define("captureSeconds", PropertyCodec.INT, 35).validate(n -> n >= 15 && n <= 120);

    TanksConfig(ConfigRegistry registry) {
        registry.register(bag, "games/tanks.yml", "");
    }
}
