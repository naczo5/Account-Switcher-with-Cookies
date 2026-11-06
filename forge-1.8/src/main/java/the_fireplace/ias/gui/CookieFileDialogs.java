package the_fireplace.ias.gui;

import javax.swing.SwingUtilities;
import javax.swing.JFileChooser;
import java.awt.FileDialog;
import java.awt.Frame;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * OS file dialogs for cookie import.
 */
public final class CookieFileDialogs {
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final String PICKER_CLASS = FilePickerMain.class.getName().replace('.', '/') + ".class";
    private static Path pickerDir;

    private CookieFileDialogs() {
    }

    public static String pickFile(String title, String startPath) throws Exception {
        if (isHeadless()) {
            List<String> external = pickExternal(title, startPath, null, "All Files", false);
            if (external != null) {
                return external.isEmpty() ? null : external.get(0);
            }
        }
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
        if (isHeadless()) {
            String first = startPath == null ? null : startPath.split(";")[0].trim();
            List<String> external = pickExternal(title, first, null, "All Files", true);
            if (external != null) {
                return external;
            }
        }
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

    /**
     * Single-file PNG picker for skin uploads. Returns {@code null} when cancelled.
     */
    public static String pickPngFile(String title, String startPath) throws Exception {
        if (isHeadless()) {
            List<String> external = pickExternal(title, startPath, new String[]{"*.png"}, "PNG Images", false);
            if (external != null) {
                return external.isEmpty() ? null : external.get(0);
            }
        }
        final String[] result = new String[1];
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                JFileChooser chooser = new JFileChooser();
                chooser.setDialogTitle(title);
                chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PNG Images (*.png)", "png"));
                if (startPath != null && !startPath.trim().isEmpty()) {
                    File file = new File(startPath.trim());
                    File parent = file.isDirectory() ? file : file.getParentFile();
                    if (parent != null && parent.isDirectory()) {
                        chooser.setCurrentDirectory(parent);
                    }
                }
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
                    result[0] = chooser.getSelectedFile().getAbsolutePath();
                }
            }
        });
        return result[0];
    }

    private static boolean isHeadless() {
        try {
            return java.awt.GraphicsEnvironment.isHeadless();
        } catch (Throwable t) {
            return true;
        }
    }

    /**
     * Java caches the headless state when AWT is first initialized, and it can't be reset at runtime.
     * A picker in a separate JVM with {@code java.awt.headless=false} isn't affected by that.
     *
     * @return Selected paths (empty if cancelled), or {@code null} if the dialog couldn't be shown
     */
    private static List<String> pickExternal(String title, String startPath, String[] filters, String description, boolean multiple) {
        Process process = null;
        Writer stdin = null;
        try {
            Path classes = pickerClasspath();
            boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
            Path java = Paths.get(System.getProperty("java.home"), "bin", windows ? "javaw.exe" : "java");
            if (!Files.isRegularFile(java)) {
                java = Paths.get(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java");
            }

            ProcessBuilder builder = new ProcessBuilder(java.toString(),
                    "-Djava.awt.headless=false",
                    "-Dfile.encoding=UTF-8",
                    "-Xmx64M",
                    "-cp", classes.toString(),
                    FilePickerMain.class.getName());
            // Environment-provided options are applied after ours and could force headless mode again.
            builder.environment().remove("JAVA_TOOL_OPTIONS");
            builder.environment().remove("_JAVA_OPTIONS");
            builder.environment().remove("JDK_JAVA_OPTIONS");
            // Java 8 has no Redirect.DISCARD. Inheriting an invalid stderr (javaw) can fail the launch, so use the null device.
            builder.redirectError(ProcessBuilder.Redirect.appendTo(new File(System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? "NUL" : "/dev/null")));
            process = builder.start();

            // Keep stdin open until the picker is done, the picker exits when it's closed (the game quit).
            stdin = new OutputStreamWriter(process.getOutputStream(), UTF_8);
            stdin.write(singleLine(title) + "\n");
            stdin.write(singleLine(startPath) + "\n");
            stdin.write((multiple ? "1" : "0") + "\n");
            StringBuilder joined = new StringBuilder();
            if (filters != null) {
                for (String filter : filters) {
                    if (joined.length() > 0) {
                        joined.append(',');
                    }
                    joined.append(filter);
                }
            }
            stdin.write(joined + "\n");
            stdin.write(singleLine(description) + "\n");
            stdin.flush();

            List<String> selected = new ArrayList<String>();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), UTF_8));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith(FilePickerMain.RESULT_PREFIX)) {
                        String path = line.substring(FilePickerMain.RESULT_PREFIX.length());
                        if (!path.isEmpty()) {
                            selected.add(path);
                        }
                    }
                }
            } finally {
                reader.close();
            }
            return process.waitFor() == 0 ? selected : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Throwable t) {
            return null;
        } finally {
            if (stdin != null) {
                try {
                    stdin.close();
                } catch (IOException ignored) {
                }
            }
            if (process != null) {
                try {
                    process.exitValue();
                } catch (IllegalThreadStateException running) {
                    process.destroy();
                }
            }
        }
    }

    private static String singleLine(String value) {
        return value == null ? "" : value.replace('\r', ' ').replace('\n', ' ');
    }

    /**
     * Extracts {@link FilePickerMain} from the mod into a temporary directory so a separate JVM can run it.
     */
    private static synchronized Path pickerClasspath() throws IOException {
        if (pickerDir != null && Files.isRegularFile(pickerDir.resolve(PICKER_CLASS))) {
            return pickerDir;
        }
        Path dir = Files.createTempDirectory("ias-file-picker");
        dir.toFile().deleteOnExit();
        Path target = dir.resolve(PICKER_CLASS);
        Files.createDirectories(target.getParent());
        InputStream in = FilePickerMain.class.getResourceAsStream("FilePickerMain.class");
        if (in == null) {
            throw new FileNotFoundException("FilePickerMain.class");
        }
        try {
            Files.copy(in, target);
        } finally {
            in.close();
        }
        // Deletion hooks run longest path first, so children are removed before their parents.
        Path current = dir;
        for (Path part : dir.relativize(target)) {
            current = current.resolve(part);
            current.toFile().deleteOnExit();
        }
        pickerDir = dir;
        return dir;
    }
}
