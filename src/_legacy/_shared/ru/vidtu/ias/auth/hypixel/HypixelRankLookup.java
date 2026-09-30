/*
 * In-Game Account Switcher is a mod for Minecraft that allows you to change your logged in account in-game, without restarting Minecraft.
 * Copyright (C) 2015-2022 The_Fireplace
 * Copyright (C) 2021-2026 VidTu
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>
 */

package ru.vidtu.ias.auth.hypixel;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ru.vidtu.ias.config.IASConfig;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Looks up Hypixel package rank via the official API so unranked banned
 * accounts are not joined from the local IP.
 */
final class HypixelRankLookup {
    private static final Set<String> EMPTY_RANKS = rankSet("", "none", "normal", "null");
    private static final Set<String> PAID_OR_STAFF = rankSet(
            "vip", "vip_plus", "mvp", "mvp_plus", "superstar",
            "youtuber", "helper", "moderator", "admin", "game_master",
            "gm", "staff", "owner", "pig", "pig+++", "events", "mcp"
    );

    private static Set<String> rankSet(String... values) {
        Set<String> set = new HashSet<String>();
        Collections.addAll(set, values);
        return Collections.unmodifiableSet(set);
    }

    enum Kind {
        /** VIP / MVP / staff / youtuber — Hypixel typically does not IP-ban these on join. */
        RANKED,
        /** Played Hypixel with no package rank — banned ones can IP-ban the connecting IP. */
        UNRANKED,
        /** API says this UUID has never been on Hypixel. */
        NEVER_JOINED,
        /** No API key, request failed, or player object unreadable. */
        UNKNOWN
    }

    private HypixelRankLookup() {
    }

    static Kind lookup(UUID uuid) {
        String key = IASConfig.hypixelApiKey;
        if (key == null || key.trim().isEmpty() || uuid == null) {
            return Kind.UNKNOWN;
        }
        HttpURLConnection connection = null;
        try {
            String undashed = uuid.toString().replace("-", "");
            connection = (HttpURLConnection) new URL(
                    "https://api.hypixel.net/v2/player?uuid=" + undashed).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);
            connection.setRequestProperty("API-Key", key.trim());
            connection.setRequestProperty("User-Agent", HypixelBanChecker.USER_AGENT);
            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String body;
            if (stream == null) {
                body = "";
            } else {
                try (InputStream s = stream) {
                    body = new String(readAll(s), StandardCharsets.UTF_8);
                }
            }
            if (status != 200) {
                return Kind.UNKNOWN;
            }
            JsonObject json = new JsonParser().parse(body).getAsJsonObject();
            if (!json.has("success") || !json.get("success").getAsBoolean()) {
                return Kind.UNKNOWN;
            }
            JsonElement playerEl = json.get("player");
            if (playerEl == null || playerEl.isJsonNull()) {
                return Kind.NEVER_JOINED;
            }
            if (!playerEl.isJsonObject()) {
                return Kind.UNKNOWN;
            }
            return isRanked(playerEl.getAsJsonObject()) ? Kind.RANKED : Kind.UNRANKED;
        } catch (Exception e) {
            return Kind.UNKNOWN;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    static boolean isRanked(JsonObject player) {
        return rankedValue(player, "rank")
                || rankedValue(player, "monthlyPackageRank")
                || rankedValue(player, "newPackageRank")
                || rankedValue(player, "packageRank")
                || (player.has("prefix") && player.get("prefix").isJsonPrimitive()
                && !player.get("prefix").getAsString().trim().isEmpty());
    }

    private static boolean rankedValue(JsonObject player, String key) {
        if (!player.has(key) || player.get(key).isJsonNull() || !player.get(key).isJsonPrimitive()) {
            return false;
        }
        String value = player.get(key).getAsString().trim().toLowerCase(Locale.ROOT);
        if (EMPTY_RANKS.contains(value)) {
            return false;
        }
        return PAID_OR_STAFF.contains(value) || value.contains("mvp") || value.contains("vip");
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) >= 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
