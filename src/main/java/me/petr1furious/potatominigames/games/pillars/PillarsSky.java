package me.petr1furious.potatominigames.games.pillars;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.random.RandomGenerator;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.World;

/** A match-world-only sky; the bootstrap registers its client-visible dimension definitions. */
final class PillarsSky implements AutoCloseable {
    static final int PALETTE_SIZE = 256;
    enum Mode { DAY, NIGHT, CUSTOM }
    record Profile(Mode mode, int paletteIndex, long time, int rgb, float starBrightness) {
        String id() {
            return mode == Mode.CUSTOM ? "potatominigames:pillars_sky_" + paletteIndex : "minecraft:overworld";
        }
    }

    private final Profile profile;
    private Saved saved;
    private boolean closed;

    private PillarsSky(Profile profile) { this.profile = profile; }

    static PillarsSky roll(RandomGenerator random) {
        Objects.requireNonNull(random, "random");
        return new PillarsSky(switch (random.nextInt(3)) {
            case 0 -> new Profile(Mode.DAY, -1, 6000, 0, 0);
            case 1 -> new Profile(Mode.NIGHT, -1, 18000, 0, 0.5f);
            case 2 -> palette(random.nextInt(PALETTE_SIZE));
            default -> throw new AssertionError("RandomGenerator returned an invalid bounded value");
        });
    }

    Profile profile() { return profile; }

    /** Sixty-four saturated hues, each with four genuinely non-vanilla night intensities. */
    static Profile palette(int index) {
        Objects.checkIndex(index, PALETTE_SIZE);
        float hue = (index / 4) * (6.0f / 64);
        int sector = (int) hue;
        float fraction = hue - sector;
        int high = 230, low = 46;
        int rising = Math.round(low + (high - low) * fraction);
        int falling = Math.round(high - (high - low) * fraction);
        int rgb = switch (sector) {
            case 0 -> high << 16 | rising << 8 | low;
            case 1 -> falling << 16 | high << 8 | low;
            case 2 -> low << 16 | high << 8 | rising;
            case 3 -> low << 16 | falling << 8 | high;
            case 4 -> rising << 16 | low << 8 | high;
            case 5 -> high << 16 | low << 8 | falling;
            default -> throw new AssertionError("Invalid palette hue");
        };
        float stars = switch (index % 4) {
            case 0 -> 0.15f;
            case 1 -> 0.30f;
            case 2 -> 0.70f;
            default -> 0.90f;
        };
        return new Profile(Mode.CUSTOM, index, 18000, rgb, stars);
    }

