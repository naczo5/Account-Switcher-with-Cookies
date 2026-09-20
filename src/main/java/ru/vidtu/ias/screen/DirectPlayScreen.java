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

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.vidtu.ias.utils.DirectPlay;

//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else
/*import net.minecraft.client.gui.GuiGraphics;*/

/**
 * IAS-owned direct-connect screen (server IP field + join).
 * <p>
 * This intentionally does not reuse vanilla's {@code DirectJoinServerScreen}:
 * on gated setups (e.g. Lunar Client without launcher sign-in) the vanilla
 * multiplayer screens are hooked and their buttons silently do nothing.
 * Every widget here belongs to IAS, only the actual connect reuses vanilla.
 *
 * @author VidTu
 */
public final class DirectPlayScreen extends Screen {
    /**
     * Logger for this class.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger("IAS/DirectPlayScreen");

    /**
     * Parent screen, {@code null} if none.
     */
    private final Screen parent;

    /**
     * Server address field.
     */
    private PopupBox address;

    /**
     * Join button.
     */
    private Button join;

    /**
     * Status/error line.
     */
    private Component status = Component.empty();

    /**
     * Status line color, white by default.
     */
    private int statusColor = 0xFF_FF_FF_FF;

    /**
     * Creates a new screen.
     *
     * @param parent Parent screen, {@code null} if none
     */
    public DirectPlayScreen(Screen parent) {
        super(Component.translatable("ias.directPlay.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // Bruh.
        assert this.minecraft != null;

        // Add address field.
        this.address = new PopupBox(this.font, this.width / 2 - 100, this.height / 2 - 40, 200, 20, this.address,
                Component.translatable("ias.directPlay.address"), () -> {
                    if (this.join != null && this.join.active) this.doJoin();
                }, false);
        this.address.setMaxLength(255);
        this.address.setHint(Component.translatable("ias.directPlay.address").withStyle(ChatFormatting.DARK_GRAY));
        this.address.setValue(DirectPlay.lastAddress(this.minecraft, this.address.getValue()));
        this.address.setResponder(value -> {
            if (this.join != null) this.join.active = ServerAddress.isValidAddress(value.trim());
            this.status = Component.empty();
        });
        this.addRenderableWidget(this.address);
        this.setInitialFocus(this.address);

        // Add join button.
        this.join = Button.builder(Component.translatable("ias.directPlay.join"), btn -> this.doJoin())
                .bounds(this.width / 2 - 100, this.height / 2 - 12, 200, 20)
                .build();
        this.join.active = ServerAddress.isValidAddress(this.address.getValue().trim());
        this.addRenderableWidget(this.join);

        // Add cancel button.
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, btn -> this.onClose())
                .bounds(this.width / 2 - 100, this.height / 2 + 12, 200, 20)
                .build());

        // Add server-list fallback button.
        Button serverList = Button.builder(Component.translatable("ias.directPlay.serverList"),
                        btn -> DirectPlay.openServerList(this.minecraft, this))
                .bounds(4, 4, 100, 20)
                .tooltip(Tooltip.create(Component.translatable("ias.directPlay.serverList.tip")))
                .build();
        this.addRenderableWidget(serverList);
    }

    @Override
    public void onClose() {
        // Persist the typed address, like vanilla does.
        if (this.address != null && this.minecraft != null) {
            DirectPlay.saveLastAddress(this.minecraft, this.address.getValue());
        }
        //$ set_screen 'this.minecraft' 'this.parent'
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public void removed() {
        // Persist the typed address, like vanilla does.
        if (this.address != null && this.minecraft != null) {
            DirectPlay.saveLastAddress(this.minecraft, this.address.getValue());
        }
    }

    /**
     * Joins the typed server with the current IAS session.
     */
    private void doJoin() {
        assert this.minecraft != null;
        String ip = this.address.getValue().trim();
        if (!ServerAddress.isValidAddress(ip)) {
            this.status = Component.translatable("ias.directPlay.invalid").withStyle(ChatFormatting.RED);
            this.statusColor = 0xFF_FF_55_55;
            return;
        }
        // Join feedback; replaced by the connecting screen on success.
        this.status = Component.translatable("ias.directPlay.joining", ip);
        this.statusColor = 0xFF_FF_FF_FF;
        try {
            LOGGER.info("IAS: Direct-play joining: {}", ip);
            DirectPlay.saveLastAddress(this.minecraft, ip);
            // Run through the game-thread queue instead of calling from input handling.
            Minecraft minecraft = this.minecraft;
            DirectPlayScreen self = this;
            minecraft.execute(() -> self.attemptJoin(minecraft, ip));
        } catch (Throwable t) {
            LOGGER.error("IAS: Unable to join server.", t);
            String detail = t.getMessage();
            this.status = Component.translatable("ias.directPlay.error", detail == null ? t.toString() : detail)
                    .withStyle(ChatFormatting.RED);
            this.statusColor = 0xFF_FF_55_55;
        }
    }

    /**
     * Attempts the join with the current IAS session.
     *
     * @param minecraft Minecraft instance
     * @param ip        Server address as typed
     */
    private void attemptJoin(Minecraft minecraft, String ip) {
        try {
            String via = DirectPlay.joinServer(minecraft, this, ip);
            // startConnecting opens ConnectScreen synchronously. If another screen
            // is showing afterwards, the transition was suppressed somewhere.
            //? if >=26.2 {
            Screen now = minecraft.gui.screen();
            //?} else
            /*Screen now = minecraft.screen;*/
            if (now instanceof net.minecraft.client.gui.screens.ConnectScreen) return;
            String name = now == null ? "null" : now.getClass().getName();
            this.status = Component.translatable("ias.directPlay.blocked", via, name);
            this.statusColor = 0xFF_FF_55_55;
        } catch (Throwable t) {
            LOGGER.error("IAS: Unable to join server.", t);
            String detail = t.getMessage();
            this.status = Component.translatable("ias.directPlay.error", detail == null ? t.toString() : detail)
                    .withStyle(ChatFormatting.RED);
            this.statusColor = 0xFF_FF_55_55;
        }
    }

    @Override
    //? if >=26.1 {
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
    //?} else
    /*public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {*/
        // Render background and widgets.
        //? if >=26.1 {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        //?} else
        /*super.render(graphics, mouseX, mouseY, delta);*/

        // Render title.
        //? if >=26.1 {
        graphics.centeredText(this.font, this.title, this.width / 2, this.height / 2 - 60, 0xFF_FF_FF_FF);
        //?} else
        /*graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 60, 0xFF_FF_FF_FF);*/

        // Render status line.
        if (!this.status.getString().isBlank()) {
            //? if >=26.1 {
            graphics.centeredText(this.font, this.status, this.width / 2, this.height / 2 + 38, this.statusColor);
            //?} else
            /*graphics.drawCenteredString(this.font, this.status, this.width / 2, this.height / 2 + 38, this.statusColor);*/
        }
    }
}
