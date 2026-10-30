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

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Full details of a Localts order, including packaged items.
 */
public final class LocaltsOrder {
    private final boolean success;
    private final String orderId;
    private final String status;
    private final String orderStatus;
    private final Integer refundedCredits;
    private final Integer refundedItems;
    private final LocaltsOrderReplacement replacement;
    private final String productName;
    private final List<LocaltsOrderItem> items;

    public LocaltsOrder(boolean success, String orderId, String status, String orderStatus,
                        Integer refundedCredits, Integer refundedItems, LocaltsOrderReplacement replacement,
                        String productName, List<LocaltsOrderItem> items) {
        this.success = success;
        this.orderId = orderId != null ? orderId : "";
        this.status = status != null ? status : "";
        this.orderStatus = orderStatus != null ? orderStatus : "";
        this.refundedCredits = refundedCredits;
        this.refundedItems = refundedItems;
        this.replacement = replacement;
        this.productName = productName;
        this.items = items != null ? Collections.unmodifiableList(items) : Collections.<LocaltsOrderItem>emptyList();
    }

    public boolean success() {
        return this.success;
    }

    public String orderId() {
        return this.orderId;
    }

    public String status() {
        return this.status;
    }

    public String orderStatus() {
        return this.orderStatus;
    }

    public Integer refundedCredits() {
        return this.refundedCredits;
    }

    public Integer refundedItems() {
        return this.refundedItems;
    }

    public LocaltsOrderReplacement replacement() {
        return this.replacement;
    }

    public String productName() {
        return this.productName;
    }

    public List<LocaltsOrderItem> items() {
        return this.items;
    }

    public boolean hasPackagedItems() {
        if ("REFUNDED".equalsIgnoreCase(this.orderStatus)) {
            return false;
        }
        return "PACKAGED".equalsIgnoreCase(this.status) && !this.items.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LocaltsOrder that = (LocaltsOrder) o;
        return success == that.success && Objects.equals(orderId, that.orderId) &&
                Objects.equals(status, that.status) && Objects.equals(orderStatus, that.orderStatus) &&
                Objects.equals(refundedCredits, that.refundedCredits) && Objects.equals(refundedItems, that.refundedItems) &&
                Objects.equals(replacement, that.replacement) && Objects.equals(productName, that.productName) &&
                Objects.equals(items, that.items);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, orderId, status, orderStatus, refundedCredits, refundedItems, replacement, productName, items);
    }

    @Override
    public String toString() {
        return "LocaltsOrder[orderId=" + orderId + ", status=" + status + ", orderStatus=" + orderStatus + "]";
    }
}
