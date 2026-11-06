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

package the_fireplace.ias.gui;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.FileDialog;
import java.awt.Frame;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AWT/Swing file picker.
 * <p>
 * This class is launched by {@link CookieFileDialogs} as the {@code main} class of a separate JVM,
 * so it must only depend on the JDK and must not have nested/anonymous classes
 * (the single {@code .class} file is extracted from the mod and run on its own).
 * <p>
 * Minecraft's JVM may have AWT cached in headless mode, which can't be reversed at runtime.
 * A fresh JVM started with {@code -Djava.awt.headless=false} is not affected by that.
 * <p>
 * Protocol: five UTF-8 lines on stdin (title, start path, multiple {@code 1}/{@code 0},
 * comma-separated filters, filter description). Selected files are printed to stdout as
 * {@link #RESULT_PREFIX}-prefixed lines. Exit code {@code 0} means the dialog was shown
 * (no paths means cancelled), any other code means the dialog couldn't be shown.
 *
 */
public final class FilePickerMain {
    /**
     * Prefix of the stdout lines that contain selected paths.
     */
    static final String RESULT_PREFIX = "IAS-PATH:";

    /**
     * An instance of this class cannot be created.
     */
    private FilePickerMain() {
        throw new AssertionError("No instances.");
    }

    /**
     * Entry point of the separate picker JVM.
     *
     * @param args Ignored, the input is read from stdin
     */
    public static void main(String[] args) {
        int code = 2;
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
            PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, "UTF-8");
            String title = in.readLine();
            String startPath = in.readLine();
            boolean multiple = "1".equals(in.readLine());
            String rawFilters = in.readLine();
            String filterDescription = in.readLine();
            watchParent(in);
            String[] filters = rawFilters == null || rawFilters.trim().isEmpty() ? null : rawFilters.split(",");

            List<String> files;
            try {
                files = pickAwt(title, startPath, filters, multiple);
            } catch (Throwable t) {
                files = pickSwingOnEdt(title, startPath, filters, filterDescription, multiple);
            }
            for (String file : files) {
                out.println(RESULT_PREFIX + file);
            }
            out.flush();
            code = 0;
        } catch (Throwable t) {
            t.printStackTrace();
        }
        System.exit(code);
    }

    /**
     * The parent keeps our stdin open while the dialog is shown. If it closes (the game quit or crashed),
     * exit so that the dialog isn't left on the desktop with no owner.
     */
    private static void watchParent(final BufferedReader in) {
        Thread watcher = new Thread(() -> {
            try {
                while (in.read() != -1) {
                    // Nothing else is expected, just drain.
                }
            } catch (Throwable ignored) {
            }
            Runtime.getRuntime().halt(1);
        }, "IAS/ParentWatcher");
        watcher.setDaemon(true);
        watcher.start();
    }

    private static List<String> pickSwingOnEdt(String title, String startPath, String[] filters,
                                               String filterDescription, boolean multiple) throws Exception {
        final List<List<String>> result = new ArrayList<List<String>>(1);
        final Throwable[] failure = new Throwable[1];
        final String t = title;
        final String s = startPath;
        final String[] f = filters;
        final String d = filterDescription;
        final boolean m = multiple;
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.add(pickSwing(t, s, f, d, m));
            } catch (Throwable e) {
                failure[0] = e;
            }
        });
        if (failure[0] != null) {
            throw new Exception("Unable to open file browser.", failure[0]);
        }
        return result.isEmpty() ? Collections.<String>emptyList() : result.get(0);
    }

    /**
     * Shows the native AWT file dialog.
     */
    static List<String> pickAwt(String title, String startPath, String[] filters, boolean multiple) {
        Frame frame = new Frame();
        frame.setAlwaysOnTop(true);
        FileDialog dialog = new FileDialog(frame, title != null && !title.isEmpty() ? title : "Open", FileDialog.LOAD);
        try {
            dialog.setAlwaysOnTop(true);
            dialog.setMultipleMode(multiple);
            if (filters != null && filters.length > 0) {
                dialog.setFilenameFilter(filenameFilter(filters));
                dialog.setFile(filters[0]);
            }

            String normalized = defaultPath(startPath);
            if (normalized != null) {
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
                List<String> selected = new ArrayList<String>(files.length);
                for (File file : files) {
                    if (file != null) {
                        selected.add(file.getAbsolutePath());
                    }
                }
                return selected;
            }

            String directory = dialog.getDirectory();
            String name = dialog.getFile();
            if (directory != null && name != null) {
                return Collections.singletonList(new File(directory, name).getAbsolutePath());
            }
            return Collections.emptyList();
        } finally {
            try {
                dialog.dispose();
            } catch (Throwable ignored) {
            }
            try {
                frame.dispose();
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Shows the Swing file chooser. Must be called on the EDT.
     */
    static List<String> pickSwing(String title, String startPath, String[] filters,
                                  String filterDescription, boolean multiple) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title != null && !title.isEmpty() ? title : "Open");
        chooser.setMultiSelectionEnabled(multiple);
        if (filters != null && filters.length > 0) {
            List<String> extensions = new ArrayList<String>(filters.length);
            for (String filter : filters) {
                if (filter.startsWith("*.")) {
                    extensions.add(filter.substring(2));
                }
            }
            if (!extensions.isEmpty()) {
                chooser.setFileFilter(new FileNameExtensionFilter(
                        filterDescription != null && !filterDescription.isEmpty() ? filterDescription : "Files",
                        extensions.toArray(new String[0])));
            }
        }

        String normalized = defaultPath(startPath);
        if (normalized != null) {
            File file = new File(normalized);
            File parent = file.isDirectory() ? file : file.getParentFile();
            if (parent != null && parent.isDirectory()) {
                chooser.setCurrentDirectory(parent);
            }
            if (file.isFile()) {
                chooser.setSelectedFile(file);
            }
        }

        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
            return Collections.emptyList();
        }
        if (multiple) {
            File[] files = chooser.getSelectedFiles();
            List<String> selected = new ArrayList<String>(files.length);
            for (File file : files) {
                if (file != null) {
                    selected.add(file.getAbsolutePath());
                }
            }
            if (selected.isEmpty() && chooser.getSelectedFile() != null) {
                selected.add(chooser.getSelectedFile().getAbsolutePath());
            }
            return selected;
        }
        File selected = chooser.getSelectedFile();
        return selected != null ? Collections.singletonList(selected.getAbsolutePath()) : Collections.<String>emptyList();
    }

    /**
     * Normalizes a user-entered start path.
     *
     * @return Trimmed path without surrounding quotes, or {@code null} if blank
     */
    static String defaultPath(String startPath) {
        if (startPath == null) {
            return null;
        }
        startPath = startPath.trim();
        if (startPath.length() >= 2 && startPath.charAt(0) == '"' && startPath.charAt(startPath.length() - 1) == '"') {
            startPath = startPath.substring(1, startPath.length() - 1).trim();
        }
        return startPath.isEmpty() ? null : startPath;
    }

    private static FilenameFilter filenameFilter(String[] filters) {
        return (dir, name) -> {
            String lower = name.toLowerCase();
            for (String filter : filters) {
                String normalized = filter.trim().toLowerCase();
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
