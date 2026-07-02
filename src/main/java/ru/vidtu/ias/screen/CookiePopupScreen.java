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
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.components.Tooltip;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else
/*import net.minecraft.client.gui.GuiGraphics;*/
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2fStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.vidtu.ias.IAS;
import ru.vidtu.ias.account.Account;
import ru.vidtu.ias.account.MicrosoftAccount;
import ru.vidtu.ias.auth.cookie.CookieParser;
import ru.vidtu.ias.auth.handlers.CreateHandler;
import ru.vidtu.ias.auth.microsoft.MSAuth;
import ru.vidtu.ias.auth.microsoft.MSAccountFactory;
import ru.vidtu.ias.config.IASConfig;
import ru.vidtu.ias.crypt.Crypt;
import ru.vidtu.ias.crypt.PasswordCrypt;
import ru.vidtu.ias.platform.IStonecutter;
import ru.vidtu.ias.utils.exceptions.FriendlyException;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Cookie alt import popup screen.
 *
 * @author VidTu
 */
final class CookiePopupScreen extends Screen implements CreateHandler {
    /**
     * Logger for this class.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger("IAS/CookiePopupScreen");

    /**
     * Input row width in pixels.
     */
    private static final int INPUT_WIDTH = 198;

    /**
     * Path field width (browse button fills the remaining row width).
     */
    private static final int PATH_WIDTH = 178;

    /**
     * Paste area height in pixels.
     */
    private static final int PASTE_HEIGHT = 64;

    /**
     * Panel half-height in file-path mode.
     */
    private static final int PANEL_HALF_PATH = 92;

    /**
     * Panel half-height in paste mode.
     */
    private static final int PANEL_HALF_PASTE = 100;

    /**
     * Parent screen.
     */
    private final Screen parent;

    /**
     * Account handler.
     */
    private final Consumer<Account> handler;

    /**
     * Synchronization lock.
     */
    private final Object lock = new Object();

    /**
     * Crypt method.
     */
    private Crypt crypt;

    /**
     * Whether paste mode is active ({@code false} = file path).
     */
    private boolean pasteMode;

    /**
     * Whether import is in progress.
     */
    private boolean importing;

    /**
     * Whether this screen was closed.
     */
    private volatile boolean closed;

    /**
     * Cookie file path input.
     */
    private PopupBox pathInput;

    /**
     * Pasted cookie file contents.
     */
    private MultiLineEditBox pasteInput;

    /**
     * Saved path while toggling input modes.
     */
    private String savedPath = "";

    /**
     * Saved paste text while toggling input modes.
     */
    private String savedPaste = "";

    /**
     * Password box for password-based encryption.
     */
    private PopupBox password;

    /**
     * Current stage.
     */
    @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
    private Component stage = Component.empty();

    /**
     * Current stage label.
     */
    @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
    private MultiLineLabel label;

    /**
     * Crypt password tip.
     */
    private MultiLineLabel cryptPasswordTip;

    /**
     * Non-NAN, if some sort of error is present.
     */
    private float error = Float.NaN;

    /**
     * Error note.
     */
    private MultiLineLabel errorNote;

    /**
     * Paste box top-left X for border rendering.
     */
    private int pasteBoxX;

    /**
     * Paste box top-left Y for border rendering.
     */
    private int pasteBoxY;

    /**
     * Creates a new cookie import screen.
     *
     * @param parent  Parent screen
     * @param handler Account handler
     * @param crypt   Crypt method, {@code null} to use password
     */
    CookiePopupScreen(Screen parent, Consumer<Account> handler, Crypt crypt) {
        super(Component.translatable("ias.cookie"));
        this.parent = parent;
        this.handler = handler;
        this.crypt = crypt;
    }

