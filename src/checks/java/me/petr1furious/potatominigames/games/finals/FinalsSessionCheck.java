package me.petr1furious.potatominigames.games.finals;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;

import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.core.config.ConfigRegistry;
import me.petr1furious.potatominigames.core.player.ParticipantRegistry;
import me.petr1furious.potatominigames.core.session.ActiveSession;
import me.petr1furious.potatominigames.core.session.GamePhase;
import me.petr1furious.potatominigames.core.text.Messages;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

import org.bukkit.ExplosionResult;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.block.Action;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Vector;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Real Paper handler checks; visual kits, inventory conversion and respawning need a live server.
 */
@SuppressWarnings({"deprecation", "removal"})
public final class FinalsSessionCheck {
    private static final UUID RED = new UUID(1, 1);
    private static final UUID BLUE = new UUID(1, 2);
    private static final UUID BLUE_MATE = new UUID(1, 3);
    private static final UUID UNKNOWN = new UUID(1, 4);
    private static final Map<UUID, Integer> ROSTER = Map.of(RED, 0, BLUE, 1, BLUE_MATE, 1);
    private static int checks;

    public static void main(String[] args) throws Exception {
        bulletClassification();
        friendlyAndAttributedDamage();
        protectionAndObjectiveGates();
        damageInterruptions();
        fallDamageIsDisabled();
        bareHandGlassBreaking();
        doorAndTrapdoorInteractions();
        nativeControlsAndPressurePlates();
        deathAndRejoin();
        creditedEnemyKills();
        teamWipeDeadlines();
        downedModeAndEligibility();
        gameMasterRole();
        legacyItemsAndNativeMutation();
        customDamageAndActionGates();
        menuAndEquipmentSlotGates();
        protectedPlacementAreas();
        vaultMovesOnlyAfterCashout();
        cashoutMovesOnlyAfterPayout();
        fixedCountdownAnchor();
        mapDeathHeight();
        objectiveRecovery();
        sharedTeamStartingPoints();
        mapPreparationGates();
        countdownLoadoutWindow();
        cashboxThrowDirection();
        cashboxThrowGuards();
        cashoutStealSiren();
        System.out.println("FinalsSessionCheck: " + checks + " checks passed");
    }

    private static void bulletClassification() throws Exception {
        var bullet = FinalsCombatContext.class.getDeclaredMethod(
                "damageBullet", Player.class, Player.class, double.class, Location.class);
        bullet.setAccessible(true);
        Fixture f = new Fixture();
        FinalsCombatContext combat = context(f.session);
        Location bulletOrigin = new Location(f.world.world, 3, 67, 4);
        Location blastOrigin = new Location(f.world.world, 8, 65, 9);
        Location reflectedOrigin = new Location(f.world.world, -2, 66, 1);

        f.blue.damageAction = (amount, source) -> {
            damageScope(f, false, f.red.location, "ordinary melee damage is combat damage, never bullet damage");
            check(amount == 3 && source == f.red.player, "generic damage retains its amount and attacker");
        };
        combat.damage(f.blue.player, f.red.player, 3);
        damageRestored(f, false, false, null, "generic damage restores the enclosing flags and origin");
        f.blue.damageAction = (amount, source) -> {
            damageScope(f, false, blastOrigin, "explosion-origin damage is not classified as a bullet");
            check(field(f.session, "damageOrigin").get(f.session) != blastOrigin,
                    "generic damage snapshots the caller's origin");
        };
        combat.damage(f.blue.player, f.red.player, 5, blastOrigin);
        damageRestored(f, false, false, null, "explosion damage leaves no bullet or origin state behind");

        DamageFailure nestedFailure = new DamageFailure();
        f.red.damageAction = (amount, source) -> {
            damageScope(f, false, reflectedOrigin, "nested reflected/gadget damage clears bullet classification");
            check(source == f.blue.player && amount == 2, "nested generic damage attributes the defender as attacker");
            throw nestedFailure;
        };
        f.blue.damageAction = (amount, source) -> {
            damageScope(f, true, bulletOrigin, "only the explicit bullet callback is marked as bullet damage");
            check(field(f.session, "damageOrigin").get(f.session) != bulletOrigin,
                    "bullet damage snapshots the caller's origin");
            Object outerOrigin = field(f.session, "damageOrigin").get(f.session);
            try {
                combat.damage(f.red.player, f.blue.player, 2, reflectedOrigin);
                throw new AssertionError("Nested damage failure must reach its caller");
            } catch (DamageFailure expected) {
                check(expected == nestedFailure, "nested damage preserves the original exception");
            }
            damageRestored(f, true, true, outerOrigin,
                    "nested generic failure restores the outer bullet scope and exact origin object");
        };
        invokeBullet(bullet, combat, f.blue.player, f.red.player, 7, bulletOrigin);
        damageRestored(f, false, false, null, "completed bullet damage restores the idle session scope");

        DamageFailure bulletFailure = new DamageFailure();
        f.blue.damageAction = (amount, source) -> {
            damageScope(f, true, bulletOrigin, "bullet classification remains active inside a failing damage callback");
            throw bulletFailure;
        };
        try {
            invokeBullet(bullet, combat, f.blue.player, f.red.player, 7, bulletOrigin);
            throw new AssertionError("Bullet damage failure must reach its caller");
        } catch (DamageFailure expected) {
            check(expected == bulletFailure, "bullet damage preserves the original exception");
        }
        damageRestored(f, false, false, null, "failed bullet damage restores all flags and origin");

        Location enclosingOrigin = new Location(f.world.world, 12, 68, -1);
        set(f.session, "applyingCombatDamage", true);
        set(f.session, "applyingBulletDamage", true);
        set(f.session, "damageOrigin", enclosingOrigin);
        f.blue.damageAction = (amount, source) -> {
            damageScope(f, false, blastOrigin, "nested explosions clear an existing bullet marker");
            throw nestedFailure;
        };
        try {
            combat.damage(f.blue.player, f.red.player, 5, blastOrigin);
            throw new AssertionError("Generic damage failure must reach its caller");
        } catch (DamageFailure expected) {
            check(expected == nestedFailure, "nested explosion preserves its failure");
        }
        damageRestored(f, true, true, enclosingOrigin,
                "generic damage restores non-default enclosing bullet state after failure");
    }

    private static void invokeBullet(java.lang.reflect.Method method, FinalsCombatContext context,
            Player target, Player source, double amount, Location origin) throws Exception {
        try { method.invoke(context, target, source, amount, origin); }
        catch (java.lang.reflect.InvocationTargetException failure) {
            if (failure.getCause() instanceof Exception exception) throw exception;
            throw (Error) failure.getCause();
        }
    }

    private static void damageScope(Fixture f, boolean bullet, Location origin, String label) throws Exception {
        check((boolean) field(f.session, "applyingCombatDamage").get(f.session)
                        && (boolean) field(f.session, "applyingBulletDamage").get(f.session) == bullet
                        && origin.equals(field(f.session, "damageOrigin").get(f.session)), label);
    }

    private static void damageRestored(Fixture f, boolean combat, boolean bullet, Object origin,
            String label) throws Exception {
        check((boolean) field(f.session, "applyingCombatDamage").get(f.session) == combat
                        && (boolean) field(f.session, "applyingBulletDamage").get(f.session) == bullet
                        && field(f.session, "damageOrigin").get(f.session) == origin, label);
    }

    @FunctionalInterface
    private interface DamageAction { void accept(double amount, Entity source) throws Exception; }

    private static final class DamageFailure extends RuntimeException {}

    private static void friendlyAndAttributedDamage() throws Exception {
        Fixture f = new Fixture();
        EntityDamageByEntityEvent friendly = hit(f.blueMate.player, f.blue.player, 3);
        f.session.onEntityDamageByEntity(friendly);
        check(friendly.isCancelled(), "teammates cannot deal melee damage");
        EntityDamageByEntityEvent enemy = hit(f.red.player, f.blue.player, 3);
        f.session.onEntityDamageByEntity(enemy);
        check(!enemy.isCancelled(), "enemy melee remains enabled");
        Shot friendlyArrow = new Shot(f.blueMate.player);
        EntityDamageByEntityEvent arrow = hit(friendlyArrow.projectile, f.blue.player, 3);
        f.session.onEntityDamageByEntity(arrow);
        check(arrow.isCancelled(), "arrow damage uses the shooter's team");
        Shot enemyArrow = new Shot(f.red.player);
        EntityDamageByEntityEvent enemyShot = hit(enemyArrow.projectile, f.blue.player, 3);
        f.session.onEntityDamageByEntity(enemyShot);
        check(!enemyShot.isCancelled(), "enemy arrow damage remains enabled");
        Bomb friendlyBomb = new Bomb(f.blueMate.player);
        EntityDamageByEntityEvent tnt = hit(friendlyBomb.entity, f.blue.player, 3);
        f.session.onEntityDamageByEntity(tnt);
        check(tnt.isCancelled(), "TNT damage uses the thrower's team");
        Bomb enemyBomb = new Bomb(f.red.player);
        EntityDamageByEntityEvent enemyBlast = hit(enemyBomb.entity, f.blue.player, 3);
        f.session.onEntityDamageByEntity(enemyBlast);
        check(!enemyBlast.isCancelled(), "enemy TNT damage remains enabled");
        Bomb ownerless = new Bomb(null);
        EntityDamageByEntityEvent ownerlessBlast = hit(ownerless.entity, f.blue.player, 3);
        f.session.onEntityDamageByEntity(ownerlessBlast);
        check(ownerlessBlast.isCancelled(), "unattributed explosions cannot damage contestants");
        f.connected().remove(RED);
        EntityDamageByEntityEvent departedShot = hit(enemyArrow.projectile, f.blue.player, 3);
        f.session.onEntityDamageByEntity(departedShot);
        check(
                departedShot.isCancelled(),
                "projectile from departed shooter cannot damage contestants");
    }

    private static void protectionAndObjectiveGates() throws Exception {
        Fixture f = new Fixture();
        f.protection().put(RED, 101L);
        EntityDamageByEntityEvent protectedSource = hit(f.red.player, f.blue.player, 3);
        f.session.onEntityDamageByEntity(protectedSource);
        check(protectedSource.isCancelled(), "protected source cannot attack");
        EntityDamageByEntityEvent protectedTarget = hit(f.blue.player, f.red.player, 3);
        f.session.onEntityDamageByEntity(protectedTarget);
        check(protectedTarget.isCancelled(), "protected target cannot be damaged");
        Shot shot = new Shot(f.red.player);
        ProjectileLaunchEvent launch = new ProjectileLaunchEvent(shot.projectile);
        f.session.onProjectileLaunch(launch);
        check(
                launch.isCancelled() && f.ordnance().isEmpty(),
                "protected player cannot launch a projectile");
        f.red.location = f.arena.vault().add(0, 0, 2);
        f.session.onPlayerInteract(
                interact(f.red.player, f.arena.vault().getBlock(), EquipmentSlot.HAND));
        check(
                f.state.phase() == FinalsState.Phase.VAULT_CLOSED,
                "spawn protection blocks vault activation");
        f.protection().clear();
        f.session.onPlayerInteract(
                interact(f.red.player, f.arena.vault().getBlock(), EquipmentSlot.OFF_HAND));
        check(
                f.state.phase() == FinalsState.Phase.VAULT_CLOSED,
                "offhand duplicate cannot activate vault");
        f.red.location = new Location(f.world.world, 20, 65, 20);
        f.session.onPlayerInteract(
                interact(f.red.player, f.arena.vault().getBlock(), EquipmentSlot.HAND));
        check(
                f.state.phase() == FinalsState.Phase.VAULT_CLOSED,
                "distant objective interaction is rejected");
        f.red.location = f.arena.vault().add(0, 0, 2);
        f.session.onPlayerInteract(
                interact(f.red.player, f.arena.vault().getBlock(), EquipmentSlot.HAND));
        check(
                f.state.phase() == FinalsState.Phase.VAULT_OPENING,
                "nearby unprotected player can activate vault");
        f.session.onProjectileLaunch(new ProjectileLaunchEvent(shot.projectile));
        check(
                f.ordnance().containsKey(shot.id),
                "eligible launched projectile is tracked for cleanup");
        set(f.session, "state", null);
        EntityDamageByEntityEvent countdownHit = hit(f.red.player, f.blue.player, 3);
        f.session.onEntityDamageByEntity(countdownHit);
        check(countdownHit.isCancelled(), "countdown rejects combat");
    }

