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

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.UUID;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Checks Hypixel ban status by connecting with Minecraft protocol 47.
 * <p>
 * Ban-check logic adapted from <a href="https://github.com/cooldood-dev/mcchecker">mcchecker</a>.
 */
public final class HypixelBanChecker {
    private static final int HYPIXEL_PROTOCOL = 47;
    private static final String[] HYPIXEL_HOSTS = {"mc.hypixel.net", "hypixel.net"};
    private static final int HYPIXEL_PORT = 25565;
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 8_000;
    private static final String USER_AGENT = "IAS-HypixelBanChecker/1.0";
    private static final Gson GSON = new Gson();
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern DURATION_PATTERN = Pattern.compile(
            "(?i)(?:for|banned for|duration:?)\\s*([0-9]+\\s*(?:d(?:ays?)?|h(?:ours?)?|m(?:in(?:utes?)?)?|s(?:ec(?:onds?)?)?|months?|years?)(?:\\s*[0-9]+\\s*(?:d(?:ays?)?|h(?:ours?)?|m(?:in(?:utes?)?)?|s(?:ec(?:onds?)?)?|months?|years?))*)"
    );
    private static final Pattern REASON_PATTERN = Pattern.compile(
            "(?i)reason:\\s*(.+?)(?:\\s*(?:Find out more|Block ID|Ban ID|Sharing your|https?://|\\||$))"
    );
    private static final Pattern ID_PATTERN = Pattern.compile(
            "(?i)(?:ban|block)\\s*id:\\s*(#[0-9a-zA-Z]+|[0-9a-zA-Z]+)"
    );

    private HypixelBanChecker() {
    }

    /**
     * Checks whether the given account is banned on Hypixel.
     *
     * @param username    Minecraft username
     * @param uuid        Profile UUID
     * @param accessToken Minecraft services access token
     * @return Ban check result
     */
    public static HypixelBanResult checkBan(String username, UUID uuid, String accessToken) {
        if (username == null || username.trim().isEmpty() || "Unknown".equalsIgnoreCase(username)
                || accessToken == null || accessToken.trim().isEmpty()) {
            return HypixelBanResult.error("Missing account info");
        }
        String uuidStr = uuidToUndashed(uuid);
        Exception lastError = null;
        for (String host : HYPIXEL_HOSTS) {
            try {
                return tryHypixelConnect(host, username, uuidStr, accessToken);
            } catch (Exception e) {
                lastError = e;
            }
        }
        if (lastError != null) {
            String detail = lastError.getMessage() != null ? lastError.getMessage() : lastError.getClass().getSimpleName();
            return HypixelBanResult.error("All Hypixel hosts failed: " + detail);
        }
        return HypixelBanResult.unbanned();
    }

