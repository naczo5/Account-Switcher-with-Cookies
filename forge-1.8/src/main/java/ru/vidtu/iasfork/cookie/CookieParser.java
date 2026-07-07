package ru.vidtu.iasfork.cookie;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for Microsoft cookie alt files (Netscape export and Localts token format).
 */
public final class CookieParser {
    private static final String MSA_TOKEN_PREFIX = "M.C";
    private static final Pattern SPACE_NETSCAPE_LINE = Pattern.compile(
            "^(\\S+)\\s+(TRUE|FALSE)\\s+(\\S+)\\s+(TRUE|FALSE)\\s+(\\d+)\\s+(\\S+)(?:\\s+(.*))?$",
            Pattern.CASE_INSENSITIVE);

    private CookieParser() {
    }

    public static ParsedCookies fromPath(String path) throws CookieAuthException {
        String normalized = normalizePath(path);
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(normalized));
            return fromText(new String(bytes, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new CookieAuthException("Unable to read cookie file: " + normalized, "ias.error.cookie.file");
        }
    }

    private static String normalizePath(String path) {
        path = path.trim();
        if (path.length() >= 2 && path.charAt(0) == '"' && path.charAt(path.length() - 1) == '"') {
            return path.substring(1, path.length() - 1).trim();
        }
        return path;
    }

    public static ParsedCookies fromText(String text) throws CookieAuthException {
        String trimmed = normalizeInput(text).trim();
        if (trimmed.contains("\t") && looksLikeNetscape(trimmed)) {
            return fromNetscape(trimmed);
        }
        if (looksLikeCookieHeader(trimmed)) {
            return fromCookieHeader(trimmed);
        }
        if (looksLikeLocalts(trimmed)) {
            return fromLocalts(trimmed);
        }
        if (trimmed.contains("\t")) {
            return fromNetscape(trimmed);
        }
        throw new CookieAuthException(
                "Unrecognized cookie format. Use a Netscape cookie file, semicolon-separated cookie header, or Localts token file.",
                "ias.error.cookie.invalid");
    }

    private static String normalizeInput(String text) {
        text = text.replace("\uFEFF", "").replace("\r\n", "\n").replace('\r', '\n');
        if (text.contains("\t") || !looksLikeSpaceSeparatedNetscape(text)) {
            return text;
        }
        return convertSpaceSeparatedNetscape(text);
    }

    private static boolean looksLikeSpaceSeparatedNetscape(String text) {
        for (String line : text.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            return SPACE_NETSCAPE_LINE.matcher(line).matches();
        }
        return false;
    }

