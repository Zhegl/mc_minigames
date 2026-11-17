package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.Component;

import org.bukkit.Location;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.PiglinAbstract;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityCombustEvent;

import java.util.ArrayList;
import java.util.List;

final class MobCountSession extends PartySurvivalSession {
    private enum Phase {
        COUNT,
        READY,
        ANSWER,
        DROP
    }

    private MobCountArena arena;
    private MobCountRound round;
    private Phase phase = Phase.COUNT;
    private long phaseEnd;
    private final List<Entity> mobs = new ArrayList<>();
    private final int countSeconds, answerSeconds, rounds;

    MobCountSession(SessionServices services, PartyConfig config) {
        this(services, config, PartyRoundContext.standalone(services));
    }

    MobCountSession(SessionServices services, PartyConfig config, PartyRoundContext context) {
        super(services, config, "mob_count", context);
        countSeconds = Math.clamp(config.mobCountSeconds.get(), 6, 30);
        answerSeconds = Math.clamp(config.mobCountAnswerSeconds.get(), 4, 20);
        rounds = Math.clamp(config.mobCountRounds.get(), 2, 30);
    }

    @Override
    void buildArena() {
        arena = new MobCountArena(world);
        round = MobCountRound.generate(1, random);
        arena.prepare(round);
    }

    @Override
    Location spawn(int index, int count) {
        return arena.spawn(index, count);
    }

    @Override
    Location viewpoint() {
        return new Location(world, .5, MobCountArena.FLOOR + 19, .5, 0, 90);
    }

    @Override
    double outHeight() {
        return MobCountArena.FLOOR - 4;
    }

    @Override
    int durationSeconds() {
        return rounds * (countSeconds + answerSeconds + 6) + 20;
    }

    @Override
    Component instructions() {
        return I18n.tr("party.rounds.mob_count.instructions", answerSeconds);
    }

    @Override
    void beginPlay() {
        startCount(false);
    }

    private void startCount(boolean next) {
        if (next) {
            round = MobCountRound.generate(round.number() + 1, random);
            arena.prepare(round);
            returnToHub(false);
        }
        phase = Phase.COUNT;
        phaseEnd = elapsed() + MobCountRound.countSeconds(round.number(), countSeconds) * 20L;
        List<Location> spots = arena.countingSpots();
        java.util.Collections.shuffle(spots, random);
        for (var entry : round.counts().entrySet())
            for (int i = 0; i < entry.getValue(); i++) {
                // Different species are mixed across unique cells, rather than lined up by answer.
                Location location = spots.get(mobs.size());
                location.setYaw(random.nextFloat() * 360);
                Entity entity =
                        world.spawnEntity(location, EntityType.valueOf(entry.getKey().name()));
                if (entity instanceof LivingEntity living) {
                    living.setAI(false);
                    living.setCollidable(false);
                    living.setRemoveWhenFarAway(false);
                    if (living.getEquipment() != null) living.getEquipment().clear();
                }
                if (entity instanceof PiglinAbstract piglin) piglin.setImmuneToZombification(true);
                preventDaylightBurn(entity);
                entity.setGravity(false);
                entity.setInvulnerable(true);
                entity.setSilent(true);
                entity.setPersistent(true);
                mobs.add(entity);
            }
        announceTitle(
                I18n.tr("party.rounds.mob_count.round", round.number()),
                I18n.tr("party.rounds.mob_count.count"),
                2);
    }

    static void preventDaylightBurn(Entity mob) {
        if (mob instanceof Zombie zombie) zombie.setShouldBurnInDay(false);
        if (mob instanceof AbstractSkeleton skeleton) skeleton.setShouldBurnInDay(false);
    }

    @EventHandler
    public void preventMobCombustion(EntityCombustEvent event) {
        if (mobs.contains(event.getEntity())) event.setCancelled(true);
    }

    private void returnToHub(boolean freeze) {
        List<Player> players = alivePlayers();
        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            Location start = arena.spawn(i, players.size());
            start.setYaw(player.getLocation().getYaw());
            start.setPitch(player.getLocation().getPitch());
            teleport(player, start);
            if (freeze) freezeMovement(player);
        }
    }

    @Override
    void playTick(long elapsed) {
        if (elapsed < phaseEnd) {
            if (phase == Phase.READY && (phaseEnd - elapsed) % 20 == 0) {
                announceTitle(
                        Component.text(round.target()),
                        I18n.tr("party.rounds.mob_count.ready", (phaseEnd - elapsed) / 20),
                        1);
                for (Player player : alivePlayers())
                    player.playSound(
                            player.getLocation(), "minecraft:block.note_block.hat", .6f, 1.2f);
            }
            return;
        }
        switch (phase) {
            case COUNT -> {
                mobs.forEach(Entity::remove);
                mobs.clear();
                returnToHub(true);
                phase = Phase.READY;
                phaseEnd = elapsed + 60;
                announceTitle(
                        Component.text(round.target()),
                        I18n.tr("party.rounds.mob_count.ready", 3),
                        3);
            }
            case READY -> {
                releaseMovement();
                phase = Phase.ANSWER;
                phaseEnd = elapsed + answerSeconds * 20L;
                announceTitle(
                        Component.text(round.target()),
                        I18n.tr("party.rounds.mob_count.choose"),
                        answerSeconds);
                say(I18n.tr("party.rounds.mob_count.question", round.target(), answerSeconds));
            }
            case ANSWER -> {
                standings.holdResults(true);
                for (Player player : alivePlayers())
                    player.playSound(
                            player.getLocation(), "minecraft:block.note_block.bell", .8f, 1);
                // Only support disappears. The common height check eliminates actual falls,
                // so jumping or reaching the surviving sector can still save a player.
                arena.removeWrong(round);
                phase = Phase.DROP;
                phaseEnd = elapsed + 60;
                announceTitle(
                        I18n.tr(
                                "party.rounds.mob_count.answer",
                                round.answer().title,
                                round.target()),
                        I18n.tr("party.rounds.mob_count.wrong_disappear"),
                        2);
            }
            case DROP -> {
                // Do not restore the floor or teleport someone out of a fall that is still
                // resolving.
                if (alivePlayers().stream()
                        .anyMatch(player -> player.getLocation().getY() < MobCountArena.FLOOR + 1))
                    return;
                standings.holdResults(false);
                if (standings.survivors().isEmpty()
                        || !services.isTest() && standings.survivors().size() == 1) return;
                if (round.number() >= rounds) finish(standings.survivors());
                else startCount(true);
            }
        }
    }

    @Override
    Component status() {
        long left = Math.max(0, (phaseEnd - elapsed() + 19) / 20);
        return switch (phase) {
            case COUNT -> I18n.tr("party.rounds.mob_count.status.count", round.number(), left);
            case READY -> I18n.tr("party.rounds.mob_count.status.ready", round.number(), left);
            case ANSWER ->
                    I18n.tr(
                            "party.rounds.mob_count.status.answer",
                            round.number(),
                            round.target(),
                            left);
            case DROP ->
                    I18n.tr(
                            "party.rounds.mob_count.status.drop",
                            round.number(),
                            round.answer().title);
        };
    }

    @Override
    void cleanup() {
        mobs.forEach(Entity::remove);
        mobs.clear();
        if (arena != null) arena.cleanupLabels();
    }
}
