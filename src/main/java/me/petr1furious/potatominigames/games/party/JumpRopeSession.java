package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class JumpRopeSession extends PartySurvivalSession {
    private static final String KEY = "party.tag_and_timing.jump_rope.";
    private static final int FLOOR = 80;
    private static final int SCORED_SWEEPS = 7;
    private static final int PAUSE_TICKS = 30;

    private final Map<UUID, BoundingBox> lastBodies = new HashMap<>();
    private final Set<UUID> practiceMisses = new HashSet<>();
    private BlockDisplay rope;
    private double ropeZ = JumpRopeSweep.position(1, 0, 1);
    private int direction = 1;
    private int stage;
    private long sweepStartsAt;

    JumpRopeSession(SessionServices services, PartyConfig config, PartyRoundContext context) {
        super(services, config, "jump_rope", context);
    }

    @Override
    void buildArena() {
        int halfWidth = (int) JumpRopeSweep.HALF_WIDTH;
        for (int x = -halfWidth - 1; x <= halfWidth; x++) {
            for (int z = -10; z <= 9; z++) {
                boolean wall = x == -halfWidth - 1 || x == halfWidth || z == -10 || z == 9;
                boolean stripe = Math.floorDiv(z + 9, 3) % 2 == 0;
                Material tile =
                        wall
                                ? Material.CYAN_CONCRETE
                                : stripe ? Material.BLUE_TERRACOTTA : Material.BLACK_TERRACOTTA;
                world.getBlockAt(x, FLOOR, z).setType(tile, false);
                if (wall) {
                    for (int y = FLOOR + 1; y <= FLOOR + 3; y++)
                        world.getBlockAt(x, y, z).setType(Material.LIGHT_BLUE_STAINED_GLASS, false);
                }
            }
        }
        rope =
                world.spawn(
                        ropeLocation(ropeZ),
                        BlockDisplay.class,
                        display -> {
                            display.setBlock(Material.YELLOW_CONCRETE.createBlockData());
                            display.setBrightness(new Display.Brightness(15, 15));
                            display.setDisplayWidth(24);
                            display.setDisplayHeight(1);
                            display.setTeleportDuration(1);
                            display.setPersistent(false);
                            display.setTransformation(
                                    new Transformation(
                                            new Vector3f(),
                                            new Quaternionf(),
                                            new Vector3f(
                                                    (float) (2 * JumpRopeSweep.HALF_WIDTH),
                                                    (float) JumpRopeSweep.HEIGHT,
                                                    (float) (2 * JumpRopeSweep.HALF_DEPTH)),
                                            new Quaternionf()));
                        });
    }

    private Location ropeLocation(double z) {
        return new Location(
                world,
                -JumpRopeSweep.HALF_WIDTH,
                FLOOR + 1 + JumpRopeSweep.BOTTOM,
                z - JumpRopeSweep.HALF_DEPTH);
    }

    @Override
    Location spawn(int index, int count) {
        int row = index / 4;
        int rowSize = Math.min(4, count - row * 4);
        double x = (index % 4 - (rowSize - 1) / 2.0) * 2.75;
        double z = (row - ((count - 1) / 4) / 2.0) * 2.5;
        return new Location(world, x, FLOOR + 1, z, 180, 0);
    }

    @Override
    Location viewpoint() {
        return new Location(world, 0, 93, 0, 0, 80);
    }

    @Override
    double outHeight() {
        return FLOOR - 4;
    }

    @Override
    long arenaTime() {
        return 6000;
    }

    @Override
    int durationSeconds() {
        return 45;
    }

    @Override
    boolean podiumAwards() {
        return true;
    }

    @Override
    boolean survivalAutoFinish() {
        // Sweep the rest of the lane before declaring the last survivor.
        return elapsed() < sweepStartsAt || standings.survivors().isEmpty();
    }

    @Override
    Component instructions() {
        return I18n.tr(KEY + "rules");
    }

    @Override
    void beginPlay() {
        sweepStartsAt = PAUSE_TICKS;
    }

    @Override
    void playTick(long elapsed) {
        long age = elapsed - sweepStartsAt;
        int sweepTicks = JumpRopeSweep.ticks(stage);
        double nextZ = age < 0 ? ropeZ : JumpRopeSweep.position(direction, age, sweepTicks);
        if (age == 0) {
            world.playSound(
                    new Location(world, 0, FLOOR + 1, nextZ),
                    Sound.BLOCK_NOTE_BLOCK_BELL,
                    1.25f,
                    stage == 0 ? .75f : 1.25f);
        }
        if (nextZ != ropeZ) rope.teleport(ropeLocation(nextZ));
        Component prompt = status().color(stage == 0 ? NamedTextColor.AQUA : NamedTextColor.YELLOW);
        for (Player player : alivePlayers()) {
            if (!player.getWorld().equals(world)) continue;
            BoundingBox current = player.getBoundingBox();
            BoundingBox last = lastBodies.put(player.getUniqueId(), current);
            if (elapsed % 10 == 0) player.sendActionBar(prompt);
            if (age < 0
                    || !JumpRopeSweep.hits(
                            last == null ? current : last, current, ropeZ, nextZ, FLOOR + 1))
                continue;
            if (stage > 0) eliminate(player);
            else if (practiceMisses.add(player.getUniqueId())) {
                player.sendMessage(I18n.tr(KEY + "practice_hit").color(NamedTextColor.GRAY));
                player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, .45f, .9f);
            }
        }
        ropeZ = nextZ;
        if (age < sweepTicks) return;
        if (stage == SCORED_SWEEPS) {
            finish(standings.survivors());
            return;
        }
        stage++;
        direction = -direction;
        sweepStartsAt = elapsed + PAUSE_TICKS;
        if (stage == 1) {
            say(I18n.tr(KEY + "scoring"));
            PartySounds.play(alivePlayers(), Sound.BLOCK_NOTE_BLOCK_PLING, .75f, 1.3f);
        }
    }

    @Override
    Component status() {
        if (stage == 0) return I18n.tr(KEY + "practice");
        return I18n.tr(
                KEY + (elapsed() < sweepStartsAt ? "turn" : "stage"), stage, SCORED_SWEEPS);
    }

    @Override
    void playerOut(Player player) {
        lastBodies.remove(player.getUniqueId());
    }

    @Override
    void cleanup() {
        if (rope != null) rope.remove();
        lastBodies.clear();
    }
}
