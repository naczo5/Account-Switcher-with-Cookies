package ru.vidtu.iasfork.cookie;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bare-token import for 1.8.9, mirroring modern {@code TokenImporter}.
 * Accepts JWT access tokens, M.C refresh tokens, JSON {@code "mcToken"} exports,
 * {@code Bearer / MCToken} prefixes and {@code username:token} lines.
 */
public final class TokenImporter {
    private static final Pattern MC_TOKEN_JSON = Pattern.compile("\"mcToken\"\\s*:\\s*\"([^\"]+)\"");

    private TokenImporter() {
    }

    public static List<String> extractValues(String raw) {
        if (raw == null) {
            return new ArrayList<String>();
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            return new ArrayList<String>();
        }
        Set<String> tokens = new LinkedHashSet<String>();
        Matcher matcher = MC_TOKEN_JSON.matcher(text);
        while (matcher.find()) {
            String token = normalizeToken(matcher.group(1));
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        if (!tokens.isEmpty()) {
            return new ArrayList<String>(tokens);
        }
        String[] lines = text.split("\n");
        List<String> cleaned = new ArrayList<String>();
        for (String line : lines) {
            String s = line == null ? "" : line.trim();
            if (!s.isEmpty() && !s.startsWith("#")) {
                cleaned.add(s);
            }
        }
        if (cleaned.size() > 1) {
            for (String line : cleaned) {
                String token = normalizeToken(line);
                if (!token.isEmpty()) {
                    tokens.add(token);
                }
            }
        } else {
            String token = normalizeToken(text);
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        return new ArrayList<String>(tokens);
    }

    public static CookieAuth.MinecraftProfile importSingleToken(String token) throws Exception {
        String normalized = normalizeToken(token);
        if (normalized.isEmpty()) {
            throw new CookieAuthException("Unrecognized import format.", "ias.error.cookie.invalid");
        }
        if (looksLikeMinecraftAccessToken(normalized)) {
            try {
                return CookieAuth.profileFromAccessToken(normalized);
            } catch (Throwable accessFailed) {
                // JWT-shaped but expired/invalid -> fall through to refresh attempt
                // only if it also looks like a refresh token; otherwise rethrow.
                if (!normalized.startsWith("M.C")) {
                    throw accessFailed;
                }
            }
        }
        return CookieAuth.profileFromRefreshToken(normalized);
    }

    static boolean looksLikeMinecraftAccessToken(String token) {
        if (token == null || !token.startsWith("eyJ")) {
            return false;
        }
        int firstDot = token.indexOf('.');
        return firstDot > 3 && token.indexOf('.', firstDot + 1) > firstDot + 1;
    }

    static String normalizeToken(String raw) {
        String value = raw == null ? "" : raw.trim();
        value = unwrap(value).replace("\r", "").replace("\n", "").trim();
        value = removePrefix(value, "MCToken ");
        value = removePrefix(value, "Bearer ");
        value = stripUsernamePrefix(value);
        return unwrap(value).trim();
    }

    private static String stripUsernamePrefix(String value) {
        int colon = value.indexOf(':');
        if (colon <= 0 || colon >= value.length() - 1) {
            return value;
        }
        String prefix = value.substring(0, colon);
        String rest = value.substring(colon + 1);
        if (rest.trim().isEmpty() || prefix.contains(".") || prefix.length() > 32) {
            return value;
        }
        return rest;
    }

    private static String unwrap(String value) {
        String next = value == null ? "" : value.trim();
        if (next.length() >= 2 && next.charAt(0) == '"' && next.charAt(next.length() - 1) == '"') {
            next = next.substring(1, next.length() - 1);
        }
        return next;
    }

    private static String removePrefix(String value, String prefix) {
        String next = value == null ? "" : value.trim();
        if (next.length() >= prefix.length() && next.substring(0, prefix.length()).equalsIgnoreCase(prefix)) {
            return next.substring(prefix.length()).trim();
        }
        return next;
    }
}
