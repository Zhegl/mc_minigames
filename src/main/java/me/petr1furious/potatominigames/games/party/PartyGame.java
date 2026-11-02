package me.petr1furious.potatominigames.games.party;

import java.util.List;
import org.bukkit.Material;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import me.petr1furious.potatominigames.core.config.ConfigRegistry;
import me.petr1furious.potatominigames.core.game.GameSession;
import me.petr1furious.potatominigames.core.game.Minigame;
import me.petr1furious.potatominigames.core.game.SessionServices;

public final class PartyGame implements Minigame {
    private final PartyConfig config;

    public PartyGame(ConfigRegistry configs) { this(new PartyConfig(configs)); }
    PartyGame(PartyConfig config) { this.config = config; }
    @Override public String id() { return "party"; }
    @Override public Component displayName() { return Component.text("Вечеринка", NamedTextColor.LIGHT_PURPLE); }
    @Override public List<String> modes() { return List.of("pvp", "sumo", "arms_race", "tnt_run", "mob_count", "rhino", "miners"); }
    @Override public boolean showModes() { return true; }
    @Override public Material icon() { return Material.IRON_SWORD; }
    @Override public String modeTitle(String mode) { return mode.equals("miners") ? "Битва шахтёров" : mode; }
    @Override public Material icon(String mode) { return mode.equals("miners") ? Material.DIAMOND_PICKAXE : icon(); }
    @Override public int maxPlayers(String mode) { return mode.equals("pvp") || mode.equals("sumo") || mode.equals("miners") ? 12 : 16; }
    @Override public Component description(String mode) {
        return Component.text(switch (mode) {
            case "miners" -> "Битва шахтёров: личные шахты • случайный лут из руд • затем PvP";
            case "sumo" -> "Сумо: столкни соперника • каждый с каждым • BO3";
            case "arms_race" -> "Гонка вооружений: все против всех • 12 ступеней оружия";
            case "tnt_run" -> "TNT Run: три яруса • исчезающий пол • последний выживший";
            case "mob_count" -> "Пересчёт: запомни мобов • выбери голову • прыгни на верную платформу";
            case "rhino" -> "Носорожий переполох: рывки разорителей • бросай мины • не упади";
            default -> "PvP: каждый с каждым • BO3 • случайные классы";
        });
    }
    @Override public GameSession createSession(SessionServices services, String mode) {
        if (mode.equals("miners")) return new MinersSession(services, config);
        if (mode.equals("arms_race")) return new ArmsRaceSession(services, config);
        if (mode.equals("tnt_run")) return new TntRunSession(services, config);
        if (mode.equals("mob_count")) return new MobCountSession(services, config);
        if (mode.equals("rhino")) return new RhinoSession(services, config);
        return new DuelSession(services, config, mode.equals("sumo"));
    }
}
