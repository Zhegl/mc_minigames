package me.petr1furious.potatominigames.games.tanks;

import io.papermc.paper.entity.TeleportFlag;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.TextDisplay;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Vanilla-client tracked vehicles: hull, traverse and gun elevation animate separately. */
final class TankVisual {
    private enum Frame {
        HULL,
        TURRET,
        GUN
    }

    private enum Motion {
        FIXED,
        WHEEL,
        TREAD
    }

    private record Part(
            BlockDisplay display,
            Frame frame,
            float x,
            float y,
            float z,
            float width,
            float height,
            float depth,
            float pitch,
            float roll,
            Motion motion) {}

    private final World world;
    private final TanksRules.Chassis chassis;
    private final List<Entity> entities = new ArrayList<>();
    private final List<Part> parts = new ArrayList<>();
    private Interaction seat;
    private final TextDisplay health;
    private final float scale;
    private final NamedTextColor teamColor;
    private Location hull;
    private float turretYaw, turretPitch;
    private boolean wrecked, removed;
    private int shownHp = -1, shownMax = -1;
    private float lastHullYaw = Float.NaN, lastTurretYaw = Float.NaN, lastPitch = Float.NaN;
    private double leftTravel, rightTravel;

    TankVisual(Location location, int side, TanksRules.Chassis chassis) {
        this.world = location.getWorld();
        this.chassis = chassis;
        this.hull = location.clone();
        this.turretYaw = location.getYaw();
        this.scale = (float) TanksRules.geometry(chassis).scale();
        this.teamColor = side == 0 ? NamedTextColor.RED : NamedTextColor.BLUE;
        Material armor = side == 0 ? Material.GREEN_TERRACOTTA : Material.CYAN_TERRACOTTA;
        Material darkArmor = side == 0 ? Material.GREEN_CONCRETE : Material.CYAN_CONCRETE;
        Material team = side == 0 ? Material.RED_CONCRETE : Material.BLUE_CONCRETE;
        seat = createSeat();
        entities.add(seat);
        // Long track belts, beveled ends, five distinct road wheels and bright axle hubs.
        for (int sign : new int[] {-1, 1}) {
            box(Frame.HULL, Material.POLISHED_BLACKSTONE, sign * 1.25f, .43f, 0, .62f, .76f, 3.65f);
            box(
                    Frame.HULL,
                    Material.DEEPSLATE_TILES,
                    sign * 1.25f,
                    .42f,
                    1.77f,
                    .62f,
                    .5f,
                    .62f,
                    -32,
                    0);
            box(
                    Frame.HULL,
                    Material.DEEPSLATE_TILES,
                    sign * 1.25f,
                    .42f,
                    -1.77f,
                    .62f,
                    .5f,
                    .62f,
                    32,
                    0);
            for (int i = -2; i <= 2; i++) {
                box(
                        Frame.HULL,
                        Material.POLISHED_DEEPSLATE,
                        sign * 1.58f,
                        .43f,
                        i * .68f,
                        .13f,
                        .46f,
                        .46f,
                        45,
                        0,
                        Motion.WHEEL);
                box(
                        Frame.HULL,
                        Material.LIGHT_GRAY_CONCRETE,
                        sign * 1.625f,
                        .43f,
                        i * .68f,
                        .04f,
                        .2f,
                        .2f,
                        0,
                        0,
                        Motion.WHEEL);
            }
            for (int i = 0; i < 4; i++)
                box(
                        Frame.HULL,
                        Material.GRAY_CONCRETE,
                        sign * 1.25f,
                        .44f,
                        (float) (i * Math.PI / 2),
                        .6f,
                        .08f,
                        .15f,
                        0,
                        0,
                        Motion.TREAD);
            box(Frame.HULL, armor, sign * 1.22f, .92f, -.12f, .65f, .13f, 3.55f);
            box(Frame.HULL, Material.IRON_BLOCK, sign * .95f, 1.03f, 1.54f, .23f, .15f, .12f);
        }
        box(Frame.HULL, darkArmor, 0, .65f, -.08f, 2.5f, .46f, 3.65f);
        box(Frame.HULL, armor, 0, 1.0f, -.15f, 2.7f, .45f, 2.65f);
        box(Frame.HULL, armor, 0, 1.0f, 1.42f, 2.65f, .24f, .9f, -26, 0);
        box(Frame.HULL, darkArmor, 0, 1.02f, -1.65f, 2.4f, .35f, .5f, 15, 0);
        box(Frame.HULL, Material.POLISHED_BLACKSTONE_BRICKS, 0, 1.28f, -1.14f, 1.25f, .07f, .72f);
        box(Frame.HULL, team, 0, .87f, -1.95f, 1.1f, .22f, .06f);
        box(Frame.HULL, Material.DEEPSLATE_BRICKS, -.85f, 1.18f, -1.62f, .16f, .28f, .2f);
        box(Frame.HULL, Material.DEEPSLATE_BRICKS, .85f, 1.18f, -1.62f, .16f, .28f, .2f);
        // Rounded impression from a broad lower turret and narrower sloped upper armor.
        box(Frame.TURRET, darkArmor, 0, -.07f, -.15f, 1.95f, .28f, 1.8f);
        box(Frame.TURRET, armor, 0, .2f, -.2f, 1.75f, .55f, 1.5f);
        box(Frame.TURRET, armor, 0, .23f, .57f, 1.7f, .42f, .4f, -20, 0);
        box(Frame.TURRET, darkArmor, -.86f, .25f, -.15f, .16f, .4f, 1.35f, 0, -15);
        box(Frame.TURRET, darkArmor, .86f, .25f, -.15f, .16f, .4f, 1.35f, 0, 15);
        box(Frame.TURRET, Material.POLISHED_ANDESITE, -.25f, .53f, -.22f, .65f, .12f, .65f);
        box(Frame.TURRET, Material.BLACK_CONCRETE, .4f, .59f, -.36f, .12f, .18f, .28f);
        box(Frame.TURRET, team, -.96f, .2f, -.25f, .035f, .28f, .55f);
        box(Frame.TURRET, team, .96f, .2f, -.25f, .035f, .28f, .55f);
        // Barrel is anchored to the same pivot as muzzleAt; its end is never a guessed location.
        float length = barrelLength(chassis);
        box(Frame.GUN, darkArmor, 0, 0, .65f, .56f, .46f, .5f);
        box(Frame.GUN, Material.POLISHED_ANDESITE, 0, 0, .8f + length / 2, .22f, .22f, length);
        box(Frame.GUN, Material.POLISHED_BLACKSTONE, 0, 0, .8f + length - .1f, .34f, .31f, .3f);
        box(Frame.GUN, Material.BLACK_CONCRETE, 0, 0, .8f + length + .056f, .19f, .16f, .014f);
        health =
                world.spawn(
                        location.clone().add(0, 3.3, 0),
                        TextDisplay.class,
                        display -> {
                            configure(display);
                            display.setBillboard(Display.Billboard.CENTER);
                            display.setShadowed(true);
                            display.setSeeThrough(false);
                            display.text(Component.empty());
                        });
        entities.add(health);
        update(location, turretYaw, 0, 1, 1);
    }

