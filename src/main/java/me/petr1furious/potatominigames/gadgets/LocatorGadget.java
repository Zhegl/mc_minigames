package me.petr1furious.potatominigames.gadgets;

import me.petr1furious.potatominigames.core.config.Property;
import me.petr1furious.potatominigames.core.config.PropertyCodec;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class LocatorGadget extends AbstractGadget {
    private static final String TAG = "locator";

    private final Property<Integer> cooldown;
    private final Property<Double> precision;
    private final Property<Integer> steps;
    private final Property<Integer> effectLength;

    public LocatorGadget(GadgetContext context) {
        super("locator", context, true);
        cooldown = config().define("cooldown", PropertyCodec.INT, 30);
        precision = config().define("precision", PropertyCodec.DOUBLE, 5.0);
        steps = config().define("steps", PropertyCodec.INT, 5);
        effectLength = config().define("effectLength", PropertyCodec.INT, 4);
    }

    @Override
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!GadgetItems.isRightClick(event))
            return;
        if (!GadgetItems.checkForItem(event.getItem(), TAG))
            return;
        Player player = event.getPlayer();
        event.setCancelled(true);
        if (player.getGameMode() == GameMode.SPECTATOR)
            return;
        int remaining = getRemainingCooldownSeconds(player);
        if (remaining > 0) {
            player.sendActionBar(Component.text("Locator: " + remaining + "s").color(NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.2f, 0.5f); // cooldown/fail sound
            return;
        }

        Player nearest = findNearest(player);
        if (nearest == null) {
            player.sendActionBar(Component.text("No players").color(NamedTextColor.GRAY));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.2f, 0.7f); // not found
            return;
        }
        setCooldown(player);

        double dy = nearest.getLocation().getY() - player.getLocation().getY();
        String arrows = buildArrows(dy);
        Component msg = (context.hideTargetNames(player)
                ? Component.text("Enemy").color(NamedTextColor.AQUA)
                : Component.text(nearest.getName()).color(NamedTextColor.AQUA))
                .append(Component.text(" "))
                .append(Component.text(arrows).color(NamedTextColor.GOLD));
        player.sendActionBar(msg);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.2f, 2f); // success
        if (effectLength.get() > 0) {
            player.addPotionEffect(
                    new PotionEffect(PotionEffectType.BLINDNESS, effectLength.get() * 20, 0, false, false));
        }
    }

    private int getRemainingCooldownSeconds(Player player) {
        return (int) Math.ceil(player.getCooldown(Material.COMPARATOR) / 20.0);
    }

    private void setCooldown(Player player) {
        player.setCooldown(Material.COMPARATOR, cooldown.get() * 20);
    }

    private Player findNearest(Player source) {
        Player nearest = null;
        double best = Double.MAX_VALUE;
        for (Player p : context.targetsFor(source)) {
            double d = p.getLocation().distanceSquared(source.getLocation());
            if (d < best) {
                best = d;
                nearest = p;
            }
        }
        return nearest;
    }

    private String buildArrows(double dy) {
        double effectivePrecision = Math.max(0.0001, precision.get());
        int arrows = (int) Math.ceil(Math.abs(dy) / effectivePrecision);
        if (arrows == 0)
            return "=";
        arrows = Math.min(arrows, steps.get());
        StringBuilder sb = new StringBuilder();
        char ch = dy > 0 ? '↑' : '↓';
        for (int i = 0; i < arrows; i++)
            sb.append(ch);
        return sb.toString();
    }

    @Override
    public ItemStack createItem(int count) {
        var item = new ItemStack(Material.COMPARATOR);
        GadgetItems.addTag(item, TAG);
        var meta = item.getItemMeta();
        meta.displayName(
                Component.text("Locator").color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        item.setAmount(count);
        return item;
    }
}
