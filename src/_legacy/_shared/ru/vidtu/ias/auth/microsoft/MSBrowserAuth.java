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

package ru.vidtu.ias.auth.microsoft;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.vidtu.ias.IAS;
import ru.vidtu.ias.account.Account;
import ru.vidtu.ias.account.MicrosoftAccount;
import ru.vidtu.ias.auth.microsoft.fields.MSTokens;
import ru.vidtu.ias.utils.exceptions.FriendlyException;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Automated browser manager that injects Microsoft session cookies into an isolated
 * Chromium browser instance and intercepts the resulting OAuth authorization code.
 *
 * @author Articuling
 */
public final class MSBrowserAuth {
    private static final Logger LOGGER = LoggerFactory.getLogger("IAS/MSBrowserAuth");
    private static final Pattern CODE_PATTERN = Pattern.compile("oauth20_desktop\\.srf\\?(?:[^\"&]*&)*code=([^\"&]+)");
    private static final Pattern ERROR_PATTERN = Pattern.compile("oauth20_desktop\\.srf\\?(?:[^\"&]*&)*error=([^\"&]+)");

    private record CookieObj(String name, String value, String domain, String path, boolean secure, boolean httpOnly) {}

    /**
     * Checks if a compatible Chromium browser (Edge / Chrome / Brave / Chromium) is available.
     */
    public static boolean isBrowserSupported() {
        return findBrowserExecutable() != null;
    }

    /**
     * Extracts session cookies from an account and completes interactive browser OAuth authorization.
     *
     * @param account Target MicrosoftAccount
     * @return CompletableFuture resolving to MSTokens with persistent refresh token
     */
    @NotNull
    public static CompletableFuture<MSTokens> authorizeAccountWithCookies(@NotNull Account account) {
        String cookieHeader = extractCookieHeader(account);
        if (cookieHeader == null || cookieHeader.isBlank()) {
            return CompletableFuture.failedFuture(new FriendlyException("No cookie session found for this account.", "ias.error.noRefreshToken"));
        }
        return authorizeWithCookies(cookieHeader);
    }