    private static void damageInterruptions() throws Exception {
        Fixture f = new Fixture();
        f.depositRed();
        f.state.beginSteal(BLUE, 100);
        EntityDamageByEntityEvent friendly = hit(f.blueMate.player, f.blue.player, 3);
        f.session.onEntityDamage(friendly);
        f.session.onEntityDamageByEntity(friendly);
        check(
                friendly.isCancelled() && f.state.stealDeadlines().containsKey(BLUE),
                "generic damage hook cannot interrupt steal before friendly-fire validation");
        EntityDamageByEntityEvent enemy = hit(f.red.player, f.blue.player, 3);
        f.session.onEntityDamage(enemy);
        f.session.onEntityDamageByEntity(enemy);
        check(
                !enemy.isCancelled() && !f.state.stealDeadlines().containsKey(BLUE),
                "positive valid enemy damage interrupts steal");
        f.state.beginSteal(BLUE, 100);
        EntityDamageEvent cancelledEnvironment = environmental(f.blue.player, 3);
        cancelledEnvironment.setCancelled(true);
        f.session.onEntityDamage(cancelledEnvironment);
        check(
                f.state.stealDeadlines().containsKey(BLUE),
                "cancelled environmental damage preserves steal");
        f.session.onEntityDamage(environmental(f.blue.player, 0));
        check(
                f.state.stealDeadlines().containsKey(BLUE),
                "zero environmental damage preserves steal");
        f.session.onEntityDamage(environmental(f.blue.player, 3));
        check(
                !f.state.stealDeadlines().containsKey(BLUE),
                "positive environmental damage interrupts steal");
        f.state.beginSteal(BLUE, 100);
        f.protection().put(BLUE, 101L);
        EntityDamageEvent protectedEnvironment = environmental(f.blue.player, 3);
        f.session.onEntityDamage(protectedEnvironment);
        check(
                protectedEnvironment.isCancelled() && f.state.stealDeadlines().containsKey(BLUE),
                "spawn protection blocks environmental damage and preserves channel");
    }

    private static void fallDamageIsDisabled() throws Exception {
        Fixture f = new Fixture();
        f.depositRed();
        f.state.beginSteal(BLUE, 100);
        EntityDamageEvent fall = new EntityDamageEvent(f.blue.player,
                EntityDamageEvent.DamageCause.FALL, null, 18);
        f.session.onEntityDamage(fall);
        check(fall.isCancelled(), "fall damage is disabled for an active contestant without spawn protection");
        check(f.state.stealDeadlines().containsKey(BLUE), "falling does not interrupt an ongoing cashout steal");
        EntityDamageEvent fire = environmental(f.blue.player, 3);
        f.session.onEntityDamage(fire);
        check(!fire.isCancelled() && !f.state.stealDeadlines().containsKey(BLUE),
                "other environmental damage still applies and interrupts a steal");
    }

    private static void bareHandGlassBreaking() throws Exception {
        Fixture f = new Fixture();
        Block cover = f.world.block(6, 65, 3);
        f.red.location = cover.getLocation().add(-1, 0, 0);
        f.red.hand.type = Material.AIR;
        f.red.slot = 6;
        for (Material material : Material.values()) {
            if (material != Material.GLASS && material != Material.GLASS_PANE
                    && material != Material.TINTED_GLASS && !material.name().endsWith("_STAINED_GLASS")
                    && !material.name().endsWith("_STAINED_GLASS_PANE")) continue;
            cover.setType(material);
            PlayerInteractEvent start = interaction(f.red.player, cover, EquipmentSlot.HAND, Action.LEFT_CLICK_BLOCK);
            f.session.onPlayerInteract(start);
            check(start.useInteractedBlock() == Event.Result.ALLOW && start.useItemInHand() == Event.Result.ALLOW,
                    "bare-hand left click permits natural glass breaking: " + material);
            BlockBreakEvent broken = new BlockBreakEvent(cover, f.red.player);
            broken.setExpToDrop(4);
            f.session.onBlockBreak(broken);
            check(!broken.isCancelled() && !broken.isDropItems() && broken.getExpToDrop() == 0,
                    "bare-hand glass breaks without drops or XP: " + material);
        }
        cover.setType(Material.GLASS);
        PlayerInteractEvent offhand = interaction(f.red.player, cover, EquipmentSlot.OFF_HAND, Action.LEFT_CLICK_BLOCK);
        f.session.onPlayerInteract(offhand);
        check(offhand.isCancelled(), "offhand cannot initiate glass demolition");
        for (Material held : List.of(Material.IRON_PICKAXE, Material.WOODEN_HOE, Material.STICK)) {
            f.red.hand.type = held;
            f.red.slot = 7;
            BlockBreakEvent broken = new BlockBreakEvent(cover, f.red.player);
            f.session.onBlockBreak(broken);
            check(broken.isCancelled(), "holding an item cannot break glass, including the old pickaxe slot");
        }
        f.red.hand.type = Material.AIR;
        for (Material solid : List.of(Material.OAK_PLANKS, Material.STONE, Material.ICE, Material.SEA_LANTERN)) {
            cover.setType(solid);
            BlockBreakEvent broken = new BlockBreakEvent(cover, f.red.player);
            f.session.onBlockBreak(broken);
            check(broken.isCancelled(), "bare hands cannot break non-glass terrain: " + solid);
        }
        cover.setType(Material.GLASS);
        for (Block protectedBlock : List.of(f.world.block(0, 64, 0), f.arena.vault().getBlock(),
                f.world.block(24, 65, 0))) {
            protectedBlock.setType(Material.GLASS);
            BlockBreakEvent broken = new BlockBreakEvent(protectedBlock, f.red.player);
            f.session.onBlockBreak(broken);
            check(broken.isCancelled(), "glass must still belong to destructible arena cover");
        }
        for (String gate : List.of("protection", "countdown", "downed", "disconnected", "finished")) {
            Fixture blocked = new Fixture();
            Block glass = blocked.world.block(6, 65, 3);
            glass.setType(Material.GLASS);
            blocked.red.hand.type = Material.AIR;
            interactionGate(blocked, gate);
            BlockBreakEvent broken = new BlockBreakEvent(glass, blocked.red.player);
            blocked.session.onBlockBreak(broken);
            check(broken.isCancelled(), "glass demolition respects " + gate);
            PlayerInteractEvent start = interaction(blocked.red.player, glass, EquipmentSlot.HAND, Action.LEFT_CLICK_BLOCK);
            blocked.session.onPlayerInteract(start);
            check(start.useInteractedBlock() != Event.Result.ALLOW, "glass interaction respects " + gate);
        }
    }

    private static void doorAndTrapdoorInteractions() throws Exception {
        for (Material material : List.of(Material.OAK_DOOR, Material.IRON_DOOR, Material.COPPER_DOOR,
                Material.WAXED_OXIDIZED_COPPER_DOOR)) {
            Fixture f = new Fixture();
            f.red.location = new Location(f.world.world, 5, 65, 3);
            f.red.hand.type = Material.WOODEN_HOE;
            Block bottom = f.world.block(6, 65, 3), top = f.world.block(6, 66, 3);
            f.world.openable(bottom, material, false, Bisected.Half.BOTTOM);
            f.world.openable(top, material, false, Bisected.Half.TOP);
            PlayerInteractEvent opening = interact(f.red.player, top, EquipmentSlot.HAND);
            f.session.onPlayerInteract(opening);
            check(opening.isCancelled() && f.world.open(bottom) && f.world.open(top),
                    "right-clicking the upper half opens both halves with a weapon held: " + material);
            f.world.unchangedShape(bottom);
            f.world.unchangedShape(top);
            check(((Door) bottom.getBlockData()).getHalf() == Bisected.Half.BOTTOM
                            && ((Door) top.getBlockData()).getHalf() == Bisected.Half.TOP,
                    "opening preserves both door-half identities");
            f.session.onPlayerInteract(interact(f.red.player, bottom, EquipmentSlot.HAND));
            check(!f.world.open(bottom) && !f.world.open(top), "second click closes both door halves: " + material);
            f.session.onPlayerInteract(interact(f.red.player, bottom, EquipmentSlot.OFF_HAND));
            check(!f.world.open(bottom) && !f.world.open(top), "offhand duplicate cannot double-toggle a door");
        }
        for (Material material : List.of(Material.OAK_TRAPDOOR, Material.IRON_TRAPDOOR, Material.COPPER_TRAPDOOR)) {
            Fixture f = new Fixture();
            f.red.location = new Location(f.world.world, 5, 65, 3);
            Block trapdoor = f.world.block(6, 65, 3);
            f.world.openable(trapdoor, material, true, Bisected.Half.TOP);
            PlayerInteractEvent close = interact(f.red.player, trapdoor, EquipmentSlot.HAND);
            f.session.onPlayerInteract(close);
            check(close.isCancelled() && !f.world.open(trapdoor), "right-click toggles a trapdoor: " + material);
            f.world.unchangedShape(trapdoor);
            check(((TrapDoor) trapdoor.getBlockData()).getHalf() == Bisected.Half.TOP,
                    "opening preserves a top-mounted trapdoor");
        }
        for (String gate : List.of("protection", "countdown", "downed", "disconnected", "finished", "distance", "outside")) {
            Fixture f = new Fixture();
            Block door = f.world.block(gate.equals("outside") ? 24 : 6, 65, 3);
            f.world.openable(door, Material.IRON_TRAPDOOR, false, Bisected.Half.BOTTOM);
            f.red.location = door.getLocation().add(-1, 0, 0);
            if (gate.equals("distance")) f.red.location.add(-10, 0, 0);
            else if (!gate.equals("outside")) interactionGate(f, gate);
            f.session.onPlayerInteract(interact(f.red.player, door, EquipmentSlot.HAND));
            check(!f.world.open(door), "door toggling respects " + gate);
        }
        Fixture f = new Fixture();
        Block reachable = f.world.block(6, 65, 3);
        f.world.openable(reachable, Material.OAK_TRAPDOOR, false, Bisected.Half.BOTTOM);
        f.red.location = reachable.getLocation().add(.5 - 4.5, .5, .5);
        f.session.onPlayerInteract(interact(f.red.player, reachable, EquipmentSlot.HAND));
        check(f.world.open(reachable), "door interaction accepts the exact 4.5-block reach boundary");
        f.red.location.add(-.001, 0, 0);
        f.session.onPlayerInteract(interact(f.red.player, reachable, EquipmentSlot.HAND));
        check(f.world.open(reachable), "door interaction rejects distances beyond 4.5 blocks");
        Block brokenDoor = f.world.block(8, 65, 3);
        f.world.openable(brokenDoor, Material.IRON_DOOR, false, Bisected.Half.BOTTOM);
        f.red.location = brokenDoor.getLocation().add(-1, 0, 0);
        f.session.onPlayerInteract(interact(f.red.player, brokenDoor, EquipmentSlot.HAND));
        check(!f.world.open(brokenDoor), "a missing matching door half cannot be mutated");
        Block chest = f.world.block(6, 65, 3);
        chest.setType(Material.CHEST);
        PlayerInteractEvent other = interact(f.red.player, chest, EquipmentSlot.HAND);
        f.session.onPlayerInteract(other);
        check(other.useInteractedBlock() == Event.Result.DENY, "other native block interactions remain denied");
    }

