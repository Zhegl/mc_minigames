import com.google.gson.JsonPrimitive;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistrySynchronization;
import net.minecraft.core.RegistrySynchronization.PackedRegistryEntry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.configuration.ClientboundRegistryDataPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.timeline.Timeline;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.plugin.java.JavaPlugin;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/** Runs only on an isolated ASP 26.3 server bootstrapped with the production plugin. */
public final class PillarsSkySmoke {
    private int assertions;

    public static int run(JavaPlugin smoke, JavaPlugin main) throws Exception {
        return new PillarsSkySmoke().check(smoke, main);
    }

    private int check(JavaPlugin smoke, JavaPlugin main) throws Exception {
        require(Bukkit.isPrimaryThread(), "Native sky smoke runs on the server thread");
        World lobby = Bukkit.getWorlds().getFirst();
        ServerLevel lobbyLevel = ((CraftWorld) lobby).getHandle();
        Object lobbyDimension = lobbyLevel.dimensionTypeRegistration();
        Object lobbyAttributes = lobbyLevel.environmentAttributes();
        long lobbyTime = lobby.getFullTime();
        boolean lobbyAdvance = Boolean.TRUE.equals(lobby.getGameRuleValue(GameRules.ADVANCE_TIME));
        Class<?> skyType = Class.forName("me.petr1furious.potatominigames.games.pillars.PillarsSky", true,
                main.getClass().getClassLoader());
        Method roll = method(skyType, "roll", RandomGenerator.class);
        Method apply = method(skyType, "apply", World.class);
        Method close = method(skyType, "close");
        Method profile = method(skyType, "profile");
        String name = "pillars_sky_smoke_" + UUID.randomUUID().toString().replace("-", "");
        World world = new WorldCreator(name).type(WorldType.FLAT).generateStructures(false).createWorld();
        require(world != null && world != lobby, "The fixture uses a disposable native match world");
        Path folder = world.getWorldFolder().toPath();
        ServerLevel level = ((CraftWorld) world).getHandle();
        Object current = null;
        try {
            Map<ResourceKey<? extends Registry<?>>, List<PackedRegistryEntry>> synchronizedData = new HashMap<>();
            // Match SynchronizeRegistriesTask: encode WORLD registries against the complete
            // composite lookup, so cross-registry references (including item holders) resolve.
            var registries = level.getServer().registries();
            RegistrySynchronization.packRegistries(registries.compositeAccess().createSerializationContext(NbtOps.INSTANCE),
                    registries.getAccessFrom(RegistryLayer.WORLD), Set.of(), synchronizedData::put);
            List<PackedRegistryEntry> dimensions = packetEntries(Registries.DIMENSION_TYPE, synchronizedData);
            List<PackedRegistryEntry> timelines = packetEntries(Registries.TIMELINE, synchronizedData);
            require(dimensions.stream().filter(entry -> entry.id().getNamespace().equals("potatominigames")
                            && entry.id().getPath().startsWith("pillars_sky_")).count() == 256,
                    "The real configuration packet synchronizes the complete registered dimension palette");
            require(timelines.stream().filter(entry -> entry.id().getNamespace().equals("potatominigames")
                            && entry.id().getPath().startsWith("pillars_sky_")).count() == 256,
                    "The real configuration packet synchronizes the complete registered timeline palette");
            for (int[] selection : new int[][] { {0, 0}, {1, 0}, {2, 0}, {2, 255} }) {
                world.setFullTime(12345);
                world.setGameRule(GameRules.ADVANCE_TIME, true);
                world.setGameRule(GameRules.ADVANCE_WEATHER, true);
                world.setStorm(true);
                world.setThundering(true);
                world.setWeatherDuration(431);
                world.setThunderDuration(987);
                world.setClearWeatherDuration(0);
                level.setRainLevel(1);
                level.setThunderLevel(1);
                Object originalDimension = level.dimensionTypeRegistration();
                Object originalAttributes = level.environmentAttributes();
                current = roll.invoke(null, new Selection(selection[0], selection[1]));
                Object chosen = profile.invoke(current);
                Class<?> profileType = chosen.getClass();
                String id = (String) method(profileType, "id").invoke(chosen);
                long time = (long) method(profileType, "time").invoke(chosen);
                int rgb = (int) method(profileType, "rgb").invoke(chosen);
                float stars = (float) method(profileType, "starBrightness").invoke(chosen);
                apply.invoke(current, world);
                require(level.dimensionTypeRegistration().unwrapKey().orElseThrow().identifier().toString().equals(id),
                        "The match receives the selected registered dimension holder");
                require(world.getTime() == time && !world.getGameRuleValue(GameRules.ADVANCE_TIME)
                                && !world.getGameRuleValue(GameRules.ADVANCE_WEATHER),
                        "Selected noon/midnight overrides map time and cannot advance");
                require(!world.hasStorm() && !world.isThundering() && level.getRainLevel(1) == 0
                                && level.getThunderLevel(1) == 0 && world.getClearWeatherDuration() > 0,
                        "Native rain/thunder interpolation is cleared immediately, not twenty ticks later");
                ServerPlayer entrant = new ServerPlayer(level.getServer(), level,
                        new GameProfile(UUID.randomUUID(), "SkySmoke"), ClientInformation.createDefault());
                try {
                    var spawn = entrant.createCommonSpawnInfo(level);
                    ClientboundRespawnPacket respawn = respawnRoundTrip(level,
                            new ClientboundRespawnPacket(spawn, ClientboundRespawnPacket.KEEP_ALL_DATA));
                    require(respawn.commonPlayerSpawnInfo().dimensionType() == level.dimensionTypeRegistration(),
                            "The actual respawn wire codec carries the chosen registered holder ID");
                    ClientboundLoginPacket login = loginRoundTrip(level, new ClientboundLoginPacket(entrant.getId(), false,
                            Set.of(level.dimension()), 20, 10, 10, false, true, false, spawn, false, false));
                    require(login.commonPlayerSpawnInfo().dimensionType() == level.dimensionTypeRegistration(),
                            "A reconnecting client receives the same profile through the actual login wire codec");
                } finally { entrant.getTextFilter().leave(); }
                if (selection[0] == 2) {
                    Vector3fc expected = ExtraCodecs.STRING_RGB_VEC3_COLOR.parse(JsonOps.INSTANCE,
                            new JsonPrimitive(String.format("#%06x", rgb))).getOrThrow();
                    Vector3fc actual = level.environmentAttributes().getValue(EnvironmentAttributes.SKY_COLOR, new Vec3(0, 100, 0));
                    require(same(actual, expected), "The actual native sky sampler keeps the saturated tint visible at midnight");
                    require(Math.abs(level.environmentAttributes().getValue(EnvironmentAttributes.STAR_BRIGHTNESS,
                                    new Vec3(0, 100, 0)) - stars) < 0.00001f,
                            "The actual native star sampler retains the selected non-vanilla brightness");
                    PackedRegistryEntry timelineEntry = find(timelines, id);
                    Timeline clientTimeline = Timeline.NETWORK_CODEC.parse(
                            RegistryOps.create(NbtOps.INSTANCE, level.registryAccess()), timelineEntry.data().orElseThrow()).getOrThrow();
                    require(same(clientTimeline.createTrackSampler(EnvironmentAttributes.SKY_COLOR, level.clockManager())
                                    .applyTimeBased(new Vector3f(), 0), expected),
                            "Client-synchronized sky track overrides black/biome colour instead of multiplying it by night");
                    require(Math.abs(clientTimeline.createTrackSampler(EnvironmentAttributes.STAR_BRIGHTNESS, level.clockManager())
                                    .applyTimeBased(0.5f, 0) - stars) < 0.00001f,
                            "Client-synchronized star track overrides vanilla 0.5 even when selected brightness is lower");
                    DimensionType clientDimension = DimensionType.NETWORK_CODEC.parse(
                            RegistryOps.create(NbtOps.INSTANCE, level.registryAccess()), find(dimensions, id).data().orElseThrow()).getOrThrow();
                    require(clientDimension.timelines().stream().anyMatch(holder -> holder.unwrapKey().orElseThrow()
                                    .identifier().toString().equals(id)),
                            "Client-synchronized dimension references the selected custom timeline");
                }
                close.invoke(current);
                close.invoke(current);
                current = null;
                require(level.dimensionTypeRegistration() == originalDimension && level.environmentAttributes() == originalAttributes,
                        "Idempotent cleanup restores the original world holder and environment sampler identities");
                require(world.getFullTime() == 12345 && world.getGameRuleValue(GameRules.ADVANCE_TIME)
                                && world.getGameRuleValue(GameRules.ADVANCE_WEATHER) && world.hasStorm() && world.isThundering()
                                && world.getWeatherDuration() == 431 && world.getThunderDuration() == 987
                                && world.getClearWeatherDuration() == 0 && level.getRainLevel(1) == 1 && level.getThunderLevel(1) == 1,
                        "Cleanup restores original time, gamerules, weather durations and interpolated native weather");
            }
            require(lobbyLevel.dimensionTypeRegistration() == lobbyDimension && lobbyLevel.environmentAttributes() == lobbyAttributes
                            && lobby.getFullTime() == lobbyTime && lobby.getGameRuleValue(GameRules.ADVANCE_TIME) == lobbyAdvance,
                    "All profiles and cleanup leave the lobby and its clock untouched");
        } finally {
            try {
                if (current != null) close.invoke(current);
            } finally {
                require(Bukkit.unloadWorld(world, false), "The disposable native fixture world unloads without saving");
                try (var paths = Files.walk(folder)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
                }
            }
        }
        require(Bukkit.getWorld(name) == null && !Files.exists(folder), "No disposable world or files leak after cleanup");
        smoke.getLogger().info("PILLARS_SKY_SMOKE_PASS: " + assertions + " native assertions");
        return assertions;
    }

