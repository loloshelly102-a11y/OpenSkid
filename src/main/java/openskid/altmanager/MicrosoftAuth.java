package openskid.altmanager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import java.net.URL;

// Adapted for OpenSkid. Flow follows the donor MicrosoftAuthenticator
// chain (MS token, XboxLive, XSTS, Minecraft login_with_xbox, entitlements,
// profile) but uses device-code OAuth plus plain HttpsURLConnection and Gson
// only. Rewritten. No donor code pasted. No MSAL dependency.
// Network use is limited to the Microsoft/Xbox/Minecraft endpoints below.
public final class MicrosoftAuth {
    public static final String DEVICE_CODE_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
    public static final String TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    public static final String XBL_URL = "https://user.auth.xboxlive.com/user/authenticate";
    public static final String XSTS_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
    public static final String MC_LOGIN_URL = "https://api.minecraftservices.com/authentication/login_with_xbox";
    public static final String MC_STORE_URL = "https://api.minecraftservices.com/entitlements/mcstore";
    public static final String MC_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";

    // Public client id used by Minecraft launchers for this flow.
    private static final String CLIENT_ID = "000000004C12AE6F";
    private static final String DEVICE_SCOPE = "XboxLive.signin offline_access";
    private static final JsonParser PARSER = new JsonParser();

    private MicrosoftAuth() {
    }

    public static final class AuthException extends Exception {
        public AuthException(String message) {
            super(message);
        }

        public AuthException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static final class DeviceCode {
        public final String deviceCode;
        public final String userCode;
        public final String verificationUri;
        public final int intervalSec;
        public final int expiresInSec;

        public DeviceCode(String deviceCode, String userCode, String verificationUri, int intervalSec, int expiresInSec) {
            this.deviceCode = deviceCode;
            this.userCode = userCode;
            this.verificationUri = verificationUri;
            this.intervalSec = intervalSec <= 0 ? 5 : intervalSec;
            this.expiresInSec = expiresInSec <= 0 ? 900 : expiresInSec;
        }
    }

    public static final class Result {
        public final String username;
        public final String uuid;
        public final String mcToken;
        public final String refreshToken;

        public Result(String username, String uuid, String mcToken, String refreshToken) {
            this.username = username;
            this.uuid = uuid;
            this.mcToken = mcToken;
            this.refreshToken = refreshToken;
        }
    }

    public static DeviceCode requestDeviceCode() throws AuthException {
        Map<String, String> params = new HashMap<String, String>();
        params.put("client_id", CLIENT_ID);
        params.put("scope", DEVICE_SCOPE);
        String body = postForm(DEVICE_CODE_URL, params);
        JsonObject o = parse(body);
        String deviceCode = str(o, "device_code");
        String userCode = str(o, "user_code");
        String uri = str(o, "verification_uri");
        if (uri == null) {
            uri = str(o, "verification_url");
        }
        if (deviceCode == null || userCode == null || uri == null) {
            throw new AuthException("Microsoft did not return a device code. Try again later.");
        }
        int interval = num(o, "interval", 5);
        int expires = num(o, "expires_in", 900);
        return new DeviceCode(deviceCode, userCode, uri, interval, expires);
    }

    // Single bounded poll for one explicit user login. Not a relogin loop.
    public static TokenPair pollForToken(DeviceCode dc) throws AuthException {
        long deadline = System.currentTimeMillis() + (dc.expiresInSec * 1000L);
        int intervalMs = dc.intervalSec * 1000;
        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AuthException("Microsoft login was cancelled.");
            }
            Map<String, String> params = new HashMap<String, String>();
            params.put("client_id", CLIENT_ID);
            params.put("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
            params.put("device_code", dc.deviceCode);
            String body;
            try {
                body = postForm(TOKEN_URL, params);
            } catch (AuthException e) {
                String msg = e.getMessage() == null ? "" : e.getMessage();
                if (msg.contains("authorization_pending")) {
                    continue;
                }
                if (msg.contains("slow_down")) {
                    intervalMs += 5000;
                    continue;
                }
                if (msg.contains("expired_token")) {
                    throw new AuthException("The device code expired. Start the Microsoft login again.");
                }
                if (msg.contains("access_denied")) {
                    throw new AuthException("Microsoft login was denied in the browser.");
                }
                throw e;
            }
            JsonObject o = parse(body);
            String access = str(o, "access_token");
            String refresh = str(o, "refresh_token");
            if (access == null || access.isEmpty()) {
                throw new AuthException("Microsoft returned an empty token. Try again.");
            }
            return new TokenPair(access, refresh);
        }
        throw new AuthException("The device code expired. Start the Microsoft login again.");
    }

    public static Result loginWithDeviceCode(DeviceCode dc) throws AuthException {
        TokenPair pair = pollForToken(dc);
        return loginWithMicrosoftToken(pair.accessToken, pair.refreshToken);
    }