    private static HypixelBanResult tryHypixelConnect(
            String host,
            String username,
            String uuidStr,
            String accessToken
    ) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, HYPIXEL_PORT), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS);
            InputStream rawIn = socket.getInputStream();
            OutputStream rawOut = socket.getOutputStream();
            StreamIO io = new StreamIO(rawIn, rawOut);

            io.writePacket(buildHandshake(host), 0x00);
            io.writePacket(buildLoginStart(username), 0x00);

            while (true) {
                McProtocolIO.Packet packet;
                try {
                    packet = io.readPacket();
                } catch (SocketTimeoutException e) {
                    throw e;
                }

                switch (packet.id) {
                    case 0x00:
                        return parseBanReason(readChatMessage(packet));
                    case 0x01:
                        EncryptionRequest request = readEncryptionRequest(packet);
                        handleEncryption(io, accessToken, username, uuidStr, request);
                        break;
                    case 0x02:
                        return HypixelBanResult.unbanned();
                    case 0x03:
                        int threshold = McProtocolIO.readVarInt(packet.reader());
                        io.setCompressionThreshold(threshold);
                        break;
                    default:
                        throw new IOException("Unexpected packet id 0x" + Integer.toHexString(packet.id));
                }
            }
        }
    }

    private static byte[] buildHandshake(String address) throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(payload);
        McProtocolIO.writeVarInt(out, HYPIXEL_PROTOCOL);
        McProtocolIO.writeString(out, address);
        McProtocolIO.writeUnsignedShort(out, HYPIXEL_PORT);
        McProtocolIO.writeVarInt(out, 2);
        return payload.toByteArray();
    }

    private static byte[] buildLoginStart(String username) throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(payload);
        McProtocolIO.writeString(out, username);
        return payload.toByteArray();
    }

    private static String readChatMessage(McProtocolIO.Packet packet) throws IOException {
        DataInputStream in = packet.reader();
        String json = McProtocolIO.readString(in);
        return parseChatJson(json);
    }

    private static String parseChatJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return "";
        }
        try {
            JsonElement element = GSON.fromJson(json, JsonElement.class);
            StringBuilder sb = new StringBuilder();
            extractTextFromJson(element, sb);
            return sb.toString().trim();
        } catch (Throwable t) {
            return json.trim();
        }
    }

    private static void extractTextFromJson(JsonElement element, StringBuilder sb) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonPrimitive()) {
            sb.append(element.getAsString());
            return;
        }
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            if (obj.has("text") && obj.get("text").isJsonPrimitive()) {
                sb.append(obj.get("text").getAsString());
            }
            if (obj.has("extra") && obj.get("extra").isJsonArray()) {
                for (JsonElement child : obj.getAsJsonArray("extra")) {
                    extractTextFromJson(child, sb);
                }
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                extractTextFromJson(child, sb);
            }
        }
    }

    private static EncryptionRequest readEncryptionRequest(McProtocolIO.Packet packet) throws IOException {
        DataInputStream in = packet.reader();
        String serverId = McProtocolIO.readString(in);
        byte[] publicKey = McProtocolIO.readByteArray(in);
        byte[] verifyToken = McProtocolIO.readByteArray(in);
        return new EncryptionRequest(serverId, publicKey, verifyToken);
    }

    private static void handleEncryption(
            StreamIO io,
            String accessToken,
            String username,
            String uuidStr,
            EncryptionRequest request
    ) throws Exception {
        byte[] sharedSecret = new byte[16];
        new SecureRandom().nextBytes(sharedSecret);
        String digest = hypixelAuthDigest(request.serverId, sharedSecret, request.publicKey);
        mojangJoin(accessToken, uuidStr, digest);

        RSAPublicKey rsaKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(request.publicKey));
        Cipher rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        rsa.init(Cipher.ENCRYPT_MODE, rsaKey);
        byte[] encryptedSecret = rsa.doFinal(sharedSecret);
        byte[] encryptedToken = rsa.doFinal(request.verifyToken);

        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(payload);
        McProtocolIO.writeByteArray(out, encryptedSecret);
        McProtocolIO.writeByteArray(out, encryptedToken);
        io.writePacket(payload.toByteArray(), 0x01);

        io.enableEncryption(sharedSecret);
    }

    private static void mojangJoin(String accessToken, String uuidStr, String serverHash) throws IOException {
        String body = "{\"accessToken\":\"" + escapeJson(accessToken)
                + "\",\"selectedProfile\":\"" + escapeJson(uuidStr)
                + "\",\"serverId\":\"" + escapeJson(serverHash) + "\"}";
        HttpURLConnection connection = (HttpURLConnection) new URL("https://sessionserver.mojang.com/session/minecraft/join").openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(CONNECT_TIMEOUT_MS);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        int status = connection.getResponseCode();
        if (status != HttpURLConnection.HTTP_NO_CONTENT) {
            InputStream error = connection.getErrorStream();
            String response = error != null ? new String(readAll(error), StandardCharsets.UTF_8) : "";
            throw new IOException("Mojang join failed: " + response);
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String hypixelAuthDigest(String serverId, byte[] sharedSecret, byte[] publicKey) throws Exception {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        sha1.update(serverId.getBytes(StandardCharsets.US_ASCII));
        sha1.update(sharedSecret);
        sha1.update(publicKey);
        byte[] hash = sha1.digest();
        boolean negative = (hash[0] & 0x80) == 0x80;
        if (negative) {
            hash = twosComplement(hash);
        }
        String hex = bytesToHex(hash).replaceFirst("^0+(?!$)", "");
        return negative ? "-" + hex : hex;
    }

    private static byte[] twosComplement(byte[] value) {
        byte[] copy = value.clone();
        boolean carry = true;
        for (int i = copy.length - 1; i >= 0; i--) {
            copy[i] = (byte) ~copy[i];
            if (carry) {
                carry = copy[i] == (byte) 0xFF;
                copy[i]++;
            }
        }
        return copy;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static HypixelBanResult parseBanReason(String text) {
        String normalized = WHITESPACE.matcher(text.replace('\n', ' ')).replaceAll(" ").trim();
        if (normalized.isEmpty()) {
            return HypixelBanResult.error("Empty disconnect reason");
        }
        String lower = normalized.toLowerCase(Locale.ROOT);

        boolean isSecurityBan = lower.contains("security")
                || lower.contains("blocked")
                || lower.contains("block id")
                || lower.contains("suspicious activity")
                || lower.contains("compromised");

        boolean isStandardBan = lower.contains("banned")
                || lower.contains("ban ")
                || lower.contains("ban#")
                || lower.contains("ban id")
                || lower.contains("permanently banned")
                || lower.contains("temporarily banned");

        boolean isPunishment = isSecurityBan || isStandardBan;

        if (!isPunishment) {
            if (lower.contains("session") || lower.contains("verify") || lower.contains("authentication") || lower.contains("invalid")) {
                return HypixelBanResult.error("Auth error: " + normalized);
            } else if (lower.contains("maintenance")) {
                return HypixelBanResult.error("Server in maintenance");
            } else if (lower.contains("already connected")) {
                return HypixelBanResult.error("Already connected");
            } else {
                return HypixelBanResult.error(normalized);
            }
        }

        String banType;
        String banDur = null;
        String banReason = null;

        if (isSecurityBan) {
            banType = "security";
            banDur = "Permanent (Appealable)";
        } else if (lower.contains("permanently") || lower.contains("permanent")) {
            banType = "permanent";
            banDur = "permanent";
        } else if (lower.contains("temporarily") || lower.contains("temporary")) {
            banType = "temporary";
        } else {
            banType = "banned";
        }

        Matcher durationMatcher = DURATION_PATTERN.matcher(normalized);
        if (durationMatcher.find()) {
            banDur = durationMatcher.group(1).trim();
            if (!isSecurityBan) {
                banType = "temporary";
            }
        }

        Matcher reasonMatcher = REASON_PATTERN.matcher(normalized);
        if (reasonMatcher.find()) {
            banReason = reasonMatcher.group(1).trim();
        } else if (isSecurityBan) {
            banReason = "Suspicious activity has been detected on your account.";
        }

        Matcher idMatcher = ID_PATTERN.matcher(normalized);
        if (idMatcher.find()) {
            String punishmentId = idMatcher.group(1).trim();
            if (banReason != null && !banReason.contains(punishmentId)) {
                banReason = banReason + " (" + punishmentId + ")";
            }
        }

        return HypixelBanResult.banned(banType, banDur, banReason, text);
    }

    private static String uuidToUndashed(UUID uuid) {
        return uuid.toString().replace("-", "");
    }

    private static final class EncryptionRequest {
        final String serverId;
        final byte[] publicKey;
        final byte[] verifyToken;

        EncryptionRequest(String serverId, byte[] publicKey, byte[] verifyToken) {
            this.serverId = serverId;
            this.publicKey = publicKey;
            this.verifyToken = verifyToken;
        }
    }

    private static final class StreamIO {
        private final InputStream rawIn;
        private final OutputStream rawOut;
        private final ByteArrayOutputStream decryptedBuffer = new ByteArrayOutputStream();
        private Cipher decrypt;
        private Cipher encrypt;
        private int compressionThreshold = -1;

        StreamIO(InputStream rawIn, OutputStream rawOut) {
            this.rawIn = rawIn;
            this.rawOut = rawOut;
        }

        void enableEncryption(byte[] sharedSecret) throws Exception {
            SecretKeySpec key = new SecretKeySpec(sharedSecret, "AES");
            IvParameterSpec iv = new IvParameterSpec(sharedSecret);
            this.encrypt = Cipher.getInstance("AES/CFB8/NoPadding");
            this.encrypt.init(Cipher.ENCRYPT_MODE, key, iv);
            this.decrypt = Cipher.getInstance("AES/CFB8/NoPadding");
            this.decrypt.init(Cipher.DECRYPT_MODE, key, iv);
        }

        void setCompressionThreshold(int threshold) {
            this.compressionThreshold = threshold;
        }

        void writePacket(byte[] payload, int packetId) throws IOException {
            ByteArrayOutputStream inner = new ByteArrayOutputStream();
            DataOutputStream innerOut = new DataOutputStream(inner);
            McProtocolIO.writeVarInt(innerOut, packetId);
            innerOut.write(payload);
            byte[] data = inner.toByteArray();

            ByteArrayOutputStream body = new ByteArrayOutputStream();
            DataOutputStream bodyOut = new DataOutputStream(body);
            if (this.compressionThreshold >= 0) {
                if (data.length >= this.compressionThreshold) {
                    McProtocolIO.writeVarInt(bodyOut, data.length);
                    bodyOut.write(deflate(data));
                } else {
                    McProtocolIO.writeVarInt(bodyOut, 0);
                    bodyOut.write(data);
                }
            } else {
                bodyOut.write(data);
            }
            byte[] bodyBytes = body.toByteArray();
            ByteArrayOutputStream framed = new ByteArrayOutputStream();
            DataOutputStream frame = new DataOutputStream(framed);
            McProtocolIO.writeVarInt(frame, bodyBytes.length);
            frame.write(bodyBytes);
            byte[] packet = framed.toByteArray();
            if (this.encrypt != null) {
                packet = this.encrypt.update(packet);
            }
            this.rawOut.write(packet);
            this.rawOut.flush();
        }

        McProtocolIO.Packet readPacket() throws IOException {
            while (true) {
                McProtocolIO.Packet packet = tryReadPacket();
                if (packet != null) {
                    return packet;
                }
                int read = this.rawIn.read();
                if (read < 0) {
                    throw new IOException("Connection closed");
                }
                byte plain = (byte) read;
                if (this.decrypt != null) {
                    byte[] decoded = this.decrypt.update(new byte[]{plain});
                    if (decoded == null || decoded.length == 0) {
                        continue;
                    }
                    plain = decoded[0];
                }
                this.decryptedBuffer.write(plain);
            }
        }

        private McProtocolIO.Packet tryReadPacket() throws IOException {
            byte[] buffer = this.decryptedBuffer.toByteArray();
            if (buffer.length == 0) {
                return null;
            }
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(buffer));
            int packetLength;
            int headerLength;
            try {
                packetLength = McProtocolIO.readVarInt(in);
                headerLength = buffer.length - in.available();
            } catch (IOException e) {
                return null;
            }
            if (packetLength < 0 || headerLength + packetLength > buffer.length) {
                return null;
            }
            byte[] packetBytes = new byte[packetLength];
            System.arraycopy(buffer, headerLength, packetBytes, 0, packetLength);
            int consumed = headerLength + packetLength;
            this.decryptedBuffer.reset();
            if (consumed < buffer.length) {
                this.decryptedBuffer.write(buffer, consumed, buffer.length - consumed);
            }
            DataInputStream payload = new DataInputStream(new ByteArrayInputStream(packetBytes));
            if (this.compressionThreshold >= 0) {
                int dataLength = McProtocolIO.readVarInt(payload);
                byte[] rest = new byte[payload.available()];
                payload.readFully(rest);
                if (dataLength == 0) {
                    payload = new DataInputStream(new ByteArrayInputStream(rest));
                } else {
                    payload = new DataInputStream(new ByteArrayInputStream(inflate(rest, dataLength)));
                }
            }
            int packetId = McProtocolIO.readVarInt(payload);
            byte[] remaining = new byte[payload.available()];
            payload.readFully(remaining);
            return new McProtocolIO.Packet(packetId, remaining);
        }

        private static byte[] deflate(byte[] data) {
            Deflater deflater = new Deflater();
            deflater.setInput(data);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            while (!deflater.finished()) {
                int n = deflater.deflate(buf);
                out.write(buf, 0, n);
            }
            deflater.end();
            return out.toByteArray();
        }

        private static byte[] inflate(byte[] compressed, int expected) throws IOException {
            Inflater inflater = new Inflater();
            inflater.setInput(compressed);
            byte[] result = new byte[expected];
            try {
                int n = inflater.inflate(result);
                if (n != expected) {
                    throw new IOException("Bad compressed packet size: " + n + " != " + expected);
                }
                return result;
            } catch (DataFormatException e) {
                throw new IOException("Bad compressed packet", e);
            } finally {
                inflater.end();
            }
        }
    }
}
