package me.petr1furious.potatominigames.games.party;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Tracked native TNT: short fuses, separate throw cooldown and bounded simultaneous charges. */
final class PvpBombs {
    static final int STOCK = 12, REFILL = 4, FUSE = 50, THROW_COOLDOWN = 40, PLACE_COOLDOWN = 6, MAX_ACTIVE = 4;
    static final float POWER = 2;
    static final double MAX_DAMAGE = 10;
    private static final NamespacedKey KEY = new NamespacedKey("potatominigames", "pvp_bomb");
    static final class Cooldowns {
        private final Map<UUID, Integer> handReady = new HashMap<>(), throwReady = new HashMap<>();
        int remaining(UUID owner, int tick, boolean thrown) {
            return Math.max(0, Math.max(handReady.getOrDefault(owner, 0), thrown ? throwReady.getOrDefault(owner, 0) : 0) - tick);
        }
        void used(UUID owner, int tick, boolean thrown) {
            handReady.put(owner, tick + PLACE_COOLDOWN);
            if (thrown) throwReady.put(owner, tick + THROW_COOLDOWN);
        }
        void forget(UUID owner) { handReady.remove(owner); throwReady.remove(owner); }
        void clear() { handReady.clear(); throwReady.clear(); }
    }
    private record Bomb(Player owner, TNTPrimed entity) { }
    private final Map<UUID, Bomb> bombs = new HashMap<>();
    private final Cooldowns cooldowns = new Cooldowns();
    private BukkitTask cleanup;

    static ItemStack item(int amount) {
        ItemStack item = new ItemStack(Material.TNT, amount);
        item.editMeta(meta -> {
            meta.displayName(Component.text("Заряд TNT", NamedTextColor.GOLD));
            meta.lore(List.of(Component.text("ПКМ по блоку — установить • 0,3 с", NamedTextColor.GRAY),
                    Component.text("ЛКМ — бросить по взгляду • 2 с", NamedTextColor.GRAY),
                    Component.text("Фитиль 2,5 с • до 4 активных • взрыв ранит и тебя", NamedTextColor.RED)));
            meta.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
        });
        return item;
    }
    static boolean isBomb(ItemStack item) {
        return item != null && item.getType() == Material.TNT && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(KEY, PersistentDataType.BYTE);
    }
    static int replenish(Player player) {
        var inventory = player.getInventory();
        int owned = 0;
        for (ItemStack stack : inventory.getContents()) if (isBomb(stack)) owned += stack.getAmount();
        int added = PvpPotion.refillAmount(owned, REFILL, STOCK);
        if (added == 0) return 0;
        return added - inventory.addItem(item(added)).values().stream().mapToInt(ItemStack::getAmount).sum();
    }
    /** Called only after placement/throw geometry has passed session validation. */
    boolean use(Player player, Location at, boolean thrown, Plugin plugin) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!isBomb(held)) return false;
        UUID id = player.getUniqueId();
        int tick = Bukkit.getCurrentTick();
        int remaining = cooldowns.remaining(id, tick, thrown);
        if (remaining > 0) {
            player.sendActionBar(Component.text((thrown ? "Бросок" : "Установка") + ": ещё "
                    + String.format(java.util.Locale.ROOT, "%.1f", remaining / 20.0) + " с", NamedTextColor.YELLOW));
            return false;
        }
        prune();
        if (active(id) >= MAX_ACTIVE) {
            player.sendActionBar(Component.text("Уже 4 активных TNT — дождись взрыва", NamedTextColor.YELLOW));
            return false;
        }
        Vector velocity = thrown ? throwVelocity(player.getEyeLocation().getDirection()) : new Vector();
        TNTPrimed tnt = at.getWorld().spawn(at, TNTPrimed.class, charge -> configure(charge, player, velocity));
        if (!tnt.isValid()) return false; // A plugin may cancel the spawn; no ammo/cooldown is spent.
        track(player, tnt);
        cooldowns.used(id, tick, thrown);
        int amount = held.getAmount() - 1;
        held.setAmount(amount);
        player.getInventory().setItemInMainHand(amount > 0 ? held : null);
        at.getWorld().playSound(at, Sound.ENTITY_TNT_PRIMED, .8f, 1);
        if (cleanup == null) cleanup = Bukkit.getScheduler().runTaskTimer(plugin, this::prune, 1, 1);
        return true;
    }
    static void configure(TNTPrimed charge, Player owner, Vector velocity) {
        charge.setSource(owner);
        charge.setFuseTicks(FUSE);
        charge.setYield(POWER);
        charge.setIsIncendiary(false);
        charge.setPersistent(false);
        charge.setVelocity(velocity);
    }
    void track(Player owner, TNTPrimed entity) { bombs.put(entity.getUniqueId(), new Bomb(owner, entity)); }
    Player owner(Entity entity) {
        if (!(entity instanceof TNTPrimed)) return null;
        Bomb bomb = bombs.get(entity.getUniqueId());
        return bomb == null ? null : bomb.owner();
    }
    int active(UUID owner) {
        return (int) bombs.values().stream().filter(bomb -> bomb.owner().getUniqueId().equals(owner) && bomb.entity().isValid()).count();
    }
    void prune() {
        // Do not prune from damage/explode callbacks: native TNT is discarded before applying its blast.
        bombs.values().removeIf(bomb -> !bomb.entity().isValid());
        if (bombs.isEmpty() && cleanup != null) { cleanup.cancel(); cleanup = null; }
    }
    void remove(UUID owner) { remove(owner, false); }
    void eliminate(UUID owner) { remove(owner, true); }
    private void remove(UUID owner, boolean keepExploding) {
        for (var entry : List.copyOf(bombs.entrySet())) {
            Bomb bomb = entry.getValue();
            if (!bomb.owner().getUniqueId().equals(owner) || keepExploding && !bomb.entity().isValid()) continue;
            if (bomb.entity().isValid()) bomb.entity().remove();
            bombs.remove(entry.getKey());
        }
        cooldowns.forget(owner);
        if (bombs.isEmpty() && cleanup != null) { cleanup.cancel(); cleanup = null; }
    }
    void clear() {
        for (Bomb bomb : bombs.values()) if (bomb.entity().isValid()) bomb.entity().remove();
        bombs.clear(); cooldowns.clear();
        if (cleanup != null) { cleanup.cancel(); cleanup = null; }
    }
    static double blastDamage(double raw) { return Math.clamp(raw, 0, MAX_DAMAGE); }
    static Vector throwVelocity(Vector facing) {
        Vector direction = facing.clone();
        if (direction.lengthSquared() < 1.0e-6) direction = new Vector(0, 0, 1);
        return direction.normalize().multiply(1.05).add(new Vector(0, .12, 0));
    }
}
