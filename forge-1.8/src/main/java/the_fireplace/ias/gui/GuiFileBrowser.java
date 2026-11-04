package the_fireplace.ias.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Mouse;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * In-game file browser for 1.8.9 when AWT dialogs are unavailable (headless clients).
 */
public class GuiFileBrowser extends GuiScreen {
    /**
     * Receives the chosen absolute paths after this screen has returned to its parent.
     */
    public interface Callback {
        void onChosen(List<String> paths);
    }

    private static final int ROW_HEIGHT = 12;
    private static final int VISIBLE_ROWS = 10;

    private final GuiScreen parent;
    private final String extension;
    private final boolean multi;
    private final Callback callback;
    private final List<File> entries = new ArrayList<File>();
    private final Set<File> selected = new LinkedHashSet<File>();
    private File directory;
    private int scroll;

    public GuiFileBrowser(GuiScreen parent, String startPath, String extension, boolean multi, Callback callback) {
        this.parent = parent;
        this.extension = extension;
        this.multi = multi;
        this.callback = callback;
        this.directory = initialDirectory(startPath);
        this.refreshEntries();
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        int centerX = this.width / 2;
        int y = this.height / 2 + 72;
        this.buttonList.add(new GuiButton(1, centerX - 145, y, 60, 20, label("ias.fileBrowser.up", "Up")));
        this.buttonList.add(new GuiButton(2, centerX - 75, y, 100, 20, label("ias.fileBrowser.select", "Select")));
        this.buttonList.add(new GuiButton(3, centerX + 35, y, 100, 20, label("ias.fileBrowser.cancel", "Cancel")));
        this.updateButtons();
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (!button.enabled) {
            return;
        }
        if (button.id == 1) {
            this.goUp();
        } else if (button.id == 2) {
            this.selectAndClose();
        } else if (button.id == 3) {
            this.mc.displayGuiScreen(this.parent);
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        int mouseX = Mouse.getEventX() * this.width / Math.max(1, this.mc.displayWidth);
        int mouseY = this.height - Mouse.getEventY() * this.height / Math.max(1, this.mc.displayHeight) - 1;
        if (!this.inList(mouseX, mouseY)) {
            return;
        }
        int maxScroll = Math.max(0, this.entries.size() - VISIBLE_ROWS);
        this.scroll = Math.max(0, Math.min(maxScroll, this.scroll - (wheel > 0 ? 1 : -1)));
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton != 0 || !this.inList(mouseX, mouseY)) {
            return;
        }
        int row = this.rowAt(mouseY);
        if (row < 0 || row >= this.entries.size()) {
            return;
        }
        File file = this.entries.get(row);
        if (file.isDirectory()) {
            this.directory = file;
            this.selected.clear();
            this.scroll = 0;
            this.refreshEntries();
            return;
        }
        if (!this.multi) {
            this.selected.clear();
            this.selected.add(file);
        } else if (!this.selected.remove(file)) {
            this.selected.add(file);
        }
        this.updateButtons();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        String title = this.directory == null ? label("ias.fileBrowser.roots", "Drives") : this.directory.getAbsolutePath();
        this.drawCenteredString(this.fontRendererObj, this.fontRendererObj.trimStringToWidth(title, 260), this.width / 2, this.height / 2 - 88, 0xFFFFFF);
        int top = this.listTop();
        int left = this.width / 2 - 140;
        if (this.entries.isEmpty()) {
            this.drawCenteredString(this.fontRendererObj, label("ias.fileBrowser.empty", "No matching files in this folder."), this.width / 2, top + 8, 0xAAAAAA);
        }
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int index = this.scroll + i;
            if (index >= this.entries.size()) {
                break;
            }
            File file = this.entries.get(index);
            int y = top + i * ROW_HEIGHT;
            boolean hover = mouseX >= left && mouseX <= left + 280 && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (this.selected.contains(file) || hover) {
                drawRect(left, y, left + 280, y + ROW_HEIGHT, this.selected.contains(file) ? 0x80305080 : 0x40FFFFFF);
            }
            String name = file.getName();
            if (name == null || name.isEmpty()) {
                name = file.getPath();
            }
            if (file.isDirectory()) {
                name = name + "/";
            }
            this.drawString(this.fontRendererObj, this.fontRendererObj.trimStringToWidth(name, 270), left + 4, y + 2, file.isDirectory() ? 0x55FFFF : 0xFFFFFF);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void selectAndClose() {
        List<String> paths = new ArrayList<String>();
        for (File file : this.selected) {
            if (file != null && file.isFile()) {
                paths.add(file.getAbsolutePath());
            }
        }
        this.mc.displayGuiScreen(this.parent);
        if (!paths.isEmpty() && this.callback != null) {
            this.callback.onChosen(paths);
        }
    }

    private void goUp() {
        if (this.directory == null) {
            return;
        }
        this.directory = this.directory.getParentFile();
        this.selected.clear();
        this.scroll = 0;
        this.refreshEntries();
    }

    private void refreshEntries() {
        this.entries.clear();
        if (this.directory == null) {
            File[] roots = File.listRoots();
            if (roots != null) {
                Collections.addAll(this.entries, roots);
            }
        } else {
            File[] children = this.directory.listFiles();
            List<File> directories = new ArrayList<File>();
            List<File> files = new ArrayList<File>();
            if (children != null) {
                for (File child : children) {
                    if (child == null) {
                        continue;
                    }
                    if (child.isDirectory()) {
                        directories.add(child);
                    } else if (this.isAllowed(child)) {
                        files.add(child);
                    }
                }
            }
            Comparator<File> byName = new Comparator<File>() {
                @Override
                public int compare(File a, File b) {
                    return name(a).compareTo(name(b));
                }
            };
            Collections.sort(directories, byName);
            Collections.sort(files, byName);
            this.entries.addAll(directories);
            this.entries.addAll(files);
        }
        int maxScroll = Math.max(0, this.entries.size() - VISIBLE_ROWS);
        this.scroll = Math.max(0, Math.min(this.scroll, maxScroll));
        this.updateButtons();
    }

    private boolean isAllowed(File file) {
        if (this.extension == null || this.extension.isEmpty()) {
            return true;
        }
        String name = file.getName();
        return name != null && name.toLowerCase(Locale.ROOT).endsWith("." + this.extension.toLowerCase(Locale.ROOT));
    }

    private void updateButtons() {
        for (int i = 0; i < this.buttonList.size(); i++) {
            GuiButton button = (GuiButton) this.buttonList.get(i);
            if (button.id == 1) {
                button.enabled = this.directory != null;
            } else if (button.id == 2) {
                button.enabled = !this.selected.isEmpty();
            }
        }
    }

    private int listTop() {
        return this.height / 2 - 70;
    }

    private boolean inList(int mouseX, int mouseY) {
        int left = this.width / 2 - 140;
        int top = this.listTop();
        return mouseX >= left && mouseX <= left + 280 && mouseY >= top && mouseY < top + VISIBLE_ROWS * ROW_HEIGHT;
    }

    private int rowAt(int mouseY) {
        return (mouseY - this.listTop()) / ROW_HEIGHT + this.scroll;
    }

    private static File initialDirectory(String startPath) {
        File home = new File(System.getProperty("user.home", "."));
        if (startPath == null || startPath.trim().isEmpty()) {
            return home.isDirectory() ? home : null;
        }
        String first = startPath.split("[;\n]")[0].trim();
        if (first.length() >= 2 && first.charAt(0) == '"' && first.charAt(first.length() - 1) == '"') {
            first = first.substring(1, first.length() - 1).trim();
        }
        File file = new File(first);
        if (file.isDirectory()) {
            return file;
        }
        File parent = file.getParentFile();
        if (parent != null && parent.isDirectory()) {
            return parent;
        }
        return home.isDirectory() ? home : null;
    }

    private static String name(File file) {
        String value = file.getName();
        if (value == null || value.isEmpty()) {
            value = file.getPath();
        }
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static String label(String key, String fallback) {
        String value = I18n.format(key);
        return key.equals(value) ? fallback : value;
    }
}
