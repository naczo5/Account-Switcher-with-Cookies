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

import javax.swing.SwingUtilities;
import java.awt.FileDialog;
import java.awt.Frame;
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
     * Opens a native file-open dialog on the AWT event thread.
     *
     * @param title     Dialog title
     * @param startPath Optional starting file or directory
     * @return Selected absolute path, or {@code null} if cancelled
     */
    @Nullable
    static String pickFile(@Nullable String title, @Nullable String startPath) throws Exception {
        final String[] result = new String[1];
        SwingUtilities.invokeAndWait(() -> {
            FileDialog dialog = new FileDialog((Frame) null, title, FileDialog.LOAD);
            dialog.setAlwaysOnTop(true);
            if (startPath != null && !startPath.isBlank()) {
                File file = new File(startPath);
                File parent = file.isDirectory() ? file : file.getParentFile();
                if (parent != null && parent.isDirectory()) {
                    dialog.setDirectory(parent.getAbsolutePath());
                }
                if (file.isFile()) {
                    dialog.setFile(file.getName());
                }
            }
            dialog.setVisible(true);
            String directory = dialog.getDirectory();
            String name = dialog.getFile();
            if (directory != null && name != null) {
                result[0] = new File(directory, name).getAbsolutePath();
            }
            dialog.dispose();
        });
        return result[0];
    }
}
