package the_fireplace.ias.gui;

import javax.swing.SwingUtilities;
import javax.swing.JFileChooser;
import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;

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
}
