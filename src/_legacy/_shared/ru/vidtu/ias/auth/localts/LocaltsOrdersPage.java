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
 * Paginated list of orders from the Localts API.
 */
public final class LocaltsOrdersPage {
    private final boolean success;
    private final List<LocaltsOrderSummary> orders;
    private final int page;
    private final int size;
    private final int totalPages;
    private final int totalElements;

    public LocaltsOrdersPage(boolean success, List<LocaltsOrderSummary> orders, int page, int size, int totalPages, int totalElements) {
        this.success = success;
        this.orders = orders != null ? Collections.unmodifiableList(orders) : Collections.<LocaltsOrderSummary>emptyList();
        this.page = page;
        this.size = size;
        this.totalPages = totalPages;
        this.totalElements = totalElements;
    }

    public boolean success() {
        return this.success;
    }

    public List<LocaltsOrderSummary> orders() {
        return this.orders;
    }

    public int page() {
        return this.page;
    }

    public int size() {
        return this.size;
    }

    public int totalPages() {
        return this.totalPages;
    }

    public int totalElements() {
        return this.totalElements;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LocaltsOrdersPage that = (LocaltsOrdersPage) o;
        return success == that.success && page == that.page && size == that.size &&
                totalPages == that.totalPages && totalElements == that.totalElements &&
                Objects.equals(orders, that.orders);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, orders, page, size, totalPages, totalElements);
    }

    @Override
    public String toString() {
        return "LocaltsOrdersPage[page=" + page + ", totalElements=" + totalElements + "]";
    }
}
