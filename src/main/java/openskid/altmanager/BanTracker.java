package openskid.altmanager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// Adapted for OpenSkid. Idea follows donor BanDetectionHandler
// (flag banned accounts, persist the flag). Rewritten as a plain static
// tracker so no Mixin or event-bus edit is needed. Never auto-retries.
public final class BanTracker {
    private static final Set<String> BANNED = ConcurrentHashMap.newKeySet();
    private static final Set<String> LOCKED = ConcurrentHashMap.newKeySet();

    private BanTracker() {
    }

    private static String key(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    public static void markBanned(String name) {
        String k = key(name);
        if (!k.isEmpty()) {
            BANNED.add(k);
        }
        for (Alt a : new ArrayList<Alt>(AltStore.getAlts())) {
            if (a.getName() != null && key(a.getName()).equals(k)) {
                a.setBanned(true);
            }
        }
        AltStore.save();
    }

    public static void markLocked(String name) {
        String k = key(name);
        if (!k.isEmpty()) {
            LOCKED.add(k);
        }
        for (Alt a : new ArrayList<Alt>(AltStore.getAlts())) {
            if (a.getName() != null && key(a.getName()).equals(k)) {
                a.setLocked(true);
            }
        }
        AltStore.save();
    }

    public static boolean isBanned(String name) {
        return BANNED.contains(key(name));
    }

    public static boolean isLocked(String name) {
        return LOCKED.contains(key(name));
    }

    public static boolean isFlagged(String name) {
        String k = key(name);
        return BANNED.contains(k) || LOCKED.contains(k);
    }

    public static void applyTo(Alt alt) {
        if (alt == null) {
            return;
        }
        String k = key(alt.getName());
        if (BANNED.contains(k)) {
            alt.setBanned(true);
        }
        if (LOCKED.contains(k)) {
            alt.setLocked(true);
        }
        if (alt.isBanned()) {
            BANNED.add(k);
        }
        if (alt.isLocked()) {
            LOCKED.add(k);
        }
    }

    public static Set<String> bannedView() {
        return Collections.unmodifiableSet(new java.util.HashSet<String>(BANNED));
    }

    public static boolean shouldSkip(Alt alt) {
        if (alt == null) {
            return true;
        }
        applyTo(alt);
        return alt.isFlagged();
    }

    // Called once per explicit login failure. Returns true when the message
    // names a ban or lock. Plain bad passwords stay unflagged.
    public static boolean noteLoginFailure(String name, String error) {
        if (error == null) {
            return false;
        }
        String e = error.toLowerCase(Locale.ROOT);
        boolean ban = e.contains("ban") || e.contains("suspend");
        boolean lock = e.contains("lock") || e.contains("challenge") || e.contains("unauthorised device")
                || e.contains("unauthorized device") || e.contains("account locked")
                || e.contains("requires verification") || e.contains("confirm your identity");
        if (ban) {
            markBanned(name);
            return true;
        }
        if (lock) {
            markLocked(name);
            return true;
        }
        return false;
    }
}
