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
import java.net.URI;
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
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(proxy.host, proxy.port), connectTimeoutMs);
        socket.setSoTimeout(readTimeoutMs);
        try {
            if (proxy.http) {
                httpConnect(socket, proxy, targetHost, targetPort);
            } else {
                socks5Connect(socket, proxy, targetHost, targetPort);
            }
            return socket;
        } catch (IOException e) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            throw e;
        }
    }

    private static void socks5Connect(Socket socket, ParsedProxy proxy, String targetHost, int targetPort) throws IOException {
        OutputStream out = socket.getOutputStream();
        InputStream in = socket.getInputStream();
        boolean auth = proxy.user != null && !proxy.user.isEmpty();
        out.write(auth ? new byte[]{0x05, 0x01, 0x02} : new byte[]{0x05, 0x01, 0x00});
        out.flush();
        int ver = in.read();
        int method = in.read();
        if (ver != 0x05) {
            throw new IOException("SOCKS proxy is not SOCKS5 (ver=" + ver + ")");
        }
        if (method == 0x02) {
            if (!auth) {
                throw new IOException("SOCKS5 proxy requested a username/password");
            }
            byte[] user = proxy.user.getBytes(StandardCharsets.UTF_8);
            byte[] pass = proxy.pass == null ? new byte[0] : proxy.pass.getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream authBuf = new ByteArrayOutputStream();
            authBuf.write(0x01);
            authBuf.write(user.length);
            authBuf.write(user);
            authBuf.write(pass.length);
            authBuf.write(pass);
            out.write(authBuf.toByteArray());
            out.flush();
            if (in.read() != 0x01 || in.read() != 0x00) {
                throw new IOException("SOCKS5 proxy rejected username/password");
            }
        } else if (method != 0x00) {
            throw new IOException("SOCKS5 proxy rejected auth method " + method);
        }

        byte[] host = targetHost.getBytes(StandardCharsets.US_ASCII);
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
        switch (atyp) {
            case 0x01 -> data.skipBytes(4);
            case 0x03 -> data.skipBytes(data.readUnsignedByte());
            case 0x04 -> data.skipBytes(16);
            default -> throw new IOException("SOCKS5 unknown address type " + atyp);
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
        String text = header.toString(StandardCharsets.US_ASCII);
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
            if (!lower.startsWith("socks5://") && !lower.startsWith("socks://") && !http) {
                raw = "socks5://" + raw;
                lower = raw.toLowerCase(Locale.ROOT);
                http = false;
            }
            URI uri;
            try {
                uri = URI.create(raw);
            } catch (IllegalArgumentException e) {
                throw new IOException("Invalid proxy URL: " + spec, e);
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw new IOException("Proxy host missing: " + spec);
            }
            int port = uri.getPort();
            if (port <= 0) {
                port = http ? 8080 : 1080;
            }
            String user = null;
            String pass = null;
            String info = uri.getUserInfo();
            if (info != null && !info.isEmpty()) {
                int colon = info.indexOf(':');
                if (colon >= 0) {
                    user = info.substring(0, colon);
                    pass = info.substring(colon + 1);
                } else {
                    user = info;
                    pass = "";
                }
            }
            return new ParsedProxy(http, host, port, user, pass);
        }
    }
}