    private static void nativeControlsAndPressurePlates() throws Exception {
        Fixture f = new Fixture();
        Block control = f.world.block(6, 65, 3);
        f.red.location = control.getLocation().add(-1, 0, 0);
        f.red.hand.type = Material.WOODEN_HOE;
        for (Material material : Material.values()) {
            if (!material.name().endsWith("_BUTTON") && !material.name().endsWith("_FENCE_GATE")) continue;
            control.setType(material);
            PlayerInteractEvent click = interact(f.red.player, control, EquipmentSlot.HAND);
            f.session.onPlayerInteract(click);
            check(click.useInteractedBlock() == Event.Result.ALLOW && click.useItemInHand() == Event.Result.DENY,
                    "main-hand right click permits native redstone/toggle behavior without firing equipment: " + material);
            PlayerInteractEvent offhand = interact(f.red.player, control, EquipmentSlot.OFF_HAND);
            f.session.onPlayerInteract(offhand);
            check(offhand.useInteractedBlock() == Event.Result.DENY && offhand.useItemInHand() == Event.Result.DENY,
                    "offhand duplicates cannot activate native controls: " + material);
        }
        for (Material material : Material.values()) {
            if (!material.name().endsWith("_PRESSURE_PLATE")) continue;
            control.setType(material);
            PlayerInteractEvent step = interaction(f.red.player, control, null, Action.PHYSICAL);
            f.session.onPlayerInteract(step);
            check(step.useInteractedBlock() == Event.Result.ALLOW && step.useItemInHand() == Event.Result.DENY,
                    "physical pressure plate activation accepts its native null hand and never uses equipment: " + material);
        }
        for (Material material : List.of(Material.STONE_BUTTON, Material.OAK_FENCE_GATE, Material.HEAVY_WEIGHTED_PRESSURE_PLATE)) {
            boolean plate = material.name().endsWith("_PRESSURE_PLATE");
            for (String gate : List.of("inactive", "preparing", "countdown", "protection", "downed", "disconnected", "finished", "outside")) {
                Fixture blocked = new Fixture();
                Block target = blocked.world.block(gate.equals("outside") ? 24 : 6, 65, 3);
                target.setType(material);
                blocked.red.location = target.getLocation().add(-1, 0, 0);
                if (!gate.equals("outside")) interactionGate(blocked, gate);
                PlayerInteractEvent denied = interaction(blocked.red.player, target, plate ? null : EquipmentSlot.HAND,
                        plate ? Action.PHYSICAL : Action.RIGHT_CLICK_BLOCK);
                blocked.session.onPlayerInteract(denied);
                check(denied.useInteractedBlock() == Event.Result.DENY,
                        material + " interaction respects " + gate);
            }
        }
        for (Material material : List.of(Material.FARMLAND, Material.TURTLE_EGG, Material.STONE,
                Material.STONE_BUTTON, Material.OAK_FENCE_GATE)) {
            control.setType(material);
            PlayerInteractEvent step = interaction(f.red.player, control, null, Action.PHYSICAL);
            f.session.onPlayerInteract(step);
            check(step.useInteractedBlock() == Event.Result.DENY,
                    "physical activation does not enable unrelated terrain mutations: " + material);
        }
        for (Material material : List.of(Material.CHEST, Material.LEVER, Material.CRAFTING_TABLE)) {
            control.setType(material);
            PlayerInteractEvent click = interact(f.red.player, control, EquipmentSlot.HAND);
            f.session.onPlayerInteract(click);
            check(click.useInteractedBlock() == Event.Result.DENY,
                    "unrelated right-click block interactions stay blocked: " + material);
        }
        control.setType(Material.OAK_PRESSURE_PLATE);
        FinalsWeapons weapons = new FinalsWeapons(context(f.session));
        Class<?> equippedType = Class.forName(FinalsWeapons.class.getName() + "$Equipped");
        var equippedConstructor = equippedType.getDeclaredConstructor(FinalsWeapon.class);
        equippedConstructor.setAccessible(true);
        Object equipped = equippedConstructor.newInstance(FinalsWeapon.DUAL_BLADES);
        set(equipped, "guardUntil", 400L);
        map(weapons, "equipped").put(RED, equipped);
        set(f.session, "weapons", weapons);
        PlayerInteractEvent step = interaction(f.red.player, control, null, Action.PHYSICAL);
        f.session.onPlayerInteract(step);
        check(step.useInteractedBlock() == Event.Result.ALLOW && (Long) field(equipped, "guardUntil").get(equipped) == 400L,
                "walking on a pressure plate does not cancel ongoing weapon use");

        FinalsRevives revives = bare(FinalsRevives.class);
        Class<?> channelType = Class.forName(FinalsRevives.class.getName() + "$Channel");
        var channelConstructor = channelType.getDeclaredConstructor(Player.class, UUID.class, boolean.class, int.class, long.class);
        channelConstructor.setAccessible(true);
        Object channel = channelConstructor.newInstance(f.red.player, BLUE_MATE, false, 0, 300L);
        Map<UUID, Object> channels = new HashMap<>();
        channels.put(RED, channel);
        set(revives, "channels", channels);
        set(f.session, "revives", revives);
        PlayerInteractEvent duringRevive = interaction(f.red.player, control, null, Action.PHYSICAL);
        f.session.onPlayerInteract(duringRevive);
        check(duringRevive.useInteractedBlock() == Event.Result.DENY && channels.get(RED) == channel,
                "an occupied reviver cannot activate a pressure plate, but walking never interrupts the revive channel");
        check((Long) field(equipped, "guardUntil").get(equipped) == 400L,
                "a rejected physical interaction also preserves weapon state");
    }

    private static void interactionGate(Fixture f, String gate) throws Exception {
        switch (gate) {
            case "inactive" -> set(f.session, "done", true);
            case "preparing" -> set(f.session, "preparing", true);
            case "protection" -> f.protection().put(RED, 101L);
            case "countdown" -> set(f.session, "state", null);
            case "downed" -> map(f.session, "respawnAt").put(RED, 200L);
            case "disconnected" -> f.connected().remove(RED);
            case "finished" -> f.state.tick(f.rules.matchTicks());
            default -> throw new AssertionError(gate);
        }
    }

    private static void deathAndRejoin() throws Exception {
        Fixture f = new Fixture();
        f.depositRed();
        f.state.beginSteal(BLUE, 100);
        Shot shot = new Shot(f.blue.player);
        Shot survivorShot = new Shot(f.red.player);
        f.ordnance().put(shot.id, shot.projectile);
        f.ordnance().put(survivorShot.id, survivorShot.projectile);
        PlayerDeathEvent death =
                new PlayerDeathEvent(
                        f.blue.player, null, new ArrayList<>(), 7, Component.text("death"), true);
        f.session.onPlayerDeath(death);
        check(f.respawns().get(BLUE) == 160L, "death schedules the configured respawn delay");
        check(
                !f.state.stealDeadlines().containsKey(BLUE),
                "session death interrupts current steal");
        check(
                death.getKeepInventory() && death.getKeepLevel() && death.getDroppedExp() == 0,
                "death retains kit and level without dropping experience");
        check(
                shot.removed == 1
                        && survivorShot.removed == 0
                        && !f.ordnance().containsKey(shot.id),
                "death removes only that player's tracked ordnance");
        set(f.session, "tick", 110L);
        f.session.onPlayerDeath(death);
        check(f.respawns().get(BLUE) == 160L, "duplicate death cannot extend respawn timer");

        Fixture rejoin = new Fixture();
        rejoin.connected().remove(BLUE);
        rejoin.state.disconnect(BLUE, 100);
        rejoin.blue.dead =
                true; // A dead client's normal respawn event will finish spectator recovery.
        rejoin.session.onParticipantRejoin(rejoin.blue.player);
        check(
                rejoin.connected().contains(BLUE)
                        && rejoin.state.isActive(BLUE)
                        && rejoin.state.teamOf(BLUE) == 1
                        && rejoin.respawns().get(BLUE) == 160L,
                "rejoin keeps team and schedules recovery instead of immediate combat");
        EntityDamageByEntityEvent recovering = hit(rejoin.blue.player, rejoin.red.player, 3);
        rejoin.session.onEntityDamageByEntity(recovering);
        check(recovering.isCancelled(), "rejoining player cannot attack during respawn delay");
        rejoin.connected().remove(BLUE_MATE);
        rejoin.withdrawn().add(BLUE_MATE);
        rejoin.state.withdraw(BLUE_MATE, 100);
        rejoin.session.onParticipantRejoin(rejoin.blueMate.player);
        check(
                !rejoin.connected().contains(BLUE_MATE)
                        && !rejoin.respawns().containsKey(BLUE_MATE),
                "withdrawn player cannot rejoin or acquire a respawn callback");
        FakePlayer unknown = new FakePlayer(UNKNOWN, rejoin.world.world);
        rejoin.session.onParticipantRejoin(unknown.player);
        check(!rejoin.connected().contains(UNKNOWN), "unknown player cannot join fixed roster");
    }

    private static void creditedEnemyKills() throws Exception {
        Fixture credited = new Fixture();
        credited.blue.killer = credited.red.player;
        PlayerDeathEvent death = death(credited.blue.player);
        credited.session.onPlayerDeath(death);
        FinalsKills kills = (FinalsKills) field(credited.session, "kills").get(credited.session);
        check(kills.snapshot().get(RED) == 1, "actual enemy death records native credited killer");
        credited.session.onPlayerDeath(death);
        check(kills.snapshot().get(RED) == 1, "duplicate session death cannot inflate kills");
        check(credited.red.sounds == 1, "only the actual credited enemy kill plays the killer cue once");
        credited.red.dead = true;
        credited.connected().remove(RED);
        credited.state.disconnect(RED, 100);
        credited.session.onParticipantRejoin(credited.red.player);
        check(kills.snapshot().get(RED) == 1, "session disconnect and recovery preserve match kills");

        Fixture friendly = new Fixture();
        friendly.blue.killer = friendly.blueMate.player;
        friendly.session.onPlayerDeath(death(friendly.blue.player));
        FinalsKills friendlyKills = (FinalsKills) field(friendly.session, "kills").get(friendly.session);
        check(friendlyKills.snapshot().isEmpty(), "actual friendly death receives no kill credit");

        Fixture outsider = new Fixture();
        outsider.blue.killer = new FakePlayer(UNKNOWN, outsider.world.world).player;
        outsider.session.onPlayerDeath(death(outsider.blue.player));
        FinalsKills outsiderKills = (FinalsKills) field(outsider.session, "kills").get(outsider.session);
        check(outsiderKills.snapshot().isEmpty(), "nonparticipant native killer receives no match credit");

        Fixture countdown = new Fixture();
        set(countdown.session, "state", null);
        countdown.blue.killer = countdown.red.player;
        countdown.session.onPlayerDeath(death(countdown.blue.player));
        FinalsKills countdownKills = (FinalsKills) field(countdown.session, "kills").get(countdown.session);
        check(countdownKills.snapshot().isEmpty(), "countdown accidents never count as match kills");
    }

