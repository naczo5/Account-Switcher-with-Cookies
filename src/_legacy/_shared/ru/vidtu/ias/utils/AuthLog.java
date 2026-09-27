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

package ru.vidtu.ias.utils;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import ru.vidtu.ias.utils.exceptions.FriendlyException;

/**
 * Compact auth logging. Invalid/expired accounts must not dump HTML bodies or stack traces into latest.log.
 *
 * @author VidTu
 */
public final class AuthLog {
    /**
     * Maximum characters kept from an exception or HTTP body in a log line.
     */
    private static final int MAX_SNIPPET = 240;

    /**
     * An instance of this class cannot be created.
     */
    @Contract(value = "-> fail", pure = true)
    private AuthLog() {
        throw new AssertionError("No instances.");
    }

    /**
     * Logs an expected auth/import failure as a single warn line. Stack traces stay at debug.
     *
     * @param log     Target logger
     * @param message Short description
     * @param error   Failure, may be {@code null}
     */
    public static void expected(@NotNull Logger log, @NotNull String message, @Nullable Throwable error) {
        log.warn("IAS: {} ({})", message, summarize(error));
        if (error != null) {
            log.debug("IAS: {}", message, error);
        }
    }

    /**
     * Logs an unexpected failure without flooding the log with HTTP bodies.
     *
     * @param log     Target logger
     * @param message Short description
     * @param error   Failure, may be {@code null}
     */
    public static void unexpected(@NotNull Logger log, @NotNull String message, @Nullable Throwable error) {
        log.error("IAS: {} ({})", message, summarize(error));
        if (error != null) {
            log.debug("IAS: {}", message, error);
        }
    }

    /**
     * Whether the throwable is a known, user-facing auth failure rather than a bug.
     *
     * @param error Failure
     * @return {@code true} if this should not dump a stack trace at error level
     */
    @Contract(value = "null -> false", pure = true)
    public static boolean expectedFailure(@Nullable Throwable error) {
        FriendlyException friendly = FriendlyException.friendlyInChain(error);
        if (friendly == null) {
            return false;
        }
        String key = friendly.key();
        return key.startsWith("ias.error.cookie")
                || key.startsWith("ias.error.token")
                || "ias.error.noProfile".equals(key)
                || "ias.error.noXbox".equals(key)
                || "ias.error.xboxAvailable".equals(key)
                || "ias.error.xboxAdult".equals(key)
                || "ias.error.rateLimited".equals(key)
                || "ias.error.connect".equals(key)
                || "ias.error.noRefreshToken".equals(key);
    }

    /**
     * One-line summary of a throwable chain, truncated so HTML login pages never reach latest.log.
     *
     * @param error Failure, may be {@code null}
     * @return Compact description
     */
    @NotNull
    public static String summarize(@Nullable Throwable error) {
        if (error == null) {
            return "unknown";
        }
        FriendlyException friendly = FriendlyException.friendlyInChain(error);
        String key = friendly != null ? friendly.key() : error.getClass().getSimpleName();
        String detail = firstUsefulMessage(error);
        if (detail == null || detail.isBlank()) {
            return key;
        }
        return key + ": " + truncate(detail);
    }

    /**
     * Truncates HTTP bodies and other blobs for exception messages.
     *
     * @param body Raw body, may be {@code null}
     * @return Short snippet
     */
    @NotNull
    public static String truncateBody(@Nullable String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String trimmed = body.strip();
        if (trimmed.regionMatches(true, 0, "<!doctype", 0, 9) || trimmed.regionMatches(true, 0, "<html", 0, 5)) {
            return "[html " + trimmed.length() + " chars]";
        }
        return truncate(trimmed.replace('\n', ' ').replace('\r', ' '));
    }

    @Nullable
    private static String firstUsefulMessage(@Nullable Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && !message.isBlank()) {
                return message.replace('\n', ' ').replace('\r', ' ');
            }
            current = current.getCause();
        }
        return error != null ? error.getClass().getSimpleName() : null;
    }

    @NotNull
    private static String truncate(@NotNull String value) {
        if (value.length() <= MAX_SNIPPET) {
            return value;
        }
        return value.substring(0, MAX_SNIPPET) + "...(" + value.length() + " chars)";
    }
}
