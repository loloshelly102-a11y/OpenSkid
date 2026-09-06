package openskid.setup;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class SetupScanner {
    private SetupScanner() {
    }

    public static File gameDir() {
        try {
            File dir = net.minecraft.client.Minecraft.getMinecraft().mcDataDir;
            if (dir != null) {
                return dir;
            }
        } catch (Exception ignored) {
        }
        return new File(".");
    }

    public static File modsDir() {
        File dir = new File(gameDir(), "mods");
        dir.mkdirs();
        return dir;
    }

    public static File packsDir() {
        File dir = new File(gameDir(), "resourcepacks");
        dir.mkdirs();
        return dir;
    }

    public static File targetDir(SetupEntry entry) {
        return entry.section == SetupEntry.Section.MODS ? modsDir() : packsDir();
    }

    public static boolean isInstalled(SetupEntry entry) {
        try {
            File dir = targetDir(entry);
            File[] files = dir.listFiles();
            if (files == null) {
                return false;
            }
            String want = entry.fileName.toLowerCase(Locale.ROOT);
            for (File file : files) {
                String name = file.getName().toLowerCase(Locale.ROOT);
                if (name.equals(want)) {
                    return true;
                }
                if (isNumberedDuplicate(name, want)) {
                    return true;
                }
            }
            if (entry.section == SetupEntry.Section.PACKS) {
                return matchesPackDescription(files, entry);
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static boolean isNumberedDuplicate(String name, String want) {
        if (!want.endsWith(".jar") || !name.endsWith(".jar")) {
            return false;
        }
        String base = want.substring(0, want.length() - 4);
        if (!name.startsWith(base)) {
            return false;
        }
        String rest = name.substring(base.length(), name.length() - 4).trim();
        return rest.matches("\\(\\d+\\)");
    }

    private static boolean matchesPackDescription(File[] files, SetupEntry entry) {
        String key = SetupEntry.stripColors(entry.name).toLowerCase(Locale.ROOT).trim();
        if (key.isEmpty()) {
            return false;
        }
        for (File file : files) {
            if (!file.isFile() || !file.getName().toLowerCase(Locale.ROOT).endsWith(".zip")) {
                continue;
            }
            String description = readPackDescription(file);
            if (description != null && description.toLowerCase(Locale.ROOT).contains(key)) {
                return true;
            }
        }
        return false;
    }

    private static String readPackDescription(File zip) {
        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(zip);
            ZipEntry meta = zipFile.getEntry("pack.mcmeta");
            if (meta == null) {
                return null;
            }
            InputStream in = zipFile.getInputStream(meta);
            try {
                byte[] buffer = new byte[4096];
                StringBuilder text = new StringBuilder();
                int read;
                while ((read = in.read(buffer)) != -1 && text.length() < 4096) {
                    text.append(new String(buffer, 0, read, "UTF-8"));
                }
                String raw = text.toString();
                int desc = raw.indexOf("\"description\"");
                if (desc < 0) {
                    return null;
                }
                int colon = raw.indexOf(':', desc);
                int firstQuote = raw.indexOf('"', colon);
                int secondQuote = raw.indexOf('"', firstQuote + 1);
                if (colon < 0 || firstQuote < 0 || secondQuote < 0) {
                    return null;
                }
                return raw.substring(firstQuote + 1, secondQuote);
            } finally {
                try {
                    in.close();
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (zipFile != null) {
                try {
                    zipFile.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public static File findDuplicateJar(SetupEntry entry) {
        try {
            if (!entry.fileName.toLowerCase(Locale.ROOT).endsWith(".jar")) {
                return null;
            }
            File dir = targetDir(entry);
            File[] files = dir.listFiles();
            if (files == null) {
                return null;
            }
            String want = entry.fileName.toLowerCase(Locale.ROOT);
            File exact = null;
            File duplicate = null;
            for (File file : files) {
                String name = file.getName().toLowerCase(Locale.ROOT);
                if (name.equals(want)) {
                    exact = file;
                } else if (isNumberedDuplicate(name, want)) {
                    duplicate = file;
                }
            }
            if (exact != null && duplicate != null) {
                return duplicate.lastModified() < exact.lastModified() ? duplicate : exact;
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