    private static void teamWipeDeadlines() throws Exception {
        Fixture f = new Fixture();
        map(f.session, "roster").put(BLUE, 0);
        map(f.session, "roster").put(BLUE_MATE, 0);
        f.session.onPlayerDeath(death(f.red.player));
        set(f.session, "tick", 110L);
        f.session.onPlayerDeath(death(f.blue.player));
        check(f.respawns().get(RED) == 160L && f.respawns().get(BLUE) == 170L,
                "individual deaths keep the ordinary five-second schedule before a wipe");
        set(f.session, "tick", 120L);
        f.session.onPlayerDeath(death(f.blueMate.player));
        check(f.respawns().values().stream().allMatch(deadline -> deadline == 440L),
                "the third teammate death synchronizes all three timers to last death plus320 ticks");
        set(f.session, "tick", 439L);
        var spawn = FinalsSession.class.getDeclaredMethod("spawn", Player.class);
        spawn.setAccessible(true);
        spawn.invoke(f.session, f.red.player);
        check(map(f.session, "classes").isEmpty(), "the shared spawn entry cannot bypass a pending team wipe");
        f.respawns().remove(RED);
        check(!context(f.session).revive(f.blue.player, f.red.player),
                "revive rejects wipe-locked teammates even if another path makes a reviver active");
        f.respawns().put(RED, 440L);
        f.connected().remove(BLUE_MATE);
        f.state.disconnect(BLUE_MATE, 439L);
        set(f.session, "tick", 440L);
        var prepare = FinalsSession.class.getDeclaredMethod("prepareTeamRespawns");
        prepare.setAccessible(true);
        prepare.invoke(f.session);
        Map<UUID, Location> positions = map(f.session, "teamRespawnPositions");
        check(positions.size() == 3 && positions.values().stream().allMatch(positions.get(RED)::equals),
                "one chosen team spawn is cached identically for the entire cohort including an offline member");
        f.blueMate.dead = true;
        f.session.onParticipantRejoin(f.blueMate.player);
        check(f.respawns().get(BLUE_MATE) == 440L && positions.containsKey(BLUE_MATE),
                "late reconnect retains its completed wave and shared anchor instead of adding five more seconds");
        f.connected().clear();
        f.session.onEnd(me.petr1furious.potatominigames.core.game.EndReason.ABORTED);
        check(positions.isEmpty(), "match cleanup releases pending shared-spawn anchors");
    }

    private static void downedModeAndEligibility() throws Exception {
        FinalsSession exact = sessionFromStart(5, 6);
        check((Boolean) field(exact, "revivalMode").get(exact),
                "the actual six-player three-per-team start enables downed statues");
        FinalsSession duos = sessionFromStart(5, 4);
        FinalsSession hosted = sessionFromStart(5, 5);
        check((Boolean) field(duos, "revivalMode").get(duos)
                        && (Boolean) field(hosted, "revivalMode").get(hosted),
                "four-player and five-player hosted matches both enable two-v-two revivals");
        FinalsSession uneven = sessionFromStart(5, 3);
        FinalsSession larger = sessionFromStart(5, 8);
        check(!(Boolean) field(uneven, "revivalMode").get(uneven)
                        && !(Boolean) field(larger, "revivalMode").get(larger),
                "uneven and larger initial rosters retain normal respawn behavior");
        Fixture normal = new Fixture();
        normal.session.onPlayerDeath(death(normal.blue.player));
        check(context(normal.session).awaitingRespawn(normal.blue.player),
                "actual deaths outside three-v-three still qualify for the original instant Defibrillator");
        normal.connected().remove(RED);
        normal.state.disconnect(RED, 100);
        normal.red.dead = true;
        normal.session.onParticipantRejoin(normal.red.player);
        check(!context(normal.session).awaitingRespawn(normal.red.player),
                "a reconnect-only recovery timer never creates a revivable corpse");
        Fixture downed = new Fixture();
        set(downed.session, "revivalMode", true);
        downed.session.onPlayerDeath(death(downed.blue.player));
        check(downed.respawns().get(BLUE) == 260L, "a three-v-three death unlocks Space exactly eight seconds later");
        var request = FinalsSession.class.getDeclaredMethod("requestRespawn", UUID.class);
        request.setAccessible(true);
        set(downed.session, "tick", 259L);
        request.invoke(downed.session, BLUE);
        check(downed.respawns().containsKey(BLUE) && map(downed.session, "classes").isEmpty(),
                "Space before eight seconds cannot begin the native spawn path");
        set(downed.session, "tick", 1000L);
        var actualDowned = FinalsSession.class.getDeclaredMethod("downed", UUID.class);
        actualDowned.setAccessible(true);
        check((Boolean) actualDowned.invoke(downed.session, BLUE),
                "a corpse remains revivable indefinitely after the Space unlock time");
    }

    private static void gameMasterRole() throws Exception {
        FinalsSession hosted = sessionFromStart(5, 5);
        Map<UUID, Integer> teams = map(hosted, "roster");
        UUID master = new UUID(99, 5);
        check(master.equals(field(hosted, "gameMaster").get(hosted))
                        && teams.size() == 4 && !teams.containsKey(master),
                "the fifth participant is game master outside the four-contestant roster");
        check(teams.values().stream().filter(team -> team == 0).count() == 2
                        && teams.values().stream().filter(team -> team == 1).count() == 2,
                "a five-player start produces exactly two combatants on each team");
        SessionServices services = (SessionServices) field(hosted, "services").get(hosted);
        check(services.participants().find(master).orElseThrow().team() == null,
                "the game master has no core team assignment");
        FakePlayer observer = new FakePlayer(master, new FakeWorld().world);
        check(hosted.isGameMaster(observer.player) && !hosted.isInGame(observer.player)
                        && hosted.mayFollowPlayers(observer.player),
                "the game master may spectate but is never treated as a live contestant");
        check(!hosted.gameEventsReady(), "the game master cannot start events during map preparation/countdown");
        hosted.onTeamChange(observer.player, me.petr1furious.potatominigames.core.team.TeamColor.RED);
        check(services.participants().find(master).orElseThrow().team() == null,
                "team commands cannot move the game master onto a fighting team");
        set(hosted, "preparing", true);
        hosted.onParticipantQuit(observer.player);
        check(!hosted.isGameMaster(observer.player) && teams.size() == 4,
                "a disconnected game master loses controls without changing the teams");
        hosted.onParticipantRejoin(observer.player);
        check(hosted.isGameMaster(observer.player) && teams.size() == 4,
                "a returning game master retains the spectator role without becoming a contestant");
        hosted.onParticipantLeave(observer.player);
        hosted.onParticipantRejoin(observer.player);
        check(!hosted.isGameMaster(observer.player),
                "a permanent game-master departure cannot regain event controls by reconnecting");
        FinalsSession six = sessionFromStart(5, 6);
        check(field(six, "gameMaster").get(six) == null && map(six, "roster").size() == 6,
                "six participants remain ordinary three-v-three with no game master");
    }

    private static PlayerDeathEvent death(Player player) {
        return new PlayerDeathEvent(player, null, new ArrayList<>(), 0, Component.text("death"), true);
    }

    private static void legacyItemsAndNativeMutation() throws Exception {
        Fixture f = new Fixture();
        f.red.hand.type = Material.TNT;
        f.red.hand.amount = 2;
        f.red.location = new Location(f.world.world, 0, 65, 23.8f);
        f.session.onPlayerInteract(interact(f.red.player, null, EquipmentSlot.HAND));
        check(
                f.world.spawned == 0 && f.red.hand.amount == 2,
                "legacy TNT cannot spawn beyond arena boundary or consume an item");
        f.red.location = new Location(f.world.world, 0, 65, 0);
        f.protection().put(RED, 101L);
        f.session.onPlayerInteract(interact(f.red.player, null, EquipmentSlot.HAND));
        check(f.world.spawned == 0 && f.red.hand.amount == 2, "protected player cannot throw TNT");
        f.protection().clear();
        f.session.onPlayerInteract(interact(f.red.player, null, EquipmentSlot.HAND));
        check(
                f.world.spawned == 0 && f.red.hand.amount == 2 && f.ordnance().isEmpty(),
                "legacy raw TNT no longer launches or consumes items during combat");
        Block cover = f.world.block(6, 65, 3);
        Block floor = f.world.block(0, 64, 0);
        Block vault = f.world.block(0, 65, 0);
        Block wall = f.world.block(24, 65, 0);
        Bomb legacy = new Bomb(f.red.player);
        EntityExplodeEvent blast = explosion(legacy.entity, List.of(cover, floor, vault, wall));
        f.session.onEntityExplode(blast);
        check(
                blast.blockList().isEmpty() && blast.getYield() == 0,
                "untracked native TNT cannot mutate cover, floor, objectives or walls");
        EntityExplodeEvent outside = explosion(new Bomb(null).entity, List.of(cover));
        f.session.onEntityExplode(outside);
        check(outside.blockList().isEmpty(), "ownerless native blasts cannot alter arena blocks");
        BlockPlaceEvent placed =
                new BlockPlaceEvent(
                        cover, null, floor, f.red.hand, f.red.player, true, EquipmentSlot.HAND);
        f.session.onBlockPlace(placed);
        check(placed.isCancelled(), "native TNT and block placement are denied");
        f.red.slot = 0;
        BlockBreakEvent wrongSlot = new BlockBreakEvent(cover, f.red.player);
        f.session.onBlockBreak(wrongSlot);
        check(
                wrongSlot.isCancelled() && !wrongSlot.isDropItems(),
                "primary weapons cannot use native block breaking");
        f.red.slot = 7;
        for (Block protectedBlock : List.of(floor, vault, wall)) {
            BlockBreakEvent broken = new BlockBreakEvent(protectedBlock, f.red.player);
            f.session.onBlockBreak(broken);
            check(
                    broken.isCancelled() && !broken.isDropItems(),
                    "demolition slot preserves permanent terrain and objectives");
        }
        f.protection().put(RED, 101L);
        BlockBreakEvent protectedBreak = new BlockBreakEvent(cover, f.red.player);
        f.session.onBlockBreak(protectedBreak);
        check(protectedBreak.isCancelled(), "spawn protection also blocks terrain demolition");
        f.connected().remove(RED);
        EntityDamageByEntityEvent oldBlast = hit(legacy.entity, f.blue.player, 3);
        f.session.onEntityDamageByEntity(oldBlast);
        check(oldBlast.isCancelled(), "TNT from departed source cannot damage players");
    }

