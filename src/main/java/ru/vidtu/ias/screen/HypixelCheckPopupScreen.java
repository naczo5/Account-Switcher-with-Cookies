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
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;
import ru.vidtu.ias.platform.IStonecutter;

import java.util.function.Supplier;

/**
 * Progress popup for Hypixel ban checks.
 */
final class HypixelCheckPopupScreen extends Screen implements AccountList.HypixelCheckProgress {
    private static final int PANEL_HALF_WIDTH = 125;
    private static final int PANEL_HALF_HEIGHT = 60;

    private final Screen parent;
    private final AccountList list;
    private MultiLineLabel statusLabel;
    private PopupButton dismissButton;
    private int completed;
    private int total;
    private boolean finished;
    private boolean started;

    HypixelCheckPopupScreen(Screen parent, AccountList list) {
        super(Component.translatable("ias.accounts.checkHypixel"));
        this.parent = parent;
        this.list = list;
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

        if (this.statusLabel == null) {
            this.statusLabel = MultiLineLabel.create(this.font, Component.translatable("ias.hypixel.progress.preparing"), 220);
        }

        this.dismissButton = new PopupButton(this.width / 2 - 37, this.height / 2 + 44, 74, 20,
                this.dismissLabel(), btn -> this.onClose(), Supplier::get);
        this.clearWidgets();
        this.addRenderableWidget(this.dismissButton);

        if (!this.started) {
            this.started = true;
            this.list.checkAllHypixelBans(this);
        }
    }

    private Component dismissLabel() {
        return this.finished
                ? Component.translatable("ias.hypixel.progress.close")
                : CommonComponents.GUI_CANCEL;
    }

    private void updateDismissButton() {
        if (this.dismissButton != null) {
            this.dismissButton.setMessage(this.dismissLabel());
        }
    }

    @Override
    public void onHypixelProgress(int completed, int total, String accountName, Component stage) {
        this.completed = completed;
        this.total = total;
        this.statusLabel = MultiLineLabel.create(this.font,
                Component.translatable("ias.hypixel.progress.account", completed + 1, total, accountName)
                        .append("\n")
                        .append(stage), 220);
    }

    @Override
    public void onHypixelComplete() {
        this.finished = true;
        this.completed = this.total;
        this.statusLabel = MultiLineLabel.create(this.font, Component.translatable("ias.hypixel.progress.done"), 220);
        this.updateDismissButton();
        this.list.update(this.list.screen().search().getValue());
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

        pose.pushMatrix();
        pose.scale(2.0F, 2.0F);
        //? if >=26.1 {
        graphics.centeredText(this.font, this.title, this.width / 4, this.height / 4 - 56 / 2, 0xFF_FF_FF_FF);
        //?} else
        /*graphics.drawCenteredString(this.font, this.title, this.width / 4, this.height / 4 - 56 / 2, 0xFF_FF_FF_FF);*/
        pose.popMatrix();

        IStonecutter.renderMultilineLabelCentered(this.statusLabel, graphics, this.width / 2, this.height / 2 - 18);

        int barX = this.width / 2 - 100;
        int barY = this.height / 2 + 18;
        int barW = 200;
        int barH = 8;
        graphics.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, 0xFF_40_40_40);
        graphics.fill(barX, barY, barX + barW, barY + barH, 0xFF_10_10_10);
        if (this.total > 0) {
            int fillCompleted = this.finished ? this.completed : Math.min(this.total, this.completed + 1);
            int fillW = Math.max(1, barW * fillCompleted / this.total);
            graphics.fill(barX, barY, barX + fillW, barY + barH, this.finished ? 0xFF_55_FF_55 : 0xFF_55_AA_FF);
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
            //?} elif >= 1.21.10 {
            /*this.parent.renderWithTooltipAndSubtitles(graphics, 0, 0, delta);
            *///?} else
            /*this.parent.renderWithTooltip(graphics, 0, 0, delta);*/
            graphics.nextStratum();
            int[] bounds = this.parent instanceof AccountScreen accountScreen
                    ? accountScreen.listOverlayBounds()
                    : new int[]{0, 0, this.width, this.height};
            graphics.fill(bounds[0], bounds[1], bounds[0] + bounds[2], bounds[1] + bounds[3], 0x80_00_00_00);
        } else {
            //? if >=26.1 {
            super.extractBackground(graphics, mouseX, mouseY, delta);
            //?} else
            /*super.renderBackground(graphics, mouseX, mouseY, delta);*/
        }

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        graphics.fill(centerX - PANEL_HALF_WIDTH, centerY - PANEL_HALF_HEIGHT, centerX + PANEL_HALF_WIDTH, centerY + PANEL_HALF_HEIGHT, 0xF8_20_20_30);
        graphics.fill(centerX - PANEL_HALF_WIDTH + 1, centerY - PANEL_HALF_HEIGHT - 1, centerX + PANEL_HALF_WIDTH - 1, centerY - PANEL_HALF_HEIGHT, 0xF8_20_20_30);
        graphics.fill(centerX - PANEL_HALF_WIDTH + 1, centerY + PANEL_HALF_HEIGHT, centerX + PANEL_HALF_WIDTH - 1, centerY + PANEL_HALF_HEIGHT + 1, 0xF8_20_20_30);
    }

    @Override
    public void onClose() {
        assert this.minecraft != null;
        if (!this.finished) {
            this.list.cancelHypixelCheck();
        }
        //$set_screen 'this.minecraft' 'this.parent'
        this.minecraft.gui.setScreen(this.parent);
    }
}
