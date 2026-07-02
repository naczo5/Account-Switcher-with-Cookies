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

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.network.chat.Component;

/**
 * Factory for styled multi-line boxes used in popup screens.
 *
 * @author VidTu
 */
final class PopupMultiLineBox {
    /**
     * An instance of this class cannot be created.
     */
    private PopupMultiLineBox() {
        throw new AssertionError("No instances.");
    }

    /**
     * Creates a new multi-line box.
     *
     * @param font    Font renderer
     * @param x       Box X
     * @param y       Box Y
     * @param width   Box width
     * @param height  Box height
     * @param inherit Previous box, if any
     * @param title   Box title
     * @param hint    Placeholder hint
     * @return Configured multi-line input
     */
    static MultiLineEditBox create(Font font, int x, int y, int width, int height, MultiLineEditBox inherit, Component title, Component hint) {
        //? if >=1.21.10 {
        MultiLineEditBox box = MultiLineEditBox.builder()
                .setX(x)
                .setY(y)
                .setPlaceholder(hint)
                .setTextColor(0xFF_FF_FF_FF)
                .setTextShadow(true)
                .setCursorColor(0xFF_FF_FF_FF)
                .setShowBackground(true)
                .setShowDecorations(false)
                .build(font, width, height, title);
        if (inherit != null) {
            box.setValue(inherit.getValue());
        }
        return box;
        //?} else
        /*MultiLineEditBox box = new MultiLineEditBox(font, x, y, width, height, hint, title);
        if (inherit != null) {
            box.setValue(inherit.getValue());
        }
        return box;*/
    }
}