    private void box(
            Frame frame, Material material, float x, float y, float z, float w, float h, float d) {
        box(frame, material, x, y, z, w, h, d, 0, 0);
    }

    private void box(
            Frame frame,
            Material material,
            float x,
            float y,
            float z,
            float w,
            float h,
            float d,
            float pitch,
            float roll) {
        box(frame, material, x, y, z, w, h, d, pitch, roll, Motion.FIXED);
    }

    private void box(
            Frame frame,
            Material material,
            float x,
            float y,
            float z,
            float w,
            float h,
            float d,
            float pitch,
            float roll,
            Motion motion) {
        BlockDisplay display =
                world.spawn(
                        hull.clone().setDirection(new org.bukkit.util.Vector(0, 0, 1)),
                        BlockDisplay.class,
                        entity -> {
                            configure(entity);
                            entity.setBlock(material.createBlockData());
                        });
        parts.add(new Part(display, frame, x, y, z, w, h, d, pitch, roll, motion));
        entities.add(display);
    }

    private static void configure(Display display) {
        display.setPersistent(false);
        display.setVisibleByDefault(false);
        display.setInvulnerable(true);
        display.setGravity(false);
        display.setSilent(true);
        display.setViewRange(3);
        display.setInterpolationDuration(2);
        display.setInterpolationDelay(0);
        display.setTeleportDuration(1);
        display.setDisplayWidth(7);
        display.setDisplayHeight(5);
    }

