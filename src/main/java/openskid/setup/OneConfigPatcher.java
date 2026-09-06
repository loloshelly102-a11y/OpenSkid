package openskid.setup;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

/**
 * Makes OneConfig open on O instead of RightShift, which clashes with the
 * OpenSkid ClickGUI key. OneConfig stores its GUI key in
 * OneConfig/Preferences.json as {"oneConfigKeyBind":{"keyBinds":[code]}}.
 * Runs on the first 3 launches only, then never touches the file again.
 */
public final class OneConfigPatcher {
    private static final int KEY_O = 24;
    private static final int MAX_PATCHES = 3;

    private OneConfigPatcher() {
    }

    public static void run(File gameDir) {
        try {
            if (gameDir == null || !isOneConfigPresent(gameDir)) {
                return;
            }
            File counter = new File(gameDir, "config/OpenSkid/oneconfig_patch_count");
            int count = readCount(counter);
            if (count >= MAX_PATCHES) {
                return;
            }
            File prefs = new File(gameDir, "OneConfig/Preferences.json");
            if (patch(prefs)) {
                writeCount(counter, count + 1);
            }
        } catch (Exception ignored) {
        }
    }

    private static boolean isOneConfigPresent(File gameDir) {
        try {
            File mods = new File(gameDir, "mods");
            File[] files = mods.listFiles();
            if (files != null) {
                for (File file : files) {
                    String name = file.getName().toLowerCase(java.util.Locale.ROOT);
                    if (name.contains("oneconfig") && name.endsWith(".jar")) {
                        return true;
                    }
                }
            }
            return new File(gameDir, "OneConfig").isDirectory();
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean patch(File prefs) {
        try {
            JsonObject root;
            if (prefs.isFile()) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(new FileInputStream(prefs), "UTF-8"))) {
                    JsonElement parsed = new JsonParser().parse(reader);
                    root = parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
                } catch (Exception e) {
                    return false;
                }
            } else {
                File parent = prefs.getParentFile();
                if (parent != null) {
                    parent.mkdirs();
                }
                root = new JsonObject();
            }
            JsonElement existing = root.get("oneConfigKeyBind");
            if (existing != null && existing.isJsonObject()) {
                JsonElement binds = existing.getAsJsonObject().get("keyBinds");
                if (binds != null && binds.isJsonArray() && binds.getAsJsonArray().size() == 1
                        && binds.getAsJsonArray().get(0).getAsInt() == KEY_O) {
                    return true;
                }
            }
            JsonObject bind = new JsonObject();
            JsonArray binds = new JsonArray();
            binds.add(new com.google.gson.JsonPrimitive(KEY_O));
            bind.add("keyBinds", binds);
            root.add("oneConfigKeyBind", bind);
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(prefs), "UTF-8"))) {
                writer.write(root.toString());
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static int readCount(File counter) {
        try {
            if (!counter.isFile()) {
                return 0;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(counter), "UTF-8"))) {
                return Math.max(0, Integer.parseInt(reader.readLine().trim()));
            }
        } catch (Exception e) {
            return 0;
        }
    }

    private static void writeCount(File counter, int count) {
        try {
            File parent = counter.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(counter), "UTF-8"))) {
                writer.write(String.valueOf(count));
            }
        } catch (Exception ignored) {
        }
    }
}
