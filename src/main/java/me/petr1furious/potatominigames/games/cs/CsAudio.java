package me.petr1furious.potatominigames.games.cs;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Objects;

/** Shared distance-bounded positional sound delivery for match participants and spectators. */
final class CsAudio {
    record Gunshot(Sound sound, float pitch) {}

    /** Short vanilla samples with a distinct sample/pitch pair for every weapon. */
    static Gunshot gunshot(CsWeapon weapon) {
        return switch (weapon) {
            case GLOCK -> new Gunshot(Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.8f);
            case USP -> new Gunshot(Sound.BLOCK_NOTE_BLOCK_HAT, 1.25f);
            case P250 -> new Gunshot(Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.5f);
            case DUAL_BERETTAS -> new Gunshot(Sound.ITEM_CROSSBOW_SHOOT, 1.7f);
            case FIVE_SEVEN -> new Gunshot(Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.95f);
            case TEC9 -> new Gunshot(Sound.ITEM_CROSSBOW_SHOOT, 1.4f);
            case CZ75 -> new Gunshot(Sound.BLOCK_NOTE_BLOCK_SNARE, 1.65f);
            case DEAGLE -> new Gunshot(Sound.ENTITY_FIREWORK_ROCKET_BLAST, .85f);
            case MAC10 -> new Gunshot(Sound.ITEM_CROSSBOW_SHOOT, 1.8f);
            case MP9 -> new Gunshot(Sound.ITEM_CROSSBOW_SHOOT, 2f);
            case UMP45 -> new Gunshot(Sound.ITEM_CROSSBOW_SHOOT, .9f);
            case MP7 -> new Gunshot(Sound.BLOCK_NOTE_BLOCK_SNARE, 1.35f);
            case P90 -> new Gunshot(Sound.BLOCK_NOTE_BLOCK_SNARE, 1.9f);
            case GALIL -> new Gunshot(Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.35f);
            case FAMAS -> new Gunshot(Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.65f);
            case AK47 -> new Gunshot(Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, .9f);
            case M4A1S -> new Gunshot(Sound.BLOCK_NOTE_BLOCK_HAT, .75f);
            case M4A4 -> new Gunshot(Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.15f);
            case AUG -> new Gunshot(Sound.ENTITY_IRON_GOLEM_HURT, 1.8f);
            case SG553 -> new Gunshot(Sound.ENTITY_IRON_GOLEM_HURT, 1.45f);
            case SSG08 -> new Gunshot(Sound.ENTITY_FIREWORK_ROCKET_BLAST, .65f);
            case AWP -> new Gunshot(Sound.ENTITY_GENERIC_EXPLODE, .65f);
            case NOVA -> new Gunshot(Sound.ENTITY_GENERIC_EXPLODE, 1f);
            case XM1014 -> new Gunshot(Sound.ENTITY_GENERIC_EXPLODE, 1.4f);
            case MAG7 -> new Gunshot(Sound.ENTITY_GENERIC_EXPLODE, 1.75f);
            case SAWED_OFF -> new Gunshot(Sound.ENTITY_GENERIC_EXPLODE, .8f);
            case NEGEV -> new Gunshot(Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, .65f);
        };
    }

    static void shot(Collection<Player> listeners, Location at, CsWeapon weapon) {
        Gunshot profile = gunshot(weapon);
        play(listeners, at, profile.sound(), weapon.soundRadius(), profile.pitch());
    }

    static boolean audible(Location source, Location listener, int radius) {
        if (source == null
                || listener == null
                || !Objects.equals(source.getWorld(), listener.getWorld())) return false;
        double dx = source.getX() - listener.getX();
        double dy = source.getY() - listener.getY();
        double dz = source.getZ() - listener.getZ();
        return dx * dx + dy * dy + dz * dz < (double) radius * radius;
    }

    static void play(
            Collection<Player> listeners, Location at, Sound sound, int radius, float pitch) {
        if (at == null) return;
        // Native variable-range sounds attenuate over 16 * volume blocks. Keep the actual
        // source position so the client can pan it as the listener turns or moves.
        float volume = radius / 16f;
        for (Player listener : listeners)
            if (audible(at, listener.getEyeLocation(), radius))
                listener.playSound(at, sound, SoundCategory.PLAYERS, volume, pitch);
    }

    private CsAudio() {}
}
