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
import ru.vidtu.ias.auth.TokenImporter;
import ru.vidtu.ias.auth.cookie.CookieParser;
import ru.vidtu.ias.auth.handlers.CreateHandler;
import ru.vidtu.ias.auth.microsoft.MSAccountFactory;
import ru.vidtu.ias.config.IASConfig;
import ru.vidtu.ias.config.IASStorage;
import ru.vidtu.ias.crypt.Crypt;
import ru.vidtu.ias.crypt.PasswordCrypt;
import ru.vidtu.ias.platform.IStonecutter;
import ru.vidtu.ias.utils.AuthLog;
import ru.vidtu.ias.utils.exceptions.FriendlyException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
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
     * Delay between files during multi-cookie imports. Minecraft auth starts rate-limiting
     * if several accounts are exchanged back-to-back.
     */
    private static final long MULTI_COOKIE_IMPORT_DELAY_MS = 6000L;

    /**
     * Delay before retrying the same cookie after a Minecraft auth rate limit.
     */
    private static final long MULTI_COOKIE_RATE_LIMIT_DELAY_MS = 30000L;

    /**
     * Maximum number of rate-limit retries for one cookie file before moving on.
     */
    private static final int MULTI_COOKIE_RATE_LIMIT_RETRIES = 2;

    /**
     * Width of the failed-import list.
     */
    private static final int FAILURE_LIST_WIDTH = 230;

    /**
     * Height of one failed-import line.
     */
    private static final int FAILURE_LINE_HEIGHT = 9;

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
     * Cookie files selected by the native multi-file picker.
     */
    private List<String> selectedCookieFiles = List.of();

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
     * Whether an unrecoverable import error occurred.
     */
    private boolean errored;

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
     * Failed imports, shown after a batch finishes.
     */
    private final List<ImportFailure> failures = new CopyOnWriteArrayList<>();

    /**
     * Whether the completion screen should show the failure list.
     */
    private boolean showFailures;

    /**
     * Scroll offset of the failure list, in lines.
     */
    private int failureScroll;

    /**
     * Initial raw sources to import immediately on open, if any.
     */
    private List<String> initialRawSources;

    /**
     * Creates a new cookie import screen.
     *
     * @param parent  Parent screen
     * @param handler Account handler
     * @param crypt   Crypt method, {@code null} to use password
     */
    CookiePopupScreen(Screen parent, Consumer<Account> handler, Crypt crypt) {
        this(parent, handler, crypt, null);
    }

    /**
     * Creates a new cookie/token import screen pre-populated with raw sources (e.g. from Localts).
     *
     * @param parent     Parent screen
     * @param handler    Account handler
     * @param crypt      Crypt method, {@code null} to use password
     * @param rawSources Raw account source texts to import immediately
     */
    CookiePopupScreen(Screen parent, Consumer<Account> handler, Crypt crypt, List<String> rawSources) {
        super(Component.translatable("ias.cookie"));
        this.parent = parent;
        this.handler = handler;
        this.crypt = crypt;
        this.initialRawSources = rawSources != null && !rawSources.isEmpty() ? List.copyOf(rawSources) : null;
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

        if (this.initialRawSources != null && !this.initialRawSources.isEmpty()) {
            List<String> toImport = this.initialRawSources;
            this.initialRawSources = null;
            this.importRawSources(toImport);
            return;
        }

        if (this.importing) {
            return;
        }

        if (this.showFailures) {
            this.pathInput = null;
            this.pasteInput = null;
            return;
        }

        if (this.errored) {
            this.pathInput = null;
            this.pasteInput = null;
            PopupButton retryBtn = new PopupButton(centerX - 75, centerY + 48, 150, 20,
                    Component.translatable("ias.cookie.retry"), btn -> {
                this.errored = false;
                this.error = Float.NaN;
                this.errorNote = null;
                //? if >=1.21.11 {
                this.init(this.width, this.height);
                //?} else
                /*this.init(this.minecraft, this.width, this.height);*/
            }, Supplier::get);
            retryBtn.color(0.5F, 1.0F, 0.75F, true);
            this.addRenderableWidget(retryBtn);
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
            this.pathInput.setHint(Component.literal(IAS.importDirectory().toString()).withStyle(ChatFormatting.DARK_GRAY));
            this.pathInput.setMaxLength(512);
            if (this.savedPath.isBlank()) {
                this.savedPath = IAS.importDirectory().toString();
            }
            if (!this.savedPath.isBlank()) {
                this.pathInput.setValue(this.savedPath);
            }
            this.pathInput.setResponder(value -> {
                if (!Objects.equals(value, this.savedPath)) {
                    this.selectedCookieFiles = List.of();
                }
            });
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
        if (pasteMode && !this.pasteMode && this.savedPaste.isBlank()) {
            this.savedPaste = this.clipboardContents();
        }
        this.pasteMode = pasteMode;
        this.errored = false;
        this.error = Float.NaN;
        this.errorNote = null;
        this.selectedCookieFiles = List.of();
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

        this.showFailures = false;
        this.failureScroll = 0;
        this.failures.clear();

        String raw;
        if (this.pasteMode) {
            raw = this.resolvePasteSource();
            if (raw.isBlank()) {
                return;
            }
            this.savedPaste = raw;
        } else {
            if (this.pathInput == null) return;
            if (!this.selectedCookieFiles.isEmpty()) {
                this.importCookieFiles(this.selectedCookieFiles);
                return;
            }
            raw = this.pathInput.getValue().strip();
            if (raw.isBlank()) {
                this.importFromFolder(IAS.importDirectory());
                return;
            }
            this.savedPath = raw;
            Path path = Path.of(unwrapPath(raw));
            if (Files.isDirectory(path)) {
                this.importFromFolder(path);
                return;
            }
        }

        this.importing = true;
        this.errored = false;
        this.error = Float.NaN;
        this.errorNote = null;
        this.stage(MicrosoftAccount.INITIALIZING);

        final String source = raw;
        final boolean fromPath = !this.pasteMode;

        //? if >=1.21.11 {
        this.init(this.width, this.height);
        //?} else
        /*this.init(this.minecraft, this.width, this.height);*/

        IAS.executor().execute(() -> this.startCreateFromSource(source, fromPath, this));
    }

    void importFromFolder(Path dir) {
        List<String> files;
        try {
            IAS.ensureImportDirectory();
            files = IAS.listImportFiles(dir);
        } catch (Throwable t) {
            this.error(new FriendlyException("Unable to read import folder: " + dir, t, "ias.error.cookie.file"));
            return;
        }
        if (files.isEmpty()) {
            synchronized (this.lock) {
                this.stage = Component.translatable("ias.cookie.folder.empty", dir.toAbsolutePath().toString()).withStyle(ChatFormatting.YELLOW);
                this.label = null;
                this.error = 0.0F;
            }
            return;
        }
        this.importCookieFiles(files);
    }

    /**
     * Imports cookie/token files (and the contents of folders) dropped onto the account screen.
     *
     * @param paths Dropped files and folders
     */
    void importFromDrop(List<Path> paths) {
        List<String> files;
        try {
            files = IAS.listImportFiles(paths);
        } catch (Throwable t) {
            this.error(new FriendlyException("Unable to read dropped files.", t, "ias.error.cookie.file"));
            return;
        }
        if (files.isEmpty()) {
            this.error(new FriendlyException("No cookie or token files found in the drop.", "ias.error.cookie.drop"));
            return;
        }
        this.importCookieFiles(files);
    }

    void importRawSources(List<String> sources) {
        assert this.minecraft != null;
        if (this.crypt == null || this.importing || sources == null || sources.isEmpty()) return;

        this.importing = true;
        this.errored = false;
        this.error = Float.NaN;
        this.errorNote = null;
        this.showFailures = false;
        this.failureScroll = 0;
        this.failures.clear();
        this.selectedCookieFiles = List.of();
        this.stage(Component.translatable("ias.cookie.multi.progress", 1, sources.size()).withStyle(ChatFormatting.YELLOW));

        //? if >=1.21.11 {
        this.init(this.width, this.height);
        //?} else
        /*this.init(this.minecraft, this.width, this.height);*/

        IAS.executor().execute(() -> {
            List<ImportJob> records = new ArrayList<>();
            for (int i = 0; i < sources.size(); i++) {
                records.add(new ImportJob("Localts #" + (i + 1), sources.get(i)));
            }
            this.importTextRecords(records, 0, 0, 0, 0, 0, records.size(), this);
        });
    }

    private void importCookieFiles(List<String> sources) {
        assert this.minecraft != null;
        if (this.crypt == null || this.importing || sources.isEmpty()) return;

        this.importing = true;
        this.errored = false;
        this.error = Float.NaN;
        this.errorNote = null;
        this.showFailures = false;
        this.failureScroll = 0;
        this.failures.clear();
        this.selectedCookieFiles = List.copyOf(sources);
        this.stage(Component.translatable("ias.cookie.multi.progress", 1, sources.size()).withStyle(ChatFormatting.YELLOW));

        //? if >=1.21.11 {
        this.init(this.width, this.height);
        //?} else
        /*this.init(this.minecraft, this.width, this.height);*/

        IAS.executor().execute(() -> {
            List<ImportJob> records = new ArrayList<>();
            int readFailed = 0;
            for (String source : sources) {
                String label = sourceName(source);
                try {
                    List<String> split = CookieParser.splitRecords(CookieParser.readText(Path.of(unwrapPath(source))));
                    for (int i = 0; i < split.size(); i++) {
                        records.add(new ImportJob(split.size() == 1 ? label : label + " #" + (i + 1), split.get(i)));
                    }
                } catch (Throwable t) {
                    readFailed++;
                    this.recordFailure(label, new FriendlyException("Unable to read " + source, t, "ias.error.cookie.file"));
                    AuthLog.expected(LOGGER, "Unable to read " + source, t);
                }
            }
            if (records.isEmpty()) {
                if (readFailed > 0) {
                    this.finishCookieFileImport(0, readFailed, 0, readFailed);
                } else {
                    this.error(new FriendlyException("No cookie accounts found in the selected files.", "ias.error.cookie.invalid"));
                }
                return;
            }
            this.importTextRecords(records, 0, 0, readFailed, 0, 0, records.size() + readFailed, this);
        });
    }

    private void importTextRecords(List<ImportJob> records, int index, int imported, int failed, int duplicate, int rateLimitRetries, int total, CreateHandler handler) {
        if (this.closed) {
            return;
        }
        if (index >= records.size()) {
            this.finishCookieFileImport(imported, failed, duplicate, total);
            return;
        }

        int number = index + 1;
        ImportJob job = records.get(index);
        this.stage(Component.translatable("ias.cookie.multi.progress", number, records.size()).withStyle(ChatFormatting.YELLOW));
        CompletableFuture<MicrosoftAccount> future = new CompletableFuture<>();
        this.startCreateFromSource(job.source(), false, new CreateHandler() {
            @Override
            public boolean cancelled() {
                return CookiePopupScreen.this.closed || handler.cancelled();
            }

            @Override
            public void stage(String stage, Object... args) {
                handler.stage(stage, args);
            }

            @Override
            public void success(MicrosoftAccount account) {
                future.complete(account);
            }

            @Override
            public void error(Throwable error) {
                future.completeExceptionally(error);
            }
        });

        future.whenCompleteAsync((account, error) -> {
            if (this.closed) {
                return;
            }
            if (error != null || account == null) {
                if (this.isRateLimited(error) && rateLimitRetries < MULTI_COOKIE_RATE_LIMIT_RETRIES) {
                    int attempt = rateLimitRetries + 1;
                    this.stage(Component.literal("Rate limited. Retrying account " + number + "/" + records.size() + " after a short wait (attempt " + attempt + "/" + MULTI_COOKIE_RATE_LIMIT_RETRIES + ")...").withStyle(ChatFormatting.YELLOW));
                    this.scheduleTextRecordImport(records, index, imported, failed, duplicate, attempt, total, MULTI_COOKIE_RATE_LIMIT_DELAY_MS, handler);
                    return;
                }
                AuthLog.expected(LOGGER, "Cookie account " + number + "/" + records.size() + " failed", error);
                this.recordFailure(job.label(), error);
                this.scheduleTextRecordImport(records, index + 1, imported, failed + 1, duplicate, 0, total, MULTI_COOKIE_IMPORT_DELAY_MS, handler);
                return;
            }

            boolean wasDuplicate = this.storeImportedAccount(account);
            this.scheduleTextRecordImport(records, index + 1, imported + 1, failed, duplicate + (wasDuplicate ? 1 : 0), 0, total, MULTI_COOKIE_IMPORT_DELAY_MS, handler);
        }, IAS.executor());
    }

    private void scheduleTextRecordImport(List<ImportJob> records, int index, int imported, int failed, int duplicate, int rateLimitRetries, int total, long delayMs, CreateHandler handler) {
        if (index >= records.size()) {
            this.importTextRecords(records, index, imported, failed, duplicate, rateLimitRetries, total, handler);
            return;
        }
        CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS, IAS.executor())
                .execute(() -> this.importTextRecords(records, index, imported, failed, duplicate, rateLimitRetries, total, handler));
    }

    private static String sourceName(String source) {
        try {
            Path file = Path.of(unwrapPath(source)).getFileName();
            return file != null ? file.toString() : source;
        } catch (Throwable t) {
            return source;
        }
    }

    private void importCookieFileAt(List<String> sources, int index, int imported, int failed, int duplicate, int rateLimitRetries) {
        if (this.closed) {
            return;
        }
        if (index >= sources.size()) {
            this.finishCookieFileImport(imported, failed, duplicate, sources.size());
            return;
        }

        int number = index + 1;
        this.stage(Component.translatable("ias.cookie.multi.progress", number, sources.size()).withStyle(ChatFormatting.YELLOW));
        CompletableFuture<MicrosoftAccount> future = new CompletableFuture<>();
        this.startCreateFromSource(sources.get(index), true, new CreateHandler() {
            @Override
            public boolean cancelled() {
                return CookiePopupScreen.this.closed;
            }

            @Override
            public void stage(String stage, Object... args) {
                CookiePopupScreen.this.stage(stage, args);
            }

            @Override
            public void success(MicrosoftAccount account) {
                future.complete(account);
            }

            @Override
            public void error(Throwable error) {
                future.completeExceptionally(error);
            }
        });

        future.whenCompleteAsync((account, error) -> {
            if (this.closed) {
                return;
            }
            if (error != null || account == null) {
                if (this.isRateLimited(error) && rateLimitRetries < MULTI_COOKIE_RATE_LIMIT_RETRIES) {
                    int attempt = rateLimitRetries + 1;
                    this.stage(Component.literal("Rate limited. Retrying cookie file " + number + "/" + sources.size() + " after a short wait (attempt " + attempt + "/" + MULTI_COOKIE_RATE_LIMIT_RETRIES + ")...").withStyle(ChatFormatting.YELLOW));
                    this.scheduleCookieFileImport(sources, index, imported, failed, duplicate, attempt, MULTI_COOKIE_RATE_LIMIT_DELAY_MS);
                    return;
                }
                AuthLog.expected(LOGGER, "Cookie file " + number + "/" + sources.size() + " failed: " + sources.get(index), error);
                this.recordFailure(sourceName(sources.get(index)), error);
                this.scheduleCookieFileImport(sources, index + 1, imported, failed + 1, duplicate, 0, MULTI_COOKIE_IMPORT_DELAY_MS);
                return;
            }

            boolean wasDuplicate = this.storeImportedAccount(account);
            this.scheduleCookieFileImport(sources, index + 1, imported + 1, failed, duplicate + (wasDuplicate ? 1 : 0), 0, MULTI_COOKIE_IMPORT_DELAY_MS);
        }, IAS.executor());
    }

    private void scheduleCookieFileImport(List<String> sources, int index, int imported, int failed, int duplicate, int rateLimitRetries, long delayMs) {
        if (index >= sources.size()) {
            this.importCookieFileAt(sources, index, imported, failed, duplicate, rateLimitRetries);
            return;
        }
        CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS, IAS.executor())
                .execute(() -> this.importCookieFileAt(sources, index, imported, failed, duplicate, rateLimitRetries));
    }

    private boolean isRateLimited(Throwable error) {
        FriendlyException friendly = FriendlyException.friendlyInChain(error);
        return friendly != null && "ias.error.rateLimited".equals(friendly.key());
    }

    private void recordFailure(String label, Throwable error) {
        FriendlyException friendly = FriendlyException.friendlyInChain(error);
        Component reason = friendly != null
                ? Component.translatable(friendly.key(), friendly.args())
                : Component.translatable("ias.error");
        this.failures.add(new ImportFailure(label, reason));
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

    private void finishCookieFileImport(int imported, int failed, int duplicate, int total) {
        assert this.minecraft != null;
        this.minecraft.execute(() -> {
            if (this.closed) {
                return;
            }
            this.importing = false;
            this.selectedCookieFiles = List.of();
            this.showFailures = !this.failures.isEmpty();
            this.failureScroll = 0;
            synchronized (this.lock) {
                this.stage = Component.translatable("ias.cookie.multi.done", imported, total, failed, duplicate).withStyle(failed == 0 ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
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

    private void startCreateFromSource(String source, boolean fromPath, CreateHandler createHandler) {
        try {
            if (this.closed) {
                return;
            }

            final String text;
            if (fromPath) {
                try {
                    text = CookieParser.readText(Path.of(unwrapPath(source)));
                } catch (java.io.IOException e) {
                    createHandler.error(new FriendlyException("Unable to read cookie file: " + source, e, "ias.error.cookie.file"));
                    return;
                }
            } else {
                text = source;
            }

            List<String> records = CookieParser.splitRecords(text);
            if (records.size() > 1) {
                List<ImportJob> jobs = new ArrayList<>(records.size());
                for (int i = 0; i < records.size(); i++) {
                    jobs.add(new ImportJob(Component.translatable("ias.cookie.account", i + 1).getString(), records.get(i)));
                }
                this.importTextRecords(jobs, 0, 0, 0, 0, 0, jobs.size(), createHandler);
                return;
            }

            List<String> tokens = TokenImporter.extractValues(text);
            if (tokens.size() > 1) {
                this.importTokenValues(tokens, 0, 0, 0, 0, createHandler);
                return;
            }

            try {
                createHandler.stage(MicrosoftAccount.COOKIES_TO_MSA_MSR);

                CookieParser.ParsedCookies cookies = CookieParser.fromText(text);

                if (this.closed) {
                    return;
                }

                if (!cookies.refreshToken().isBlank()) {
                    // Route through TokenImporter so tokens minted for the IAS OAuth client (for
                    // example, a refresh token copied from an account added via Microsoft login)
                    // are retried with the launcher client, and vice versa. A launcher-only
                    // exchange rejects IAS-bound tokens with "Client mismatch" (400).
                    boolean hasCookies = !cookies.cookies().isEmpty();
                    TokenImporter.createAccountFromToken(this.crypt, cookies.refreshToken(), new CreateHandler() {
                        @Override
                        public boolean cancelled() {
                            return createHandler.cancelled();
                        }

                        @Override
                        public void stage(String stage, Object... args) {
                            createHandler.stage(stage, args);
                        }

                        @Override
                        public void success(MicrosoftAccount account) {
                            createHandler.success(account);
                        }

                        @Override
                        public void error(Throwable error) {
                            if (!hasCookies || createHandler.cancelled()) {
                                createHandler.error(error);
                                return;
                            }

                            // The M.C artifact embedded in the cookies may belong to a different
                            // OAuth client (e.g. the login.live.com web session), in which case it
                            // cannot be redeemed here. The cookies themselves may still be a live
                            // session, so retry through the regular cookie (OAuth/SISU) flow.
                            AuthLog.expected(LOGGER, "Embedded refresh token failed, falling back to cookie auth", error);
                            MSAccountFactory.createFromCookies(CookiePopupScreen.this.crypt, cookies.toCookieHeader(), createHandler)
                                    .exceptionallyAsync(t -> {
                                        createHandler.error(t);
                                        return null;
                                    }, IAS.executor());
                        }
                    });
                    return;
                }

                MSAccountFactory.createFromCookies(this.crypt, cookies.toCookieHeader(), createHandler).exceptionallyAsync(t -> {
                    createHandler.error(t);
                    return null;
                }, IAS.executor());
                return;
            } catch (FriendlyException cookieError) {
                if (!"ias.error.cookie.invalid".equals(cookieError.key())) {
                    createHandler.error(cookieError);
                    return;
                }
            }

            TokenImporter.importText(this.crypt, text, createHandler);
        } catch (Throwable t) {
            createHandler.error(t);
        }
    }

    private void importTokenValues(List<String> tokens, int index, int imported, int failed, int duplicate, CreateHandler handler) {
        if (this.closed) {
            return;
        }
        if (index >= tokens.size()) {
            this.finishCookieFileImport(imported, failed, duplicate, tokens.size());
            return;
        }

        int number = index + 1;
        this.stage(Component.translatable("ias.cookie.multi.progress", number, tokens.size()).withStyle(ChatFormatting.YELLOW));
        TokenImporter.createAccountFromToken(this.crypt, tokens.get(index), new CreateHandler() {
            @Override
            public boolean cancelled() {
                return CookiePopupScreen.this.closed || handler.cancelled();
            }

            @Override
            public void stage(String stage, Object... args) {
                handler.stage(stage, args);
            }

            @Override
            public void success(MicrosoftAccount account) {
                boolean wasDuplicate = CookiePopupScreen.this.storeImportedAccount(account);
                CookiePopupScreen.this.importTokenValues(tokens, index + 1, imported + 1, failed, duplicate + (wasDuplicate ? 1 : 0), handler);
            }

            @Override
            public void error(Throwable error) {
                AuthLog.expected(LOGGER, "Token " + number + "/" + tokens.size() + " failed during batch import", error);
                CookiePopupScreen.this.recordFailure(Component.translatable("ias.cookie.account", number).getString(), error);
                CookiePopupScreen.this.importTokenValues(tokens, index + 1, imported, failed + 1, duplicate, handler);
            }
        });
    }

    private static String unwrapPath(String path) {
        String value = path.strip();
        if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
            value = value.substring(1, value.length() - 1).strip();
        }
        return value;
    }

    /**
     * Resolves pasted cookie text, preferring the clipboard when the text box lost tab characters.
     */
    private String resolvePasteSource() {
        String box = this.pasteInput != null ? this.pasteInput.getValue() : "";
        String clip = this.clipboardContents();
        if (!box.isBlank() && box.contains("\t")) {
            return box;
        }
        if (!clip.isBlank() && clip.contains("\t")) {
            return clip;
        }
        if (!box.isBlank()) {
            return box;
        }
        return clip;
    }

    /**
     * Reads clipboard text without allowing a platform clipboard failure to break the import screen.
     */
    private String clipboardContents() {
        assert this.minecraft != null;
        try {
            String clipboard = this.minecraft.keyboardHandler.getClipboard();
            return clipboard != null ? clipboard : "";
        } catch (Throwable t) {
            LOGGER.warn("IAS: Unable to read cookie import text from the clipboard.", t);
            return "";
        }
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
                List<String> paths = CookieFileDialogs.pickCookieFiles(dialogTitle, startPath);
                if (paths.isEmpty() || this.closed) {
                    return;
                }

                this.minecraft.execute(() -> {
                    if (this.closed || this != this.currentScreen()) {
                        return;
                    }
                    this.selectedCookieFiles = List.copyOf(paths);
                    this.savedPath = paths.size() == 1
                            ? paths.get(0)
                            : Component.translatable("ias.cookie.selected", paths.size()).getString();
                    if (this.pathInput != null) {
                        this.pathInput.setValue(this.savedPath);
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
        this.errored = false;
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
        } else if (this.importing || this.errored) {
            synchronized (this.lock) {
                if (this.label == null) {
                    Component component = Objects.requireNonNullElse(this.stage, Component.empty());
                    this.label = MultiLineLabel.create(this.font, component, 230);
                    this.minecraft.getNarrator().saySystemQueued(component);
                }
                int centerY = this.height / 2;
                int labelY = this.errored
                        ? (centerY - 6) - (this.label.getLineCount() * 9) / 2
                        : (this.height - this.label.getLineCount() * 9) / 2 - 4;
                IStonecutter.renderMultilineLabelCentered(this.label, graphics, this.width / 2, labelY);
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
                int sy = this.height / 2 + 96;
                graphics.fill(cx - w, sy, cx + w, sy + h, 0x101010 | opacityMask);
                graphics.fill(cx - w + 1, sy - 1, cx + w - 1, sy, 0x101010 | opacityMask);
                graphics.fill(cx - w + 1, sy + h, cx + w - 1, sy + h + 1, 0x101010 | opacityMask);
                pose.pushMatrix();
                pose.scale(0.5F, 0.5F);
                //? if >= 1.21.11 {
                var renderer = graphics.textRenderer();
                renderer.defaultParameters(renderer.defaultParameters().withOpacity(opacityFloat));
                this.errorNote.visitLines(net.minecraft.client.gui.TextAlignment.CENTER, this.width, this.height + 192, 9, renderer);
                //?} elif >= 1.21.10 {
                /*this.errorNote.render(graphics, MultiLineLabel.Align.CENTER, this.width, this.height + 192, 9, false, 0xFF_FF_FF | opacityMask);
                *///?} else
                /*this.errorNote.renderCentered(graphics, this.width, this.height + 192, 9, 0xFF_FF_FF | opacityMask);*/
                pose.popMatrix();
            }
        } else if (this.showFailures) {
            this.renderFailures(graphics);
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

    //? if >=26.1 {
    private void renderFailures(GuiGraphicsExtractor graphics) {
    //?} else
    /*private void renderFailures(GuiGraphics graphics) {*/
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int left = centerX - FAILURE_LIST_WIDTH / 2;
        int top = this.failureListTop();
        int bottom = this.failureListBottom();

        synchronized (this.lock) {
            if (this.label == null) {
                this.label = MultiLineLabel.create(this.font, Objects.requireNonNullElse(this.stage, Component.empty()), FAILURE_LIST_WIDTH);
            }
            IStonecutter.renderMultilineLabelCentered(this.label, graphics, centerX, centerY - 54);
        }

        List<FormattedCharSequence> lines = this.failureLines();
        int visibleLines = Math.max(1, (bottom - top) / FAILURE_LINE_HEIGHT);
        int maxScroll = Math.max(0, lines.size() - visibleLines);
        this.failureScroll = Math.max(0, Math.min(maxScroll, this.failureScroll));

        Component heading = Component.translatable("ias.cookie.failures", this.failures.size());
        //? if >=26.1 {
        graphics.centeredText(this.font, heading, centerX, top - 12, 0xFF_FF_80_80);
        //?} else
        /*graphics.drawCenteredString(this.font, heading, centerX, top - 12, 0xFF_FF_80_80);*/

        graphics.enableScissor(left, top, left + FAILURE_LIST_WIDTH, bottom);
        int y = top;
        for (int i = this.failureScroll; i < lines.size() && i < this.failureScroll + visibleLines; i++) {
            //? if >=26.1 {
            graphics.text(this.font, lines.get(i), left, y, 0xFF_FF_FF_FF);
            //?} else
            /*graphics.drawString(this.font, lines.get(i), left, y, 0xFF_FF_FF_FF);*/
            y += FAILURE_LINE_HEIGHT;
        }
        graphics.disableScissor();

        if (maxScroll > 0) {
            int trackX = left + FAILURE_LIST_WIDTH + 5;
            graphics.fill(trackX, top, trackX + 2, bottom, 0x60_FF_FF_FF);
            int trackHeight = bottom - top;
            int thumbHeight = Math.max(10, trackHeight * visibleLines / lines.size());
            int thumbY = top + (trackHeight - thumbHeight) * this.failureScroll / maxScroll;
            graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, 0xFF_FF_FF_FF);
        }
    }

    private List<FormattedCharSequence> failureLines() {
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (ImportFailure failure : this.failures) {
            Component text = Component.literal(failure.label()).withStyle(ChatFormatting.RED)
                    .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(failure.reason().copy().withStyle(ChatFormatting.GRAY));
            lines.addAll(this.font.split(text, FAILURE_LIST_WIDTH - 8));
        }
        return lines;
    }

    private int failureListTop() {
        return this.height / 2 - 24;
    }

    private int failureListBottom() {
        return this.height / 2 + 62;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.showFailures) {
            int left = this.width / 2 - FAILURE_LIST_WIDTH / 2;
            int top = this.failureListTop();
            int bottom = this.failureListBottom();
            if (mouseX >= left && mouseX <= left + FAILURE_LIST_WIDTH && mouseY >= top && mouseY <= bottom) {
                List<FormattedCharSequence> lines = this.failureLines();
                int visibleLines = Math.max(1, (bottom - top) / FAILURE_LINE_HEIGHT);
                int maxScroll = Math.max(0, lines.size() - visibleLines);
                this.failureScroll = Math.max(0, Math.min(maxScroll, this.failureScroll - (int) (scrollY * 2.0D)));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
        int panelHalfHeight = this.pasteMode && this.crypt != null && !this.importing && !this.errored ? PANEL_HALF_PASTE : PANEL_HALF_PATH;
        graphics.fill(centerX - 125, centerY - panelHalfHeight, centerX + 125, centerY + panelHalfHeight, 0xF8_20_20_30);
        graphics.fill(centerX - 124, centerY - panelHalfHeight - 1, centerX + 124, centerY - panelHalfHeight, 0xF8_20_20_30);
        graphics.fill(centerX - 124, centerY + panelHalfHeight, centerX + 124, centerY + panelHalfHeight + 1, 0xF8_20_20_30);

        if (this.pasteMode && this.pasteInput != null && !this.importing && !this.errored) {
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
        if (AuthLog.expectedFailure(error)) {
            AuthLog.expected(LOGGER, "Cookie import failed", error);
        } else {
            AuthLog.unexpected(LOGGER, "Cookie import error", error);
        }
        assert this.minecraft != null;

        if (this.pathInput != null) {
            this.savedPath = this.pathInput.getValue();
        }
        if (this.pasteInput != null) {
            this.savedPaste = this.pasteInput.getValue();
        }

        FriendlyException probable = FriendlyException.friendlyInChain(error);
        String key = probable != null ? probable.key() : "ias.error";
        Object[] args = probable != null ? probable.args() : new Object[0];
        Component component = (args.length > 0 ? Component.translatable(key, args) : Component.translatable(key)).withStyle(ChatFormatting.RED);
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
            this.errored = true;
            this.showFailures = false;
            //? if >=1.21.11 {
            this.init(this.width, this.height);
            //?} else
            /*this.init(this.minecraft, this.width, this.height);*/
        });
    }

    private record ImportJob(String label, String source) {
    }

    private record ImportFailure(String label, Component reason) {
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