    void update(Location location, float yaw, float pitch, double hp, double maxHp) {
        if (removed) return;
        double dx = location.getX() - hull.getX(), dz = location.getZ() - hull.getZ();
        boolean moved = hull.distanceSquared(location) > 1e-8;
        double heading = Math.toRadians(location.getYaw());
        double travel = (-Math.sin(heading) * dx + Math.cos(heading) * dz) / scale;
        double turn =
                Math.toRadians(TanksRules.angleDelta(hull.getYaw(), location.getYaw())) * 1.25;
        if (!wrecked) {
            leftTravel += travel - turn;
            rightTravel += travel + turn;
        }
        hull = location.clone();
        turretYaw = yaw;
        turretPitch = Math.clamp(pitch, -25, 18);
        float effectiveYaw = wrecked ? yaw + 28 : yaw;
        float effectivePitch = wrecked ? 18 : turretPitch;
        boolean hullTurned = hull.getYaw() != lastHullYaw;
        boolean turretTurned = effectiveYaw != lastTurretYaw || effectivePitch != lastPitch;
        Location anchor = new Location(world, hull.getX(), hull.getY(), hull.getZ(), 0, 0);
        for (Part part : parts) {
            if (moved) part.display.teleport(anchor);
            if ((part.frame == Frame.HULL ? hullTurned : turretTurned)
                    || (!wrecked && part.motion != Motion.FIXED && moved)) {
                Matrix4f matrix =
                        new Matrix4f()
                                .rotateY(
                                        (float)
                                                Math.toRadians(
                                                        -(part.frame == Frame.HULL
                                                                ? hull.getYaw()
                                                                : effectiveYaw)));
                if (part.frame != Frame.HULL) matrix.translate(0, turretHeight(chassis), 0);
                if (part.frame == Frame.GUN) matrix.rotateX((float) Math.toRadians(effectivePitch));
                double movement = part.x < 0 ? leftTravel : rightTravel;
                float py = part.y, pz = part.z, localPitch = part.pitch;
                if (part.motion == Motion.WHEEL)
                    localPitch += (float) Math.toDegrees(movement / .27);
                if (part.motion == Motion.TREAD) {
                    double phase = part.z + movement / 1.9;
                    py = (float) (.44 + .4 * Math.sin(phase));
                    pz = (float) (1.9 * Math.cos(phase));
                    localPitch =
                            (float)
                                    Math.toDegrees(
                                            Math.atan2(
                                                    -.4 * Math.cos(phase), -1.9 * Math.sin(phase)));
                }
                matrix.scale(scale)
                        .translate(part.x, py, pz)
                        .rotateX((float) Math.toRadians(localPitch))
                        .rotateZ((float) Math.toRadians(part.roll))
                        .scale(part.width, part.height, part.depth)
                        .translate(-.5f, -.5f, -.5f);
                part.display.setTransformationMatrix(matrix);
                part.display.setInterpolationDelay(0);
            }
        }
        if (moved || hullTurned) {
            // Fixed anchor yaw decouples steering from the rider's mouse aim.
            seat.teleport(seatAt(hull), TeleportFlag.EntityState.RETAIN_PASSENGERS);
            health.teleport(hull.clone().add(0, 3.3, 0));
        }
        lastHullYaw = hull.getYaw();
        lastTurretYaw = effectiveYaw;
        lastPitch = effectivePitch;
        int current = (int) Math.ceil(Math.max(0, hp)),
                maximum = (int) Math.ceil(Math.max(1, maxHp));
        if (!wrecked && (current != shownHp || maximum != shownMax)) {
            shownHp = current;
            shownMax = maximum;
            double ratio = hp / Math.max(1, maxHp);
            NamedTextColor color =
                    ratio > .6
                            ? NamedTextColor.GREEN
                            : ratio > .3 ? NamedTextColor.YELLOW : NamedTextColor.RED;
            String rank =
                    switch (chassis) {
                        case LIGHT -> "Ⅰ";
                        case MEDIUM -> "Ⅱ";
                        case HEAVY -> "Ⅲ";
                    };
            health.text(
                    Component.text("◆ ", teamColor)
                            .append(
                                    Component.text(
                                            rank + "  " + current + " / " + maximum, color)));
        }
    }