    /**
     * Injects cookies into a fresh browser session, navigates to the OAuth authorization page,
     * and intercepts the OAuth code upon redirect.
     *
     * @param cookieHeader Netscape or HTTP Cookie header value
     * @return CompletableFuture resolving to MSTokens with persistent refresh token
     */
    @NotNull
    public static CompletableFuture<MSTokens> authorizeWithCookies(@NotNull String cookieHeader) {
        return CompletableFuture.supplyAsync(() -> {
            String browserExe = findBrowserExecutable();
            if (browserExe == null) {
                throw new FriendlyException("No compatible Chromium browser found (Edge/Chrome/Brave).", "ias.error.noRefreshToken");
            }

            int port = findFreePort();
            Path tempProfile;
            try {
                tempProfile = Files.createTempDirectory("ias_browser_auth_");
            } catch (IOException e) {
                throw new RuntimeException("Unable to create temporary browser profile directory.", e);
            }

            List<CookieObj> cookies = parseCookies(cookieHeader);
            LOGGER.info("IAS: Launching browser {} on port {} with {} cookies...", browserExe, port, cookies.size());

            ProcessBuilder pb = new ProcessBuilder(
                    browserExe,
                    "--remote-debugging-port=" + port,
                    "--user-data-dir=" + tempProfile.toAbsolutePath(),
                    "--no-first-run",
                    "--no-default-browser-check",
                    "about:blank"
            );

            Process proc;
            try {
                proc = pb.start();
            } catch (IOException e) {
                deleteDirQuietly(tempProfile);
                throw new RuntimeException("Unable to start browser process.", e);
            }

            CompletableFuture<String> codeFuture = new CompletableFuture<>();
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            // Run CDP orchestration asynchronously
            CompletableFuture.runAsync(() -> {
                try {
                    String wsUrl = pollWebSocketUrl(client, port, 30);
                    if (wsUrl == null) {
                        codeFuture.completeExceptionally(new FriendlyException("Browser DevTools did not become ready.", "ias.error.noRefreshToken"));
                        return;
                    }

                    LOGGER.info("IAS: Connected to browser DevTools at {}", wsUrl);
                    AtomicInteger reqId = new AtomicInteger(1);

                    WebSocket ws = client.newWebSocketBuilder()
                            .buildAsync(URI.create(wsUrl), new WebSocket.Listener() {
                                private final StringBuilder buffer = new StringBuilder();

                                @Override
                                public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                                    buffer.append(data);
                                    if (last) {
                                        String msg = buffer.toString();
                                        buffer.setLength(0);
                                        checkCdpMessage(msg, codeFuture);
                                    }
                                    return WebSocket.Listener.super.onText(webSocket, data, last);
                                }
                            }).get(10, TimeUnit.SECONDS);

                    // Enable network and page tracking
                    sendCdp(ws, reqId.getAndIncrement(), "Network.enable", "{}");
                    sendCdp(ws, reqId.getAndIncrement(), "Page.enable", "{}");

                    // Inject session cookies into browser
                    if (!cookies.isEmpty()) {
                        StringBuilder cookieArray = new StringBuilder("[");
                        for (int i = 0; i < cookies.size(); i++) {
                            CookieObj c = cookies.get(i);
                            if (i > 0) cookieArray.append(",");
                            cookieArray.append(String.format(
                                    "{\"name\":\"%s\",\"value\":\"%s\",\"domain\":\"%s\",\"path\":\"%s\",\"secure\":true,\"httpOnly\":%s}",
                                    escapeJson(c.name()), escapeJson(c.value()), escapeJson(c.domain()), escapeJson(c.path()), c.httpOnly()
                            ));
                        }
                        cookieArray.append("]");
                        sendCdp(ws, reqId.getAndIncrement(), "Network.setCookies", "{\"cookies\":" + cookieArray + "}");
                        LOGGER.info("IAS: Successfully injected {} cookies into browser session.", cookies.size());
                    }

                    // Navigate to official Microsoft Launcher OAuth authorization URL
                    String authUrl = MSAuth.MINECRAFT_LAUNCHER_AUTH_URL;
                    sendCdp(ws, reqId.getAndIncrement(), "Page.navigate", "{\"url\":\"" + escapeJson(authUrl) + "\"}");
                    LOGGER.info("IAS: Browser navigated to Microsoft OAuth URL.");

                } catch (Throwable t) {
                    LOGGER.error("IAS: Error in browser DevTools communication.", t);
                    codeFuture.completeExceptionally(t);
                }
            }, IAS.executor());

            // Wait for authorization code or browser exit
            try {
                String code = codeFuture.get(180, TimeUnit.SECONDS);
                LOGGER.info("IAS: Successfully captured authorization code from browser!");
                return code;
            } catch (Exception e) {
                throw new FriendlyException("Browser authorization was cancelled or timed out.", "ias.error.noRefreshToken");
            } finally {
                try {
                    proc.destroy();
                    if (proc.isAlive()) {
                        proc.destroyForcibly();
                    }
                } catch (Throwable ignored) {}
                deleteDirQuietly(tempProfile);
            }
        }, IAS.executor()).thenComposeAsync(code -> {
            LOGGER.info("IAS: Exchanging captured browser code for persistent refresh token...");
            return MSAuth.minecraftAuthCodeToMsaMsr(code, "https://login.live.com/oauth20_desktop.srf");
        }, IAS.executor());
    }

    private static void checkCdpMessage(String msg, CompletableFuture<String> codeFuture) {
        if (msg == null || msg.isBlank()) return;

        // Check for success code
        Matcher codeMatcher = CODE_PATTERN.matcher(msg);
        if (codeMatcher.find()) {
            String code = URLDecoder.decode(codeMatcher.group(1), StandardCharsets.UTF_8);
            codeFuture.complete(code);
            return;
        }

        // Check for OAuth error in redirect
        Matcher errMatcher = ERROR_PATTERN.matcher(msg);
        if (errMatcher.find()) {
            String error = URLDecoder.decode(errMatcher.group(1), StandardCharsets.UTF_8);
            if (!"login_required".equalsIgnoreCase(error) && !"consent_required".equalsIgnoreCase(error)) {
                codeFuture.completeExceptionally(new FriendlyException("Microsoft OAuth error: " + error, "ias.error.noRefreshToken"));
            }
        }
    }

    private static void sendCdp(WebSocket ws, int id, String method, String params) {
        String msg = String.format("{\"id\":%d,\"method\":\"%s\",\"params\":%s}", id, method, params);
        ws.sendText(msg, true);
    }

    @Nullable
    private static String pollWebSocketUrl(HttpClient client, int port, int maxAttempts) {
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                Thread.sleep(200);
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + port + "/json"))
                        .timeout(Duration.ofSeconds(1))
                        .GET().build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    String body = resp.body();
                    Matcher m = Pattern.compile("\"type\":\\s*\"page\"[\\s\\S]*?\"webSocketDebuggerUrl\":\\s*\"([^\"]+)\"").matcher(body);
                    if (m.find()) {
                        return m.group(1);
                    }
                    Matcher m2 = Pattern.compile("\"webSocketDebuggerUrl\":\\s*\"([^\"]+)\"").matcher(body);
                    if (m2.find()) {
                        return m2.group(1);
                    }
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    @Nullable
    private static String extractCookieHeader(@NotNull Account account) {
        if (!(account instanceof MicrosoftAccount ms)) return null;
        try {
            // Check if account holds a cookie-header in its stored refresh payload
            var method = MicrosoftAccount.class.getDeclaredMethod("cookieHeader");
            method.setAccessible(true);
            Object res = method.invoke(ms);
            if (res instanceof String s && !s.isBlank()) {
                return s;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static List<CookieObj> parseCookies(String cookieData) {
        List<CookieObj> list = new ArrayList<>();
        if (cookieData == null || cookieData.isBlank()) return list;

        // Check if Netscape format (lines with tabs)
        if (cookieData.contains("\t")) {
            String[] lines = cookieData.split("\r?\n");
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\t");
                if (parts.length >= 6) {
                    String domain = parts[0];
                    String name = parts[5];
                    String val = parts.length > 6 ? parts[6] : "";
                    String path = parts.length > 2 ? parts[2] : "/";
                    boolean httpOnly = name.contains("AUTH") || name.contains("MSP") || name.contains("PPL");
                    list.add(new CookieObj(name, val, domain, path, true, httpOnly));
                }
            }
            if (!list.isEmpty()) return list;
        }

        // Standard Cookie header key=value pairs
        String[] pairs = cookieData.split(";\\s*");
        for (String pair : pairs) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            String name = pair.substring(0, eq).trim();
            String val = pair.substring(eq + 1).trim();
            if (name.isEmpty()) continue;

            String domain = name.startsWith("__Host-") ? "login.live.com" : ".login.live.com";
            boolean httpOnly = name.contains("AUTH") || name.contains("MSP") || name.contains("PPL");
            list.add(new CookieObj(name, val, domain, "/", true, httpOnly));
            if (!name.startsWith("__Host-")) {
                list.add(new CookieObj(name, val, ".live.com", "/", true, httpOnly));
            }
        }
        return list;
    }

    @Nullable
    private static String findBrowserExecutable() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        List<String> candidates = new ArrayList<>();

        if (os.contains("win")) {
            String progFiles = System.getenv("ProgramFiles");
            String progFilesX86 = System.getenv("ProgramFiles(x86)");
            String localApp = System.getenv("LOCALAPPDATA");

            if (progFilesX86 != null) {
                candidates.add(progFilesX86 + "\\Microsoft\\Edge\\Application\\msedge.exe");
                candidates.add(progFilesX86 + "\\Google\\Chrome\\Application\\chrome.exe");
                candidates.add(progFilesX86 + "\\BraveSoftware\\Brave-Browser\\Application\\brave.exe");
            }
            if (progFiles != null) {
                candidates.add(progFiles + "\\Microsoft\\Edge\\Application\\msedge.exe");
                candidates.add(progFiles + "\\Google\\Chrome\\Application\\chrome.exe");
                candidates.add(progFiles + "\\BraveSoftware\\Brave-Browser\\Application\\brave.exe");
            }
            if (localApp != null) {
                candidates.add(localApp + "\\Microsoft\\Edge\\Application\\msedge.exe");
                candidates.add(localApp + "\\Google\\Chrome\\Application\\chrome.exe");
                candidates.add(localApp + "\\BraveSoftware\\Brave-Browser\\Application\\brave.exe");
            }
            candidates.add("C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe");
            candidates.add("C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe");
            candidates.add("C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe");
        } else if (os.contains("mac")) {
            candidates.add("/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge");
            candidates.add("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome");
            candidates.add("/Applications/Brave Browser.app/Contents/MacOS/Brave Browser");
            candidates.add("/Applications/Chromium.app/Contents/MacOS/Chromium");
        } else {
            // Linux / Unix binaries
            candidates.add("/usr/bin/microsoft-edge");
            candidates.add("/usr/bin/microsoft-edge-stable");
            candidates.add("/usr/bin/google-chrome");
            candidates.add("/usr/bin/google-chrome-stable");
            candidates.add("/usr/bin/chromium");
            candidates.add("/usr/bin/chromium-browser");
            candidates.add("/usr/bin/brave-browser");
        }

        for (String path : candidates) {
            File f = new File(path);
            if (f.exists() && f.canExecute()) {
                return f.getAbsolutePath();
            }
        }
        return null;
    }

    private static int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException e) {
            return 9333 + (int) (Math.random() * 1000);
        }
    }

    private static void deleteDirQuietly(Path dir) {
        try {
            if (Files.exists(dir)) {
                Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.deleteIfExists(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                        Files.deleteIfExists(d);
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        } catch (Throwable ignored) {}
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
