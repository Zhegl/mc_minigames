package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.games.party.DropTowerCourse.Lane;
import me.petr1furious.potatominigames.games.party.DropTowerCourse.Pad;
import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.Component;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
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
    private static final List<Material> BAND_FLOORS = List.of(
            Material.PRISMARINE_BRICKS, Material.LAPIS_BLOCK, Material.PURPUR_BLOCK,
            Material.POLISHED_BLACKSTONE_BRICKS);
    final DropTowerCourse course = DropTowerCourse.generate(random);
    private final Map<UUID, Lane> lanes = new LinkedHashMap<>();
    private ParkourRace race;
    private long settledAt = -1;

    DropTowerSession(SessionServices services, PartyConfig config, PartyRoundContext context) {
        super(services, config, "drop_tower", context);
    }

    @Override
    void buildArena() {
        race = new ParkourRace(names.keySet(), DropTowerCourse.JUMPS);
        int index = 0;
        for (UUID id : names.keySet()) {
            Lane lane = Lane.at(index++);
            lanes.put(id, lane);
            for (int stage = 0; stage < course.pads().size(); stage++) buildPad(lane, stage);
            for (var jump : course.jumps()) {
                for (var baffle : jump.baffles()) {
                    for (int x = baffle.minX(); x <= baffle.maxX(); x++)
                        for (int z = baffle.minZ(); z <= baffle.maxZ(); z++)
                            world.getBlockAt(lane.x() + x, baffle.floor(), lane.z() + z)
                                    .setType(Material.MAGENTA_CONCRETE, false);
                }
            }
        }
    }

    private void buildPad(Lane lane, int stage) {
        Pad pad = course.pads().get(stage);
        boolean finish = stage == DropTowerCourse.JUMPS;
        int radius = pad.radius();
        boolean gap = !finish && course.jumps().get(stage).kind().isGap();
        Material floor = finish ? Material.LIME_CONCRETE
                : gap ? Material.ORANGE_CONCRETE : BAND_FLOORS.get(stage / 5);
        Material light = finish ? Material.VERDANT_FROGLIGHT
                : gap ? Material.SHROOMLIGHT : Material.SEA_LANTERN;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (!pad.tile(x, z)) continue;
                world.getBlockAt(lane.x() + pad.x() + x, pad.floor(), lane.z() + pad.z() + z)
                        .setType(x == 0 && z == 0 ? light : floor, false);
            }
        }
    }

    private Location checkpoint(Lane lane, int stage, Location view) {
        var pad = course.pads().get(stage);
        return new Location(
                world, lane.x() + pad.x() + .5, pad.floor() + 1, lane.z() + pad.z() + .5,
                view.getYaw(), view.getPitch());
    }

    @Override
    Location spawn(int index, int count) {
        Player player = services.participants().onlinePlayers().get(index);
        return checkpoint(lanes.get(player.getUniqueId()), 0, player.getLocation());
    }

    @Override
    Location viewpoint() {
        var start = course.pads().getFirst();
        return new Location(world, start.x() + 10.5, start.floor() + 8, start.z() - 8.5)
                .setDirection(new Vector(-10, -8, 9));
    }

    @Override
    double outHeight() {
        return Double.NEGATIVE_INFINITY;
    }

    @Override
    int durationSeconds() {
        return 180;
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
        return I18n.tr(KEY + "instructions", DropTowerCourse.JUMPS);
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
            double y = at.getY();
            Pad target = course.pads().get(stage + 1);
            boolean inLane = lane.contains(at.getX(), at.getZ());
            boolean grounded = player.isOnGround();
            boolean caught = grounded && course.jumps().get(stage).baffles().stream()
                    .anyMatch(baffle -> baffle.caught(x, y, z));
            if (inLane && grounded && target.landed(x, y, z) && race.reach(id, stage + 1, elapsed)) {
                player.playSound(at, "minecraft:block.note_block.pling", .8f, 1 + stage * .05f);
                if (race.finished(id)) {
                    player.sendMessage(text(I18n.tr(KEY + "finish", seconds(elapsed))));
                    spectate(player);
                } else {
                    player.sendActionBar(I18n.tr(
                            KEY + "landed", stage + 1, DropTowerCourse.JUMPS, hint(stage + 1)));
                }
            } else if (!inLane || target.missed(y) || caught || grounded && onWrongPad(stage, x, y, z)) {
                teleport(player, checkpoint(lane, stage, at));
                player.sendActionBar(I18n.tr(KEY + (caught ? "bar_retry" : "retry"), hint(stage)));
                player.playSound(at, "minecraft:block.note_block.bass", .6f, 1);
            } else if (elapsed % 10 == 0) {
                player.spawnParticle(Particle.END_ROD, lane.x() + target.x() + .5,
                        target.floor() + 1.45, lane.z() + target.z() + .5, 5, .2, .16, .2, .005);
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

    private boolean onWrongPad(int stage, double x, double y, double z) {
        // Walking off a lip may report onGround for one more tick. Require a physical wrong pad.
        for (int i = 0; i < course.pads().size(); i++) {
            if (i != stage && i != stage + 1 && course.pads().get(i).landed(x, y, z)) return true;
        }
        return false;
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
        int stage = race.checkpoint(id);
        Component landings = I18n.tr(KEY + "checkpoint", stage, DropTowerCourse.JUMPS);
        return race.finished(id)
                ? List.of(landings, clock, line)
                : List.of(landings, clock, line, hint(stage));
    }

    private Component hint(int stage) {
        String kind = course.jumps().get(stage).kind().name().toLowerCase(Locale.ROOT);
        return I18n.tr(KEY + "guide_" + kind);
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
