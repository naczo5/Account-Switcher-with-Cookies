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
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2fStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.vidtu.ias.IAS;
import ru.vidtu.ias.account.MicrosoftAccount;
import ru.vidtu.ias.auth.LoginData;
import ru.vidtu.ias.auth.handlers.LoginHandler;
import ru.vidtu.ias.auth.microsoft.MSAuth;
import ru.vidtu.ias.auth.microsoft.fields.MCProfile;
import ru.vidtu.ias.config.IASConfig;
import ru.vidtu.ias.platform.IStonecutter;
import ru.vidtu.ias.utils.exceptions.FriendlyException;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Applies Minecraft profile updates that need a valid stored account token.
 *
 * @author Articuling
 */
final class AccountUpdatePopupScreen extends Screen implements LoginHandler {
    /**
     * Logger for this class.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger("IAS/AccountUpdatePopupScreen");

    enum Operation {
        NAME,
        SKIN
    }

    /**
     * Parent screen.
     */
    private final Screen parent;

    /**
     * Account being updated.
     */
    private final MicrosoftAccount account;

    /**
     * Operation to apply.
     */
    private final Operation operation;

    /**
     * Target IGN for name update.
     */
    private final String targetName;

    /**
     * Skin PNG file for skin update.
     */
    private final Path skinPng;

    /**
     * Skin model variant for upload.
     */
    private final MSAuth.SkinVariant skinVariant;

    /**
     * Synchronization lock.
     */
    private final Object lock = new Object();

    /**
     * Current stage.
     */
    @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
    private Component stage;

    /**
     * Current stage label.
     */
    @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
    private MultiLineLabel label;

    /**
     * Password box.
     */
    private PopupBox password;

    /**
     * Password future.
     */
    private CompletableFuture<String> passFuture;

    /**
     * Crypt password tip.
     */
    private MultiLineLabel cryptPasswordTip;

    /**
     * Non-NAN, if some sort of error is present.
     */
    private float error = Float.NaN;

    /**
     * Whether the update flow has started.
     */
    private boolean started;

    /**
     * Whether the update flow finished.
     */
    private boolean finished;

    AccountUpdatePopupScreen(Screen parent, MicrosoftAccount account, Operation operation, String targetName,
            Path skinPng, MSAuth.SkinVariant skinVariant) {
        super(Component.translatable(operation == Operation.NAME ? "ias.profile.name.title" : "ias.profile.skin.title"));
        this.parent = parent;
        this.account = account;
        this.operation = operation;
        this.targetName = targetName;
        this.skinPng = skinPng;
        this.skinVariant = skinVariant;
        this.stage = Component.translatable(operation == Operation.NAME ? "ias.profile.name.preparing" : "ias.profile.skin.preparing").withStyle(ChatFormatting.YELLOW);
    }

    @Override
    public boolean cancelled() {
        assert this.minecraft != null;
        return this != this.currentScreen();
    }

