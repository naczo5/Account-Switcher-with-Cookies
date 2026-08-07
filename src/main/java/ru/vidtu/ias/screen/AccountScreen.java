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
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.vidtu.ias.IAS;
import ru.vidtu.ias.IASMinecraft;
import ru.vidtu.ias.account.Account;
import ru.vidtu.ias.account.MicrosoftAccount;
import ru.vidtu.ias.auth.LoginData;
import ru.vidtu.ias.auth.microsoft.MSAuth;
import ru.vidtu.ias.config.IASStorage;
import ru.vidtu.ias.platform.IStonecutter;
import ru.vidtu.ias.config.IASConfig;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public final class AccountScreen extends Screen {
    /**
     * Logger for this class.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger("IAS/AccountScreen");

    /**
     * Minecraft IGN validation pattern.
     */
    private static final Pattern MINECRAFT_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

    /**
     * Parent screen, {@code null} if none.
     */
    private final Screen parent;

    /**
     * Search widget.
     */
    private EditBox search;

    /**
     * Account list widget.
     */
    private AccountList list;

    /**
     * Player skin widget.
     */
    private PlayerSkinWidget skin;

    /**
     * Skin PNG picker button.
     */
    private PopupButton skinPng;

    /**
     * Skin model toggle button.
     */
    private PopupButton skinModel;

    /**
     * Apply selected skin button.
     */
    private PopupButton applySkin;

    /**
     * IGN edit box.
     */
    private PopupBox nameInput;

    /**
     * Apply IGN button.
     */
    private PopupButton applyName;

    /**
     * Last account shown in profile controls.
     */
    private Account controlsAccount;

    /**
     * Selected skin PNG path.
     */
    private String skinPngPath = "";

    /**
     * Whether the selected skin should be uploaded as slim model.
     */
    private boolean slimSkin;

    /**
     * Small profile update status line.
     */
    private Component profileStatus = Component.empty();

    /**
     * Login button.
     */
    private Button login;

    /**
     * Offline login button.
     */
    private Button offlineLogin;

    /**
     * Edit button.
     */
    private Button edit;

    /**
     * Edit button.
     */
    private Button delete;

    /**
     * Log out cookie button.
     */
    private Button logoutCookie;

    /**
     * Copy token button.
     */
    private Button copyToken;

    /**
     * Creates a new screen.
     *
     * @param parent Parent screen, {@code null} if none
     */
    public AccountScreen(Screen parent) {
        super(Component.translatable("ias.accounts"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // Bruh.
        assert this.minecraft != null;

        // Disabled check.
        if (IAS.disabled()) {
            final Screen alert = new AlertScreen(this::onClose, Component.translatable("ias.disabled.title").withStyle(ChatFormatting.RED),
                    Component.translatable("ias.disabled.text"), CommonComponents.GUI_BACK, true);
            //$ set_screen 'this.minecraft' 'alert'
            this.minecraft.gui.setScreen(alert);
            return;
        }

        // Disclaimer.
        if (!IASStorage.gameDisclaimerShown) {
            final Screen alert = new AlertScreen(() -> {
                // Save disclaimer.
                try {
                    IAS.gameDisclaimerShownStorage();
                } catch (Throwable t) {
                    LOGGER.error("Unable to set or write game disclaimer state.", t);
                }

                // Set screen.
                //$ set_screen 'this.minecraft' this
                this.minecraft.gui.setScreen(this);
            }, Component.translatable("ias.disclaimer.title").withStyle(ChatFormatting.YELLOW),
                    Component.translatable("ias.disclaimer.text"), CommonComponents.GUI_CONTINUE, false);
            //$ set_screen 'this.minecraft' 'alert'
            this.minecraft.gui.setScreen(alert);
            return;
        }

        // Add search widget.
        this.search = new EditBox(this.font, this.width / 2 - 75, 11, 150, 20, this.search, Component.translatable("ias.accounts.search"));
        this.search.setHint(this.search.getMessage().copy().withStyle(ChatFormatting.DARK_GRAY));
        this.addRenderableWidget(this.search);

        // Add skin renderer.
        if (this.skin == null) {
            this.skin = new PlayerSkinWidget(85, 120, this.minecraft.getEntityModels(), () -> {
                // Return default if list is removed. (for whatever reason)
                if (this.list == null) return DefaultPlayerSkin.get(IStonecutter.NIL_UUID);

                // Return default if nothing is selected. (for whatever reason)
                AccountEntry selected = this.list.getSelected();
                if (selected == null) return DefaultPlayerSkin.get(IStonecutter.NIL_UUID);

                // Return skin of selected.
                return this.list.skin(selected);
            });
        }
        this.skin.setPosition(5, this.height / 2 - 60);
        this.addRenderableWidget(this.skin);

        // Add profile controls under skin preview.
        int profileX = 4;
        int profileW = 160;
        int profileY = this.height / 2 + 64;
        this.skinPng = new PopupButton(profileX, profileY, profileW, 20,
                Component.translatable("ias.profile.skin.png"), btn -> this.browseSkinPng(), Supplier::get);
        this.skinPng.color(0.25F, 1.0F, 1.0F, true);
        this.skinPng.setTooltip(Tooltip.create(Component.translatable("ias.profile.skin.png.tip")));
        this.skinPng.setTooltipDelay(Duration.ofMillis(250L));
        this.addRenderableWidget(this.skinPng);

        this.skinModel = new PopupButton(profileX, profileY + 24, 78, 20,
                this.skinModelMessage(), btn -> {
            this.slimSkin = !this.slimSkin;
            this.skinModel.setMessage(this.skinModelMessage());
        }, Supplier::get);
        this.skinModel.color(0.75F, 0.75F, 1.0F, true);
        this.skinModel.setTooltip(Tooltip.create(Component.translatable("ias.profile.skin.model.tip")));
        this.skinModel.setTooltipDelay(Duration.ofMillis(250L));
        this.addRenderableWidget(this.skinModel);

        this.applySkin = new PopupButton(profileX + 82, profileY + 24, 78, 20,
                Component.translatable("ias.profile.skin.apply"), btn -> this.applySkinPng(), Supplier::get);
        this.applySkin.color(0.5F, 1.0F, 0.5F, true);
        this.addRenderableWidget(this.applySkin);

        this.nameInput = new PopupBox(this.font, profileX, profileY + 48, profileW, 20, this.nameInput,
                Component.translatable("ias.profile.name.input"), this::confirmNameChange, false);
        this.nameInput.setMaxLength(16);
        this.nameInput.setHint(Component.translatable("ias.profile.name.input").withStyle(ChatFormatting.DARK_GRAY));
        this.nameInput.setResponder(value -> this.updateProfileControlState());
        this.addRenderableWidget(this.nameInput);

        this.applyName = new PopupButton(profileX, profileY + 72, profileW, 20,
                Component.translatable("ias.profile.name.apply"), btn -> this.confirmNameChange(), Supplier::get);
        this.applyName.color(0.5F, 1.0F, 0.5F, true);
        this.applyName.setTooltip(Tooltip.create(Component.translatable("ias.profile.name.apply.tip")));
        this.applyName.setTooltipDelay(Duration.ofMillis(250L));
        this.addRenderableWidget(this.applyName);

        // Add login button.
        this.login = Button.builder(Component.translatable("ias.accounts.login"), btn -> {
            this.list.login(true, IASConfig.closeOnLogin ? () -> {
                //$ set_screen 'this.minecraft' 'this.parent'
                this.minecraft.gui.setScreen(this.parent);
            } : null);
        })
            .bounds(this.width / 2 - 50 - 100 - 4, this.height - 24 - 24, 100, 20).build();
        this.addRenderableWidget(this.login);

        // Add offline login button.
        this.offlineLogin = Button.builder(Component.translatable("ias.accounts.offlineLogin"), btn -> {
            this.list.login(false, IASConfig.closeOnLogin ? () -> {
                //$ set_screen 'this.minecraft' 'this.parent'
                this.minecraft.gui.setScreen(this.parent);
            } : null);
        })
            .bounds(this.width / 2 - 50 - 100 - 4, this.height - 24, 100, 20)
            .build();
        this.addRenderableWidget(this.offlineLogin);

        // Add edit button.
        this.edit = Button.builder(Component.translatable("ias.accounts.edit"), btn -> this.list.edit())
                .bounds(this.width / 2 - 50, this.height - 24 - 24, 100, 20)
                .build();
        this.addRenderableWidget(this.edit);

        // Add delete button.
        //? if >=1.21.10 {
        this.delete = Button.builder(Component.translatable("ias.accounts.delete"), btn -> this.list.delete(!this.minecraft.hasShiftDown()))
        //?} else
        /*this.delete = Button.builder(Component.translatable("ias.accounts.delete"), btn -> this.list.delete(!Screen.hasShiftDown()))*/
                .bounds(this.width / 2 - 50, this.height - 24, 100, 20)
                .build();
        this.addRenderableWidget(this.delete);

        // Add cookie logout button.
        this.logoutCookie = Button.builder(Component.translatable("ias.accounts.logoutCookie"), btn -> this.logoutCookie())
                .bounds(this.width / 2 - 50, this.height - 24 - 24 - 24, 100, 20)
                .build();
        this.logoutCookie.setTooltip(Tooltip.create(Component.translatable("ias.accounts.logoutCookie.tip")));
        this.addRenderableWidget(this.logoutCookie);

        // Add copy token button.
        this.copyToken = Button.builder(Component.translatable("ias.accounts.copyToken"), btn -> this.list.copyToken())
                .bounds(this.width / 2 + 50 + 4, this.height - 24 - 24 - 24, 100, 20)
                .build();
        this.copyToken.setTooltip(Tooltip.create(Component.translatable("ias.accounts.copyToken.tip")));
        this.copyToken.setTooltipDelay(Duration.ofMillis(250L));
        this.addRenderableWidget(this.copyToken);

        // Add edit button.
        this.addRenderableWidget(Button.builder(Component.translatable("ias.accounts.add"), btn -> this.list.add())
                .bounds(this.width / 2 + 50 + 4, this.height - 24 - 24, 100, 20)
                .build());

        // Add delete button.
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, btn -> {
            //$ set_screen 'this.minecraft' 'this.parent'
            this.minecraft.gui.setScreen(this.parent);
        })
                .bounds(this.width / 2 + 50 + 4, this.height - 24, 100, 20)
                .build());

        // Add account list.
        if (this.list != null) {
            this.list.setRectangle(this.width, this.height - 24 - 24 - 24 - 4 - 34, 0, 34);
        } else {
            this.list = new AccountList(this, this.minecraft, this.width, this.height - 24 - 24 - 24 - 4 - 34, 34, 12);
        }
        this.addRenderableWidget(this.list);

        // Update the list.
        this.search.setResponder(this.list::update);
        this.list.update(this.search.getValue());
        this.updateSelected();
    }

    @Override
    public void onClose() {
        // Bruh.
        assert this.minecraft != null;

        // Close to parent.
        //$ set_screen 'this.minecraft' 'this.parent'
        this.minecraft.gui.setScreen(this.parent);
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
        graphics.centeredText(this.font, this.title, this.width / 2, 1, 0xFF_FF_FF_FF);
        //?} else
        /*graphics.drawCenteredString(this.font, this.title, this.width / 2, 1, 0xFF_FF_FF_FF);*/

        if (!this.profileStatus.getString().isBlank()) {
            //? if >=26.1 {
            graphics.text(this.font, this.profileStatus, 4, this.height / 2 + 158, 0xFF_FF_FF_FF);
            //?} else
            /*graphics.drawString(this.font, this.profileStatus, 4, this.height / 2 + 158, 0xFF_FF_FF_FF);*/
        }
    }

    /**
     * Gets the search.
     *
     * @return Search widget
     */
    EditBox search() {
        return this.search;
    }

    /**
     * Refreshes the account list from storage.
     */
    void refreshAccounts() {
        if (this.list == null || this.search == null) return;
        this.list.update(this.search.getValue());
    }

    /**
     * Updates the selected entry.
     */
    void updateSelected() {
        // Get the selected.
        AccountEntry selected = this.list != null ? this.list.getSelected() : null;
        boolean multiSelected = this.list != null && this.list.hasMultiSelection();

        if (multiSelected) {
            this.login.active = this.offlineLogin.active = this.edit.active = false;
            this.delete.active = true;
            this.copyToken.active = false;
            this.login.setTooltip(null);
            this.skin.visible = selected != null;
            this.updateProfileControls(selected);
            this.updateLogoutCookieButton();
            return;
        }

        // Nothing is selected.
        if (selected == null) {
            // Disable every button.
            this.login.active = this.offlineLogin.active = this.edit.active = this.delete.active = this.copyToken.active = false;
            this.updateLogoutCookieButton();

            // Hide tooltip, if exists.
            this.login.setTooltip(null);

            // Hide skin.
            this.skin.visible = false;
            this.updateProfileControls(null);

            // Stop here.
            return;
        }

        // Enable always-on buttons.
        this.offlineLogin.active = this.edit.active = this.delete.active = this.copyToken.active = true;

        // Enable online login button if we can log in.
        if (selected.account().canLogin()) {
            this.login.active = true;
            this.login.setTooltip(null);
        } else {
            this.login.active = false;
            this.login.setTooltip(Tooltip.create(Component.translatable("ias.accounts.login.offline")));
            this.login.setTooltipDelay(Duration.ZERO);
        }

        // Show skin.
        this.skin.visible = true;
        this.updateProfileControls(selected);

        // Update cookie logout button.
        this.updateLogoutCookieButton();
    }

    private void updateProfileControls(AccountEntry selected) {
        Account account = selected != null ? selected.account() : null;
        if (this.controlsAccount != account) {
            this.controlsAccount = account;
            this.profileStatus = Component.empty();
            this.skinPngPath = "";
            if (this.nameInput != null) {
                this.nameInput.setValue(account != null ? account.name() : "");
            }
        }
        this.updateProfileControlState();
    }

    private void updateProfileControlState() {
        boolean microsoft = this.selectedMicrosoftAccount() != null;
        if (this.skinPng != null) {
            this.skinPng.visible = this.skinModel.visible = this.applySkin.visible = this.nameInput.visible = this.applyName.visible = microsoft;
            this.skinPng.active = microsoft;
            this.skinModel.active = microsoft;
            this.applySkin.active = microsoft && !this.skinPngPath.isBlank();
            String value = this.nameInput != null ? this.nameInput.getValue().strip() : "";
            this.applyName.active = microsoft && MINECRAFT_NAME.matcher(value).matches() && !value.equals(this.controlsAccount != null ? this.controlsAccount.name() : "");
        }
    }

    private Component skinModelMessage() {
        return Component.translatable(this.slimSkin ? "ias.profile.skin.model.slim" : "ias.profile.skin.model.wide");
    }

    private MicrosoftAccount selectedMicrosoftAccount() {
        if (this.list == null) return null;
        AccountEntry selected = this.list.getSelected();
        if (selected == null) return null;
        Account account = selected.account();
        return account instanceof MicrosoftAccount microsoft ? microsoft : null;
    }

    private void browseSkinPng() {
        assert this.minecraft != null;
        String title = Component.translatable("ias.profile.skin.choose").getString();
        String startPath = this.skinPngPath;
        IAS.executor().execute(() -> {
            try {
                String path = CookieFileDialogs.pickPngFile(title, startPath);
                if (path == null) {
                    return;
                }
                this.minecraft.execute(() -> {
                    if (this.minecraft == null || this != this.currentScreen()) return;
                    this.skinPngPath = path;
                    this.profileStatus = Component.translatable("ias.profile.skin.selected", Path.of(path).getFileName().toString()).withStyle(ChatFormatting.AQUA);
                    this.updateProfileControlState();
                });
            } catch (Throwable t) {
                LOGGER.warn("IAS: Skin PNG browse failed.", t);
                this.minecraft.execute(() -> this.profileStatus = Component.translatable("ias.profile.skin.browse.failed").withStyle(ChatFormatting.RED));
            }
        });
    }

    private void applySkinPng() {
        assert this.minecraft != null;
        MicrosoftAccount account = this.selectedMicrosoftAccount();
        if (account == null || this.skinPngPath.isBlank()) return;

        Path path;
        try {
            path = Path.of(this.skinPngPath);
        } catch (InvalidPathException e) {
            this.profileStatus = Component.translatable("ias.profile.skin.file").withStyle(ChatFormatting.RED);
            return;
        }

        this.minecraft.gui.setScreen(new AccountUpdatePopupScreen(this, account, AccountUpdatePopupScreen.Operation.SKIN, "",
                path, this.slimSkin ? MSAuth.SkinVariant.SLIM : MSAuth.SkinVariant.CLASSIC));
    }

    private void confirmNameChange() {
        assert this.minecraft != null;
        MicrosoftAccount account = this.selectedMicrosoftAccount();
        if (account == null || this.nameInput == null) return;

        String value = this.nameInput.getValue().strip();
        if (!MINECRAFT_NAME.matcher(value).matches()) {
            this.profileStatus = Component.translatable("ias.profile.name.invalid").withStyle(ChatFormatting.RED);
            return;
        }
        if (value.equals(account.name())) {
            this.profileStatus = Component.translatable("ias.profile.name.same").withStyle(ChatFormatting.YELLOW);
            return;
        }

        this.minecraft.gui.setScreen(new ConfirmPopupScreen(this,
                Component.translatable("ias.profile.name.confirm.title"),
                Component.translatable("ias.profile.name.confirm", account.name(), value),
                Component.translatable("ias.profile.name.apply"),
                () -> this.minecraft.gui.setScreen(new AccountUpdatePopupScreen(this, account, AccountUpdatePopupScreen.Operation.NAME,
                        value, null, MSAuth.SkinVariant.CLASSIC))));
    }

    /**
     * Updates the cookie logout button state.
     */
    private void updateLogoutCookieButton() {
        if (this.logoutCookie == null || this.minecraft == null) return;
        this.logoutCookie.active = IASMinecraft.canRestoreLaunchAccount(this.minecraft);
    }

    /**
     * Logs out of the current cookie account and restores the launcher account.
     */
    private void logoutCookie() {
        // Bruh.
        assert this.minecraft != null;

        LoginData data = IASMinecraft.launchAccount(this.minecraft);
        if (data == null || !IASMinecraft.canRestoreLaunchAccount(this.minecraft)) return;

        LoginPopupScreen login = new LoginPopupScreen(this);
        //$ set_screen 'this.minecraft' 'login'
        this.minecraft.gui.setScreen(login);
        login.success(data, false);
    }

    private Screen currentScreen() {
        //? if >=26.2 {
        return this.minecraft.gui.screen();
        //?} else {
        /*return this.minecraft.screen;
        *///?}
    }

    @Override
    //? if >=1.21.10 {
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int key = event.key();
        boolean shift = event.hasShiftDown();
        boolean control = event.hasControlDown();
        boolean select = event.isSelection();
    //?} else {
    /*public boolean keyPressed(int key, int scan, int mods) {
        boolean shift = Screen.hasShiftDown();
        boolean control = Screen.hasControlDown();
        boolean select = net.minecraft.client.gui.navigation.CommonInputs.selected(key);
    *///?}
        // Bruh.
        assert this.minecraft != null;

        // Shift+Down or Page Down to swap down.
        if ((key == GLFW.GLFW_KEY_DOWN && shift) || key == GLFW.GLFW_KEY_PAGE_DOWN) {
            this.list.swapDown(this.list.getSelected());
            return true;
        }

        // Shift+Up or Page Up to swap up.
        if ((key == GLFW.GLFW_KEY_UP && shift) || key == GLFW.GLFW_KEY_PAGE_UP) {
            this.list.swapUp(this.list.getSelected());
            return true;
        }

        // Ctrl+C to copy name. (Ctrl+Shift+C to copy UUID) {
        if (key == GLFW.GLFW_KEY_C && control) {
            AccountEntry selected = this.list.getSelected();
            if (selected != null) {
                Account account = selected.account();
                this.minecraft.keyboardHandler.setClipboard(shift ? account.uuid().toString() : account.name());
                return true;
            }
        }

        // Skip if handled by super.
        //? if >=1.21.10 {
        if (super.keyPressed(event)) {
        //?} else
        /*if (super.keyPressed(key, scan, mods)) {*/
            return true;
        }

        // Enter or Numpad Enter to log in.
        if (select) {
            this.list.login(!shift, IASConfig.closeOnLogin ? () -> {
                //$set_screen 'this.minecraft' 'this.parent'
                this.minecraft.gui.setScreen(this.parent);
            } : null);
            return true;
        }

        // Delete or Numpad Minus to delete.
        if (key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_KP_SUBTRACT) {
            this.list.delete(!shift);
            return true;
        }

        // CTRL+N or Numpad Plus to add.
        if ((key == GLFW.GLFW_KEY_N && control) || key == GLFW.GLFW_KEY_KP_ADD) {
            this.list.add();
            return true;
        }

        // CTRL+R or Numpad Asterisk to edit.
        if ((key == GLFW.GLFW_KEY_R && control) || key == GLFW.GLFW_KEY_KP_MULTIPLY) {
            this.list.edit();
            return true;
        }

        // Not handled.
        return false;
    }


    /**
     * Gets the parent screen.
     *
     * @return Parent screen
     */
    Screen parent() {
        return this.parent;
    }
    

    @Override
    public String toString() {
        return "AccountScreen{" +
                "list=" + this.list +
                '}';
    }
}
