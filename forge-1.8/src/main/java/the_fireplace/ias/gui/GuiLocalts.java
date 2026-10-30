package the_fireplace.ias.gui;

import com.github.mrebhan.ingameaccountswitcher.tools.Config;
import com.github.mrebhan.ingameaccountswitcher.tools.alt.AltDatabase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.Sys;
import org.lwjgl.input.Keyboard;
import ru.vidtu.ias.auth.localts.LocaltsClient;
import ru.vidtu.ias.auth.localts.LocaltsException;
import ru.vidtu.ias.auth.localts.LocaltsMe;
import ru.vidtu.ias.auth.localts.LocaltsOrder;
import ru.vidtu.ias.auth.localts.LocaltsOrderSummary;
import ru.vidtu.ias.auth.localts.LocaltsOrdersPage;
import ru.vidtu.ias.auth.localts.LocaltsProduct;
import ru.vidtu.ias.auth.localts.LocaltsPurchaseResult;
import ru.vidtu.ias.config.IASConfig;
import ru.vidtu.iasfork.cookie.CookieAuth;
import ru.vidtu.iasfork.cookie.CookieParser;
import ru.vidtu.iasfork.cookie.TokenImporter;
import the_fireplace.ias.account.ExtendedAccountData;
import the_fireplace.ias.enums.EnumBool;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * In-game Localts Store and account importing interface for Minecraft 1.8.9.
 * Allows viewing balance, purchasing alts in-game, and importing purchased accounts into IAS.
 */
public class GuiLocalts extends GuiScreen {
    private final GuiScreen parent;
    private boolean editingKey;
    private boolean buyingMode;
    private boolean loading;
    private boolean importing;
    private boolean purchasing;
    private boolean initialRefreshTriggered;
    private volatile boolean closed;

    private GuiTextField apiKeyField;
    private String statusText = "";
    private int statusColor = 0xFFFFFF;

    private LocaltsMe user;
    private final List<String> packagedAccounts = new ArrayList<String>();
    private int packagedOrdersCount;

    // Buying mode state
    private final List<LocaltsProduct> products = new ArrayList<LocaltsProduct>();
    private int selectedProductIndex = 0;
    private int purchaseQty = 1;

    public GuiLocalts(GuiScreen parent) {
        this.parent = parent;
        this.editingKey = IASConfig.localtsApiKey == null || IASConfig.localtsApiKey.trim().isEmpty();
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        if (this.editingKey) {
            this.apiKeyField = new GuiTextField(0, this.fontRendererObj, centerX - 100, centerY - 25, 200, 20);
            this.apiKeyField.setMaxStringLength(256);
            this.apiKeyField.setFocused(true);
            if (IASConfig.localtsApiKey != null && !IASConfig.localtsApiKey.isEmpty()) {
                this.apiKeyField.setText(IASConfig.localtsApiKey);
            }

            this.buttonList.add(new GuiButton(1, centerX - 100, centerY + 2, 98, 20, I18n.format("ias.localts.connect")));
            this.buttonList.add(new GuiButton(2, centerX + 2, centerY + 2, 98, 20, I18n.format("ias.localts.getKey")));
            this.buttonList.add(new GuiButton(3, centerX - 50, centerY + 28, 100, 20, I18n.format("gui.cancel")));
        } else if (this.buyingMode) {
            this.apiKeyField = null;

            int prodY = centerY - 30;
            this.buttonList.add(new GuiButton(20, centerX - 110, prodY, 20, 20, "<"));
            this.buttonList.add(new GuiButton(21, centerX + 90, prodY, 20, 20, ">"));

            int qtyY = centerY + 2;
            this.buttonList.add(new GuiButton(22, centerX - 45, qtyY, 20, 20, "-"));
            this.buttonList.add(new GuiButton(23, centerX + 25, qtyY, 20, 20, "+"));

            GuiButton buyBtn = new GuiButton(24, centerX - 80, centerY + 28, 160, 20, I18n.format("ias.localts.buy.confirm"));
            boolean canBuy = canPurchaseSelected();
            buyBtn.enabled = canBuy && !this.loading && !this.purchasing;
            this.buttonList.add(buyBtn);

            this.buttonList.add(new GuiButton(25, centerX - 50, centerY + 52, 100, 20, I18n.format("gui.back")));
        } else {
            this.apiKeyField = null;

            int btnW = 160;
            // Buy Alts button
            GuiButton buyAltsBtn = new GuiButton(10, centerX - btnW / 2, centerY - 24, btnW, 20, I18n.format("ias.localts.buy"));
            buyAltsBtn.enabled = !this.loading && !this.importing;
            this.buttonList.add(buyAltsBtn);

            // Import Accounts button
            String importLabel = I18n.format("ias.localts.importAccounts", this.packagedAccounts.size());
            GuiButton importBtn = new GuiButton(11, centerX - btnW / 2, centerY, btnW, 20, importLabel);
            importBtn.enabled = !this.packagedAccounts.isEmpty() && !this.loading && !this.importing;
            this.buttonList.add(importBtn);

            // Row with Refresh, Store, Key
            int rowY = centerY + 26;
            this.buttonList.add(new GuiButton(12, centerX - 98, rowY, 62, 20, I18n.format("ias.localts.refresh")));
            this.buttonList.add(new GuiButton(13, centerX - 32, rowY, 64, 20, I18n.format("ias.localts.store")));
            this.buttonList.add(new GuiButton(14, centerX + 36, rowY, 62, 20, I18n.format("ias.localts.keyBtn")));

            // Back button
            this.buttonList.add(new GuiButton(15, centerX - 50, centerY + 50, 100, 20, I18n.format("gui.back")));

            if (!this.initialRefreshTriggered && this.user == null && !this.loading) {
                this.initialRefreshTriggered = true;
                refreshData();
            }
        }
    }