    private static void customDamageAndActionGates() throws Exception {
        Fixture f = new Fixture();
        EntityDamageByEntityEvent ordinarySelf = hit(f.blue.player, f.blue.player, 3);
        f.session.onEntityDamageByEntity(ordinarySelf);
        check(ordinarySelf.isCancelled(), "native self damage cannot bypass the team gate");
        set(f.session, "applyingCombatDamage", true);
        f.depositRed();
        f.state.beginSteal(BLUE, 100);
        EntityDamageByEntityEvent customSelf = hit(f.blue.player, f.blue.player, 3);
        f.session.onEntityDamageByEntity(customSelf);
        check(
                !customSelf.isCancelled() && !f.state.stealDeadlines().containsKey(BLUE),
                "custom self explosion passes the team gate and interrupts stealing");
        EntityDamageByEntityEvent customAlly = hit(f.blueMate.player, f.blue.player, 3);
        f.session.onEntityDamageByEntity(customAlly);
        check(customAlly.isCancelled(), "custom damage still cannot hurt teammates");
        f.protection().put(BLUE, 101L);
        EntityDamageByEntityEvent protectedSelf = hit(f.blue.player, f.blue.player, 3);
        f.session.onEntityDamageByEntity(protectedSelf);
        check(protectedSelf.isCancelled(), "custom self explosion respects spawn protection");
        f.protection().clear();
        f.respawns().put(BLUE, 160L);
        EntityDamageByEntityEvent respawningSelf = hit(f.blue.player, f.blue.player, 3);
        f.session.onEntityDamageByEntity(respawningSelf);
        check(
                respawningSelf.isCancelled(),
                "custom self explosion cannot hurt a respawning player");
        f.respawns().clear();
        set(f.session, "applyingCombatDamage", false);
        FinalsCombatContext context = context(f.session);
        check(context.canAct(f.blue.player), "active unprotected player can use combat equipment");
        f.state.beginSteal(BLUE, 100);
        check(!context.canAct(f.blue.player), "stealing blocks weapon and gadget activation");
        f.state.cancelSteal(BLUE);
        f.protection().put(BLUE, 101L);
        check(!context.canAct(f.blue.player), "spawn protection blocks gadget activation");
        f.protection().clear();
        f.respawns().put(BLUE, 160L);
        check(!context.canAct(f.blue.player), "respawn waiting blocks gadget activation");
        f.respawns().clear();
        set(f.session, "state", null);
        check(!context.canAct(f.blue.player), "countdown blocks gadget activation");
    }

    private static FinalsCombatContext context(FinalsSession session) throws Exception {
        var method = FinalsSession.class.getDeclaredMethod("combatContext");
        method.setAccessible(true);
        return (FinalsCombatContext) method.invoke(session);
    }

    private static void menuAndEquipmentSlotGates() throws Exception {
        Fixture f = new Fixture();
        set(f.session, "loadouts", bare(FinalsLoadouts.class));
        FinalsCombatContext context = context(f.session);
        check(context.canAct(f.blue.player), "ordinary inventory view permits combat");
        InventoryOpenEvent ordinary = new InventoryOpenEvent(f.blue.view);
        f.session.onInventoryOpen(ordinary);
        check(ordinary.isCancelled(), "ordinary container inventories stay locked");
        var menuType = Class.forName(FinalsLoadouts.class.getName() + "$Menu");
        var menuConstructor = menuType.getDeclaredConstructor(UUID.class);
        menuConstructor.setAccessible(true);
        f.blue.openHolder = (InventoryHolder) menuConstructor.newInstance(BLUE);
        check(!context.canAct(f.blue.player), "open loadout menu blocks all combat equipment");
        InventoryOpenEvent loadout = new InventoryOpenEvent(f.blue.view);
        f.session.onInventoryOpen(loadout);
        check(!loadout.isCancelled(), "loadout menu is the sole allowed inventory");
        f.blue.openHolder = null;
        check(context.canAct(f.blue.player), "closing loadout menu restores combat eligibility");

        FinalsWeapons weapons = new FinalsWeapons(context);
        var equippedType = Class.forName(FinalsWeapons.class.getName() + "$Equipped");
        var equippedConstructor = equippedType.getDeclaredConstructor(FinalsWeapon.class);
        equippedConstructor.setAccessible(true);
        map(weapons, "equipped")
                .put(BLUE, equippedConstructor.newInstance(FinalsWeapon.SLEDGEHAMMER));
        map(f.session, "classes").put(BLUE, FinalsClass.HEAVY);
        set(f.session, "weapons", weapons);
        for (int slot : new int[] {1, 4, 7, 8}) {
            f.blue.slot = slot;
            EntityDamageByEntityEvent handHit = hit(f.blue.player, f.red.player, 4);
            f.session.onEntityDamageByEntity(handHit);
            check(
                    handHit.isCancelled(),
                    "gadget, specialization, demolition and menu slots cannot deal vanilla melee"
                            + " damage");
        }
    }

    private static void protectedPlacementAreas() throws Exception {
        Fixture f = new Fixture();
        FinalsCombatContext context = context(f.session);
        List<Location> objectives = new ArrayList<>(f.arena.vaultLocations());
        objectives.add(f.arena.station(0));
        objectives.add(f.arena.station(1));
        for (Location objective : objectives) {
            check(
                    !context.canPlace(objective.getBlock()),
                    "objective block rejects temporary cover");
            check(
                    !context.canPlace(objective.clone().add(0, 1, 0).getBlock()),
                    "temporary cover cannot seal the air immediately above an objective");
            check(
                    !context.canPlace(objective.clone().add(3, 0, 0).getBlock()),
                    "objective placement protection includes its three-block boundary");
        }
        var spawnIndex = FinalsSession.class.getDeclaredMethod("spawnIndex", UUID.class);
        spawnIndex.setAccessible(true);
        for (var member : ROSTER.entrySet()) {
            Location spawn =
                    f.arena.spawn(
                            member.getValue(), (int) spawnIndex.invoke(f.session, member.getKey()));
            check(
                    !context.canPlace(spawn.getBlock()),
                    "each roster spawn stays clear at foot level");
            check(
                    !context.canPlace(spawn.clone().add(0, 1, 0).getBlock()),
                    "each roster spawn keeps standing headroom clear");
            check(
                    !context.canPlace(spawn.clone().add(2, 2, 0).getBlock()),
                    "two-block spawn protection also preserves upper barricade headroom");
        }
        Location spawn = f.arena.spawn(0, (int) spawnIndex.invoke(f.session, RED));
        f.connected().clear();
        check(
                !context.canPlace(spawn.getBlock()),
                "disconnected roster members retain safe future spawn locations");
        check(
                context.canPlace(f.world.block(5, 65, 0)),
                "remote in-bounds terrain remains available for useful cover");
        check(
                context.canPlace(f.world.block(5, 67, 0)),
                "remote upper cover is permitted outside reserved areas");
        check(
                !context.canPlace(f.world.block(30, 65, 0)),
                "placement outside arena bounds is denied");
        check(
                !context.canPlace(new FakeWorld().block(8, 65, 0)),
                "placement cannot leak into a different world");
        check(!context.canPlace(null), "missing placement targets are rejected safely");
        Location retired = f.arena.station(0).clone();
        f.arena.moveStation(0, retired.clone().add(0, 4, 0));
        check(context.canPlace(retired.getBlock()), "cashout protection follows the actual body, freeing its old position");
        FinalsGadgets gadgets = new FinalsGadgets(context);
        var placeable = FinalsGadgets.class.getDeclaredMethod("placeable", Block.class);
        placeable.setAccessible(true);
        check(
                !(boolean) placeable.invoke(gadgets, spawn.clone().add(0, 1, 0).getBlock()),
                "gadget placement enforces the session's spawn restriction before touching"
                        + " terrain");
        check(
                !(boolean)
                        placeable.invoke(gadgets, f.arena.vault().clone().add(0, 1, 0).getBlock()),
                "gadget placement enforces the session's objective restriction");
    }

    private static void mapPreparationGates() throws Exception {
        Fixture f = new Fixture();
        set(f.session, "preparing", true);
        set(f.session, "state", null);
        set(f.session, "arena", null);
        FinalsCombatContext context = context(f.session);
        check(!context.active(f.red.player) && !context.canAct(f.red.player),
                "map preparation blocks combat before weapons or geometry exist");
        check(f.session.spectatorLocation(f.red.player) == null,
                "spectators cannot enter a partially placed map");
        PlayerInteractEvent objective = interact(f.red.player, null, EquipmentSlot.HAND);
        f.session.onPlayerInteract(objective);
        check(objective.isCancelled(), "objective input is rejected while the map is preparing");
        EntityDamageByEntityEvent hit = hit(f.red.player, f.blue.player, 3);
        f.session.onEntityDamageByEntity(hit);
        check(hit.isCancelled(), "preparation prevents player damage");
        f.session.loadoutChanged(f.red.player);
        f.connected().remove(RED);
        f.session.onParticipantRejoin(f.red.player);
        check(f.connected().contains(RED) && f.respawns().isEmpty(),
                "rejoining during preparation waits for the prepared callback without spawning");
        var spawn = FinalsSession.class.getDeclaredMethod("spawn", Player.class);
        spawn.setAccessible(true);
        spawn.invoke(f.session, f.red.player);
        check(map(f.session, "classes").isEmpty(), "early spawn requests cannot apply uninitialized loadouts");
    }

    private static void countdownLoadoutWindow() throws Exception {
        ConfigRegistry configs = new ConfigRegistry(null);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("cashoutTimingVersion", 1);
        yaml.set("matchTimingVersion", 1);
        map(configs, "files").put(FinalsConfig.FILE, yaml);
        FinalsConfig defaults = new FinalsConfig(configs);
        check(defaults.countdownSeconds.get() == 18, "fresh Finals configuration grants 18 seconds for loadout selection");
        check(countdownFromStart(5) == 360L, "persisted five-second defaults cannot shorten the loadout window");
        check(countdownFromStart(0) == 360L, "invalid short settings still grant the full minimum window");
        check(countdownFromStart(45) == 900L, "longer configured loadout windows remain supported");
        check(countdownFromStart(1000) == 1200L, "loadout selection is bounded to sixty seconds");

        Fixture f = new Fixture(new Messages(() -> { throw new CountdownStarted(); }, null));
        set(f.session, "state", null);
        set(f.session, "countdownEnd", 360L);
        set(f.session, "tick", 358L);
        var tick = FinalsSession.class.getDeclaredMethod("tick");
        tick.setAccessible(true);
        tick.invoke(f.session);
        check(field(f.session, "state").get(f.session) == null
                        && (Long) field(f.session, "tick").get(f.session) == 359L,
                "combat remains disabled through countdown tick359");
        try {
            tick.invoke(f.session);
            throw new AssertionError("GO must broadcast at the end of the loadout window");
        } catch (java.lang.reflect.InvocationTargetException expected) {
            check(expected.getCause() instanceof CountdownStarted,
                    "tick360 reaches the real GO broadcast after creating match state");
        }
        FinalsState started = (FinalsState) field(f.session, "state").get(f.session);
        check(started != null && started.phase() == FinalsState.Phase.VAULT_CLOSED
                        && started.matchDeadline() == 360L + FinalsState.Rules.defaults().matchTicks(),
                "tick360 starts combat and its own complete match timer");
    }

    private static long countdownFromStart(int configured) throws Exception {
        FinalsSession session = sessionFromStart(configured, 2);
        return (Long) field(session, "countdownEnd").get(session);
    }

