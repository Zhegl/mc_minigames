package me.petr1furious.potatominigames.games.tanks;

import io.papermc.paper.event.player.PlayerArmSwingEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;

import me.petr1furious.potatominigames.core.command.CommandFailure;
import me.petr1furious.potatominigames.core.game.*;
import me.petr1furious.potatominigames.core.team.TeamColor;
import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.potion.*;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.*;

/** Server-authoritative vehicles; the vanilla player's health never represents tank armor. */
final class TanksSession implements GameSession, Listener {
    private static final class Vehicle {
        Location at;
        TankVisual visual;
        float turretYaw, turretPitch;
        double speed, trackSpeed;
        int settled;
        long revealedUntil;
        long smokeUntil;
        Component feedback = Component.empty();
        long feedbackUntil;
        TanksRules.Chassis chassis;

        Vehicle(Location at) {
            this.at = at;
            turretYaw = at.getYaw();
        }
    }

    private static final class ShellFlight {
        final UUID owner;
        final TanksRules.Chassis chassis;
        final TanksRules.Shell shell;
        final TanksMatch.Spotters spotters;
        Location at;
        Vector velocity;
        int age;

        ShellFlight(
                TanksMatch.Actor shooter,
                Location at,
                Vector velocity,
                TanksMatch.Spotters spotters) {
            this.owner = shooter.id();
            this.chassis = shooter.chassis();
            this.shell = shooter.shell();
            this.at = at;
            this.velocity = velocity;
            this.spotters = spotters;
        }
    }

    private record Smoke(Location at, long until) {}

    private final SessionServices services;
    private final TanksConfig config;
    private final TanksGame owner;
    private final Map<UUID, Vehicle> vehicles = new LinkedHashMap<>();
    private final Map<UUID, String> names = new LinkedHashMap<>();
    private final Map<UUID, Player> connections = new HashMap<>();
    private final Map<UUID, Input> inputs = new HashMap<>();
    private final List<ShellFlight> shells = new ArrayList<>();
    private final List<Smoke> smoke = new ArrayList<>();
    private final Random random = new Random();
    private final TanksScope scopes = new TanksScope();
    private final TanksAudio audio = new TanksAudio();
    private TanksMatch match;
    private TanksArena arena;
    private TanksRadar radar;
    private World world;
    private BukkitTask task;
    private BossBar timer;
    private final BossBar[] captures = new BossBar[2];
    private long tick, liveAt, finishAt = -1;
    private boolean built, closed, internalTeleport;
    private List<Component> results = List.of();

    TanksSession(SessionServices services, TanksConfig config, TanksGame owner) {
        this.services = services;
        this.config = config;
        this.owner = owner;
    }

    static Component tr(String key, Object... args) {
        return I18n.tr("tanks." + key, args);
    }

    private TanksMatch.Actor actor(Player p) {
        return match == null ? null : match.actor(p.getUniqueId());
    }

    private Player player(UUID id) {
        return connections.get(id);
    }

    private boolean active(Player p) {
        var a = actor(p);
        return !closed
                && built
                && a != null
                && a.alive()
                && a.online()
                && connections.get(a.id()) == p;
    }

    private Component team(int side) {
        return tr(side == 0 ? "team.red" : "team.blue");
    }

    private void say(Component text) {
        services.messages().broadcast(text);
    }

    @Override
    public void onStart() throws CommandFailure {
        try {
            start();
        } catch (CommandFailure | RuntimeException failure) {
            onEnd(EndReason.ABORTED);
            throw failure;
        }
    }

    private void start() throws CommandFailure {
        var roster = new ArrayList<>(services.participants().all());
        if (roster.isEmpty() || roster.size() > 12 || !services.isTest() && roster.size() < 2)
            throw new CommandFailure(tr("error.roster"));
        match =
                new TanksMatch(
                        config.battle.get() * 20, config.capture.get() * 20, services.isTest());
        int[] counts = {0, 0};
        for (var p : roster) {
            int side =
                    p.team() == TeamColor.RED
                            ? 0
                            : p.team() == TeamColor.BLUE ? 1 : counts[0] <= counts[1] ? 0 : 1;
            match.add(p.id(), side, TanksRules.Chassis.MEDIUM);
            counts[side]++;
            names.put(p.id(), p.name());
        }
        // Respect picks until they would leave the battle unplayable or materially unbalanced.
        while (Math.abs(counts[0] - counts[1]) > 1) {
            int big = counts[0] > counts[1] ? 0 : 1;
            UUID move =
                    match.actors().stream()
                            .filter(a -> a.team() == big)
                            .map(TanksMatch.Actor::id)
                            .findFirst()
                            .orElseThrow();
            // Initial teams are immutable in the model; rebuild before any actor has entered
            // battle.
            var replacement =
                    new TanksMatch(
                            config.battle.get() * 20, config.capture.get() * 20, services.isTest());
            for (var a : match.actors())
                replacement.add(a.id(), a.id().equals(move) ? 1 - big : a.team(), a.chassis());
            match = replacement;
            counts[big]--;
            counts[1 - big]++;
        }
        for (var p : roster) {
            p.setTeam(match.actor(p.id()).team() == 0 ? TeamColor.RED : TeamColor.BLUE);
        }
        owner.started(this, names.keySet());
        world =
                services.worlds()
                        .createVoid("tanks_" + UUID.randomUUID().toString().replace("-", ""));
        configureArenaView(world);
        services.arena().applyStandardGameRules(world);
        world.setTime(9000);
        world.setStorm(false);
        world.setThundering(false);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, false);
        world.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
        // Display entities are nonpersistent; keep every arena chunk resident for the battle.
        retainArenaChunks();
        arena = new TanksArena(world);
        services.sidebar().setNameTagsVisible(false);
        timer =
                services.bossBars()
                        .create(tr("loading", 0), BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
        services.bossBars().showAll(timer);
        for (int side = 0; side < 2; side++) {
            captures[side] =
                    services.bossBars()
                            .create(
                                    tr("capture", team(side), 0),
                                    side == 0 ? BossBar.Color.RED : BossBar.Color.BLUE,
                                    BossBar.Overlay.NOTCHED_10);
        }
        Bukkit.getPluginManager().registerEvents(this, services.plugin());
        for (Player p : services.participants().onlinePlayers()) {
            connections.put(p.getUniqueId(), p);
            inputs.put(p.getUniqueId(), p.getCurrentInput());
            services.playerState().resetForRound(p, GameMode.SPECTATOR);
        }
        say(tr("rules"));
        task =
                Bukkit.getScheduler()
                        .runTaskTimer(
                                services.plugin(),
                                () -> {
                                    try {
                                        step();
                                    } catch (RuntimeException failure) {
                                        services.plugin()
                                                .getLogger()
                                                .log(
                                                        java.util.logging.Level.SEVERE,
                                                        "Tank battle failed",
                                                        failure);
                                        services.session().endGame(EndReason.ABORTED);
                                    }
                                },
                                1,
                                1);
    }

