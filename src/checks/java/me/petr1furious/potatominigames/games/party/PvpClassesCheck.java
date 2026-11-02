package me.petr1furious.potatominigames.games.party;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/** Kit layout, potion targeting/caps, random eligibility and combat-passive rules. */
public final class PvpClassesCheck {
    public static void main(String[] args) {
        loadouts();
        arrowChecks();
        randomEligibility();
        combatRules();
        harpoonChecks();
        thunderChecks();
        demolitionistChecks();
        System.out.println("PvP classes: " + PvpClass.values().length + " equally eligible kits, mage potions, wind charges, slot safety, refill caps, frost/backstab/leech/wand cooldowns safe movement and ordered finite effect arrows passed");
    }
    private static void loadouts() {
        for (PvpClass kit : PvpClass.values()) {
            Set<Integer> occupied = new HashSet<>(List.of(0, 1, 2, 7, PvpClass.FOOD_SLOT));
            for (var supply : kit.supplies()) {
                check(supply.slot() >= 0 && supply.slot() < 36 && occupied.add(supply.slot()), "Supplies cannot overwrite common kit items: " + kit);
                check(supply.amount() > 0 && supply.amount() <= 64, "Supply stack has a valid size");
            }
            boolean bow = kit.supplies().stream().anyMatch(slot -> slot.material() == Material.BOW || slot.material() == Material.CROSSBOW);
            check(bow == kit.unlimitedArrows(), "Every bow/crossbow class has unlimited ammo");
            if (bow) check(kit.supplies().stream().anyMatch(slot -> slot.material() == Material.ARROW && slot.amount() == 64), "Every ranged kit starts with a full arrow stack");
            for (PvpArrow arrow : kit.specialArrows()) {
                check(arrow.slot() >= 9 && arrow.slot() < 36 && occupied.add(arrow.slot()), "Special arrows cannot overwrite kit items: " + kit);
            }
            int healing = 0;
            for (var potion : kit.potions()) {
                check(potion.slot() >= 0 && potion.slot() < 36 && occupied.add(potion.slot()), "Potions cannot overwrite weapons, shears, blocks, food, ammo or ability: " + kit);
                check(potion.amount() > 0 && potion.amount() <= 8, "Potion stack fits its custom stack limit");
                if (potion.kind() == PvpPotion.HEALING) healing += potion.amount();
            }
            check(healing > 0 && healing <= 3, "Each kit has limited healing");
            check(kit.armor().helmet() != null && kit.armor().chestplate() != null && kit.armor().leggings() != null && kit.armor().boots() != null, "All four armour slots assigned");
            check(kit.primaryWeapon() != null && kit.cooldown >= (kit == PvpClass.THUNDERER ? 6 : 14) && kit.cooldown <= 24, "Every class has a weapon and bounded ability cooldown");
        }
        check(PvpClass.ALCHEMIST.potions().stream().mapToInt(PvpClass.PotionSlot::amount).sum() == 12, "Mage has twelve starting potions");
        check(PvpClass.ALCHEMIST.potions().stream().map(PvpClass.PotionSlot::kind).distinct().count() == 6, "Mage has six potion types");
        check(PvpClass.ENGINEER.blocks() > PvpClass.GUARDIAN.blocks(), "Engineer has a larger building reserve");
        check(PvpClass.GUARDIAN.armor().boots() == Material.NETHERITE_BOOTS, "Guardian retains its heavier armour identity");
        check(PvpClass.ALCHEMIST.armor().chestplate() == Material.LEATHER_CHESTPLATE, "Mage's potion arsenal has a lighter-armour tradeoff");
        check(PvpClass.AERONAUT.primaryWeapon() == Material.MACE, "Aerial knight gets a real mace");
        check(PvpClass.AERONAUT.supplies().stream().anyMatch(slot -> slot.material() == Material.WIND_CHARGE && slot.amount() == 8), "Aerial knight has eight native wind charges");
        check(PvpClass.AERONAUT.supplies().stream().anyMatch(slot -> slot.material() == Material.STONE_SWORD), "Aerial knight can fight on the ground");
        check(PvpClass.CRYOMANCER.supplies().stream().anyMatch(slot -> slot.material() == Material.SNOWBALL && slot.amount() == 16), "Cryomancer starts with sixteen projectiles");
        check(PvpClass.LANCER.primaryWeapon() == Material.IRON_SPEAR, "Lancer uses the native iron spear");
        check(PvpClass.LANCER.supplies().stream().anyMatch(slot -> slot.material() == Material.STONE_SWORD), "Lancer retains a backup after losing their horse");
        check(PvpClass.LANCER.armor().chestplate() == Material.LEATHER_CHESTPLATE, "Mounted mobility has a light-armour tradeoff");
        check(PvpClass.SHADOW.primaryWeapon() == Material.DIAMOND_SWORD, "Shadow has the requested diamond sword");
        check(PvpClass.SHADOW.armor().helmet() == Material.AIR
                && PvpClass.SHADOW.armor().chestplate() == Material.AIR
                && PvpClass.SHADOW.armor().leggings() == Material.AIR
                && PvpClass.SHADOW.armor().boots() == Material.AIR, "Shadow has no armour even outside invisibility");
        int invisibility = PvpClass.SHADOW.potions().stream().filter(slot -> slot.kind() == PvpPotion.INVISIBILITY)
                .mapToInt(PvpClass.PotionSlot::amount).sum();
        check(invisibility == 2 && PvpPotion.INVISIBILITY.durationTicks == 400, "Shadow has two finite twenty-second splash invisibility charges");
        check(PvpClass.SHADOW.cooldown == PvpCombat.SHADOW_WAND_COOLDOWN, "Wand tooltip and on-hit cooldown agree");
        for (PvpClass kit : PvpClass.values()) {
            check(!kit.focusPotions().contains(PvpPotion.INVISIBILITY), "No class can replenish invisibility potions");
            if (kit != PvpClass.SHADOW) check(kit.potions().stream().noneMatch(slot -> slot.kind() == PvpPotion.INVISIBILITY),
                    "Only Shadow receives invisibility stock");
        }
        for (PvpPotion potion : PvpClass.ALCHEMIST.focusPotions()) {
            check(potion.harmful, "Focus cannot regenerate healing or speed potions");
            check(PvpClass.ALCHEMIST.potions().stream().anyMatch(slot -> slot.kind() == potion), "Focus only supplies existing mage spells");
        }
        for (PvpPotion potion : PvpPotion.values()) {
            check(!potion.affects(false, false) && !potion.affects(true, false), "Potions never affect spectators or eliminated players");
            check(potion.affects(true, true) != potion.affects(false, true), "Beneficial and harmful splashes have separate targets");
            check(potion.affects(false, true) == potion.harmful, "Only harmful potions affect opponents");
        }
        for (int owned = 0; owned <= 8; owned++) for (int amount = 0; amount <= 3; amount++) for (int cap = 0; cap <= 4; cap++) {
            int added = PvpPotion.refillAmount(owned, amount, cap);
            check(added >= 0 && added <= amount, "Refill cannot exceed request or become negative");
            if (owned <= cap) check(owned + added <= cap, "Refill never exceeds stock cap");
            else check(added == 0, "Over-cap inventory cannot be refilled");
        }
    }
    private static void arrowChecks() {
        check(PvpClass.RANGER.specialArrows().equals(List.of(PvpArrow.SLOWNESS, PvpArrow.POISON, PvpArrow.WEAKNESS, PvpArrow.SPECTRAL)),
                "Ranger arrows are ordered by usefulness: slow, poison, weakness, spectral");
        for (PvpClass kit : PvpClass.values()) if (kit != PvpClass.RANGER)
            check(kit.specialArrows().isEmpty(), "Only Ranger receives the new special ammunition");
        int next = 9, tipped = 0;
        for (PvpArrow arrow : PvpClass.RANGER.specialArrows()) {
            check(arrow.slot() == next++, "Special ammunition occupies consecutive top-row slots");
            check(arrow.material() != Material.ARROW && arrow.amount > 0 && arrow.amount <= 6, "Special ammunition is finite and excluded from regular-arrow refill");
            if (arrow.material() == Material.TIPPED_ARROW) {
                tipped += arrow.amount;
                check(arrow.durationTicks >= 20 && arrow.durationTicks <= 100, "Debuffs are short level-I control effects");
            }
        }
        check(tipped == 14, "Ranger starts with fourteen finite tipped arrows");
        int normal = PvpClass.RANGER.supplies().stream().filter(slot -> slot.material() == Material.ARROW).findFirst().orElseThrow().slot();
        check(normal == next && normal == PvpArrow.NORMAL_SLOT, "Ordinary arrows follow every special stack in native inventory selection");
        check(PvpArrow.normalRefillSlot(slot -> true) == normal, "Missing normal arrows are not recreated ahead of specials");
        check(PvpArrow.normalRefillSlot(slot -> slot != normal) == normal + 1, "Refill preserves another item moved into the regular-arrow slot");
        check(PvpArrow.normalRefillSlot(slot -> slot < normal) == -1, "A full reserve area never falls back to the hotbar or overwrites specials");
    }
    private static void harpoonChecks() {
        PvpClass kit = PvpClass.HARPOONER;
        check(kit.primaryWeapon() == Material.TRIDENT, "Harpooner gets a real throwable trident");
        check(kit.armor().chestplate() == Material.CHAINMAIL_CHESTPLATE
                && kit.armor().leggings() == Material.CHAINMAIL_LEGGINGS, "Charge mobility trades heavy armour for chain armour");
        check(kit.supplies().stream().anyMatch(slot -> slot.material() == Material.STONE_SWORD), "A thrown trident leaves a backup weapon");
        check(kit.potions().stream().filter(slot -> slot.kind() == PvpPotion.HEALING).mapToInt(PvpClass.PotionSlot::amount).sum() == 1,
                "Charge kit has only one finite healing potion");
        check(kit.cooldown == 20 && PvpHarpoon.DAMAGE == 6 && PvpHarpoon.DURATION == 12,
                "Short charge has a twenty-second cooldown and three-heart raw damage cap");
        UUID a = new UUID(0, 1), b = new UUID(0, 2);
        var first = new PvpHarpoon.HitWindow(100);
        var second = new PvpHarpoon.HitWindow(100);
        check(first.accept(a, 100) && !first.accept(a, 100) && !first.accept(a, 111), "Spin/manual hit overlap cannot damage one target twice");
        check(second.accept(a, 100), "Identical-class deciders keep independent charge hit windows");
        check(first.active(111) && !first.active(112) && !first.accept(b, 112), "Expired charge cannot land delayed hits");
        check(new PvpHarpoon.HitWindow(120).accept(a, 120), "New activation resets its one-hit guard");
        for (Vector direction : List.of(new Vector(0, 0, 1), new Vector(1, 0, 1), new Vector(0, 1, 0), new Vector(0, -1, 0), new Vector())) {
            Vector original = direction.clone(), motion = PvpHarpoon.motion(direction);
            check(Double.isFinite(motion.lengthSquared()) && motion.clone().setY(0).length() <= 1.450001,
                    "All aim angles have bounded horizontal speed and no NaNs");
            check(motion.getY() >= -.15 && motion.getY() <= .35, "Vertical aim cannot turn the charge into a high-flight ability");
            check(direction.equals(original), "Charge math preserves input vectors");
        }
        check(PvpHarpoon.motion(new Vector(0, 0, 1)).getY() > 0, "A dry flat-ground charge lifts clear of ground friction");
        Vector push = PvpHarpoon.knockback(new Vector(20, 40, 20), 0);
        check(push.clone().setY(0).length() <= .650001 && push.getY() == .25, "Collision knockback is bounded, not a launch across the arena");
        check(PvpHarpoon.knockback(new Vector(0, 0, 1), .5).length() < push.length(), "Knockback resistance still counters the ability");
        check(PvpHarpoon.knockback(new Vector(), 1).lengthSquared() == 0, "Full knockback resistance is respected");
    }
    private static void thunderChecks() {
        PvpClass kit = PvpClass.THUNDERER;
        check(kit.primaryWeapon() == Material.IRON_AXE && kit.cooldown == 6, "Thunderer can repeatedly use its ranged spell while retaining the slow axe");
        check(kit.armor().chestplate() == Material.CHAINMAIL_CHESTPLATE
                && kit.armor().leggings() == Material.CHAINMAIL_LEGGINGS, "Thunderer's ranged pressure trades heavy armour for chain armour");
        check(kit.potions().stream().filter(slot -> slot.kind() == PvpPotion.HEALING).mapToInt(PvpClass.PotionSlot::amount).sum() == 1,
                "Thunderer receives only one finite healing potion");
        check(PvpThunder.DAMAGE == 8 && PvpThunder.RANGE == 48 && PvpThunder.DELAY == 20 && PvpThunder.RADIUS == 3,
                "Lightning crosses the arena, has a one-second warning and bounded area damage");
        Vector center = new Vector(10, 81, 10);
        var strike = new PvpThunder.Strike(center, 100);
        check(!strike.take(100) && !strike.take(119) && strike.take(120), "Lightning cannot fire before its twenty-tick warning");
        check(!strike.take(120) && !strike.take(200), "One lightning cast cannot fire multiple times");
        var cancelled = new PvpThunder.Strike(center, 100);
        cancelled.cancel(); cancelled.cancel();
        check(!cancelled.take(120) && !cancelled.take(1000), "Death, quit, result and shutdown cancellation prevents all delayed damage");
        var other = new PvpThunder.Strike(center, 100);
        check(other.take(120), "Two Thunderers have independent delayed spells");
        center.setX(1000);
        check(strike.contains(new Vector(10, 81, 10)), "Marker stays fixed instead of following a moving aim vector");
        check(strike.contains(new Vector(13, 81, 10)) && !strike.contains(new Vector(13.001, 81, 10)), "Stepping outside the telegraph dodges the spell");
        check(!strike.contains(new Vector(12.5, 81, 12.5)), "Strike uses a circular radius rather than a larger square");
        check(strike.contains(new Vector(10, 83, 10)) && !strike.contains(new Vector(10, 84, 10)), "Mounted riders can be hit; high airborne targets evade it");
        check(!strike.contains(new Vector(10, 80, 10)), "A marker on a roof cannot strike players below it");
        check(strike.damage(new Vector(10, 81, 10)) == 8 && strike.damage(new Vector(11, 81, 10)) == 8,
                "The central one-block core gets full damage");
        check(strike.damage(new Vector(12, 81, 10)) == 5.5 && strike.damage(new Vector(13, 81, 10)) == 3,
                "Splash damage falls continuously towards the edge instead of hitting only the exact centre");
        check(strike.damage(new Vector(13.01, 81, 10)) == 0 && strike.damage(new Vector(10, 80, 10)) == 0,
                "Outside/under-surface targets take no splash damage");
        double previous = 8;
        for (int i = 0; i <= 30; i++) {
            double damage = strike.damage(new Vector(10 + i / 10.0, 81, 10));
            check(damage >= 3 && damage <= previous, "Area damage never increases with distance or exceeds its bounds");
            previous = damage;
        }
        thunderCoverChecks();
        thunderPreviewChecks();
        PvpCombat combat = new PvpCombat();
        UUID a = new UUID(0, 1), b = new UUID(0, 2);
        check(!combat.staticStrike(a, 0, false) && combat.staticStrike(a, 0, true), "Blocked axe hits do not apply static or spend its cooldown");
        check(!combat.staticStrike(a, 7, true) && combat.staticStrike(a, 8, true), "Static cannot continuously refresh its three-second outline");
        check(combat.staticStrike(b, 0, true), "Static cooldown belongs to the individual player");
        combat.forget(a);
        check(combat.staticStrike(a, 8, true), "Leaving clears the personal static cooldown");
        combat.reset();
        check(combat.staticStrike(a, 0, true) && combat.staticStrike(b, 0, true), "Every round restores both static passives");
    }
    private static void thunderCoverChecks() {
        boolean[] blocked = {false};
        BlockFace[] face = {BlockFace.UP};
        Block[] hitBlock = new Block[1];
        double[] rayRange = new double[1];
        World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class}, (proxy, method, args) -> {
            if (method.getName().equals("equals")) return proxy == args[0];
            if (!method.getName().equals("rayTraceBlocks")) throw new AssertionError("Unexpected world call: " + method.getName());
            check((double) args[2] > 0 && Double.isFinite(((Vector) args[1]).lengthSquared()), "Cover rays have a finite direction and positive range");
            rayRange[0] = (double) args[2];
            return blocked[0] ? new RayTraceResult(new Vector(30, 81, 0), hitBlock[0], face[0]) : null;
        });
        hitBlock[0] = new ThunderBlock(world, 30, previewData()).block;
        Location point = new Location(world, 0, 81.05, 0);
        LivingEntity target = (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(), new Class<?>[] {LivingEntity.class}, (proxy, method, args) -> switch (method.getName()) {
            case "getLocation" -> new Location(world, 1, 81, 0);
            case "getHeight" -> 1.8;
            default -> throw new AssertionError("Unexpected target call: " + method.getName());
        });
        check(PvpThunder.exposed(point, target), "An unobstructed target can take the strike");
        blocked[0] = true;
        check(!PvpThunder.exposed(point, target), "A solid wall between the marker and target prevents lightning damage");
        check(point.equals(new Location(world, 0, 81.05, 0)), "Cover checks preserve the fixed marker location");
        Player caster = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class}, (proxy, method, args) -> switch (method.getName()) {
            case "getEyeLocation" -> new Location(world, 0, 82.6, 0, 0, 45);
            case "getWorld" -> world;
            default -> throw new AssertionError("Unexpected caster call: " + method.getName());
        });
        var aim = PvpThunder.aim(caster);
        check(aim != null && aim.block() == hitBlock[0] && aim.point().equals(new Location(world, 30.5, 81.05, .5)),
                "Preview and strike select the same highlighted block centre, well beyond the old twelve-block limit");
        check(rayRange[0] == 48, "The actual aim ray uses the full forty-eight-block range");
        face[0] = BlockFace.NORTH;
        check(PvpThunder.aim(caster).block() == hitBlock[0], "A side face selects the wall itself without aiming through it");
        blocked[0] = false;
        check(PvpThunder.aim(caster) == null, "Aiming at empty air cannot start a strike");
    }
    private record PreviewPacket(Location location, BlockData data) { }
    private static BlockData previewData() {
        return (BlockData) Proxy.newProxyInstance(BlockData.class.getClassLoader(), new Class<?>[] {BlockData.class},
                (proxy, method, args) -> { throw new AssertionError("Unexpected block data call: " + method.getName()); });
    }
    private static final class ThunderBlock {
        final Block block;
        BlockData live;
        ThunderBlock(World world, int x, BlockData data) {
            live = data;
            block = (Block) Proxy.newProxyInstance(Block.class.getClassLoader(), new Class<?>[] {Block.class}, (proxy, method, args) -> switch (method.getName()) {
                case "getWorld" -> world;
                case "getLocation" -> new Location(world, x, 80, 0);
                case "getBlockData" -> live;
                case "equals" -> proxy == args[0];
                default -> throw new AssertionError("Preview must not mutate world blocks: " + method.getName());
            });
        }
    }
    private static void thunderPreviewChecks() {
        World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("equals")) return proxy == args[0];
                    throw new AssertionError("Private previews must not broadcast or edit the world: " + method.getName());
                });
        World otherWorld = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class},
                (proxy, method, args) -> { if (method.getName().equals("equals")) return proxy == args[0]; throw new AssertionError("No preview call belongs in the new world"); });
        List<PreviewPacket> packets = new ArrayList<>();
        UUID id = new UUID(0, 10);
        boolean[] online = {true}, sameWorld = {true};
        Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class}, (proxy, method, args) -> switch (method.getName()) {
            case "getUniqueId" -> id;
            case "isOnline" -> online[0];
            case "getWorld" -> sameWorld[0] ? world : otherWorld;
            case "sendBlockChange" -> { packets.add(new PreviewPacket(((Location) args[0]).clone(), (BlockData) args[1])); yield null; }
            default -> throw new AssertionError("Unexpected preview player call: " + method.getName());
        });
        BlockData stone = previewData(), wool = previewData(), gold = previewData(), red = previewData();
        ThunderBlock first = new ThunderBlock(world, 1, stone), second = new ThunderBlock(world, 20, stone);
        PvpThunder.Aim a = new PvpThunder.Aim(first.block, new Location(world, 1.5, 81.05, .5));
        PvpThunder.Aim b = new PvpThunder.Aim(second.block, new Location(world, 20.5, 81.05, .5));
        PvpThunder thunder = new PvpThunder();
        thunder.showPreview(player, a, gold);
        check(packets.size() == 1 && packets.getLast().data() == gold, "Only the caster receives the highlighted block");
        first.live = wool;
        thunder.showPreview(player, b, red);
        check(packets.size() == 3 && packets.get(1).data() == wool && packets.getLast().data() == red,
                "Moving aim restores current block data instead of undoing a block changed while aiming");
        thunder.showPreview(player, b, gold);
        check(packets.size() == 4 && packets.getLast().data() == gold, "Cooldown colours update on the same target without stale restores");
        thunder.clearPreview(id); thunder.clearPreview(id);
        check(packets.size() == 5 && packets.getLast().data() == stone, "Putting away the rod restores the targeted block exactly once");
        thunder.showPreview(player, a, gold); thunder.remove(id); thunder.remove(id);
        check(packets.size() == 7 && packets.getLast().data() == wool, "Death/withdrawal removes personal preview state");
        thunder.showPreview(player, a, gold); thunder.clear(); thunder.clear();
        check(packets.size() == 9 && packets.getLast().data() == wool, "Round/session cleanup restores previews even without a pending cast");
        thunder.showPreview(player, a, gold); online[0] = false; thunder.clear();
        check(packets.size() == 10, "Disconnect cleanup does not send packets to an offline player");
        online[0] = true;
        thunder.showPreview(player, a, gold); sameWorld[0] = false; thunder.clear();
        check(packets.size() == 11, "Old-world preview coordinates are never restored into another world");
    }
    private static void demolitionistChecks() {
        PvpClass kit = PvpClass.DEMOLITIONIST;
        check(kit.primaryWeapon() == Material.STONE_SWORD && kit.cooldown == 18, "Bomber has a modest melee backup and eighteen-second ammo refill");
        check(kit.armor().chestplate() == Material.LEATHER_CHESTPLATE, "Bomber trades melee armour for blast protection");
        check(kit.supplies().stream().filter(slot -> slot.material() == Material.TNT).mapToInt(PvpClass.SupplySlot::amount).sum() == 12,
                "Bomber starts with twelve finite TNT charges");
        check(kit.potions().stream().filter(slot -> slot.kind() == PvpPotion.HEALING).mapToInt(PvpClass.PotionSlot::amount).sum() == 1,
                "Bomber only receives one healing potion");
        check(PvpBombs.FUSE == 50 && PvpBombs.THROW_COOLDOWN == 40 && PvpBombs.PLACE_COOLDOWN == 6 && PvpBombs.MAX_ACTIVE == 4,
                "Fuse, throw/place pace and simultaneous bombs are bounded independently");
        for (int stock = 0; stock <= 20; stock++) {
            int added = PvpPotion.refillAmount(stock, PvpBombs.REFILL, PvpBombs.STOCK);
            check(added >= 0 && added <= 4 && (stock >= 12 ? added == 0 : stock + added <= 12), "Ammo refills never exceed twelve or add more than four");
        }
        PvpBombs.Cooldowns cooldown = new PvpBombs.Cooldowns();
        UUID a = new UUID(0, 1), b = new UUID(0, 2);
        check(cooldown.remaining(a, 100, true) == 0 && cooldown.remaining(a, 100, false) == 0, "Bombs start ready");
        cooldown.used(a, 100, true);
        check(cooldown.remaining(a, 105, false) == 1 && cooldown.remaining(a, 106, false) == 0,
                "Placing is available six ticks after a throw, without waiting two seconds");
        cooldown.used(a, 106, false);
        check(cooldown.remaining(a, 111, false) == 1 && cooldown.remaining(a, 112, false) == 0, "Rapid placing obeys its own six-tick limit");
        check(cooldown.remaining(a, 139, true) == 1 && cooldown.remaining(a, 140, true) == 0,
                "Alternating placement and throws cannot bypass or extend the forty-tick throw cooldown");
        check(cooldown.remaining(b, 101, true) == 0, "Two bombers have independent throw cooldowns");
        cooldown.used(a, 140, true); cooldown.forget(a);
        check(cooldown.remaining(a, 141, true) == 0, "Leaving removes personal bomb cooldowns");
        cooldown.used(a, 200, true); cooldown.used(b, 200, false); cooldown.clear();
        check(cooldown.remaining(a, 0, true) == 0 && cooldown.remaining(b, 0, false) == 0, "New rounds clear both bomb cooldowns");
        Vector facing = new Vector(1, .2, 1), original = facing.clone();
        check(PvpBombs.throwVelocity(facing).getZ() > 0 && facing.equals(original), "Throw follows aim without mutating the player's facing vector");
        for (Vector aim : List.of(facing, new Vector(), new Vector(0, 1, 0), new Vector(0, -1, 0)))
            check(Double.isFinite(PvpBombs.throwVelocity(aim).length()) && PvpBombs.throwVelocity(aim).length() <= 1.170001, "Vertical/zero aim never creates runaway TNT velocity");
        for (double damage : new double[] {-1, 0, 1, 6, 10, 40, 1000})
            check(PvpBombs.blastDamage(damage) == Math.clamp(damage, 0, 10), "Native explosion damage is capped before armour for both opponents and the bomber");
    }
    private static void randomEligibility() {
        PvpClass[] classes = PvpClass.values();
        int n = classes.length;
        check(n == 15, "Demolitionist joins the existing fourteen classes");
        int[][] pairs = new int[n][n];
        for (int first = 0; first < n; first++) for (int second = 0; second < n - 1; second++) {
            int a = first, b = second;
            var drawn = PvpClass.opening(new Random(0) {
                int calls;
                @Override public int nextInt(int bound) {
                    if (calls++ == 0) { check(bound == n, "First draw covers all classes"); return a; }
                    check(bound == n - 1, "Second draw excludes exactly one class"); return b;
                }
            });
            pairs[drawn.getFirst().ordinal()][drawn.getLast().ordinal()]++;
        }
        for (int a = 0; a < n; a++) for (int b = 0; b < n; b++) check(pairs[a][b] == (a == b ? 0 : 1), "Every distinct matchup has equal probability");
    }
    private static void combatRules() {
        PvpCombat combat = new PvpCombat();
        UUID a = new UUID(0, 1), b = new UUID(0, 2);
        Vector forward = new Vector(0, 0, 1), behind = new Vector(0, 4, -2);
        check(!PvpCombat.isBehind(forward, forward) && !PvpCombat.isBehind(forward, new Vector(1, 0, 0)), "Front and side hits are not backstabs");
        check(PvpCombat.isBehind(forward, behind), "Behind calculation ignores height differences");
        check(!PvpCombat.isBehind(forward, new Vector()), "Overlapping players cannot fabricate a backstab");
        check(!combat.backstab(a, 0, forward, forward), "Invalid angle does not spend passive cooldown");
        check(combat.backstab(a, 0, forward, behind) && !combat.backstab(a, 4, forward, behind), "Backstab has a five-second cooldown");
        check(combat.backstab(a, 5, forward, behind) && combat.backstab(b, 5, forward, behind), "Backstab is per player");
        check(combat.leech(a, 0) && !combat.leech(a, 2) && combat.leech(a, 3), "Lifesteal has a three-second cooldown");
        check(combat.frost(a, 0) && !combat.frost(a, 1) && combat.frost(a, 2), "Frost projectiles cannot continuously refresh slow effects");
        check(!combat.shadowStrike(a, 0, false) && combat.shadowRemaining(a, 0) == 0, "Blocked/invalid wand hits cannot activate the perk or spend its cooldown");
        check(combat.shadowStrike(a, 0, true) && !combat.shadowStrike(a, 1, true) && !combat.shadowStrike(a, 19, true), "Wand hit spam cannot chain blindness or refresh slowness");
        check(combat.shadowRemaining(a, 19) == 1 && combat.shadowStrike(a, 20, true), "Wand becomes ready exactly after twenty seconds");
        check(combat.shadowStrike(b, 1, true), "Two Shadows have independent wand cooldowns");
        check(Math.max(PvpCombat.SHADOW_BLINDNESS_TICKS, PvpCombat.SHADOW_SLOWNESS_TICKS) < PvpCombat.SHADOW_WAND_COOLDOWN * 20,
                "Control always ends before the next possible proc");
        combat.forget(a);
        check(combat.shadowStrike(a, 20, true), "Leaving clears personal wand cooldown");
        check(combat.leech(a, 3) && combat.backstab(a, 5, forward, behind) && combat.frost(a, 2), "Leaving clears personal passive state");
        combat.reset();
        check(combat.shadowStrike(a, 0, true), "Next round resets wand cooldown");
        check(combat.leech(a, 0) && combat.backstab(a, 0, forward, behind) && combat.frost(a, 0), "Each new round resets passives");
        check(!PvpCombat.canPayFury(.5) && !PvpCombat.canPayFury(2) && PvpCombat.canPayFury(2.01), "Fury cannot kill its caster or grant health at low HP");
        Vector retreat = PvpCombat.dash(forward, true), advance = PvpCombat.dash(forward, false);
        check(retreat.getZ() < 0 && advance.getZ() > 0 && advance.getY() == .2 && retreat.getY() == .2, "Ranger retreats while duelist advances");
        check(Double.isFinite(PvpCombat.dash(new Vector(0, 1, 0), false).getZ()), "Looking vertically never creates NaN dash velocity");
        Vector momentum = new Vector(4, -3, 4);
        Vector launched = PvpCombat.launch(momentum);
        check(launched.getY() == .9 && launched.clone().setY(0).length() <= .600001, "Wind leap gains height without runaway horizontal velocity");
        check(momentum.equals(new Vector(4, -3, 4)), "Wind leap does not mutate input velocity");
        check(forward.equals(new Vector(0, 0, 1)) && behind.equals(new Vector(0, 4, -2)), "Combat math does not mutate input locations");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
