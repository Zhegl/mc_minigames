package me.petr1furious.potatominigames.gadgets;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import me.petr1furious.potatominigames.core.config.Property;
import me.petr1furious.potatominigames.core.config.PropertyBag;
import me.petr1furious.potatominigames.core.config.PropertyCodec;

/** Subclass defaults are the production values; keep them verbatim. */
public abstract class AbstractGadget implements Gadget {
    protected final GadgetContext context;
    private final String id;
    private final PropertyBag config;
    private final Property<Boolean> allowOutsideGame;

    protected AbstractGadget(String id, GadgetContext context, boolean allowOutsideGameDefault) {
        this.id = id;
        this.context = context;
        this.config = new PropertyBag("gadgets." + id);
        this.allowOutsideGame = config.define("allowOutsideGame", PropertyCodec.BOOL, allowOutsideGameDefault);
    }

    @Override
    public final String id() {
        return id;
    }

    @Override
    public final PropertyBag config() {
        return config;
    }

    @Override
    public final boolean usableBy(Player player) {
        // Knocked-out participants are still in the game, but must not act in it; e.g. a drone
        // launched from spectator mode would put them back in survival.
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        if (context.inGame(player)) {
            return true;
        }
        return allowOutsideGame.get() && !context.inLobby(player);
    }
}
