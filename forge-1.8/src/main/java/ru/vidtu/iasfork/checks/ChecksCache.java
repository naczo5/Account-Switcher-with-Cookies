package ru.vidtu.iasfork.checks;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import ru.vidtu.ias.auth.hypixel.HypixelBanResult;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * Minimal persistent Hypixel cache for 1.8.9 (modern ChecksCache parity-lite).
 * Keyed by {@code GuiAccountSelector.hypixelKey()} (uuid:/user:/alias:).
 */
public final class ChecksCache {
    private ChecksCache() {
    }

    private static File cacheFile() {
        try {
            File dir = new File(Minecraft.getMinecraft().mcDataDir, "ias_checks");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            return new File(dir, "hypixel_cache.json");
        } catch (Throwable t) {
            return new File("ias_hypixel_cache.json");
        }
    }

    public static Map<String, HypixelBanResult> load() {
        Map<String, HypixelBanResult> out = new HashMap<String, HypixelBanResult>();
        try {
            File f = cacheFile();
            if (!f.exists()) {
                return out;
            }
            String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            if (text.trim().isEmpty()) {
                return out;
            }
            JsonObject root = new JsonParser().parse(text).getAsJsonObject();
            for (Map.Entry<String, com.google.gson.JsonElement> e : root.entrySet()) {
                try {
                    JsonObject o = e.getValue().getAsJsonObject();
                    String statusStr = o.has("status") ? o.get("status").getAsString() : "ERROR";
                    HypixelBanResult.Status status;
                    try {
                        status = HypixelBanResult.Status.valueOf(statusStr);
                    } catch (Throwable ignored) {
                        status = HypixelBanResult.Status.ERROR;
                    }
                    String banType = o.has("banType") && !o.get("banType").isJsonNull() ? o.get("banType").getAsString() : null;
                    String duration = o.has("duration") && !o.get("duration").isJsonNull() ? o.get("duration").getAsString() : null;
                    String reason = o.has("reason") && !o.get("reason").isJsonNull() ? o.get("reason").getAsString() : null;
                    String raw = o.has("rawMessage") && !o.get("rawMessage").isJsonNull() ? o.get("rawMessage").getAsString() : null;
                    String err = o.has("errorMessage") && !o.get("errorMessage").isJsonNull() ? o.get("errorMessage").getAsString() : null;
                    out.put(e.getKey(), HypixelBanResult.of(status, banType, duration, reason, raw, err));
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    public static void save(Map<String, HypixelBanResult> results) {
        try {
            JsonObject root = new JsonObject();
            for (Map.Entry<String, HypixelBanResult> e : results.entrySet()) {
                try {
                    HypixelBanResult r = e.getValue();
                    if (r == null) {
                        continue;
                    }
                    JsonObject o = new JsonObject();
                    o.addProperty("status", r.status().name());
                    if (r.banType() != null) {
                        o.addProperty("banType", r.banType());
                    }
                    if (r.duration() != null) {
                        o.addProperty("duration", r.duration());
                    }
                    if (r.reason() != null) {
                        o.addProperty("reason", r.reason());
                    }
                    if (r.rawMessage() != null) {
                        o.addProperty("rawMessage", r.rawMessage());
                    }
                    if (r.errorMessage() != null) {
                        o.addProperty("errorMessage", r.errorMessage());
                    }
                    root.add(e.getKey(), o);
                } catch (Throwable ignored) {
                }
            }
            File f = cacheFile();
            Files.write(f.toPath(), root.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) {
        }
    }
}
