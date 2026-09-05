package me.petr1furious.potatominigames.games.chase;

import me.petr1furious.potatominigames.core.config.ConfigRegistry;
import me.petr1furious.potatominigames.core.config.Property;
import me.petr1furious.potatominigames.core.config.PropertyBag;
import me.petr1furious.potatominigames.core.config.PropertyCodec;

/**
 * Defaults are the values the original Ultimate Tag was play-tested with; they
 * are not arbitrary, so change them with care.
 */
public final class ChaseConfig {
    public static final String FILE = "games/chase.yml";

    private final PropertyBag bag = new PropertyBag("chase");
    public final Property<Integer> hunterCount = bag.define("hunterCount", PropertyCodec.INT, 1);
    public final Property<Integer> borderSize = bag.define("borderSize", PropertyCodec.INT, 100);
    public final Property<Integer> roundLengthSeconds = bag.define("roundLengthSeconds", PropertyCodec.INT, 60);
    public final Property<Integer> roundLengthPerPlayerSeconds = bag.define("roundLengthPerPlayerSeconds",
            PropertyCodec.INT, 15);
    public final Property<Integer> intermissionSeconds = bag.define("intermissionSeconds", PropertyCodec.INT, 5);
    /** A moment at the start of each round for everyone to finish loading the world. */
    public final Property<Integer> freezeSeconds = bag.define("freezeSeconds", PropertyCodec.INT, 3);

    /**
     * How far below and above the local ground players may roam. Building is never
     * restricted; straying outside this band is what has consequences, so nobody can
     * be walled in by a refused block.
     */
    public final Property<Integer> allowedDepth = bag.define("allowedDepth", PropertyCodec.INT, 20);
    public final Property<Integer> allowedHeight = bag.define("allowedHeight", PropertyCodec.INT, 20);
    /** Seconds out of bounds before you start glowing for everyone to see. */
    public final Property<Integer> glowAfterSeconds = bag.define("glowAfterSeconds", PropertyCodec.INT, 5);
    /** Seconds out of bounds before you start taking damage. */
    public final Property<Integer> damageAfterSeconds = bag.define("damageAfterSeconds", PropertyCodec.INT, 10);

    /** Comma-separated gadget ids added to the hunter kit. */
    public final Property<String> hunterGadgets = bag.define("hunterGadgets", PropertyCodec.STRING, "radar,locator");

    public ChaseConfig(ConfigRegistry configs) {
        configs.register(bag, FILE, "");
    }

    public int roundLengthFor(int playerCount) {
        return Math.max(1, roundLengthSeconds.get() + roundLengthPerPlayerSeconds.get() * playerCount);
    }
}