    @Override
    protected void init() {
        assert this.minecraft != null;

        synchronized (this.lock) {
            this.label = null;
        }

        if (this.parent != null) {
            //? if >=1.21.11 {
            this.parent.init(this.width, this.height);
            //?} else
            /*this.parent.init(this.minecraft, this.width, this.height);*/
        }

        this.addRenderableWidget(new PopupButton(this.width / 2 - 75, this.height / 2 + 74 - 22, 150, 20,
                this.finished ? CommonComponents.GUI_BACK : CommonComponents.GUI_CANCEL, btn -> this.onClose(), Supplier::get));

        if (this.passFuture != null) {
            this.password = new PopupBox(this.font, this.width / 2 - 100, this.height / 2 - 10 + 5, 178, 20, this.password,
                    Component.translatable("ias.password"), () -> {
                if (this.passFuture == null || this.password == null) return;
                String value = this.password.getValue();
                if (value.isBlank()) return;
                this.passFuture.complete(value);
            }, true);
            this.password.setHint(Component.translatable("ias.password.hint").withStyle(ChatFormatting.DARK_GRAY));
            //? if >=1.21.10 {
            this.password.addFormatter((s, i) -> IASConfig.passwordEchoing ? FormattedCharSequence.forward("*".repeat(s.length()), Style.EMPTY) : FormattedCharSequence.EMPTY);
            //?} else
            /*this.password.setFormatter((s, i) -> IASConfig.passwordEchoing ? FormattedCharSequence.forward("*".repeat(s.length()), Style.EMPTY) : FormattedCharSequence.EMPTY);*/
            this.password.setMaxLength(32);
            this.addRenderableWidget(this.password);

            PopupButton button = new PopupButton(this.width / 2 - 100 + 180, this.height / 2 - 10 + 5, 20, 20,
                    Component.literal(">>"), btn -> {
                if (this.passFuture == null || this.password == null) return;
                String value = this.password.getValue();
                if (value.isBlank()) return;
                this.passFuture.complete(value);
            }, Supplier::get);
            button.active = !this.password.getValue().isBlank();
            this.addRenderableWidget(button);
            this.password.setResponder(value -> button.active = !value.isBlank());
            this.cryptPasswordTip = MultiLineLabel.create(this.font, Component.translatable("ias.password.tip").withColor(0xFF_FF_00), 320);
        } else if (!this.started) {
            this.started = true;
            IAS.executor().execute(() -> this.account.login(this, null));
        }
    }

