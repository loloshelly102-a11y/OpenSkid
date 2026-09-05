package openskid.altmanager;

// Adapted for OpenSkid. Structure inspired by Raven S+ Alt (donor, read-only).
// Rewritten here. No donor code pasted.
public enum AltType {
    CRACKED,
    MICROSOFT;

    public static AltType fromString(String s) {
        if (s == null) {
            return CRACKED;
        }
        try {
            return valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            if (s.equalsIgnoreCase("cracked") || s.equalsIgnoreCase("offline") || s.equalsIgnoreCase("legacy")) {
                return CRACKED;
            }
            return MICROSOFT;
        }
    }
}
