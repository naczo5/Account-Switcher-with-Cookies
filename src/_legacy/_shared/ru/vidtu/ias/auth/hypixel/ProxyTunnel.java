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

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

/**
 * Opens a TCP tunnel through a SOCKS5 or HTTP CONNECT proxy.
 */
final class ProxyTunnel {
    private ProxyTunnel() {
    }

    static Socket connect(String spec, String targetHost, int targetPort, int connectTimeoutMs, int readTimeoutMs) throws IOException {
        return connect(ParsedProxy.parse(spec), targetHost, targetPort, connectTimeoutMs, readTimeoutMs);
    }

    static Socket connect(ParsedProxy proxy, String targetHost, int targetPort, int connectTimeoutMs, int readTimeoutMs) throws IOException {
        IOException socksError = null;
        if (!proxy.http) {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(proxy.host, proxy.port), connectTimeoutMs);
            socket.setSoTimeout(readTimeoutMs);
            try {
                socks5Connect(socket, proxy, targetHost, targetPort);
                return socket;
            } catch (IOException e) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
                socksError = e;
            }
        }
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(proxy.host, proxy.port), connectTimeoutMs);
        socket.setSoTimeout(readTimeoutMs);
        try {
            httpConnect(socket, proxy, targetHost, targetPort);
            return socket;
        } catch (IOException httpError) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            if (socksError != null) {
                httpError.addSuppressed(socksError);
            }
            throw socksError != null ? socksError : httpError;
        }
    }

    private static void socks5Connect(Socket socket, ParsedProxy proxy, String targetHost, int targetPort) throws IOException {
        OutputStream out = socket.getOutputStream();
        InputStream in = socket.getInputStream();
        boolean auth = proxy.user != null && !proxy.user.isEmpty();
        // Netty / LiquidBounce offer NO_AUTH + PASSWORD together when credentials exist.
        if (auth) {
            out.write(new byte[]{0x05, 0x02, 0x00, 0x02});
        } else {
            out.write(new byte[]{0x05, 0x01, 0x00});
        }
        out.flush();
        int ver = in.read();
        int method = in.read();
        if (ver != 0x05) {
            throw new IOException("SOCKS proxy is not SOCKS5 (ver=" + ver + ")");
        }
        if (method == 0xFF) {
            throw new IOException("SOCKS5 rejected auth methods (credentials " + (auth ? "present" : "missing") + "). Put dedicated.REGION.liquidproxy.net:1080 on liquidProxyHost and the Proxy Manager user/pass on liquidProxyUsername/liquidProxyPassword.");
        }
        if (method == 0x02) {
            if (!auth) {
                throw new IOException("SOCKS5 proxy requested a username/password");
            }
            byte[] user = proxy.user.getBytes(StandardCharsets.ISO_8859_1);
            byte[] pass = (proxy.pass == null ? "" : proxy.pass).getBytes(StandardCharsets.ISO_8859_1);
            if (user.length > 255 || pass.length > 255) {
                throw new IOException("SOCKS5 username/password longer than 255 bytes");
            }
            ByteArrayOutputStream authBuf = new ByteArrayOutputStream();
            authBuf.write(0x01);
            authBuf.write(user.length);
            authBuf.write(user);
            authBuf.write(pass.length);
            authBuf.write(pass);
            out.write(authBuf.toByteArray());
            out.flush();
            int authVer = in.read();
            int authStatus = in.read();
            if (authVer != 0x01 || authStatus != 0x00) {
                throw new IOException("SOCKS5 username/password rejected (status=" + authStatus + ")");
            }
        } else if (method != 0x00) {
            throw new IOException("SOCKS5 proxy rejected auth method " + method);
        }

        byte[] host = targetHost.getBytes(StandardCharsets.US_ASCII);
        if (host.length > 255) {
            throw new IOException("Target hostname too long for SOCKS5 (" + host.length + " > 255)");
        }
        ByteArrayOutputStream req = new ByteArrayOutputStream();
        req.write(0x05);
        req.write(0x01);
        req.write(0x00);
        req.write(0x03);
        req.write(host.length);
        req.write(host);
        req.write((targetPort >>> 8) & 0xFF);
        req.write(targetPort & 0xFF);
        out.write(req.toByteArray());
        out.flush();

        DataInputStream data = new DataInputStream(in);
        if (data.readUnsignedByte() != 0x05) {
            throw new IOException("Bad SOCKS5 connect reply");
        }
        int status = data.readUnsignedByte();
        if (status != 0x00) {
            throw new IOException("SOCKS5 CONNECT failed, status " + status);
        }
        data.readUnsignedByte();
        int atyp = data.readUnsignedByte();
        if (atyp == 0x01) {
            data.skipBytes(4);
        } else if (atyp == 0x03) {
            data.skipBytes(data.readUnsignedByte());
        } else if (atyp == 0x04) {
            data.skipBytes(16);
        } else {
            throw new IOException("SOCKS5 unknown address type " + atyp);
        }
        data.readUnsignedShort();
    }

    private static void httpConnect(Socket socket, ParsedProxy proxy, String targetHost, int targetPort) throws IOException {
        OutputStream out = socket.getOutputStream();
        InputStream in = socket.getInputStream();
        StringBuilder req = new StringBuilder();
        req.append("CONNECT ").append(targetHost).append(':').append(targetPort).append(" HTTP/1.1\r\n");
        req.append("Host: ").append(targetHost).append(':').append(targetPort).append("\r\n");
        if (proxy.user != null && !proxy.user.isEmpty()) {
            String token = proxy.user + ':' + (proxy.pass == null ? "" : proxy.pass);
            req.append("Proxy-Authorization: Basic ")
                    .append(Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8)))
                    .append("\r\n");
        }
        req.append("Proxy-Connection: Keep-Alive\r\n\r\n");
        out.write(req.toString().getBytes(StandardCharsets.US_ASCII));
        out.flush();

        ByteArrayOutputStream header = new ByteArrayOutputStream();
        int prev = 0;
        while (true) {
            int b = in.read();
            if (b < 0) {
                throw new IOException("HTTP proxy closed during CONNECT");
            }
            header.write(b);
            if (prev == '\r' && b == '\n' && header.size() >= 4) {
                byte[] bytes = header.toByteArray();
                if (bytes[bytes.length - 4] == '\r' && bytes[bytes.length - 3] == '\n') {
                    break;
                }
            }
            prev = b;
            if (header.size() > 8192) {
                throw new IOException("HTTP proxy CONNECT header too large");
            }
        }
        String text = header.toString("US-ASCII");
        String line = text.split("\\r\\n", 2)[0];
        if (!line.contains(" 200")) {
            throw new IOException("HTTP proxy CONNECT failed: " + line);
        }
    }

    static final class ParsedProxy {
        final boolean http;
        final String host;
        final int port;
        final String user;
        final String pass;

        ParsedProxy(boolean http, String host, int port, String user, String pass) {
            this.http = http;
            this.host = host;
            this.port = port;
            this.user = user;
            this.pass = pass;
        }

        static ParsedProxy parse(String spec) throws IOException {
            String raw = spec.trim();
            if (raw.isEmpty()) {
                throw new IOException("Empty proxy");
            }
            String lower = raw.toLowerCase(Locale.ROOT);
            boolean http = lower.startsWith("http://") || lower.startsWith("https://");
            boolean socks = lower.startsWith("socks5://") || lower.startsWith("socks://");
            String remainder = raw;
            if (http || socks) {
                int scheme = remainder.indexOf("://");
                remainder = remainder.substring(scheme + 3);
            } else {
                remainder = "socks5://" + remainder;
                // Recompute without scheme prefix for host parsing below.
                remainder = remainder.substring("socks5://".length());
                http = false;
            }
            // Split on last '@' so passwords containing '@', ':', '/', '?', '#' survive.
            String user = null;
            String pass = null;
            String hostPort = remainder;
            int at = remainder.lastIndexOf('@');
            if (at >= 0) {
                String info = remainder.substring(0, at);
                hostPort = remainder.substring(at + 1);
                int colon = info.indexOf(':');
                if (colon >= 0) {
                    user = percentDecode(info.substring(0, colon));
                    pass = percentDecode(info.substring(colon + 1));
                } else {
                    user = percentDecode(info);
                    pass = "";
                }
            }
            // Strip any path/query after host:port.
            int slash = hostPort.indexOf('/');
            if (slash >= 0) {
                hostPort = hostPort.substring(0, slash);
            }
            int q = hostPort.indexOf('?');
            if (q >= 0) {
                hostPort = hostPort.substring(0, q);
            }
            int hash = hostPort.indexOf('#');
            if (hash >= 0) {
                hostPort = hostPort.substring(0, hash);
            }
            hostPort = hostPort.trim();
            if (hostPort.isEmpty()) {
                throw new IOException("Proxy host missing: " + spec);
            }
            String host;
            int port;
            int colon = hostPort.lastIndexOf(':');
            if (colon > 0 && looksLikePort(hostPort.substring(colon + 1))) {
                host = hostPort.substring(0, colon).trim();
                port = Integer.parseInt(hostPort.substring(colon + 1).trim());
            } else {
                host = hostPort;
                port = http ? 8080 : 1080;
            }
            if (host.isEmpty()) {
                throw new IOException("Proxy host missing: " + spec);
            }
            // Strip IPv6 brackets.
            if (host.length() >= 2 && host.startsWith("[") && host.endsWith("]")) {
                host = host.substring(1, host.length() - 1);
            }
            return new ParsedProxy(http, host, port, user, pass);
        }

        private static String percentDecode(String value) {
            try {
                // '+' is a literal plus in userinfo, not a space — protect it first.
                return java.net.URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                return value;
            }
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
}
