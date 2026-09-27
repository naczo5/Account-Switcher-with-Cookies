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

package ru.vidtu.ias.auth.cookie;

import com.google.errorprone.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.vidtu.ias.utils.exceptions.FriendlyException;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for Microsoft cookie alt files (Netscape export and Localts token format).
 *
 * @author VidTu
 */
public final class CookieParser {
    /**
     * Microsoft refresh token prefix used by Localts exports.
     */
    private static final String MSA_TOKEN_PREFIX = "M.C";

    /**
     * Netscape HttpOnly cookie prefix used by browser cookie dumps.
     */
    private static final String HTTPONLY_PREFIX = "#HttpOnly_";

    /**
     * Cookie names that can authenticate a Microsoft session.
     */
    private static final Set<String> AUTH_COOKIE_NAMES = Set.of(
            "__host-msaauthp",
            "__host-msaauth",
            "msaauthp",
            "msaauth",
            "mspauth",
            "mspprof",
            "wlssid"
    );

    /**
     * Netscape line with spaces instead of tabs (common after GUI paste).
     */
    private static final Pattern SPACE_NETSCAPE_LINE = Pattern.compile(
            "^(\\S+)\\s+(TRUE|FALSE)\\s+(\\S+)\\s+(TRUE|FALSE)\\s+(\\d+)\\s+(\\S+)(?:\\s+(.*))?$",
            Pattern.CASE_INSENSITIVE);
    /**
     * An instance of this class cannot be created.
     *
     * @throws AssertionError Always
     */
    @Contract(value = "-> fail", pure = true)
    private CookieParser() {
        throw new AssertionError("No instances.");
    }

    /**
     * Reads and parses a cookie alt file from the given path.
     *
     * @param path File path
     * @return Parsed cookies
     */
    @CheckReturnValue
    @NotNull
    public static ParsedCookies fromPath(@NotNull String path) {
        String normalized = normalizePath(path);
        try {
            return fromText(readText(Path.of(normalized)));
        } catch (IOException e) {
            throw new FriendlyException("Unable to read cookie file: " + normalized, e, "ias.error.cookie.file");
        }
    }

    /**
     * Reads alt-file text, detecting UTF-8, UTF-16, and Windows ANSI dumps.
     *
     * @param path File path
     * @return File contents
     */
    @CheckReturnValue
    @NotNull
    public static String readText(@NotNull Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        return decodeBytes(bytes);
    }

