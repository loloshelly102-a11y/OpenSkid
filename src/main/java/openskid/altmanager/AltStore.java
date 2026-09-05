package openskid.altmanager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Adapted for OpenSkid. Persistence idea follows donor AltJsonHandler
// (alts.json via Gson). Rewritten with tolerant load. No donor code pasted.
// Secrets stay in this local file only. Never sent anywhere except the
// Microsoft auth endpoints during an explicit user login.
public final class AltStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final JsonParser PARSER = new JsonParser();
    private static final File FILE = new File("./config/OpenSkid/alts.json");
    private static final List<Alt> ALTS = new ArrayList<Alt>();

    private AltStore() {
    }

    public static List<Alt> getAlts() {
        return ALTS;
    }

    public static List<Alt> snapshot() {
        return Collections.unmodifiableList(new ArrayList<Alt>(ALTS));
    }

    private static String text(JsonObject o, String key) {
        try {
            if (o.has(key) && !o.get(key).isJsonNull()) {
                return o.get(key).getAsString();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static synchronized void load() {
        ALTS.clear();
        try {
            if (!FILE.exists()) {
                return;
            }
            BufferedReader reader = new BufferedReader(new FileReader(FILE));
            JsonElement parsed;
            try {
                parsed = PARSER.parse(reader);
            } finally {
                try {
                    reader.close();
                } catch (Exception ignored) {
                }
            }
            if (parsed == null || !parsed.isJsonObject()) {
                return;
            }
            JsonObject root = parsed.getAsJsonObject();
            JsonElement arr = root.has("alts") ? root.get("alts") : root.get("accounts");
            if (arr == null || !arr.isJsonArray()) {
                return;
            }
            JsonArray list = arr.getAsJsonArray();
            for (JsonElement e : list) {
                try {
                    if (e == null || !e.isJsonObject()) {
                        continue;
                    }
                    JsonObject o = e.getAsJsonObject();
                    String name = text(o, "name");
                    if (name == null || name.trim().isEmpty()) {
                        continue;
                    }
                    Alt alt = new Alt(name.trim(), AltType.fromString(text(o, "type")));
                    if (o.has("cracked") && o.get("cracked").isJsonPrimitive()) {
                        try {
                            if (o.get("cracked").getAsBoolean()) {
                                alt.setType(AltType.CRACKED);
                            }
                        } catch (Exception ignored) {
                        }
                    }
                    alt.setUuid(text(o, "uuid"));
                    alt.setToken(deobfuscate(text(o, "token")));
                    alt.setRefreshToken(deobfuscate(text(o, "refreshToken")));
                    if (alt.getRefreshToken() == null) {
                        alt.setRefreshToken(text(o, "refresh_token"));
                    }
                    try {
                        if (o.has("lastUsed") && !o.get("lastUsed").isJsonNull()) {
                            alt.setLastUsed(o.get("lastUsed").getAsLong());
                        } else if (o.has("last_used") && !o.get("last_used").isJsonNull()) {
                            alt.setLastUsed(o.get("last_used").getAsLong());
                        }
                    } catch (Exception ignored) {
                    }
                    try {
                        if (o.has("banned") && o.get("banned").isJsonPrimitive()) {
                            alt.setBanned(o.get("banned").getAsBoolean());
                        }
                    } catch (Exception ignored) {
                    }
                    try {
                        if (o.has("locked") && o.get("locked").isJsonPrimitive()) {
                            alt.setLocked(o.get("locked").getAsBoolean());
                        }
                    } catch (Exception ignored) {
                    }
                    if (BanTracker.isFlagged(alt.getName())) {
                        BanTracker.applyTo(alt);
                    }
                    ALTS.add(alt);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static synchronized void save() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            JsonObject root = new JsonObject();
            JsonArray list = new JsonArray();
            for (Alt alt : ALTS) {
                try {
                    JsonObject o = new JsonObject();
                    o.addProperty("name", alt.getName());
                    o.addProperty("type", alt.getType().name());
                    if (alt.getUuid() != null) {
                        o.addProperty("uuid", alt.getUuid());
                    }
                    if (alt.getToken() != null) {
                        o.addProperty("token", obfuscate(alt.getToken()));
                    }
                    if (alt.getRefreshToken() != null) {
                        o.addProperty("refreshToken", obfuscate(alt.getRefreshToken()));
                    }
                    o.addProperty("lastUsed", alt.getLastUsed());
                    o.addProperty("banned", alt.isBanned());
                    o.addProperty("locked", alt.isLocked());
                    list.add(o);
                } catch (Exception ignored) {
                }
            }
            root.add("alts", list);
            PrintWriter out = new PrintWriter(new FileWriter(FILE));
            try {
                out.println(GSON.toJson(root));
            } finally {
                out.close();
            }
        } catch (Exception ignored) {
        }
    }

    private static final byte[] OBFUSCATION_KEY = "OpenSkidAltStore-v1".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    // Plain XOR obfuscation, not encryption. It keeps tokens out of casual
    // log pastes and shoulder surfing, nothing more. Values that fail to
    // decode are returned as-is so old plaintext files keep loading.
    private static String obfuscate(String plain) {
        if (plain == null) {
            return null;
        }
        byte[] bytes = plain.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (int i = 0; i < bytes.length; i++) {
            int b = (bytes[i] ^ OBFUSCATION_KEY[i % OBFUSCATION_KEY.length]) & 0xFF;
            if (b < 16) {
                out.append('0');
            }
            out.append(Integer.toHexString(b));
        }
        return out.toString();
    }

    private static String deobfuscate(String stored) {
        if (stored == null) {
            return null;
        }
        try {
            if ((stored.length() & 1) != 0) {
                return stored;
            }
            byte[] bytes = new byte[stored.length() / 2];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) Integer.parseInt(stored.substring(i * 2, i * 2 + 2), 16);
            }
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] ^= OBFUSCATION_KEY[i % OBFUSCATION_KEY.length];
            }
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return stored;
        }
    }

    public static synchronized Alt addCracked(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        String clean = name.trim();
        for (Alt a : ALTS) {
            if (a.isCracked() && a.getName().equalsIgnoreCase(clean)) {
                return null;
            }
        }
        Alt alt = new Alt(clean, AltType.CRACKED);
        BanTracker.applyTo(alt);
        ALTS.add(alt);
        save();
        return alt;
    }

    public static synchronized Alt upsertMicrosoft(String name, String uuid, String mcToken, String refreshToken) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        String clean = name.trim();
        Alt found = null;
        for (Alt a : ALTS) {
            if (!a.isCracked() && a.getName().equalsIgnoreCase(clean)) {
                found = a;
                break;
            }
        }
        if (found == null && uuid != null) {
            for (Alt a : ALTS) {
                if (!a.isCracked() && uuid.equalsIgnoreCase(a.getUuid())) {
                    found = a;
                    break;
                }
            }
        }
        if (found == null) {
            found = new Alt(clean, AltType.MICROSOFT);
            ALTS.add(found);
        }
        found.setName(clean);
        found.setType(AltType.MICROSOFT);
        if (uuid != null && !uuid.isEmpty()) {
            found.setUuid(uuid);
        }
        if (mcToken != null && !mcToken.isEmpty()) {
            found.setToken(mcToken);
        }
        if (refreshToken != null && !refreshToken.isEmpty()) {
            found.setRefreshToken(refreshToken);
        }
        BanTracker.applyTo(found);
        save();
        return found;
    }

    public static synchronized boolean remove(Alt alt) {
        boolean ok = ALTS.remove(alt);
        if (ok) {
            save();
        }
        return ok;
    }

    public static synchronized void touchUsed(Alt alt) {
        if (alt != null) {
            alt.setLastUsed(System.currentTimeMillis());
            save();
        }
    }
}
