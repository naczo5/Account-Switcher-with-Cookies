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

import ru.vidtu.ias.config.IASConfig;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Locale;

/**
 * LiquidProxy (liquidproxy.net) — Minecraft-only SOCKS5/HTTP credentials
 * plus dashboard route hostnames for vanilla server-list joins.
 */
public final class LiquidProxy {
    private LiquidProxy() {
    }

    public static boolean credentialsConfigured() {
        return host() != null && username() != null;
    }

    public static boolean routeConfigured() {
        return routeHost() != null;
    }

    public static boolean available() {
        return credentialsConfigured() || routeConfigured();
    }

    public static String host() {
        String raw = socksEndpoint(IASConfig.liquidProxyHost);
        if (raw == null) {
            raw = socksEndpoint(IASConfig.liquidProxyRoute);
        }
        if (raw == null) {
            return null;
        }
        int colon = raw.lastIndexOf(':');
        if (colon > 0 && colon < raw.length() - 1 && looksLikePort(raw.substring(colon + 1))) {
            return raw.substring(0, colon).trim();
        }
        return raw;
    }

    public static int port() {
        String raw = socksEndpoint(IASConfig.liquidProxyHost);
        if (raw == null) {
            raw = socksEndpoint(IASConfig.liquidProxyRoute);
        }
        if (raw != null) {
            int colon = raw.lastIndexOf(':');
            if (colon > 0 && colon < raw.length() - 1 && looksLikePort(raw.substring(colon + 1))) {
                return Integer.parseInt(raw.substring(colon + 1).trim());
            }
        }
        int configured = IASConfig.liquidProxyPort;
        return configured > 0 ? configured : 1080;
    }

    public static String username() {
        return blankToNull(IASConfig.liquidProxyUsername);
    }

    public static String password() {
        String pass = IASConfig.liquidProxyPassword;
        return pass == null ? "" : pass;
    }

    public static boolean http() {
        String type = IASConfig.liquidProxyType;
        if (type == null) {
            return false;
        }
        String lower = type.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("http");
    }

    public static String routeHost() {
        String raw = blankToNull(IASConfig.liquidProxyRoute);
        if (raw == null || socksEndpoint(raw) != null) {
            return null;
        }
        int slash = raw.indexOf('/');
        if (slash >= 0) {
            raw = raw.substring(0, slash);
        }
        if (raw.contains("://")) {
            raw = raw.substring(raw.indexOf("://") + 3);
        }
        int colon = raw.lastIndexOf(':');
        if (colon > 0 && looksLikePort(raw.substring(colon + 1))) {
            return raw.substring(0, colon).trim();
        }
        return raw.trim();
    }

    public static int routePort() {
        String raw = blankToNull(IASConfig.liquidProxyRoute);
        if (raw != null) {
            int colon = raw.lastIndexOf(':');
            if (colon > 0 && colon < raw.length() - 1 && looksLikePort(raw.substring(colon + 1))) {
                return Integer.parseInt(raw.substring(colon + 1).trim());
            }
        }
        int configured = IASConfig.liquidProxyRoutePort;
        return configured > 0 ? configured : 25565;
    }

    /**
     * Opens a Minecraft TCP connection through LiquidProxy credentials (SOCKS5/HTTP)
     * or, if only a route is set, directly to the route hostname.
     */
    public static Socket open(String targetHost, int targetPort, int connectTimeoutMs, int readTimeoutMs) throws IOException {
        if (host() != null && username() == null) {
            throw new IOException("liquidProxyHost is set but liquidProxyUsername is empty. Paste the Proxy Manager username/password, not the route.");
        }
        if (credentialsConfigured()) {
            ProxyTunnel.ParsedProxy proxy = new ProxyTunnel.ParsedProxy(
                    http(), host(), port(), username(), password());
            return ProxyTunnel.connect(proxy, targetHost, targetPort, connectTimeoutMs, readTimeoutMs);
        }
        if (routeConfigured()) {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(routeHost(), routePort()), connectTimeoutMs);
            socket.setSoTimeout(readTimeoutMs);
            return socket;
        }
        throw new IOException("LiquidProxy is not configured");
    }

    /**
     * Hostname placed in the Minecraft handshake.
     * SOCKS credentials still handshake the real server (Hypixel).
     * A dashboard route is itself the vanilla server address.
     */
    public static String handshakeHost(String intendedHost) {
        if (credentialsConfigured()) {
            return intendedHost;
        }
        if (routeConfigured()) {
            return routeHost();
        }
        return intendedHost;
    }

    public static String handshakeConnectHost(String intendedHost) {
        if (credentialsConfigured()) {
            return intendedHost;
        }
        if (routeConfigured()) {
            return routeHost();
        }
        return intendedHost;
    }

    /**
     * Rewrites a typed multiplayer address onto the configured LiquidProxy route
     * when joining Hypixel (or when the field is empty/hypixel). Other IPs are left alone
     * unless they already point at liquidproxy.net.
     */
    public static String rewriteJoinAddress(String typed) {
        if (!routeConfigured()) {
            return typed;
        }
        if (typed == null || typed.isBlank()) {
            return joinAddress();
        }
        String lower = typed.trim().toLowerCase(Locale.ROOT);
        if (lower.contains("liquidproxy.net")) {
            return typed.trim();
        }
        if (looksLikeHypixel(lower)) {
            return joinAddress();
        }
        return typed;
    }

    public static String joinAddress() {
        int port = routePort();
        String host = routeHost();
        if (host == null) {
            return "";
        }
        return port == 25565 ? host : host + ":" + port;
    }

    public static boolean looksLikeHypixel(String address) {
        if (address == null) {
            return false;
        }
        String lower = address.toLowerCase(Locale.ROOT);
        return lower.contains("hypixel.net") || lower.contains("hypixel.io") || "hypixel".equals(lower);
    }

    /**
     * Dedicated/residential SOCKS endpoints look like
     * {@code dedicated.na-ord.liquidproxy.net:1080}, not a Minecraft route.
     */
    static String socksEndpoint(String raw) {
        String value = blankToNull(raw);
        if (value == null) {
            return null;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        boolean named = lower.startsWith("dedicated.") || lower.startsWith("residential.")
                || lower.startsWith("socks.") || lower.contains(".dedicated.") || lower.contains(".residential.");
        int colon = value.lastIndexOf(':');
        boolean port1080 = colon > 0 && looksLikePort(value.substring(colon + 1))
                && Integer.parseInt(value.substring(colon + 1).trim()) == 1080;
        if ((named || port1080) && lower.contains("liquidproxy.net")) {
            return value;
        }
        return null;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean looksLikePort(String value) {
        try {
            int port = Integer.parseInt(value.trim());
            return port > 0 && port <= 65535;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
