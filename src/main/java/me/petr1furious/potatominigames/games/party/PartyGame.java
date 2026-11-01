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

    public PartyGame(ConfigRegistry configs) { config = new PartyConfig(configs); }
    @Override public String id() { return "party"; }
    @Override public Component displayName() { return Component.text("Вечеринка", NamedTextColor.LIGHT_PURPLE); }
    @Override public List<String> modes() { return List.of("pvp", "sumo", "arms_race"); }
    @Override public boolean showModes() { return true; }
    @Override public Material icon() { return Material.IRON_SWORD; }
    @Override public int maxPlayers(String mode) { return mode.equals("arms_race") ? 16 : 12; }
    @Override public Component description(String mode) {
        return Component.text(switch (mode) {
            case "sumo" -> "Сумо: столкни соперника • каждый с каждым • BO3";
            case "arms_race" -> "Гонка вооружений: все против всех • 12 ступеней оружия";
            default -> "PvP: каждый с каждым • BO3 • случайные классы";
        });
    }
    @Override public GameSession createSession(SessionServices services, String mode) {
        if (mode.equals("arms_race")) return new ArmsRaceSession(services, config);
        return new DuelSession(services, config, mode.equals("sumo"));
    }
}