    Location muzzle() {
        return muzzleAt(hull, turretYaw, turretPitch, chassis);
    }

    static Location muzzleAt(Location hull, float yaw, float pitch, TanksRules.Chassis chassis) {
        float scale = (float) TanksRules.geometry(chassis).scale();
        double reach = (.8 + barrelLength(chassis) + .08) * scale;
        double yr = Math.toRadians(yaw), pr = Math.toRadians(Math.clamp(pitch, -25, 18));
        return hull.clone()
                .add(
                        -Math.sin(yr) * Math.cos(pr) * reach,
                        turretHeight(chassis) - Math.sin(pr) * reach,
                        Math.cos(yr) * Math.cos(pr) * reach)
                .setDirection(
                        new org.bukkit.util.Vector(
                                -Math.sin(yr) * Math.cos(pr),
                                -Math.sin(pr),
                                Math.cos(yr) * Math.cos(pr)));
    }

    private static float barrelLength(TanksRules.Chassis chassis) {
        return switch (chassis) {
            case LIGHT -> 2.15f;
            case MEDIUM -> 2.65f;
            case HEAVY -> 3f;
        };
    }

    private static float turretHeight(TanksRules.Chassis chassis) {
        return (float) TanksRules.geometry(chassis).turretPivotY();
    }

    private Location seatAt(Location location) {
        double a = Math.toRadians(location.getYaw());
        return new Location(
                world,
                location.getX() + Math.sin(a) * .35 * scale,
                // Player's .6-block riding attachment is subtracted from this anchor.
                // Keep the 1.62-block eye above the roof and held gear within the turret.
                location.getY() + TanksRules.geometry(chassis).turretTop() + .28 - (1.62 - .6),
                location.getZ() - Math.cos(a) * .35 * scale,
                0,
                0);
    }

    private Interaction createSeat() {
        return world.spawn(
                seatAt(hull),
                Interaction.class,
                stand -> {
                    // Nonliving seats preserve vehicle input without native mount HP.
                    stand.setInteractionWidth(0);
                    stand.setInteractionHeight(0);
                    stand.setResponsive(false);
                    stand.setInvulnerable(true);
                    stand.setGravity(false);
                    stand.setSilent(true);
                    stand.setPersistent(false);
                    stand.setVisibleByDefault(false);
                });
    }

    void wreck() {
        if (wrecked || removed) return;
        wrecked = true;
        seat.eject();
        for (int i = 0; i < parts.size(); i++)
            parts.get(i)
                    .display
                    .setBlock(
                            (i % 4 == 0 ? Material.COAL_BLOCK : Material.POLISHED_BLACKSTONE)
                                    .createBlockData());
        health.text(Component.text("✕", NamedTextColor.DARK_GRAY));
        update(hull, turretYaw, turretPitch, 0, 1);
        world.spawnParticle(Particle.LARGE_SMOKE, hull.clone().add(0, 1.5, 0), 14, .6, .5, .6, .05);
        world.spawnParticle(Particle.FLAME, hull.clone().add(0, 1.1, 0), 9, .55, .3, .55, .025);
    }

    void remove() {
        if (removed) return;
        removed = true;
        seat.eject();
        for (Entity entity : entities) entity.remove();
        entities.clear();
        parts.clear();
    }

    Collection<Entity> entities() {
        return List.copyOf(entities);
    }

    Entity seat() {
        if (!removed && !wrecked && !seat.isValid()) {
            // Paper can remove a player's root mount during logout. Keep the model and
            // combat state, replacing only its missing passenger anchor on the next attach.
            entities.remove(seat);
            seat.remove();
            seat = createSeat();
            entities.add(seat);
        }
        return seat;
    }

    Entity healthLabel() {
        return health;
    }
}
