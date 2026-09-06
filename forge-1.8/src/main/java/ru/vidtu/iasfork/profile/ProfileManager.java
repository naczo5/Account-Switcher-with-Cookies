package ru.vidtu.iasfork.profile;

import net.minecraft.client.Minecraft;
import the_fireplace.ias.account.ExtendedAccountData;
import ru.vidtu.iasfork.cookie.CookieAuth;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Handles skin upload and IGN changes via Mojang/Minecraft API for 1.8.9.
 */
public final class ProfileManager {
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    private ProfileManager() {}

    public static String getValidToken(ExtendedAccountData data) throws Exception {
        String token = data.cookieAccessToken();
        if (token != null && !token.trim().isEmpty()) {
            if (isTokenValid(token)) {
                return token;
            }
        }
        String refreshToken = data.cookieRefreshToken();
        if (refreshToken != null && !refreshToken.trim().isEmpty()) {
            CookieAuth.MinecraftProfile profile = CookieAuth.profileFromRefreshToken(refreshToken);
            data.updateCookieTokens(profile.token, profile.refreshToken, profile.uuid, profile.name);
            com.github.mrebhan.ingameaccountswitcher.tools.Config.save();
            return profile.token;
        }
        if (Minecraft.getMinecraft().getSession() != null && data.alias.equals(Minecraft.getMinecraft().getSession().getUsername())) {
            return Minecraft.getMinecraft().getSession().getToken();
        }
        throw new IllegalStateException("Selected account does not have a stored Microsoft session or refresh token.");
    }

    private static boolean isTokenValid(String token) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL("https://api.minecraftservices.com/minecraft/profile").openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            return conn.getResponseCode() == 200;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void changeName(String token, String newName) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL("https://api.minecraftservices.com/minecraft/profile/name/" + URLEncoder.encode(newName, "UTF-8")).openConnection();
        conn.setRequestMethod("PUT");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        int code = conn.getResponseCode();
        if (code == 200) {
            return;
        }
        String err = readStream(conn.getErrorStream());
        if (code == 400 || code == 403) {
            throw new IllegalArgumentException("Minecraft username is unavailable or cannot be changed right now.");
        }
        if (code == 429) {
            throw new IllegalStateException("Name change rate-limited. Please wait before trying again.");
        }
        throw new IOException("Failed to change name (HTTP " + code + "): " + err);
    }

    public static void uploadSkin(String token, File skinFile, String variant) throws Exception {
        if (!skinFile.exists()) {
            throw new FileNotFoundException("Skin file not found: " + skinFile.getAbsolutePath());
        }
        byte[] fileBytes;
        try (InputStream in = new FileInputStream(skinFile);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int r;
            while ((r = in.read(buf)) != -1) {
                out.write(buf, 0, r);
            }
            fileBytes = out.toByteArray();
        }

        String boundary = "===IAS" + UUID.randomUUID().toString() + "===";
        HttpURLConnection conn = (HttpURLConnection) new URL("https://api.minecraftservices.com/minecraft/profile/skins").openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);

        try (OutputStream out = conn.getOutputStream();
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), true)) {
            // Variant part
            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"variant\"\r\n\r\n");
            writer.append(variant).append("\r\n");
            writer.flush();

            // File part
            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(skinFile.getName()).append("\"\r\n");
            writer.append("Content-Type: image/png\r\n\r\n");
            writer.flush();
            out.write(fileBytes);
            out.flush();
            writer.append("\r\n");
            writer.append("--").append(boundary).append("--\r\n");
            writer.flush();
        }

        int code = conn.getResponseCode();
        if (code == 200 || code == 204) {
            return;
        }
        String err = readStream(conn.getErrorStream());
        if (code == 429) {
            throw new IllegalStateException("Skin upload rate-limited. Please wait before trying again.");
        }
        throw new IOException("Failed to upload skin (HTTP " + code + "): " + err);
    }

    private static String readStream(InputStream is) {
        if (is == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (Throwable t) {
            return "";
        }
    }
}