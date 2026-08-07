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

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.jetbrains.annotations.Nullable;

import javax.swing.SwingUtilities;
import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

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
     * Opens a native file-open dialog.
     *
     * @param title     Dialog title
     * @param startPath Optional starting file or directory
     * @return Selected absolute path, or {@code null} if cancelled
     */
    @Nullable
    static String pickFile(@Nullable String title, @Nullable String startPath) throws Exception {
        List<String> files = pickFiles(title, startPath, null, "All Files", false);
        return files.isEmpty() ? null : files.get(0);
    }

    /**
     * Opens a native multi-file-open dialog for cookie imports.
     *
     * @param title     Dialog title
     * @param startPath Optional starting file or directory
     * @return Selected absolute paths, or an empty list if cancelled
     */
    static List<String> pickCookieFiles(@Nullable String title, @Nullable String startPath) throws Exception {
        return pickFiles(title, startPath, null, "All Files", true);
    }

    /**
     * Opens a native multi-file-open dialog for token imports.
     *
     * @param title     Dialog title
     * @param startPath Optional starting file or directory
     * @return Selected absolute paths, or an empty list if cancelled
     */
    static List<String> pickTokenFiles(@Nullable String title, @Nullable String startPath) throws Exception {
        return pickFiles(title, startPath, new String[]{"*.txt"}, "Text Files", true);
    }

    /**
     * Opens a native PNG file-open dialog for skin uploads.
     *
     * @param title     Dialog title
     * @param startPath Optional starting file or directory
     * @return Selected absolute path, or {@code null} if cancelled
     */
    @Nullable
    static String pickPngFile(@Nullable String title, @Nullable String startPath) throws Exception {
        List<String> files = pickFiles(title, startPath, new String[]{"*.png"}, "PNG Files", false);
        return files.isEmpty() ? null : files.get(0);
    }

    private static List<String> pickFiles(@Nullable String title, @Nullable String startPath, @Nullable String[] filters,
            @Nullable String filterDescription, boolean multiple) throws Exception {
        List<String> tiny = pickFilesTiny(title, startPath, filters, filterDescription, multiple);
        if (tiny != null) {
            return tiny;
        }
        return pickFilesAwt(title, startPath, filters, multiple);
    }

    @Nullable
    private static List<String> pickFilesTiny(@Nullable String title, @Nullable String startPath, @Nullable String[] filters,
            @Nullable String filterDescription, boolean multiple) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer patterns = null;
            if (filters != null && filters.length > 0) {
                patterns = stack.mallocPointer(filters.length);
                for (String filter : filters) {
                    patterns.put(stack.UTF8(filter));
                }
                patterns.flip();
            }
            String selected = TinyFileDialogs.tinyfd_openFileDialog(title, defaultPath(startPath), patterns, filterDescription, multiple);
            if (selected == null || selected.isBlank()) {
                return List.of();
            }
            return splitTinySelection(selected);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static List<String> splitTinySelection(String selected) {
        String[] parts = selected.split("\\|");
        List<String> files = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (!part.isBlank()) {
                files.add(new File(part).getAbsolutePath());
            }
        }
        return files;
    }

    private static List<String> pickFilesAwt(@Nullable String title, @Nullable String startPath, @Nullable String[] filters,
            boolean multiple) throws Exception {
        AtomicReference<List<String>> result = new AtomicReference<>(List.of());
        SwingUtilities.invokeAndWait(() -> {
            Frame frame = new Frame();
            frame.setAlwaysOnTop(true);
            FileDialog dialog = new FileDialog(frame, title, FileDialog.LOAD);
            try {
                dialog.setAlwaysOnTop(true);
                dialog.setMultipleMode(multiple);
                if (filters != null && filters.length > 0) {
                    dialog.setFilenameFilter(filenameFilter(filters));
                    dialog.setFile(filters[0]);
                }

                String normalized = defaultPath(startPath);
                if (normalized != null && !normalized.isBlank()) {
                    File file = new File(normalized);
                    File parent = file.isDirectory() ? file : file.getParentFile();
                    if (parent != null && parent.isDirectory()) {
                        dialog.setDirectory(parent.getAbsolutePath());
                    }
                    if (file.isFile()) {
                        dialog.setFile(file.getName());
                    }
                }
                dialog.setVisible(true);

                if (multiple) {
                    File[] files = dialog.getFiles();
                    List<String> selected = new ArrayList<>(files.length);
                    for (File file : files) {
                        if (file != null) {
                            selected.add(file.getAbsolutePath());
                        }
                    }
                    result.set(selected);
                    return;
                }

                String directory = dialog.getDirectory();
                String name = dialog.getFile();
                if (directory != null && name != null) {
                    result.set(List.of(new File(directory, name).getAbsolutePath()));
                }
            } finally {
                dialog.dispose();
                frame.dispose();
            }
        });
        return result.get();
    }

    @Nullable
    private static String defaultPath(@Nullable String startPath) {
        if (startPath == null || startPath.isBlank()) {
            return null;
        }
        startPath = startPath.strip();
        if (startPath.length() >= 2 && startPath.charAt(0) == '"' && startPath.charAt(startPath.length() - 1) == '"') {
            startPath = startPath.substring(1, startPath.length() - 1).strip();
        }
        return startPath;
    }

    private static FilenameFilter filenameFilter(String[] filters) {
        return (dir, name) -> {
            String lower = name.toLowerCase();
            for (String filter : filters) {
                String normalized = filter.toLowerCase();
                if (normalized.startsWith("*.")) {
                    if (lower.endsWith(normalized.substring(1))) {
                        return true;
                    }
                } else if (lower.equals(normalized)) {
                    return true;
                }
            }
            return false;
        };
    }
}
