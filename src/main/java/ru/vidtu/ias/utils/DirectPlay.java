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
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.vidtu.ias.screen.DirectPlayScreen;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Opens the vanilla singleplayer list and the IAS-owned direct-connect screen,
 * bypassing the launcher's home UI (e.g. Lunar Client's signed-in account gate).
 * <p>
 * Singleplayer always works offline. Multiplayer uses an IAS-owned screen
 * (server IP field), deliberately avoiding the vanilla multiplayer screens:
 * on gated setups their buttons silently do nothing. Only the actual connect
 * reuses vanilla. The last used address is read from and written back to the
 * vanilla options, just like the vanilla dialog does.
 * <p>
 * Online-mode servers still need a valid Microsoft session, so log in via IAS
 * first. Offline servers work without one.
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
     * Opens the IAS direct-connect screen (server IP field + join),
     * bypassing both the launcher menu and the vanilla server list.
     *
     * @param minecraft Minecraft instance
     * @param parent    Parent screen for the back button
     * @return {@code true} if the screen was opened
     */
    public static boolean openMultiplayer(Minecraft minecraft, Screen parent) {
        if (minecraft.player != null || minecraft.level != null) return false;
        try {
            DirectPlayScreen target = new DirectPlayScreen(parent);
            //$ set_screen minecraft target
            minecraft.gui.setScreen(target);
            return true;
        } catch (Throwable t) {
            LOGGER.error("IAS: Unable to open direct-play screen.", t);
            return false;
        }
    }

    /**
     * Joins the server with the current IAS session, mirroring vanilla
     * {@code JoinMultiplayerScreen#join}.
     *
     * @param minecraft Minecraft instance
     * @param parent    Parent screen for the connecting screen
     * @param ip        Server address as typed
     * @return Description of the invoked connect entrypoint
     * @throws Exception If the connect entrypoint cannot be invoked
     */
    public static String joinServer(Minecraft minecraft, Screen parent, String ip) throws Exception {
        //? if >=1.20.5 {
        ServerData data = new ServerData(ip, ip, ServerData.Type.OTHER);
        //?} else
        /*ServerData data = new ServerData(ip, ip, false);*/
        return connect(minecraft, parent, data);
    }

    /**
     * Joins the server, mirroring vanilla {@code JoinMultiplayerScreen#join}.
     * The connect entrypoint grew extra trailing parameters over the versions
     * (quick-play flag, transfer state), so it is resolved by parameter types
     * instead of a fixed signature. Trailing parameters use vanilla defaults:
     * {@code false} for flags, {@code null} for state objects.
     *
     * @param minecraft Minecraft instance
     * @param parent    Parent screen for the connecting screen
     * @param data      Server data, IP already filled in by the dialog
     * @return Description of the invoked connect entrypoint
     */
    private static String connect(Minecraft minecraft, Screen parent, ServerData data) throws Exception {
        ServerAddress address = ServerAddress.parseString(data.ip);
        Method target = null;
        int bestScore = Integer.MIN_VALUE;
        for (Method method : ConnectScreen.class.getDeclaredMethods()) {
            if (!Modifier.isStatic(method.getModifiers())) continue;
            if (method.getReturnType() != void.class) continue;
            // Never pick mixin handlers, lambdas, or other synthetic lookalikes:
            // Lunar adds e.g. handler$xxx$lunar$setCurrentServer(7) next to the
            // real entrypoint, and invoking it is a silent no-op.
            if (method.isSynthetic() || method.isBridge() || method.getName().indexOf('$') != -1) continue;
            Class<?>[] params = method.getParameterTypes();
            if (params.length < 4) continue;
            if (!params[0].isAssignableFrom(Screen.class)) continue;
            if (!params[1].isAssignableFrom(Minecraft.class)) continue;
            if (!params[2].isAssignableFrom(ServerAddress.class)) continue;
            if (!params[3].isAssignableFrom(ServerData.class)) continue;
            int score = params.length + ("startConnecting".equals(method.getName()) ? 1000 : 0);
            if (target == null || score > bestScore) {
                target = method;
                bestScore = score;
            }
        }
        if (target == null) {
            throw new NoSuchMethodException("IAS: No connect entrypoint found.");
        }
        LOGGER.info("IAS: Direct-play connect via {}.", target);
        Class<?>[] params = target.getParameterTypes();
        Object[] args = new Object[params.length];
        args[0] = parent;
        args[1] = minecraft;
        args[2] = address;
        args[3] = data;
        for (int i = 4; i < params.length; i++) {
            args[i] = params[i] == boolean.class ? Boolean.FALSE : null;
        }
        target.setAccessible(true);
        target.invoke(null, args);
        return String.valueOf(target);
    }

    /**
     * Vanilla option keys holding the last used server address, newest first.
     * Mojang renamed the key at some point, so both are probed.
     */
    private static final String[] LAST_ADDRESS_KEYS = {"lastMpIp", "lastServer"};

    /**
     * Reads the last used server address from the vanilla options.
     *
     * @param minecraft Minecraft instance
     * @param fallback  Value to return when nothing is stored
     * @return Last address or the fallback
     */
    public static String lastAddress(Minecraft minecraft, String fallback) {
        try {
            Object options = minecraft.options;
            if (options == null) return fallback;
            for (String key : LAST_ADDRESS_KEYS) {
                try {
                    Field field = options.getClass().getField(key);
                    if (field.getType() != String.class) continue;
                    Object value = field.get(options);
                    if (value instanceof String address && !address.isBlank()) return address;
                } catch (NoSuchFieldException ignored) {
                    // Try the next key.
                }
            }
        } catch (Throwable t) {
            LOGGER.debug("IAS: Unable to read last server address.", t);
        }
        return fallback;
    }

    /**
     * Stores the server address into the vanilla options, like the vanilla
     * dialog does.
     *
     * @param minecraft Minecraft instance
     * @param ip        Server address as typed
     */
    public static void saveLastAddress(Minecraft minecraft, String ip) {
        if (ip == null || ip.isBlank()) return;
        try {
            Object options = minecraft.options;
            if (options == null) return;
            boolean changed = false;
            for (String key : LAST_ADDRESS_KEYS) {
                try {
                    Field field = options.getClass().getField(key);
                    if (field.getType() != String.class) continue;
                    field.set(options, ip);
                    changed = true;
                } catch (NoSuchFieldException ignored) {
                    // Try the next key.
                }
            }
            if (changed) options.getClass().getMethod("save").invoke(options);
        } catch (Throwable t) {
            LOGGER.debug("IAS: Unable to save last server address.", t);
        }
    }

    /**
     * Opens the vanilla server list. This is a fallback for setups without the
     * launcher gate: on gated setups (e.g. Lunar without launcher sign-in) it
     * may redirect to Microsoft sign-in instead.
     *
     * @param minecraft Minecraft instance
     * @param parent    Parent screen for the back button
     * @return {@code true} if the screen was opened
     */
    public static boolean openServerList(Minecraft minecraft, Screen parent) {
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
