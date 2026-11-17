package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.Component;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

final class DropTowerSession extends PartySurvivalSession {
    private static final String KEY = "party.drop_and_shoot.drop.";
    private static final int SPACING = 24;
    private static final int LAST_PAD = DropTowerCourse.PADS.size() - 1;
    private final Map<UUID, Lane> lanes = new LinkedHashMap<>();
    private ParkourRace race;
    private long settledAt = -1;

    DropTowerSession(SessionServices services, PartyConfig config, PartyRoundContext context) {
        super(services, config, "drop_tower", context);
    }

    @Override
    void buildArena() {
        race = new ParkourRace(names.keySet(), LAST_PAD);
        int index = 0;
        for (UUID id : names.keySet()) {
            Lane lane = lane(index++);
            lanes.put(id, lane);
            for (int stage = 0; stage <= LAST_PAD; stage++) buildPad(lane, stage);
        }
    }

    private void buildPad(Lane lane, int stage) {
        var pad = DropTowerCourse.PADS.get(stage);
        var next = DropTowerCourse.PADS.get(Math.min(stage + 1, LAST_PAD));
        int dx = Integer.signum(next.x() - pad.x());
        int dz = Integer.signum(next.z() - pad.z());
        int radius = pad.radius();
        Material floor = stage == LAST_PAD ? Material.LIME_CONCRETE : Material.CYAN_CONCRETE;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                Material material = floor;
                if (Math.abs(x) == radius && Math.abs(z) == radius)
                    material = stage == LAST_PAD ? Material.GOLD_BLOCK : Material.PRISMARINE_BRICKS;
                if (stage < LAST_PAD && x == dx * radius && z == dz * radius)
                    material = Material.WHITE_CONCRETE;
                if (x == 0 && z == 0) material = Material.SEA_LANTERN;
                world.getBlockAt(lane.x() + pad.x() + x, pad.floor(), lane.z() + pad.z() + z)
                        .setType(material, false);
            }
        }
    }

    private record Lane(int x, int z) {}

    private static Lane lane(int index) {
        return new Lane(index % 3 * SPACING, index / 3 * SPACING);
    }

    private Location padCenter(Lane lane, int stage) {
        var pad = DropTowerCourse.PADS.get(stage);
        return new Location(
                world, lane.x() + pad.x() + .5, pad.floor() + 1, lane.z() + pad.z() + .5);
    }

    private Location checkpoint(Lane lane, int stage) {
        Location at = padCenter(lane, stage);
        if (stage < LAST_PAD) {
            var next = DropTowerCourse.PADS.get(stage + 1);
            at.setDirection(
                    new Vector(
                            next.x() + lane.x() + .5 - at.getX(),
                            -8,
                            next.z() + lane.z() + .5 - at.getZ()));
        }
        return at;
    }

    @Override
    Location spawn(int index, int count) {
        UUID id = services.participants().onlinePlayers().get(index).getUniqueId();
        return checkpoint(lanes.get(id), 0);
    }

    @Override
    Location viewpoint() {
        return new Location(world, 10.5, 109, -8.5).setDirection(new Vector(-8, -15, 10));
    }

    @Override
    double outHeight() {
        return Double.NEGATIVE_INFINITY;
    }

    @Override
    int durationSeconds() {
        return 65;
    }

    @Override
    long arenaTime() {
        return 6000;
    }

    @Override
    boolean survivalAutoFinish() {
        return false;
    }

    @Override
    boolean podiumAwards() {
        return true;
    }

    @Override
    Component instructions() {
        return I18n.tr(KEY + "instructions", LAST_PAD);
    }

    @Override
    void playTick(long elapsed) {
        for (Player player : alivePlayers()) {
            if (!racing(player)) continue;
            UUID id = player.getUniqueId();
            int stage = race.checkpoint(id);
            Lane lane = lanes.get(id);
            Location at = player.getLocation();
            double x = at.getX() - lane.x();
            double z = at.getZ() - lane.z();
            var target = DropTowerCourse.PADS.get(stage + 1);
            if (target.landed(x, at.getY(), z) && race.reach(id, stage + 1, elapsed)) {
                player.playSound(at, Sound.BLOCK_NOTE_BLOCK_PLING, .8f, 1 + stage * .15f);
                if (race.finished(id)) {
                    player.sendMessage(text(I18n.tr(KEY + "finish", seconds(elapsed))));
                    spectate(player);
                } else {
                    teleport(player, checkpoint(lane, stage + 1));
                    player.sendActionBar(I18n.tr(KEY + "checkpoint", stage + 1, LAST_PAD));
                }
            } else if (target.missed(at.getY()) || Math.abs(x) >= 10 || Math.abs(z) >= 10) {
                teleport(player, checkpoint(lane, stage));
                player.sendActionBar(I18n.tr(KEY + "retry"));
                player.playSound(at, Sound.BLOCK_NOTE_BLOCK_BASS, .6f, 1);
            } else if (elapsed % 6 == 0) {
                player.spawnParticle(
                        Particle.END_ROD,
                        padCenter(lane, stage + 1).add(0, .2, 0),
                        5,
                        target.radius() * .45,
                        .12,
                        target.radius() * .45,
                        .01);
            }
        }
        if (context.eligible().stream().allMatch(race::finished)) {
            if (settledAt < 0) {
                settledAt = elapsed + 40;
                standings.holdResults(true);
            }
            if (elapsed >= settledAt) finish(List.of());
        }
    }

    private boolean racing(Player player) {
        return isInGame(player)
                && player.getGameMode() == GameMode.SURVIVAL
                && player.getWorld().equals(world);
    }

    @Override
    public boolean isInGame(Player player) {
        return race != null && alive(player) && !race.finished(player.getUniqueId());
    }

    @Override
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity().getWorld().equals(world)) event.setCancelled(true);
    }

    @Override
    protected boolean allowTeleport(PlayerTeleportEvent event) {
        return event.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE
                && race.finished(event.getPlayer().getUniqueId())
                && event.getTo().getWorld().equals(world);
    }

    @Override
    Component status() {
        return I18n.tr(
                KEY + "status",
                context.eligible().stream().filter(race::finished).count(),
                Math.max(0, durationSeconds() - elapsed() / 20));
    }

    @Override
    List<Component> sidebarLines(Player player, Component line) {
        UUID id = player.getUniqueId();
        if (!context.eligible().contains(id)) return List.of(line);
        Component clock =
                race.finished(id)
                        ? I18n.tr(KEY + "finish", seconds(race.time(id)))
                        : I18n.tr(KEY + "time", seconds(elapsed()));
        return List.of(I18n.tr(KEY + "checkpoint", race.checkpoint(id), LAST_PAD), clock, line);
    }

    private static String seconds(long ticks) {
        return String.format(Locale.ROOT, "%.2f", ticks / 20.0);
    }

    @Override
    public PartyRoundResult roundResult() {
        return race == null ? PartyRoundResult.empty() : race.result(context.eligible());
    }

    @Override
    void cleanup() {
        if (race != null) race.close();
        lanes.clear();
    }
}
