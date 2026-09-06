package openskid.setup;

public final class SetupEntry {
    public enum Section {
        MODS,
        PACKS
    }

    public enum Source {
        DIRECT,
        MEDIAFIRE_SHARE
    }

    public final Section section;
    public final Source source;
    public final String name;
    public final String blurb;
    public final String fileName;
    public final String url;
    public final long expectedBytes;
    public final boolean auto;
    public final boolean matchPrefix;

    public SetupEntry(Section section, Source source, String name, String blurb,
                      String fileName, String url, long expectedBytes, boolean auto) {
        this(section, source, name, blurb, fileName, url, expectedBytes, auto, false);
    }

    public SetupEntry(Section section, Source source, String name, String blurb,
                      String fileName, String url, long expectedBytes, boolean auto, boolean matchPrefix) {
        this.section = section;
        this.source = source;
        this.name = name;
        this.blurb = blurb;
        this.fileName = fileName;
        this.url = url;
        this.expectedBytes = expectedBytes;
        this.auto = auto;
        this.matchPrefix = matchPrefix;
    }

    public static String stripColors(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\u00A7.", "");
    }

    public String displayName() {
        return stripColors(name).trim();
    }
}
