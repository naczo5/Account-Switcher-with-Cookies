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
 * Summary of an order returned in order listings.
 */
public final class LocaltsOrderSummary {
    private final String id;
    private final String productId;
    private final String productType;
    private final long timestamp;

    public LocaltsOrderSummary(String id, String productId, String productType, long timestamp) {
        this.id = id != null ? id : "";
        this.productId = productId != null ? productId : "";
        this.productType = productType != null ? productType : "";
        this.timestamp = timestamp;
    }

    public String id() {
        return this.id;
    }

    public String productId() {
        return this.productId;
    }

    public String productType() {
        return this.productType;
    }

    public long timestamp() {
        return this.timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LocaltsOrderSummary that = (LocaltsOrderSummary) o;
        return timestamp == that.timestamp && Objects.equals(id, that.id) &&
                Objects.equals(productId, that.productId) && Objects.equals(productType, that.productType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, productId, productType, timestamp);
    }

    @Override
    public String toString() {
        return "LocaltsOrderSummary[id=" + id + ", productId=" + productId + ", timestamp=" + timestamp + "]";
    }
}
