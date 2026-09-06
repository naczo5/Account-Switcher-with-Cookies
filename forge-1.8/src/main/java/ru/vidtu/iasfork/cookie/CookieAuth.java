package ru.vidtu.iasfork.cookie;

import com.google.gson.JsonObject;
import ru.vidtu.iasfork.msauth.AuthSys;
import ru.vidtu.iasfork.msauth.GetRequest;
import ru.vidtu.iasfork.msauth.PostRequest;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Cookie and Localts authentication for 1.8.9.
 */
public final class CookieAuth {
    private static final String SISU_AUTH_URL = "https://sisu.xboxlive.com/connect/XboxLive/?state=login&cobrandId=8058f65d-ce06-4c30-9559-473c9275a65d&tid=896928775&ru=https%3A%2F%2Fwww.minecraft.net%2Fen-us%2Flogin&aid=1142970254";
    private static final String COOKIE_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:146.0) Gecko/20100101 Firefox/146.0";
    private static final String MINECRAFT_OAUTH_CLIENT_ID = "00000000402b5328";
    private static final String MINECRAFT_OAUTH_SCOPE = "service::user.auth.xboxlive.com::MBI_SSL";

    private CookieAuth() {
    }

    public static MinecraftProfile authenticate(CookieParser.ParsedCookies cookies) throws Exception {
        if (cookies.refreshToken() != null && !cookies.refreshToken().trim().isEmpty()) {
            return profileFromRefreshToken(cookies.refreshToken().trim());
        }
        // Send only login.live.com-scoped cookies (browser parity). The old
        // toCookieHeader() leaked unrelated domains and broke auth.
        String sisuHeader;
        try {
            sisuHeader = cookies.toSisuCookieHeader();
        } catch (Throwable noSisu) {
            sisuHeader = cookies.toCookieHeader();
        }
        try {
            MsaTokens tokens = cookiesToMsa(sisuHeader);
            MinecraftProfile profile = profileFromMsa(tokens.access, "t=");
            return new MinecraftProfile(profile.name, profile.uuid, profile.token, tokens.refresh);
        } catch (Throwable oauthFailed) {
            String mca = cookiesToMcaViaSisu(sisuHeader);
            MinecraftProfile profile = profileFromMca(mca);
            // SISU path yields no refresh token; preserve empty (callers fall back).
            return new MinecraftProfile(profile.name, profile.uuid, profile.token, "");
        }
    }

    /** Refreshes a Localts-backed account and preserves Microsoft's rotated refresh token. */
    public static MinecraftProfile profileFromRefreshToken(String refresh) throws Exception {
        MsaTokens tokens = localtsRefreshToMsa(refresh);
        MinecraftProfile profile = profileFromMsa(tokens.access, "t=");
        return new MinecraftProfile(profile.name, profile.uuid, profile.token, tokens.refresh);
    }

    /**
     * Resolves a previously saved Minecraft services access token.  This also
     * lets installations made before UUID persistence was added keep working.
     */
    public static MinecraftProfile profileFromAccessToken(String token) throws Exception {
        return profileFromMca(token);
    }

    private static MsaTokens cookiesToMsa(String cookieHeader) throws Exception {
        String redirect1 = followSisuRedirect(SISU_AUTH_URL, null);
        String redirect2 = followSisuRedirect(redirect1, cookieHeader);
        String code = extractQueryParam(redirect2, "code");
        if (code == null || code.trim().isEmpty()) {
            throw new CookieAuthException("No authorization code in cookie auth response.", "ias.error.cookie.expired");
        }

        PostRequest pr = new PostRequest("https://login.live.com/oauth20_token.srf")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json");
        Map<Object, Object> data = new HashMap<>();
        // Unified on the launcher client (modern 81a1148 parity); the old
        // 000000004415db7f ID rotated tokens the refresh path could not reuse.
        data.put("client_id", MINECRAFT_OAUTH_CLIENT_ID);
        data.put("code", code);
        data.put("grant_type", "authorization_code");
        data.put("redirect_uri", "https://sisu.xboxlive.com/connect/XboxLive/");
        data.put("scope", MINECRAFT_OAUTH_SCOPE);
        pr.post(data);
        if (pr.response() != 200) {
            throw new CookieAuthException("Cookie authorization code exchange failed.", "ias.error.cookie.expired");
        }
        JsonObject jo = AuthSys.gson().fromJson(pr.body(), JsonObject.class);
        String rotated = jo.has("refresh_token") ? jo.get("refresh_token").getAsString() : "";
        return new MsaTokens(jo.get("access_token").getAsString(), rotated);
    }

    private static String extractQueryParam(String url, String param) {
        if (url == null || param == null || param.isEmpty()) {
            return null;
        }
        // Boundary-safe scan: param must start at ?, &, ;, # or string start.
        // The old indexOf("code=") matched "error_code=".
        String needle = param + "=";
        int from = 0;
        while (true) {
            int idx = url.indexOf(needle, from);
            if (idx < 0) {
                return null;
            }
            boolean boundaryOk = idx == 0;
            if (!boundaryOk && idx > 0) {
                char prev = url.charAt(idx - 1);
                boundaryOk = prev == '?' || prev == '&' || prev == ';' || prev == '#';
            }
            if (!boundaryOk) {
                from = idx + needle.length();
                continue;
            }
            String raw = url.substring(idx + param.length() + 1);
            int amp = raw.indexOf('&');
            if (amp >= 0) {
                raw = raw.substring(0, amp);
            }
            int semi = raw.indexOf(';');
            if (semi >= 0) {
                raw = raw.substring(0, semi);
            }
            int hash = raw.indexOf('#');
            if (hash >= 0) {
                raw = raw.substring(0, hash);
            }
            try {
                return URLDecoder.decode(raw, "UTF-8");
            } catch (Exception e) {
                return raw;
            }
        }
    }

    private static MsaTokens localtsRefreshToMsa(String refresh) throws Exception {
        PostRequest pr = new PostRequest("https://login.live.com/oauth20_token.srf")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json");
        Map<Object, Object> data = new HashMap<>();
        data.put("client_id", MINECRAFT_OAUTH_CLIENT_ID);
        data.put("refresh_token", refresh);
        data.put("grant_type", "refresh_token");
        data.put("redirect_uri", "https://login.live.com/oauth20_desktop.srf");
        data.put("scope", MINECRAFT_OAUTH_SCOPE);
        pr.post(data);
        if (pr.response() != 200) {
            throw new CookieAuthException("Localts refresh token exchange failed.", "ias.error.cookie.expired");
        }
        JsonObject jo = AuthSys.gson().fromJson(pr.body(), JsonObject.class);
        String rotated = jo.has("refresh_token") ? jo.get("refresh_token").getAsString() : refresh;
        return new MsaTokens(jo.get("access_token").getAsString(), rotated);
    }

    private static String cookiesToMcaViaSisu(String cookieHeader) throws Exception {
        String redirect1 = followSisuRedirect(SISU_AUTH_URL, null);
        String redirect2 = followSisuRedirect(redirect1, cookieHeader);
        String redirect3 = followSisuRedirect(redirect2, cookieHeader);
        String encoded = extractSisuAccessToken(redirect3);
        if (encoded == null || encoded.trim().isEmpty()) {
            throw new CookieAuthException("No Xbox access token in SISU cookie auth response.", "ias.error.cookie.expired");
        }
        String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        int rp = decoded.indexOf("\"rp://api.minecraftservices.com/\",");
        if (rp < 0) {
            throw new CookieAuthException("Unexpected SISU token payload.", "ias.error.cookie.expired");
        }
        String slice = decoded.substring(rp);
        int uhsMarker = slice.indexOf("{\"DisplayClaims\":{\"xui\":[{\"uhs\":\"");
        int tokenMarker = slice.indexOf("\"Token\":\"");
        if (uhsMarker < 0 || tokenMarker < 0) {
            throw new CookieAuthException("Unable to parse SISU XSTS payload.", "ias.error.cookie.expired");
        }
        String hash = slice.substring(uhsMarker + "{\"DisplayClaims\":{\"xui\":[{\"uhs\":\"".length());
        hash = hash.substring(0, hash.indexOf('"'));
        String xsts = slice.substring(tokenMarker + "\"Token\":\"".length());
        xsts = xsts.substring(0, xsts.indexOf('"'));
        return xstsToMca(xsts, hash);
    }

    private static String followSisuRedirect(String url, String cookieHeader) throws Exception {
        GetRequest gr = new GetRequest(url.replace(" ", "%20"))
                .header("User-Agent", COOKIE_USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.8");
        if (cookieHeader != null && !cookieHeader.trim().isEmpty()) {
            gr.header("Cookie", cookieHeader);
        }
        gr.get();
        int status = gr.response();
        boolean isRedirect = status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
        if (!isRedirect) {
            // Some SISU steps return 200 with a Location header or meta-refresh.
            String location = gr.location();
            if (location != null && !location.trim().isEmpty()) {
                return location.replace(" ", "%20");
            }
            String body = gr.body();
            if (body != null) {
                String meta = extractMetaRefresh(body);
                if (meta != null && !meta.trim().isEmpty()) {
                    return meta.replace(" ", "%20");
                }
            }
            throw new CookieAuthException("Unexpected SISU redirect status: " + status, "ias.error.cookie.expired");
        }
        String location = gr.location();
        if (location == null || location.trim().isEmpty()) {
            throw new CookieAuthException("SISU redirect missing Location header.", "ias.error.cookie.expired");
        }
        return location.replace(" ", "%20");
    }

    private static String extractMetaRefresh(String body) {
        try {
            String lower = body.toLowerCase();
            int idx = lower.indexOf("http-equiv=\"refresh\"");
            if (idx < 0) {
                idx = lower.indexOf("http-equiv='refresh'");
            }
            if (idx < 0) {
                return null;
            }
            int urlIdx = lower.indexOf("url=", idx);
            if (urlIdx < 0) {
                return null;
            }
            String sub = body.substring(urlIdx + 4).trim();
            if (sub.startsWith("\"") || sub.startsWith("'")) {
                sub = sub.substring(1);
            }
            int end = sub.indexOf('"');
            int sq = sub.indexOf('\'');
            if (end < 0 || (sq >= 0 && sq < end)) {
                end = sq;
            }
            int gt = sub.indexOf('>');
            if (end < 0 || (gt >= 0 && gt < end)) {
                end = gt;
            }
            int sp = sub.indexOf(' ');
            if (sub.startsWith("http") && sp >= 0) {
                // URLs contain no spaces; trailing attributes follow.
                end = sp;
            }
            if (end >= 0) {
                sub = sub.substring(0, end);
            }
            sub = sub.trim();
            if (sub.endsWith(";")) {
                sub = sub.substring(0, sub.length() - 1).trim();
            }
            return sub.isEmpty() ? null : sub;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String extractSisuAccessToken(String url) {
        int idx = url.indexOf("accessToken=");
        if (idx < 0) {
            return null;
        }
        String raw = url.substring(idx + "accessToken=".length());
        int amp = raw.indexOf('&');
        if (amp >= 0) {
            raw = raw.substring(0, amp);
        }
        int hash = raw.indexOf('#');
        if (hash >= 0) {
            raw = raw.substring(0, hash);
        }
        try {
            return URLDecoder.decode(raw, "UTF-8");
        } catch (Exception e) {
            return raw;
        }
    }

    private static String xstsToMca(String xsts, String hash) throws Exception {
        PostRequest pr = new PostRequest("https://api.minecraftservices.com/authentication/login_with_xbox")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");
        Map<Object, Object> map = new HashMap<>();
        map.put("identityToken", "XBL3.0 x=" + hash + ";" + xsts);
        pr.post(AuthSys.gson().toJson(map));
        if (pr.response() == 429) {
            throw new CookieAuthException("Minecraft is rate-limiting authentication.", "ias.error.rateLimited");
        }
        if (pr.response() != 200) {
            throw new CookieAuthException("Minecraft token exchange failed.", "ias.error.cookie.expired");
        }
        JsonObject jo = AuthSys.gson().fromJson(pr.body(), JsonObject.class);
        return jo.get("access_token").getAsString();
    }

    private static MinecraftProfile profileFromMca(String mca) throws Exception {
        GetRequest gr = new GetRequest("https://api.minecraftservices.com/minecraft/profile")
                .header("Authorization", "Bearer " + mca);
        gr.get();
        if (gr.response() == 429) {
            throw new CookieAuthException("Minecraft is rate-limiting authentication.", "ias.error.rateLimited");
        }
        if (gr.response() != 200) {
            throw new CookieAuthException("Minecraft profile lookup failed.", "ias.error.cookie.expired");
        }
        JsonObject jo = AuthSys.gson().fromJson(gr.body(), JsonObject.class);
        return new MinecraftProfile(jo.get("name").getAsString(), jo.get("id").getAsString(), mca);
    }

    private static MinecraftProfile profileFromMsa(String msa, String ticketPrefix) throws Exception {
        String mca = msaToMca(msa, ticketPrefix);
        return profileFromMca(mca);
    }

    private static String msaToMca(String msa, String ticketPrefix) throws Exception {
        PostRequest pr = new PostRequest("https://user.auth.xboxlive.com/user/authenticate")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");
        Map<Object, Object> map = new HashMap<>();
        Map<Object, Object> sub = new HashMap<>();
        sub.put("AuthMethod", "RPS");
        sub.put("SiteName", "user.auth.xboxlive.com");
        sub.put("RpsTicket", ticketPrefix + msa);
        map.put("Properties", sub);
        map.put("RelyingParty", "http://auth.xboxlive.com");
        map.put("TokenType", "JWT");
        pr.post(AuthSys.gson().toJson(map));
        if (pr.response() != 200) {
            throw new CookieAuthException("XBL auth failed.", "ias.error.cookie.expired");
        }
        String xbl = AuthSys.gson().fromJson(pr.body(), JsonObject.class).get("Token").getAsString();

        pr = new PostRequest("https://xsts.auth.xboxlive.com/xsts/authorize")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");
        map = new HashMap<>();
        sub = new HashMap<>();
        sub.put("SandboxId", "RETAIL");
        sub.put("UserTokens", Arrays.asList(xbl));
        map.put("Properties", sub);
        map.put("RelyingParty", "rp://api.minecraftservices.com/");
        map.put("TokenType", "JWT");
        pr.post(AuthSys.gson().toJson(map));
        if (pr.response() == 401) {
            throw new CookieAuthException("This account doesn't have Minecraft account linked.", "ias.msauth.error.noxbox");
        }
        if (pr.response() != 200) {
            throw new CookieAuthException("XSTS auth failed.", "ias.error.cookie.expired");
        }
        JsonObject jo = AuthSys.gson().fromJson(pr.body(), JsonObject.class);
        String hash = jo.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();
        String xsts = jo.get("Token").getAsString();
        return xstsToMca(xsts, hash);
    }

    public static final class MinecraftProfile {
        public final String name;
        public final String uuid;
        public final String token;
        public final String refreshToken;

        public MinecraftProfile(String name, String uuid, String token) {
            this(name, uuid, token, "");
        }

        public MinecraftProfile(String name, String uuid, String token, String refreshToken) {
            this.name = name;
            this.uuid = uuid;
            this.token = token;
            this.refreshToken = refreshToken == null ? "" : refreshToken;
        }
    }

    private static final class MsaTokens {
        private final String access;
        private final String refresh;

        private MsaTokens(String access, String refresh) {
            this.access = access;
            this.refresh = refresh;
        }
    }
}
