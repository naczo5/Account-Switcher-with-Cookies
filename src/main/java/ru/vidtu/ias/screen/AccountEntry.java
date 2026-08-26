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

import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else
/*import net.minecraft.client.gui.GuiGraphics;*/
import net.minecraft.client.gui.components.ObjectSelectionList;
//? if >=26.1 {
import net.minecraft.client.gui.components.PlayerFaceExtractor;
//?} else
/*import net.minecraft.client.gui.components.PlayerFaceRenderer;*/
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.NotNull;
import ru.vidtu.ias.account.Account;
import ru.vidtu.ias.platform.IStonecutter;
import ru.vidtu.ias.config.IASConfig;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

//? if >= 1.21.10 {
import net.minecraft.world.entity.player.PlayerSkin;
//?} else
/*import net.minecraft.client.resources.PlayerSkin;*/

/**
 * Account GUI entry.
 *
 * @author VidTu
 */
final class AccountEntry extends ObjectSelectionList.Entry<AccountEntry> {
    /**
     * Warning sprites.
     */
    private static final WidgetSprites WARNING = new WidgetSprites(
            IStonecutter.identifier("warning_off"),
            IStonecutter.identifier("warning_on")
    );

    /**
     * Minecraft instance.
     */
    private final Minecraft minecraft;

    /**
     * Parent list.
     */
    private final AccountList list;

    /**
     * IAS account.
     */
    private final Account account;

    /**
     * Account tooltip.
     */
    private final List<FormattedCharSequence> tooltip;

    /**
     * Last click time.
     */
    private long clicked = IStonecutter.internalMillisClock();

    /**
     * Last non-hovered time.
     */
    private long lastFree = System.nanoTime();

    /**
     * Creates a new account list entry widget.
     *
     * @param minecraft Minecraft instance
     * @param list      Parent list
     * @param account   IAS account
     */
    AccountEntry(Minecraft minecraft, AccountList list, Account account) {
        this.minecraft = minecraft;
        this.list = list;
        this.account = account;
        this.tooltip = Stream.of(
                CommonComponents.optionNameValue(Component.translatable("ias.accounts.tip.nick"), Component.literal(this.account.name())),
                CommonComponents.optionNameValue(Component.translatable("ias.accounts.tip.uuid"), Component.literal(this.account.uuid().toString())),
                CommonComponents.optionNameValue(Component.translatable("ias.accounts.tip.type"), Component.translatable(this.account.typeTipKey()))
        ).map(Component::getVisualOrderText).toList();
    }

