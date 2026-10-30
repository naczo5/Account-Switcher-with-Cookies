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

import java.util.List;

/**
 * Paginated list of orders from the Localts API.
 *
 * @param success       Whether the request succeeded
 * @param orders        Orders in the current page
 * @param page          Current zero-based page index
 * @param size          Current page size
 * @param totalPages    Total number of available pages
 * @param totalElements Total number of orders across all pages
 * @author VidTu
 */
public record LocaltsOrdersPage(
        boolean success,
        @NotNull List<LocaltsOrderSummary> orders,
        int page,
        int size,
        int totalPages,
        int totalElements
) {
}
