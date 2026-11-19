package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.i18n.I18n;
import net.kyori.adventure.text.Component;

final class PartyRoundFactory {
    static PartyRoundSession create(
            String mode, SessionServices services, PartyConfig config, PartyRoundContext context) {
        return switch (mode) {
            case "parkour_race" -> new ParkourRaceSession(services, config, context);
            case "hole_in_the_wall" -> new HoleWallSession(services, config, context);
            case "pvp", "sumo" -> new DuelSession(services, config, mode.equals("sumo"), context);
            case "one_shot" -> new OneShotSession(services, config, context);
            case "arms_race" -> new ArmsRaceSession(services, config, context);
            case "tnt_run" -> new TntRunSession(services, config, context);
            case "mob_count" -> new MobCountSession(services, config, context);
            case "rhino" -> new RhinoSession(services, config, context);
            case "miners" -> new MinersSession(services, config, context);
            case "color_floor" -> new ColorFloorSession(services, config, context);
            case "anvils" -> new AnvilSession(services, config, context);
            case "build_memory" -> new BuildMemorySession(services, config, context);
            case FurryQuestSession.ID -> new FurryQuestSession(services, config, context);
            case "tool_race" -> new ToolRaceSession(services, config, context);
            case "hot_potato" -> new HotPotatoSession(services, config, context);
            case "jump_rope" -> new JumpRopeSession(services, config, context);
            case "drop_tower" -> new DropTowerSession(services, config, context);
            case "chicken_shooter" -> new ChickenShooterSession(services, config, context);
            case "less_is_better" -> new LessIsBetterSession(services, config, context);
            case "wait_for_it" -> new WaitForItSession(services, config, context);
            default -> throw new IllegalArgumentException("Unknown party round: " + mode);
        };
    }

    static Component title(String mode) {
        return switch (mode) {
            case FurryQuestSession.ID, "gartic_phone", "hole_in_the_wall", "pvp", "sumo", "arms_race", "one_shot", "tnt_run", "mob_count",
                    "rhino", "miners", "magic_battle", "color_floor", "anvils", "build_memory", "tool_race", "parkour_race",
                    "hot_potato", "jump_rope", "drop_tower", "chicken_shooter", "less_is_better", "wait_for_it" ->
                    I18n.tr("party.rounds.mode." + mode);
            default -> Component.text(mode);
        };
    }
}
