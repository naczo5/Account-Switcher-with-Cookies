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

public class LocaltsException extends RuntimeException {
    private final String translationKey;
    private final Object[] args;

    public LocaltsException(String message, String translationKey, Object... args) {
        super(message);
        this.translationKey = translationKey;
        this.args = args != null ? args : new Object[0];
    }

    public LocaltsException(String message, Throwable cause, String translationKey, Object... args) {
        super(message, cause);
        this.translationKey = translationKey;
        this.args = args != null ? args : new Object[0];
    }

    public String translationKey() {
        return this.translationKey;
    }

    public Object[] args() {
        return this.args;
    }
}