    @NotNull
    static String decodeBytes(byte @NotNull [] bytes) {
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
            return new String(bytes, StandardCharsets.UTF_16LE);
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
            return new String(bytes, StandardCharsets.UTF_16BE);
        }
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        int nuls = 0;
        int probe = Math.min(bytes.length, 64);
        for (int i = 0; i < probe; i++) {
            if (bytes[i] == 0) {
                nuls++;
            }
        }
        if (nuls > 8) {
            return new String(bytes, StandardCharsets.UTF_16LE);
        }
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        if (utf8.indexOf('\uFFFD') >= 0) {
            return new String(bytes, Charset.forName("windows-1252"));
        }
        return utf8;
    }

    /**
     * Strips whitespace and optional surrounding quotes from a Windows file path.
     */
    @NotNull
    private static String normalizePath(@NotNull String path) {
        path = path.strip();
        if (path.length() >= 2 && path.charAt(0) == '"' && path.charAt(path.length() - 1) == '"') {
            return path.substring(1, path.length() - 1).strip();
        }
        return path;
    }

    /**
     * Parses cookie alt text (Netscape or Localts format).
     *
     * @param text Raw cookie file contents
     * @return Parsed cookies
     */
    @CheckReturnValue
    @NotNull
    public static ParsedCookies fromText(@NotNull String text) {
        String trimmed = normalizeInput(text).strip();
        if (trimmed.contains("\t") && looksLikeNetscape(trimmed)) {
            return fromNetscape(trimmed);
        }
        ParsedCookies parsed;
        if (looksLikeLocalts(trimmed)) {
            parsed = fromLocalts(trimmed);
        } else if (looksLikeCookieHeader(trimmed)) {
            parsed = fromCookieHeader(trimmed);
        } else if (trimmed.contains("\t") || looksLikeSpaceSeparatedNetscape(trimmed)) {
            parsed = fromNetscape(trimmed.contains("\t") ? trimmed : convertSpaceSeparatedNetscape(trimmed));
        } else {
            throw new FriendlyException("Unrecognized cookie format. Use a Netscape cookie file, semicolon-separated cookie header, or Localts token file.", "ias.error.cookie.invalid");
        }
        return parsed;
    }

    /**
     * Splits checker dumps and concatenated Netscape files into one blob per account.
     * <p>
     * Supports:
     * <ul>
     *     <li>{@code Source:} / Email / Username metadata blocks (NFA loader / checker exports)</li>
     *     <li>Multiple {@code # Netscape HTTP Cookie File} headers in one file</li>
     *     <li>{@code --------} separators</li>
     * </ul>
     *
     * @param text Raw file contents
     * @return One or more account blobs
     */
    @CheckReturnValue
    @NotNull
    public static List<String> splitRecords(@NotNull String text) {
        String trimmed = normalizeInput(text).strip();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        List<String> bySource = splitLookahead(trimmed, "(?m)(?=^Source\\s*:)");
        if (bySource.size() > 1) {
            return bySource;
        }
        List<String> byNetscape = splitLookahead(trimmed, "(?m)(?=^# Netscape HTTP Cookie File)");
        if (byNetscape.size() > 1) {
            return byNetscape;
        }
        List<String> byDash = new ArrayList<>();
        for (String part : trimmed.split("(?m)^-{3,}\\s*$")) {
            String blob = part.strip();
            if (!blob.isEmpty()) {
                byDash.add(blob);
            }
        }
        if (byDash.size() > 1) {
            return byDash;
        }
        return List.of(trimmed);
    }

    @NotNull
    private static List<String> splitLookahead(@NotNull String text, @NotNull String regex) {
        String[] parts = text.split(regex);
        List<String> out = new ArrayList<>(parts.length);
        for (String part : parts) {
            String blob = part.strip();
            if (!blob.isEmpty()) {
                out.add(blob);
            }
        }
        return out;
    }

    /**
     * Normalizes pasted cookie text (line endings, space-separated Netscape exports).
     */
    @NotNull
    private static String normalizeInput(@NotNull String text) {
        text = text.replace("\uFEFF", "").replace("\r\n", "\n").replace('\r', '\n');
        if (text.contains("\t")) {
            return text;
        }
        if (!looksLikeSpaceSeparatedNetscape(text)) {
            return text;
        }
        return convertSpaceSeparatedNetscape(text);
    }

    @Contract(pure = true)
    private static boolean looksLikeSpaceSeparatedNetscape(@NotNull String text) {
        for (String line : text.split("\n")) {
            line = stripHttpOnly(line.strip());
            if (isIgnorableLine(line)) {
                continue;
            }
            return SPACE_NETSCAPE_LINE.matcher(line).matches();
        }
        return false;
    }

    @NotNull
    private static String convertSpaceSeparatedNetscape(@NotNull String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (String line : text.split("\n", -1)) {
            String stripped = stripHttpOnly(line.strip());
            if (isIgnorableLine(stripped)) {
                out.append(line).append('\n');
                continue;
            }
            Matcher matcher = SPACE_NETSCAPE_LINE.matcher(stripped);
            if (!matcher.matches()) {
                out.append(line).append('\n');
                continue;
            }
            out.append(matcher.group(1)).append('\t')
                    .append(matcher.group(2)).append('\t')
                    .append(matcher.group(3)).append('\t')
                    .append(matcher.group(4)).append('\t')
                    .append(matcher.group(5)).append('\t')
                    .append(matcher.group(6));
            String value = matcher.group(7);
            if (value != null && !value.isEmpty()) {
                out.append('\t').append(value);
            }
            out.append('\n');
        }
        return out.toString();
    }

    /**
     * Whether the text looks like a Netscape cookie export (tab-separated fields per line).
     */
    @Contract(pure = true)
    private static boolean looksLikeNetscape(@NotNull String text) {
        for (String line : text.split("\n")) {
            line = stripHttpOnly(line.strip());
            if (isIgnorableLine(line)) {
                continue;
            }
            return line.split("\t", -1).length >= 6;
        }
        return false;
    }

    @NotNull
    private static String stripHttpOnly(@NotNull String line) {
        if (line.regionMatches(true, 0, HTTPONLY_PREFIX, 0, HTTPONLY_PREFIX.length())) {
            return line.substring(HTTPONLY_PREFIX.length()).strip();
        }
        return line;
    }

    @Contract(pure = true)
    private static boolean isIgnorableLine(@NotNull String line) {
        return line.isEmpty() || (line.startsWith("#") && !line.regionMatches(true, 0, HTTPONLY_PREFIX, 0, HTTPONLY_PREFIX.length()));
    }

    @Contract(pure = true)
    private static boolean isAuthCookie(@NotNull String name) {
        return AUTH_COOKIE_NAMES.contains(name.toLowerCase(Locale.ROOT));
    }

    /**
     * Whether the text looks like a semicolon-separated HTTP {@code Cookie} header.
     */
    @Contract(pure = true)
    private static boolean looksLikeCookieHeader(@NotNull String text) {
        if (text.contains("\t")) {
            return false;
        }
        if (!text.contains("=")) {
            return false;
        }
        if (text.contains(";")) {
            return true;
        }
        return text.lines().allMatch(line -> {
            line = line.strip();
            return line.isEmpty() || line.startsWith("#") || line.contains("=");
        }) && text.contains("=") && !text.contains("\t");
    }

    /**
     * Parses {@code name=value; name=value} cookie header text.
     */
    @CheckReturnValue
    @NotNull
    private static ParsedCookies fromCookieHeader(@NotNull String text) {
        Map<String, CookieEntry> cookies = new LinkedHashMap<>();
        String domain = "login.live.com";

        for (String segment : text.split(";")) {
            segment = segment.strip();
            if (segment.isEmpty()) {
                continue;
            }
            int eq = segment.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String name = segment.substring(0, eq).strip();
            String value = segment.substring(eq + 1).strip();
            if (name.isEmpty()) {
                continue;
            }
            cookies.put(domain + '\0' + name, new CookieEntry(domain, "/", true, name, value));
        }

        return validateAuthCookies(cookies);
    }

    @CheckReturnValue
    @NotNull
    private static ParsedCookies validateAuthCookies(@NotNull Map<String, CookieEntry> cookies) {
        if (cookies.isEmpty()) {
            throw new FriendlyException("Cookie file is empty.", "ias.error.cookie.invalid");
        }

        boolean hasAuth = cookies.values().stream().anyMatch(c -> isAuthCookie(c.name()));
        String embedded = extractEmbeddedRefresh(cookies);
        if (!hasAuth && (embedded == null || embedded.isBlank())) {
            throw new FriendlyException("Cookie file is missing Microsoft session cookies (__Host-MSAAUTHP / __Host-MSAAUTH / MSPAuth).", "ias.error.cookie.invalid");
        }

        return embedded == null || embedded.isBlank()
                ? new ParsedCookies(cookies)
                : new ParsedCookies(cookies, embedded);
    }

    @Nullable
    private static String extractEmbeddedRefresh(@NotNull Map<String, CookieEntry> cookies) {
        for (CookieEntry entry : cookies.values()) {
            String value = entry.value();
            if (value == null || value.isBlank()) {
                continue;
            }
            int mc = indexOfTokenPrefix(value);
            if (mc < 0) {
                continue;
            }
            int end = mc;
            while (end < value.length()) {
                char c = value.charAt(end);
                if (c == '\n' || c == '\r' || c == ' ' || c == '\t') {
                    break;
                }
                end++;
            }
            String token = value.substring(mc, end);
            if (looksLikeLocaltsRefreshToken(token) || token.startsWith("M.") || token.startsWith("0.")) {
                return token;
            }
        }
        return null;
    }

    @Contract(pure = true)
    private static int indexOfTokenPrefix(@NotNull String value) {
        int mc = value.indexOf(MSA_TOKEN_PREFIX);
        if (mc >= 0) {
            return mc;
        }
        int m = value.indexOf("M.");
        if (m >= 0) {
            return m;
        }
        return value.indexOf("0.");
    }

    /**
     * Whether the text looks like a Localts bare token or {@code username:token} export rather than Netscape cookies.
     */
    @Contract(pure = true)
    private static boolean looksLikeLocalts(@NotNull String text) {
        for (String line : text.split("\n")) {
            line = stripHttpOnly(line.strip());
            if (isIgnorableLine(line) || line.contains("Localts")) {
                if (line.contains("Localts")) {
                    return true;
                }
                continue;
            }
            if (looksLikeLocaltsRefreshToken(line) || looksLikeRefreshToken(line)) {
                return true;
            }
            if (line.contains("\t")) {
                return false;
            }
            int sep = line.indexOf(':');
            if (sep > 0) {
                String value = line.substring(sep + 1).strip();
                if (looksLikeLocaltsRefreshToken(value) || looksLikeRefreshToken(value)) {
                    return true;
                }
            }
            int eq = line.indexOf('=');
            if (eq > 0) {
                String value = line.substring(eq + 1).strip();
                if (looksLikeLocaltsRefreshToken(value) || looksLikeRefreshToken(value)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Parses Localts format: a bare token or an optional header line plus {@code username:M.C...} refresh token.
     */
    @CheckReturnValue
    @NotNull
    private static ParsedCookies fromLocalts(@NotNull String text) {
        String token = extractLocaltsToken(text);

        if (token == null || token.isBlank()) {
            throw new FriendlyException("Localts input is missing a valid MSA session token (M.C... or username:M.C...).", "ias.error.cookie.invalid");
        }
        return new ParsedCookies(Map.of(), token);
    }

    @Nullable
    private static String extractLocaltsToken(@NotNull String text) {
        for (String line : text.split("\n")) {
            line = line.strip();
            if (line.isEmpty() || line.startsWith("#") || line.contains("Localts")) {
                continue;
            }

            int sep = line.indexOf(':');
            if (sep > 0) {
                String value = line.substring(sep + 1).strip();
                if (looksLikeLocaltsRefreshToken(value) || looksLikeRefreshToken(value)) {
                    return value;
                }
            }

            int eq = line.indexOf('=');
            if (eq > 0) {
                String value = line.substring(eq + 1).strip();
                if (looksLikeLocaltsRefreshToken(value) || looksLikeRefreshToken(value)) {
                    return value;
                }
            }

            if (looksLikeLocaltsRefreshToken(line) || looksLikeRefreshToken(line)) {
                return line;
            }
        }

        int mc = indexOfTokenPrefix(text);
        if (mc < 0) {
            return null;
        }
        int end = mc;
        while (end < text.length()) {
            char c = text.charAt(end);
            if (c == '\n' || c == '\r' || c == ' ' || c == '\t') {
                break;
            }
            end++;
        }
        return text.substring(mc, end);
    }

    @Contract(pure = true)
    private static boolean looksLikeLocaltsRefreshToken(@NotNull String value) {
        return value.startsWith(MSA_TOKEN_PREFIX);
    }

    @Contract(pure = true)
    private static boolean looksLikeRefreshToken(@NotNull String value) {
        if (value.startsWith(MSA_TOKEN_PREFIX) || value.startsWith("M.") || value.startsWith("0.")) {
            return value.length() > 40;
        }
        return value.length() >= 80 && value.matches("[A-Za-z0-9._\\-+=/*!$]+");
    }

    /**
     * Parses Netscape-format cookie alt text.
     */
    @CheckReturnValue
    @NotNull
    private static ParsedCookies fromNetscape(@NotNull String text) {
        Map<String, CookieEntry> cookies = new LinkedHashMap<>();

        for (String line : text.split("\n")) {
            line = stripHttpOnly(line.strip());
            if (isIgnorableLine(line)) {
                continue;
            }

            String[] parts = line.split("\t", -1);
            if (parts.length < 6) {
                continue;
            }

            String domain = parts[0];
            String cookiePath = parts[2];
            boolean secure = "TRUE".equalsIgnoreCase(parts[3]);
            String name = parts[5];
            String value = parts.length == 6 ? "" : String.join("\t", Arrays.copyOfRange(parts, 6, parts.length));

            cookies.put(domain + '\0' + name, new CookieEntry(domain, cookiePath, secure, name, value));
        }

        return validateAuthCookies(cookies);
    }

    /**
     * Parsed cookie collection.
     *
     * @param cookies Cookie map keyed by {@code domain + "\0" + name}
     */
    public record ParsedCookies(@NotNull Map<String, CookieEntry> cookies, @NotNull String refreshToken) {
        public ParsedCookies(@NotNull Map<String, CookieEntry> cookies) {
            this(cookies, "");
        }

        /**
         * Builds an HTTP {@code Cookie} header value from the parsed cookies.
         * Login.live.com cookies are ordered first.
         *
         * @return Cookie header value
         */
        @Contract(pure = true)
        @NotNull
        public String toCookieHeader() {
            List<CookieEntry> ordered = new ArrayList<>(this.cookies.values());
            ordered.sort(Comparator.comparingInt(c -> {
                String domain = c.domain().toLowerCase();
                if (domain.contains("login.live.com")) return 0;
                if (domain.contains("live.com")) return 1;
                return 2;
            }));

            StringBuilder header = new StringBuilder();
            for (CookieEntry entry : ordered) {
                if (!header.isEmpty()) {
                    header.append("; ");
                }
                header.append(entry.pair());
            }
            return header.toString();
        }

        /**
         * Builds a SISU {@code Cookie} header using only cookies that a real browser would
         * send to {@code login.live.com} (RiseClient-style): cookies scoped to that exact
         * host, plus cookies scoped to any of its parent domains (e.g. {@code .live.com}),
         * per standard cookie domain-matching rules. Cookies scoped to unrelated domains
         * (e.g. {@code login.microsoftonline.com}, {@code microsoft.com}) are excluded.
         *
         * @return Cookie header value
         */
        @Contract(pure = true)
        @NotNull
        public String toSisuCookieHeader() {
            Map<String, CookieEntry> byName = new LinkedHashMap<>();
            for (CookieEntry entry : this.cookies.values()) {
                if (!appliesToSisuHost(entry.domain())) {
                    continue;
                }
                byName.putIfAbsent(entry.name(), entry);
            }
            if (byName.isEmpty()) {
                throw new FriendlyException("Cookie file has no login.live.com cookies for SISU auth.", "ias.error.cookie.invalid");
            }

            StringBuilder header = new StringBuilder();
            for (CookieEntry entry : byName.values()) {
                if (!header.isEmpty()) {
                    header.append("; ");
                }
                header.append(entry.pair());
            }
            return header.toString();
        }

        /**
         * Whether a cookie scoped to the given domain would be sent by a browser making a
         * request to {@code login.live.com}. This is true if the domain is exactly
         * {@code login.live.com}, or a parent domain of it (e.g. {@code live.com} or
         * {@code .live.com}) - matching RFC 6265 domain-matching, not the reverse.
         *
         * @param cookieDomain Cookie's domain attribute, as stored in the parsed file
         * @return Whether the cookie applies to the SISU host
         */
        @Contract(pure = true)
        private static boolean appliesToSisuHost(@NotNull String cookieDomain) {
            String domain = cookieDomain.toLowerCase();
            if (domain.startsWith(".")) {
                domain = domain.substring(1);
            }
            String host = "login.live.com";
            return host.equals(domain) || host.endsWith("." + domain);
        }
    }
}
