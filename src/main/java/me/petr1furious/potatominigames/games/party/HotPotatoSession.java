package me.petr1furious.potatominigames.games.party;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;

import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class HotPotatoSession extends PartySurvivalSession {
    private static final String KEY = "party.tag_and_timing.hot_potato.";
    private static final int FLOOR = 80;
    private static final int ARENA_EDGE = 15;
    private static final int PRACTICE_PHASES = 3;
    private static final double PASS_RANGE_SQUARED = 3.5 * 3.5;

    private final HotPotatoFuse fuse = new HotPotatoFuse(random);
    private ArmorStand dummy;
    private Player markedPlayer;
    private boolean previouslyGlowing;
    private int potatoSlot;
    private int phase;
    private long nextPhaseAt;

    HotPotatoSession(SessionServices services, PartyConfig config, PartyRoundContext context) {
        super(services, config, "hot_potato", context);
    }

    @Override
    void buildArena() {
        for (int x = -ARENA_EDGE; x <= ARENA_EDGE; x++) {
            for (int z = -ARENA_EDGE; z <= ARENA_EDGE; z++) {
                int edge = Math.max(Math.abs(x), Math.abs(z));
                Material tile =
                        edge == ARENA_EDGE || edge == 2
                                ? Material.COPPER_BLOCK
                                : Math.floorMod(x + z, 2) == 0
                                        ? Material.POLISHED_DEEPSLATE
                                        : Material.DEEPSLATE_TILES;
                world.getBlockAt(x, FLOOR, z).setType(tile, false);
                if (edge == ARENA_EDGE) {
                    world.getBlockAt(x, FLOOR + 1, z).setType(Material.WAXED_COPPER_GRATE, false);
                    for (int y = FLOOR + 2; y <= FLOOR + 3; y++)
                        world.getBlockAt(x, y, z).setType(Material.ORANGE_STAINED_GLASS, false);
                }
            }
        }
        for (int offset : new int[] {3, 10}) {
            for (int x : new int[] {-offset, offset}) {
                for (int z : new int[] {-offset, offset}) {
                    world.getBlockAt(x, FLOOR + 1, z).setType(Material.WAXED_CUT_COPPER, false);
                    world.getBlockAt(x, FLOOR + 2, z).setType(Material.SHROOMLIGHT, false);
                }
            }
        }
        if (names.size() == 1) {
            dummy =
                    world.spawn(
                            new Location(world, .5, FLOOR + 1, .5),
                            ArmorStand.class,
                            stand -> {
                                stand.setGravity(false);
                                stand.setInvulnerable(true);
                                stand.setPersistent(false);
                                stand.setArms(true);
                                stand.setBasePlate(false);
                                stand.customName(I18n.tr(KEY + "dummy").color(NamedTextColor.GOLD));
                                stand.setCustomNameVisible(true);
                                stand.getEquipment()
                                        .setHelmet(new ItemStack(Material.CARVED_PUMPKIN));
                            });
        }
    }

    @Override
    Location spawn(int index, int count) {
        if (count == 1) return new Location(world, .5, FLOOR + 1, 5.5, 180, 0);
        double angle = 2 * Math.PI * index / count;
        double radius = count <= 3 ? 5.5 : 8.5;
        Location at =
                new Location(
                        world,
                        .5 + radius * Math.sin(angle),
                        FLOOR + 1,
                        .5 + radius * Math.cos(angle));
        return at.setDirection(new Location(world, .5, FLOOR + 1, .5).subtract(at).toVector());
    }

    @Override
    Location viewpoint() {
        return new Location(world, .5, 104, .5, 0, 80);
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
        return 150;
    }

    @Override
    boolean podiumAwards() {
        return true;
    }

    @Override
    Component instructions() {
        return I18n.tr(KEY + (dummy == null ? "rules" : "practice_rules"));
    }

    @Override
    void beginPlay() {
        if (dummy != null || standings.survivors().size() > 1) startPhase(0, arenaPlayers());
    }

    private void startPhase(long elapsed, List<Player> alive) {
        if (alive.isEmpty()) return;
        phase++;
        nextPhaseAt = 0;
        // A practice pass always begins with the player so they can try the control each time.
        fuse.start(
                alive.stream().map(Player::getUniqueId).toList(),
                elapsed,
                20 * Math.max(8, 14 - phase));
        markCarrier(carrier());
        PartySounds.play(alive, Sound.BLOCK_NOTE_BLOCK_BELL, .65f, 1.25f);
        showRoles(alive, elapsed);
    }

    @Override
    void playTick(long elapsed) {
        if (dummy == null && standings.survivors().size() <= 1) {
            if (standings.decided(false, gameTick())) finish(standings.survivors());
            return;
        }
        List<Player> alive = arenaPlayers();
        if (phase == 0) {
            startPhase(elapsed, alive);
            return;
        }
        if (nextPhaseAt != 0) {
            if (elapsed >= nextPhaseAt) startPhase(elapsed, alive);
            return;
        }

        List<UUID> candidates = new ArrayList<>();
        for (Player player : alive) candidates.add(player.getUniqueId());
        if (dummy != null && dummy.isValid()) candidates.add(dummy.getUniqueId());
        if (fuse.replaceMissing(candidates, elapsed)) {
            markCarrier(carrier());
            showRoles(alive, elapsed);
        }
        Entity current = carrier();
        if (current == null) return;
        long remaining = fuse.remaining(elapsed);
        if (remaining == 0) {
            pop(current, elapsed);
            return;
        }
        if (elapsed % 5 == 0) {
            world.spawnParticle(
                    Particle.FLAME, current.getLocation().add(0, 2, 0), 4, .22, .12, .22, .01);
            showRoles(alive, elapsed);
        }
        PartySounds.countdown(alive, remaining);
    }

    private List<Player> arenaPlayers() {
        return alivePlayers().stream().filter(player -> player.getWorld().equals(world)).toList();
    }

    private void pop(Entity current, long elapsed) {
        Location at = current.getLocation().add(0, 1, 0);
        world.spawnParticle(Particle.EXPLOSION, at, 1);
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, .7f, 1.2f);
        clearMark();
        if (dummy == null) {
            if (current instanceof Player player) eliminate(player);
        } else {
            say(I18n.tr(KEY + (current == dummy ? "practice_success" : "practice_missed")));
            if (phase >= PRACTICE_PHASES) {
                finish(standings.survivors());
                return;
            }
        }
        nextPhaseAt = elapsed + 25;
        for (Player player : alivePlayers())
            player.sendActionBar(I18n.tr(KEY + "next").color(NamedTextColor.YELLOW));
    }

    @Override
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!player.getWorld().equals(world) || !alive(player)) return;
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND) tryPass(player, event.getRightClicked());
    }

    @Override
    public void onPrePlayerAttackEntity(PrePlayerAttackEntityEvent event) {
        Player player = event.getPlayer();
        if (!player.getWorld().equals(world) || !alive(player)) return;
        event.setCancelled(true);
        tryPass(player, event.getAttacked());
    }

    private void tryPass(Player player, Entity target) {
        if (!playing() || nextPhaseAt != 0 || !player.getUniqueId().equals(fuse.carrier())) return;
        if (target != dummy && (!(target instanceof Player runner) || !alive(runner))) return;
        if (!target.getWorld().equals(world)
                || player.getLocation().distanceSquared(target.getLocation()) > PASS_RANGE_SQUARED
                || !player.hasLineOfSight(target)) return;
        if (fuse.pass(player.getUniqueId(), target.getUniqueId(), elapsed())) {
            markCarrier(target);
            showRoles(alivePlayers(), elapsed());
            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, .7f, 1.5f);
        } else if (fuse.remaining(elapsed()) > 0) {
            player.sendActionBar(I18n.tr(KEY + "protected").color(NamedTextColor.YELLOW));
        }
    }

    private Entity carrier() {
        UUID id = fuse.carrier();
        if (id == null) return null;
        return dummy != null && id.equals(dummy.getUniqueId()) ? dummy : Bukkit.getPlayer(id);
    }

    private void markCarrier(Entity carrier) {
        clearMark();
        ItemStack potato = new ItemStack(Material.BAKED_POTATO);
        potato.editMeta(meta -> meta.displayName(I18n.tr(KEY + "item").color(NamedTextColor.GOLD)));
        if (carrier instanceof Player player) {
            markedPlayer = player;
            previouslyGlowing = player.isGlowing();
            player.setGlowing(true);
            player.addPotionEffect(
                    new PotionEffect(
                            PotionEffectType.SPEED,
                            PotionEffect.INFINITE_DURATION,
                            0,
                            false,
                            false,
                            true));
            potatoSlot = player.getInventory().getHeldItemSlot();
            player.getInventory().setItem(potatoSlot, I18n.item(potato, player));
            player.playSound(player, Sound.ENTITY_BLAZE_HURT, .55f, 1.35f);
        } else if (carrier != null && carrier == dummy) {
            dummy.setGlowing(true);
            dummy.getEquipment().setItemInMainHand(potato);
        }
    }

    private void clearMark() {
        if (markedPlayer != null) {
            markedPlayer.setGlowing(previouslyGlowing);
            markedPlayer.removePotionEffect(PotionEffectType.SPEED);
            markedPlayer.getInventory().setItem(potatoSlot, null);
            markedPlayer = null;
        }
        if (dummy != null) {
            dummy.setGlowing(false);
            dummy.getEquipment().setItemInMainHand(null);
        }
    }

    @Override
    void playerOut(Player player) {
        if (markedPlayer != null && markedPlayer.getUniqueId().equals(player.getUniqueId()))
            clearMark();
    }

    @Override
    void cleanup() {
        clearMark();
        if (dummy != null) dummy.remove();
    }

    private long secondsLeft(long elapsed) {
        return (fuse.remaining(elapsed) + 19) / 20;
    }

    private Component carrierName() {
        UUID current = fuse.carrier();
        return dummy != null && dummy.getUniqueId().equals(current)
                ? I18n.tr(KEY + "dummy")
                : Component.text(names.getOrDefault(current, ""));
    }

    private void showRoles(List<Player> players, long elapsed) {
        if (fuse.carrier() == null) return;
        long seconds = secondsLeft(elapsed);
        Component runner =
                (dummy == null
                                ? I18n.tr(KEY + "runner", carrierName(), seconds)
                                : I18n.tr(KEY + "practice_wait", seconds))
                        .color(NamedTextColor.YELLOW);
        Component holder =
                I18n.tr(KEY + (dummy == null ? "carrier" : "practice_carrier"), seconds)
                        .color(seconds <= 3 ? NamedTextColor.RED : NamedTextColor.GOLD);
        for (Player player : players)
            player.sendActionBar(player.getUniqueId().equals(fuse.carrier()) ? holder : runner);
    }

    @Override
    Component status() {
        if (nextPhaseAt != 0 || fuse.carrier() == null) return I18n.tr(KEY + "next");
        return I18n.tr(KEY + "status", carrierName(), secondsLeft(elapsed()));
    }
}