    private boolean canPurchaseSelected() {
        if (this.products.isEmpty() || this.selectedProductIndex >= this.products.size()) return false;
        LocaltsProduct prod = this.products.get(this.selectedProductIndex);
        if (prod.stock() >= 0 && prod.stock() < this.purchaseQty) return false;
        if (this.purchaseQty < 1) return false;
        if (this.user == null) return false;
        long totalCost = (long) prod.priceInCredits() * this.purchaseQty;
        return this.user.balance() >= totalCost;
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        this.closed = true;
        super.onGuiClosed();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (this.editingKey && this.apiKeyField != null) {
            this.apiKeyField.textboxKeyTyped(typedChar, keyCode);
            if (keyCode == Keyboard.KEY_RETURN) {
                saveApiKey();
                return;
            }
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (this.buyingMode) {
                this.buyingMode = false;
                initGui();
                return;
            }
            if (this.editingKey && IASConfig.localtsApiKey != null && !IASConfig.localtsApiKey.isEmpty()) {
                this.editingKey = false;
                initGui();
                return;
            }
            this.mc.displayGuiScreen(this.parent);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (this.editingKey && this.apiKeyField != null) {
            this.apiKeyField.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    public void updateScreen() {
        if (this.editingKey && this.apiKeyField != null) {
            this.apiKeyField.updateCursorCounter();
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (!button.enabled) return;

        if (button.id == 1) { // Connect
            saveApiKey();
        } else if (button.id == 2) { // Get API Key
            openUrl("https://localts.store/user/settings#api-keys");
        } else if (button.id == 3) { // Cancel
            if (IASConfig.localtsApiKey != null && !IASConfig.localtsApiKey.isEmpty()) {
                this.editingKey = false;
                initGui();
            } else {
                this.mc.displayGuiScreen(this.parent);
            }
        } else if (button.id == 10) { // Buy Alts
            this.buyingMode = true;
            this.purchaseQty = 1;
            initGui();
            if (this.products.isEmpty() && !this.loading) {
                fetchProducts();
            }
        } else if (button.id == 11) { // Import Accounts
            importPackagedAccounts();
        } else if (button.id == 12) { // Refresh
            refreshData();
        } else if (button.id == 13) { // Store (open browser)
            openUrl("https://localts.store/store");
        } else if (button.id == 14) { // Edit API Key
            this.editingKey = true;
            initGui();
        } else if (button.id == 15) { // Back
            this.mc.displayGuiScreen(this.parent);
        } else if (button.id == 20) { // Prev product
            if (!this.products.isEmpty()) {
                this.selectedProductIndex = (this.selectedProductIndex - 1 + this.products.size()) % this.products.size();
                this.purchaseQty = 1;
                initGui();
            }
        } else if (button.id == 21) { // Next product
            if (!this.products.isEmpty()) {
                this.selectedProductIndex = (this.selectedProductIndex + 1) % this.products.size();
                this.purchaseQty = 1;
                initGui();
            }
        } else if (button.id == 22) { // Qty -
            if (this.purchaseQty > 1) {
                this.purchaseQty--;
                initGui();
            }
        } else if (button.id == 23) { // Qty +
            if (!this.products.isEmpty() && this.selectedProductIndex < this.products.size()) {
                LocaltsProduct prod = this.products.get(this.selectedProductIndex);
                if (this.purchaseQty < prod.stock()) {
                    this.purchaseQty++;
                    initGui();
                }
            }
        } else if (button.id == 24) { // Confirm Buy
            confirmPurchase();
        } else if (button.id == 25) { // Back from buy
            this.buyingMode = false;
            initGui();
        }
    }

    private void saveApiKey() {
        if (this.apiKeyField == null) return;
        String key = this.apiKeyField.getText().trim();
        if (key.isEmpty()) {
            this.statusText = I18n.format("ias.error.localts.missingKey");
            this.statusColor = 0xFF5555;
            return;
        }
        IASConfig.localtsApiKey = key;
        IASConfig.save(this.mc.mcDataDir);
        this.editingKey = false;
        this.user = null;
        this.packagedAccounts.clear();
        this.packagedOrdersCount = 0;
        this.statusText = I18n.format("ias.localts.connecting");
        this.statusColor = 0xFFFF55;
        initGui();
        refreshData();
    }

    private void refreshData() {
        if (this.loading || IASConfig.localtsApiKey == null || IASConfig.localtsApiKey.trim().isEmpty()) return;
        this.loading = true;
        this.statusText = I18n.format("ias.localts.loading");
        this.statusColor = 0xFFFF55;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    LocaltsClient client = new LocaltsClient(IASConfig.localtsApiKey);
                    final LocaltsMe me = client.getMe();
                    final List<LocaltsProduct> prods = client.getProducts();

                    LocaltsOrdersPage page = client.getOrders(0, 50);
                    final List<String> accounts = new ArrayList<String>();
                    int pkgOrders = 0;

                    for (LocaltsOrderSummary summary : page.orders()) {
                        if (closed) return;
                        try {
                            LocaltsOrder order = client.getOrder(summary.id());
                            if (order.hasPackagedItems()) {
                                List<String> extracted = LocaltsClient.extractOrderAccounts(order);
                                if (!extracted.isEmpty()) {
                                    accounts.addAll(extracted);
                                    pkgOrders++;
                                }
                            }
                        } catch (Throwable ignored) {
                        }
                    }

                    final int finalPkgOrders = pkgOrders;
                    Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                        @Override
                        public void run() {
                            if (closed) return;
                            user = me;
                            products.clear();
                            products.addAll(prods);
                            packagedAccounts.clear();
                            packagedAccounts.addAll(accounts);
                            packagedOrdersCount = finalPkgOrders;
                            loading = false;
                            statusText = I18n.format("ias.localts.ready", packagedAccounts.size(), packagedOrdersCount);
                            statusColor = 0x55FF55;
                            initGui();
                        }
                    });
                } catch (final Throwable t) {
                    Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                        @Override
                        public void run() {
                            if (closed) return;
                            loading = false;
                            statusText = formatError(t);
                            statusColor = 0xFF5555;
                            initGui();
                        }
                    });
                }
            }
        }, "IAS-Localts-Refresh").start();
    }

    private void fetchProducts() {
        this.loading = true;
        this.statusText = I18n.format("ias.localts.loading");
        this.statusColor = 0xFFFF55;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    LocaltsClient client = new LocaltsClient(IASConfig.localtsApiKey);
                    final List<LocaltsProduct> prods = client.getProducts();
                    Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                        @Override
                        public void run() {
                            if (closed) return;
                            products.clear();
                            products.addAll(prods);
                            loading = false;
                            statusText = "";
                            initGui();
                        }
                    });
                } catch (final Throwable t) {
                    Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                        @Override
                        public void run() {
                            if (closed) return;
                            loading = false;
                            statusText = formatError(t);
                            statusColor = 0xFF5555;
                            initGui();
                        }
                    });
                }
            }
        }, "IAS-Localts-Products").start();
    }

    private void confirmPurchase() {
        if (!canPurchaseSelected() || this.purchasing || this.importing) return;
        final LocaltsProduct prod = this.products.get(this.selectedProductIndex);
        final int qty = this.purchaseQty;

        this.purchasing = true;
        this.statusText = I18n.format("ias.localts.buy.purchasing");
        this.statusColor = 0xFFFF55;
        initGui();

        new Thread(new Runnable() {
            @Override
            public void run() {
                LocaltsPurchaseResult purchaseResult = null;
                try {
                    LocaltsClient client = new LocaltsClient(IASConfig.localtsApiKey);
                    purchaseResult = client.purchase(prod.id(), qty);
                    LocaltsOrder order = client.getOrder(purchaseResult.orderId());
                    List<String> rawAccounts = LocaltsClient.extractOrderAccounts(order);

                    final List<CookieAuth.MinecraftProfile> profiles = new ArrayList<CookieAuth.MinecraftProfile>();
                    for (String payload : rawAccounts) {
                        try {
                            List<String> tokens = TokenImporter.extractValues(payload);
                            if (!tokens.isEmpty()) {
                                profiles.add(TokenImporter.importSingleToken(tokens.get(0)));
                            } else {
                                CookieParser.ParsedCookies parsed = CookieParser.fromText(payload);
                                profiles.add(CookieAuth.authenticate(parsed));
                            }
                        } catch (Throwable t) {
                            t.printStackTrace();
                        }
                    }

                    LocaltsMe updatedMe = null;
                    try {
                        updatedMe = client.getMe();
                    } catch (Throwable ignored) {
                    }
                    final LocaltsMe finalMe = updatedMe;

                    Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                        @Override
                        public void run() {
                            if (closed) return;
                            if (finalMe != null) {
                                user = finalMe;
                            }
                            int count = 0;
                            for (CookieAuth.MinecraftProfile profile : profiles) {
                                saveAccount(profile);
                                count++;
                            }
                            if (count > 0) {
                                Config.save();
                            }
                            purchasing = false;
                            buyingMode = false;
                            statusText = I18n.format("ias.localts.buy.success", count);
                            statusColor = 0x55FF55;
                            initGui();
                        }
                    });
                } catch (final Throwable t) {
                    final LocaltsPurchaseResult res = purchaseResult;
                    Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                        @Override
                        public void run() {
                            if (closed) return;
                            purchasing = false;
                            if (res != null && res.orderId() != null && !res.orderId().isEmpty()) {
                                statusText = "Order " + res.orderId() + " created, delivery failed: " + formatError(t) + ". Click Refresh to retrieve.";
                                statusColor = 0xFFAA00;
                            } else {
                                statusText = formatError(t);
                                statusColor = 0xFF5555;
                            }
                            initGui();
                        }
                    });
                }
            }
        }, "IAS-Localts-Purchase").start();
    }

    private void importPackagedAccounts() {
        if (this.packagedAccounts.isEmpty() || this.importing || this.purchasing) return;
        this.importing = true;
        this.statusText = I18n.format("ias.login.cookiesToMsaMsr");
        this.statusColor = 0xFFFF55;
        initGui();

        final List<String> toImport = new ArrayList<String>(this.packagedAccounts);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<CookieAuth.MinecraftProfile> profiles = new ArrayList<CookieAuth.MinecraftProfile>();
                for (String payload : toImport) {
                    if (closed) return;
                    try {
                        List<String> tokens = TokenImporter.extractValues(payload);
                        if (!tokens.isEmpty()) {
                            profiles.add(TokenImporter.importSingleToken(tokens.get(0)));
                        } else {
                            CookieParser.ParsedCookies parsed = CookieParser.fromText(payload);
                            profiles.add(CookieAuth.authenticate(parsed));
                        }
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }

                Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        if (closed) return;
                        int count = 0;
                        for (CookieAuth.MinecraftProfile profile : profiles) {
                            saveAccount(profile);
                            count++;
                        }
                        if (count > 0) {
                            Config.save();
                        }
                        importing = false;
                        statusText = I18n.format("ias.localts.imported", count);
                        statusColor = 0x55FF55;
                        initGui();
                    }
                });
            }
        }, "IAS-Localts-Import").start();
    }

    private static boolean saveAccount(CookieAuth.MinecraftProfile profile) {
        ExtendedAccountData data = ExtendedAccountData.cookieSession(profile.name, profile.token, profile.uuid, profile.refreshToken);
        data.premium = EnumBool.TRUE;
        return ExtendedAccountData.replaceOrAddCookieAccount(AltDatabase.getInstance(), data);
    }

    private static String formatError(Throwable t) {
        if (t == null) return "Unknown error";
        if (t instanceof LocaltsException) {
            LocaltsException le = (LocaltsException) t;
            if (le.translationKey() != null && !le.translationKey().isEmpty()) {
                try {
                    return I18n.format(le.translationKey(), le.args());
                } catch (Throwable ignored) {
                }
            }
        }
        String msg = t.getMessage();
        return (msg != null && !msg.isEmpty()) ? msg : t.toString();
    }

    private static void openUrl(String url) {
        try {
            Sys.openURL(url);
        } catch (Throwable t) {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(new URI(url));
                }
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (this.parent != null) {
            this.parent.drawScreen(0, 0, partialTicks);
        }
        this.drawDefaultBackground();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        if (this.editingKey) {
            this.drawCenteredString(this.fontRendererObj, I18n.format("ias.localts.title"), centerX, centerY - 60, 0xFFFFFF);
            this.drawCenteredString(this.fontRendererObj, I18n.format("ias.localts.key"), centerX, centerY - 40, 0xAAAAAA);
            if (this.apiKeyField != null) {
                this.apiKeyField.drawTextBox();
            }
        } else if (this.buyingMode) {
            this.drawCenteredString(this.fontRendererObj, I18n.format("ias.localts.buy.title"), centerX, centerY - 64, 0xFFFFFF);

            String balanceStr = this.user != null ? String.valueOf(this.user.balance()) : "---";

            if (this.products.isEmpty()) {
                String msg = this.loading ? I18n.format("ias.localts.loading") : I18n.format("ias.localts.buy.noProducts");
                this.drawCenteredString(this.fontRendererObj, msg, centerX, centerY - 10, 0xAAAAAA);
            } else if (this.selectedProductIndex < this.products.size()) {
                LocaltsProduct prod = this.products.get(this.selectedProductIndex);

                String stockStr = prod.stock() >= 0 ? String.valueOf(prod.stock()) : "∞";
                String prodLine = I18n.format("ias.localts.buy.product", prod.name(), prod.priceInCredits(), stockStr);
                this.drawCenteredString(this.fontRendererObj, prodLine, centerX, centerY - 25, 0xFFFFAA);

                this.drawCenteredString(this.fontRendererObj, I18n.format("ias.localts.buy.qty", this.purchaseQty), centerX, centerY + 8, 0xFFFFFF);

                long totalCost = (long) prod.priceInCredits() * this.purchaseQty;
                boolean affordable = this.user != null && this.user.balance() >= totalCost;
                String totalLine = I18n.format("ias.localts.buy.total", totalCost, balanceStr);
                this.drawCenteredString(this.fontRendererObj, (affordable ? EnumChatFormatting.YELLOW : EnumChatFormatting.RED) + totalLine, centerX, centerY + 18, 0xFFFFFF);
            }
        } else {
            this.drawCenteredString(this.fontRendererObj, I18n.format("ias.localts.title"), centerX, centerY - 64, 0xFFFFFF);

            if (this.user != null) {
                String userHeader = I18n.format("ias.localts.connected", this.user.username(), this.user.balance());
                this.drawCenteredString(this.fontRendererObj, EnumChatFormatting.GREEN + userHeader, centerX, centerY - 48, 0xFFFFFF);
            } else if (this.loading) {
                this.drawCenteredString(this.fontRendererObj, I18n.format("ias.localts.connecting"), centerX, centerY - 48, 0xFFFF55);
            }
        }

        if (this.statusText != null && !this.statusText.isEmpty()) {
            this.drawCenteredString(this.fontRendererObj, this.statusText, centerX, this.height - 24, this.statusColor);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
