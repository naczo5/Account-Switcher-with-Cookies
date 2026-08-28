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

package ru.vidtu.ias.auth;

import com.google.errorprone.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import ru.vidtu.ias.IAS;
import ru.vidtu.ias.account.MicrosoftAccount;
import ru.vidtu.ias.auth.handlers.CreateHandler;
import ru.vidtu.ias.auth.microsoft.MSAccountFactory;
import ru.vidtu.ias.auth.microsoft.MSAuth;
import ru.vidtu.ias.crypt.Crypt;
import ru.vidtu.ias.utils.exceptions.FriendlyException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Imports Microsoft accounts from bare token text when cookie parsing does not apply.
 *
 * @author VidTu
 */
public final class TokenImporter {
    /**
     * JSON field matcher for copied token exports.
     */
    private static final Pattern MC_TOKEN_JSON = Pattern.compile("\"mcToken\"\\s*:\\s*\"([^\"]+)\"");

    /**
     * An instance of this class cannot be created.
     */
    @Contract(value = "-> fail", pure = true)
    private TokenImporter() {
        throw new AssertionError("No instances.");
    }

    /**
     * Imports one or more tokens from raw text.
     *
     * @param crypt   Account encryption
     * @param raw     Pasted or file contents
     * @param handler Creation handler
     */
    public static void importText(@NotNull Crypt crypt, @NotNull String raw, @NotNull CreateHandler handler) {
        List<String> tokens = extractValues(raw);
        if (tokens.isEmpty()) {
            handler.error(new FriendlyException("Unrecognized import format.", "ias.error.cookie.invalid"));
            return;
        }
        importValues(crypt, tokens, 0, handler);
    }

    /**
     * Extracts normalized token values from pasted or file text.
     *
     * @param raw Raw text
     * @return Token values, in order
     */
    @CheckReturnValue
    @NotNull
    public static List<String> extractValues(@NotNull String raw) {
        String text = raw.strip();
        if (text.isBlank()) {
            return List.of();
        }

        Set<String> tokens = new LinkedHashSet<>();
        Matcher matcher = MC_TOKEN_JSON.matcher(text);
        while (matcher.find()) {
            String token = normalizeToken(matcher.group(1));
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        if (!tokens.isEmpty()) {
            return new ArrayList<>(tokens);
        }

        List<String> lines = text.lines().map(String::strip).filter(line -> !line.isBlank()).toList();
        if (lines.size() > 1) {
            for (String line : lines) {
                String token = normalizeToken(line);
                if (!token.isBlank()) {
                    tokens.add(token);
                }
            }
        } else {
            String token = normalizeToken(text);
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        return new ArrayList<>(tokens);
    }

    private static void importValues(@NotNull Crypt crypt, @NotNull List<String> tokens, int index, @NotNull CreateHandler handler) {
        if (handler.cancelled()) {
            return;
        }
        if (index >= tokens.size()) {
            handler.error(new IllegalArgumentException("No usable token."));
            return;
        }

        createAccountFromToken(crypt, tokens.get(index), new CreateHandler() {
            @Override
            public boolean cancelled() {
                return handler.cancelled();
            }

            @Override
            public void stage(String stage, Object... args) {
                handler.stage(stage, args);
            }

            @Override
            public void success(MicrosoftAccount account) {
                handler.success(account);
            }

            @Override
            public void error(Throwable error) {
                if (index + 1 < tokens.size()) {
                    importValues(crypt, tokens, index + 1, handler);
                    return;
                }
                handler.error(error);
            }
        });
    }

    /**
     * Creates an account from a single token. Minecraft access tokens are JWTs ({@code eyJ...});
     * everything else is treated as a Microsoft refresh token.
     *
     * @param crypt   Account encryption
     * @param token   Raw token value
     * @param handler Creation handler
     */
    public static void createAccountFromToken(@NotNull Crypt crypt, @NotNull String token, @NotNull CreateHandler handler) {
        if (looksLikeMinecraftAccessToken(token)) {
            MSAccountFactory.createFromMinecraftAccess(crypt, token, handler);
            return;
        }

        createAccountFromRefreshToken(crypt, token, handler);
    }

    private static void createAccountFromRefreshToken(@NotNull Crypt crypt, @NotNull String token, @NotNull CreateHandler handler) {
        CreateHandler viaLauncherClient = new CreateHandler() {
            @Override
            public boolean cancelled() {
                return handler.cancelled();
            }

            @Override
            public void stage(String stage, Object... args) {
                handler.stage(stage, args);
            }

            @Override
            public void success(MicrosoftAccount account) {
                handler.success(account);
            }

            @Override
            public void error(Throwable error) {
                handler.error(error);
            }
        };

        CreateHandler viaIasClient = new CreateHandler() {
            @Override
            public boolean cancelled() {
                return handler.cancelled();
            }

            @Override
            public void stage(String stage, Object... args) {
                handler.stage(stage, args);
            }

            @Override
            public void success(MicrosoftAccount account) {
                handler.success(account);
            }

            @Override
            public void error(Throwable error) {
                if (handler.cancelled()) return;
                MSAuth.minecraftRefreshToMsaMsr(token)
                        .thenComposeAsync(ms -> MSAccountFactory.createFromMinecraftRefresh(crypt, ms, viaLauncherClient), IAS.executor())
                        .exceptionallyAsync(t -> {
                            viaLauncherClient.error(t);
                            return null;
                        }, IAS.executor());
            }
        };

        MSAuth.msrToMsaMsr(token)
                .thenComposeAsync(ms -> MSAccountFactory.create(crypt, ms, viaIasClient), IAS.executor())
                .exceptionallyAsync(t -> {
                    viaIasClient.error(t);
                    return null;
                }, IAS.executor());
    }

    @Contract(value = "null -> false", pure = true)
    private static boolean looksLikeMinecraftAccessToken(String token) {
        if (token == null || !token.startsWith("eyJ")) {
            return false;
        }
        int firstDot = token.indexOf('.');
        return firstDot > 3 && token.indexOf('.', firstDot + 1) > firstDot + 1;
    }

    private static String normalizeToken(String raw) {
        String value = raw == null ? "" : raw.strip();
        value = unwrap(value).replace("\r", "").replace("\n", "").strip();
        value = removePrefix(value, "MCToken ");
        value = removePrefix(value, "Bearer ");
        value = stripUsernamePrefix(value);
        return unwrap(value).strip();
    }

    private static String stripUsernamePrefix(String value) {
        int colon = value.indexOf(':');
        if (colon <= 0 || colon >= value.length() - 1) {
            return value;
        }
        String prefix = value.substring(0, colon);
        String rest = value.substring(colon + 1);
        if (rest.isBlank() || prefix.contains(".") || prefix.length() > 32) {
            return value;
        }
        return rest;
    }

    private static String unwrap(String value) {
        String next = value.strip();
        if (next.length() >= 2 && next.charAt(0) == '"' && next.charAt(next.length() - 1) == '"') {
            next = next.substring(1, next.length() - 1);
        }
        return next;
    }

    private static String removePrefix(String value, String prefix) {
        String next = value.strip();
        if (next.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return next.substring(prefix.length()).strip();
        }
        return next;
    }
}
