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
import java.util.Map;
import java.util.Objects;

/**
 * A store product available on Localts.
 */
public final class LocaltsProduct {
    private final String id;
    private final String name;
    private final String description;
    private final String category;
    private final int priceInCredits;
    private final int stock;
    private final String type;
    private final String logoUrl;
    private final List<String> tags;
    private final Map<String, Integer> quantityDiscounts;

    public LocaltsProduct(String id, String name, String description, String category,
                          int priceInCredits, int stock, String type, String logoUrl,
                          List<String> tags, Map<String, Integer> quantityDiscounts) {
        this.id = id != null ? id : "";
        this.name = name != null ? name : "";
        this.description = description != null ? description : "";
        this.category = category != null ? category : "";
        this.priceInCredits = priceInCredits;
        this.stock = stock;
        this.type = type != null ? type : "";
        this.logoUrl = logoUrl;
        this.tags = tags != null ? Collections.unmodifiableList(tags) : Collections.<String>emptyList();
        this.quantityDiscounts = quantityDiscounts != null ? Collections.unmodifiableMap(quantityDiscounts) : Collections.<String, Integer>emptyMap();
    }

    public String id() {
        return this.id;
    }

    public String name() {
        return this.name;
    }

    public String description() {
        return this.description;
    }

    public String category() {
        return this.category;
    }

    public int priceInCredits() {
        return this.priceInCredits;
    }

    public int stock() {
        return this.stock;
    }

    public String type() {
        return this.type;
    }

    public String logoUrl() {
        return this.logoUrl;
    }

    public List<String> tags() {
        return this.tags;
    }

    public Map<String, Integer> quantityDiscounts() {
        return this.quantityDiscounts;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LocaltsProduct that = (LocaltsProduct) o;
        return priceInCredits == that.priceInCredits && stock == that.stock &&
                Objects.equals(id, that.id) && Objects.equals(name, that.name) &&
                Objects.equals(description, that.description) && Objects.equals(category, that.category) &&
                Objects.equals(type, that.type) && Objects.equals(logoUrl, that.logoUrl) &&
                Objects.equals(tags, that.tags) && Objects.equals(quantityDiscounts, that.quantityDiscounts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, description, category, priceInCredits, stock, type, logoUrl, tags, quantityDiscounts);
    }

    @Override
    public String toString() {
        return "LocaltsProduct[id=" + id + ", name=" + name + ", priceInCredits=" + priceInCredits + ", stock=" + stock + "]";
    }
}
