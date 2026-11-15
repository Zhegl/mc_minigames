package me.petr1furious.potatominigames.games.finals;

import org.bukkit.configuration.file.YamlConfiguration;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Preview descriptors must reflect draft edits, including incomplete setup, without mutating it.
 */
public final class FinalsMapPreviewCheck {
    public static void main(String[] args) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("world", "editor");
        yaml.set("vault", List.of(10d, 65d, 10d, 0d, 0d));
        yaml.set("cashout-b", List.of(20d, 65d, 20d, 0d, 0d));
        yaml.set("red.0", List.of(1.5d, 65d, 3.5d, 90d, 0d));
        yaml.set("blue.0", List.of(30.5d, 65d, 3.5d, -90d, 0d));
        FinalsMapData.Draft draft = FinalsMapData.Draft.read(yaml);
        Class<?> preview;
        try {
            preview =
                    Class.forName("me.petr1furious.potatominigames.games.finals.FinalsMapPreview");
        } catch (ClassNotFoundException missing) {
            throw new AssertionError(
                    "Map editing must provide previews for configured draft locations", missing);
        }
        Method markers = preview.getDeclaredMethod("markers", FinalsMapData.Draft.class);
        markers.setAccessible(true);
        List<?> before = (List<?>) markers.invoke(null, draft);
        require(
                before.size() == 4,
                "Incomplete drafts preview every configured point without requiring A");
        require(value(before.get(0), "key").equals("vault"), "Vault uses its own preview label");
        require(
                value(before.get(1), "key").equals("cashout")
                        && value(before.get(1), "number").equals(2),
                "A missing candidate preserves B's displayed index");
        require(
                value(before.get(2), "key").equals("red_spawn")
                        && value(before.get(3), "key").equals("blue_spawn"),
                "Team spawn previews identify the assigned team");
        FinalsMapData.Point spawn = (FinalsMapData.Point) value(before.get(2), "point");
        require(
                spawn.x() == 1.5 && spawn.z() == 3.5 && spawn.yaw() == 90,
                "Spawn preview retains both exact position and facing");
        draft.vaults.clear();
        draft.red.add(new FinalsMapData.Point(4.5, 65, 3.5, 180, 0));
        List<?> after = (List<?>) markers.invoke(null, draft);
        require(
                after.size() == 4 && value(after.get(0), "key").equals("cashout"),
                "Cleared vaults disappear when the draft is refreshed");
        require(
                value(after.get(2), "key").equals("red_spawn")
                        && value(after.get(2), "number").equals(2),
                "Appended spawn locations appear with distinct numbered labels");
        require(
                before.size() == 4 && value(before.get(0), "key").equals("vault"),
                "Each view snapshot remains independent of later draft edits");
        var origin = new FinalsMapData.Point(8.5, 65, 8.5, 0, 0);
        draft.jumpPads.add(new FinalsMapData.JumpPad(origin, true, null));
        List<?> unfinished = (List<?>) markers.invoke(null, draft);
        require(value(unfinished.getLast(), "key").equals("pad_targeted"), "An unfinished targeted pad remains visible in editor previews");
        var target = new FinalsMapData.Point(18.5, 75, 8.5, 0, 0);
        draft.jumpPads.set(0, new FinalsMapData.JumpPad(origin, true, target));
        List<?> completed = (List<?>) markers.invoke(null, draft);
        require(value(completed.getLast(), "key").equals("pad_target")
                        && value(completed.getLast(), "number").equals(1)
                        && value(completed.getLast(), "point").equals(target), "Target preview shares its launch pad number and destination");
        draft.jumpPads.clear();
        require(((List<?>) markers.invoke(null, draft)).size() == after.size(), "Removing a pad removes both preview endpoints");
        System.out.println(
                "FinalsMapPreviewCheck: draft previews and live-edit descriptors passed");
    }

    private static Object value(Object record, String name) throws Exception {
        Method method = record.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(record);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
