package me.petr1furious.potatominigames.games.party;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.PiglinAbstract;
import me.petr1furious.potatominigames.core.game.SessionServices;

final class MobCountSession extends PartySurvivalSession {
    private enum Phase { COUNT, ANSWER, DROP }
    private MobCountArena arena;
    private MobCountRound round;
    private Phase phase = Phase.COUNT;
    private long phaseEnd;
    private final List<Entity> mobs = new ArrayList<>();
    private final Set<UUID> failed = new HashSet<>();
    private final int countSeconds, answerSeconds, rounds;

    MobCountSession(SessionServices services, PartyConfig config) {
        super(services, config, "mob_count", "Пересчёт");
        countSeconds = Math.clamp(config.mobCountSeconds.get(), 6, 30);
        answerSeconds = Math.clamp(config.mobCountAnswerSeconds.get(), 4, 20);
        rounds = Math.clamp(config.mobCountRounds.get(), 2, 30);
    }
    @Override void buildArena() {
        arena = new MobCountArena(world);
        round = MobCountRound.generate(1, MobCountArena.PLATFORMS, random);
        arena.prepare(round);
    }
    @Override Location spawn(int index, int count) { return arena.spawn(index * MobCountArena.PLATFORMS / Math.max(1, count)); }
    @Override Location viewpoint() { return new Location(world, .5, MobCountArena.FLOOR + 13, .5, 0, 75); }
    @Override double outHeight() { return MobCountArena.FLOOR - 4; }
    @Override int durationSeconds() { return rounds * (countSeconds + answerSeconds + 3) + 20; }
    @Override String instructions() { return "Пересчёт: запомни, сколько мобов каждого вида! Затем выбери за " + answerSeconds + " секунд платформу с головой того, кого было указанное число. Ошибка — вылет."; }
    @Override void beginPlay() { startCount(false); }
    private void startCount(boolean next) {
        if (next) round = MobCountRound.generate(round.number() + 1, MobCountArena.PLATFORMS, random);
        arena.prepare(round);
        for (Player player : alivePlayers()) teleport(player, arena.nearest(player.getLocation()));
        phase = Phase.COUNT;
        phaseEnd = elapsed() + MobCountRound.countSeconds(round.number(), countSeconds) * 20L;
        List<Integer> cells = new ArrayList<>();
        for (int i = 0; i < 121; i++) cells.add(i);
        java.util.Collections.shuffle(cells, random);
        for (var entry : round.counts().entrySet()) for (int i = 0; i < entry.getValue(); i++) {
            // Different species are mixed across unique cells, rather than lined up by answer.
            int cell = cells.get(mobs.size());
            int x = MobCountArena.MIN + cell % 11 * 2;
            int z = MobCountArena.MIN + cell / 11 * 2;
            Location location = new Location(world, x + .7, MobCountArena.FLOOR + 1.25, z + .7, random.nextFloat() * 360, 0);
            Entity entity = world.spawnEntity(location, EntityType.valueOf(entry.getKey().name()));
            if (entity instanceof LivingEntity living) {
                living.setAI(false); living.setCollidable(false); living.setRemoveWhenFarAway(false);
                if (living.getEquipment() != null) living.getEquipment().clear();
            }
            if (entity instanceof PiglinAbstract piglin) piglin.setImmuneToZombification(true);
            entity.setGravity(false); entity.setInvulnerable(true); entity.setSilent(true); entity.setPersistent(true);
            mobs.add(entity);
        }
        announceTitle("Раунд " + round.number(), "Считай мобов каждого вида!", 2);
    }
    @Override void playTick(long elapsed) {
        if (elapsed < phaseEnd) return;
        switch (phase) {
            case COUNT -> {
                mobs.forEach(Entity::remove); mobs.clear();
                // Starting over a gap must not kill someone before the choice phase begins.
                for (Player player : alivePlayers()) teleport(player, arena.nearest(player.getLocation()));
                arena.removeGlass();
                phase = Phase.ANSWER;
                phaseEnd = elapsed + answerSeconds * 20L;
                announceTitle("" + round.target(), "Кого было столько? Прыгай на нужную голову!", answerSeconds);
                say("Кого было " + round.target() + "? Выбери платформу за " + answerSeconds + " с!");
            }
            case ANSWER -> {
                standings.holdResults(true);
                failed.clear();
                for (Player player : alivePlayers()) {
                    if (!arena.correct(player.getLocation(), round)) failed.add(player.getUniqueId());
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, .8f, 1);
                }
                arena.removeWrong(round);
                phase = Phase.DROP;
                phaseEnd = elapsed + 60;
                announceTitle(round.answer().title + " — " + round.target(), "Неверные платформы исчезают!", 2);
            }
            case DROP -> {
                for (Player player : alivePlayers()) if (failed.contains(player.getUniqueId())) eliminate(player);
                failed.clear();
                standings.holdResults(false);
                if (standings.survivors().isEmpty() || !services.isTest() && standings.survivors().size() == 1) return;
                if (round.number() >= rounds) finish(standings.survivors());
                else startCount(true);
            }
        }
    }
    @Override String status() {
        long left = Math.max(0, (phaseEnd - elapsed() + 19) / 20);
        return "Раунд " + round.number() + " • " + switch (phase) {
            case COUNT -> "Считай! " + left + " с";
            case ANSWER -> "Число " + round.target() + " • " + left + " с";
            case DROP -> "Ответ: " + round.answer().title;
        };
    }
    @Override void cleanup() { mobs.forEach(Entity::remove); mobs.clear(); if (arena != null) arena.cleanupLabels(); }
}