    private static void configureArenaView(World arenaWorld) {
        // Party defaults cap entity tracking below scout range even when target chunks are sent.
        arenaWorld.setViewDistance(8);
        arenaWorld.setSendViewDistance(8);
    }

    private void retainArenaChunks() {
        for (int x = Math.floorDiv(-TanksArena.HALF_X, 16);
                x <= Math.floorDiv(TanksArena.HALF_X, 16);
                x++)
            for (int z = Math.floorDiv(-TanksArena.HALF_Z, 16);
                    z <= Math.floorDiv(TanksArena.HALF_Z, 16);
                    z++) world.addPluginChunkTicket(x, z, services.plugin());
    }

    private void step() {
        if (closed) return;
        tick++;
        if (!built) {
            if (!arena.buildSlice(3500)) {
                timer.name(tr("loading", (int) (arena.progress() * 100)));
                timer.progress(arena.progress());
                return;
            }
            built = true;
            liveAt = tick + config.preparation.get() * 20;
            radar = new TanksRadar(world, this::radarBlips);
            int[] index = {0, 0};
            for (var a : match.actors()) {
                Vehicle v = new Vehicle(arena.spawn(a.team(), index[a.team()]++));
                v.chassis = a.chassis();
                vehicles.put(a.id(), v);
                v.visual = new TankVisual(v.at, a.team(), a.chassis());
                Player p = player(a.id());
                if (p != null) mount(p, a, v, true);
            }
            say(tr("preparation", config.preparation.get()));
            visibility();
        }
        if (finishAt >= 0) {
            if (tick >= finishAt) services.session().endGame(EndReason.FINISHED);
            return;
        }
        if (!match.started() && tick >= liveAt) {
            match.start();
            say(tr("fight"));
            world.playSound(
                    arena.base(0), Sound.EVENT_RAID_HORN, SoundCategory.PLAYERS, .55f, 1.2f);
            world.playSound(
                    arena.base(1), Sound.EVENT_RAID_HORN, SoundCategory.PLAYERS, .55f, 1.2f);
        }
        for (var a : match.actors()) if (a.alive()) drive(a, vehicles.get(a.id()));
        if (match.started()) {
            flyShells();
            for (var a : match.actors()) {
                Vehicle v = vehicles.get(a.id());
                int base = -1;
                if (a.alive())
                    for (int side = 0; side < 2; side++)
                        if (v.at.distanceSquared(arena.base(side)) < 49) base = side;
                a.base(base);
            }
            Map<UUID, Integer> before = new HashMap<>();
            for (var a : match.actors()) before.put(a.id(), a.reloadTicks());
            match.tick();
            for (var a : match.actors())
                if (a.alive() && before.get(a.id()) > 0 && a.reloadTicks() == 0) {
                    Player p = player(a.id());
                    if (p != null)
                        p.playSound(
                                p.getLocation(),
                                Sound.BLOCK_IRON_TRAPDOOR_CLOSE,
                                SoundCategory.PLAYERS,
                                .65f,
                                1.5f);
                }
            if (match.finished()) finish();
        }
        smoke.removeIf(s -> tick >= s.until);
        if (tick % 5 == 0) {
            visibility();
            hud();
            effects();
        }
    }

    private void mount(Player p, TanksMatch.Actor a, Vehicle v, boolean reset) {
        internalTeleport = true;
        try {
            p.leaveVehicle();
            // Creative suppresses infantry hearts/hunger; all actions remain server-authoritative.
            if (reset) services.playerState().resetForRound(p, GameMode.CREATIVE);
            else p.setGameMode(GameMode.CREATIVE);
            p.setFallDistance(0);
            p.addPotionEffect(
                    new PotionEffect(PotionEffectType.INVISIBILITY, 1_000_000, 0, false, false));
            attachDriver(p, v);
            prepareKit(p, a, reset);
            p.showBossBar(timer);
            if (reset) p.sendMessage(tr("controls"));
        } finally {
            internalTeleport = false;
        }
    }

    private boolean attachDriver(Player p, Vehicle v) {
        boolean previous = internalTeleport;
        internalTeleport = true;
        try {
            p.leaveVehicle();
            p.teleport(v.at.clone().add(0, 3, 0));
            Entity seat = v.visual.seat();
            boolean attached = seat.addPassenger(p) && p.getVehicle() == seat;
            if (!attached) v.speed = v.trackSpeed = 0;
            return attached;
        } finally {
            internalTeleport = previous;
        }
    }

    private void prepareKit(Player p, TanksMatch.Actor a, boolean reset) {
        // Replacing an otherwise identical offhand map during remount can lose its client hand
        // render state.
        if (reset) kit(p, a);
        else p.getInventory().setHeldItemSlot(a.shell() == TanksRules.Shell.HE ? 1 : 0);
    }

    private void kit(Player p, TanksMatch.Actor a) {
        p.getInventory().clear();
        item(p, 0, Material.IRON_NUGGET, "shell.ap", "lore.ap");
        item(p, 1, Material.FIRE_CHARGE, "shell.he", "lore.he");
        item(p, 2, Material.ANVIL, "item.repair", "lore.repair");
        item(p, 3, Material.CAMPFIRE, "item.smoke", "lore.smoke");
        item(p, 4, Material.AMETHYST_SHARD, "item.scope", "lore.scope");
        item(p, 6, Material.FEATHER, "class.light", "lore.light");
        item(p, 7, Material.IRON_INGOT, "class.medium", "lore.medium");
        item(p, 8, Material.NETHERITE_INGOT, "class.heavy", "lore.heavy");
        p.getInventory().setHeldItemSlot(a.shell() == TanksRules.Shell.HE ? 1 : 0);
        if (radar != null) p.getInventory().setItemInOffHand(radar.item(p));
    }

    private void item(Player p, int slot, Material material, String name, String lore) {
        ItemStack stack = new ItemStack(material);
        var meta = stack.getItemMeta();
        meta.displayName(tr(name));
        meta.lore(List.of(tr(lore)));
        stack.setItemMeta(meta);
        p.getInventory().setItem(slot, I18n.item(stack, p));
    }

