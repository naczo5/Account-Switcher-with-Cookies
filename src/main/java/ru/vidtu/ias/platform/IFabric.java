/*
 * In-Game Account Switcher is a third-party mod for Minecraft Java Edition that
 * allows you to change your logged in account in-game, without restarting it.
 *
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

//? if fabric {
package ru.vidtu.ias.platform;

import com.google.errorprone.annotations.DoNotCall;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NullMarked;
import org.lwjgl.glfw.GLFW;
import ru.vidtu.ias.IAS;
import ru.vidtu.ias.IASMinecraft;
import ru.vidtu.ias.utils.MainMenuScreens;

/**
 * Main IAS class for Fabric.
 *
 * @author VidTu
 * @apiNote Internal use only
 * @see IAS
 */
@ApiStatus.Internal
@NullMarked
public final class IFabric implements ClientModInitializer {
    /**
     * Logger for this class.
     */
    private static final Logger LOGGER = LogManager.getLogger("IAS/IFabric");

    /**
     * Keybind that opens the account switcher from menus (works on Lunar Client).
     */
    //? if >=1.21.10 {
    private static final KeyMapping OPEN_ACCOUNT_SWITCHER = new KeyMapping(
            "key.ias.open",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            KeyMapping.Category.MISC
    );
    //?} else {
    /*private static final KeyMapping OPEN_ACCOUNT_SWITCHER = new KeyMapping(
            "key.ias.open",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            "key.categories.ias"
    );*/
    //?}

    /**
     * Whether the open keybind was registered through Fabric's key-mapping API.
     */
    private static boolean keybindRegistered;

    /**
     * Previous O-key state for the Lunar Client GLFW fallback.
     */
    private static boolean oWasDown;

    /**
     * Whether the Lunar access hint toast was shown this session.
     */
    private static boolean lunarHintShown;

    /**
     * Creates a new mod.
     *
     * @apiNote Do not call, called by Fabric
     */
    @Contract(pure = true)
    public IFabric() {
        // Empty.
    }

    /**
     * Initializes the client.
     *
     * @apiNote Do not call, called by Fabric
     */
    @DoNotCall("Called by Fabric")
    @Override
    public void onInitializeClient() {
        // Log.
        long start = System.nanoTime();
        LOGGER.info("IAS: Loading... (platform: fabric)");

        // Init the mod.
        IASMinecraft.init();

        // Register the shutdown handler.
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> IAS.close());

        // Open account switcher from menus (Lunar Client and other custom title screens).
        registerOpenKeybind();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean pressed = false;
            if (keybindRegistered) {
                while (OPEN_ACCOUNT_SWITCHER.consumeClick()) {
                    pressed = true;
                }
            }
            //? if >=1.21.10 {
            long window = client.getWindow().handle();
            //?} else {
            /*long window = client.getWindow().getWindow();*/
            //?}
            boolean oDown = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_O) == GLFW.GLFW_PRESS;
            if (!pressed && oDown && !oWasDown) {
                pressed = true;
            }
            oWasDown = oDown;
            if (pressed) {
                IASMinecraft.tryOpenAccountSwitcher(client);
            }
            maybeShowAccessHint(client);
        });

        // Register screen handlers.
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            //? if >=26.1 {
            IASMinecraft.onInit(client, screen, Screens.getWidgets(screen)::add);
            //?} else
            /*IASMinecraft.onInit(client, screen, Screens.getButtons(screen)::add);*/
            if (MainMenuScreens.isMainMenu(screen) || screen instanceof JoinMultiplayerScreen) {
                Font font = client.font;
                //? if >=26.1 {
                ScreenEvents.afterExtract(screen).register((scr, graphics, mouseX, mouseY, delta) -> IASMinecraft.onDraw(scr, font, graphics));
                //?} else
                /*ScreenEvents.afterRender(screen).register((scr, graphics, mouseX, mouseY, delta) -> IASMinecraft.onDraw(scr, font, graphics));*/
            }
        });

        // Done.
        LOGGER.info("IAS: Loaded. ({} ms)", (System.nanoTime() - start) / 1_000_000L);
    }

    /**
     * Registers the open keybind when Fabric's key-mapping API is available (optional on Lunar Client).
     */
    private static void registerOpenKeybind() {
        String[][] helpers = {
                {"net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper", "registerKeyBinding"},
                {"net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper", "registerKeyMapping"},
        };
        for (String[] helper : helpers) {
            try {
                Class<?> clazz = Class.forName(helper[0]);
                clazz.getMethod(helper[1], KeyMapping.class).invoke(null, OPEN_ACCOUNT_SWITCHER);
                keybindRegistered = true;
                LOGGER.debug("IAS: Registered open keybind via {}.", helper[0]);
                return;
            } catch (Throwable ignored) {
                // Try the next helper.
            }
        }
        LOGGER.info("IAS: Fabric key-mapping API not present; press O to open the account switcher.");
    }

    /**
     * Shows a one-time hint on the main menu (especially useful on Lunar Client).
     */
    private static void maybeShowAccessHint(net.minecraft.client.Minecraft client) {
        if (lunarHintShown || client.player != null || client.level != null) return;
        lunarHintShown = true;
        //? if >=26.2 {
        var manager = client.gui.toastManager();
        manager.addToast(new SystemToast(
                SystemToast.SystemToastId.NARRATOR_TOGGLE,
                Component.literal("In-Game Account Switcher"),
                Component.translatable("ias.lunar.hint")
        ));
        //?} else {
        /*var manager = client.getToastManager();
        manager.addToast(SystemToast.multiline(client, SystemToast.SystemToastId.NARRATOR_TOGGLE,
                Component.literal("In-Game Account Switcher"),
                Component.translatable("ias.lunar.hint")));*/
        //?}
    }

    @Contract(pure = true)
    @Override
    public String toString() {
        return "IAS/IFabric{}";
    }
}
//?}
