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

package ru.vidtu.ias.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opens the vanilla singleplayer/multiplayer screens directly, bypassing the
 * launcher's home UI (e.g. Lunar Client's signed-in account gate).
 * <p>
 * Singleplayer always works offline. Multiplayer uses the current Minecraft
 * session, so log in via IAS first for online-mode servers.
 *
 * @author VidTu
 */
public final class DirectPlay {
    /**
     * Logger for this class.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger("IAS/DirectPlay");

    /**
     * An instance of this class cannot be created.
     *
     * @throws AssertionError Always
     */
    private DirectPlay() {
        throw new AssertionError("No instances.");
    }

    /**
     * Opens the vanilla world list, bypassing the launcher menu.
     *
     * @param minecraft Minecraft instance
     * @param parent    Parent screen for the back button
     * @return {@code true} if the screen was opened
     */
    public static boolean openSingleplayer(Minecraft minecraft, Screen parent) {
        if (minecraft.player != null || minecraft.level != null) return false;
        try {
            SelectWorldScreen target = new SelectWorldScreen(parent);
            //$ set_screen minecraft target
            minecraft.gui.setScreen(target);
            return true;
        } catch (Throwable t) {
            LOGGER.error("IAS: Unable to open singleplayer screen.", t);
            return false;
        }
    }

    /**
     * Opens the vanilla server list, bypassing the launcher menu.
     *
     * @param minecraft Minecraft instance
     * @param parent    Parent screen for the back button
     * @return {@code true} if the screen was opened
     */
    public static boolean openMultiplayer(Minecraft minecraft, Screen parent) {
        if (minecraft.player != null || minecraft.level != null) return false;
        try {
            JoinMultiplayerScreen target = new JoinMultiplayerScreen(parent);
            //$ set_screen minecraft target
            minecraft.gui.setScreen(target);
            return true;
        } catch (Throwable t) {
            LOGGER.error("IAS: Unable to open multiplayer screen.", t);
            return false;
        }
    }
}
