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
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;
import ru.vidtu.ias.account.Account;
import ru.vidtu.ias.crypt.Crypt;
import ru.vidtu.ias.crypt.DummyCrypt;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Account providers selection popup screen.
 * Lists available external account providers (currently Localts Store), with room for future providers.
 *
 * @author VidTu
 */
final class ProvidersPopupScreen extends Screen {
    private final Screen parent;
    private final Consumer<Account> handler;
    private final Crypt crypt;

    ProvidersPopupScreen(Screen parent, Consumer<Account> handler, Crypt crypt) {
        super(Component.translatable("ias.providers.title"));
        this.parent = parent;
        this.handler = handler;
        this.crypt = crypt != null ? crypt : DummyCrypt.INSTANCE;
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

        // Localts Store provider button
        PopupButton localtsBtn = new PopupButton(centerX - 100, centerY - 32, 200, 24,
                Component.translatable("ias.providers.localts"), btn -> {
            //$set_screen 'this.minecraft' 'new LocaltsPopupScreen(this.parent, this.handler, this.crypt)'
            this.minecraft.gui.setScreen(new LocaltsPopupScreen(this.parent, this.handler, this.crypt));
        }, Supplier::get);
        localtsBtn.setTooltip(Tooltip.create(Component.translatable("ias.providers.localts.desc")));
        localtsBtn.setTooltipDelay(Duration.ofMillis(250L));
        localtsBtn.color(1.0F, 0.75F, 0.35F, true);
        this.addRenderableWidget(localtsBtn);

        // Upcoming providers placeholder
        PopupButton moreBtn = new PopupButton(centerX - 100, centerY, 200, 24,
                Component.translatable("ias.providers.more"), btn -> {}, Supplier::get);
        moreBtn.setTooltip(Tooltip.create(Component.translatable("ias.providers.more.tip")));
        moreBtn.setTooltipDelay(Duration.ofMillis(250L));
        moreBtn.active = false;
        this.addRenderableWidget(moreBtn);

        // Back button
        PopupButton backBtn = new PopupButton(centerX - 50, centerY + 36, 100, 20,
                CommonComponents.GUI_BACK, btn -> this.onClose(), Supplier::get);
        this.addRenderableWidget(backBtn);
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
        pose.scale(1.5F, 1.5F);
        //? if >=26.1 {
        graphics.centeredText(this.font, this.title, (int) (this.width / 3.0F), (int) ((this.height / 2.0F - 62) / 1.5F), 0xFF_FF_FF_FF);
        //?} else
        /*graphics.drawCenteredString(this.font, this.title, (int) (this.width / 3.0F), (int) ((this.height / 2.0F - 62) / 1.5F), 0xFF_FF_FF_FF);*/
        pose.popMatrix();

        Component subtitle = Component.translatable("ias.providers.subtitle");
        //? if >=26.1 {
        graphics.centeredText(this.font, subtitle, this.width / 2, this.height / 2 - 44, 0xFF_AA_AA_AA);
        //?} else
        /*graphics.drawCenteredString(this.font, subtitle, this.width / 2, this.height / 2 - 44, 0xFF_AA_AA_AA);*/
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
        graphics.fill(centerX - 115, centerY - 68, centerX + 115, centerY + 68, 0xF8_20_20_30);
        graphics.fill(centerX - 114, centerY - 69, centerX + 114, centerY - 68, 0xF8_20_20_30);
        graphics.fill(centerX - 114, centerY + 68, centerX + 114, centerY + 69, 0xF8_20_20_30);
    }

    @Override
    public void onClose() {
        assert this.minecraft != null;
        //$set_screen 'this.minecraft' 'this.parent'
        this.minecraft.gui.setScreen(this.parent);
    }
}