    private static FinalsSession sessionFromStart(int configured, int participantCount) throws Exception {
        ConfigRegistry configs = new ConfigRegistry(null);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("cashoutTimingVersion", 1);
        yaml.set("matchTimingVersion", 1);
        yaml.set("countdownSeconds", configured);
        map(configs, "files").put(FinalsConfig.FILE, yaml);
        FinalsConfig config = new FinalsConfig(configs);
        ParticipantRegistry participants = new ParticipantRegistry();
        FakeWorld world = new FakeWorld();
        for (int index = 0; index < participantCount; index++)
            participants.add(new FakePlayer(new UUID(99, index + 1), world.world).player);
        ActiveSession active = bare(ActiveSession.class);
        set(active, "phase", GamePhase.RUNNING);
        FinalsSession session = new FinalsSession(new SessionServices(null, active, participants,
                null, null, null, null, null, null, null, new Messages(Audience::empty, null), null), config);
        var start = FinalsSession.class.getDeclaredMethod("start");
        start.setAccessible(true);
        try {
            start.invoke(session);
            throw new AssertionError("The pure fixture must stop at the native world boundary");
        } catch (java.lang.reflect.InvocationTargetException expected) {
            check(expected.getCause() instanceof NullPointerException
                            && expected.getCause().getMessage().contains("createVoid"),
                    "actual start configures rules before requesting unavailable native world transport");
        }
        return session;
    }

    private static final class CountdownStarted extends RuntimeException {}

    private static void cashboxThrowDirection() {
        Vector aim = new Vector(0, 0, 4);
        Vector velocity = FinalsSession.cashboxThrowVelocity(aim);
        check(velocity.getZ() == 1.3 && velocity.getX() == 0 && velocity.getY() == .12,
                "a horizontal cashbox throw has aimed forward force and a small upward arc");
        check(aim.equals(new Vector(0, 0, 4)), "throw normalization never mutates the caller's aim");
        check(FinalsSession.cashboxThrowVelocity(new Vector(0, 1, 0)).getY() > 1.4,
                "looking up throws the cashbox upward with force");
        check(FinalsSession.cashboxThrowVelocity(new Vector(0, -1, 0)).getY() < -1.1,
                "looking down throws the cashbox downward");
    }

    private static void cashboxThrowGuards() throws Exception {
        Fixture f = new Fixture();
        var throwBox = FinalsSession.class.getDeclaredMethod("throwCashbox", Player.class);
        throwBox.setAccessible(true);
        throwBox.invoke(f.session, f.red.player);
        check(f.state.phase() == FinalsState.Phase.VAULT_CLOSED && f.world.spawned == 0,
                "a non-carrier cannot create or throw a cashbox");
        f.state.activateVault(RED, 0);
        f.state.pickUp(RED, 100);
        f.protection().put(RED, 101L);
        throwBox.invoke(f.session, f.red.player);
        check(RED.equals(f.state.carrier()) && f.world.spawned == 0,
                "a protected carrier cannot release the cashbox with Q");
        f.protection().clear();
        set(f.session, "preparing", true);
        throwBox.invoke(f.session, f.red.player);
        check(RED.equals(f.state.carrier()) && f.world.spawned == 0,
                "preparation blocks throwing before initialized geometry or item transport");
    }

    private static void cashoutStealSiren() throws Exception {
        Fixture f = new Fixture();
        java.lang.reflect.Method pulse;
        try {
            pulse = FinalsSession.class.getDeclaredMethod("updateStealSiren");
        } catch (NoSuchMethodException missing) {
            throw new AssertionError("An active cashout steal needs a repeating warning siren", missing);
        }
        pulse.setAccessible(true);
        pulse.invoke(f.session);
        f.depositRed();
        pulse.invoke(f.session);
        check(f.world.sounds.isEmpty(), "idle objectives and ordinary cashouts stay silent");
        var interact = FinalsSession.class.getDeclaredMethod("interactStation", Player.class, int.class);
        interact.setAccessible(true);
        f.blue.location = f.arena.station(0);
        f.blueMate.location = f.arena.station(0);
        interact.invoke(f.session, f.blue.player, 0);
        check(f.world.sounds.size() == 1, "starting a cashout steal immediately sounds the warning");
        interact.invoke(f.session, f.blueMate.player, 0);
        check(f.world.sounds.size() == 1, "simultaneous thieves share one siren without stacking sounds");
        set(f.session, "tick", 105L);
        pulse.invoke(f.session);
        check(f.world.sounds.size() == 1, "the siren respects its pulse interval");
        f.arena.moveStation(0, f.arena.station(0).add(0, -.5, 0));
        set(f.session, "tick", 106L);
        pulse.invoke(f.session);
        check(f.world.sounds.size() == 2
                        && f.world.sounds.get(0).pitch() != f.world.sounds.get(1).pitch(),
                "an ongoing steal alternates the two warning tones");
        check(f.world.sounds.get(1).location().equals(f.arena.station(0).add(0, .75, 0)),
                "each warning pulse follows the current moving cashout position");
        var interrupt = FinalsSession.class.getDeclaredMethod("interruptSteal", Player.class);
        interrupt.setAccessible(true);
        interrupt.invoke(f.session, f.blue.player);
        set(f.session, "tick", 112L);
        pulse.invoke(f.session);
        check(f.world.sounds.size() == 3, "the siren continues while another thief is still channeling");
        interrupt.invoke(f.session, f.blueMate.player);
        set(f.session, "tick", 118L);
        pulse.invoke(f.session);
        check(f.world.sounds.size() == 3, "interrupting the last channel immediately silences future pulses");
        interact.invoke(f.session, f.blue.player, 0);
        check(f.world.sounds.size() == 4, "a fresh steal immediately restarts its warning");
        long deadline = f.state.stealDeadlines().get(BLUE);
        check(f.state.completeSteal(BLUE, deadline), "the siren fixture completes a real steal");
        set(f.session, "tick", deadline);
        pulse.invoke(f.session);
        check(f.world.sounds.size() == 4, "a successful steal stops the warning");
        f.state.beginSteal(RED, deadline);
        pulse.invoke(f.session);
        check(f.world.sounds.size() == 5, "a later opposing channel has its own immediate warning");
        f.connected().clear();
        f.session.onEnd(me.petr1furious.potatominigames.core.game.EndReason.ABORTED);
        set(f.session, "tick", deadline + 6);
        pulse.invoke(f.session);
        check(f.world.sounds.size() == 5, "ending the session prevents all remaining siren pulses");
    }

    private static void vaultMovesOnlyAfterCashout() throws Exception {
        Fixture f = new Fixture();
        Location initial = f.arena.vault();
        f.state.activateVault(RED, 0);
        advanceObjective(f, 50);
        check(initial.equals(f.arena.vault()), "opening a vault never moves it");
        advanceObjective(f, 100);
        check(initial.equals(f.arena.vault()), "an opened vault keeps its cashbox location");
        check(
                initial.clone()
                        .add(0, 1, 0)
                        .equals(field(f.session, "cashboxLocation").get(f.session)),
                "the cashbox appears at the randomly selected vault");
        f.state.pickUp(RED, 100);
        advanceObjective(f, 101);
        check(initial.equals(f.arena.vault()), "carrying a cashbox never moves the vault");
        f.state.deposit(RED, 0, 101);
        advanceObjective(f, 900);
        check(initial.equals(f.arena.vault()), "an ongoing cashout keeps the current vault");
        advanceObjective(f, 901);
        Location next = f.arena.vault();
        check(!initial.equals(next), "a paid cashout selects a different next vault");
        check(
                f.state.phase() == FinalsState.Phase.VAULT_CLOSED && f.state.score(0) == 10000,
                "the next vault accompanies exactly one payout");
        check(
                field(f.session, "cashboxLocation").get(f.session) == null,
                "a paid cashout clears the old cashbox position");
        advanceObjective(f, 902);
        check(next.equals(f.arena.vault()), "ordinary ticks do not reroll the next vault");
        f.state.activateVault(RED, 902);
        f.state.pickUp(RED, 1002);
        f.state.deposit(RED, 1, 1002);
        advanceObjective(f, 1802);
        check(
                f.state.isFinished() && next.equals(f.arena.vault()),
                "the winning cashout ends the match without spawning another vault");
    }

    private static void advanceObjective(Fixture fixture, long tick) throws Exception {
        set(fixture.session, "tick", tick);
        var method = FinalsSession.class.getDeclaredMethod("advanceObjective");
        method.setAccessible(true);
        method.invoke(fixture.session);
    }

    private static void cashoutMovesOnlyAfterPayout() throws Exception {
        Fixture f = new Fixture();
        Location first = f.arena.station(0), second = f.arena.station(1);
        f.depositRed();
        advanceObjective(f, 899);
        check(
                first.equals(f.arena.station(0)) && second.equals(f.arena.station(1)),
                "an ongoing cashout keeps both station positions");
        advanceObjective(f, 900);
        Location replacement = f.arena.station(0);
        check(
                !first.equals(replacement),
                "a completed cashout moves its station to another location");
        check(
                second.equals(f.arena.station(1)),
                "a completed cashout leaves the other station in place");
        check(
                !replacement.equals(second),
                "the replacement never occupies the other active station");
        check(
                f.arena.stationAt(first.getBlock()) == -1,
                "the old station stops accepting deposits after payout");
        advanceObjective(f, 901);
        check(
                replacement.equals(f.arena.station(0)),
                "ordinary ticks do not move cashout stations");
        f.state.activateVault(RED, 901);
        f.state.pickUp(RED, 1001);
        f.state.deposit(RED, 1, 1001);
        advanceObjective(f, 1801);
        check(
                f.state.isFinished() && second.equals(f.arena.station(1)),
                "the winning payout ends the match without replacing its station");
    }

    private static void sharedTeamStartingPoints() throws Exception {
        Fixture f = new Fixture();
        UUID redMate = new UUID(1, 5);
        Map<UUID, Integer> roster = map(f.session, "roster");
        roster.clear();
        roster.put(RED, 0);
        roster.put(redMate, 0);
        roster.put(BLUE, 1);
        roster.put(BLUE_MATE, 1);
        var spawnIndex = FinalsSession.class.getDeclaredMethod("spawnIndex", UUID.class);
        spawnIndex.setAccessible(true);
        var spawnLocation = FinalsSession.class.getDeclaredMethod("spawnLocation", UUID.class);
        spawnLocation.setAccessible(true);
        set(f.session, "state", null);
        Location redStart = (Location) spawnLocation.invoke(f.session, RED);
        Location blueStart = (Location) spawnLocation.invoke(f.session, BLUE);
        check(redStart.equals(spawnLocation.invoke(f.session, redMate)),
                "all red teammates start at the same position during the loadout countdown");
        check(blueStart.equals(spawnLocation.invoke(f.session, BLUE_MATE)),
                "all blue teammates start at the same position during the loadout countdown");
        check(!redStart.equals(blueStart), "opposing teams retain distinct starting positions");
        List<Location> redCandidates = new ArrayList<>(), blueCandidates = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            redCandidates.add(f.arena.spawn(0, index));
            blueCandidates.add(f.arena.spawn(1, index));
        }
        check(redCandidates.contains(redStart) && blueCandidates.contains(blueStart),
                "each shared starting position belongs to that team's full configured candidate set");
        Location returned = (Location) spawnLocation.invoke(f.session, redMate);
        returned.add(100, 20, -100);
        returned.setYaw(42);
        check(redStart.equals(spawnLocation.invoke(f.session, RED)),
                "mutating a returned starting Location cannot move the cached team anchor");
        map(f.session, "classes").put(redMate, FinalsClass.HEAVY);
        map(f.session, "classes").put(BLUE_MATE, FinalsClass.LIGHT);
        set(f.session, "tick", 359L);
        check(redStart.equals(spawnLocation.invoke(f.session, redMate))
                        && blueStart.equals(spawnLocation.invoke(f.session, BLUE_MATE)),
                "countdown loadout changes keep each teammate at the shared team anchor");
        f.connected().remove(BLUE_MATE);
        f.connected().add(BLUE_MATE);
        check(blueStart.equals(spawnLocation.invoke(f.session, BLUE_MATE)),
                "reconnecting during the countdown retains the shared team starting point");

