package openskid.altmanager;

// Adapted for OpenSkid. Shape follows donor Alt plus task spec
// (name, uuid, token, type, last-used). Rewritten. No donor code pasted.
public class Alt {
    private String name;
    private String uuid;
    private String token;
    private String refreshToken;
    private AltType type;
    private long lastUsed;
    private boolean banned;
    private boolean locked;

    public Alt() {
        this.type = AltType.CRACKED;
    }

    public Alt(String name, AltType type) {
        this.name = name == null ? "" : name.trim();
        this.type = type == null ? AltType.CRACKED : type;
    }

    public String getName() {
        return name == null ? "" : name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name.trim();
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public boolean hasRefreshToken() {
        return refreshToken != null && !refreshToken.isEmpty();
    }

    public AltType getType() {
        return type == null ? AltType.CRACKED : type;
    }

    public void setType(AltType type) {
        this.type = type == null ? AltType.CRACKED : type;
    }

    public boolean isCracked() {
        return getType() == AltType.CRACKED;
    }

    public long getLastUsed() {
        return lastUsed;
    }

    public void setLastUsed(long lastUsed) {
        this.lastUsed = lastUsed;
    }

    public boolean isBanned() {
        return banned;
    }

    public void setBanned(boolean banned) {
        this.banned = banned;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public boolean isFlagged() {
        return banned || locked;
    }

    public String displayLabel(String currentName) {
        StringBuilder sb = new StringBuilder();
        sb.append(getName());
        sb.append(isCracked() ? " [cracked]" : " [ms]");
        if (isFlagged()) {
            sb.append(isBanned() ? " BANNED" : " LOCKED");
        } else if (currentName != null && currentName.equalsIgnoreCase(getName())) {
            sb.append(" *");
        }
        return sb.toString();
    }
}
