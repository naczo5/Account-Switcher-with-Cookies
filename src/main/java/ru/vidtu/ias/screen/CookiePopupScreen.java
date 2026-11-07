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
import net.minecraft.network.chat.MutableComponent;
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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
     * Short delay used after the user explicitly skips an account.
     */
    private static final long MULTI_COOKIE_SKIP_DELAY_MS = 1000L;

    /**
     * Ignore duplicate skip clicks within this window so a double-click skips only one account.
     */
    private static final long MULTI_COOKIE_SKIP_CLICK_COOLDOWN_MS = 750L;

    /**
     * Maximum number of rate-limit retries for one cookie file before moving on.
     */
    private static final int MULTI_COOKIE_RATE_LIMIT_RETRIES = 2;

    /**
     * Maximum time one account may take in a batch import before it is counted as failed and the batch moves on.
     */
    private static final long MULTI_COOKIE_RECORD_TIMEOUT_MS = 120000L;

    /**
     * Maximum width of a batch status line.
     */
    private static final int STATUS_WIDTH = 230;

    /**
     * Matcher for the Minecraft username in a Minecraft access token payload.
     */
    private static final Pattern JWT_NAME = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]{1,64})\"");

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
     * 1-based index the user asked to skip while waiting between accounts, or {@code 0}.
     */
    private volatile int skipRequestedIndex;

    /**
     * 1-based index of the job that is currently active or scheduled next, or {@code 0}.
     */
    private volatile int pendingJobIndex;

    /**
     * {@link System#nanoTime()} of the last accepted skip click, used to debounce double-clicks.
     */
    private volatile long lastSkipNanos;

    /**
     * Future of the account currently being imported, if any.
     */
    private volatile CompletableFuture<MicrosoftAccount> activeRecordFuture;

    /**
     * Cancellation flag of the account currently being imported, if any.
     */
    private volatile AtomicBoolean activeRecordCancelled;

    /**
     * {@link System#nanoTime()} at which the current batch started.
     */
    private volatile long batchStartedNanos;

    /**
     * {@link System#nanoTime()} at which the whole import started (including file parsing).
     */
    private volatile long importStartedNanos;

    /**
     * {@link System#nanoTime()} at which the current account started.
     */
    private volatile long accountStartedNanos;

    /**
     * Final duration of the last account, used while waiting for the next one to start.
     */
    private volatile long accountElapsedNanos;

    /**
     * Total number of jobs in the current batch (including unreadable files).
     */
    private volatile int batchTotal;

    /**
     * 1-based number of the current batch job.
     */
    private volatile int batchIndex;

    /**
     * Successfully imported accounts in the current batch.
     */
    private volatile int batchImported;

    /**
     * Failed accounts in the current batch.
     */
    private volatile int batchFailed;

    /**
     * Accounts skipped by the user in the current batch.
     */
    private volatile int batchSkipped;

    /**
     * Duplicate accounts in the current batch.
     */
    private volatile int batchDuplicate;

    /**
     * Job currently being imported, if any.
     */
    private volatile ImportJob batchJob;

    /**
     * Username/mail reported by the authentication pipeline for the current job.
     */
    private volatile String batchAccountName;

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
    private volatile Component stage = Component.empty();

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

        if (this.importing && this.batchTotal > 1) {
            PopupButton skipButton = new PopupButton(centerX - 75, centerY + 46, 150, 20,
                    Component.translatable("ias.cookie.batch.skip"), btn -> this.skipCurrentAccount(), Supplier::get);
            skipButton.color(1.0F, 0.75F, 0.5F, true);
            skipButton.setTooltip(Tooltip.create(Component.translatable("ias.cookie.batch.skip.tip")));
            skipButton.setTooltipDelay(Duration.ofMillis(250L));
            this.addRenderableWidget(skipButton);
        }

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
        this.importStartedNanos = System.nanoTime();
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

    @Override
    public void onFilesDrop(List<Path> paths) {
        if (this.crypt == null || this.importing || paths == null || paths.isEmpty()) {
            return;
        }
        this.importFromDrop(paths);
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
        this.importStartedNanos = System.nanoTime();
        this.stage(Component.translatable("ias.cookie.multi.progress", 1, sources.size()).withStyle(ChatFormatting.YELLOW));

        //? if >=1.21.11 {
        this.init(this.width, this.height);
        //?} else
        /*this.init(this.minecraft, this.width, this.height);*/

        IAS.executor().execute(() -> {
            try {
                List<ImportJob> records = new ArrayList<>();
                for (int i = 0; i < sources.size(); i++) {
                    addJobs(records, "Localts #" + (i + 1), sources.get(i));
                }
                if (this.closed) {
                    return;
                }
                if (records.isEmpty()) {
                    this.error(new FriendlyException("No usable accounts found in the import.", "ias.error.cookie.invalid"));
                    return;
                }
                int total = Math.max(1, records.size());
                this.beginBatch(total);
                this.refreshBatchWidgets();
                this.importJobs(records, 0, 0, 0, 0, 0, 0, total, this);
            } catch (Throwable t) {
                this.abortImport("Cookie batch import crashed while preparing accounts", t);
            }
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
        this.importStartedNanos = System.nanoTime();
        this.stage(Component.translatable("ias.cookie.multi.progress", 1, sources.size()).withStyle(ChatFormatting.YELLOW));

        //? if >=1.21.11 {
        this.init(this.width, this.height);
        //?} else
        /*this.init(this.minecraft, this.width, this.height);*/

        IAS.executor().execute(() -> {
            try {
                List<ImportJob> records = new ArrayList<>();
                int readFailed = 0;
                for (String source : sources) {
                    String label = sourceName(source);
                    try {
                        List<String> split = CookieParser.splitRecords(readImportText(source));
                        for (int i = 0; i < split.size(); i++) {
                            addJobs(records, split.size() == 1 ? label : label + " #" + (i + 1), split.get(i));
                        }
                    } catch (Throwable t) {
                        readFailed++;
                        this.recordFailure(label, new FriendlyException("Unable to read " + source, t, "ias.error.cookie.file"));
                        AuthLog.expected(LOGGER, "Unable to read " + source, t);
                    }
                }
                if (this.closed) {
                    return;
                }
                if (records.isEmpty()) {
                    if (readFailed > 0) {
                        this.finishCookieFileImport(0, readFailed, 0, 0, readFailed);
                    } else {
                        this.error(new FriendlyException("No cookie accounts found in the selected files.", "ias.error.cookie.invalid"));
                    }
                    return;
                }
                int total = records.size() + readFailed;
                this.beginBatch(total);
                this.refreshBatchWidgets();
                this.importJobs(records, 0, 0, readFailed, 0, 0, 0, total, this);
            } catch (Throwable t) {
                this.abortImport("Cookie batch import crashed while preparing accounts", t);
            }
        });
    }

    /**
     * Adds a batch job for the record, or one job per token if the record holds several tokens.
     * <p>
     * Each job also carries a best-effort type and username hint for the status display.
     */
    private static void addJobs(List<ImportJob> out, String label, String record) {
        List<String> tokens = TokenImporter.extractValues(record);
        if (tokens.size() > 1) {
            for (int i = 0; i < tokens.size(); i++) {
                String token = tokens.get(i);
                out.add(new ImportJob(label + " #" + (i + 1), token, detectType(token), tokenUsername(token)));
            }
            return;
        }
        out.add(new ImportJob(label, record, detectType(record), usernameHint(record)));
    }

    /**
     * Resets the per-batch progress counters.
     *
     * @param total Number of jobs in the batch
     */
    private void beginBatch(int total) {
        this.batchStartedNanos = this.importStartedNanos > 0L ? this.importStartedNanos : System.nanoTime();
        this.accountStartedNanos = 0L;
        this.accountElapsedNanos = 0L;
        this.batchTotal = Math.max(0, total);
        this.batchIndex = 0;
        this.batchImported = 0;
        this.batchFailed = 0;
        this.batchSkipped = 0;
        this.batchDuplicate = 0;
        this.batchJob = null;
        this.batchAccountName = null;
        this.skipRequestedIndex = 0;
        this.pendingJobIndex = total > 0 ? 1 : 0;
        this.lastSkipNanos = 0L;
        this.activeRecordFuture = null;
        this.activeRecordCancelled = null;
    }

    /**
     * Rebuilds the import widgets after the exact job count is known, so the skip button can appear.
     */
    private void refreshBatchWidgets() {
        assert this.minecraft != null;
        this.minecraft.execute(() -> {
            if (this.closed || this != this.currentScreen()) {
                return;
            }
            synchronized (this.lock) {
                this.label = null;
            }
            //? if >=1.21.11 {
            this.init(this.width, this.height);
            //?} else
            /*this.init(this.minecraft, this.width, this.height);*/
        });
    }

    /**
     * Updates the counters displayed by the live batch status.
     */
    private void updateBatchProgress(int index, int imported, int failed, int skipped, int duplicate) {
        this.batchIndex = index;
        this.batchImported = imported;
        this.batchFailed = failed;
        this.batchSkipped = skipped;
        this.batchDuplicate = duplicate;
    }

    /**
     * Imports the given batch jobs one by one.
     *
     * @param jobs             Jobs to import
     * @param index            Current job index
     * @param imported         Successful imports so far
     * @param failed           Failed imports so far
     * @param skipped          Imports skipped by the user so far
     * @param duplicate        Duplicate imports so far
     * @param rateLimitRetries Rate-limit retries used for the current job
     * @param total            Total number of jobs (including files that could not be read)
     * @param handler          Parent creation handler
     */
    private void importJobs(List<ImportJob> jobs, int index, int imported, int failed, int skipped, int duplicate,
                            int rateLimitRetries, int total, CreateHandler handler) {
        if (this.closed) {
            return;
        }
        if (index >= jobs.size()) {
            this.finishCookieFileImport(imported, failed, skipped, duplicate, Math.max(total, jobs.size()));
            return;
        }

        if (this.skipRequestedIndex != 0) {
            if (this.skipRequestedIndex == index + 1) {
                this.skipRequestedIndex = 0;
                int skippedNumber = index + 1;
                this.batchJob = jobs.get(index);
                this.batchAccountName = null;
                this.updateBatchProgress(skippedNumber, imported, failed, skipped + 1, duplicate);
                this.accountStartedNanos = 0L;
                this.accountElapsedNanos = 0L;
                this.stage(Component.translatable("ias.cookie.batch.skipped", skippedNumber, Math.max(total, jobs.size())).withStyle(ChatFormatting.YELLOW));
                this.scheduleJobImport(jobs, index + 1, imported, failed, skipped + 1, duplicate, 0, total, MULTI_COOKIE_SKIP_DELAY_MS, handler);
                return;
            }
            this.skipRequestedIndex = 0;
        }

        int number = index + 1;
        int jobCount = Math.max(total, jobs.size());
        ImportJob job = jobs.get(index);
        this.pendingJobIndex = number;
        this.updateBatchProgress(number, imported, failed, skipped, duplicate);
        this.batchJob = job;
        this.batchAccountName = null;
        this.accountStartedNanos = System.nanoTime();
        this.accountElapsedNanos = 0L;
        this.stage(Component.translatable(MicrosoftAccount.INITIALIZING).withStyle(ChatFormatting.YELLOW));
        // A record that never reports success or failure must not stall the whole batch.
        CompletableFuture<MicrosoftAccount> future = new CompletableFuture<MicrosoftAccount>()
                .orTimeout(MULTI_COOKIE_RECORD_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        AtomicBoolean recordCancelled = new AtomicBoolean();
        this.activeRecordFuture = future;
        this.activeRecordCancelled = recordCancelled;
        this.startCreateSingleSource(job.source(), new CreateHandler() {
            @Override
            public boolean cancelled() {
                return CookiePopupScreen.this.closed || handler.cancelled() || recordCancelled.get();
            }

            @Override
            public void stage(String stage, Object... args) {
                // Ignore late stage updates from a record that already finished or timed out.
                if (!future.isDone()) {
                    handler.stage(stage, args);
                }
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

        future.whenCompleteAsync((account, rawError) -> {
            // The returned future is never observed, so an exception escaping this callback would be
            // swallowed and leave the screen stuck on the last stage. Always surface it instead.
            try {
                if (this.closed) {
                    return;
                }
                if (this.activeRecordFuture == future) {
                    this.activeRecordFuture = null;
                    this.activeRecordCancelled = null;
                }
                // Stop any auth pipeline that is still running after a timeout or skip.
                recordCancelled.set(true);
                if (this.accountStartedNanos > 0L) {
                    this.accountElapsedNanos = System.nanoTime() - this.accountStartedNanos;
                    this.accountStartedNanos = 0L;
                }
                Throwable error = this.normalizeTimeout(rawError, job.label());
                if (this.isSkipped(error)) {
                    this.updateBatchProgress(number, imported, failed, skipped + 1, duplicate);
                    this.stage(Component.translatable("ias.cookie.batch.skipped", number, jobCount).withStyle(ChatFormatting.YELLOW));
                    AuthLog.expected(LOGGER, "Cookie account " + number + "/" + jobCount + " skipped", error);
                    this.scheduleJobImport(jobs, index + 1, imported, failed, skipped + 1, duplicate, 0, total, MULTI_COOKIE_SKIP_DELAY_MS, handler);
                    return;
                }
                if (error != null || account == null) {
                    if (this.isRateLimited(error) && rateLimitRetries < MULTI_COOKIE_RATE_LIMIT_RETRIES) {
                        int attempt = rateLimitRetries + 1;
                        this.stage(Component.translatable("ias.cookie.batch.retry", MULTI_COOKIE_RATE_LIMIT_DELAY_MS / 1000L, attempt, MULTI_COOKIE_RATE_LIMIT_RETRIES).withStyle(ChatFormatting.YELLOW));
                        this.scheduleJobImport(jobs, index, imported, failed, skipped, duplicate, attempt, total, MULTI_COOKIE_RATE_LIMIT_DELAY_MS, handler);
                        return;
                    }
                    AuthLog.expected(LOGGER, "Cookie account " + number + "/" + jobCount + " failed", error);
                    this.recordFailure(job.label(), error);
                    this.updateBatchProgress(number, imported, failed + 1, skipped, duplicate);
                    this.scheduleJobImport(jobs, index + 1, imported, failed + 1, skipped, duplicate, 0, total, MULTI_COOKIE_IMPORT_DELAY_MS, handler);
                    return;
                }

                boolean wasDuplicate = this.storeImportedAccount(account);
                this.batchAccountName = account.name();
                int nextDuplicate = duplicate + (wasDuplicate ? 1 : 0);
                this.updateBatchProgress(number, imported + 1, failed, skipped, nextDuplicate);
                if (jobs.size() == 1) {
                    // One account should enter the session, matching Add / provider import.
                    this.minecraft.execute(() -> {
                        if (this.closed || this != this.currentScreen()) {
                            return;
                        }
                        this.closed = true;
                        this.handler.accept(account);
                    });
                }
                this.scheduleJobImport(jobs, index + 1, imported + 1, failed, skipped, nextDuplicate, 0, total, MULTI_COOKIE_IMPORT_DELAY_MS, handler);
            } catch (Throwable t) {
                this.abortImport("Cookie batch import crashed at account " + number + "/" + jobCount, t);
            }
        }, IAS.executor());
    }

    /**
     * Schedules the next batch job (or the same one for a rate-limit retry).
     */
    private void scheduleJobImport(List<ImportJob> jobs, int index, int imported, int failed, int skipped, int duplicate,
                                   int rateLimitRetries, int total, long delayMs, CreateHandler handler) {
        this.pendingJobIndex = index < jobs.size() ? index + 1 : 0;
        if (index >= jobs.size()) {
            this.importJobs(jobs, index, imported, failed, skipped, duplicate, rateLimitRetries, total, handler);
            return;
        }
        CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS, IAS.executor()).execute(() -> {
            try {
                this.importJobs(jobs, index, imported, failed, skipped, duplicate, rateLimitRetries, total, handler);
            } catch (Throwable t) {
                this.abortImport("Cookie batch import crashed at account " + (index + 1) + "/" + jobs.size(), t);
            }
        });
    }

    /**
     * Skips the account currently being imported (or the next one, if between accounts).
     */
    private void skipCurrentAccount() {
        if (!this.importing) {
            return;
        }
        long now = System.nanoTime();
        if (now - this.lastSkipNanos < TimeUnit.MILLISECONDS.toNanos(MULTI_COOKIE_SKIP_CLICK_COOLDOWN_MS)) {
            return;
        }
        this.lastSkipNanos = now;

        CompletableFuture<MicrosoftAccount> future = this.activeRecordFuture;
        if (future != null && !future.isDone()) {
            AtomicBoolean cancelled = this.activeRecordCancelled;
            if (cancelled != null) {
                cancelled.set(true);
            }
            future.completeExceptionally(new ImportSkippedException());
            return;
        }

        // Between accounts (including a rate-limit delay): skip the job that is scheduled next.
        if (this.pendingJobIndex > 0) {
            this.skipRequestedIndex = this.pendingJobIndex;
        }
    }

    /**
     * Whether the error was caused by the user skipping the account.
     */
    private static boolean isSkipped(Throwable error) {
        Throwable current = error;
        for (int depth = 0; current != null && depth < 16; depth++, current = current.getCause()) {
            if (current instanceof ImportSkippedException) {
                return true;
            }
        }
        return false;
    }

    /**
     * Converts a per-record timeout into a user-facing connection failure.
     */
    private Throwable normalizeTimeout(Throwable error, String label) {
        Throwable current = error;
        for (int depth = 0; current != null && depth < 16; depth++, current = current.getCause()) {
            if (current instanceof TimeoutException) {
                return new FriendlyException("Timed out importing " + label, error, "ias.error.connect");
            }
        }
        return error;
    }

    /**
     * Stops a batch import after an unexpected failure and shows the error instead of staying on the last stage.
     */
    private void abortImport(String message, Throwable t) {
        LOGGER.error("IAS: {}", message, t);
        try {
            this.error(t);
        } catch (Throwable inner) {
            LOGGER.error("IAS: Unable to display the import failure.", inner);
        }
    }

    private static String sourceName(String source) {
        try {
            Path file = Path.of(unwrapPath(source)).getFileName();
            return file != null ? file.toString() : source;
        } catch (Throwable t) {
            return source;
        }
    }

    /**
     * Reads an import file, rejecting non-regular files (FIFOs/devices) so a bad path
     * cannot block the import thread forever.
     */
    private static String readImportText(String source) throws java.io.IOException {
        Path path = Path.of(unwrapPath(source));
        if (!Files.isRegularFile(path)) {
            throw new java.io.IOException("Not a regular file: " + path);
        }
        return CookieParser.readText(path);
    }

    /**
     * Detects the account type for the status display.
     *
     * @param record Account source text (cookie export or token)
     * @return Translation key for the type
     */
    private static String detectType(String record) {
        String lower = record.toLowerCase(Locale.ROOT);
        if (lower.contains("__host-msaauth") || lower.contains("mspauth=") || lower.contains("\tmspauth\t")
                || lower.contains("\tmspauthp\t") || lower.contains("mspprof=")) {
            return "ias.cookie.batch.type.cookie";
        }
        List<String> tokens = TokenImporter.extractValues(record);
        if (tokens.size() == 1) {
            String token = tokens.get(0);
            if (looksLikeMinecraftAccessToken(token)) {
                return "ias.cookie.batch.type.access";
            }
            if (token.startsWith("M.") || token.startsWith("0.") || token.length() > 40) {
                return "ias.cookie.batch.type.refresh";
            }
        }
        return "ias.cookie.batch.type.unknown";
    }

    /**
     * Extracts a best-effort username/email for the status display.
     *
     * @param record Account source text (cookie export or token)
     * @return Username or email, or {@code null} if it cannot be detected cheaply
     */
    private static String usernameHint(String record) {
        String cookieName = cookieValue(record, "JSH");
        if (cookieName == null || cookieName.isBlank()) {
            cookieName = cookieValue(record, "JSHP");
        }
        if (cookieName != null && !cookieName.isBlank()) {
            String[] fields = cookieName.split("\\$", -1);
            if (fields.length > 2 && !fields[2].isBlank()) {
                return fields[2].strip();
            }
            if (fields.length > 1 && !fields[1].isBlank()) {
                return decodeUrl(fields[1].strip());
            }
        }
        String pre = cookieValue(record, "MSPPre");
        if (pre != null && !pre.isBlank()) {
            String value = decodeUrl(pre);
            int sep = value.indexOf('|');
            if (sep > 0) {
                value = value.substring(0, sep);
            }
            value = value.strip();
            if (!value.isBlank()) {
                return value;
            }
        }

        for (String line : record.split("\n")) {
            String stripped = line.strip();
            String lower = stripped.toLowerCase(Locale.ROOT);
            if (lower.startsWith("email:") || lower.startsWith("username:") || lower.startsWith("user:")
                    || lower.startsWith("login:") || lower.startsWith("account:")) {
                String rest = stripped.substring(stripped.indexOf(':') + 1);
                // Localts lines use the same "name:token" shape; never treat their token as a username.
                if (TokenImporter.looksLikeImportToken(rest.strip())) {
                    continue;
                }
                String value = cleanMetadataUsername(rest);
                if (value != null) {
                    return value;
                }
            }
        }
        for (String line : record.split("\n")) {
            String stripped = line.strip();
            if (stripped.toLowerCase(Locale.ROOT).startsWith("source:")) {
                String rest = stripped.substring(stripped.indexOf(':') + 1);
                if (TokenImporter.looksLikeImportToken(rest.strip())) {
                    continue;
                }
                String value = cleanMetadataUsername(rest);
                // A checker "Source:" may be an email:password pair, a seller name, or a path.
                // Only trust it when an email address is actually present.
                if (value != null && value.contains("@")) {
                    return value;
                }
            }
        }

        for (String line : record.split("\n")) {
            String stripped = line.strip();
            String lower = stripped.toLowerCase(Locale.ROOT);
            for (String prefix : new String[]{"# username:", "# user:", "# email:", "# login:", "# account:"}) {
                if (lower.startsWith(prefix)) {
                    String rest = stripped.substring(prefix.length());
                    if (TokenImporter.looksLikeImportToken(rest.strip())) {
                        continue;
                    }
                    String value = cleanMetadataUsername(rest);
                    if (value != null) {
                        return value;
                    }
                }
            }
        }

        // Localts-style "username:M.C..." or "email:token" prefix.
        for (String line : record.split("\n")) {
            String stripped = line.strip();
            String lower = stripped.toLowerCase(Locale.ROOT);
            if (stripped.isEmpty() || stripped.startsWith("#") || stripped.startsWith("//")
                    || lower.contains("localts") || lower.contains("thanks for choosing")) {
                continue;
            }
            int sep = stripped.indexOf(':');
            if (sep > 0 && sep <= 64) {
                String prefix = stripped.substring(0, sep).strip();
                String rest = stripped.substring(sep + 1).strip();
                if (!prefix.isEmpty() && !prefix.contains(" ") && !prefix.contains("\t")
                        && !prefix.contains("/") && !prefix.contains("\\")
                        && TokenImporter.looksLikeImportToken(rest)) {
                    return prefix;
                }
            }
            break;
        }

        List<String> tokens = TokenImporter.extractValues(record);
        for (String token : tokens) {
            String username = tokenUsername(token);
            if (username != null && !username.isBlank()) {
                return username;
            }
        }
        return null;
    }

    /**
     * Cleans a checker metadata value (for example {@code email:password}) into a display name,
     * dropping passwords and paths.
     */
    private static String cleanMetadataUsername(String raw) {
        String value = raw.strip();
        int cut = value.indexOf('|');
        if (cut < 0) {
            cut = value.indexOf('\t');
        }
        if (cut > 0) {
            value = value.substring(0, cut).strip();
        }
        // Metadata is often "username:password" (checker exports). Never display the secret part.
        int colon = value.indexOf(':');
        if (colon > 0) {
            value = value.substring(0, colon).strip();
        }
        if (value.isBlank() || value.length() > 64 || value.contains("/") || value.contains("\\")) {
            return null;
        }
        return value;
    }

    /**
     * Reads a cookie value from Netscape TSV or a semicolon-separated Cookie header.
     *
     * @param text Cookie text
     * @param name Cookie name
     * @return Cookie value, or {@code null} if absent
     */
    private static String cookieValue(String text, String name) {
        for (String line : text.split("\n")) {
            String stripped = line.strip();
            if (stripped.regionMatches(true, 0, "#HttpOnly_", 0, 10)) {
                stripped = stripped.substring(10).strip();
            } else if (stripped.startsWith("#")) {
                continue;
            }
            if (stripped.isEmpty()) {
                continue;
            }
            if (stripped.contains("\t")) {
                String[] parts = stripped.split("\t", -1);
                if (parts.length >= 6 && parts[5].equalsIgnoreCase(name)) {
                    return parts.length >= 7 ? parts[6] : "";
                }
                continue;
            }
            for (String segment : stripped.split(";")) {
                String part = segment.strip();
                int eq = part.indexOf('=');
                if (eq > 0 && part.substring(0, eq).strip().equalsIgnoreCase(name)) {
                    return part.substring(eq + 1).strip();
                }
            }
        }
        return null;
    }

    private static String decodeUrl(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return value;
        }
    }

    private static String tokenUsername(String token) {
        return looksLikeMinecraftAccessToken(token) ? jwtUsername(token) : null;
    }

    /**
     * Extracts the Minecraft profile name from an access token JWT payload.
     */
    private static String jwtUsername(String token) {
        int first = token.indexOf('.');
        if (first < 0) {
            return null;
        }
        int second = token.indexOf('.', first + 1);
        if (second < 0) {
            return null;
        }
        String payload = token.substring(first + 1, second);
        if (payload.isEmpty()) {
            return null;
        }
        try {
            int padding = (4 - payload.length() % 4) % 4;
            if (padding > 0) {
                payload += "=".repeat(padding);
            }
            byte[] decoded = Base64.getUrlDecoder().decode(payload);
            Matcher matcher = JWT_NAME.matcher(new String(decoded, StandardCharsets.UTF_8));
            return matcher.find() ? matcher.group(1) : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean looksLikeMinecraftAccessToken(String token) {
        if (token == null || !token.startsWith("eyJ")) {
            return false;
        }
        int firstDot = token.indexOf('.');
        return firstDot > 3 && token.indexOf('.', firstDot + 1) > firstDot + 1;
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

    private void finishCookieFileImport(int imported, int failed, int skipped, int duplicate, int total) {
        assert this.minecraft != null;
        this.minecraft.execute(() -> {
            if (this.closed) {
                return;
            }
            this.importing = false;
            this.selectedCookieFiles = List.of();
            this.showFailures = !this.failures.isEmpty();
            this.failureScroll = 0;
            this.activeRecordFuture = null;
            this.activeRecordCancelled = null;
            this.skipRequestedIndex = 0;
            this.pendingJobIndex = 0;
            this.batchJob = null;
            this.batchAccountName = null;
            this.batchTotal = 0;
            this.importStartedNanos = 0L;
            synchronized (this.lock) {
                String key = skipped > 0 ? "ias.cookie.multi.done.skipped" : "ias.cookie.multi.done";
                this.stage = Component.translatable(key, imported, total, failed, duplicate, skipped).withStyle(failed == 0 ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
                this.label = null;
                this.error = 0.0F;
            }
            AccountScreen.refreshFrom(this.parent);
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
                    text = readImportText(source);
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
                    addJobs(jobs, Component.translatable("ias.cookie.account", i + 1).getString(), records.get(i));
                }
                this.beginBatch(jobs.size());
                this.refreshBatchWidgets();
                this.importJobs(jobs, 0, 0, 0, 0, 0, 0, jobs.size(), createHandler);
                return;
            }

            List<String> tokens = TokenImporter.extractValues(text);
            if (tokens.size() > 1) {
                List<ImportJob> jobs = new ArrayList<>(tokens.size());
                for (int i = 0; i < tokens.size(); i++) {
                    String token = tokens.get(i);
                    jobs.add(new ImportJob(Component.translatable("ias.cookie.account", i + 1).getString(), token, detectType(token), tokenUsername(token)));
                }
                this.beginBatch(jobs.size());
                this.refreshBatchWidgets();
                this.importJobs(jobs, 0, 0, 0, 0, 0, 0, jobs.size(), createHandler);
                return;
            }

            this.startCreateSingleSource(text, createHandler);
        } catch (Throwable t) {
            createHandler.error(t);
        }
    }

    /**
     * Creates one account from one already-split record. Batch jobs must use this instead of
     * {@link #startCreateFromSource} so a record can never start a nested batch that finishes
     * the outer batch on its own.
     */
    private void startCreateSingleSource(String text, CreateHandler createHandler) {
        try {
            if (this.closed) {
                return;
            }

            createHandler.stage(MicrosoftAccount.COOKIES_TO_MSA_MSR);

            final CookieParser.ParsedCookies cookies;
            try {
                cookies = CookieParser.fromText(text);
            } catch (FriendlyException cookieError) {
                if (!"ias.error.cookie.invalid".equals(cookieError.key())) {
                    createHandler.error(cookieError);
                    return;
                }
                if (this.closed) {
                    return;
                }
                TokenImporter.importText(this.crypt, text, createHandler);
                return;
            }

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
        } catch (Throwable t) {
            createHandler.error(t);
        }
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
        CookieFileDialogs.runAsync(() -> {
            try {
                List<String> paths = CookieFileDialogs.pickCookieFiles(dialogTitle, startPath);
                if (paths.isEmpty() || this.closed) {
                    return;
                }

                this.minecraft.execute(() -> {
                    if (this.closed || this != this.currentScreen()) {
                        return;
                    }
                    this.applySelectedFiles(paths);
                });
            } catch (Throwable t) {
                LOGGER.warn("IAS: Cookie file browse failed.", t);
                this.minecraft.execute(() -> {
                    if (this.closed || this != this.currentScreen()) {
                        return;
                    }
                    this.openInGameBrowser(startPath);
                });
            }
        });
    }

    private void openInGameBrowser(String startPath) {
        if (this.closed) {
            return;
        }
        assert this.minecraft != null;
        Screen browser = new FileBrowserPopupScreen(this,
                Component.translatable("ias.cookie.browse"),
                startPath,
                null,
                this::applySelectedFiles);
        //$set_screen 'this.minecraft' 'browser'
        this.minecraft.gui.setScreen(browser);
    }

    private void applySelectedFiles(List<String> paths) {
        if (paths == null || paths.isEmpty() || this.closed) {
            return;
        }
        this.selectedCookieFiles = List.copyOf(paths);
        this.savedPath = paths.size() == 1
                ? paths.get(0)
                : Component.translatable("ias.cookie.selected", paths.size()).getString();
        if (this.pathInput != null) {
            this.pathInput.setValue(this.savedPath);
        }
    }

    Screen parent() {
        return this.parent;
    }

    @Override
    public void onClose() {
        assert this.minecraft != null;
        this.closed = true;
        this.importing = false;
        this.errored = false;
        this.skipRequestedIndex = 0;
        this.pendingJobIndex = 0;
        this.activeRecordFuture = null;
        this.activeRecordCancelled = null;
        this.importStartedNanos = 0L;
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
            if (this.importing && this.batchTotal > 1) {
                this.renderBatchStatus(graphics);
            } else {
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

    /**
     * Renders the live batch status: account number, elapsed times, type/user and counters.
     */
    //? if >=26.1 {
    private void renderBatchStatus(GuiGraphicsExtractor graphics) {
    //?} else
    /*private void renderBatchStatus(GuiGraphics graphics) {*/
        List<Component> lines = this.batchStatusLines();
        List<MultiLineLabel> labels = new ArrayList<>(lines.size());
        int totalHeight = 0;
        for (Component line : lines) {
            MultiLineLabel label = MultiLineLabel.create(this.font, line, STATUS_WIDTH);
            labels.add(label);
            totalHeight += label.getLineCount() * 9 + 2;
        }
        int y = (this.height - totalHeight) / 2 - 6;
        for (MultiLineLabel label : labels) {
            IStonecutter.renderMultilineLabelCentered(label, graphics, this.width / 2, y);
            y += label.getLineCount() * 9 + 2;
        }
    }

    private List<Component> batchStatusLines() {
        List<Component> lines = new ArrayList<>(4);
        long now = System.nanoTime();
        long totalNanos = this.batchStartedNanos > 0L ? now - this.batchStartedNanos : 0L;
        long accountNanos = this.accountStartedNanos > 0L ? now - this.accountStartedNanos : this.accountElapsedNanos;
        int total = Math.max(1, this.batchTotal);
        lines.add(Component.translatable("ias.cookie.batch.progress", Math.max(1, this.batchIndex), total,
                formatElapsed(totalNanos), formatElapsed(accountNanos)).withStyle(ChatFormatting.YELLOW));

        ImportJob job = this.batchJob;
        if (job != null) {
            MutableComponent info = Component.literal(job.label()).withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.translatable(job.typeKey()).withStyle(ChatFormatting.AQUA));
            String username = this.batchAccountName != null && !this.batchAccountName.isBlank()
                    ? this.batchAccountName
                    : job.usernameHint();
            if (username != null && !username.isBlank()) {
                info.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                        .append(Component.translatable("ias.cookie.batch.user", username).withStyle(ChatFormatting.WHITE));
            }
            lines.add(info);
        }

        lines.add(this.stage);
        lines.add(Component.translatable("ias.cookie.batch.counts", this.batchImported, this.batchFailed,
                this.batchDuplicate, this.batchSkipped).withStyle(ChatFormatting.GRAY));
        return lines;
    }

    /**
     * Formats a nanosecond duration as {@code Hh MMm SSs}, {@code Mm SSs} or {@code Ss}.
     */
    private static String formatElapsed(long nanos) {
        long seconds = Math.max(0L, TimeUnit.NANOSECONDS.toSeconds(nanos));
        long minutes = seconds / 60L;
        long rest = seconds % 60L;
        if (minutes >= 60L) {
            long hours = minutes / 60L;
            return String.format(Locale.ROOT, "%dh %02dm %02ds", hours, minutes % 60L, rest);
        }
        return minutes > 0L ? String.format(Locale.ROOT, "%dm %02ds", minutes, rest) : rest + "s";
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
            if (this.importing && this.batchTotal > 1) {
                this.minecraft.getNarrator().saySystemQueued(component);
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
            AtomicBoolean cancelled = this.activeRecordCancelled;
            if (cancelled != null) {
                cancelled.set(true);
            }
            synchronized (this.lock) {
                this.stage = component;
                this.label = null;
                this.error = 0.0F;
            }
            this.importing = false;
            this.errored = true;
            this.showFailures = false;
            this.batchTotal = 0;
            this.batchJob = null;
            this.batchAccountName = null;
            this.skipRequestedIndex = 0;
            this.pendingJobIndex = 0;
            this.activeRecordFuture = null;
            this.activeRecordCancelled = null;
            this.accountStartedNanos = 0L;
            this.accountElapsedNanos = 0L;
            this.importStartedNanos = 0L;
            //? if >=1.21.11 {
            this.init(this.width, this.height);
            //?} else
            /*this.init(this.minecraft, this.width, this.height);*/
        });
    }

    private record ImportJob(String label, String source, String typeKey, String usernameHint) {
    }

    private record ImportFailure(String label, Component reason) {
    }

    /**
     * Marker error used when the user skips the current account.
     */
    private static final class ImportSkippedException extends RuntimeException {
        private ImportSkippedException() {
            super("Import skipped by user.", null, false, false);
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
        return "CookiePopupScreen{}";
    }
}
