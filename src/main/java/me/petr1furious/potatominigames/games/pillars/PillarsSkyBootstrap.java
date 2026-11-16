package me.petr1furious.potatominigames.games.pillars;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Registers ordinary vanilla-client data, before the server freezes/synchronizes its registries. */
@SuppressWarnings("UnstableApiUsage")
public final class PillarsSkyBootstrap implements PluginBootstrap {
    @Override public void bootstrap(BootstrapContext context) {
        context.getLifecycleManager().registerEventHandler(LifecycleEvents.DATAPACK_DISCOVERY, event -> {
            try {
                ClassLoader nativeResources = Class.forName("net.minecraft.SharedConstants", false,
                        getClass().getClassLoader()).getClassLoader();
                Path pack = writePack(context.getDataDirectory(), nativeResources);
                event.registrar().discoverPack(pack, "pillars_sky", config -> config.autoEnableOnServerStart(true));
            } catch (IOException | ClassNotFoundException failure) {
                throw new IllegalStateException("Cannot register the Pillars sky datapack; custom skies cannot be presented", failure);
            }
        });
    }

    static Path writePack(Path directory, ClassLoader nativeResources) throws IOException {
        JsonObject version = resource(nativeResources, "version.json");
        JsonObject format = version.getAsJsonObject("pack_version");
        JsonArray supported = new JsonArray();
        supported.add(format.get("data_major").getAsInt());
        supported.add(format.get("data_minor").getAsInt());
        JsonObject metadata = new JsonObject();
        JsonObject info = new JsonObject();
        info.addProperty("description", "Pillars match-only sky definitions");
        info.add("min_format", supported);
        info.add("max_format", supported.deepCopy());
        metadata.add("pack", info);
        JsonObject overworld = resource(nativeResources, "data/minecraft/dimension_type/overworld.json");
        JsonObject day = resource(nativeResources, "data/minecraft/timeline/day.json");
        List<String> timelines = new ArrayList<>();
        JsonElement configured = overworld.get("timelines");
        if (configured.isJsonArray()) {
            for (JsonElement entry : configured.getAsJsonArray())
                expandTimeline(nativeResources, entry.getAsString(), timelines, new HashSet<>());
        } else {
            expandTimeline(nativeResources, configured.getAsString(), timelines, new HashSet<>());
        }
        if (!timelines.contains("minecraft:day")) throw new IOException("The native overworld has no day timeline");
        Files.createDirectories(directory);
        Path target = directory.resolve("pillars-sky.zip");
        Path temporary = Files.createTempFile(directory, "pillars-sky-", ".zip");
        try {
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temporary))) {
                entry(zip, "pack.mcmeta", metadata);
                for (int index = 0; index < PillarsSky.PALETTE_SIZE; index++) {
                    PillarsSky.Profile profile = PillarsSky.palette(index);
                    JsonObject timeline = day.deepCopy();
                    JsonObject tracks = timeline.getAsJsonObject("tracks");
                    tracks.add("minecraft:visual/sky_color", constant(String.format("#%06x", profile.rgb())));
                    tracks.add("minecraft:visual/star_brightness", constant(profile.starBrightness()));
                    // Time-marker names are global registry entries: only vanilla day owns them.
                    timeline.remove("time_markers");
                    JsonObject dimension = overworld.deepCopy();
                    JsonArray selectedTimelines = new JsonArray();
                    for (String name : timelines)
                        selectedTimelines.add(name.equals("minecraft:day") ? profile.id() : name);
                    dimension.add("timelines", selectedTimelines);
                    String name = "pillars_sky_" + index + ".json";
                    entry(zip, "data/potatominigames/timeline/" + name, timeline);
                    entry(zip, "data/potatominigames/dimension_type/" + name, dimension);
                }
            }
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static JsonObject constant(Object value) {
        JsonObject frame = new JsonObject();
        frame.addProperty("ticks", 0);
        if (value instanceof Number number) frame.addProperty("value", number);
        else frame.addProperty("value", (String) value);
        JsonArray keyframes = new JsonArray();
        keyframes.add(frame);
        JsonObject track = new JsonObject();
        track.add("keyframes", keyframes);
        // No modifier means override. In particular, do not multiply sky RGB by vanilla's
        // near-black night value or maximum star brightness against vanilla's 0.5.
        return track;
    }

    private static void expandTimeline(ClassLoader resources, String name, List<String> result,
                                       Set<String> visiting) throws IOException {
        if (!name.startsWith("#")) { result.add(name); return; }
        if (!visiting.add(name)) throw new IOException("Cyclic native timeline tag " + name);
        String[] id = name.substring(1).split(":", 2);
        if (id.length != 2) throw new IOException("Invalid native timeline tag " + name);
        JsonObject tag = resource(resources, "data/" + id[0] + "/tags/timeline/" + id[1] + ".json");
        for (JsonElement entry : tag.getAsJsonArray("values")) {
            String value = entry.isJsonPrimitive() ? entry.getAsString() : entry.getAsJsonObject().get("id").getAsString();
            expandTimeline(resources, value, result, visiting);
        }
        visiting.remove(name);
    }

    private static JsonObject resource(ClassLoader resources, String name) throws IOException {
        try (InputStream stream = resources.getResourceAsStream(name)) {
            if (stream == null) throw new IOException("Missing native sky resource " + name);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static void entry(ZipOutputStream zip, String name, JsonObject json) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(json.toString().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