    private List<PackedRegistryEntry> packetEntries(ResourceKey<? extends Registry<?>> key,
            Map<ResourceKey<? extends Registry<?>>, List<PackedRegistryEntry>> data) {
        List<PackedRegistryEntry> entries = data.get(key);
        require(entries != null, "The registry is part of native configuration synchronization: " + key);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ClientboundRegistryDataPacket.STREAM_CODEC.encode(buffer, new ClientboundRegistryDataPacket(key, entries));
            ClientboundRegistryDataPacket decoded = ClientboundRegistryDataPacket.STREAM_CODEC.decode(buffer);
            require(decoded.registry().equals(key) && buffer.readableBytes() == 0,
                    "Actual configuration-registry packet roundtrip consumes all bytes");
            return decoded.entries();
        } finally { buffer.release(); }
    }

    private static PackedRegistryEntry find(List<PackedRegistryEntry> entries, String id) {
        return entries.stream().filter(entry -> entry.id().equals(Identifier.parse(id))).findFirst().orElseThrow();
    }

    private static ClientboundRespawnPacket respawnRoundTrip(ServerLevel level, ClientboundRespawnPacket packet) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        try {
            ClientboundRespawnPacket.STREAM_CODEC.encode(buffer, packet);
            return ClientboundRespawnPacket.STREAM_CODEC.decode(buffer);
        } finally { buffer.release(); }
    }

    private static ClientboundLoginPacket loginRoundTrip(ServerLevel level, ClientboundLoginPacket packet) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        try {
            ClientboundLoginPacket.STREAM_CODEC.encode(buffer, packet);
            return ClientboundLoginPacket.STREAM_CODEC.decode(buffer);
        } finally { buffer.release(); }
    }

    private static boolean same(Vector3fc left, Vector3fc right) {
        return Math.abs(left.x() - right.x()) < 0.00001f && Math.abs(left.y() - right.y()) < 0.00001f
                && Math.abs(left.z() - right.z()) < 0.00001f;
    }

    private static Method method(Class<?> type, String name, Class<?>... parameters) throws Exception {
        Method method = type.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }

    private static final class Selection implements RandomGenerator {
        private final int mode, palette;
        private int calls;
        Selection(int mode, int palette) { this.mode = mode; this.palette = palette; }
        @Override public long nextLong() { throw new AssertionError("Unexpected unbounded random draw"); }
        @Override public int nextInt(int bound) { return calls++ == 0 ? mode : palette; }
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}
