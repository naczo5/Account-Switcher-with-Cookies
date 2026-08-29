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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Minimal Minecraft protocol 1.8 helpers for Hypixel ban checks.
 */
final class McProtocolIO {
    private McProtocolIO() {
    }

    static byte[] buildPacket(int packetId, byte[] payload) throws IOException {
        ByteArrayOutputStream packet = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(packet);
        writeVarInt(out, packetId);
        out.write(payload);
        byte[] data = packet.toByteArray();
        ByteArrayOutputStream framed = new ByteArrayOutputStream();
        DataOutputStream frame = new DataOutputStream(framed);
        writeVarInt(frame, data.length);
        frame.write(data);
        return framed.toByteArray();
    }

    static int readVarInt(DataInputStream in) throws IOException {
        int numRead = 0;
        int result = 0;
        byte read;
        do {
            read = in.readByte();
            int value = read & 0x7F;
            result |= value << (7 * numRead);
            numRead++;
            if (numRead > 5) {
                throw new IOException("VarInt too big");
            }
        } while ((read & 0x80) != 0);
        return result;
    }

    static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while (true) {
            if ((value & ~0x7F) == 0) {
                out.writeByte(value);
                return;
            }
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
    }

    static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    static String readString(DataInputStream in) throws IOException {
        int length = readVarInt(in);
        if (length < 0 || length > 32767) {
            throw new IOException("Invalid string length: " + length);
        }
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static byte[] readByteArray(DataInputStream in) throws IOException {
        int length = readVarInt(in);
        if (length < 0) {
            throw new IOException("Invalid byte array length: " + length);
        }
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return bytes;
    }

    static void writeByteArray(DataOutputStream out, byte[] value) throws IOException {
        writeVarInt(out, value.length);
        out.write(value);
    }

    static void writeUnsignedShort(DataOutputStream out, int value) throws IOException {
        out.writeShort(value & 0xFFFF);
    }

    static Packet readPacket(DataInputStream in) throws IOException {
        int length = readVarInt(in);
        if (length < 0) {
            throw new IOException("Invalid packet length: " + length);
        }
        byte[] data = new byte[length];
        in.readFully(data);
        DataInputStream payload = new DataInputStream(new ByteArrayInputStream(data));
        int packetId = readVarInt(payload);
        byte[] remaining = new byte[payload.available()];
        payload.readFully(remaining);
        return new Packet(packetId, remaining);
    }

    static final class Packet {
        final int id;
        final byte[] data;

        Packet(int id, byte[] data) {
            this.id = id;
            this.data = data;
        }

        DataInputStream reader() {
            return new DataInputStream(new ByteArrayInputStream(this.data));
        }
    }
}
