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
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.vidtu.ias.IAS;
import ru.vidtu.ias.account.Account;
import ru.vidtu.ias.account.MicrosoftAccount;
import ru.vidtu.ias.auth.handlers.CreateHandler;
import ru.vidtu.ias.auth.microsoft.MSAccountFactory;
import ru.vidtu.ias.config.IASStorage;
import ru.vidtu.ias.crypt.Crypt;
import ru.vidtu.ias.platform.IStonecutter;
import ru.vidtu.ias.utils.exceptions.FriendlyException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minecraft access token import popup screen.
 *
 * @author VidTu
 */
final class TokenPopupScreen extends Screen {
    /**
     * Logger for this class.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger("IAS/TokenPopupScreen");

    /**
     * JSON field matcher for copied token exports.
     */
    private static final Pattern MC_TOKEN_JSON = Pattern.compile("\"mcToken\"\\s*:\\s*\"([^\"]+)\"");

    /**
     * Input row width in pixels.
     */
    private static final int INPUT_WIDTH = 198;

    /**
     * Path field width.
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
     * Crypt method.
     */
    private final Crypt crypt;

    /**
     * Synchronization lock.
     */
    private final Object lock = new Object();

    /**
     * Whether paste mode is active.
     */
    private boolean pasteMode = true;

    /**
     * Whether import is in progress.
     */
    private boolean importing;

    /**
     * Whether this screen was closed.
     */
    private volatile boolean closed;

    /**
     * Token file path input.
     */
    private PopupBox pathInput;

    /**
     * Pasted token contents.
     */
    private MultiLineEditBox pasteInput;

    /**
     * Saved path while toggling input modes.
     */
    private String savedPath = "";

    /**
     * Token files selected by the native multi-file picker.
     */
    private List<String> selectedTokenFiles = List.of();

    /**
     * Saved paste text while toggling input modes.
     */
    private String savedPaste = "";

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
     * Non-NAN, if some sort of error is present.
     */
    private float error = Float.NaN;

    /**
     * Paste box top-left X for border rendering.
     */
    private int pasteBoxX;

    /**
     * Paste box top-left Y for border rendering.
     */
    private int pasteBoxY;

    /**
     * Creates a new token import screen.
     *
     * @param parent  Parent screen
     * @param handler Account handler
     * @param crypt   Crypt method
     */
    TokenPopupScreen(Screen parent, Consumer<Account> handler, Crypt crypt) {
        super(Component.translatable("ias.token"));
        this.parent = parent;
        this.handler = handler;
        this.crypt = crypt;
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
                Component.translatable("ias.token.path"), btn -> this.setPasteMode(false), Supplier::get);
        pathBtn.color(this.pasteMode ? 0.75F : 0.5F, this.pasteMode ? 0.75F : 1.0F, this.pasteMode ? 0.75F : 1.0F, true);
        this.addRenderableWidget(pathBtn);

        PopupButton pasteBtn = new PopupButton(toggleLeft + toggleWidth + toggleGap, toggleY, toggleWidth, 20,
                Component.translatable("ias.token.paste"), btn -> this.setPasteMode(true), Supplier::get);
        pasteBtn.color(this.pasteMode ? 0.5F : 0.75F, this.pasteMode ? 1.0F : 0.75F, this.pasteMode ? 1.0F : 0.75F, true);
        this.addRenderableWidget(pasteBtn);

        if (this.pasteMode) {
            this.pasteBoxX = inputX;
            this.pasteBoxY = inputY;
            this.pasteInput = PopupMultiLineBox.create(this.font, inputX, inputY, INPUT_WIDTH, PASTE_HEIGHT, this.pasteInput,
                    Component.translatable("ias.token.paste.hint"),
                    Component.translatable("ias.token.paste.placeholder").withStyle(ChatFormatting.DARK_GRAY));
            this.pasteInput.setCharacterLimit(131072);
            if (!this.savedPaste.isBlank()) {
                this.pasteInput.setValue(this.savedPaste);
            }
            this.addRenderableWidget(this.pasteInput);
        } else {
            this.pathInput = new PopupBox(this.font, inputX, inputY, PATH_WIDTH, 20, this.pathInput,
                    Component.translatable("ias.token.path.hint"), this::importTokens, false);
            this.pathInput.setHint(Component.literal("C:\\alts\\token.txt").withStyle(ChatFormatting.DARK_GRAY));
            this.pathInput.setMaxLength(512);
            if (!this.savedPath.isBlank()) {
                this.pathInput.setValue(this.savedPath);
            }
            this.pathInput.setResponder(value -> {
                if (!Objects.equals(value, this.savedPath)) {
                    this.selectedTokenFiles = List.of();
                }
            });
            this.addRenderableWidget(this.pathInput);

            PopupButton browseBtn = new PopupButton(inputX + PATH_WIDTH, inputY, INPUT_WIDTH - PATH_WIDTH, 20,
                    Component.literal("..."), btn -> this.browseTokenFiles(), Supplier::get);
            browseBtn.setTooltip(Tooltip.create(Component.translatable("ias.token.browse")));
            browseBtn.setTooltipDelay(Duration.ofMillis(250L));
            this.addRenderableWidget(browseBtn);
        }

