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

package ru.vidtu.ias.utils.exceptions;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * A runtime exception with "probable" user-friendly message.
 *
 * @author VidTu
 */
public final class FriendlyException extends RuntimeException {
    /**
     * Message translation key.
     */
    @NotNull
    private final String key;

    /**
     * Message translation arguments.
     */
    @NotNull
    private final Object[] args;

    /**
     * Creates a new exception.
     *
     * @param message Target message
     * @param key     Message translation key
     */
    @Contract(pure = true)
    public FriendlyException(@NotNull String message, @NotNull String key) {
        this(message, null, key, new Object[0]);
    }

    /**
     * Creates a new exception.
     *
     * @param message Target message
     * @param cause   Suppressed exception cause
     * @param key     Message translation key
     */
    @Contract(pure = true)
    public FriendlyException(@NotNull String message, @Nullable Throwable cause, @NotNull String key) {
        this(message, cause, key, new Object[0]);
    }

    /**
     * Creates a new exception with translation arguments.
     *
     * @param message Target message
     * @param key     Message translation key
     * @param args    Message translation arguments
     */
    @Contract(pure = true)
    public FriendlyException(@NotNull String message, @NotNull String key, Object... args) {
        this(message, null, key, args);
    }

    /**
     * Creates a new exception with translation arguments.
     *
     * @param message Target message
     * @param cause   Suppressed exception cause
     * @param key     Message translation key
     * @param args    Message translation arguments
     */
    @Contract(pure = true)
    public FriendlyException(@NotNull String message, @Nullable Throwable cause, @NotNull String key, Object... args) {
        super(message + " (friendly key: " + key + ")", cause);
        this.key = key;
        this.args = args != null ? args : new Object[0];
    }

    /**
     * Gets the key.
     *
     * @return Message translation key
     */
    @Contract(pure = true)
    @NotNull
    public String key() {
        return this.key;
    }

    /**
     * Gets the translation arguments.
     *
     * @return Message translation arguments
     */
    @Contract(pure = true)
    @NotNull
    public Object[] args() {
        return this.args;
    }

    /**
     * Gets the friendly exception from the causal chain.
     *
     * @param root Causal chain root exception
     * @return First friendly exception in the causal chain, {@code null} if none, causal loop detected, or causal stack is too big
     */
    @Contract(pure = true)
    @Nullable
    public static FriendlyException friendlyInChain(Throwable root) {
        // Causal loop detection set.
        Set<Throwable> dejaVu = Collections.newSetFromMap(new IdentityHashMap<>(8));

        // 256 is the (arbitrarily-chosen) limit for causal stack.
        for (int i = 0; i < 256; i++) {
            // Break if null (reached the end) or loop detected.
            if (root == null || !dejaVu.add(root)) break;

            // Return if friendly exception.
            if (root instanceof FriendlyException fr) return fr;

            // Next cause.
            root = root.getCause();
        }

        // Not found. (or reached the 256 limit)
        return null;
    }
}