    private static String convertSpaceSeparatedNetscape(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (String line : text.split("\n", -1)) {
            String stripped = line.trim();
            if (stripped.isEmpty() || stripped.startsWith("#")) {
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

    private static boolean looksLikeNetscape(String text) {
        for (String line : text.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            return line.split("\t", -1).length >= 6;
        }
        return false;
    }

    private static boolean looksLikeCookieHeader(String text) {
        if (text.contains("\t") || !text.contains("=")) {
            return false;
        }
        if (text.contains(";")) {
            return true;
        }
        boolean hasValue = false;
        for (String line : text.split("\n")) {
            line = line.trim();
            if (!line.isEmpty() && !line.startsWith("#") && line.contains("=")) {
                hasValue = true;
            }
        }
        return hasValue;
    }

    private static ParsedCookies fromCookieHeader(String text) throws CookieAuthException {
        Map<String, CookieEntry> cookies = new LinkedHashMap<>();
        String domain = "login.live.com";
        for (String segment : text.split(";")) {
            segment = segment.trim();
            if (segment.isEmpty()) {
                continue;
            }
            int eq = segment.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String name = segment.substring(0, eq).trim();
            String value = segment.substring(eq + 1).trim();
            if (name.isEmpty()) {
                continue;
            }
            cookies.put(domain + '\0' + name, new CookieEntry(domain, "/", true, name, value));
        }
        return validateAuthCookies(cookies);
    }

    private static ParsedCookies validateAuthCookies(Map<String, CookieEntry> cookies) throws CookieAuthException {
        if (cookies.isEmpty()) {
            throw new CookieAuthException("Cookie file is empty.", "ias.error.cookie.invalid");
        }
        boolean hasAuth = false;
        for (CookieEntry entry : cookies.values()) {
            if ("__Host-MSAAUTHP".equals(entry.name()) || "__Host-MSAAUTH".equals(entry.name())) {
                hasAuth = true;
                break;
            }
        }
        if (!hasAuth) {
            throw new CookieAuthException("Cookie file is missing __Host-MSAAUTHP or __Host-MSAAUTH.", "ias.error.cookie.invalid");
        }
        return new ParsedCookies(cookies);
    }

    private static boolean looksLikeLocalts(String text) {
        for (String line : text.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.contains("Localts")) {
                return true;
            }
            if (line.contains("\t") || line.contains("=")) {
                continue;
            }
            int sep = line.indexOf(':');
            if (sep > 0 && looksLikeLocaltsRefreshToken(line.substring(sep + 1).trim())) {
                return true;
            }
        }
        return false;
    }

    private static ParsedCookies fromLocalts(String text) throws CookieAuthException {
        String token = extractLocaltsToken(text);
        if (token == null || token.trim().isEmpty()) {
            throw new CookieAuthException("Localts file is missing a valid MSA session token (username:M.C...).", "ias.error.cookie.invalid");
        }
        return new ParsedCookies(Collections.<String, CookieEntry>emptyMap(), token);
    }

    private static String extractLocaltsToken(String text) {
        for (String line : text.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#") || line.contains("Localts")) {
                continue;
            }
            int sep = line.indexOf(':');
            if (sep > 0) {
                String value = line.substring(sep + 1).trim();
                if (looksLikeLocaltsRefreshToken(value)) {
                    return value;
                }
            }
            int eq = line.indexOf('=');
            if (eq > 0) {
                String value = line.substring(eq + 1).trim();
                if (looksLikeLocaltsRefreshToken(value)) {
                    return value;
                }
            }
        }
        int mc = text.indexOf(MSA_TOKEN_PREFIX);
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

    private static boolean looksLikeLocaltsRefreshToken(String value) {
        return value.startsWith(MSA_TOKEN_PREFIX);
    }

    private static ParsedCookies fromNetscape(String text) throws CookieAuthException {
        Map<String, CookieEntry> cookies = new LinkedHashMap<>();
        for (String line : text.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\t", -1);
            if (parts.length < 6) {
                throw new CookieAuthException("Invalid cookie line (expected at least 6 tab-separated fields): " + line, "ias.error.cookie.invalid");
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

    public static final class ParsedCookies {
        private final Map<String, CookieEntry> cookies;
        private final String refreshToken;

        public ParsedCookies(Map<String, CookieEntry> cookies) {
            this(cookies, "");
        }

        public ParsedCookies(Map<String, CookieEntry> cookies, String refreshToken) {
            this.cookies = cookies;
            this.refreshToken = refreshToken == null ? "" : refreshToken;
        }

        public Map<String, CookieEntry> cookies() {
            return cookies;
        }

        public String refreshToken() {
            return refreshToken;
        }

        public String toSisuCookieHeader() throws CookieAuthException {
            Map<String, CookieEntry> byName = new LinkedHashMap<>();
            for (CookieEntry entry : cookies.values()) {
                if (!entry.domain().toLowerCase().endsWith("login.live.com")) {
                    continue;
                }
                if (!byName.containsKey(entry.name())) {
                    byName.put(entry.name(), entry);
                }
            }
            if (byName.isEmpty()) {
                throw new CookieAuthException("Cookie file has no login.live.com cookies for SISU auth.", "ias.error.cookie.invalid");
            }
            StringBuilder header = new StringBuilder();
            for (CookieEntry entry : byName.values()) {
                if (header.length() > 0) {
                    header.append("; ");
                }
                header.append(entry.pair());
            }
            return header.toString();
        }
    }
}