        int importY = this.pasteMode ? centerY + 50 : centerY + 40;
        PopupButton importButton = new PopupButton(centerX - 75, importY, 150, 20,
                Component.translatable("ias.token.import"), btn -> this.importTokens(), Supplier::get);
        importButton.color(0.5F, 1.0F, 1.0F, true);
        this.addRenderableWidget(importButton);
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
        this.selectedTokenFiles = List.of();
        //? if >=1.21.11 {
        this.init(this.width, this.height);
        //?} else
        /*this.init(this.minecraft, this.width, this.height);*/
    }

    private void importTokens() {
        assert this.minecraft != null;
        if (this.importing) {
            return;
        }

        if (this.pasteMode) {
            String raw = this.resolvePasteSource();
            List<String> tokens = this.extractTokenValues(raw);
            if (tokens.isEmpty()) {
                this.showInputError(Component.translatable("ias.token.empty").withStyle(ChatFormatting.RED));
                return;
            }
            this.importTokenValues(tokens);
            return;
        }

        if (!this.selectedTokenFiles.isEmpty()) {
            this.importTokenFiles(this.selectedTokenFiles);
            return;
        }

        if (this.pathInput == null) {
            return;
        }
        String path = this.pathInput.getValue().strip();
        if (path.isBlank()) {
            this.showInputError(Component.translatable("ias.token.empty").withStyle(ChatFormatting.RED));
            return;
        }
        this.importTokenFiles(List.of(path));
    }

    private void importTokenFiles(List<String> sources) {
        assert this.minecraft != null;
        if (sources.isEmpty()) {
            return;
        }

        this.importing = true;
        this.error = Float.NaN;
        this.selectedTokenFiles = List.copyOf(sources);
        this.stage(Component.translatable("ias.token.multi.progress", 1, sources.size()).withStyle(ChatFormatting.YELLOW));

        //? if >=1.21.11 {
        this.init(this.width, this.height);
        //?} else
        /*this.init(this.minecraft, this.width, this.height);*/

        IAS.executor().execute(() -> this.importTokenFileAt(this.selectedTokenFiles, 0, 0, 0, 0));
    }

    private void importTokenValues(List<String> tokens) {
        assert this.minecraft != null;
        if (tokens.isEmpty()) {
            return;
        }

        this.importing = true;
        this.error = Float.NaN;
        this.stage(Component.translatable("ias.token.multi.progress", 1, tokens.size()).withStyle(ChatFormatting.YELLOW));

        //? if >=1.21.11 {
        this.init(this.width, this.height);
        //?} else
        /*this.init(this.minecraft, this.width, this.height);*/

        IAS.executor().execute(() -> this.importTokenValueAt(tokens, 0, 0, 0, 0));
    }

    private void importTokenFileAt(List<String> sources, int index, int imported, int failed, int duplicate) {
        if (this.closed) {
            return;
        }
        if (index >= sources.size()) {
            this.finishTokenImport(imported, failed, duplicate, sources.size());
            return;
        }

        int number = index + 1;
        this.stage(Component.translatable("ias.token.multi.progress", number, sources.size()).withStyle(ChatFormatting.YELLOW));
        List<String> tokens;
        try {
            String source = sources.get(index);
            String raw = Files.readString(Path.of(this.unwrapPath(source)));
            tokens = this.extractTokenValues(raw);
            if (tokens.isEmpty()) {
                throw new IllegalArgumentException("Token file is empty.");
            }
        } catch (Throwable t) {
            LOGGER.warn("IAS: Token file {}/{} could not be read: {}", number, sources.size(), sources.get(index), t);
            this.importTokenFileAt(sources, index + 1, imported, failed + 1, duplicate);
            return;
        }

        this.importTokenFromList(tokens, 0, new CreateHandler() {
            @Override
            public boolean cancelled() {
                return TokenPopupScreen.this.closed;
            }

            @Override
            public void stage(String stage, Object... args) {
                TokenPopupScreen.this.stage(stage, args);
            }

            @Override
            public void success(MicrosoftAccount account) {
                boolean wasDuplicate = TokenPopupScreen.this.storeImportedAccount(account);
                TokenPopupScreen.this.importTokenFileAt(sources, index + 1, imported + 1, failed, duplicate + (wasDuplicate ? 1 : 0));
            }

            @Override
            public void error(Throwable error) {
                LOGGER.warn("IAS: Token file {}/{} failed during batch import: {}", number, sources.size(), sources.get(index), error);
                TokenPopupScreen.this.importTokenFileAt(sources, index + 1, imported, failed + 1, duplicate);
            }
        });
    }

    private void importTokenValueAt(List<String> tokens, int index, int imported, int failed, int duplicate) {
        if (this.closed) {
            return;
        }
        if (index >= tokens.size()) {
            this.finishTokenImport(imported, failed, duplicate, tokens.size());
            return;
        }

        int number = index + 1;
        this.stage(Component.translatable("ias.token.multi.progress", number, tokens.size()).withStyle(ChatFormatting.YELLOW));
        MSAccountFactory.createFromMinecraftAccess(this.crypt, tokens.get(index), new CreateHandler() {
            @Override
            public boolean cancelled() {
                return TokenPopupScreen.this.closed;
            }

            @Override
            public void stage(String stage, Object... args) {
                TokenPopupScreen.this.stage(stage, args);
            }

            @Override
            public void success(MicrosoftAccount account) {
                boolean wasDuplicate = TokenPopupScreen.this.storeImportedAccount(account);
                TokenPopupScreen.this.importTokenValueAt(tokens, index + 1, imported + 1, failed, duplicate + (wasDuplicate ? 1 : 0));
            }

            @Override
            public void error(Throwable error) {
                LOGGER.warn("IAS: Pasted token {}/{} failed during batch import.", number, tokens.size(), error);
                TokenPopupScreen.this.importTokenValueAt(tokens, index + 1, imported, failed + 1, duplicate);
            }
        });
    }

    private void importTokenFromList(List<String> tokens, int index, CreateHandler handler) {
        if (index >= tokens.size()) {
            handler.error(new IllegalArgumentException("No usable token in file."));
            return;
        }

        MSAccountFactory.createFromMinecraftAccess(this.crypt, tokens.get(index), new CreateHandler() {
            @Override
            public boolean cancelled() {
                return handler.cancelled();
            }

            @Override
            public void stage(String stage, Object... args) {
                handler.stage(stage, args);
            }

            @Override
            public void success(MicrosoftAccount account) {
                handler.success(account);
            }

            @Override
            public void error(Throwable error) {
                TokenPopupScreen.this.importTokenFromList(tokens, index + 1, handler);
            }
        });
    }

    private boolean storeImportedAccount(MicrosoftAccount account) {
        boolean duplicate = IASStorage.ACCOUNTS.removeIf(Predicate.isEqual(account));
        IASStorage.ACCOUNTS.add(account);
        try {
            IAS.disclaimersStorage();
            IAS.saveStorage();
        } catch (Throwable t) {
            LOGGER.error("IAS: Unable to save storage.", t);
        }
        return duplicate;
    }

    private void finishTokenImport(int imported, int failed, int duplicate, int total) {
        assert this.minecraft != null;
        this.minecraft.execute(() -> {
            if (this.closed) {
                return;
            }
            this.importing = false;
            this.selectedTokenFiles = List.of();
            synchronized (this.lock) {
                this.stage = Component.translatable("ias.token.multi.done", imported, total, failed, duplicate).withStyle(failed == 0 ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
                this.label = null;
                this.error = 0.0F;
            }
            if (this.parent instanceof AccountScreen accountScreen) {
                accountScreen.refreshAccounts();
            }
            //? if >=1.21.11 {
            this.init(this.width, this.height);
            //?} else
            /*this.init(this.minecraft, this.width, this.height);*/
        });
    }

    private String resolvePasteSource() {
        assert this.minecraft != null;
        String box = this.pasteInput != null ? this.pasteInput.getValue() : "";
        if (!box.isBlank()) {
            return box;
        }
        String clip = this.minecraft.keyboardHandler.getClipboard();
        return clip != null ? clip : "";
    }

    private List<String> extractTokenValues(String raw) {
        String text = raw == null ? "" : raw.strip();
        if (text.isBlank()) {
            return List.of();
        }

        Set<String> tokens = new LinkedHashSet<>();
        Matcher matcher = MC_TOKEN_JSON.matcher(text);
        while (matcher.find()) {
            String token = this.normalizeToken(matcher.group(1));
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        if (!tokens.isEmpty()) {
            return new ArrayList<>(tokens);
        }

        List<String> lines = text.lines().map(String::strip).filter(line -> !line.isBlank()).toList();
        if (lines.size() > 1) {
            for (String line : lines) {
                String token = this.normalizeToken(line);
                if (!token.isBlank()) {
                    tokens.add(token);
                }
            }
        } else {
            String token = this.normalizeToken(text);
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        return new ArrayList<>(tokens);
    }

    private String normalizeToken(String raw) {
        String value = raw == null ? "" : raw.strip();
        value = this.unwrap(value).replace("\r", "").replace("\n", "").strip();
        value = this.removePrefix(value, "MCToken ");
        value = this.removePrefix(value, "Bearer ");
        return this.unwrap(value).strip();
    }

    private String unwrap(String value) {
        String next = value.strip();
        if (next.length() >= 2 && next.charAt(0) == '"' && next.charAt(next.length() - 1) == '"') {
            next = next.substring(1, next.length() - 1);
        }
        return next;
    }

    private String removePrefix(String value, String prefix) {
        String next = value.strip();
        if (next.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return next.substring(prefix.length()).strip();
        }
        return next;
    }

    private String unwrapPath(String path) {
        String value = path.strip();
        if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
            value = value.substring(1, value.length() - 1).strip();
        }
        return value;
    }

    private void browseTokenFiles() {
        assert this.minecraft != null;
        String initial = this.pathInput != null ? this.pathInput.getValue().strip() : this.savedPath.strip();
        final String startPath = this.unwrapPath(initial);
        final String dialogTitle = Component.translatable("ias.token.browse").getString();
        IAS.executor().execute(() -> {
            try {
                List<String> paths = CookieFileDialogs.pickTokenFiles(dialogTitle, startPath);
                if (paths.isEmpty() || this.closed) {
                    return;
                }

                this.minecraft.execute(() -> {
                    if (this.closed || this != this.currentScreen()) {
                        return;
                    }
                    this.selectedTokenFiles = List.copyOf(paths);
                    this.savedPath = paths.size() == 1
                            ? paths.get(0)
                            : Component.translatable("ias.token.selected", paths.size()).getString();
                    if (this.pathInput != null) {
                        this.pathInput.setValue(this.savedPath);
                    }
                });
            } catch (Throwable t) {
                LOGGER.warn("IAS: Token file browse failed.", t);
                this.minecraft.execute(() -> this.showBrowseError());
            }
        });
    }

    private void showBrowseError() {
        if (this.closed) {
            return;
        }
        synchronized (this.lock) {
            this.stage = Component.translatable("ias.token.browse.failed").withStyle(ChatFormatting.RED);
            this.label = null;
            this.error = 0.0F;
        }
    }

    private void showInputError(Component component) {
        synchronized (this.lock) {
            this.stage = component;
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

        if (this.importing) {
            this.renderStage(graphics);
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
                        ? Component.translatable("ias.token.paste.hint")
                        : Component.translatable("ias.token.path.hint");
                //? if >=26.1 {
                graphics.centeredText(this.font, inputTitle, this.width / 2, centerY - 54, 0xFF_FF_FF_FF);
                //?} else
                /*graphics.drawCenteredString(this.font, inputTitle, this.width / 2, centerY - 54, 0xFF_FF_FF_FF);*/
            }
        }
    }

    private void renderStage(GuiGraphicsExtractor graphics) {
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
        int panelHalfHeight = this.pasteMode && !this.importing ? PANEL_HALF_PASTE : PANEL_HALF_PATH;
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

    private void stage(String stage, Object... args) {
        this.stage(Component.translatable(stage, args).withStyle(ChatFormatting.YELLOW));
    }

    private void stage(Component component) {
        assert this.minecraft != null;
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

    private Screen currentScreen() {
        //? if >=26.2 {
        return this.minecraft.gui.screen();
        //?} else {
        /*return this.minecraft.screen;
        *///?}
    }

    @Override
    public String toString() {
        return "TokenPopupScreen{}";
    }
}
