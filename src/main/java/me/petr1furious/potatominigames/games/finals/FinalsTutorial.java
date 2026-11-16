package me.petr1furious.potatominigames.games.finals;

import java.time.Duration;
import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import me.petr1furious.potatominigames.i18n.I18n;
import static me.petr1furious.potatominigames.games.finals.FinalsTutorialProgress.*;

/** Guides the real Finals mechanics; it never substitutes clicks on a lesson menu for gameplay. */
final class FinalsTutorial {
    enum Directive { NONE, ENEMY_CASHOUT, CLEAR_OBJECTIVE, FINISH }
    private final FinalsTutorialProgress progress = new FinalsTutorialProgress();
    private final FinalsLoadouts loadouts;
    private FinalsLoadouts.Choice baseline;
    private Location walkStart;
    private long enteredAt;

    FinalsTutorial(FinalsLoadouts loadouts) { this.loadouts = loadouts; }
    Step step() { return progress.step(); }
    boolean equipmentLesson() { return step().ordinal() >= Step.LIGHT.ordinal(); }
    float fraction() { return (float) step().ordinal() / Step.DONE.ordinal(); }
    Component title() { return tr("step." + step().name().toLowerCase(Locale.ROOT)); }

    private static Component tr(String key, Object... args) { return I18n.tr("finals.tutorial." + key, args); }

    void start(Player player, long now) {
        player.sendMessage(tr("welcome"));
        enter(player, now);
    }

    void help(Player player) {
        player.sendMessage(title().color(NamedTextColor.GOLD));
        player.sendMessage(tr("help." + step().name().toLowerCase(Locale.ROOT)));
        player.sendMessage(tr("commands").color(NamedTextColor.GRAY));
    }

    private void enter(Player player, long now) {
        enteredAt = now;
        walkStart = null;
        baseline = loadouts.choice(player.getUniqueId());
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, .5f, 1.4f);
        player.showTitle(Title.title(tr("name"), title(),
                Title.Times.times(Duration.ofMillis(150), Duration.ofSeconds(3), Duration.ofMillis(300))));
        help(player);
    }

    Directive tick(Player player, FinalsState state, long now) {
        Step before = step();
        Action action = switch (before) {
            case OPEN -> state.phase() != FinalsState.Phase.VAULT_CLOSED
                    && state.phase() != FinalsState.Phase.VAULT_OPENING ? Action.VAULT_OPENED : null;
            case CARRY -> player.getUniqueId().equals(state.carrier()) ? Action.BOX_PICKED_UP : null;
            case DEPOSIT -> state.phase() == FinalsState.Phase.CASHOUT ? Action.BOX_DEPOSITED : null;
            case PAYOUT -> state.score(0) > 0 ? Action.CASHOUT_PAID : null;
            case STEAL -> state.phase() == FinalsState.Phase.CASHOUT && state.ownerTeam() == 0
                    ? Action.CASHOUT_STOLEN : null;
            case LIGHT, MEDIUM, HEAVY -> triedClass(player, FinalsClass.valueOf(before.name()))
                    ? Action.values()[before.ordinal()] : null;
            case WEAPON, GADGET, SPECIALIZATION -> changedEquipment(player, before)
                    ? Action.values()[before.ordinal()] : null;
            case DONE -> null;
        };
        if (action != null && progress.accept(action)) enter(player, now);
        if (before == Step.PAYOUT && step() == Step.STEAL) return Directive.ENEMY_CASHOUT;
        if (before == Step.STEAL && step() == Step.LIGHT) return Directive.CLEAR_OBJECTIVE;
        // An unattended enemy payout resets the exercise instead of making it impossible.
        if (step() == Step.STEAL && state.phase() != FinalsState.Phase.CASHOUT) return Directive.ENEMY_CASHOUT;
        if (step() == Step.DONE && now - enteredAt >= 160) return Directive.FINISH;
        if (now % 400 == 0) help(player);
        return Directive.NONE;
    }

    private boolean triedClass(Player player, FinalsClass expected) {
        if (player.isDead() || player.getGameMode() != org.bukkit.GameMode.SURVIVAL
                || player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getBaseValue() != expected.health()
                || loadouts.choice(player.getUniqueId()).playerClass() != expected || FinalsLoadouts.viewing(player)) {
            walkStart = null;
            return false;
        }
        if (walkStart == null) walkStart = player.getLocation().clone();
        return walkStart.getWorld() == player.getWorld() && walkStart.distanceSquared(player.getLocation()) >= 9;
    }

    private boolean changedEquipment(Player player, Step step) {
        FinalsLoadouts.Choice current = loadouts.choice(player.getUniqueId());
        if (current.playerClass() != baseline.playerClass()) {
            // Class defaults do not count as explicitly selecting a weapon/gadget/specialization.
            baseline = current;
            return false;
        }
        if (FinalsLoadouts.viewing(player)) return false;
        return switch (step) {
            case WEAPON -> current.weapon() != baseline.weapon();
            case GADGET -> !current.gadgets().equals(baseline.gadgets());
            case SPECIALIZATION -> current.specialization() != baseline.specialization();
            default -> false;
        };
    }
}
