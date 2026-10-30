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

//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else
/*import net.minecraft.client.gui.GuiGraphics;*/
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * In-game file browser used when AWT/Swing file dialogs are unavailable (for example,
 * when the game runs with {@code java.awt.headless=true}).
 *
 * @author VidTu
 */
final class FileBrowserPopupScreen extends Screen {
    private static final int PANEL_HALF_W = 150;
    private static final int PANEL_HALF_H = 110;
    private static final int LIST_TOP = -70;
    private static final int LIST_BOTTOM = 56;
    private static final int LIST_X = -140;
    private static final int LIST_RIGHT = 140;
    private static final int ROW_HEIGHT = 12;
    private static final int VISIBLE_ROWS = (LIST_BOTTOM - LIST_TOP) / ROW_HEIGHT;

    private final Screen parent;
    private final Consumer<List<String>> handler;
    private final String[] filters;
    private final Set<Path> selected = new LinkedHashSet<>();
    private final List<Path> entries = new ArrayList<>();
    private Path directory;
    private int scroll;
    private PopupButton upButton;
    private PopupButton selectButton;

    FileBrowserPopupScreen(Screen parent, Component title, String startPath, String[] filters,
                           Consumer<List<String>> handler) {
        super(title);
        this.parent = parent;
        this.filters = filters;
        this.handler = handler;
        this.directory = initialDirectory(startPath);
        this.refreshEntries();
    }

    @Override
    protected void init() {
        assert this.minecraft != null;
        if (this.parent != null) {
            //? if >=1.21.11 {
            this.parent.init(this.width, this.height);
            //?} else
            /*this.parent.init(this.minecraft, this.width, this.height);*/
        }

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.upButton = new PopupButton(centerX - 145, centerY + 72, 60, 20,
                Component.translatable("ias.fileBrowser.up"), btn -> this.goUp(), Supplier::get);
        this.upButton.setTooltip(Tooltip.create(Component.translatable("ias.fileBrowser.up.tip")));
        this.addRenderableWidget(this.upButton);

        this.selectButton = new PopupButton(centerX - 75, centerY + 72, 100, 20,
                Component.translatable("ias.fileBrowser.select"), btn -> this.selectAndClose(), Supplier::get);
        this.selectButton.color(0.5F, 1.0F, 0.5F, true);
        this.addRenderableWidget(this.selectButton);

        PopupButton cancelButton = new PopupButton(centerX + 35, centerY + 72, 100, 20,
                Component.translatable("ias.fileBrowser.cancel"), btn -> this.onClose(), Supplier::get);
        this.addRenderableWidget(cancelButton);

        this.updateButtons();
    }

    private Path initialDirectory(String startPath) {
        Path home = Path.of(System.getProperty("user.home", ".")).toAbsolutePath().normalize();
        if (startPath == null || startPath.isBlank()) {
            return home;
        }
        String normalized = startPath.strip();
        if (normalized.length() >= 2 && normalized.charAt(0) == '"'
                && normalized.charAt(normalized.length() - 1) == '"') {
            normalized = normalized.substring(1, normalized.length() - 1).strip();
        }
        try {
            Path path = Path.of(normalized).toAbsolutePath().normalize();
            if (Files.isDirectory(path)) {
                return path;
            }
            if (Files.isRegularFile(path)) {
                this.selected.add(path);
                Path parent = path.getParent();
                return parent != null ? parent : home;
            }
            Path parent = path.getParent();
            if (parent != null && Files.isDirectory(parent)) {
                return parent;
            }
        } catch (Throwable ignored) {
        }
        return home;
    }

    private void refreshEntries() {
        this.entries.clear();
        if (this.directory == null) {
            for (File root : File.listRoots()) {
                this.entries.add(root.toPath());
            }
            this.scroll = 0;
            this.updateButtons();
            return;
        }

        List<Path> directories = new ArrayList<>();
        List<Path> files = new ArrayList<>();
        try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(this.directory)) {
            for (Path path : stream) {
                if (Files.isDirectory(path)) {
                    directories.add(path);
                } else if (this.isAllowed(path)) {
                    files.add(path);
                }
            }
        } catch (Throwable ignored) {
        }