    public static Result loginWithRefreshToken(String refreshToken) throws AuthException {
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            throw new AuthException("Saved Microsoft login is empty. Do a fresh Microsoft login.");
        }
        Map<String, String> params = new HashMap<String, String>();
        params.put("client_id", CLIENT_ID);
        params.put("scope", DEVICE_SCOPE);
        params.put("grant_type", "refresh_token");
        params.put("refresh_token", refreshToken.trim());
        String body;
        try {
            body = postForm(TOKEN_URL, params);
        } catch (AuthException e) {
            throw new AuthException("Saved Microsoft login expired or was revoked. Do a fresh login. (" + shortMsg(e) + ")");
        }
        JsonObject o = parse(body);
        String access = str(o, "access_token");
        String refresh = str(o, "refresh_token");
        if (access == null || access.isEmpty()) {
            throw new AuthException("Saved Microsoft login expired or was revoked. Do a fresh login.");
        }
        if (refresh == null || refresh.isEmpty()) {
            refresh = refreshToken;
        }
        return loginWithMicrosoftToken(access, refresh);
    }

    public static final class TokenPair {
        public final String accessToken;
        public final String refreshToken;

        public TokenPair(String accessToken, String refreshToken) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
        }
    }

    private static Result loginWithMicrosoftToken(String msToken, String refreshToken) throws AuthException {
        String xblToken = xboxLiveLogin(msToken);
        String[] xsts = xstsLogin(xblToken);
        String mcToken = minecraftLogin(xsts[0], xsts[1]);
        checkStore(mcToken);
        return minecraftProfile(mcToken, refreshToken);
    }

    private static String xboxLiveLogin(String msToken) throws AuthException {
        JsonObject props = new JsonObject();
        props.addProperty("AuthMethod", "RPS");
        props.addProperty("SiteName", "user.auth.xboxlive.com");
        props.addProperty("RpsTicket", "d=" + msToken);
        JsonObject req = new JsonObject();
        req.add("Properties", props);
        req.addProperty("RelyingParty", "http://auth.xboxlive.com");
        req.addProperty("TokenType", "JWT");
        String body;
        try {
            body = postJson(XBL_URL, req.toString());
        } catch (AuthException e) {
            throw new AuthException("Xbox Live rejected the Microsoft token. " + shortMsg(e));
        }
        String token = str(parse(body), "Token");
        if (token == null || token.isEmpty()) {
            throw new AuthException("Xbox Live returned an empty token.");
        }
        return token;
    }

    // Returns {userHash, xstsToken}.
    private static String[] xstsLogin(String xblToken) throws AuthException {
        JsonObject props = new JsonObject();
        props.addProperty("SandboxId", "RETAIL");
        JsonArray arr = new JsonArray();
        arr.add(new com.google.gson.JsonPrimitive(xblToken));
        props.add("UserTokens", arr);
        JsonObject req = new JsonObject();
        req.add("Properties", props);
        req.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        req.addProperty("TokenType", "JWT");
        String body;
        try {
            body = postJson(XSTS_URL, req.toString());
        } catch (AuthException e) {
            String m = e.getMessage() == null ? "" : e.getMessage();
            if (m.contains("2148916233")) {
                throw new AuthException("This Microsoft account has no Xbox profile. Create one at xbox.com, then retry.");
            }
            if (m.contains("2148916238")) {
                throw new AuthException("This account is banned or child-blocked on Xbox. It is flagged and will not auto-retry.");
            }
            throw new AuthException("Xbox auth (XSTS) failed. " + shortMsg(e));
        }
        JsonObject o = parse(body);
        String token = str(o, "Token");
        String hash = null;
        try {
            JsonObject claims = o.getAsJsonObject("DisplayClaims");
            JsonArray users = claims.getAsJsonArray("xui");
            JsonObject first = users.get(0).getAsJsonObject();
            hash = str(first, "uhs");
        } catch (Exception ignored) {
        }
        if (token == null || hash == null) {
            throw new AuthException("Xbox auth returned an incomplete response.");
        }
        return new String[] {hash, token};
    }

    private static String minecraftLogin(String userHash, String xstsToken) throws AuthException {
        JsonObject req = new JsonObject();
        req.addProperty("identityToken", "XBL3.0 x=" + userHash + ";" + xstsToken);
        String body;
        try {
            body = postJson(MC_LOGIN_URL, req.toString());
        } catch (AuthException e) {
            throw new AuthException("Minecraft rejected the Xbox token. " + shortMsg(e));
        }
        String token = str(parse(body), "access_token");
        if (token == null || token.isEmpty()) {
            throw new AuthException("Minecraft returned an empty access token.");
        }
        return token;
    }

    private static void checkStore(String mcToken) throws AuthException {
        String body;
        try {
            body = getAuthed(MC_STORE_URL, mcToken);
        } catch (AuthException e) {
            throw new AuthException("Could not check game ownership. " + shortMsg(e));
        }
        try {
            JsonObject o = parse(body);
            JsonArray items = o.getAsJsonArray("items");
            if (items != null) {
                for (JsonElement e : items) {
                    if (e.isJsonObject() && "game_minecraft".equals(str(e.getAsJsonObject(), "name"))) {
                        return;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        throw new AuthException("This account owns no Minecraft Java Edition license (or it is not migrated).");
    }

    private static Result minecraftProfile(String mcToken, String refreshToken) throws AuthException {
        String body;
        try {
            body = getAuthed(MC_PROFILE_URL, mcToken);
        } catch (AuthException e) {
            String m = e.getMessage() == null ? "" : e.getMessage();
            if (m.contains("404") || m.contains("NOT_FOUND") || m.contains("null")) {
                throw new AuthException("This account has no Minecraft Java profile name yet. Set one at minecraft.net.");
            }
            throw new AuthException("Could not read the Minecraft profile. " + shortMsg(e));
        }
        JsonObject o = parse(body);
        String name = str(o, "name");
        String id = str(o, "id");
        String err = str(o, "error");
        if (name == null || id == null) {
            if (err != null) {
                throw new AuthException("Minecraft profile error: " + err + ". Set a profile name at minecraft.net.");
            }
            throw new AuthException("Minecraft profile is missing. Set a profile name at minecraft.net.");
        }
        return new Result(name, id, mcToken, refreshToken);
    }

    private static String postForm(String url, Map<String, String> params) throws AuthException {
        StringBuilder sb = new StringBuilder();
        try {
            for (Map.Entry<String, String> e : params.entrySet()) {
                if (sb.length() > 0) {
                    sb.append('&');
                }
                sb.append(URLEncoder.encode(e.getKey(), "UTF-8"));
                sb.append('=');
                sb.append(URLEncoder.encode(e.getValue(), "UTF-8"));
            }
        } catch (Exception e) {
            throw new AuthException("Failed to encode the login request.", e);
        }
        return http("POST", url, "application/x-www-form-urlencoded", null, sb.toString());
    }

    private static String postJson(String url, String json) throws AuthException {
        return http("POST", url, "application/json", "application/json", json);
    }

    private static String getAuthed(String url, String bearer) throws AuthException {
        return http("GET", url, null, "application/json", null, bearer);
    }

    private static String http(String method, String url, String contentType, String accept, String body) throws AuthException {
        return http(method, url, contentType, accept, body, null);
    }

    private static String http(String method, String url, String contentType, String accept, String body, String bearer) throws AuthException {
        HttpsURLConnection con = null;
        try {
            con = (HttpsURLConnection) new URL(url).openConnection();
            con.setConnectTimeout(15000);
            con.setReadTimeout(15000);
            con.setRequestMethod(method);
            con.setRequestProperty("User-Agent", "OpenSkid/1.0");
            if (contentType != null) {
                con.setRequestProperty("Content-Type", contentType);
            }
            if (accept != null) {
                con.setRequestProperty("Accept", accept);
            }
            if (bearer != null) {
                con.setRequestProperty("Authorization", "Bearer " + bearer);
            }
            if (body != null) {
                con.setDoOutput(true);
                byte[] bytes = body.getBytes("UTF-8");
                con.setRequestProperty("Content-Length", Integer.toString(bytes.length));
                OutputStream out = con.getOutputStream();
                try {
                    out.write(bytes);
                } finally {
                    out.close();
                }
            }
            int code = con.getResponseCode();
            InputStream in = code >= 200 && code < 300 ? con.getInputStream() : con.getErrorStream();
            if (in == null) {
                throw new AuthException("HTTP " + code + " from " + hostOf(url) + " with an empty response.");
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            } finally {
                reader.close();
            }
            String text = sb.toString();
            if (code >= 200 && code < 300) {
                return text;
            }
            String snippet = text.length() > 300 ? text.substring(0, 300) : text;
            throw new AuthException("HTTP " + code + " from " + hostOf(url) + ": " + snippet);
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthException("Network error contacting " + hostOf(url) + ". Check your connection.", e);
        } finally {
            if (con != null) {
                con.disconnect();
            }
        }
    }

    private static JsonObject parse(String body) throws AuthException {
        try {
            return PARSER.parse(body).getAsJsonObject();
        } catch (Exception e) {
            throw new AuthException("Login server returned an unreadable response. Try again.");
        }
    }

    private static String str(JsonObject o, String key) {
        try {
            if (o != null && o.has(key) && !o.get(key).isJsonNull()) {
                return o.get(key).getAsString();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static int num(JsonObject o, String key, int fallback) {
        try {
            if (o != null && o.has(key) && !o.get(key).isJsonNull()) {
                return o.get(key).getAsInt();
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private static String hostOf(String url) {
        try {
            return new URL(url).getHost();
        } catch (Exception e) {
            return "login server";
        }
    }

    private static String shortMsg(AuthException e) {
        String m = e.getMessage();
        if (m == null) {
            return "unknown error";
        }
        return m.length() > 220 ? m.substring(0, 220) : m;
    }
}