        set(f.session, "state", f.state);
        check((int) spawnIndex.invoke(f.session, RED) == 0 && (int) spawnIndex.invoke(f.session, redMate) == 1
                        && (int) spawnIndex.invoke(f.session, BLUE) == 0 && (int) spawnIndex.invoke(f.session, BLUE_MATE) == 1,
                "normal match respawns retain each teammate's original per-team spawn index");
        for (var entry : roster.entrySet()) check(
                spawnLocation.invoke(f.session, entry.getKey()).equals(
                        f.arena.spawn(entry.getValue(), (int) spawnIndex.invoke(f.session, entry.getKey()))),
                "ordinary later respawns keep the configured per-member position instead of the random start cache");
        f.connected().clear();
        f.session.onEnd(me.petr1furious.potatominigames.core.game.EndReason.ABORTED);
        check(map(f.session, "startingSpawns").isEmpty(), "ending the match clears its random starting-point cache");

        Fixture custom = new Fixture();
        set(custom.session, "state", null);
        Location selected = new Location(custom.world.world, 11, 70, -4, 35, 15);
        FinalsMapArena candidates = proxy(FinalsMapArena.class, (receiver, method, args) -> {
            if (method.getName().equals("spawnLocations")) return List.of(selected);
            return method.invoke(custom.arena, args);
        });
        set(custom.session, "arena", candidates);
        Location selectedCopy = selected.clone();
        check(spawnLocation.invoke(custom.session, RED).equals(selectedCopy),
                "starting selection uses the map's declared candidates instead of assuming generated spawn indices");
        selected.add(20, 4, 8);
        check(spawnLocation.invoke(custom.session, RED).equals(selectedCopy),
                "mutating a source candidate cannot change the cached team start");
    }

    private static void mapDeathHeight() throws Exception {
        for (Double level : java.util.Arrays.asList(65.0, null, 60.0)) {
            Fixture f = new Fixture();
            FinalsMapArena configured = proxy(FinalsMapArena.class, (p, method, args) ->
                    method.getName().equals("deathHeight") ? level : method.invoke(f.arena, args));
            set(f.session, "arena", configured);
            Location at = f.red.location.clone();
            f.session.onPlayerMove(new PlayerMoveEvent(f.red.player, at, at.clone()));
            check(f.red.health == 20, "Standing exactly at or above the death height is safe");
            Location below = at.clone(); below.setY(64.99);
            f.session.onPlayerMove(new PlayerMoveEvent(f.red.player, at, below));
            check(f.red.health == (level != null && level == 65 ? 0 : 20),
                    "Only the selected map's death height kills a player below it");
        }
        Fixture frozen = new Fixture();
        set(frozen.session, "arena", proxy(FinalsMapArena.class, (p, method, args) ->
                method.getName().equals("deathHeight") ? 66d : method.invoke(frozen.arena, args)));
        callPlayerMethod(frozen.session, "freeze", frozen.red.player);
        frozen.session.onPlayerMove(new PlayerMoveEvent(frozen.red.player, frozen.red.location,
                frozen.red.location.clone().subtract(0, 1, 0)));
        check(frozen.red.health == 20, "Countdown freeze takes precedence over the death floor");
    }

    private static void objectiveRecovery() throws Exception {
        Fixture f = new Fixture();
        set(f.session, "arena", proxy(FinalsMapArena.class, (p, method, args) ->
                switch (method.getName()) {
                    case "deathHeight" -> 60d;
                    case "contains" -> ((Location) args[0]).getWorld().equals(f.world.world);
                    default -> method.invoke(f.arena, args);
                }));
        Location ledge = new Location(f.world.world, 10.5, 80, 10.5);
        f.world.block(10, 79, 10).setType(Material.STONE);
        f.red.location = ledge.clone();
        f.red.grounded = true;
        f.session.onPlayerMove(new PlayerMoveEvent(f.red.player, ledge, ledge.clone()));
        f.red.grounded = false;
        f.red.location = ledge.clone().add(4, -25, 0);
        f.session.onPlayerMove(new PlayerMoveEvent(f.red.player, f.red.location, f.red.location));
        var recovery = FinalsSession.class.getDeclaredMethod("recoveryPoint", UUID.class);
        recovery.setAccessible(true);
        check(recovery.invoke(f.session, RED).equals(ledge), "Falling below the death floor preserves the departure ledge");
        set(f.session, "cashboxRecovery", recovery.invoke(f.session, RED));
        var returnBox = FinalsSession.class.getDeclaredMethod("cashboxReturnPoint");
        returnBox.setAccessible(true);
        check(returnBox.invoke(f.session).equals(ledge.clone().add(0, .25, 0)),
                "Cashbox returns just above its last grounded departure, not the vault or airborne release");
        var ctx = FinalsSession.class.getDeclaredMethod("reviveContext"); ctx.setAccessible(true);
        var revive = (FinalsRevives.Context) ctx.invoke(f.session);
        check(revive.recovery(RED).equals(ledge), "Statues use the same departure anchor as the cashbox");
        check(!revive.inside(f.red.location), "Statue movement treats a custom death floor as out of bounds");
        Location boundary = ledge.clone(); boundary.setY(60);
        check(revive.inside(boundary), "Objects exactly at the death floor remain valid");
        f.world.block(10, 79, 10).setType(Material.AIR);
        check(!revive.safeRecovery(ledge), "A destroyed departure ledge is no longer a valid return location");
        check(!recovery.invoke(f.session, RED).equals(ledge), "Destroyed ledges use a supported fallback");
    }

    private static void fixedCountdownAnchor() throws Exception {
        Fixture f = new Fixture();
        f.red.walkSpeed = .3f;
        f.red.flySpeed = .2f;
        f.red.velocity = new Vector(.2, .42, -.1);
        callPlayerMethod(f.session, "freeze", f.red.player);
        check(
                f.red.walkSpeed == 0 && f.red.flySpeed == 0 && f.red.velocity.lengthSquared() == 0,
                "freeze disables walking and flight and clears preexisting velocity");
        f.red.location.add(.15, .4, .15);
        callPlayerMethod(f.session, "freeze", f.red.player);
        Location attempted = new Location(f.world.world, 3, 67, 3, 70, 25);
        PlayerMoveEvent move = new PlayerMoveEvent(f.red.player, f.red.location.clone(), attempted);
        f.session.onPlayerMove(move);
        check(
                move.getTo().getX() == 2 && move.getTo().getY() == 65 && move.getTo().getZ() == 2,
                "repeated countdown movement returns to original anchor instead of drifting"
                        + " event.from");
        check(
                move.getTo().getYaw() == 70 && move.getTo().getPitch() == 25,
                "countdown permits looking without changing position");
        PlayerJumpEvent jump =
                new PlayerJumpEvent(f.red.player, f.red.location.clone(), attempted.clone());
        f.session.onPlayerJump(jump);
        check(
                jump.isCancelled() && f.red.velocity.lengthSquared() == 0,
                "countdown cancels jumping before velocity can accumulate");
        callPlayerMethod(f.session, "release", f.red.player);
        callPlayerMethod(f.session, "release", f.red.player);
        check(
                f.red.walkSpeed == .3f && f.red.flySpeed == .2f,
                "release restores original custom speeds exactly once after repeated freeze");
        PlayerMoveEvent released =
                new PlayerMoveEvent(f.red.player, f.red.location.clone(), attempted.clone());
        f.session.onPlayerMove(released);
        check(
                released.getTo().getX() == 3 && released.getTo().getY() == 67,
                "released fighter moves freely within arena");
        PlayerJumpEvent releasedJump =
                new PlayerJumpEvent(f.red.player, f.red.location.clone(), attempted.clone());
        f.session.onPlayerJump(releasedJump);
        check(!releasedJump.isCancelled(), "release restores jumping");
    }

    private static void callPlayerMethod(FinalsSession session, String name, Player player)
            throws Exception {
        var method = FinalsSession.class.getDeclaredMethod(name, Player.class);
        method.setAccessible(true);
        method.invoke(session, player);
    }

    private static EntityDamageByEntityEvent hit(Entity source, Player target, double damage) {
        return new EntityDamageByEntityEvent(
                source, target, EntityDamageEvent.DamageCause.ENTITY_ATTACK, null, damage);
    }

    private static EntityDamageEvent environmental(Player target, double damage) {
        return new EntityDamageEvent(target, EntityDamageEvent.DamageCause.FIRE, null, damage);
    }

    private static PlayerInteractEvent interact(Player player, Block block, EquipmentSlot hand) {
        return interaction(player, block, hand, block == null ? Action.RIGHT_CLICK_AIR : Action.RIGHT_CLICK_BLOCK);
    }

    private static PlayerInteractEvent interaction(Player player, Block block, EquipmentSlot hand, Action action) {
        return new PlayerInteractEvent(
                player,
                action,
                null,
                block,
                BlockFace.UP,
                hand);
    }

    private static EntityExplodeEvent explosion(Entity entity, List<Block> blocks) {
        return new EntityExplodeEvent(
                entity,
                new Location(null, 0, 65, 0),
                new ArrayList<>(blocks),
                1,
                ExplosionResult.DESTROY);
    }

    private static final class Fixture {
        // Explicit short cashouts keep the existing objective-rotation boundary fixtures stable.
        final FinalsState.Rules rules = new FinalsState.Rules(100, 800, 100, 12_000, 10_000, 20_000);
        final FakeWorld world = new FakeWorld();
        final FinalsArena arena = new FinalsArena(world.world);
        final FinalsState state = new FinalsState(ROSTER, 0, rules);
        final FakePlayer red = new FakePlayer(RED, world.world);
        final FakePlayer blue = new FakePlayer(BLUE, world.world);
        final FakePlayer blueMate = new FakePlayer(BLUE_MATE, world.world);
        final FinalsSession session;

        Fixture() throws Exception {
            this(new Messages(Audience::empty, null));
        }

        Fixture(Messages messages) throws Exception {
            ActiveSession active = bare(ActiveSession.class);
            set(active, "phase", GamePhase.RUNNING);
            session =
                    new FinalsSession(
                            new SessionServices(
                                    null, active, null, null, null, null, null, null, null, null,
                                    messages, null),
                            null);
            arena.build();
            set(session, "world", world.world);
            set(session, "arena", arena);
            set(session, "state", state);
            set(session, "rules", rules);
            set(session, "tick", 100L);
            set(session, "respawnTicks", 60);
            map(session, "roster").putAll(ROSTER);
            connected().addAll(ROSTER.keySet());
        }

        void depositRed() {
            state.activateVault(RED, 0);
            state.pickUp(RED, 100);
            state.deposit(RED, 0, 100);
        }

        Set<UUID> connected() throws Exception {
            return setField(session, "connected");
        }

        Set<UUID> withdrawn() throws Exception {
            return setField(session, "withdrawn");
        }

        Map<UUID, Long> protection() throws Exception {
            return map(session, "protectedUntil");
        }

        Map<UUID, Long> respawns() throws Exception {
            return map(session, "respawnAt");
        }

        Map<UUID, Entity> ordnance() throws Exception {
            return map(session, "ordnance");
        }
    }

    private static final class FakeStack extends ItemStack {
        Material type = Material.AIR;
        int amount = 1;

        @Override
        public Material getType() {
            return type;
        }

        @Override
        public int getAmount() {
            return amount;
        }

        @Override
        public void setAmount(int amount) {
            this.amount = amount;
        }
    }

    private static final class FakePlayer {
        final UUID id;
        final FakeStack hand = new FakeStack();
        final Player player;
        Location location;
        boolean dead;
        boolean grounded;
        double health = 20;
        Player killer;
        int sounds;
        int slot;
        InventoryHolder openHolder;
        final InventoryView view;
        float walkSpeed = .2f, flySpeed = .1f;
        Vector velocity = new Vector();
        DamageAction damageAction;

        FakePlayer(UUID id, World world) {
            this.id = id;
            location = new Location(world, 2, 65, 2);
            Inventory top =
                    proxy(
                            Inventory.class,
                            (p, method, args) ->
                                    switch (method.getName()) {
                                        case "getHolder" -> openHolder;
                                        default ->
                                                throw new AssertionError(
                                                        "Unexpected top inventory call: "
                                                                + method.getName());
                                    });
            view =
                    proxy(
                            InventoryView.class,
                            (p, method, args) ->
                                    switch (method.getName()) {
                                        case "getTopInventory" -> top;
                                        default ->
                                                throw new AssertionError(
                                                        "Unexpected inventory view call: "
                                                                + method.getName());
                                    });
            PlayerInventory inventory =
                    proxy(
                            PlayerInventory.class,
                            (p, method, args) ->
                                    switch (method.getName()) {
                                        case "getItemInMainHand" -> hand;
                                        case "getHeldItemSlot" -> slot;
                                        case "setItemInOffHand" -> null;
                                        default ->
                                                throw new AssertionError(
                                                        "Unexpected inventory call: "
                                                                + method.getName());
                                    });
            player =
                    proxy(
                            Player.class,
                            (p, method, args) ->
                                    switch (method.getName()) {
                                        case "getUniqueId" -> id;
                                        case "getName" -> id.toString();
                                        case "getWorld" -> location.getWorld();
                                        case "getGameMode" -> GameMode.SURVIVAL;
                                        case "getLocation" -> location.clone();
                                        case "getEyeLocation" -> location.clone().add(0, 1.6, 0);
                                        case "getInventory" -> inventory;
                                        case "getOpenInventory" -> view;
                                        case "closeInventory" -> { openHolder = null; yield null; }
                                        case "getWalkSpeed" -> walkSpeed;
                                        case "getFlySpeed" -> flySpeed;
                                        case "setWalkSpeed" -> {
                                            walkSpeed = (float) args[0];
                                            yield null;
                                        }
                                        case "setFlySpeed" -> {
                                            flySpeed = (float) args[0];
                                            yield null;
                                        }
                                        case "setVelocity" -> {
                                            velocity = ((Vector) args[0]).clone();
                                            yield null;
                                        }
                                        case "isDead" -> dead;
                                        case "isOnGround" -> grounded;
                                        case "setHealth" -> { health = (double) args[0]; yield null; }
                                        case "getKiller" -> killer;
                                        case "playSound" -> { sounds++; yield null; }
                                        case "setNoDamageTicks" -> null;
                                        case "damage" -> {
                                            if (damageAction == null) throw new AssertionError("Unexpected native damage callback");
                                            damageAction.accept((double) args[0], args.length > 1 ? (Entity) args[1] : null);
                                            yield null;
                                        }
                                        case "setGlowing", "removePotionEffect" -> null;
                                        case "isSneaking" -> true;
                                        case "sendMessage" -> null;
                                        case "displayName" -> Component.text(id.toString());
                                        case "equals" -> p == args[0];
                                        case "hashCode" -> id.hashCode();
                                        default ->
                                                throw new AssertionError(
                                                        "Unexpected player call: "
                                                                + method.getName());
                                    });
        }
    }

    private static final class Shot {
        final UUID id = UUID.randomUUID();
        final Projectile projectile;
        int removed;

        Shot(Player source) {
            projectile =
                    proxy(
                            Projectile.class,
                            (p, method, args) ->
                                    switch (method.getName()) {
                                        case "getUniqueId" -> id;
                                        case "getShooter" -> source;
                                        case "isValid" -> removed == 0;
                                        case "remove" -> {
                                            removed++;
                                            yield null;
                                        }
                                        default ->
                                                throw new AssertionError(
                                                        "Unexpected projectile call: "
                                                                + method.getName());
                                    });
        }
    }

    private static final class Bomb {
        final UUID id = UUID.randomUUID();
        final TNTPrimed entity;
        Player source;
        int fuse;

        Bomb(Player source) {
            this.source = source;
            entity =
                    proxy(
                            TNTPrimed.class,
                            (p, method, args) ->
                                    switch (method.getName()) {
                                        case "getUniqueId" -> id;
                                        case "getSource" -> this.source;
                                        case "setSource" -> {
                                            this.source = (Player) args[0];
                                            yield null;
                                        }
                                        case "setFuseTicks" -> {
                                            fuse = (int) args[0];
                                            yield null;
                                        }
                                        case "setYield", "setVelocity" -> null;
                                        case "isValid" -> true;
                                        default ->
                                                throw new AssertionError(
                                                        "Unexpected TNT call: " + method.getName());
                                    });
        }
    }

    private record Position(int x, int y, int z) {}

    private record SoundPulse(Location location, float pitch) {}

    private static final class FakeWorld {
        final Map<Position, Material> blocks = new HashMap<>();
        final Map<Position, BlockData> data = new HashMap<>();
        final List<SoundPulse> sounds = new ArrayList<>();
        final World world;
        int spawned;
        Bomb lastBomb;

        void openable(Block block, Material material, boolean open, Bisected.Half half) {
            block.setType(material);
            data.put(new Position(block.getX(), block.getY(), block.getZ()),
                    new OpenState(material, open, half).data());
        }

        boolean open(Block block) {
            return ((org.bukkit.block.data.Openable) block.getBlockData()).isOpen();
        }

        void unchangedShape(Block block) {
            BlockData state = block.getBlockData();
            check(((org.bukkit.block.data.Directional) state).getFacing() == BlockFace.WEST,
                    "opening keeps the original facing");
            check(((org.bukkit.block.data.Powerable) state).isPowered(), "opening keeps the powered property");
            if (state instanceof Door door) check(door.getHinge() == Door.Hinge.RIGHT, "opening keeps door hinge");
            if (state instanceof TrapDoor trapdoor) check(trapdoor.isWaterlogged(), "opening keeps waterlogging");
        }

        FakeWorld() {
            world =
                    proxy(
                            World.class,
                            (p, method, args) ->
                                    switch (method.getName()) {
                                        case "getBlockAt" ->
                                                args[0] instanceof Location location
                                                        ? block(
                                                                location.getBlockX(),
                                                                location.getBlockY(),
                                                                location.getBlockZ())
                                                        : block(
                                                                (int) args[0],
                                                                (int) args[1],
                                                                (int) args[2]);
                                        case "setSpawnLocation" -> true;
                                        case "playSound" -> {
                                            sounds.add(new SoundPulse(((Location) args[0]).clone(), (float) args[3]));
                                            yield null;
                                        }
                                        case "equals" -> p == args[0];
                                        case "hashCode" -> System.identityHashCode(p);
                                        case "spawn" -> {
                                            spawned++;
                                            lastBomb = new Bomb(null);
                                            yield lastBomb.entity;
                                        }
                                        default ->
                                                throw new AssertionError(
                                                        "Unexpected world call: "
                                                                + method.getName());
                                    });
        }

        Block block(int x, int y, int z) {
            Position at = new Position(x, y, z);
            return proxy(
                    Block.class,
                    (p, method, args) ->
                            switch (method.getName()) {
                                case "getWorld" -> world;
                                case "getX" -> x;
                                case "getY" -> y;
                                case "getZ" -> z;
                                case "getType" -> blocks.getOrDefault(at, Material.AIR);
                                case "isPassable" -> blocks.getOrDefault(at, Material.AIR) == Material.AIR;
                                case "isLiquid" -> false;
                                case "setType" -> {
                                    blocks.put(at, (Material) args[0]);
                                    data.remove(at);
                                    yield null;
                                }
                                case "getBlockData" -> data.getOrDefault(at,
                                        proxy(BlockData.class, (ignored, operation, values) -> {
                                            if (operation.getName().equals("getMaterial")) return blocks.getOrDefault(at, Material.AIR);
                                            throw new AssertionError("Unexpected plain block data: " + operation.getName());
                                        }));
                                case "setBlockData" -> { data.put(at, (BlockData) args[0]); yield null; }
                                case "getRelative" -> {
                                    BlockFace face = (BlockFace) args[0];
                                    int distance = args.length == 2 ? (int) args[1] : 1;
                                    yield block(x + face.getModX() * distance, y + face.getModY() * distance,
                                            z + face.getModZ() * distance);
                                }
                                case "getLocation" -> new Location(world, x, y, z);
                                case "equals" -> p == args[0];
                                case "hashCode" -> at.hashCode();
                                default ->
                                        throw new AssertionError(
                                                "Unexpected block call: " + method.getName());
                            });
        }
    }

    private static final class OpenState {
        final Material material;
        boolean open;
        Bisected.Half half;
        BlockFace facing = BlockFace.WEST;
        Door.Hinge hinge = Door.Hinge.RIGHT;
        boolean powered = true, waterlogged = true;

        OpenState(Material material, boolean open, Bisected.Half half) {
            this.material = material;
            this.open = open;
            this.half = half;
        }

        BlockData data() {
            Class<? extends BlockData> type = material.name().endsWith("TRAPDOOR") ? TrapDoor.class : Door.class;
            return proxy(type, (p, method, args) -> switch (method.getName()) {
                case "getMaterial" -> material;
                case "isOpen" -> open;
                case "setOpen" -> { open = (boolean) args[0]; yield null; }
                case "getHalf" -> half;
                case "setHalf" -> { half = (Bisected.Half) args[0]; yield null; }
                case "getFacing" -> facing;
                case "setFacing" -> { facing = (BlockFace) args[0]; yield null; }
                case "getHinge" -> hinge;
                case "setHinge" -> { hinge = (Door.Hinge) args[0]; yield null; }
                case "isPowered" -> powered;
                case "setPowered" -> { powered = (boolean) args[0]; yield null; }
                case "isWaterlogged" -> waterlogged;
                case "setWaterlogged" -> { waterlogged = (boolean) args[0]; yield null; }
                case "clone" -> {
                    OpenState copy = new OpenState(material, open, half);
                    copy.facing = facing; copy.hinge = hinge;
                    copy.powered = powered; copy.waterlogged = waterlogged;
                    yield copy.data();
                }
                default -> throw new AssertionError("Unexpected openable data call: " + method.getName());
            });
        }
    }

    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return type.cast(
                Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }

    private static <T> T bare(Class<T> type) throws Exception {
        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return type.cast(((sun.misc.Unsafe) f.get(null)).allocateInstance(type));
    }

    private static Field field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static void set(Object target, String name, Object value) throws Exception {
        field(target, name).set(target, value);
    }

    @SuppressWarnings("unchecked")
    private static <K, V> Map<K, V> map(Object target, String name) throws Exception {
        return (Map<K, V>) field(target, name).get(target);
    }

    @SuppressWarnings("unchecked")
    private static <T> Set<T> setField(Object target, String name) throws Exception {
        return (Set<T>) field(target, name).get(target);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