    @Override
    public boolean cancelled() {
        return this.closed;
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

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int backY = centerY + 72;

        this.addRenderableWidget(new PopupButton(centerX - 75, backY, 150, 20,
                CommonComponents.GUI_BACK, btn -> this.onClose(), Supplier::get));

        if (this.crypt == null) {
            this.password = new PopupBox(this.font, centerX - 100, centerY - 10 + 5, 178, 20, this.password,
                    Component.translatable("ias.password"), this::confirmPassword, true);
            this.password.setHint(Component.translatable("ias.password.hint").withStyle(ChatFormatting.DARK_GRAY));
            //? if >=1.21.10 {
            this.password.addFormatter((s, i) -> IASConfig.passwordEchoing ? FormattedCharSequence.forward("*".repeat(s.length()), Style.EMPTY) : FormattedCharSequence.EMPTY);
            //?} else
            /*this.password.setFormatter((s, i) -> IASConfig.passwordEchoing ? FormattedCharSequence.forward("*".repeat(s.length()), Style.EMPTY) : FormattedCharSequence.EMPTY);*/
            this.password.setMaxLength(32);
            this.addRenderableWidget(this.password);

            Button enterPassword = new PopupButton(centerX - 100 + 180, centerY - 10 + 5, 20, 20,
                    Component.literal(">>"), btn -> this.confirmPassword(), Supplier::get);
            enterPassword.active = !this.password.getValue().isBlank();
            this.addRenderableWidget(enterPassword);
            this.password.setResponder(value -> enterPassword.active = !value.isBlank());
            this.cryptPasswordTip = MultiLineLabel.create(this.font, Component.translatable("ias.password.tip"), 320);
            return;
        }

        if (this.importing) {
            return;
        }

        this.pathInput = null;
        this.pasteInput = null;

        int toggleWidth = 74;
        int toggleGap = 2;
        int toggleY = centerY - 38;
        int toggleLeft = centerX - toggleWidth - toggleGap / 2;
        int inputX = centerX - INPUT_WIDTH / 2;
        int inputY = centerY - 12;

        PopupButton pathBtn = new PopupButton(toggleLeft, toggleY, toggleWidth, 20,
                Component.translatable("ias.cookie.path"), btn -> this.setPasteMode(false), Supplier::get);
        pathBtn.color(this.pasteMode ? 0.75F : 0.5F, this.pasteMode ? 0.75F : 1.0F, this.pasteMode ? 0.75F : 0.5F, true);
        this.addRenderableWidget(pathBtn);

        PopupButton pasteBtn = new PopupButton(toggleLeft + toggleWidth + toggleGap, toggleY, toggleWidth, 20,
                Component.translatable("ias.cookie.paste"), btn -> this.setPasteMode(true), Supplier::get);
        pasteBtn.color(this.pasteMode ? 0.5F : 0.75F, this.pasteMode ? 1.0F : 0.75F, this.pasteMode ? 0.5F : 0.75F, true);
        this.addRenderableWidget(pasteBtn);

        if (this.pasteMode) {
            this.pasteBoxX = inputX;
            this.pasteBoxY = inputY;
            this.pasteInput = PopupMultiLineBox.create(this.font, inputX, inputY, INPUT_WIDTH, PASTE_HEIGHT, this.pasteInput,
                    Component.translatable("ias.cookie.paste.hint"),
                    Component.translatable("ias.cookie.paste.placeholder").withStyle(ChatFormatting.DARK_GRAY));
            this.pasteInput.setCharacterLimit(131072);
            if (!this.savedPaste.isBlank()) {
                this.pasteInput.setValue(this.savedPaste);
            }
            this.addRenderableWidget(this.pasteInput);
        } else {
            this.pathInput = new PopupBox(this.font, inputX, inputY, PATH_WIDTH, 20, this.pathInput,
                    Component.translatable("ias.cookie.path.hint"), this::importCookies, false);
            this.pathInput.setHint(Component.literal("C:\\alts\\account.txt").withStyle(ChatFormatting.DARK_GRAY));
            this.pathInput.setMaxLength(512);
            if (!this.savedPath.isBlank()) {
                this.pathInput.setValue(this.savedPath);
            }
            this.addRenderableWidget(this.pathInput);

            PopupButton browseBtn = new PopupButton(inputX + PATH_WIDTH, inputY, INPUT_WIDTH - PATH_WIDTH, 20,
                    Component.literal("..."), btn -> this.browseCookieFile(), Supplier::get);
            browseBtn.setTooltip(Tooltip.create(Component.translatable("ias.cookie.browse")));
            browseBtn.setTooltipDelay(Duration.ofMillis(250L));
            this.addRenderableWidget(browseBtn);
        }

        int importY = this.pasteMode ? centerY + 50 : centerY + 40;
        PopupButton importBtn = new PopupButton(centerX - 75, importY, 150, 20,
                Component.translatable("ias.cookie.import"), btn -> this.importCookies(), Supplier::get);
        importBtn.color(0.5F, 1.0F, 0.75F, true);
        this.addRenderableWidget(importBtn);
    }