    private void drive(TanksMatch.Actor a, Vehicle v) {
        if (v == null) return;
        Player p = player(a.id());
        Input input = p == null ? null : inputs.get(a.id());
        boolean control =
                match.started()
                        && a.online()
                        && p != null
                        && p.getWorld().equals(world)
                        && !p.isDead();
        if (control && p.getVehicle() != v.visual.seat()) control = attachDriver(p, v);
        double throttle =
                control && input != null
                        ? (input.isForward() ? 1 : 0) - (input.isBackward() ? 1 : 0)
                        : 0;
        double turn =
                control && input != null
                        ? (input.isLeft() ? -1 : 0) + (input.isRight() ? 1 : 0)
                        : 0;
        if (a.trackTicks() > 0) throttle = turn = 0;
        double speed = TanksRules.accelerate(v.speed, throttle, a.chassis());
        if (!control || a.trackTicks() > 0) speed = 0;
        float previousYaw = v.at.getYaw();
        float nextYaw = (float) TanksRules.steerYaw(v.at.getYaw(), turn, a.chassis());
        Location rotated = v.at.clone();
        rotated.setYaw(nextYaw);
        if (arena.canDrive(rotated, a.chassis()) && clearOfTanks(a.id(), rotated))
            v.at.setYaw(nextYaw);
        double radians = Math.toRadians(v.at.getYaw());
        Location next = v.at.clone().add(-Math.sin(radians) * speed, 0, Math.cos(radians) * speed);
        if (arena.canDrive(next, a.chassis()) && clearOfTanks(a.id(), next)) {
            v.at = next;
            v.speed = speed;
        } else v.speed = 0;
        v.trackSpeed = TanksAudio.trackSpeed(v.speed, previousYaw, v.at.getYaw());
        var desired =
                p != null && a.online()
                        ? aimAtCrosshair(p, a, v)
                        : new TanksBallistics.Aim(v.turretYaw, v.turretPitch);
        float target = (float) desired.yaw();
        double error = TanksRules.angleDelta(v.turretYaw, target);
        v.turretYaw =
                (float) TanksRules.stepYaw(v.turretYaw, target, a.chassis().turretDegreesPerTick());
        float pitch = (float) Math.clamp(desired.pitch(), -18, 14);
        v.turretPitch += (float) Math.clamp(pitch - v.turretPitch, -1.5, 1.5);
        v.settled = Math.abs(v.speed) < .01 && Math.abs(error) < 1 && turn == 0 ? v.settled + 1 : 0;
        v.visual.update(v.at, v.turretYaw, v.turretPitch, a.hp(), a.chassis().maxHp());
    }

    private boolean clearOfTanks(UUID self, Location at) {
        for (var entry : vehicles.entrySet())
            if (!entry.getKey().equals(self)) {
                Vehicle other = entry.getValue();
                if (TanksBallistics.overlap(
                        at.getX(),
                        at.getZ(),
                        at.getYaw(),
                        match.actor(self).chassis(),
                        other.at.getX(),
                        other.at.getZ(),
                        other.at.getYaw(),
                        other.chassis)) return false;
            }
        return true;
    }

