package the_fireplace.ias.gui;

import javax.swing.SwingUtilities;
import javax.swing.JFileChooser;
import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * OS file dialogs for cookie import.
 */
public final class CookieFileDialogs {
    private CookieFileDialogs() {
    }

    public static String pickFile(String title, String startPath) throws Exception {
        final String[] result = new String[1];
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                try {
                    FileDialog dialog = new FileDialog((Frame) null, title, FileDialog.LOAD);
                    dialog.setAlwaysOnTop(true);
                    if (startPath != null && !startPath.trim().isEmpty()) {
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
                } catch (Throwable ignored) {
                    JFileChooser chooser = new JFileChooser();
                    chooser.setDialogTitle(title);
                    if (startPath != null && !startPath.trim().isEmpty()) {
                        chooser.setSelectedFile(new File(startPath));
                    }
                    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                        result[0] = chooser.getSelectedFile().getAbsolutePath();
                    }
                }
            }
        });
        return result[0];
    }

    /**
     * Multi-file picker for cookie imports. Returns an empty list when the
     * user cancels. Supports {@code ;}-separated manual entry via the caller.
     */
    public static List<String> pickFiles(String title, String startPath) throws Exception {
        final List<String> result = new ArrayList<String>();
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                try {
                    FileDialog dialog = new FileDialog((Frame) null, title, FileDialog.LOAD);
                    dialog.setAlwaysOnTop(true);
                    try {
                        dialog.setMultipleMode(true);
                    } catch (Throwable ignored) {
                    }
                    if (startPath != null && !startPath.trim().isEmpty()) {
                        String first = startPath.split(";")[0].trim();
                        File file = new File(first);
                        File parent = file.isDirectory() ? file : file.getParentFile();
                        if (parent != null && parent.isDirectory()) {
                            dialog.setDirectory(parent.getAbsolutePath());
                        }
                    }
                    dialog.setVisible(true);
                    File[] files = null;
                    try {
                        files = dialog.getFiles();
                    } catch (Throwable ignored) {
                        files = null;
                    }
                    if (files != null && files.length > 0) {
                        for (File f : files) {
                            if (f != null) {
                                result.add(f.getAbsolutePath());
                            }
                        }
                    } else {
                        String directory = dialog.getDirectory();
                        String name = dialog.getFile();
                        if (directory != null && name != null) {
                            result.add(new File(directory, name).getAbsolutePath());
                        }
                    }
                    dialog.dispose();
                    if (!result.isEmpty()) {
                        return;
                    }
                } catch (Throwable ignored) {
                }
                try {
                    JFileChooser chooser = new JFileChooser();
                    chooser.setDialogTitle(title);
                    chooser.setMultiSelectionEnabled(true);
                    if (startPath != null && !startPath.trim().isEmpty()) {
                        String first = startPath.split(";")[0].trim();
                        File file = new File(first);
                        if (file.isDirectory()) {
                            chooser.setCurrentDirectory(file);
                        } else {
                            if (file.getParentFile() != null && file.getParentFile().isDirectory()) {
                                chooser.setCurrentDirectory(file.getParentFile());
                            }
                            chooser.setSelectedFile(file);
                        }
                    }
                    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                        File[] files = chooser.getSelectedFiles();
                        if (files != null && files.length > 0) {
                            for (File f : files) {
                                if (f != null) {
                                    result.add(f.getAbsolutePath());
                                }
                            }
                        } else if (chooser.getSelectedFile() != null) {
                            result.add(chooser.getSelectedFile().getAbsolutePath());
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
        });
        return result;
    }
}