    private void setPasteMode(boolean pasteMode) {
        if (this.pathInput != null) {
            this.savedPath = this.pathInput.getValue();
        }
        if (this.pasteInput != null) {
            this.savedPaste = this.pasteInput.getValue();
        }
        this.pasteMode = pasteMode;
        this.error = Float.NaN;
        this.errorNote = null;
        //? if >=1.21.11 {
        this.init(this.width, this.height);
        //?} else
        /*this.init(this.minecraft, this.width, this.height);*/
    }

    private void confirmPassword() {
        if (this.password == null || this.crypt != null) return;
        String value = this.password.getValue();
        if (value.isBlank()) return;
        this.crypt = new PasswordCrypt(value);
        this.password = null;
        this.cryptPasswordTip = null;
        //? if >=1.21.11 {
        this.init(this.width, this.height);
        //?} else
        /*this.init(this.minecraft, this.width, this.height);*/
    }

    private void importCookies() {
        assert this.minecraft != null;
        if (this.crypt == null || this.importing) return;

        String raw;
        if (this.pasteMode) {
            raw = this.resolvePasteSource();
            if (raw.isBlank()) {
                return;
            }
        } else {
            if (this.pathInput == null) return;
            raw = this.pathInput.getValue().strip();
            if (raw.isBlank()) return;
        }

        this.importing = true;
        this.error = Float.NaN;
        this.errorNote = null;
        this.stage(MicrosoftAccount.INITIALIZING);

        final String source = raw;
        final boolean fromPath = !this.pasteMode;

        //? if >=1.21.11 {
        this.init(this.width, this.height);
        //?} else
        /*this.init(this.minecraft, this.width, this.height);*/

        IAS.executor().execute(() -> {
            try {
                if (this.closed) {
                    return;
                }

                this.stage(MicrosoftAccount.COOKIES_TO_MSA_MSR);

                CookieParser.ParsedCookies cookies = fromPath
                        ? CookieParser.fromPath(source)
                        : CookieParser.fromText(source);

                if (this.closed) {
                    return;
                }

                if (!cookies.refreshToken().isBlank()) {
                    MSAuth.minecraftRefreshToMsaMsr(cookies.refreshToken())
                            .thenComposeAsync(ms -> MSAccountFactory.createFromMinecraftRefresh(this.crypt, ms, this), IAS.executor())
                            .exceptionallyAsync(t -> {
                                this.error(t);
                                return null;
                            }, IAS.executor());
                    return;
                }

                MSAccountFactory.createFromCookies(this.crypt, cookies.toSisuCookieHeader(), this).exceptionallyAsync(t -> {
                    this.error(t);
                    return null;
                }, IAS.executor());
            } catch (Throwable t) {
                this.error(t);
            }
        });
    }

    /**
     * Resolves pasted cookie text, preferring the clipboard when the text box lost tab characters.
     */
    private String resolvePasteSource() {
        assert this.minecraft != null;
        String box = this.pasteInput != null ? this.pasteInput.getValue() : "";
        String clip = this.minecraft.keyboardHandler.getClipboard();
        if (!box.isBlank() && box.contains("\t")) {
            return box;
        }
        if (!clip.isBlank() && clip.contains("\t")) {
            return clip;
        }
        if (!box.isBlank()) {
            return box;
        }
        return clip != null ? clip : "";
    }

