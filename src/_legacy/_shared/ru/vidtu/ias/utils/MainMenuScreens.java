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

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Set;

/**
 * Detects main-menu-like screens, including Lunar Client's custom title screen.
 *
 * @author VidTu
 */
public final class MainMenuScreens {
    /**
     * Vanilla main-menu button translation keys.
     */
    @NotNull
    private static final Set<String> MAIN_MENU_KEYS = Set.of(
            "menu.singleplayer",
            "menu.multiplayer",
            "menu.online"
    );

    /**
     * An instance of this class cannot be created.
     *
     * @throws AssertionError Always
     */
    @Contract(pure = true)
    private MainMenuScreens() {
        throw new AssertionError("No instances.");
    }

    /**
     * Returns whether the screen is a main menu (vanilla {@link TitleScreen} or a compatible replacement).
     *
     * @param screen Target screen
     * @return {@code true} if this screen should receive title-menu widgets
     */
    @Contract(pure = true)
    public static boolean isMainMenu(@NotNull Screen screen) {
        if (screen instanceof TitleScreen) return true;
        if (TitleScreen.class.isAssignableFrom(screen.getClass())) return true;
        if (screen instanceof JoinMultiplayerScreen) return false;
        if (isExcluded(screen)) return false;
        if (hasMainMenuButton(screen)) return true;
        return matchesMainMenuClassName(screen);
    }

    /**
     * Returns whether the screen should show title-menu overlay text.
     *
     * @param screen Target screen
     * @return {@code true} if title text may be drawn
     */
    @Contract(pure = true)
    public static boolean isTitleTextScreen(@NotNull Screen screen) {
        return isMainMenu(screen);
    }

    /**
     * Returns whether the account-switcher open key should be blocked on this screen.
     * Unknown screens on the main menu are allowed so Lunar Client's home UI keeps working.
     *
     * @param screen Target screen
     * @return {@code true} if the open key should be ignored
     */
    @Contract(pure = true)
    public static boolean blocksAccountSwitcherOpen(@NotNull Screen screen) {
        if (screen instanceof JoinMultiplayerScreen) return false;
        if (isMainMenu(screen)) return false;
        String full = screen.getClass().getName().toLowerCase(Locale.ROOT);
        String simple = screen.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        if (full.contains("modmenu")) return true;
        if (isExcluded(screen)) return true;
        boolean lunarLike = full.contains("moonsworth") || full.contains("lunar") || full.contains("ichor") || full.contains("genesis");
        return lunarLike && (simple.contains("mod") || simple.contains("setting") || simple.contains("config") || simple.contains("search"));
    }

    @Contract(pure = true)
    private static boolean isExcluded(@NotNull Screen screen) {
        String simple = screen.getClass().getSimpleName();
        return simple.contains("Options")
                || simple.contains("SelectWorld")
                || simple.contains("CreateWorld")
                || simple.contains("Connect")
                || simple.contains("Loading")
                || simple.contains("Progress")
                || simple.contains("Datapack")
                || simple.contains("Experiments")
                || simple.contains("Accessibility")
                || simple.contains("Language")
                || simple.contains("Credits")
                || simple.contains("Confirm")
                || simple.contains("Popup")
                || simple.contains("Account")
                || simple.contains("Config");
    }

    @Contract(pure = true)
    private static boolean hasMainMenuButton(@NotNull Screen screen) {
        for (GuiEventListener child : screen.children()) {
            if (!(child instanceof AbstractWidget widget)) continue;
            if (!(widget instanceof Button)) continue;
            Component message = widget.getMessage();
            if (message.getContents() instanceof TranslatableContents translatable) {
                if (MAIN_MENU_KEYS.contains(translatable.getKey())) return true;
            }
            String plain = message.getString().toLowerCase(Locale.ROOT);
            if (plain.contains("singleplayer") || plain.contains("multiplayer")) return true;
        }
        return false;
    }

    @Contract(pure = true)
    private static boolean matchesMainMenuClassName(@NotNull Screen screen) {
        String simple = screen.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        String full = screen.getClass().getName().toLowerCase(Locale.ROOT);
        boolean lunarLike = full.contains("moonsworth") || full.contains("lunar") || full.contains("ichor") || full.contains("genesis");
        if (!lunarLike && !simple.contains("title") && !simple.contains("mainmenu") && !simple.contains("main_menu")) {
            return false;
        }
        if (simple.contains("subtitle") || simple.contains("world")) return false;
        return simple.contains("title")
                || simple.contains("mainmenu")
                || simple.contains("main_menu")
                || simple.contains("homescreen")
                || simple.contains("home_screen")
                || simple.equals("home")
                || lunarLike && (simple.contains("menu") || simple.contains("screen") || simple.contains("gui"));
    }
}
