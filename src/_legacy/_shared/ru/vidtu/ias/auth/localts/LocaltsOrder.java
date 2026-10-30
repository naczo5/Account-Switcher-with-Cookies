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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Full details of a Localts order, including packaged items.
 *
 * @param success         Whether the request succeeded
 * @param orderId         The order identifier
 * @param status          Packaging stage: PENDING, PACKAGING, or PACKAGED
 * @param orderStatus     Overall order status: PENDING, PROCESSING, COMPLETE, PARTIALLY_REFUNDED, or REFUNDED
 * @param refundedCredits Credits refunded for this order, or null
 * @param refundedItems   Number of items refunded, or null
 * @param replacement     Replacement details, or null
 * @param productName     Name of the product, or null if not packaged yet
 * @param items           Purchased items (empty if not packaged or fully refunded)
 * @author VidTu
 */
public record LocaltsOrder(
        boolean success,
        @NotNull String orderId,
        @NotNull String status,
        @NotNull String orderStatus,
        @Nullable Integer refundedCredits,
        @Nullable Integer refundedItems,
        @Nullable LocaltsOrderReplacement replacement,
        @Nullable String productName,
        @NotNull List<LocaltsOrderItem> items
) {
    /**
     * Whether the order has packaged items ready for import.
     *
     * @return {@code true} if status is PACKAGED and items are present
     */
    public boolean hasPackagedItems() {
        if ("REFUNDED".equalsIgnoreCase(this.orderStatus)) {
            return false;
        }
        return "PACKAGED".equalsIgnoreCase(this.status) && !this.items.isEmpty();
    }
}
