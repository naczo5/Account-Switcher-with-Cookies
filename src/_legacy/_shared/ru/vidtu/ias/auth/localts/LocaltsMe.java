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

package ru.vidtu.ias.auth.localts;

import java.util.Objects;

/**
 * Information about the authenticated Localts user and account balance.
 */
public final class LocaltsMe {
    private final boolean success;
    private final String username;
    private final long balance;

    public LocaltsMe(boolean success, String username, long balance) {
        this.success = success;
        this.username = username != null ? username : "";
        this.balance = balance;
    }

    public boolean success() {
        return this.success;
    }

    public String username() {
        return this.username;
    }

    public long balance() {
        return this.balance;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LocaltsMe localtsMe = (LocaltsMe) o;
        return success == localtsMe.success && balance == localtsMe.balance && Objects.equals(username, localtsMe.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, username, balance);
    }

    @Override
    public String toString() {
        return "LocaltsMe[success=" + success + ", username=" + username + ", balance=" + balance + "]";
    }
}