    /** Must run before the first player enters the disposable match world. */
    void apply(World world) {
        Objects.requireNonNull(world, "world");
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Pillars sky must run on the server thread");
        if (closed || saved != null) throw new IllegalStateException("Pillars sky is already applied or closed");
        if (!world.getPlayers().isEmpty()) throw new IllegalStateException("Pillars sky must be applied before players enter");
        if (world.getEnvironment() != World.Environment.NORMAL)
            throw new IllegalArgumentException("Pillars sky requires an overworld match clone");
        Native nativeApi = Native.INSTANCE;
        Saved state = nativeApi.save(world);
        saved = state;
        try {
            nativeApi.install(state, profile.id());
            world.setGameRule(GameRules.ADVANCE_TIME, false);
            world.setGameRule(GameRules.ADVANCE_WEATHER, false);
            world.setTime(profile.time());
            world.setStorm(false);
            world.setThundering(false);
            world.setClearWeatherDuration(Integer.MAX_VALUE);
            nativeApi.weather(state.level, 0, 0);
        } catch (RuntimeException failure) {
            try { close(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        Saved state = saved;
        saved = null;
        if (state == null) return;
        Native.INSTANCE.restore(state);
    }

    private record Saved(World world, Object level, Object dimension, Object attributes,
                         long time, boolean advanceTime, boolean advanceWeather,
                         boolean storm, boolean thunder, int weatherDuration, int thunderDuration,
                         int clearDuration, float rainLevel, float thunderLevel) { }

    /** Bound lazily so profile selection remains a pure Java check without a running server. */
    private static final class Native {
        static final Native INSTANCE = new Native();
        private final Field dimension;
        private final Method handle, registryAccess, lookup, identifier, resourceKey, getHolder;
        private final Method holderValue, minY, height, attributes, setAttributes, builder, defaults, build;
        private final Method rain, thunder, setRain, setThunder;
        private final Object dimensionRegistry;

        private Native() {
            try {
                Class<?> level = Class.forName("net.minecraft.world.level.Level");
                Class<?> serverLevel = Class.forName("net.minecraft.server.level.ServerLevel");
                Class<?> key = Class.forName("net.minecraft.resources.ResourceKey");
                Class<?> id = Class.forName("net.minecraft.resources.Identifier");
                Class<?> type = Class.forName("net.minecraft.world.level.dimension.DimensionType");
                Class<?> system = Class.forName("net.minecraft.world.attribute.EnvironmentAttributeSystem");
                Class<?> systemBuilder = Class.forName("net.minecraft.world.attribute.EnvironmentAttributeSystem$Builder");
                dimension = level.getDeclaredField("dimensionTypeRegistration");
                dimension.setAccessible(true);
                handle = Class.forName("org.bukkit.craftbukkit.CraftWorld").getMethod("getHandle");
                registryAccess = level.getMethod("registryAccess");
                lookup = Class.forName("net.minecraft.core.RegistryAccess").getMethod("lookupOrThrow", key);
                identifier = id.getMethod("parse", String.class);
                resourceKey = key.getMethod("create", key, id);
                getHolder = Class.forName("net.minecraft.core.HolderGetter").getMethod("getOrThrow", key);
                holderValue = Class.forName("net.minecraft.core.Holder").getMethod("value");
                dimensionRegistry = Class.forName("net.minecraft.core.registries.Registries").getField("DIMENSION_TYPE").get(null);
                minY = type.getMethod("minY");
                height = type.getMethod("height");
                attributes = serverLevel.getMethod("environmentAttributes");
                setAttributes = serverLevel.getMethod("setEnvironmentAttributes", system);
                builder = system.getMethod("builder");
                defaults = systemBuilder.getMethod("addDefaultLayers", level);
                build = systemBuilder.getMethod("build");
                rain = level.getMethod("getRainLevel", float.class);
                thunder = level.getMethod("getThunderLevel", float.class);
                setRain = level.getMethod("setRainLevel", float.class);
                setThunder = level.getMethod("setThunderLevel", float.class);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Pillars sky requires the verified ASP 26.3 dimension APIs", failure);
            }
        }

        Saved save(World world) {
            try {
                Object level = handle.invoke(world);
                return new Saved(world, level, dimension.get(level), attributes.invoke(level), world.getFullTime(),
                        Boolean.TRUE.equals(world.getGameRuleValue(GameRules.ADVANCE_TIME)),
                        Boolean.TRUE.equals(world.getGameRuleValue(GameRules.ADVANCE_WEATHER)),
                        world.hasStorm(), world.isThundering(), world.getWeatherDuration(), world.getThunderDuration(),
                        world.getClearWeatherDuration(), (float) rain.invoke(level, 1.0f), (float) thunder.invoke(level, 1.0f));
            } catch (ReflectiveOperationException failure) { throw unavailable(failure); }
        }

        void install(Saved saved, String id) {
            try {
                Object registry = lookup.invoke(registryAccess.invoke(saved.level), dimensionRegistry);
                Object key = resourceKey.invoke(null, dimensionRegistry, identifier.invoke(null, id));
                Object replacement = getHolder.invoke(registry, key);
                Object originalType = holderValue.invoke(saved.dimension);
                Object replacementType = holderValue.invoke(replacement);
                if (!minY.invoke(originalType).equals(minY.invoke(replacementType))
                        || !height.invoke(originalType).equals(height.invoke(replacementType)))
                    throw new IllegalStateException("Pillars sky dimension height differs from the match clone");
                dimension.set(saved.level, replacement);
                Object layers = defaults.invoke(builder.invoke(null), saved.level);
                setAttributes.invoke(saved.level, build.invoke(layers));
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Pillars sky definitions are unavailable; install the bootstrap datapack before joining", failure);
            }
        }

        void weather(Object level, float rainLevel, float thunderLevel) {
            try { setRain.invoke(level, rainLevel); setThunder.invoke(level, thunderLevel); }
            catch (ReflectiveOperationException failure) { throw unavailable(failure); }
        }

        void restore(Saved saved) {
            try {
                dimension.set(saved.level, saved.dimension);
                setAttributes.invoke(saved.level, saved.attributes);
                World world = saved.world;
                world.setFullTime(saved.time);
                world.setGameRule(GameRules.ADVANCE_TIME, saved.advanceTime);
                world.setGameRule(GameRules.ADVANCE_WEATHER, saved.advanceWeather);
                world.setStorm(saved.storm);
                world.setThundering(saved.thunder);
                world.setWeatherDuration(saved.weatherDuration);
                world.setThunderDuration(saved.thunderDuration);
                world.setClearWeatherDuration(saved.clearDuration);
                weather(saved.level, saved.rainLevel, saved.thunderLevel);
            } catch (ReflectiveOperationException failure) { throw unavailable(failure); }
        }

        private static IllegalStateException unavailable(ReflectiveOperationException failure) {
            return new IllegalStateException("Cannot apply or restore the Pillars match sky", failure);
        }
    }
}
