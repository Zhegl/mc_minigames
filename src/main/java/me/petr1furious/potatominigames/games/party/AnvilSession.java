package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.Component;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.*;

final class AnvilSession extends PartySurvivalSession {
    private final int waveSeconds, moveSeconds, waves, pvpWave;
    private AnvilArena arena;
    private AnvilRain rain;
    private AnvilRound round;
    private final SumoCombat combat = new SumoCombat();

    AnvilSession(SessionServices s, PartyConfig c, PartyRoundContext context) {
        super(s, c, "anvils", context);
        waveSeconds = c.anvilWaveSeconds.get();
        moveSeconds = c.anvilMoveSeconds.get();
        waves = c.anvilWaves.get();
        pvpWave = c.anvilPvpWave.get();
    }

    void buildArena() {
        world.setGameRule(GameRules.PVP, true);
        arena = new AnvilArena(world);
        arena.build();
        round = new AnvilRound(names.size(), waveSeconds * 20, moveSeconds * 20, waves, pvpWave);
        arena.relocateShelters(round.shelters(), round.shelterSize(), random);
        rain = new AnvilRain(world, arena, random, this::eliminate);
    }

    Location spawn(int i, int n) {
        return arena.spawn(i, n);
    }

    Location viewpoint() {
        return new Location(world, .5, 91, .5, 0, 80);
    }

    double outHeight() {
        return 77;
    }

    int durationSeconds() {
        return waves * (waveSeconds + moveSeconds) + 5;
    }

    boolean podiumAwards() {
        return true;
    }

    Component instructions() {
        return I18n.tr("party.rounds.anvils.instructions", pvpWave);
    }

    void beginPlay() {
        round.start(elapsed());
        PartySounds.play(alivePlayers(), Sound.BLOCK_NOTE_BLOCK_BELL, .6f, .8f);
    }

    void playTick(long elapsed) {
        if (round.advance(elapsed)) {
            rain.clear();
            if (round.done()) {
                finish(standings.survivors());
                return;
            }
            if (!round.raining()) {
                arena.relocateShelters(round.shelters(), round.shelterSize(), random);
                announceTitle(
                        I18n.tr("party.rounds.anvils.move"),
                        I18n.tr("party.rounds.anvils.shelters_moved", round.remaining(elapsed)),
                        2);
                PartySounds.play(alivePlayers(), Sound.BLOCK_NOTE_BLOCK_BELL, .7f, 1.2f);
            } else {
                announceTitle(
                        I18n.tr("party.rounds.anvils.wave", round.wave()),
                        I18n.tr(
                                round.pvpEnabled()
                                        ? "party.rounds.anvils.push"
                                        : "party.rounds.anvils.hide"),
                        2);
                PartySounds.play(alivePlayers(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, .35f, 1.5f);
            }
        }
        if (round.raining()) rain.tick(elapsed, round.wave(), alivePlayers());
        else if (elapsed % 20 == 0)
            PartySounds.countdown(alivePlayers(), round.remaining(elapsed) * 20);
    }

    Component status() {
        return I18n.tr(
                "party.rounds.anvils.status",
                round.wave(),
                I18n.tr(round.raining() ? "party.rounds.anvils.rain" : "party.rounds.anvils.move"),
                round.remaining(elapsed()));
    }

    @Override
    protected void handleDamage(EntityDamageEvent e) {
        if (e instanceof EntityDamageByEntityEvent attack) handleAttack(attack);
        e.setCancelled(true);
    }

    @Override
    protected void handleAttack(EntityDamageByEntityEvent e) {
        e.setCancelled(true);
        if (!playing()
                || !round.pvpEnabled()
                || !(e.getEntity() instanceof Player victim)
                || !(e.getDamager() instanceof Player attacker)
                || !alive(victim)
                || !alive(attacker)
                || victim.equals(attacker)) return;
        combat.hit(victim,
                victim.getLocation().toVector().subtract(attacker.getLocation().toVector()),
                attacker.isSprinting(), 0, Bukkit.getCurrentTick());
    }

    void cleanup() {
        if (rain != null) rain.clear();
        combat.reset();
    }
}
