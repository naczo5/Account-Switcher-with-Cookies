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

package ru.vidtu.ias.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.vidtu.ias.auth.hypixel.HypixelBanResult;
import ru.vidtu.ias.utils.GSONUtils;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistent cache for account checks (Hypixel ban results and name-change availability).
 */
public final class ChecksCache {
    private static final Logger LOGGER = LoggerFactory.getLogger("IAS/ChecksCache");
    private static final String CACHE_FILE_NAME = "checks_cache_v1.json";

    private static final Map<UUID, HypixelBanResult> HYPIXEL_BANS = new ConcurrentHashMap<>();
    private static final Map<UUID, String> NAME_CHANGES = new ConcurrentHashMap<>();

    private ChecksCache() {
        // No instances.
    }

    @Nullable
    public static HypixelBanResult getHypixelBan(@NotNull UUID uuid) {
        return HYPIXEL_BANS.get(uuid);
    }

    public static void putHypixelBan(@NotNull UUID uuid, @NotNull HypixelBanResult result) {
        HYPIXEL_BANS.put(uuid, result);
    }

    public static void removeHypixelBan(@NotNull UUID uuid) {
        HYPIXEL_BANS.remove(uuid);
    }

    @NotNull
    public static Map<UUID, HypixelBanResult> allHypixelBans() {
        return HYPIXEL_BANS;
    }

    @Nullable
    public static String getNameChange(@NotNull UUID uuid) {
        return NAME_CHANGES.get(uuid);
    }

    public static void putNameChange(@NotNull UUID uuid, @NotNull String state) {
        NAME_CHANGES.put(uuid, state);
    }

    public static void removeNameChange(@NotNull UUID uuid) {
        NAME_CHANGES.remove(uuid);
    }

    @NotNull
    public static Map<UUID, String> allNameChanges() {
        return NAME_CHANGES;
    }

    public static void clear(@NotNull UUID uuid) {
        HYPIXEL_BANS.remove(uuid);
        NAME_CHANGES.remove(uuid);
    }

    public static void load(@NotNull Path gamePath) {
        try {
            Path file = gamePath.resolve("_IAS_ACCOUNTS_DO_NOT_SEND_TO_ANYONE/.hidden").resolve(CACHE_FILE_NAME);
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                LOGGER.debug("IAS: Checks cache file not found at {}.", file);
                return;
            }

            String content = Files.readString(file);
            JsonObject json = GSONUtils.GSON.fromJson(content, JsonObject.class);
            if (json == null) {
                return;
            }

            if (json.has("hypixel") && json.get("hypixel").isJsonObject()) {
                JsonObject hypixelObj = json.getAsJsonObject("hypixel");
                for (Map.Entry<String, JsonElement> entry : hypixelObj.entrySet()) {
                    try {
                        UUID uuid = UUID.fromString(entry.getKey());
                        if (entry.getValue().isJsonObject()) {
                            JsonObject banObj = entry.getValue().getAsJsonObject();
                            String statusStr = banObj.has("status") ? banObj.get("status").getAsString() : null;
                            if (statusStr != null) {
                                HypixelBanResult.Status status = HypixelBanResult.Status.valueOf(statusStr);
                                String banType = banObj.has("banType") && !banObj.get("banType").isJsonNull() ? banObj.get("banType").getAsString() : null;
                                String duration = banObj.has("duration") && !banObj.get("duration").isJsonNull() ? banObj.get("duration").getAsString() : null;
                                String reason = banObj.has("reason") && !banObj.get("reason").isJsonNull() ? banObj.get("reason").getAsString() : null;
                                String rawMessage = banObj.has("rawMessage") && !banObj.get("rawMessage").isJsonNull() ? banObj.get("rawMessage").getAsString() : null;
                                String errorMessage = banObj.has("errorMessage") && !banObj.get("errorMessage").isJsonNull() ? banObj.get("errorMessage").getAsString() : null;
                                HYPIXEL_BANS.put(uuid, HypixelBanResult.of(status, banType, duration, reason, rawMessage, errorMessage));
                            }
                        }
                    } catch (Throwable t) {
                        LOGGER.debug("IAS: Unable to parse cached Hypixel ban for {}.", entry.getKey(), t);
                    }
                }
            }

            if (json.has("nameChanges") && json.get("nameChanges").isJsonObject()) {
                JsonObject namesObj = json.getAsJsonObject("nameChanges");
                for (Map.Entry<String, JsonElement> entry : namesObj.entrySet()) {
                    try {
                        UUID uuid = UUID.fromString(entry.getKey());
                        if (entry.getValue().isJsonPrimitive()) {
                            NAME_CHANGES.put(uuid, entry.getValue().getAsString());
                        }
                    } catch (Throwable t) {
                        LOGGER.debug("IAS: Unable to parse cached name change for {}.", entry.getKey(), t);
                    }
                }
            }

            LOGGER.debug("IAS: Loaded {} Hypixel ban checks and {} name-change checks from cache.", HYPIXEL_BANS.size(), NAME_CHANGES.size());
        } catch (Throwable t) {
            LOGGER.warn("IAS: Unable to load checks cache.", t);
        }
    }

    public static void save(@NotNull Path gamePath) {
        try {
            Path folder = gamePath.resolve("_IAS_ACCOUNTS_DO_NOT_SEND_TO_ANYONE/.hidden");
            Files.createDirectories(folder);
            Path file = folder.resolve(CACHE_FILE_NAME);

            JsonObject root = new JsonObject();

            JsonObject hypixelObj = new JsonObject();
            for (Map.Entry<UUID, HypixelBanResult> entry : HYPIXEL_BANS.entrySet()) {
                HypixelBanResult result = entry.getValue();
                JsonObject obj = new JsonObject();
                obj.addProperty("status", result.status().name());
                if (result.banType() != null) obj.addProperty("banType", result.banType());
                if (result.duration() != null) obj.addProperty("duration", result.duration());
                if (result.reason() != null) obj.addProperty("reason", result.reason());
                if (result.rawMessage() != null) obj.addProperty("rawMessage", result.rawMessage());
                if (result.errorMessage() != null) obj.addProperty("errorMessage", result.errorMessage());
                hypixelObj.add(entry.getKey().toString(), obj);
            }
            root.add("hypixel", hypixelObj);

            JsonObject namesObj = new JsonObject();
            for (Map.Entry<UUID, String> entry : NAME_CHANGES.entrySet()) {
                namesObj.addProperty(entry.getKey().toString(), entry.getValue());
            }
            root.add("nameChanges", namesObj);

            Files.writeString(file, GSONUtils.GSON.toJson(root), StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE,
                    StandardOpenOption.SYNC, StandardOpenOption.DSYNC, LinkOption.NOFOLLOW_LINKS);

            LOGGER.debug("IAS: Saved checks cache with {} bans and {} names.", HYPIXEL_BANS.size(), NAME_CHANGES.size());
        } catch (Throwable t) {
            LOGGER.warn("IAS: Unable to save checks cache.", t);
        }
    }
}
