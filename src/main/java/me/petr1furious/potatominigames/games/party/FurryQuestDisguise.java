package me.petr1furious.potatominigames.games.party;

import java.lang.reflect.Method;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** The player walks normally; other clients track the animal at the player's feet. */
final class FurryQuestDisguise {
    private static final NamespacedKey FORM = new NamespacedKey("potatominigames", "furry_quest_form");
    private static final double STANDING_EYE_HEIGHT = 1.62;

    final Player player;
    final Animals animal;
    private final Object playerHandle, animalHandle;
    private final boolean wasVisible, wasSilent;

    FurryQuestDisguise(Player player, Animals animal) {
        this.player = player;
        this.animal = animal;
        wasVisible = player.isVisibleByDefault();
        wasSilent = player.isSilent();
        try {
            playerHandle = Motion.handle.invoke(player);
            animalHandle = Motion.handle.invoke(animal);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not bind Furry Quest disguise", e);
        }
    }

    void apply(Plugin plugin) {
        player.setVisibleByDefault(false);
        player.setSilent(true);
        player.hideEntity(plugin, animal);
        animal.getPathfinder().stopPathfinding();
        animal.setAware(false);
        animal.setGravity(false);
        animal.setCollidable(false);
        scale(Attribute.SCALE, animal.getEyeHeight() / STANDING_EYE_HEIGHT);
        // Vanilla mob walking uses the movement attribute for both forward input and ground acceleration.
        double pace = animal.getAttribute(Attribute.MOVEMENT_SPEED).getValue();
        scale(Attribute.MOVEMENT_SPEED, pace * pace);
    }

    private void scale(Attribute attribute, double target) {
        var instance = player.getAttribute(attribute);
        instance.removeModifier(FORM);
        instance.addTransientModifier(new AttributeModifier(FORM, target / instance.getBaseValue() - 1,
                AttributeModifier.Operation.MULTIPLY_SCALAR_1));
    }

    void sync() {
        try {
            // Let the normal entity tracker send relative movement so observers see an interpolated animal gait.
            Motion.copyPosition.invoke(animalHandle, playerHandle);
            Motion.onGround.invoke(animalHandle, player.isOnGround());
            animal.setBodyYaw(player.getBodyYaw());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not move Furry Quest disguise", e);
        }
    }

    void restore(Plugin plugin) {
        player.getAttribute(Attribute.SCALE).removeModifier(FORM);
        player.getAttribute(Attribute.MOVEMENT_SPEED).removeModifier(FORM);
        player.showEntity(plugin, animal);
        player.setSilent(wasSilent);
        player.setVisibleByDefault(wasVisible);
    }

    private static final class Motion {
        static final Method handle, copyPosition, onGround;
        static {
            try {
                Class<?> entity = Class.forName("net.minecraft.world.entity.Entity");
                handle = Class.forName("org.bukkit.craftbukkit.entity.CraftEntity").getMethod("getHandle");
                copyPosition = entity.getMethod("copyPosition", entity);
                onGround = entity.getMethod("setOnGround", boolean.class);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Animal movement is unavailable", e);
            }
        }
    }
}
