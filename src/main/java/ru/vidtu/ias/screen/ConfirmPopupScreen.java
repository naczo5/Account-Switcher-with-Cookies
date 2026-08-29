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
 * Small confirmation popup with custom text and action.
 *
 * @author Articuling
 */
final class ConfirmPopupScreen extends Screen {
    /**
     * Parent screen.
     */
    private final Screen parent;

    /**
     * Confirmation prompt.
     */
    private final Component prompt;

    /**
     * Confirmation button label.
     */
    private final Component confirm;

    /**
     * Callback handler.
     */
    private final Runnable handler;

    /**
     * Confirmation prompt label.
     */
    private MultiLineLabel label;

    ConfirmPopupScreen(Screen parent, Component title, Component prompt, Component confirm, Runnable handler) {
        super(title);
        this.parent = parent;
        this.prompt = prompt;
        this.confirm = confirm;
        this.handler = handler;
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

        PopupButton confirmButton = new PopupButton(this.width / 2 - 75, this.height / 2 + 58 - 22, 74, 20,
                this.confirm, btn -> {
            this.handler.run();
        }, Supplier::get);
        confirmButton.color(0.5F, 1.0F, 0.5F, true);
        this.addRenderableWidget(confirmButton);

        this.addRenderableWidget(new PopupButton(this.width / 2 + 1, this.height / 2 + 58 - 22, 74, 20,
                CommonComponents.GUI_CANCEL, btn -> this.onClose(), Supplier::get));

        this.label = MultiLineLabel.create(this.font, this.prompt, 220);
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
        graphics.centeredText(this.font, this.title, this.width / 4, this.height / 4 - 58 / 2, 0xFF_FF_FF_FF);
        //?} else
        /*graphics.drawCenteredString(this.font, this.title, this.width / 4, this.height / 4 - 58 / 2, 0xFF_FF_FF_FF);*/
        pose.popMatrix();

        IStonecutter.renderMultilineLabelCentered(this.label, graphics, this.width / 2, (this.height - this.label.getLineCount() * 9) / 2 - 4);
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
            graphics.fill(0, 0, this.width, this.height, 0x80_00_00_00);
        } else {
            //? if >=26.1 {
            super.extractBackground(graphics, mouseX, mouseY, delta);
            //?} else
            /*super.renderBackground(graphics, mouseX, mouseY, delta);*/
        }

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        graphics.fill(centerX - 125, centerY - 58, centerX + 125, centerY + 58, 0xF8_20_20_30);
        graphics.fill(centerX - 124, centerY - 59, centerX + 124, centerY - 58, 0xF8_20_20_30);
        graphics.fill(centerX - 124, centerY + 58, centerX + 124, centerY + 59, 0xF8_20_20_30);
    }

    @Override
    public void onClose() {
        assert this.minecraft != null;
        //$set_screen 'this.minecraft' 'this.parent'
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public String toString() {
        return "ConfirmPopupScreen{}";
    }
}
