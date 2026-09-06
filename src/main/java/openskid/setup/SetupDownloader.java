package openskid.setup;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SetupDownloader {
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 15000;
    private static final long MAX_BYTES = 128L * 1024L * 1024L;

    private static final Pattern SHARE_BUTTON =
            Pattern.compile("aria-label=\"Download file\"[^>]*href=\"([^\"]+)\"");
    private static final Pattern DIRECT_FALLBACK =
            Pattern.compile("href=\"(https://download\\d+\\.mediafire\\.com/[^\"]+)\"");

    private SetupDownloader() {
    }

    public interface ProgressListener {
        void onProgress(long downloaded, long total);
        void onDone(File file);
        void onError(String message);
    }

    public static boolean hasLink(SetupEntry entry) {
        return entry.url != null && entry.url.toLowerCase(Locale.ROOT).startsWith("http");
    }

    public static void downloadAsync(final SetupEntry entry, final ProgressListener listener) {
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File saved = download(entry, listener);
                    listener.onDone(saved);
                } catch (Exception e) {
                    String message = e.getMessage();
                    listener.onError(message == null || message.isEmpty() ? "download failed" : message);
                }
            }
        }, "OpenSkid-Setup-" + entry.displayName());
        worker.setDaemon(true);
        worker.start();
    }

    private static File download(SetupEntry entry, ProgressListener listener) throws Exception {
        if (!hasLink(entry)) {
            throw new Exception("link missing, coming soon");
        }
        String direct = resolveDirectUrl(entry);
        File dir = SetupScanner.targetDir(entry);
        File temp = new File(dir, entry.fileName + ".download");
        File dest = new File(dir, entry.fileName);
        if (temp.exists()) {
            temp.delete();
        }
        URLConnection connection = new URL(direct).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", "OpenSkid");
        connection.setUseCaches(false);
        connection.connect();
        long total = connection.getContentLengthLong();
        if (entry.expectedBytes > 0) {
            total = entry.expectedBytes;
        }
        InputStream in = connection.getInputStream();
        FileOutputStream out = new FileOutputStream(temp);
        try {
            byte[] buffer = new byte[32768];
            long downloaded = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                downloaded += read;
                if (downloaded > MAX_BYTES) {
                    throw new Exception("file too large (over 128MB)");
                }
                out.write(buffer, 0, read);
                listener.onProgress(downloaded, total);
            }
        } finally {
            try {
                in.close();
            } catch (Exception ignored) {
            }
            try {
                out.close();
            } catch (Exception ignored) {
            }
        }
        if (entry.expectedBytes > 0 && temp.length() != entry.expectedBytes) {
            temp.delete();
            throw new Exception("size mismatch, try again");
        }
        if (dest.exists()) {
            dest.delete();
        }
        if (!temp.renameTo(dest)) {
            temp.delete();
            throw new Exception("could not save file");
        }
        return dest;
    }

    static String resolveDirectUrl(SetupEntry entry) throws Exception {
        if (entry.source == SetupEntry.Source.DIRECT) {
            return entry.url;
        }
        String page = fetchText(entry.url);
        Matcher button = SHARE_BUTTON.matcher(page);
        if (button.find()) {
            return unescape(button.group(1));
        }
        Matcher fallback = DIRECT_FALLBACK.matcher(page);
        if (fallback.find()) {
            return unescape(fallback.group(1));
        }
        throw new Exception("could not resolve download link");
    }

    private static String fetchText(String url) throws Exception {
        URLConnection connection = new URL(url).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", "OpenSkid");
        connection.setUseCaches(false);
        connection.connect();
        InputStream in = connection.getInputStream();
        try {
            byte[] buffer = new byte[8192];
            StringBuilder text = new StringBuilder();
            int read;
            while ((read = in.read(buffer)) != -1 && text.length() < 1024 * 1024) {
                text.append(new String(buffer, 0, read, "UTF-8"));
            }
            return text.toString();
        } finally {
            try {
                in.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static String unescape(String url) {
        return url.replace("&amp;", "&");
    }
}
