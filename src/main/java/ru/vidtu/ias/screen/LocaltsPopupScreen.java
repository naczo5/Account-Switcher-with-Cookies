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
import ru.vidtu.ias.auth.localts.LocaltsProduct;
import ru.vidtu.ias.auth.localts.LocaltsPurchaseResult;
import ru.vidtu.ias.config.IASConfig;
import ru.vidtu.ias.crypt.Crypt;
import ru.vidtu.ias.platform.IStonecutter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Localts marketplace integration popup screen.
 * Displays credit balance, username, and packaged orders, allows in-game alt purchases,
 * and enables one-click batch import of purchased accounts into IAS.
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
    private static final int PANEL_HALF_H = 80;

    private final Screen parent;
    private final Consumer<Account> handler;
    private final Crypt crypt;

    private PopupBox apiKeyBox;
    private PopupButton saveKeyBtn;
    private PopupButton buyAltsBtn;
    private PopupButton importBtn;
    private PopupButton refreshBtn;
    private PopupButton changeKeyBtn;

    private boolean editingKey;
    private boolean buyingMode;
    private boolean loading;
    private boolean purchasing;
    private boolean closed;
    private boolean initialRefreshTriggered;
    private Component statusText = Component.empty();
    private LocaltsMe user;
    private final List<String> packagedAccounts = new ArrayList<>();
    private int packagedOrdersCount;

    // Buying mode state
    private final List<LocaltsProduct> products = new ArrayList<>();
    private int selectedProductIndex;
    private int purchaseQty = 1;

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
        } else if (this.buyingMode) {
            this.apiKeyBox = null;

            int prodY = centerY - 28;
            PopupButton prevBtn = new PopupButton(centerX - 100, prodY, 20, 20, Component.literal("<"), btn -> {
                if (!this.products.isEmpty()) {
                    this.selectedProductIndex = (this.selectedProductIndex - 1 + this.products.size()) % this.products.size();
                    this.purchaseQty = 1;
                    this.reInit();
                }
            }, Supplier::get);
            this.addRenderableWidget(prevBtn);

            PopupButton nextBtn = new PopupButton(centerX + 80, prodY, 20, 20, Component.literal(">"), btn -> {
                if (!this.products.isEmpty()) {
                    this.selectedProductIndex = (this.selectedProductIndex + 1) % this.products.size();
                    this.purchaseQty = 1;
                    this.reInit();
                }
            }, Supplier::get);
            this.addRenderableWidget(nextBtn);

            int qtyY = centerY + 4;
            PopupButton decBtn = new PopupButton(centerX - 45, qtyY, 20, 20, Component.literal("-"), btn -> {
                if (this.purchaseQty > 1) {
                    this.purchaseQty--;
                    this.reInit();
                }
            }, Supplier::get);
            this.addRenderableWidget(decBtn);

            PopupButton incBtn = new PopupButton(centerX + 25, qtyY, 20, 20, Component.literal("+"), btn -> {
                if (!this.products.isEmpty() && this.selectedProductIndex < this.products.size()) {
                    LocaltsProduct prod = this.products.get(this.selectedProductIndex);
                    if (this.purchaseQty < prod.stock()) {
                        this.purchaseQty++;
                        this.reInit();
                    }
                }
            }, Supplier::get);
            this.addRenderableWidget(incBtn);

            PopupButton confirmBuyBtn = new PopupButton(centerX - 80, centerY + 28, 160, 20,
                    Component.translatable("ias.localts.buy.confirm"), btn -> this.confirmPurchase(), Supplier::get);
            boolean canBuy = canPurchaseSelected();
            confirmBuyBtn.active = canBuy && !this.loading && !this.purchasing;
            confirmBuyBtn.color(0.5F, 1.0F, 0.5F, true);
            this.addRenderableWidget(confirmBuyBtn);

            PopupButton backBtn = new PopupButton(centerX - 50, centerY + 52, 100, 20,
                    CommonComponents.GUI_BACK, btn -> {
                this.buyingMode = false;
                this.reInit();
            }, Supplier::get);
            this.addRenderableWidget(backBtn);
        } else {
            this.apiKeyBox = null;
            int btnW = 150;

            this.buyAltsBtn = new PopupButton(centerX - btnW / 2, centerY - 24, btnW, 20,
                    Component.translatable("ias.localts.buy"), btn -> {
                this.buyingMode = true;
                this.purchaseQty = 1;
                this.reInit();
                if (this.products.isEmpty() && !this.loading) {
                    this.fetchProducts();
                }
            }, Supplier::get);
            this.buyAltsBtn.color(0.5F, 1.0F, 0.5F, true);
            this.buyAltsBtn.active = !this.loading;
            this.addRenderableWidget(this.buyAltsBtn);

            this.importBtn = new PopupButton(centerX - btnW / 2, centerY, btnW, 20,
                    Component.translatable("ias.localts.importAccounts", this.packagedAccounts.size()),
                    btn -> this.importPackagedAccounts(), Supplier::get);
            this.importBtn.active = !this.packagedAccounts.isEmpty() && !this.loading;
            this.importBtn.color(0.5F, 0.75F, 1.0F, true);
            this.addRenderableWidget(this.importBtn);

            this.refreshBtn = new PopupButton(centerX - 100, centerY + 24, 64, 20,
                    Component.translatable("ias.localts.refresh"), btn -> this.refreshData(), Supplier::get);
            this.refreshBtn.active = !this.loading;
            this.addRenderableWidget(this.refreshBtn);

            PopupButton browseStoreBtn = new PopupButton(centerX - 32, centerY + 24, 64, 20,
                    Component.translatable("ias.localts.store"), btn -> IStonecutter.openUrl("https://localts.store/store"), Supplier::get);
            browseStoreBtn.color(0.5F, 0.75F, 1.0F, true);
            this.addRenderableWidget(browseStoreBtn);

            this.changeKeyBtn = new PopupButton(centerX + 36, centerY + 24, 64, 20,
                    Component.translatable("ias.localts.keyBtn"), btn -> {
                this.editingKey = true;
                this.reInit();
            }, Supplier::get);
            this.addRenderableWidget(this.changeKeyBtn);

            PopupButton backBtn = new PopupButton(centerX - 50, centerY + 50, 100, 20,
                    CommonComponents.GUI_BACK, btn -> this.onClose(), Supplier::get);
            this.addRenderableWidget(backBtn);

            if (!this.initialRefreshTriggered && !this.editingKey && this.user == null && !this.loading) {
                this.initialRefreshTriggered = true;
                this.refreshData();
            }
        }
    }

    private void reInit() {
        //? if >=1.21.11 {
        this.init(this.width, this.height);
        //?} else
        /*this.init(this.minecraft, this.width, this.height);*/
    }

    private boolean canPurchaseSelected() {
        if (this.products.isEmpty() || this.selectedProductIndex >= this.products.size()) return false;
        LocaltsProduct prod = this.products.get(this.selectedProductIndex);
        if (prod.stock() < this.purchaseQty || this.purchaseQty < 1) return false;
        if (this.user == null) return false;
        long totalCost = (long) prod.priceInCredits() * this.purchaseQty;
        return this.user.balance() >= totalCost;
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

        this.reInit();
        this.refreshData();
    }

    private void refreshData() {
        if (this.closed || this.loading) return;

        String key = IASConfig.localtsApiKey;
        if (key == null || key.isBlank()) {
            this.editingKey = true;
            this.reInit();
            return;
        }

        this.loading = true;
        this.statusText = Component.translatable("ias.localts.loading").withStyle(ChatFormatting.YELLOW);

        EXECUTOR.execute(() -> {
            try {
                LocaltsClient client = new LocaltsClient(key);
                LocaltsMe me = client.getMe();
                List<LocaltsProduct> prods = client.getProducts();

                LocaltsOrdersPage page = client.getOrders(0, 50);
                List<String> accounts = new ArrayList<>();
                int packagedOrders = 0;

                for (LocaltsOrderSummary summary : page.orders()) {
                    if (this.closed) return;
                    try {
                        LocaltsOrder order = client.getOrder(summary.id());
                        if (order.hasPackagedItems()) {
                            List<String> extracted = LocaltsClient.extractOrderAccounts(order);
                            if (!extracted.isEmpty()) {
                                accounts.addAll(extracted);
                                packagedOrders++;
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                }

                final int finalPackagedOrders = packagedOrders;
                this.minecraft.execute(() -> {
                    if (this.closed || currentScreen() != this) return;
                    this.user = me;
                    this.products.clear();
                    this.products.addAll(prods);
                    this.packagedAccounts.clear();
                    this.packagedAccounts.addAll(accounts);
                    this.packagedOrdersCount = finalPackagedOrders;
                    this.loading = false;
                    this.statusText = Component.translatable("ias.localts.ready", this.packagedAccounts.size(), this.packagedOrdersCount)
                            .withStyle(ChatFormatting.GREEN);
                    this.reInit();
                });
            } catch (Throwable t) {
                LOGGER.error("IAS: Failed to refresh Localts data.", t);
                this.minecraft.execute(() -> {
                    if (this.closed || currentScreen() != this) return;
                    this.loading = false;
                    this.statusText = Component.literal(t.getMessage()).withStyle(ChatFormatting.RED);
                    this.reInit();
                });
            }
        });
    }

    private void fetchProducts() {
        this.loading = true;
        this.statusText = Component.translatable("ias.localts.loading").withStyle(ChatFormatting.YELLOW);

        EXECUTOR.execute(() -> {
            try {
                LocaltsClient client = new LocaltsClient(IASConfig.localtsApiKey);
                List<LocaltsProduct> prods = client.getProducts();
                this.minecraft.execute(() -> {
                    if (this.closed || currentScreen() != this) return;
                    this.products.clear();
                    this.products.addAll(prods);
                    this.loading = false;
                    this.statusText = Component.empty();
                    this.reInit();
                });
            } catch (Throwable t) {
                this.minecraft.execute(() -> {
                    if (this.closed || currentScreen() != this) return;
                    this.loading = false;
                    this.statusText = Component.literal(t.getMessage()).withStyle(ChatFormatting.RED);
                    this.reInit();
                });
            }
        });
    }

    private void confirmPurchase() {
        if (!canPurchaseSelected() || this.purchasing) return;
        LocaltsProduct prod = this.products.get(this.selectedProductIndex);
        int qty = this.purchaseQty;

        this.purchasing = true;
        this.statusText = Component.translatable("ias.localts.buy.purchasing").withStyle(ChatFormatting.YELLOW);
        this.reInit();

        EXECUTOR.execute(() -> {
            try {
                LocaltsClient client = new LocaltsClient(IASConfig.localtsApiKey);
                LocaltsPurchaseResult purchaseResult = client.purchase(prod.id(), qty);
                LocaltsOrder order = client.getOrder(purchaseResult.orderId());
                List<String> rawAccounts = LocaltsClient.extractOrderAccounts(order);

                final LocaltsMe updatedMe = client.getMe();
                this.minecraft.execute(() -> {
                    if (this.closed || currentScreen() != this) return;
                    this.user = updatedMe;
                    this.purchasing = false;
                    this.buyingMode = false;
                    if (!rawAccounts.isEmpty()) {
                        Screen cookieScreen = new CookiePopupScreen(this.parent, this.handler, this.crypt, rawAccounts);
                        //$set_screen 'this.minecraft' 'cookieScreen'
                        this.minecraft.gui.setScreen(cookieScreen);
                    } else {
                        this.statusText = Component.translatable("ias.localts.buy.success", qty).withStyle(ChatFormatting.GREEN);
                        this.reInit();
                    }
                });
            } catch (Throwable t) {
                LOGGER.error("IAS: Localts purchase failed.", t);
                this.minecraft.execute(() -> {
                    if (this.closed || currentScreen() != this) return;
                    this.purchasing = false;
                    this.statusText = Component.literal(t.getMessage()).withStyle(ChatFormatting.RED);
                    this.reInit();
                });
            }
        });
    }

    private void importPackagedAccounts() {
        if (this.packagedAccounts.isEmpty()) return;
        List<String> accountsToImport = new ArrayList<>(this.packagedAccounts);
        Screen cookieScreen = new CookiePopupScreen(this.parent, this.handler, this.crypt, accountsToImport);
        //$set_screen 'this.minecraft' 'cookieScreen'
        this.minecraft.gui.setScreen(cookieScreen);
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
        graphics.centeredText(this.font, this.title, (int) (this.width / 3.0F), (int) ((this.height / 2.0F - 72) / 1.5F), 0xFF_FF_FF_FF);
        //?} else
        /*graphics.drawCenteredString(this.font, this.title, (int) (this.width / 3.0F), (int) ((this.height / 2.0F - 72) / 1.5F), 0xFF_FF_FF_FF);*/
        pose.popMatrix();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        if (this.editingKey) {
            //? if >=26.1 {
            graphics.centeredText(this.font, Component.translatable("ias.localts.enterKey"), centerX, centerY - 38, 0xFF_AA_AA_AA);
            //?} else
            /*graphics.drawCenteredString(this.font, Component.translatable("ias.localts.enterKey"), centerX, centerY - 38, 0xFF_AA_AA_AA);*/
        } else if (this.buyingMode) {
            String balText = this.user != null ? (this.user.balance() + " credits") : "---";
            Component balComp = Component.translatable("ias.localts.buy.total", "", balText).withStyle(ChatFormatting.GOLD);
            //? if >=26.1 {
            graphics.centeredText(this.font, balComp, centerX, centerY - 52, 0xFF_FF_AA_00);
            //?} else
            /*graphics.drawCenteredString(this.font, balComp, centerX, centerY - 52, 0xFF_FF_AA_00);*/

            if (this.products.isEmpty()) {
                Component msg = this.loading ? Component.translatable("ias.localts.loading") : Component.translatable("ias.localts.buy.noProducts");
                //? if >=26.1 {
                graphics.centeredText(this.font, msg, centerX, centerY - 10, 0xFF_AA_AA_AA);
                //?} else
                /*graphics.drawCenteredString(this.font, msg, centerX, centerY - 10, 0xFF_AA_AA_AA);*/
            } else if (this.selectedProductIndex < this.products.size()) {
                LocaltsProduct prod = this.products.get(this.selectedProductIndex);
                Component prodLine = Component.literal(prod.name() + " (" + prod.priceInCredits() + "c)").withStyle(ChatFormatting.YELLOW);
                Component stockLine = Component.literal("Stock: " + prod.stock()).withStyle(prod.stock() > 0 ? ChatFormatting.GREEN : ChatFormatting.RED);
                Component qtyLine = Component.translatable("ias.localts.buy.qty", this.purchaseQty);

                long total = (long) prod.priceInCredits() * this.purchaseQty;
                Component totalLine = Component.literal("Total: " + total + " credits").withStyle(this.user != null && this.user.balance() >= total ? ChatFormatting.GREEN : ChatFormatting.RED);

                //? if >=26.1 {
                graphics.centeredText(this.font, prodLine, centerX, centerY - 24, 0xFF_FF_FF_55);
                graphics.centeredText(this.font, stockLine, centerX, centerY - 12, 0xFF_AA_AA_AA);
                graphics.centeredText(this.font, qtyLine, centerX, centerY + 10, 0xFF_FF_FF_FF);
                graphics.centeredText(this.font, totalLine, centerX, centerY + 20, 0xFF_FF_FF_FF);
                //?} else
                /*graphics.drawCenteredString(this.font, prodLine, centerX, centerY - 24, 0xFF_FF_FF_55);
                graphics.drawCenteredString(this.font, stockLine, centerX, centerY - 12, 0xFF_AA_AA_AA);
                graphics.drawCenteredString(this.font, qtyLine, centerX, centerY + 10, 0xFF_FF_FF_FF);
                graphics.drawCenteredString(this.font, totalLine, centerX, centerY + 20, 0xFF_FF_FF_FF);*/
            }
        } else if (this.user != null) {
            Component userInfo = Component.translatable("ias.localts.userInfo", this.user.username(), this.user.balance(), String.format("%.2f", this.user.balance() / 100.0));
            //? if >=26.1 {
            graphics.centeredText(this.font, userInfo, centerX, centerY - 44, 0xFF_55_FF_55);
            //?} else
            /*graphics.drawCenteredString(this.font, userInfo, centerX, centerY - 44, 0xFF_55_FF_55);*/

            if (this.statusText != null && !this.statusText.getString().isBlank()) {
                //? if >=26.1 {
                graphics.centeredText(this.font, this.statusText, centerX, centerY - 32, 0xFF_FF_FF_FF);
                //?} else
                /*graphics.drawCenteredString(this.font, this.statusText, centerX, centerY - 32, 0xFF_FF_FF_FF);*/
            }
        } else if (this.statusText != null && !this.statusText.getString().isBlank()) {
            //? if >=26.1 {
            graphics.centeredText(this.font, this.statusText, centerX, centerY - 36, 0xFF_FF_FF_FF);
            //?} else
            /*graphics.drawCenteredString(this.font, this.statusText, centerX, centerY - 36, 0xFF_FF_FF_FF);*/
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