    @Override
    //? if >=26.1 {
    public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float delta) {
    //?} elif >=1.21.10 {
    /*public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float delta) {
    *///?} else {
    /*public void render(GuiGraphics graphics, int index, int y, int x, int width, int height, int mouseX, int mouseY, boolean hovered, float delta) {*/
    //?}
        // Render the skin.
        PlayerSkin skin = this.list.skin(this);
        //? if >=1.21.10 {
        int x = this.getContentX();
        int y = this.getContentY();
        int width = this.getContentWidth();
        int height = this.getContentHeight();
        //?}
        //? if >=26.1 {
        PlayerFaceExtractor.extractRenderState(graphics, skin, x, y, 8);
        //?} else
        /*PlayerFaceRenderer.draw(graphics, skin, x, y, 8);*/

        // Get the name color.
        User user = this.minecraft.getUser();
        int color;
        // Mods break user non-nullness.
        //noinspection ConstantValue
        if (user == null || !this.account.name().equalsIgnoreCase(user.getName())) {
            color = 0xFF_FF_FF_FF;
        } else if (this.account.uuid().equals(user.getProfileId())) {
            color = 0xFF_00_FF_00;
        } else if (this.account.name().equals(user.getName())) {
            color = 0xFF_FF_FF_00;
        } else {
            color = 0xFF_FF_80_00;
        }

        // Render name.
        int nameX = x + 10;
        int nameWidth = this.minecraft.font.width(this.account.name());
        //? if >=26.1 {
        graphics.text(this.minecraft.font, this.account.name(), nameX, y, color);
        //?} else
        /*graphics.drawString(this.minecraft.font, this.account.name(), nameX, y, color);*/

        // Render exact name-change availability when it can be checked.
        boolean markerHovered = false;
        AccountList.NameChangeState nameChange = this.list.nameChangeState(this);
        if (nameChange != AccountList.NameChangeState.UNKNOWN || this.account.canLogin()) {
            Component marker = switch (nameChange) {
                case AVAILABLE -> Component.literal("\u2713");
                case UNAVAILABLE -> Component.literal("\u2715");
                case CHECKING -> Component.literal("?");
                case UNKNOWN -> Component.literal("?");
            };
            int markerColor = switch (nameChange) {
                case AVAILABLE -> 0xFF_00_FF_00;
                case UNAVAILABLE -> 0xFF_FF_40_40;
                case CHECKING -> 0xFF_FF_FF_00;
                case UNKNOWN -> 0xFF_80_80_80;
            };
            int markerX = Math.min(nameX + nameWidth + 5, x + width - 40);
            //? if >=26.1 {
            graphics.text(this.minecraft.font, marker, markerX, y, markerColor);
            //?} else
            /*graphics.drawString(this.minecraft.font, marker, markerX, y, markerColor);*/
            markerHovered = mouseX >= markerX && mouseX <= markerX + 8 && mouseY >= y && mouseY <= y + height;
            if (markerHovered) {
                String key = switch (nameChange) {
                    case AVAILABLE -> "ias.profile.name.available";
                    case UNAVAILABLE -> "ias.profile.name.unavailable";
                    case CHECKING -> "ias.profile.name.checking";
                    case UNKNOWN -> "ias.profile.name.unknown";
                };
                graphics.setTooltipForNextFrame(Component.translatable(key), mouseX, mouseY);
            }
        }

        // Render account tooltip only above the name, so it does not override marker tooltips.
        boolean nameHovered = mouseX >= nameX && mouseX <= nameX + nameWidth && mouseY >= y && mouseY <= y + height;
        if (hovered && nameHovered && !markerHovered) {
            if ((System.nanoTime() - this.lastFree) >= 500_000_000L) {
                graphics.setTooltipForNextFrame(this.tooltip, mouseX, mouseY);
            }
        } else {
            this.lastFree = System.nanoTime();
        }

        // Render bulk-selection marker.
        if (this.list.isMultiSelected(this)) {
            //? if >=26.1 {
            graphics.text(this.minecraft.font, Component.literal("+"), x - 10, y, 0xFF_00_FF_FF);
            //?} else
            /*graphics.drawString(this.minecraft.font, Component.literal("+"), x - 10, y, 0xFF_00_FF_FF);*/
        }

        // Render warning if insecure.
        if (this.account.insecure()) {
            boolean warning = (System.nanoTime() / 1_000_000_000L) % 2L == 0;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, warning ? WARNING.enabled() : WARNING.enabledFocused(), x - 6, y - 1, 2, 10);
            if (mouseX >= x - 10 && mouseX <= x && mouseY >= y && mouseY <= y + height) {
                graphics.setTooltipForNextFrame(Component.translatable("ias.accounts.tip.insecure"), mouseX, mouseY);
            }
        }
    }

    @Override
    //? if >=1.21.10 {
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
    //?} else
    /*public boolean mouseClicked(double mouseX, double mouseY, int button) {*/
        // Ctrl-click toggles bulk selection for deletion.
        //? if >=1.21.10 {
        if (event.hasControlDown()) {
            this.list.setSelected(this);
            this.list.toggleMultiSelection(this);
            return true;
        }
        //?}

        this.list.clearMultiSelection();

        // Login on double click.
        if (IStonecutter.internalMillisClock() - this.clicked < 250L) {
            //? if >=1.21.10 {
            this.list.login(!event.hasShiftDown(), IASConfig.closeOnLogin ? () -> {
                //$ set_screen minecraft 'this.list.screen().parent()'
                minecraft.gui.setScreen(this.list.screen().parent());
            } : null);
            //?} else
            /*this.list.login(!net.minecraft.client.gui.screens.Screen.hasShiftDown(), IASConfig.closeOnLogin ? () -> this.minecraft.setScreen(this.list.screen().parent()) : null);*/
            this.clicked = IStonecutter.internalMillisClock();
            return true;
        }

        // Set time for double click.
        this.clicked = IStonecutter.internalMillisClock();
        //? if >=1.21.10 {
        if (event.button() == 0) {
            this.list.startDragging(this);
        }
        //?}
        return true;
    }

    @Override
    @NotNull
    public Component getNarration() {
        return Component.literal(this.account.name());
    }

    /**
     * Gets the account.
     *
     * @return IAS account
     */
    Account account() {
        return this.account;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof AccountEntry that)) return false;
        return Objects.equals(this.account, that.account);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.account);
    }

    @Override
    public String toString() {
        return "AccountEntry{" +
                "account=" + this.account +
                '}';
    }
}
