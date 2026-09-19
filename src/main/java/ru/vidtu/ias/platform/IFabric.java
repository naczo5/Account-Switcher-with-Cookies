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
//? if >=1.21.10 {
import net.minecraft.resources.Identifier;
//?}
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
import ru.vidtu.ias.config.IASStorage;
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
     * Keybind category for the account switcher, so it gets its own line in the controls screen.
     */
    //? if >=1.21.10 {
    public static final KeyMapping.Category IAS_CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("ias", "general"));
    //?}

    /**
     * Keybind that opens the account switcher from menus (works on Lunar Client).
     */
    //? if >=1.21.10 {
    public static final KeyMapping OPEN_ACCOUNT_SWITCHER = new KeyMapping(
            "key.ias.open",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            IAS_CATEGORY
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
     * Whether the open keybind was added to the options array manually.
     * (Only needed when Fabric's key-mapping API is missing.)
     */
    private static boolean keybindEnsured;

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
            if (!keybindRegistered) {
                ensureKeybindInOptions(client);
            }
            boolean pressed = false;
            while (OPEN_ACCOUNT_SWITCHER.consumeClick()) {
                pressed = true;
            }
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
     * Adds the open keybind to the controls screen when Fabric's key-mapping API is missing,
     * so it can still be changed or disabled there. Vanilla only lists the mappings
     * from the options array, which the Fabric API normally appends to via a mixin.
     */
    private static void ensureKeybindInOptions(net.minecraft.client.Minecraft client) {
        if (keybindEnsured) return;
        try {
            Object options = client.options;
            if (options == null) return;
            java.lang.reflect.Field arrayField = null;
            for (java.lang.reflect.Field field : options.getClass().getFields()) {
                if (field.getType() == KeyMapping[].class) {
                    arrayField = field;
                    break;
                }
            }
            if (arrayField == null) return;
            arrayField.setAccessible(true);
            KeyMapping[] mappings = (KeyMapping[]) arrayField.get(options);
            if (mappings == null) return;
            for (KeyMapping mapping : mappings) {
                if (mapping == OPEN_ACCOUNT_SWITCHER) {
                    keybindEnsured = true;
                    return;
                }
            }
            KeyMapping[] resized = java.util.Arrays.copyOf(mappings, mappings.length + 1);
            resized[mappings.length] = OPEN_ACCOUNT_SWITCHER;
            arrayField.set(options, resized);
            applySavedBinding(client);
            keybindEnsured = true;
            LOGGER.debug("IAS: Added open keybind to the controls screen without Fabric's key-mapping API.");
        } catch (Throwable t) {
            LOGGER.debug("IAS: Unable to add open keybind to the controls screen.", t);
        }
    }

    /**
     * Applies the saved binding from {@code options.txt}, since vanilla loads bindings
     * before this mod appends its keybind to the options array.
     */
    private static void applySavedBinding(net.minecraft.client.Minecraft client) {
        try {
            java.io.File file = new java.io.File(client.gameDirectory, "options.txt");
            if (!file.isFile()) return;
            String prefix = "key_" + OPEN_ACCOUNT_SWITCHER.getName() + ":";
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                    new java.io.FileInputStream(file), java.nio.charset.StandardCharsets.UTF_8));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith(prefix)) continue;
                    String saved = line.substring(prefix.length());
                    if (!OPEN_ACCOUNT_SWITCHER.saveString().equals(saved)) {
                        OPEN_ACCOUNT_SWITCHER.setKey(InputConstants.getKey(saved));
                    }
                    break;
                }
            } finally {
                reader.close();
            }
        } catch (Throwable t) {
            LOGGER.debug("IAS: Unable to apply saved open keybind.", t);
        }
    }

    /**
     * Shows a one-time hint on the main menu (especially useful on Lunar Client).
     */
    private static void maybeShowAccessHint(net.minecraft.client.Minecraft client) {
        if (IASStorage.accessHintShown || client.player != null || client.level != null) return;
        IAS.accessHintShownStorage();
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