    private void browseCookieFile() {
        assert this.minecraft != null;
        String initial = this.pathInput != null ? this.pathInput.getValue().strip() : this.savedPath.strip();
        if (initial.length() >= 2 && initial.charAt(0) == '"' && initial.charAt(initial.length() - 1) == '"') {
            initial = initial.substring(1, initial.length() - 1).strip();
        }
        final String startPath = initial;
        final String dialogTitle = Component.translatable("ias.cookie.browse").getString();
        IAS.executor().execute(() -> {
            try {
                String path = CookieFileDialogs.pickFile(dialogTitle, startPath);
                if (path == null || this.closed) {
                    return;
                }

                this.minecraft.execute(() -> {
                    if (this.closed || this != this.currentScreen()) {
                        return;
                    }
                    this.savedPath = path;
                    if (this.pathInput != null) {
                        this.pathInput.setValue(path);
                    }
                });
            } catch (Throwable t) {
                LOGGER.warn("IAS: Cookie file browse failed.", t);
                this.minecraft.execute(() -> this.showBrowseError());
            }
        });
    }

    private void showBrowseError() {
        if (this.closed) {
            return;
        }
        synchronized (this.lock) {
            this.stage = Component.translatable("ias.cookie.browse.failed").withStyle(ChatFormatting.RED);
            this.label = null;
            this.error = 0.0F;
        }
    }

