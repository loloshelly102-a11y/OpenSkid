package openskid.altmanager;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

// Adapted for OpenSkid. Session swap mirrors the working pattern in
// this repo (me.ksyz.accountmanager.auth.SessionManager plus donor
// SessionUtil): find the Session-typed field on Minecraft and set it.
// Rewritten. No donor code pasted.
public final class SessionSwap {
    private static Field sessionField;

    private SessionSwap() {
    }

    private static synchronized Field field() {
        if (sessionField == null) {
            try {
                for (Field f : Minecraft.class.getDeclaredFields()) {
                    if (f.getType().isAssignableFrom(Session.class)) {
                        f.setAccessible(true);
                        sessionField = f;
                        break;
                    }
                }
            } catch (Exception e) {
                sessionField = null;
            }
        }
        return sessionField;
    }

    public static boolean set(Session session) {
        if (session == null) {
            return false;
        }
        try {
            Field f = field();
            if (f == null) {
                return false;
            }
            f.set(Minecraft.getMinecraft(), session);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean setCracked(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        String clean = name.trim();
        String uuid = UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + clean).getBytes(StandardCharsets.UTF_8)
        ).toString().replace("-", "");
        return set(new Session(clean, uuid, "0", "legacy"));
    }

    public static boolean setMicrosoft(String name, String uuid, String mcToken) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        String id = (uuid == null || uuid.isEmpty()) ? name : uuid.replace("-", "");
        String tok = (mcToken == null || mcToken.isEmpty()) ? "0" : mcToken;
        return set(new Session(name, id, tok, "mojang"));
    }

    public static String currentName() {
        try {
            Session s = Minecraft.getMinecraft().getSession();
            if (s != null) {
                return s.getUsername();
            }
        } catch (Exception ignored) {
        }
        return "";
    }
}
