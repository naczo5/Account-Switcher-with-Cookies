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
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else
/*import net.minecraft.client.gui.GuiGraphics;*/
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.vidtu.ias.IAS;
import ru.vidtu.ias.account.Account;
import ru.vidtu.ias.auth.localts.LocaltsClient;
import ru.vidtu.ias.auth.localts.LocaltsMe;
import ru.vidtu.ias.auth.localts.LocaltsOrder;
import ru.vidtu.ias.auth.localts.LocaltsOrderSummary;
import ru.vidtu.ias.auth.localts.LocaltsOrdersPage;
import ru.vidtu.ias.config.IASConfig;
import ru.vidtu.ias.crypt.Crypt;
import ru.vidtu.ias.platform.IStonecutter;
import ru.vidtu.ias.utils.exceptions.FriendlyException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Localts marketplace integration popup screen.
 * <p>
 * Displays credit balance, username, and packaged orders, allowing one-click
 * batch import of purchased accounts into IAS.
 *
 * @author VidTu
 */
final class LocaltsPopupScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger("IAS/LocaltsPopupScreen");

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "IAS-Localts");
        t.setDaemon(true);
        return t;
    });

    private static final int PANEL_HALF_W = 125;
    private static final int PANEL_HALF_H = 75;

    private final Screen parent;
    private final Consumer<Account> handler;
    private final Crypt crypt;

    private PopupBox apiKeyBox;
    private PopupButton saveKeyBtn;
    private PopupButton importBtn;
    private PopupButton refreshBtn;
    private PopupButton changeKeyBtn;

    private boolean editingKey;
    private boolean loading;
    private boolean closed;
    private boolean initialRefreshTriggered;
    private Component statusText = Component.empty();
    private LocaltsMe user;
    private final List<String> packagedAccounts = new ArrayList<>();
    private int packagedOrdersCount;

    public LocaltsPopupScreen(Screen parent, Consumer<Account> handler, Crypt crypt) {
        super(Component.translatable("ias.localts"));
        this.parent = parent;
        this.handler = handler;
        this.crypt = crypt;
        this.editingKey = IASConfig.localtsApiKey == null || IASConfig.localtsApiKey.isBlank();
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

        this.clearWidgets();

        if (this.editingKey) {
            this.apiKeyBox = new PopupBox(this.font, centerX - 100, centerY - 25, 200, 20, this.apiKeyBox,
                    Component.translatable("ias.localts.key"), this::saveApiKey, false);
            this.apiKeyBox.setHint(Component.translatable("ias.localts.key.hint").withStyle(ChatFormatting.DARK_GRAY));
            this.apiKeyBox.setMaxLength(256);
            if (IASConfig.localtsApiKey != null) {
                this.apiKeyBox.setValue(IASConfig.localtsApiKey);
            }
            this.addRenderableWidget(this.apiKeyBox);

            this.saveKeyBtn = new PopupButton(centerX - 100, centerY + 2, 98, 20,
                    Component.translatable("ias.localts.connect"), btn -> this.saveApiKey(), Supplier::get);
            this.saveKeyBtn.color(0.5F, 1.0F, 0.5F, true);
            this.addRenderableWidget(this.saveKeyBtn);

            PopupButton getApiKeyBtn = new PopupButton(centerX + 2, centerY + 2, 98, 20,
                    Component.translatable("ias.localts.getKey"), btn -> IStonecutter.openUrl("https://localts.store/user/settings#api-keys"), Supplier::get);
            getApiKeyBtn.color(0.5F, 0.75F, 1.0F, true);
            this.addRenderableWidget(getApiKeyBtn);

            PopupButton cancelBtn = new PopupButton(centerX - 50, centerY + 28, 100, 20,
                    CommonComponents.GUI_CANCEL, btn -> this.onClose(), Supplier::get);
            this.addRenderableWidget(cancelBtn);
        } else {
            int importBtnWidth = 140;
            this.importBtn = new PopupButton(centerX - importBtnWidth / 2, centerY - 2, importBtnWidth, 20,
                    Component.translatable("ias.localts.importAccounts", this.packagedAccounts.size()),
                    btn -> this.importPackagedAccounts(), Supplier::get);
            this.importBtn.active = !this.packagedAccounts.isEmpty() && !this.loading;
            this.importBtn.color(0.5F, 1.0F, 0.5F, true);
            this.addRenderableWidget(this.importBtn);

            this.refreshBtn = new PopupButton(centerX - 100, centerY + 22, 64, 20,
                    Component.translatable("ias.localts.refresh"), btn -> this.refreshData(), Supplier::get);
            this.refreshBtn.active = !this.loading;
            this.addRenderableWidget(this.refreshBtn);

            PopupButton browseStoreBtn = new PopupButton(centerX - 32, centerY + 22, 64, 20,
                    Component.translatable("ias.localts.store"), btn -> IStonecutter.openUrl("https://localts.store/store"), Supplier::get);
            browseStoreBtn.color(0.5F, 0.75F, 1.0F, true);
            this.addRenderableWidget(browseStoreBtn);

            this.changeKeyBtn = new PopupButton(centerX + 36, centerY + 22, 64, 20,
                    Component.translatable("ias.localts.keyBtn"), btn -> {
                this.editingKey = true;
                //? if >=1.21.11 {
                this.init(this.width, this.height);
                //?} else
                /*this.init(this.minecraft, this.width, this.height);*/
            }, Supplier::get);
            this.addRenderableWidget(this.changeKeyBtn);

            PopupButton backBtn = new PopupButton(centerX - 50, centerY + 46, 100, 20,
                    CommonComponents.GUI_BACK, btn -> this.onClose(), Supplier::get);
            this.addRenderableWidget(backBtn);

            if (!this.initialRefreshTriggered && !this.editingKey && this.user == null && !this.loading) {
                this.initialRefreshTriggered = true;
                this.refreshData();
            }
        }
    }

    private void saveApiKey() {
        if (this.apiKeyBox == null) return;
        String key = this.apiKeyBox.getValue().strip();
        if (key.isBlank()) {
            this.statusText = Component.translatable("ias.error.localts.missingKey").withStyle(ChatFormatting.RED);
            return;
        }

        IASConfig.localtsApiKey = key;
        try {
            IAS.saveConfig();
        } catch (Throwable t) {
            LOGGER.error("IAS: Unable to save config.", t);
        }

        this.editingKey = false;
        this.user = null;
        this.packagedAccounts.clear();
        this.packagedOrdersCount = 0;
        this.statusText = Component.translatable("ias.localts.connecting").withStyle(ChatFormatting.YELLOW);
        this.initialRefreshTriggered = true;

        //? if >=1.21.11 {
        this.init(this.width, this.height);
        //?} else
        /*this.init(this.minecraft, this.width, this.height);*/

        this.refreshData();
    }

    private void refreshData() {
        if (this.closed || this.loading) return;

        String key = IASConfig.localtsApiKey;
        if (key == null || key.isBlank()) {
            this.editingKey = true;
            //? if >=1.21.11 {
            this.init(this.width, this.height);
            //?} else
            /*this.init(this.minecraft, this.width, this.height);*/
            return;
        }

        this.loading = true;
        this.statusText = Component.translatable("ias.localts.loading").withStyle(ChatFormatting.YELLOW);
        if (this.refreshBtn != null) this.refreshBtn.active = false;
        if (this.importBtn != null) this.importBtn.active = false;

        EXECUTOR.execute(() -> {
            try {
                LocaltsClient client = new LocaltsClient(key);
                LocaltsMe me = client.getMe();
                LocaltsOrdersPage page = client.getOrders(0, 10);

                List<String> accounts = new ArrayList<>();
                int packagedCount = 0;

                for (LocaltsOrderSummary summary : page.orders()) {
                    try {
                        LocaltsOrder order = client.getOrder(summary.id());
                        if (order.hasPackagedItems()) {
                            packagedCount++;
                            accounts.addAll(LocaltsClient.extractOrderAccountContents(order));
                        }
                    } catch (Throwable orderErr) {
                        LOGGER.warn("IAS/Localts: Failed to query order {}", summary.id(), orderErr);
                    }
                }

                assert this.minecraft != null;
                final int finalPackagedCount = packagedCount;
                this.minecraft.execute(() -> {
                    if (this.closed || this.minecraft == null || this.currentScreen() != this) return;
                    this.loading = false;
                    this.user = me;
                    this.packagedOrdersCount = finalPackagedCount;
                    this.packagedAccounts.clear();
                    this.packagedAccounts.addAll(accounts);
                    this.statusText = Component.translatable("ias.localts.ready", accounts.size(), finalPackagedCount)
                            .withStyle(accounts.isEmpty() ? ChatFormatting.GRAY : ChatFormatting.GREEN);

                    //? if >=1.21.11 {
                    this.init(this.width, this.height);
                    //?} else
                    /*this.init(this.minecraft, this.width, this.height);*/
                });
            } catch (Throwable t) {
                LOGGER.error("IAS/Localts: Failed to refresh Localts data", t);
                assert this.minecraft != null;
                this.minecraft.execute(() -> {
                    if (this.closed || this.minecraft == null || this.currentScreen() != this) return;
                    this.loading = false;
                    FriendlyException friendly = FriendlyException.friendlyInChain(t);
                    if (friendly != null) {
                        this.statusText = Component.translatable(friendly.key(), friendly.args()).withStyle(ChatFormatting.RED);
                    } else {
                        this.statusText = Component.translatable("ias.error").withStyle(ChatFormatting.RED);
                    }

                    if (this.refreshBtn != null) this.refreshBtn.active = true;
                    if (this.importBtn != null) this.importBtn.active = false;
                });
            }
        });
    }

    private void importPackagedAccounts() {
        if (this.packagedAccounts.isEmpty()) return;
        assert this.minecraft != null;
        CookiePopupScreen screen = new CookiePopupScreen(this.parent, this.handler, this.crypt, this.packagedAccounts);
        //$set_screen 'this.minecraft' 'screen'
        this.minecraft.gui.setScreen(screen);
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

        // Title
        pose.pushMatrix();
        pose.scale(2.0F, 2.0F);
        //? if >=26.1 {
        graphics.centeredText(this.font, this.title, this.width / 4, this.height / 4 - 35, 0xFF_FF_FF_FF);
        //?} else
        /*graphics.drawCenteredString(this.font, this.title, this.width / 4, this.height / 4 - 35, 0xFF_FF_FF_FF);*/
        pose.popMatrix();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        if (this.editingKey) {
            //? if >=26.1 {
            graphics.centeredText(this.font, Component.translatable("ias.localts.enterKey"), centerX, centerY - 38, 0xFF_AA_AA_AA);
            //?} else
            /*graphics.drawCenteredString(this.font, Component.translatable("ias.localts.enterKey"), centerX, centerY - 38, 0xFF_AA_AA_AA);*/
        } else if (this.user != null) {
            Component userInfo = Component.translatable("ias.localts.userInfo", this.user.username(), this.user.balance(), String.format("%.2f", this.user.balance() / 100.0));
            //? if >=26.1 {
            graphics.centeredText(this.font, userInfo, centerX, centerY - 38, 0xFF_55_FF_55);
            //?} else
            /*graphics.drawCenteredString(this.font, userInfo, centerX, centerY - 38, 0xFF_55_FF_55);*/

            if (this.statusText != null && !this.statusText.getString().isBlank()) {
                //? if >=26.1 {
                graphics.centeredText(this.font, this.statusText, centerX, centerY - 20, 0xFF_FF_FF_FF);
                //?} else
                /*graphics.drawCenteredString(this.font, this.statusText, centerX, centerY - 20, 0xFF_FF_FF_FF);*/
            }
        } else if (this.statusText != null && !this.statusText.getString().isBlank()) {
            //? if >=26.1 {
            graphics.centeredText(this.font, this.statusText, centerX, centerY - 25, 0xFF_FF_FF_FF);
            //?} else
            /*graphics.drawCenteredString(this.font, this.statusText, centerX, centerY - 25, 0xFF_FF_FF_FF);*/
        }
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
            //?} elif >=1.21.10 {
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
        graphics.fill(centerX - PANEL_HALF_W, centerY - PANEL_HALF_H, centerX + PANEL_HALF_W, centerY + PANEL_HALF_H, 0xF8_20_20_30);
        graphics.fill(centerX - PANEL_HALF_W + 1, centerY - PANEL_HALF_H - 1, centerX + PANEL_HALF_W - 1, centerY - PANEL_HALF_H, 0xF8_20_20_30);
        graphics.fill(centerX - PANEL_HALF_W + 1, centerY + PANEL_HALF_H, centerX + PANEL_HALF_W - 1, centerY + PANEL_HALF_H + 1, 0xF8_20_20_30);
    }

    @Override
    public void onClose() {
        this.closed = true;
        assert this.minecraft != null;
        //$set_screen 'this.minecraft' 'this.parent'
        this.minecraft.gui.setScreen(this.parent);
    }

    private Screen currentScreen() {
        //? if >=26.2 {
        return this.minecraft.gui.screen();
        //?} else {
        /*return this.minecraft.screen;
        *///?}
    }
}