    @Override
    public void onClose() {
        assert this.minecraft != null;
        this.closed = true;
        this.importing = false;
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

        if (this.crypt == null && this.password != null && this.cryptPasswordTip != null) {
            //? if >=26.1 {
            graphics.centeredText(this.font, this.password.getMessage(), this.width / 2, this.height / 2 - 10 - 5, 0xFF_FF_FF_FF);
            //?} else
            /*graphics.drawCenteredString(this.font, this.password.getMessage(), this.width / 2, this.height / 2 - 10 - 5, 0xFF_FF_FF_FF);*/
            pose.pushMatrix();
            pose.scale(0.5F, 0.5F);
            IStonecutter.renderMultilineLabelCentered(this.cryptPasswordTip, graphics, this.width, this.height + 40);
            pose.popMatrix();
        } else if (this.importing) {
            synchronized (this.lock) {
                if (this.label == null) {
                    Component component = Objects.requireNonNullElse(this.stage, Component.empty());
                    this.label = MultiLineLabel.create(this.font, component, 240);
                    this.minecraft.getNarrator().saySystemQueued(component);
                }
                IStonecutter.renderMultilineLabelCentered(this.label, graphics, this.width / 2, (this.height - this.label.getLineCount() * 9) / 2 - 4);
            }

            if (Float.isFinite(this.error)) {
                if (this.errorNote == null) {
                    this.errorNote = MultiLineLabel.create(this.font, Component.translatable("ias.error.note").withStyle(ChatFormatting.AQUA), 245);
                }
                float opacityFloat;
                int opacityMask;
                if (this.error < 1.0F) {
                    this.error = Math.min(this.error + delta * 0.1F, 1.0F);
                    opacityFloat = (this.error * this.error * this.error * this.error);
                    int opacity = Math.max(9, (int) (opacityFloat * 255.0F));
                    opacityMask = opacity << 24;
                } else {
                    opacityFloat = 1.0F;
                    opacityMask = -16777216;
                }
                int w = this.errorNote.getWidth() / 4 + 2;
                int h = (this.errorNote.getLineCount() * 9) / 2 + 1;
                int cx = this.width / 2;
                int sy = this.height / 2 + 87;
                graphics.fill(cx - w, sy, cx + w, sy + h, 0x101010 | opacityMask);
                pose.pushMatrix();
                pose.scale(0.5F, 0.5F);
                //? if >= 1.21.11 {
                var renderer = graphics.textRenderer();
                renderer.defaultParameters(renderer.defaultParameters().withOpacity(opacityFloat));
                this.errorNote.visitLines(net.minecraft.client.gui.TextAlignment.CENTER, this.width, this.height + 174, 9, renderer);
                //?} elif >= 1.21.10 {
                /*this.errorNote.render(graphics, MultiLineLabel.Align.CENTER, this.width, this.height + 174, 9, false, 0xFF_FF_FF | opacityMask);
                *///?} else
                /*this.errorNote.renderCentered(graphics, this.width, this.height + 174, 9, 0xFF_FF_FF | opacityMask);*/
                pose.popMatrix();
            }
        } else if (this.pathInput != null || this.pasteInput != null) {
            int centerY = this.height / 2;
            if (Float.isFinite(this.error)) {
                synchronized (this.lock) {
                    if (this.label == null) {
                        Component component = Objects.requireNonNullElse(this.stage, Component.empty());
                        this.label = MultiLineLabel.create(this.font, component, 220);
                    }
                    IStonecutter.renderMultilineLabelCentered(this.label, graphics, this.width / 2, centerY - 54);
                }
            } else {
                Component inputTitle = this.pasteMode
                        ? Component.translatable("ias.cookie.paste.hint")
                        : Component.translatable("ias.cookie.path.hint");
                //? if >=26.1 {
                graphics.centeredText(this.font, inputTitle, this.width / 2, centerY - 54, 0xFF_FF_FF_FF);
                //?} else
                /*graphics.drawCenteredString(this.font, inputTitle, this.width / 2, centerY - 54, 0xFF_FF_FF_FF);*/
            }
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
        int panelHalfHeight = this.pasteMode && this.crypt != null && !this.importing ? PANEL_HALF_PASTE : PANEL_HALF_PATH;
        graphics.fill(centerX - 125, centerY - panelHalfHeight, centerX + 125, centerY + panelHalfHeight, 0xF8_20_20_30);
        graphics.fill(centerX - 124, centerY - panelHalfHeight - 1, centerX + 124, centerY - panelHalfHeight, 0xF8_20_20_30);
        graphics.fill(centerX - 124, centerY + panelHalfHeight, centerX + 124, centerY + panelHalfHeight + 1, 0xF8_20_20_30);

        if (this.pasteMode && this.pasteInput != null && !this.importing) {
            int x = this.pasteBoxX;
            int y = this.pasteBoxY;
            int width = INPUT_WIDTH;
            int height = PASTE_HEIGHT;
            graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF_00_00_00);
            graphics.fill(x + 1, y, x + width - 1, y + 1, 0xFF_FF_FF_FF);
            graphics.fill(x + 1, y + height - 1, x + width - 1, y + height, 0xFF_FF_FF_FF);
            graphics.fill(x, y + 1, x + 1, y + height - 1, 0xFF_FF_FF_FF);
            graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, 0xFF_FF_FF_FF);
        }
    }

    @Override
    public void stage(String stage, Object... args) {
        assert this.minecraft != null;
        Component component = Component.translatable(stage, args).withStyle(ChatFormatting.YELLOW);
        this.minecraft.execute(() -> {
            if (this.closed || this != this.currentScreen()) {
                return;
            }
            synchronized (this.lock) {
                this.stage = component;
                this.label = null;
            }
        });
    }

    @Override
    public void success(MicrosoftAccount account) {
        assert this.minecraft != null;
        this.minecraft.execute(() -> {
            if (this.closed || this != this.currentScreen()) {
                return;
            }
            this.importing = false;
            this.stage(MicrosoftAccount.FINALIZING);
            this.handler.accept(account);
        });
    }

    @Override
    public void error(Throwable error) {
        LOGGER.error("IAS: Cookie import error.", error);
        assert this.minecraft != null;

        FriendlyException probable = FriendlyException.friendlyInChain(error);
        String key = probable != null ? probable.key() : "ias.error";
        Component component = Component.translatable(key).withStyle(ChatFormatting.RED);
        this.minecraft.execute(() -> {
            if (this.closed) {
                return;
            }
            synchronized (this.lock) {
                this.stage = component;
                this.label = null;
                this.error = 0.0F;
            }
            this.importing = false;
            //? if >=1.21.11 {
            this.init(this.width, this.height);
            //?} else
            /*this.init(this.minecraft, this.width, this.height);*/
        });
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
        return "CookiePopupScreen{}";
    }
}
