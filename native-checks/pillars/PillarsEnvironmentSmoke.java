import java.nio.file.Files;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/** Run only on a disposable ASP server: client-only flood, contact logic and synced sky registries. */
public final class PillarsEnvironmentSmoke extends JavaPlugin {
    @Override
    public void onEnable() {
        Bukkit.getScheduler().runTask(this, () -> {
            try {
                JavaPlugin main = (JavaPlugin) Bukkit.getPluginManager().getPlugin("PotatoMinigames");
                if (main == null || !main.isEnabled()) throw new IllegalStateException("Main plugin did not load");
                int flood = FloodSmoke.run(this, main);
                int sky = PillarsSkySmoke.run(this, main);
                getDataFolder().mkdirs();
                String result = "PILLARS_ENVIRONMENT_SMOKE_PASS: " + flood + " flood, " + sky + " sky assertions\n";
                Files.writeString(getDataFolder().toPath().resolve("result.txt"), result);
                getLogger().info(result.trim());
            } catch (Throwable error) {
                getLogger().log(Level.SEVERE, "PILLARS_ENVIRONMENT_SMOKE_FAIL", error);
            } finally {
                Bukkit.shutdown();
            }
        });
    }
}