    @Override
    public void onClose() {
        assert this.minecraft != null;
        if (this.passFuture != null) {
            this.passFuture.complete(null);
        }
        //$set_screen 'this.minecraft' 'this.parent'
        this.minecraft.gui.setScreen(this.parent);
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
        pose.scale(2.0F, 2.0F);
        //? if >=26.1 {
        graphics.centeredText(this.font, this.title, this.width / 4, this.height / 4 - 74 / 2, 0xFF_FF_FF_FF);
        //?} else
        /*graphics.drawCenteredString(this.font, this.title, this.width / 4, this.height / 4 - 74 / 2, 0xFF_FF_FF_FF);*/
        pose.popMatrix();

        if (this.passFuture != null && this.password != null && this.cryptPasswordTip != null) {
            //? if >=26.1 {
            graphics.centeredText(this.font, this.password.getMessage(), this.width / 2, this.height / 2 - 10 - 5, 0xFF_FF_FF_FF);
            //?} else
            /*graphics.drawCenteredString(this.font, this.password.getMessage(), this.width / 2, this.height / 2 - 10 - 5, 0xFF_FF_FF_FF);*/
            pose.pushMatrix();
            pose.scale(0.5F, 0.5F);
            IStonecutter.renderMultilineLabelCentered(this.cryptPasswordTip, graphics, this.width, this.height + 40);
            pose.popMatrix();
            return;
        }

        synchronized (this.lock) {
            if (this.label == null) {
                Component component = Objects.requireNonNullElse(this.stage, Component.empty());
                this.label = MultiLineLabel.create(this.font, component, 240);
                this.minecraft.getNarrator().saySystemQueued(component);
            }
            IStonecutter.renderMultilineLabelCentered(this.label, graphics, this.width / 2, (this.height - this.label.getLineCount() * 9) / 2 - 4);
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
        graphics.fill(centerX - 125, centerY - 75, centerX + 125, centerY + 75, 0xF8_20_20_30);
        graphics.fill(centerX - 124, centerY - 76, centerX + 124, centerY - 75, 0xF8_20_20_30);
        graphics.fill(centerX - 124, centerY + 75, centerX + 124, centerY + 76, 0xF8_20_20_30);
    }

    @Override
    public void stage(String stage, Object... args) {
        this.setStage(Component.translatable(stage, args).withStyle(ChatFormatting.YELLOW));
    }

    @Override
    public CompletableFuture<String> password() {
        assert this.minecraft != null;
        if (this.passFuture != null) return this.passFuture;

        this.passFuture = new CompletableFuture<>();
        this.passFuture.thenAcceptAsync(password -> {
            this.passFuture = null;
            this.password = null;
            this.cryptPasswordTip = null;
            //? if >=1.21.11 {
            this.init(this.width, this.height);
            //?} else
            /*this.init(this.minecraft, this.width, this.height);*/
        }, this.minecraft);

        this.minecraft.execute(() -> {
            //? if >=1.21.11 {
            this.init(this.width, this.height);
            //?} else
            /*this.init(this.minecraft, this.width, this.height);*/
        });
        return this.passFuture;
    }

    @Override
    public void success(LoginData data, boolean changed) {
        if (data == null) {
            return;
        }
        if (changed) {
            this.saveStorage();
        }

        CompletableFuture<MCProfile> update = switch (this.operation) {
            case NAME -> {
                this.setStage(Component.translatable("ias.profile.name.applying", this.targetName).withStyle(ChatFormatting.YELLOW));
                yield MSAuth.changeName(data.token(), this.targetName);
            }
            case SKIN -> {
                this.setStage(Component.translatable("ias.profile.skin.applying").withStyle(ChatFormatting.YELLOW));
                yield MSAuth.uploadSkin(data.token(), this.skinPng, this.skinVariant);
            }
        };

        update.thenAcceptAsync(profile -> {
            AccountList.clearSkin(profile.uuid());
            AccountList.clearNameChange(this.account.uuid());
            AccountList.clearNameChange(profile.uuid());
            this.account.updateProfile(profile.uuid(), profile.name());
            this.saveStorage();
            this.finish(Component.translatable(this.operation == Operation.NAME ? "ias.profile.name.done" : "ias.profile.skin.done").withStyle(ChatFormatting.GREEN));
        }, IAS.executor()).exceptionallyAsync(t -> {
            this.error(t);
            return null;
        }, IAS.executor());
    }

    @Override
    public void error(Throwable error) {
        LOGGER.error("IAS: Account profile update error.", error);
        FriendlyException probable = FriendlyException.friendlyInChain(error);
        String key = probable != null ? probable.key() : "ias.profile.failed";
        this.setStage(Component.translatable(key).withStyle(ChatFormatting.RED));
        this.finished = true;
        this.error = 0.0F;
        assert this.minecraft != null;
        this.minecraft.execute(() -> {
            if (this != this.currentScreen()) return;
            //? if >=1.21.11 {
            this.init(this.width, this.height);
            //?} else
            /*this.init(this.minecraft, this.width, this.height);*/
        });
    }

    private void finish(Component component) {
        assert this.minecraft != null;
        this.finished = true;
        this.setStage(component);
        this.minecraft.execute(() -> {
            if (this.parent instanceof AccountScreen accountScreen) {
                accountScreen.refreshAccounts();
            }
            if (this != this.currentScreen()) return;
            //? if >=1.21.11 {
            this.init(this.width, this.height);
            //?} else
            /*this.init(this.minecraft, this.width, this.height);*/
        });
    }

    private void setStage(Component component) {
        assert this.minecraft != null;
        this.minecraft.execute(() -> {
            if (this != this.currentScreen()) return;
            synchronized (this.lock) {
                this.stage = component;
                this.label = null;
            }
        });
    }

    private void saveStorage() {
        try {
            IAS.disclaimersStorage();
            IAS.saveStorage();
        } catch (Throwable t) {
            LOGGER.error("IAS: Unable to save storage.", t);
        }
    }

    private Screen currentScreen() {
        //? if >=26.2 {
        return this.minecraft.gui.screen();
        //?} else {
        /*return this.minecraft.screen;
        *///?}
    }

    @Override
    public String toString() {
        return "AccountUpdatePopupScreen{" +
                "operation=" + this.operation +
                ", account=" + this.account +
                '}';
    }
}
