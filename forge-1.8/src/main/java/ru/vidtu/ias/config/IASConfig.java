package ru.vidtu.ias.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Hypixel-check settings shared with the Fabric builds.
 * Loaded from {@code .minecraft/config/cookieias/ias.json}.
 */
public final class IASConfig {
    public static String hypixelCheckProxy = "";
    public static String liquidProxyHost = "";
    public static int liquidProxyPort = 1080;
    public static String liquidProxyUsername = "";
    public static String liquidProxyPassword = "";
    public static String liquidProxyType = "socks5";
    public static String liquidProxyRoute = "";
    public static int liquidProxyRoutePort = 25565;
    public static String hypixelApiKey = "";
    public static boolean hypixelCheckAllowRankedDirect = true;
    public static boolean hypixelCheckAllowUnrankedDirect = false;
    public static boolean hypixelCheckAllowUnknownDirect = false;
    public static boolean hypixelCheckAllowNeverJoinedDirect = true;

    private IASConfig() {
    }

    public static void load(File minecraftDir) {
        try {
            File dir = new File(minecraftDir, "config/cookieias");
            if (!dir.isDirectory() && !dir.mkdirs()) {
                return;
            }
            File file = new File(dir, "ias.json");
            File legacy = new File(new File(minecraftDir, "config"), "ias.json");
            if (!file.isFile() && legacy.isFile()) {
                Files.copy(legacy.toPath(), file.toPath());
            }
            if (!file.isFile()) {
                return;
            }
            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            JsonObject json = new JsonParser().parse(text).getAsJsonObject();
            hypixelCheckProxy = readString(json, "hypixelCheckProxy", hypixelCheckProxy);
            liquidProxyHost = readString(json, "liquidProxyHost", liquidProxyHost);
            liquidProxyUsername = readString(json, "liquidProxyUsername", liquidProxyUsername);
            liquidProxyPassword = readString(json, "liquidProxyPassword", liquidProxyPassword);
            liquidProxyType = readString(json, "liquidProxyType", liquidProxyType);
            liquidProxyRoute = readString(json, "liquidProxyRoute", liquidProxyRoute);
            hypixelApiKey = readString(json, "hypixelApiKey", hypixelApiKey);
            if (json.has("liquidProxyPort") && json.get("liquidProxyPort").isJsonPrimitive()) {
                liquidProxyPort = json.get("liquidProxyPort").getAsInt();
            }
            if (json.has("liquidProxyRoutePort") && json.get("liquidProxyRoutePort").isJsonPrimitive()) {
                liquidProxyRoutePort = json.get("liquidProxyRoutePort").getAsInt();
            }
            hypixelCheckAllowRankedDirect = readBool(json, "hypixelCheckAllowRankedDirect", hypixelCheckAllowRankedDirect);
            hypixelCheckAllowUnrankedDirect = readBool(json, "hypixelCheckAllowUnrankedDirect", hypixelCheckAllowUnrankedDirect);
            hypixelCheckAllowUnknownDirect = readBool(json, "hypixelCheckAllowUnknownDirect", hypixelCheckAllowUnknownDirect);
            hypixelCheckAllowNeverJoinedDirect = readBool(json, "hypixelCheckAllowNeverJoinedDirect", hypixelCheckAllowNeverJoinedDirect);
        } catch (Throwable ignored) {
        }
    }

    private static String readString(JsonObject json, String key, String fallback) {
        if (!json.has(key) || json.get(key).isJsonNull() || !json.get(key).isJsonPrimitive()) {
            return fallback;
        }
        return json.get(key).getAsString();
    }

    private static boolean readBool(JsonObject json, String key, boolean fallback) {
        if (!json.has(key) || json.get(key).isJsonNull() || !json.get(key).isJsonPrimitive()) {
            return fallback;
        }
        return json.get(key).getAsBoolean();
    }
}
