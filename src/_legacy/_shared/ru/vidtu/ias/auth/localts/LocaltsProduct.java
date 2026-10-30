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
import java.util.Map;

/**
 * A store product available on Localts.
 *
 * @param id                Product identifier
 * @param name              Product display name
 * @param description       Product description
 * @param category          Category name
 * @param priceInCredits    Price in credits
 * @param stock             Available stock count
 * @param type              Product type (e.g. text)
 * @param logoUrl           Relative logo URL
 * @param tags              Tags associated with the product
 * @param quantityDiscounts Quantity discount tiers (count -> percentage)
 * @author VidTu
 */
public record LocaltsProduct(
        @NotNull String id,
        @NotNull String name,
        @NotNull String description,
        @NotNull String category,
        int priceInCredits,
        int stock,
        @NotNull String type,
        @Nullable String logoUrl,
        @NotNull List<String> tags,
        @NotNull Map<String, Integer> quantityDiscounts
) {
}
