package me.petr1furious.potatominigames.games.warfare;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import me.petr1furious.potatominigames.core.config.ConfigRegistry;
import me.petr1furious.potatominigames.core.config.Property;
import me.petr1furious.potatominigames.core.config.PropertyBag;
import me.petr1furious.potatominigames.core.config.PropertyCodec;
import me.petr1furious.potatominigames.core.team.TeamColor;

/** The "war" and "war.teambeacon" bags, plus the kit and beacon locations stored in the same file. */
public final class WarfareConfig {
    public static final String FILE = "games/warfare.yml";

    private final ConfigRegistry configs;

    private final PropertyBag bag = new PropertyBag("war");
    public final Property<Vector> gameCenter = bag.define("gameCenter", PropertyCodec.VECTOR, new Vector(0, 0, 0));
    public final Property<String> gameWorld = bag.define("gameWorld", PropertyCodec.STRING, "world");
    public final Property<Integer> gameRadius = bag.define("gameRadius", PropertyCodec.INT, 200);
    public final Property<String> mode = bag.define("mode", PropertyCodec.STRING, "classic");
    public final Property<Boolean> arenaBorderEnabled = bag.define("arenaBorderEnabled", PropertyCodec.BOOL, false);
    public final Property<Double> arenaBorderInitialSize = bag.define("arenaBorderInitialSize", PropertyCodec.DOUBLE,
            400.0);
    public final Property<Double> arenaBorderFinalSize = bag.define("arenaBorderFinalSize", PropertyCodec.DOUBLE,
            100.0);
    public final Property<Integer> arenaBorderTimeToFinalSeconds = bag.define("arenaBorderTimeToFinalSeconds",
            PropertyCodec.INT, 600);
    public final Property<Double> arenaBorderCenterRadius = bag.define("arenaBorderCenterRadius",
            PropertyCodec.DOUBLE, 0.0);
    public final Property<Integer> distanceUpdateIntervalSeconds = bag.define("distanceUpdateIntervalSeconds",
            PropertyCodec.INT, 4);
    public final Property<Integer> distanceGranularity = bag.define("distanceGranularity", PropertyCodec.INT, 50);
    public final Property<Boolean> enableLifts = bag.define("enableLifts", PropertyCodec.BOOL, true);
    public final Property<Material> liftMaterial = bag.define("liftMaterial", PropertyCodec.MATERIAL,
            Material.LIGHT_GRAY_CONCRETE);
    public final Property<Boolean> enableGameInventory = bag.define("enableGameInventory", PropertyCodec.BOOL, false);
    public final Property<Boolean> enforceGameRules = bag.define("enforceGameRules", PropertyCodec.BOOL, true);

    private final PropertyBag teamBeaconBag = new PropertyBag("war.teambeacon");
    public final Property<Boolean> hidePerPlayerDistances = teamBeaconBag.define("hidePerPlayerDistances",
            PropertyCodec.BOOL, true);
    public final Property<Boolean> keepInvisibility = teamBeaconBag.define("keepInvisibility", PropertyCodec.BOOL,
            true);
    public final Property<Integer> protectionRadius = teamBeaconBag.define("protectionRadius", PropertyCodec.INT, 16);
    public final Property<Integer> beaconMaxHealth = teamBeaconBag.define("beaconMaxHealth", PropertyCodec.INT, 20);
    public final Property<Integer> respawnDelaySeconds = teamBeaconBag.define("respawnDelaySeconds",
            PropertyCodec.INT, 8);
    public final Property<Integer> respawnBlindnessSeconds = teamBeaconBag.define("respawnBlindnessSeconds",
            PropertyCodec.INT, 8);
    public final Property<Boolean> disableSpawnProtectionOnAttack = teamBeaconBag
            .define("disableSpawnProtectionOnAttack", PropertyCodec.BOOL, true);
    public final Property<Integer> spawnProtectionSeconds = teamBeaconBag.define("spawnProtectionSeconds",
            PropertyCodec.INT, 2);
    public final Property<Boolean> friendlyFire = teamBeaconBag.define("friendlyFire", PropertyCodec.BOOL, false);
    public final Property<Boolean> beaconBlockBreaksOnlyByEnemies = teamBeaconBag
            .define("beaconBlockBreaksOnlyByEnemies", PropertyCodec.BOOL, true);

    public WarfareConfig(ConfigRegistry configs) {
        this.configs = configs;
        configs.register(bag, FILE, "");
        configs.register(teamBeaconBag, FILE, "teamBeacon");
    }

    // An enabled slime-world arena overrides this (see WarfareSessionBase#gameWorld).

    // Game kit, stored as an ItemStack list.

    public ItemStack[] gameInventory() {
        List<?> stored = configs.file(FILE).getList("gameInventory");
        if (stored == null) {
            return null;
        }
        return stored.stream().map(item -> item instanceof ItemStack stack ? stack : null)
                .toArray(ItemStack[]::new);
    }

    public void saveGameInventory(ItemStack[] contents) {
        configs.file(FILE).set("gameInventory", Arrays.asList(contents));
        configs.saveFile(FILE);
    }

    public Location getBeacon(TeamColor team) {
        ConfigurationSection section = configs.file(FILE).getConfigurationSection("beacons." + team.id());
        if (section == null) {
            return null;
        }
        World world = Bukkit.getWorld(section.getString("world", ""));
        if (world == null) {
            return null;
        }
        return new Location(world, section.getInt("x"), section.getInt("y"), section.getInt("z"));
    }

    public void setBeacon(TeamColor team, Location location) {
        String path = "beacons." + team.id();
        var file = configs.file(FILE);
        if (location == null) {
            file.set(path, null);
        } else {
            file.set(path + ".world", location.getWorld().getName());
            file.set(path + ".x", location.getBlockX());
            file.set(path + ".y", location.getBlockY());
            file.set(path + ".z", location.getBlockZ());
        }
        configs.saveFile(FILE);
    }

    public List<TeamColor> teamsWithBeacons() {
        List<TeamColor> result = new ArrayList<>();
        for (TeamColor team : TeamColor.values()) {
            if (getBeacon(team) != null) {
                result.add(team);
            }
        }
        return result;
    }
}
