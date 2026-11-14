package me.petr1furious.potatominigames.games.party;

import me.petr1furious.potatominigames.core.config.ConfigRegistry;
import me.petr1furious.potatominigames.core.game.GameSession;
import me.petr1furious.potatominigames.core.game.Minigame;
import me.petr1furious.potatominigames.core.game.SessionServices;
import me.petr1furious.potatominigames.i18n.I18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Material;

import java.util.List;

public final class PartyGame implements Minigame {
    private final PartyConfig config;

    public PartyGame(ConfigRegistry configs) {
        this(new PartyConfig(configs));
    }

    PartyGame(PartyConfig config) {
        this.config = config;
    }

    @Override
    public String id() {
        return "party";
    }

    @Override
    public Component displayName() {
        return I18n.tr("party.rounds.name").color(NamedTextColor.LIGHT_PURPLE);
    }

    @Override
    public List<String> modes() {
        return List.of(
                "pvp",
                "sumo",
                "pvp_ffa",
                "pvp_select",
                "sumo_ffa",
                "arms_race",
                "one_shot",
                "tnt_run",
                "mob_count",
                "rhino",
                "miners",
                "magic_battle",
                "color_floor",
                "anvils",
                "build_memory",
                "tool_race",
                "parkour_race",
                "hole_in_the_wall",
                "party_games");
    }

    @Override
    public boolean showModes() {
        return true;
    }

    @Override
    public List<ModeGroup> modeGroups() {
        return List.of(
                new ModeGroup(
                        I18n.tr("party.rounds.mode.pvp"),
                        Material.IRON_SWORD,
                        List.of("pvp", "pvp_ffa", "pvp_select")));
    }

    @Override
    public Component groupedModeTitle(String mode) {
        return List.of("pvp", "pvp_ffa", "pvp_select").contains(mode)
                ? I18n.tr("party.rounds.pvp_variant." + mode)
                : modeTitle(mode);
    }

    @Override
    public Material icon() {
        return Material.IRON_SWORD;
    }

    @Override
    public Component modeTitle(String mode) {
        return mode.equals("pvp_ffa") || mode.equals("pvp_select") || mode.equals("sumo_ffa")
                ? I18n.tr("party.rounds.mode." + mode)
                : mode.equals("party_games")
                        ? I18n.tr("party.rounds.mode.party_games")
                        : PartyRoundFactory.title(mode);
    }

    @Override
    public Material icon(String mode) {
        return switch (mode) {
            case "one_shot" -> Material.BOW;
            case "parkour_race" -> Material.CLOCK;
            case "magic_battle" -> Material.BLAZE_ROD;
            case "miners", "tool_race" -> Material.DIAMOND_PICKAXE;
            case "color_floor" -> Material.LIME_CONCRETE;
            case "pvp_ffa" -> Material.DIAMOND_SWORD;
            case "pvp_select" -> Material.NETHER_STAR;
            case "sumo_ffa" -> Material.STICK;
            case "anvils" -> Material.ANVIL;
            case "hole_in_the_wall" -> Material.SLIME_BLOCK;
            case "party_games" -> Material.FIREWORK_ROCKET;
            case "build_memory" -> Material.BRICKS;
            default -> icon();
        };
    }

    @Override
    public int maxPlayers(String mode) {
        return mode.equals("pvp")
                        || mode.equals("sumo")
                        || mode.equals("miners")
                        || mode.equals("magic_battle")
                        || List.of(
                                        "color_floor",
                                        "anvils",
                                        "build_memory",
                                        "tool_race",
                                        "parkour_race",
                                        "hole_in_the_wall",
                                        "party_games")
                                .contains(mode)
                ? 12
                : 16;
    }

    @Override
    public Component description(String mode) {
        return I18n.tr("party.rounds.description." + (modes().contains(mode) ? mode : "pvp"));
    }

    @Override
    public GameSession createSession(SessionServices services, String mode) {
        if (mode.equals("pvp_select")) return new DuelSession(services, config, false, true, true);
        if (mode.equals("one_shot"))
            return new OneShotSession(services, config, PartyRoundContext.standalone(services));
        if (mode.equals("parkour_race"))
            return new ParkourRaceSession(services, config, PartyRoundContext.standalone(services));
        if (mode.equals("hole_in_the_wall"))
            return new HoleWallSession(services, config, PartyRoundContext.standalone(services));
        if (mode.equals("party_games")) return new PartyGamesSession(services, config);
        if (mode.equals("color_floor"))
            return new ColorFloorSession(services, config, PartyRoundContext.standalone(services));
        if (mode.equals("anvils"))
            return new AnvilSession(services, config, PartyRoundContext.standalone(services));
        if (mode.equals("build_memory"))
            return new BuildMemorySession(services, config, PartyRoundContext.standalone(services));
        if (mode.equals("tool_race"))
            return new ToolRaceSession(services, config, PartyRoundContext.standalone(services));
        if (mode.equals("magic_battle")) return new MagicBattleSession(services, config);
        if (mode.equals("miners")) return new MinersSession(services, config);
        if (mode.equals("arms_race")) return new ArmsRaceSession(services, config);
        if (mode.equals("tnt_run")) return new TntRunSession(services, config);
        if (mode.equals("mob_count")) return new MobCountSession(services, config);
        if (mode.equals("rhino")) return new RhinoSession(services, config);
        return new DuelSession(services, config, mode.equals("sumo") || mode.equals("sumo_ffa"),
                mode.equals("pvp_ffa") || mode.equals("sumo_ffa"));
    }
}
