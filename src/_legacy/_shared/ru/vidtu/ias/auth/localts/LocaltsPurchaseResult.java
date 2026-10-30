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
 * Result of a product purchase.
 */
public final class LocaltsPurchaseResult {
    private final boolean success;
    private final String orderId;

    public LocaltsPurchaseResult(boolean success, String orderId) {
        this.success = success;
        this.orderId = orderId != null ? orderId : "";
    }

    public boolean success() {
        return this.success;
    }

    public String orderId() {
        return this.orderId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LocaltsPurchaseResult that = (LocaltsPurchaseResult) o;
        return success == that.success && Objects.equals(orderId, that.orderId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, orderId);
    }

    @Override
    public String toString() {
        return "LocaltsPurchaseResult[success=" + success + ", orderId=" + orderId + "]";
    }
}