        Comparator<Path> byName = Comparator.comparing(path -> path.getFileName() != null
                ? path.getFileName().toString().toLowerCase(Locale.ROOT)
                : path.toString().toLowerCase(Locale.ROOT));
        directories.sort(byName);
        files.sort(byName);
        this.entries.addAll(directories);
        this.entries.addAll(files);
        this.scroll = Math.max(0, Math.min(this.scroll, Math.max(0, this.entries.size() - VISIBLE_ROWS)));
        this.updateButtons();
    }

    private boolean isAllowed(Path path) {
        if (this.filters == null || this.filters.length == 0) {
            return true;
        }
        Path namePath = path.getFileName();
        if (namePath == null) {
            return false;
        }
        String name = namePath.toString().toLowerCase(Locale.ROOT);
        for (String filter : this.filters) {
            String normalized = filter.toLowerCase(Locale.ROOT);
            if (normalized.startsWith("*.") && name.endsWith(normalized.substring(1))) {
                return true;
            }
        }
        return false;
    }

    private void goUp() {
        if (this.directory == null) {
            return;
        }
        Path parent = this.directory.getParent();
        this.directory = parent;
        this.selected.clear();
        this.scroll = 0;
        this.refreshEntries();
    }

    private void enter(Path path) {
        if (path == null || !Files.isDirectory(path)) {
            return;
        }
        this.directory = path;
        this.selected.clear();
        this.scroll = 0;
        this.refreshEntries();
    }

    private void updateButtons() {
        if (this.upButton != null) {
            this.upButton.active = this.directory != null;
        }
        if (this.selectButton != null) {
            this.selectButton.active = !this.selected.isEmpty();
        }
    }

    private void selectAndClose() {
        if (this.selected.isEmpty()) {
            return;
        }
        List<String> paths = new ArrayList<>(this.selected.size());
        for (Path path : this.selected) {
            paths.add(path.toAbsolutePath().toString());
        }
        this.handler.accept(paths);
        this.onClose();
    }

    private boolean inList(double mouseX, double mouseY) {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        return mouseX >= centerX + LIST_X && mouseX <= centerX + LIST_RIGHT
                && mouseY >= centerY + LIST_TOP && mouseY < centerY + LIST_BOTTOM;
    }

    private int rowAt(double mouseY) {
        int centerY = this.height / 2;
        return (int) ((mouseY - (centerY + LIST_TOP)) / ROW_HEIGHT) + this.scroll;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (!AccountList.isPrimaryButton(event) || !this.inList(event.x(), event.y())) {
            return false;
        }
        int row = this.rowAt(event.y());
        if (row < 0 || row >= this.entries.size()) {
            return true;
        }
        Path path = this.entries.get(row);
        if (Files.isDirectory(path)) {
            this.enter(path);
            return true;
        }
        if (doubleClick) {
            this.selected.clear();
            this.selected.add(path);
            this.selectAndClose();
            return true;
        }
        if (!this.selected.remove(path)) {
            this.selected.add(path);
        }
        this.updateButtons();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!this.inList(mouseX, mouseY) || this.entries.size() <= VISIBLE_ROWS) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        int max = Math.max(0, this.entries.size() - VISIBLE_ROWS);
        this.scroll = Math.max(0, Math.min(max, this.scroll - (scrollY > 0 ? 1 : -1)));
        return true;
    }

    @Override
    //? if >=26.1 {
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
    //?} else
    /*public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {*/
        assert this.minecraft != null;
        Matrix3x2fStack pose = graphics.pose();

        //? if >=26.1 {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        //?} else
        /*super.render(graphics, mouseX, mouseY, delta);*/

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int listTop = centerY + LIST_TOP;
        int listBottom = centerY + LIST_BOTTOM;
        int listLeft = centerX + LIST_X;
        int listRight = centerX + LIST_RIGHT;

        pose.pushMatrix();
        pose.scale(1.4F, 1.4F);
        //? if >=26.1 {
        graphics.centeredText(this.font, this.title, (int) (centerX / 1.4F), (int) ((centerY - 93) / 1.4F), 0xFF_FF_FF_FF);
        //?} else
        /*graphics.drawCenteredString(this.font, this.title, (int) (centerX / 1.4F), (int) ((centerY - 93) / 1.4F), 0xFF_FF_FF_FF);*/
        pose.popMatrix();

        String path = this.directory != null ? this.directory.toString() : Component.translatable("ias.fileBrowser.roots").getString();
        path = this.font.plainSubstrByWidth(path, PANEL_HALF_W * 2 - 20);
        //? if >=26.1 {
        graphics.centeredText(this.font, Component.literal(path), centerX, centerY - 78, 0xFF_AA_AA_AA);
        //?} else
        /*graphics.drawCenteredString(this.font, path, centerX, centerY - 78, 0xFF_AA_AA_AA);*/

        graphics.fill(listLeft - 2, listTop - 2, listRight + 2, listBottom + 2, 0x80_00_00_00);
        if (this.entries.isEmpty()) {
            //? if >=26.1 {
            graphics.centeredText(this.font, Component.translatable("ias.fileBrowser.empty"), centerX, listTop + 24, 0xFF_AA_AA_AA);
            //?} else
            /*graphics.drawCenteredString(this.font, Component.translatable("ias.fileBrowser.empty"), centerX, listTop + 24, 0xFF_AA_AA_AA);*/
        } else {
            for (int i = this.scroll; i < this.entries.size() && i < this.scroll + VISIBLE_ROWS; i++) {
                int row = i - this.scroll;
                int y = listTop + row * ROW_HEIGHT;
                Path entry = this.entries.get(i);
                boolean directory = Files.isDirectory(entry);
                boolean isSelected = this.selected.contains(entry);
                if (isSelected) {
                    graphics.fill(listLeft, y - 1, listRight, y + ROW_HEIGHT - 1, 0x40_00_FF_00);
                }
                String name = entry.getFileName() != null ? entry.getFileName().toString() : entry.toString();
                if (directory) {
                    name += "/";
                }
                name = this.font.plainSubstrByWidth(name, PANEL_HALF_W * 2 - 28);
                int color = directory ? 0xFF_FF_AA_00 : 0xFF_FF_FF_FF;
                //? if >=26.1 {
                graphics.text(this.font, name, listLeft, y, color);
                //?} else
                /*graphics.drawString(this.font, name, listLeft, y, color);*/
            }
        }

        if (this.entries.size() > VISIBLE_ROWS) {
            int trackX = centerX + LIST_RIGHT + 6;
            int trackHeight = listBottom - listTop;
            graphics.fill(trackX, listTop, trackX + 2, listBottom, 0x80_FF_FF_FF);
            int thumbHeight = Math.max(10, trackHeight * VISIBLE_ROWS / this.entries.size());
            int maxScroll = this.entries.size() - VISIBLE_ROWS;
            int thumbY = listTop + (trackHeight - thumbHeight) * this.scroll / Math.max(1, maxScroll);
            graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, 0xFF_FF_FF_FF);
        }
    }

    @Override
    //? if >=26.1 {
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
    //?} else
    /*public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {*/
        assert this.minecraft != null;
        if (this.parent != null) {
            //? if >=26.1 {
            this.parent.extractRenderStateWithTooltipAndSubtitles(graphics, 0, 0, delta);
            //?} elif >=1.21.10 {
            /*this.parent.renderWithTooltipAndSubtitles(graphics, 0, 0, delta);
            *///?} else
            /*this.parent.renderWithTooltip(graphics, 0, 0, delta);*/
            graphics.nextStratum();
            graphics.fill(0, 0, this.width, this.height, 0x80_00_00_00);
        } else {
            //? if >=26.1 {
            super.extractBackground(graphics, mouseX, mouseY, delta);
            //?} else
            /*super.renderBackground(graphics, mouseX, mouseY, delta);*/
        }

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        graphics.fill(centerX - PANEL_HALF_W, centerY - PANEL_HALF_H, centerX + PANEL_HALF_W, centerY + PANEL_HALF_H, 0xF8_20_20_30);
        graphics.fill(centerX - PANEL_HALF_W + 1, centerY - PANEL_HALF_H - 1, centerX + PANEL_HALF_W - 1, centerY - PANEL_HALF_H, 0xF8_20_20_30);
        graphics.fill(centerX - PANEL_HALF_W + 1, centerY + PANEL_HALF_H, centerX + PANEL_HALF_W - 1, centerY + PANEL_HALF_H + 1, 0xF8_20_20_30);
    }

    @Override
    public void onClose() {
        assert this.minecraft != null;
        //$set_screen 'this.minecraft' 'this.parent'
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public String toString() {
        return "FileBrowserPopupScreen{" + this.directory + "}";
    }
}