    private void fire(Player p) {
        if (!active(p) || !match.started() || match.finished()) return;
        var a = actor(p);
        Vehicle v = vehicles.get(a.id());
        Location muzzle = v.visual.muzzle();
        BarrelPreflight preflight = preflight(a, muzzle, false);
        if (preflight.ownHull) {
            feedback(a, tr("gun-blocked"), Sound.BLOCK_DISPENSER_FAIL);
            return;
        }
        if (!a.fire()) {
            feedback(a, tr("reloading", seconds(a.reloadTicks())), Sound.BLOCK_DISPENSER_FAIL);
            return;
        }
        double spread = shotSpread(a, v, p);
        var aim =
                TanksBallistics.spreadDirection(
                        v.turretYaw,
                        v.turretPitch,
                        spread,
                        random.nextGaussian(),
                        random.nextGaussian());
        v.revealedUntil = tick + 100;
        for (TanksAudio.Tone tone : TanksAudio.cannon(a.chassis(), a.shell()))
            playTone(muzzle, tone);
        world.spawnParticle(Particle.FLASH, muzzle, 1, 0, 0, 0, 0, Color.ORANGE);
        world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, muzzle, 8, .15, .12, .15, .025);
        if (preflight.blocked) {
            if (preflight.target != null)
                hitTank(
                        launch(a, preflight.breech, preflight.barrel),
                        preflight.target,
                        preflight.at);
            else impact(preflight.at, false);
            return;
        }
        shells.add(
                launch(
                        a,
                        muzzle,
                        new Vector(aim.x(), aim.y(), aim.z())
                                .multiply(TanksBallistics.SHELL_SPEED)));
    }

    private ShellFlight launch(TanksMatch.Actor shooter, Location at, Vector velocity) {
        return new ShellFlight(shooter, at, velocity,
                match.spottersFor(shooter, tick, target -> canSpot(shooter, target)));
    }

    private record BarrelPreflight(
            Location breech,
            Location at,
            UUID target,
            Vector barrel,
            boolean blocked,
            boolean ownHull) {}

    /** Shared barrel-clearance guard; prediction contacts obey the viewer's spotting policy. */
    private BarrelPreflight preflight(TanksMatch.Actor a, Location muzzle, boolean predictionOnly) {
        Vehicle v = vehicles.get(a.id());
        // Derive the breech from the renderer's barrel line, so class-specific pivots agree.
        Location breech = muzzle.clone();
        breech.setX(v.at.getX());
        breech.setZ(v.at.getZ());
        double reach = Math.hypot(muzzle.getX() - v.at.getX(), muzzle.getZ() - v.at.getZ());
        breech.setY(muzzle.getY() + Math.tan(Math.toRadians(v.turretPitch)) * reach);
        Vector barrel = muzzle.toVector().subtract(breech.toVector());
        double ownHull =
                TanksBallistics.hullIntersection(
                        breech.getX(),
                        breech.getY(),
                        breech.getZ(),
                        muzzle.getX(),
                        muzzle.getY(),
                        muzzle.getZ(),
                        v.at.getX(),
                        v.at.getY(),
                        v.at.getZ(),
                        v.at.getYaw(),
                        a.chassis());
        if (Double.isFinite(ownHull))
            return new BarrelPreflight(
                    breech,
                    breech.clone().add(barrel.clone().multiply(ownHull)),
                    null,
                    barrel,
                    true,
                    true);
        var obstruction =
                world.rayTraceBlocks(
                        breech,
                        barrel.clone().normalize(),
                        barrel.length(),
                        FluidCollisionMode.NEVER,
                        true);
        double nearest =
                obstruction == null
                        ? Double.POSITIVE_INFINITY
                        : obstruction.getHitPosition().distance(breech.toVector())
                                / barrel.length();
        UUID victim = null;
        for (var entry : vehicles.entrySet())
            if (!entry.getKey().equals(a.id())) {
                if (predictionOnly) {
                    var target = match.actor(entry.getKey());
                    if (!TanksBallistics.visibleToTeam(
                            a.team(),
                            false,
                            target.team(),
                            target.alive(),
                            target.spottedUntil(),
                            tick)) continue;
                }
                double hit = intersect(breech, muzzle, entry.getValue());
                if (hit < nearest - 1e-9) {
                    nearest = hit;
                    victim = entry.getKey();
                }
            }
        if (Double.isFinite(nearest)) {
            Location at = breech.clone().add(barrel.clone().multiply(nearest));
            return new BarrelPreflight(breech, at, victim, barrel, true, false);
        }
        return new BarrelPreflight(breech, muzzle, null, barrel, false, false);
    }

    private void flyShells() {
        var iterator = shells.iterator();
        while (iterator.hasNext()) {
            ShellFlight s = iterator.next();
            Location next = s.at.clone().add(s.velocity);
            double length = s.velocity.length(), nearest = Double.POSITIVE_INFINITY;
            UUID victim = null;
            var block =
                    world.rayTraceBlocks(
                            s.at,
                            s.velocity.clone().normalize(),
                            length,
                            FluidCollisionMode.NEVER,
                            true);
            if (block != null) nearest = block.getHitPosition().distance(s.at.toVector()) / length;
            for (var entry : vehicles.entrySet())
                if (!entry.getKey().equals(s.owner)) {
                    double t = intersect(s.at, next, entry.getValue());
                    if (t < nearest) {
                        nearest = t;
                        victim = entry.getKey();
                    }
                }
            Location end =
                    Double.isFinite(nearest)
                            ? s.at.clone().add(s.velocity.clone().multiply(nearest))
                            : next;
            Vector trace = end.toVector().subtract(s.at.toVector());
            for (double t = 0; t <= 1; t += .34)
                world.spawnParticle(
                        Particle.DUST,
                        s.at.clone().add(trace.clone().multiply(t)),
                        1,
                        0,
                        0,
                        0,
                        0,
                        new Particle.DustOptions(
                                s.shell == TanksRules.Shell.AP ? Color.YELLOW : Color.ORANGE,
                                .65f));
            if (Double.isFinite(nearest)) {
                if (victim != null) hitTank(s, victim, end);
                else impact(end, false);
                iterator.remove();
                continue;
            }
            s.at = next;
            s.velocity.setY(s.velocity.getY() - TanksBallistics.GRAVITY);
            if (++s.age > 65 || s.at.getY() < 63) iterator.remove();
        }
    }

    private double intersect(Location from, Location to, Vehicle v) {
        return TanksBallistics.intersection(
                from.getX(),
                from.getY(),
                from.getZ(),
                to.getX(),
                to.getY(),
                to.getZ(),
                v.at.getX(),
                v.at.getY(),
                v.at.getZ(),
                v.at.getYaw(),
                v.chassis,
                v.turretYaw);
    }

    private void hitTank(ShellFlight s, UUID target, Location at) {
        var victim = match.actor(target);
        var shooter = match.actor(s.owner);
        Vehicle v = vehicles.get(target);
        if (victim == null || shooter == null) return;
        impact(at, true);
        if (!victim.alive()) return;
        if (victim.team() == shooter.team()) {
            feedback(shooter, tr("friendly"), Sound.BLOCK_DISPENSER_FAIL);
            return;
        }
        double x = at.getX() - v.at.getX(), y = at.getY() - v.at.getY(), z = at.getZ() - v.at.getZ();
        var hit =
                TanksBallistics.armorHit(
                        s.chassis,
                        s.shell,
                        victim.chassis(),
                        v.at.getYaw(),
                        v.turretYaw,
                        x,
                        y,
                        z,
                        s.velocity.getX(),
                        s.velocity.getZ());
        int track = TanksBallistics.treadHit(victim.chassis(), v.at.getYaw(), x, y, z) ? 70 : 0;
        boolean trackWasWorking = victim.trackTicks() == 0;
        int captureBefore = victim.captureTicks();
        double damage = match.recordHit(s.owner, target, hit, track);
        s.spotters.credit(target, damage);
        boolean trackBroken = trackWasWorking && victim.trackTicks() > 0;
        String key = TanksAudio.hitKey(hit, damage);
        int hpLost = (int) Math.ceil(damage);
        Component shooterText = damage > 0 ? tr(key, hpLost) : tr(key);
        if (trackBroken)
            shooterText = shooterText.append(Component.text(" • ")).append(tr("track-disabled"));
        feedback(shooter, shooterText, TanksAudio.hitTones(hit, damage, trackBroken));
        if (damage > 0 || trackBroken) {
            Component victimText = damage > 0 ? tr("damaged", hpLost) : tr("tracked");
            if (damage > 0 && trackBroken)
                victimText = victimText.append(Component.text(" • ")).append(tr("tracked"));
            if (captureBefore > 0 && victim.captureTicks() == 0)
                victimText = victimText.append(Component.text(" • ")).append(tr("capture-reset"));
            feedback(
                    victim,
                    victimText,
                    trackBroken ? Sound.BLOCK_ANVIL_PLACE : Sound.ENTITY_IRON_GOLEM_HURT);
        }
        if (!victim.alive()) destroy(victim, shooter);
    }

    private void impact(Location at, boolean metal) {
        world.spawnParticle(Particle.SMOKE, at, 8, .15, .15, .15, .03);
        world.spawnParticle(Particle.CRIT, at, metal ? 12 : 4, .2, .2, .2, .15);
        playTone(at, TanksAudio.impact(metal));
    }

    private void destroy(TanksMatch.Actor a, TanksMatch.Actor killer) {
        Vehicle v = vehicles.get(a.id());
        if (v == null) return;
        internalTeleport = true;
        try {
            Player p = player(a.id());
            if (p != null) {
                scopes.stop(p);
                p.leaveVehicle();
                p.removePotionEffect(PotionEffectType.INVISIBILITY);
                p.setGameMode(GameMode.SPECTATOR);
                p.teleport(v.at.clone().add(0, 7, 0));
                p.sendMessage(tr("destroyed"));
                p.showTitle(
                        Title.title(
                                tr("destroyed"),
                                tr("spectating"),
                                Title.Times.times(
                                        Duration.ofMillis(150),
                                        Duration.ofSeconds(3),
                                        Duration.ofMillis(500))));
            }
            v.speed = 0;
            v.visual.wreck();
        } finally {
            internalTeleport = false;
        }
        world.spawnParticle(Particle.EXPLOSION_EMITTER, v.at.clone().add(0, 1.5, 0), 1);
        world.playSound(v.at, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.3f, .65f);
        say(
                killer == null
                        ? tr("abandoned", names.get(a.id()))
                        : tr("kill", names.get(killer.id()), names.get(a.id())));
    }

    private void feedback(TanksMatch.Actor a, Component text, Sound cue) {
        Vehicle v = vehicles.get(a.id());
        if (v == null) return;
        v.feedback = text;
        v.feedbackUntil = tick + 40;
        Player p = player(a.id());
        if (p != null) {
            if (cue == Sound.BLOCK_DISPENSER_FAIL) {
                if (audio.allow(a.id(), TanksAudio.Cue.REJECTED, tick, 10))
                    playTone(p, p.getLocation(), TanksAudio.rejected());
            } else p.playSound(p.getLocation(), cue, SoundCategory.PLAYERS, .5f, 1.2f);
        }
    }

    private void feedback(TanksMatch.Actor a, Component text, List<TanksAudio.Tone> tones) {
        Vehicle v = vehicles.get(a.id());
        if (v == null) return;
        v.feedback = text;
        v.feedbackUntil = tick + 40;
        Player p = player(a.id());
        if (p != null) for (TanksAudio.Tone tone : tones) playTone(p, p.getLocation(), tone);
    }

    private void playTone(Location at, TanksAudio.Tone tone) {
        world.playSound(at, tone.sound(), SoundCategory.PLAYERS, tone.volume(), tone.pitch());
    }

    private void playTone(Player viewer, Location at, TanksAudio.Tone tone) {
        viewer.playSound(at, tone.sound(), SoundCategory.PLAYERS, tone.volume(), tone.pitch());
    }

    private String seconds(int ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0);
    }

    private boolean obscured(Location from, Location to) {
        for (Smoke s : smoke)
            if (TanksBallistics.smokeCrosses(
                    from.getX(),
                    from.getY(),
                    from.getZ(),
                    to.getX(),
                    to.getY(),
                    to.getZ(),
                    s.at.getX(),
                    s.at.getY() + 2,
                    s.at.getZ(),
                    5)) return true;
        return false;
    }

    private boolean canSpot(TanksMatch.Actor observer, TanksMatch.Actor target) {
        Vehicle a = vehicles.get(observer.id()), b = vehicles.get(target.id());
        double distance = a.at.distanceSquared(b.at);
        if (distance < 100) return true;
        double range = observer.chassis() == TanksRules.Chassis.LIGHT ? 86 : 70;
        if (target.chassis() == TanksRules.Chassis.LIGHT
                && Math.abs(b.speed) < .02
                && tick > b.revealedUntil) range *= .65;
        if (distance > range * range) return false;
        Location eye = a.at.clone().add(0, 2.4, 0), center = b.at.clone().add(0, 1.5, 0);
        if (obscured(eye, center)) return false;
        Vector delta = center.toVector().subtract(eye.toVector());
        return world.rayTraceBlocks(
                        eye,
                        delta.clone().normalize(),
                        delta.length(),
                        FluidCollisionMode.NEVER,
                        true)
                == null;
    }

    private void visibility() {
        if (!built) return;
        for (var target : match.actors())
            if (target.alive())
                for (var observer : match.actors())
                    if (observer.alive()
                            && observer.online()
                            && observer.team() != target.team()
                            && canSpot(observer, target))
                        target.spottedBy(observer, tick);
        for (Player viewer : services.session().audiencePlayers()) {
            var a = actor(viewer);
            for (var target : match.actors()) {
                boolean visible =
                        TanksBallistics.visibleToTeam(
                                a == null ? -1 : a.team(),
                                a == null,
                                target.team(),
                                target.alive(),
                                target.spottedUntil(),
                                tick);
                for (Entity entity : vehicles.get(target.id()).visual.entities()) {
                    boolean ownHealth =
                            viewer.getUniqueId().equals(target.id())
                                    && entity == vehicles.get(target.id()).visual.healthLabel();
                    if (visible && !ownHealth) viewer.showEntity(services.plugin(), entity);
                    else viewer.hideEntity(services.plugin(), entity);
                }
                Player driver = player(target.id());
                if (driver != null && !driver.equals(viewer)) {
                    // Invisible avatars still render held items; the tank model is their identity.
                    viewer.hidePlayer(services.plugin(), driver);
                }
            }
        }
    }

    private void hud() {
        boolean preparing = !match.started();
        int remaining = preparing ? (int) Math.max(0, liveAt - tick) : match.ticksRemaining();
        timer.name(
                preparing
                        ? tr("preparing", (remaining + 19) / 20)
                        : tr(
                                "battle",
                                remaining / 1200,
                                String.format(Locale.ROOT, "%02d", remaining / 20 % 60)));
        timer.color(preparing ? BossBar.Color.YELLOW : BossBar.Color.GREEN);
        timer.progress(
                (float)
                        Math.clamp(
                                (double) remaining
                                        / (preparing
                                                ? config.preparation.get() * 20
                                                : config.battle.get() * 20),
                                0,
                                1));
        for (int side = 0; side < 2; side++) {
            float progress = (float) match.captureProgress(side);
            var state = match.captureState(side);
            boolean contested = state == TanksMatch.CaptureState.CONTESTED;
            captures[side].progress(progress);
            captures[side].name(contested
                    ? tr("capture-contested", team(1 - side), (int) (progress * 100))
                    : tr("capture", team(side), (int) (progress * 100)));
            if (state != TanksMatch.CaptureState.IDLE) services.bossBars().showAll(captures[side]);
            else services.bossBars().hide(captures[side]);
            if (match.started() && !match.finished()) {
                for (TanksAudio.Tone tone :
                        audio.capture(side, progress, state != TanksMatch.CaptureState.IDLE, contested, tick))
                    for (Player viewer : services.session().audiencePlayers())
                        playTone(viewer, viewer.getLocation(), tone);
            }
        }
        long red = match.actors().stream().filter(a -> a.team() == 0 && a.alive()).count(),
                blue = match.actors().stream().filter(a -> a.team() == 1 && a.alive()).count();
        for (Player p : services.session().audiencePlayers()) {
            var a = actor(p);
            if (a == null) continue;
            Vehicle v = vehicles.get(a.id());
            boolean zoom = scopes.active(p);
            Component status =
                    preparing
                            ? tr("pick-class")
                            : !a.alive()
                                    ? tr("spectating")
                                    : v.feedbackUntil > tick
                                            ? v.feedback
                                            : a.trackTicks() > 0
                                                    ? tr("track-timer", seconds(a.trackTicks()))
                                                    : zoom ? Component.empty() : aimStatus(a, v, p);
            if (zoom) {
                Component sight = tr("hud.zoom", tr(a.shell() == TanksRules.Shell.AP
                        ? "shell.short-ap" : "shell.short-he"));
                status = status.equals(Component.empty()) ? sight
                        : sight.append(Component.text(" • ")).append(status);
            }
            var smokePhase = TanksSmokeVisual.phase(tick, v.smokeUntil);
            if (smokePhase == TanksSmokeVisual.Phase.ACTIVE)
                status =
                        status.append(Component.text(" • "))
                                .append(tr("smoke-active", seconds((int) (v.smokeUntil - tick))));
            else if (smokePhase == TanksSmokeVisual.Phase.FADING)
                status = status.append(Component.text(" • ")).append(tr("smoke-expired"));
            p.sendActionBar(
                    tr(
                            "hud",
                            (int) Math.ceil(a.hp()),
                            (int) a.chassis().maxHp(),
                            a.reloadTicks() > 0
                                    ? tr("reload", seconds(a.reloadTicks()))
                                    : tr("ready"),
                            status));
            services.sidebar().update(p, tr("name"), sidebarLines(a, preparing, zoom, red, blue));
            var objective =
                    p.getScoreboard().getObjective(org.bukkit.scoreboard.DisplaySlot.SIDEBAR);
            if (objective != null && objective.numberFormat() == null)
                objective.numberFormat(io.papermc.paper.scoreboard.numbers.NumberFormat.blank());
        }
    }

    static List<Component> sidebarLines(
            TanksMatch.Actor actor, boolean preparing, boolean zoom, long red, long blue) {
        if (zoom) return List.of();
        Component teams = tr("hud.teams", red, blue);
        if (preparing) return List.of(teams, classLine(actor), tr("hud.objective"), tr("hud.help"));
        return List.of(teams,
                tr("hud.shell", tr(actor.shell() == TanksRules.Shell.AP ? "shell.ap" : "shell.he")),
                tr("hud.supplies", actor.repairKits(), actor.smokeCharges()));
    }

    private static Component classLine(TanksMatch.Actor actor) {
        return tr("hud.class", tr("class." + actor.chassis().name().toLowerCase(Locale.ROOT)));
    }

    private Component aimStatus(TanksMatch.Actor a, Vehicle v, Player p) {
        return tr(
                "aim",
                String.format(
                        Locale.ROOT,
                        "%.1f",
                        TanksBallistics.confidence95Angle(shotSpread(a, v, p))));
    }

    private double shotSpread(TanksMatch.Actor a, Vehicle v, Player p) {
        return TanksRules.dispersion(
                a.chassis(),
                v.speed,
                TanksRules.angleDelta(v.turretYaw, p.getLocation().getYaw()),
                v.settled);
    }

    private TanksBallistics.Aim aimAtCrosshair(Player p, TanksMatch.Actor a, Vehicle v) {
        Location eye = p.getEyeLocation();
        Vector direction = eye.getDirection();
        Location point = eye.clone().add(direction.clone().multiply(130));
        var block = world.rayTraceBlocks(eye, direction, 130, FluidCollisionMode.NEVER, true);
        double nearest = block == null ? 1 : eye.toVector().distance(block.getHitPosition()) / 130;
        for (var entry : vehicles.entrySet())
            if (!entry.getKey().equals(a.id())) {
                var target = match.actor(entry.getKey());
                boolean visible =
                        TanksBallistics.visibleToTeam(
                                a.team(),
                                false,
                                target.team(),
                                target.alive(),
                                target.spottedUntil(),
                                tick);
                if (visible) nearest = Math.min(nearest, intersect(eye, point, entry.getValue()));
            }
        point = eye.clone().add(direction.clone().multiply(130 * nearest));
        double pivot = TanksRules.geometry(a.chassis()).turretPivotY();
        var solution =
                TanksBallistics.ballisticAim(
                        v.at.getX(),
                        v.at.getY() + pivot,
                        v.at.getZ(),
                        point.getX(),
                        point.getY(),
                        point.getZ());
        for (int i = 0; i < 4; i++) {
            Location muzzle =
                    TankVisual.muzzleAt(
                            v.at, (float) solution.yaw(), (float) solution.pitch(), a.chassis());
            solution =
                    TanksBallistics.ballisticAim(
                            muzzle.getX(),
                            muzzle.getY(),
                            muzzle.getZ(),
                            point.getX(),
                            point.getY(),
                            point.getZ());
        }
        return solution;
    }

    private void effects() {
        for (var a : match.actors()) {
            Vehicle v = vehicles.get(a.id());
            Player driver = player(a.id());
            if (a.alive() && a.online() && driver != null && match.started())
                gunMarker(a, v, driver);
            if (a.alive() && Math.abs(v.speed) > .03) {
                for (Player viewer : effectViewers(a))
                    viewer.spawnParticle(
                            Particle.BLOCK,
                            v.at.clone().add(0, .1, -.5),
                            4,
                            .9,
                            .04,
                            .7,
                            Material.COARSE_DIRT.createBlockData());
            }
            if (a.alive() && !match.finished())
                for (TanksAudio.Tone tone : audio.movement(a.id(), a.chassis(), v.trackSpeed, tick))
                    for (Player viewer : effectViewers(a)) playTone(viewer, v.at, tone);
            if (a.alive() && a.hp() < a.chassis().maxHp() * .35 || !a.alive()) {
                for (Player viewer : effectViewers(a))
                    viewer.spawnParticle(
                            Particle.CAMPFIRE_COSY_SMOKE,
                            v.at.clone().add(0, 2, 0),
                            2,
                            .3,
                            .1,
                            .3,
                            .01);
            }
        }
        for (Smoke s : smoke)
            if (TanksSmokeVisual.emits(tick, s.until))
                world.spawnParticle(
                        TanksSmokeVisual.particle(),
                        s.at.clone().add(0, 1.5, 0),
                        TanksSmokeVisual.PARTICLES_PER_BURST,
                        2.1,
                        1.2,
                        2.1,
                        .015);
    }

    private List<TanksRadar.Blip> radarBlips(Player viewer) {
        var a = actor(viewer);
        var blips = new ArrayList<TanksRadar.Blip>();
        if (a == null) return blips;
        for (var target : match.actors())
            if (target.alive()) {
                boolean ally = a.team() == target.team();
                boolean visible =
                        TanksBallistics.visibleToTeam(
                                a.team(),
                                false,
                                target.team(),
                                true,
                                target.spottedUntil(),
                                tick);
                if (visible) {
                    Vehicle v = vehicles.get(target.id());
                    blips.add(
                            new TanksRadar.Blip(
                                    v.at.getX(),
                                    v.at.getZ(),
                                    ally,
                                    target.id().equals(a.id()),
                                    v.at.getYaw()));
                }
            }
        return blips;
    }

    /** Private world-space gun circle distinguishes actual traverse from the camera crosshair. */
    private record Prediction(
            Location at, UUID target, Vector velocity, boolean blocked, boolean ownHull) {}

    private Prediction predict(TanksMatch.Actor a, Location from) {
        BarrelPreflight preflight = preflight(a, from, true);
        if (preflight.blocked)
            return new Prediction(
                    preflight.at,
                    preflight.target,
                    preflight.barrel.clone().normalize().multiply(TanksBallistics.SHELL_SPEED),
                    true,
                    preflight.ownHull);
        Location at = from.clone();
        Vector velocity = from.getDirection().multiply(TanksBallistics.SHELL_SPEED);
        for (int age = 0; age < 65; age++) {
            Location to = at.clone().add(velocity);
            double nearest = Double.POSITIVE_INFINITY;
            UUID targetId = null;
            var block =
                    world.rayTraceBlocks(
                            at,
                            velocity.clone().normalize(),
                            velocity.length(),
                            FluidCollisionMode.NEVER,
                            true);
            if (block != null)
                nearest = at.toVector().distance(block.getHitPosition()) / velocity.length();
            for (var entry : vehicles.entrySet())
                if (!entry.getKey().equals(a.id())) {
                    var target = match.actor(entry.getKey());
                    if (!TanksBallistics.visibleToTeam(
                            a.team(),
                            false,
                            target.team(),
                            target.alive(),
                            target.spottedUntil(),
                            tick)) continue;
                    double t = intersect(at, to, entry.getValue());
                    if (t < nearest) {
                        nearest = t;
                        targetId = entry.getKey();
                    }
                }
            if (Double.isFinite(nearest))
                return new Prediction(
                        at.add(velocity.clone().multiply(nearest)),
                        targetId,
                        velocity,
                        false,
                        false);
            at = to;
            velocity.setY(velocity.getY() - TanksBallistics.GRAVITY);
            if (at.getY() < 63) break;
        }
        return new Prediction(at, null, velocity, false, false);
    }

    private void gunMarker(TanksMatch.Actor a, Vehicle v, Player driver) {
        Location from = v.visual.muzzle();
        Prediction prediction = predict(a, from);
        Vector direction = prediction.velocity.clone().normalize();
        UUID targetId = prediction.target;
        Color color =
                prediction.ownHull ? Color.RED : prediction.blocked ? Color.ORANGE : Color.WHITE;
        if (prediction.ownHull && (v.feedbackUntil <= tick || v.feedback.equals(tr("gun-blocked")))) {
            v.feedback = tr("gun-blocked");
            v.feedbackUntil = tick + 6;
        }
        if (targetId != null && !prediction.blocked) {
            var target = match.actor(targetId);
            Vehicle hit = vehicles.get(targetId);
            Location point = prediction.at;
            var result =
                    TanksBallistics.armorHit(
                            a.chassis(),
                            a.shell(),
                            target.chassis(),
                            hit.at.getYaw(),
                            hit.turretYaw,
                            point.getX() - hit.at.getX(),
                            point.getY() - hit.at.getY(),
                            point.getZ() - hit.at.getZ(),
                            direction.getX(),
                            direction.getZ());
            color =
                    target.team() == a.team()
                            ? Color.AQUA
                            : result.penetrated() ? Color.LIME : Color.ORANGE;
        }
        var plane =
                TanksBallistics.markerPlane(
                        new TanksBallistics.Point(from.getX(), from.getY(), from.getZ()),
                        new TanksBallistics.Point(
                                prediction.at.getX(), prediction.at.getY(), prediction.at.getZ()),
                        new TanksBallistics.Direction(
                                direction.getX(), direction.getY(), direction.getZ()));
        Location center = new Location(world, plane.x(), plane.y(), plane.z());
        TanksBallistics.Point[] contour;
        if (prediction.blocked) {
            // Barrel obstructions happen before dispersion; show their exact guarded endpoint.
            contour = new TanksBallistics.Point[TanksBallistics.RETICLE_POINTS];
            Vector right = direction.clone().crossProduct(new Vector(0, 1, 0)).normalize(),
                    up = right.clone().crossProduct(direction).normalize();
            for (int i = 0; i < contour.length; i++) {
                double angle = i * Math.PI * 2 / contour.length;
                Location point =
                        center.clone()
                                .add(right.clone().multiply(Math.cos(angle) * .065))
                                .add(up.clone().multiply(Math.sin(angle) * .065));
                contour[i] = new TanksBallistics.Point(point.getX(), point.getY(), point.getZ());
            }
        } else
            contour =
                    TanksBallistics.dispersionContour(
                            new TanksBallistics.Point(from.getX(), from.getY(), from.getZ()),
                            v.turretYaw,
                            v.turretPitch,
                            shotSpread(a, v, driver),
                            new TanksBallistics.Point(center.getX(), center.getY(), center.getZ()),
                            new TanksBallistics.Direction(
                                    direction.getX(), direction.getY(), direction.getZ()));
        var dust = new Particle.DustOptions(color, .45f);
        for (var point : contour) {
            if (point == null) continue;
            driver.spawnParticle(
                    Particle.DUST,
                    new Location(world, point.x(), point.y(), point.z()),
                    1,
                    0,
                    0,
                    0,
                    0,
                    dust,
                    true);
        }
    }

    private List<Player> effectViewers(TanksMatch.Actor target) {
        return services.session().audiencePlayers().stream()
                .filter(p -> {
                    var a = actor(p);
                    return TanksBallistics.visibleToTeam(
                            a == null ? -1 : a.team(),
                            a == null,
                            target.team(),
                            target.alive(),
                                target.spottedUntil(),
                            tick);
                })
                .toList();
    }

    void command(Player p, String action) {
        if (action.equals("help")) {
            p.sendMessage(tr("controls"));
            p.sendMessage(tr("controls.advanced"));
            p.sendMessage(tr("rules"));
            var driver = actor(p);
            if (driver != null)
                p.sendMessage(classLine(driver).append(Component.text(" • ")).append(tr("hud.kills", driver.kills())));
            return;
        }
        choose(p, TanksRules.Chassis.valueOf(action.toUpperCase(Locale.ROOT)));
    }

    private void choose(Player p, TanksRules.Chassis chassis) {
        if (!active(p)) return;
        var a = actor(p);
        if (!a.selectChassis(chassis)) {
            p.sendMessage(tr("class-locked"));
            return;
        }
        Vehicle v = vehicles.get(a.id());
        scopes.stop(p);
        internalTeleport = true;
        v.chassis = chassis;
        try {
            p.leaveVehicle();
            v.visual.remove();
            v.visual = new TankVisual(v.at, a.team(), chassis);
            mount(p, a, v, false);
        } finally {
            internalTeleport = false;
        }
        p.sendMessage(tr("selected", tr("class." + chassis.name().toLowerCase(Locale.ROOT))));
    }

    private void use(Player p) {
        if (!active(p)) return;
        var a = actor(p);
        int slot = p.getInventory().getHeldItemSlot();
        if (slot == 0 || slot == 1) {
            fire(p);
            return;
        }
        if (slot == 4) {
            toggleScope(p);
            return;
        }
        if (slot >= 6 && slot <= 8) {
            choose(p, TanksRules.Chassis.values()[slot - 6]);
            return;
        }
        if (!match.started() || match.finished()) return;
        if (slot == 2) {
            if (a.repair()) feedback(a, tr("repaired"), Sound.BLOCK_ANVIL_USE);
            else feedback(a, tr("repair-unavailable"), Sound.BLOCK_DISPENSER_FAIL);
        }
        if (slot == 3) {
            if (a.smoke()) {
                Vehicle v = vehicles.get(a.id());
                v.smokeUntil = tick + a.smokeTicks();
                smoke.add(new Smoke(v.at.clone(), v.smokeUntil));
                feedback(a, tr("smoke-deployed"), Sound.BLOCK_FIRE_EXTINGUISH);
            } else feedback(a, tr("smoke-unavailable"), Sound.BLOCK_DISPENSER_FAIL);
        }
    }

    private void finish() {
        if (finishAt >= 0) return;
        finishAt = tick + 60;
        shells.clear();
        scopes.clear();
        audio.clear();
        int winner = match.winner();
        Component message = winner < 0 ? tr("draw") : tr("winner", team(winner));
        say(message);
        timer.name(message);
        timer.progress(1);
        var summary = new ArrayList<Component>();
        summary.add(message);
        for (var a : match.actors()) {
            int points = match.reward(a.id(), 3, 0, 0);
            if (points > 0) services.award("tanks", a.id(), points);
            summary.add(tr("standing", names.get(a.id()), a.kills(), (int) Math.ceil(a.hp()),
                    String.format(Locale.ROOT, "%.1f", a.spottingDamage())));
            Player p = player(a.id());
            if (p != null)
                p.playSound(
                        p.getLocation(),
                        a.team() == winner
                                ? Sound.UI_TOAST_CHALLENGE_COMPLETE
                                : Sound.BLOCK_NOTE_BLOCK_BASS,
                        SoundCategory.PLAYERS,
                        .6f,
                        1);
        }
        results = List.copyOf(summary);
    }

    @EventHandler
    public void input(PlayerInputEvent e) {
        if (connections.get(e.getPlayer().getUniqueId()) == e.getPlayer())
            inputs.put(e.getPlayer().getUniqueId(), e.getInput());
    }

    @EventHandler(ignoreCancelled = true)
    public void dismount(EntityDismountEvent e) {
        if (!internalTeleport
                && e.getEntity() instanceof Player p
                && active(p)
                && !match.finished()) e.setCancelled(true);
    }

    @Override
    public void onPlayerInteract(PlayerInteractEvent e) {
        if (actor(e.getPlayer()) == null) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        e.setCancelled(true);
        if (e.getAction().isRightClick()) use(e.getPlayer());
        else if (e.getAction().isLeftClick()) left(e.getPlayer());
    }

    private void toggleScope(Player p) {
        if (!active(p) || !match.started() || match.finished()) return;
        if (scopes.toggle(p, tick))
            feedback(
                    actor(p),
                    tr(scopes.active(p) ? "scope-on" : "scope-off"),
                    Sound.BLOCK_NOTE_BLOCK_HAT);
    }

    private void left(Player p) {
        int slot = p.getInventory().getHeldItemSlot();
        if (slot == 0 || slot == 1) toggleScope(p);
        else if (slot == 4) fire(p);
    }

    @EventHandler
    public void scopeSwing(PlayerArmSwingEvent e) {
        if (e.getHand() == EquipmentSlot.HAND && active(e.getPlayer())) left(e.getPlayer());
    }

    @Override
    public void onPlayerInteractEntity(PlayerInteractEntityEvent e) {
        if (actor(e.getPlayer()) != null) {
            e.setCancelled(true);
            if (e.getHand() == EquipmentSlot.HAND) use(e.getPlayer());
        }
    }

    @Override
    public void onPrePlayerAttackEntity(PrePlayerAttackEntityEvent e) {
        if (actor(e.getPlayer()) != null) {
            e.setCancelled(true);
            left(e.getPlayer());
        }
    }

    @Override
    public void onEntityDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && actor(p) != null
                || world != null && e.getEntity().getWorld().equals(world)) e.setCancelled(true);
    }

    @Override
    public void onEntityDamageByEntity(EntityDamageByEntityEvent e) {
        onEntityDamage(e);
    }

    @Override
    public void onBlockBreak(BlockBreakEvent e) {
        e.setCancelled(true);
    }

    @Override
    public void onBlockPlace(BlockPlaceEvent e) {
        e.setCancelled(true);
    }

    @Override
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().clear();
    }

    @Override
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().clear();
    }

    @Override
    public void onFoodLevelChange(FoodLevelChangeEvent e) {
        e.setCancelled(true);
    }

    @Override
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && actor(p) != null) e.setCancelled(true);
    }

    @Override
    public void onInventoryDrag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p && actor(p) != null) e.setCancelled(true);
    }

    @Override
    public void onDropItem(PlayerDropItemEvent e) {
        e.setCancelled(true);
    }

    @Override
    public void onSwapHandItems(PlayerSwapHandItemsEvent e) {
        e.setCancelled(true);
    }

    @Override
    public void onItemHeld(PlayerItemHeldEvent e) {
        var a = actor(e.getPlayer());
        if (e.getNewSlot() != 0 && e.getNewSlot() != 1 && e.getNewSlot() != 4)
            scopes.stop(e.getPlayer());
        if (a != null && e.getNewSlot() < 2)
            a.selectShell(e.getNewSlot() == 0 ? TanksRules.Shell.AP : TanksRules.Shell.HE);
    }

    @Override
    public void onPlayerTeleport(PlayerTeleportEvent e) {
        if (!internalTeleport && active(e.getPlayer()) && !match.finished()) e.setCancelled(true);
    }

    @Override
    public void onParticipantQuit(Player p) {
        scopes.stop(p);
        var a = actor(p);
        if (a == null) return;
        // Detach before Paper's logout pass removes a player's root vehicle.
        internalTeleport = true;
        try {
            p.leaveVehicle();
        } finally {
            internalTeleport = false;
        }
        Vehicle v = vehicles.get(a.id());
        if (v != null) v.speed = v.trackSpeed = 0;
        a.disconnect();
        connections.remove(a.id());
        inputs.remove(a.id());
    }

    @Override
    public void onParticipantLeave(Player p) {
        scopes.stop(p);
        var a = actor(p);
        if (a == null) return;
        boolean alive = a.alive();
        a.leave();
        if (alive && built) destroy(a, null);
        connections.remove(a.id());
        inputs.remove(a.id());
        restoreVisibility(p);
    }

    @Override
    public void onParticipantRejoin(Player p) {
        var a = actor(p);
        if (a == null || closed) return;
        a.reconnect();
        connections.put(a.id(), p);
        inputs.put(a.id(), p.getCurrentInput());
        if (!built) return;
        if (a.alive()) mount(p, a, vehicles.get(a.id()), false);
        else {
            internalTeleport = true;
            try {
                services.playerState().resetForRound(p, GameMode.SPECTATOR);
                p.teleport(arena.spectator());
            } finally {
                internalTeleport = false;
            }
        }
        visibility();
    }

    private void restoreVisibility(Player p) {
        if (services == null) return;
        for (var v : vehicles.values())
            for (Entity entity : v.visual.entities()) p.showEntity(services.plugin(), entity);
        for (Player other : Bukkit.getOnlinePlayers()) {
            p.showPlayer(services.plugin(), other);
            other.showPlayer(services.plugin(), p);
        }
    }

    @Override
    public void onEnd(EndReason reason) {
        if (closed) return;
        closed = true;
        if (task != null) task.cancel();
        scopes.clear();
        audio.clear();
        HandlerList.unregisterAll(this);
        internalTeleport = true;
        try {
            for (Player p : services.session().audiencePlayers()) {
                p.leaveVehicle();
                p.removePotionEffect(PotionEffectType.INVISIBILITY);
                restoreVisibility(p);
            }
        } finally {
            internalTeleport = false;
        }
        for (var v : vehicles.values()) v.visual.remove();
        if (radar != null) radar.clear();
        if (world != null) world.removePluginChunkTickets(services.plugin());
        shells.clear();
        smoke.clear();
        inputs.clear();
        connections.clear();
        if (timer != null) services.bossBars().hide(timer);
        for (BossBar bar : captures) if (bar != null) services.bossBars().hide(bar);
        owner.ended(this);
    }

    @Override
    public boolean isInGame(Player p) {
        var a = actor(p);
        return a != null && a.alive();
    }

    @Override
    public boolean allowLateJoin() {
        return false;
    }

    @Override
    public boolean returnToLobbyAfterGame() {
        return true;
    }

    @Override
    public boolean arePlayersFriendly(Player a, Player b) {
        var x = actor(a);
        var y = actor(b);
        return x != null && y != null && x.team() == y.team();
    }

    @Override
    public Location spectatorLocation(Player p) {
        return arena == null ? null : arena.spectator();
    }

    @Override
    public List<Component> summary() {
        return results;
    }
}
