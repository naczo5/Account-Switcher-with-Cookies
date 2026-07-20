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

package ru.vidtu.ias.screen;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.File;

/**
 * OS file dialogs for cookie import.
 *
 * @author VidTu
 */
final class CookieFileDialogs {
    /**
     * An instance of this class cannot be created.
     */
    private CookieFileDialogs() {
        throw new AssertionError("No instances.");
    }

    /**
     * Opens a native file-open dialog through LWJGL's Tiny File Dialogs binding.
     * This is part of Minecraft's runtime and does not depend on AWT being available.
     *
     * @param title     Dialog title
     * @param startPath Optional starting file or directory
     * @return Selected absolute path, or {@code null} if cancelled
     */
    @Nullable
    static String pickFile(@Nullable String title, @Nullable String startPath) {
        String initial = null;
        if (startPath != null && !startPath.isBlank()) {
            initial = new File(startPath).getAbsolutePath();
        }
        return TinyFileDialogs.tinyfd_openFileDialog(title, initial, null, null, false);
    }
}
