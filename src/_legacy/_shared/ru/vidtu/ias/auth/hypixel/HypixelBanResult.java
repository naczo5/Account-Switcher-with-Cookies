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

/**
 * Result of a Hypixel ban check.
 * <p>
 * Ban-check logic adapted from <a href="https://github.com/cooldood-dev/mcchecker">mcchecker</a>.
 */
public final class HypixelBanResult {
    public enum Status {
        UNBANNED,
        BANNED,
        ERROR
    }

    private final Status status;
    private final String banType;
    private final String duration;
    private final String reason;
    private final String rawMessage;
    private final String errorMessage;

    private HypixelBanResult(
            Status status,
            String banType,
            String duration,
            String reason,
            String rawMessage,
            String errorMessage
    ) {
        this.status = status;
        this.banType = banType;
        this.duration = duration;
        this.reason = reason;
        this.rawMessage = rawMessage;
        this.errorMessage = errorMessage;
    }

    public static HypixelBanResult unbanned() {
        return new HypixelBanResult(Status.UNBANNED, null, null, null, null, null);
    }

    public static HypixelBanResult banned(
            String banType,
            String duration,
            String reason,
            String rawMessage
    ) {
        return new HypixelBanResult(Status.BANNED, banType, duration, reason, rawMessage, null);
    }

    public static HypixelBanResult error(String message) {
        return new HypixelBanResult(Status.ERROR, null, null, null, null, message);
    }

    public static HypixelBanResult of(
            Status status,
            String banType,
            String duration,
            String reason,
            String rawMessage,
            String errorMessage
    ) {
        return new HypixelBanResult(status, banType, duration, reason, rawMessage, errorMessage);
    }

    public Status status() {
        return this.status;
    }

    public String banType() {
        return this.banType;
    }

    public String duration() {
        return this.duration;
    }

    public String reason() {
        return this.reason;
    }

    public String rawMessage() {
        return this.rawMessage;
    }

    public String errorMessage() {
        return this.errorMessage;
    }
}
