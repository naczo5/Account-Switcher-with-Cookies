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
 * Replacement details for an order, if one was opened.
 */
public final class LocaltsOrderReplacement {
    private final String id;
    private final String status;
    private final int items;

    public LocaltsOrderReplacement(String id, String status, int items) {
        this.id = id != null ? id : "";
        this.status = status != null ? status : "";
        this.items = items;
    }

    public String id() {
        return this.id;
    }

    public String status() {
        return this.status;
    }

    public int items() {
        return this.items;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LocaltsOrderReplacement that = (LocaltsOrderReplacement) o;
        return items == that.items && Objects.equals(id, that.id) && Objects.equals(status, that.status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, status, items);
    }

    @Override
    public String toString() {
        return "LocaltsOrderReplacement[id=" + id + ", status=" + status + ", items=" + items + "]";
    }
}
