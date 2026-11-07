package the_fireplace.ias.gui;

import com.github.mrebhan.ingameaccountswitcher.MR;
import com.github.mrebhan.ingameaccountswitcher.tools.Config;
import com.github.mrebhan.ingameaccountswitcher.tools.alt.AltDatabase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.Session;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import ru.vidtu.iasfork.cookie.CookieAuth;
import ru.vidtu.iasfork.cookie.CookieAuthException;
import ru.vidtu.iasfork.cookie.CookieParser;
import the_fireplace.ias.account.ExtendedAccountData;
import the_fireplace.ias.enums.EnumBool;

import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cookie / Localts import screen for 1.8.9.
 */
public class GuiCookieImport extends GuiScreen {
    private static final int SKIP_BUTTON_ID = 14;
    private static final long SKIP_CLICK_COOLDOWN_NANOS = 750_000_000L;
    private static final Pattern JWT_NAME = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]{1,64})\"");

    private final GuiScreen parent;
    private final boolean returnToAccountListOnSuccess;
    private boolean pasteMode;
    private volatile boolean importing;
    private volatile boolean closed;
    private GuiTextField pathInput;
    private GuiTextField pasteInput;
    private String savedPath = "";
    private String savedPaste = "";
    /** Files picked via Browse (multi-select). Empty = parse pathInput text. */
    private final List<String> selectedFiles = new ArrayList<String>();
    private final List<String> statusLines = new ArrayList<String>();

    // Live batch state, written by the import worker and read by the render thread.
    private volatile int batchTotal;
    private volatile int batchIndex;
    private volatile int batchImported;
    private volatile int batchFailed;
    private volatile int batchSkipped;
    private volatile int batchDuplicate;
    private volatile long batchStartedNanos;
    private volatile long accountStartedNanos;
    private volatile long accountElapsedNanos;
    private volatile String batchLabel = "";
    private volatile String batchType = "ias.cookie.batch.type.unknown";
    private volatile String batchUser = "";
    private volatile boolean skipRequested;
    private volatile long lastSkipNanos;
    private volatile Thread importThread;

    // Written by import worker threads, read by the render thread.
    private final List<String> failures = new CopyOnWriteArrayList<String>();
    private final List<String> failureDisplayLines = new ArrayList<String>();
    private boolean showFailures;
    private int failureScroll;

    public GuiCookieImport(GuiScreen parent) {
        this(parent, false);
    }

    public GuiCookieImport(GuiScreen parent, boolean returnToAccountListOnSuccess) {
        this.parent = parent;
        this.returnToAccountListOnSuccess = returnToAccountListOnSuccess;
    }

    @Override
    public void initGui() {
        // displayGuiScreen always calls onGuiClosed, which marks the screen closed.
        // Showing it again (file browser return, resize) must accept further input.
        this.closed = false;
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        int centerX = width / 2;
        int centerY = height / 2;

        if (showFailures) {
            pathInput = null;
            pasteInput = null;
            rebuildFailureLines();
            buttonList.add(new GuiButton(3, centerX - 75, centerY + 72, 150, 20, I18n.format("gui.back")));
            return;
        }

        if (importing) {
            pathInput = null;
            pasteInput = null;
            if (batchTotal > 1) {
                buttonList.add(new GuiButton(SKIP_BUTTON_ID, centerX - 75, centerY + 46, 150, 20,
                        I18n.format("ias.cookie.batch.skip")));
            }
            buttonList.add(new GuiButton(3, centerX - 75, centerY + 72, 150, 20, I18n.format("gui.back")));
            return;
        }

        buttonList.add(new GuiButton(10, centerX - 77, centerY - 38, 74, 20, I18n.format("ias.cookie.path")));
        buttonList.add(new GuiButton(11, centerX + 3, centerY - 38, 74, 20, I18n.format("ias.cookie.paste")));

        if (pasteMode) {
            pasteInput = new GuiTextField(1, fontRendererObj, centerX - 99, centerY - 12, 198, 60);
            pasteInput.setMaxStringLength(131072);
            pasteInput.setFocused(true);
            if (!savedPaste.isEmpty()) {
                pasteInput.setText(savedPaste);
            }
        } else {
            pathInput = new GuiTextField(0, fontRendererObj, centerX - 99, centerY - 12, 178, 20);
            pathInput.setMaxStringLength(4096);
            pathInput.setFocused(true);
            if (!savedPath.isEmpty()) {
                pathInput.setText(savedPath);
            }
            buttonList.add(new GuiButton(12, centerX + 81, centerY - 12, 20, 20, "..."));
        }

        buttonList.add(new GuiButton(13, centerX - 75, pasteMode ? centerY + 50 : centerY + 40, 150, 20, I18n.format("ias.cookie.import")));
        buttonList.add(new GuiButton(3, centerX - 75, centerY + 72, 150, 20, I18n.format("gui.back")));
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        closed = true;
        Thread worker = importThread;
        if (worker != null) {
            worker.interrupt();
        }
        super.onGuiClosed();
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (!button.enabled) {
            return;
        }
        if (importing && button.id != 3 && button.id != SKIP_BUTTON_ID) {
            return;
        }
        if (button.id == 3) {
            mc.displayGuiScreen(parent);
        } else if (button.id == 10) {
            setPasteMode(false);
        } else if (button.id == 11) {
            setPasteMode(true);
        } else if (button.id == 12) {
            browseCookieFile();
        } else if (button.id == 13) {
            importCookies();
        } else if (button.id == SKIP_BUTTON_ID) {
            skipCurrentAccount();
        }
    }

    private void setPasteMode(boolean paste) {
        if (pathInput != null) {
            savedPath = pathInput.getText();
        }
        if (pasteInput != null) {
            savedPaste = pasteInput.getText();
        }
        if (paste && !pasteMode && savedPaste.isEmpty()) {
            savedPaste = clipboardContents();
        }
        pasteMode = paste;
        statusLines.clear();
        initGui();
    }

    private void browseCookieFile() {
        final String start = pathInput != null ? pathInput.getText().trim() : savedPath.trim();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    List<String> paths = CookieFileDialogs.pickFiles(I18n.format("ias.cookie.browse"), start);
                    if (paths.isEmpty() || closed) {
                        return;
                    }
                    Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                        @Override
                        public void run() {
                            applySelectedFiles(paths);
                        }
                    });
                } catch (Throwable t) {
                    Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                        @Override
                        public void run() {
                            if (closed) {
                                return;
                            }
                            openInGameBrowser(start);
                        }
                    });
                }
            }
        }, "IAS/CookieBrowse").start();
    }

    private void openInGameBrowser(String start) {
        mc.displayGuiScreen(new GuiFileBrowser(this, start, null, true, new GuiFileBrowser.Callback() {
            @Override
            public void onChosen(List<String> paths) {
                applySelectedFiles(paths);
            }
        }));
    }

    private void applySelectedFiles(List<String> paths) {
        if (paths == null || paths.isEmpty() || closed) {
            return;
        }
        selectedFiles.clear();
        selectedFiles.addAll(paths);
        String joined = joinPaths(paths);
        savedPath = joined;
        if (pathInput != null) {
            pathInput.setText(joined);
        }
        statusLines.clear();
        if (paths.size() > 1) {
            statusLines.add(I18n.format("ias.cookie.selected", paths.size()));
        }
    }

    private static String joinPaths(List<String> paths) {
        StringBuilder sb = new StringBuilder();
        for (String p : paths) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(p);
        }
        return sb.toString();
    }

    /** Splits manual `;`/newline-separated input into individual file paths. */
    private static List<String> splitPaths(String raw) {
        List<String> out = new ArrayList<String>();
        if (raw == null) {
            return out;
        }
        for (String part : raw.split("[;\n]+")) {
            String t = part == null ? "" : part.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    public void beginImport(List<String> paths) {
        if (paths == null || paths.isEmpty() || importing) {
            return;
        }
        pasteMode = false;
        selectedFiles.clear();
        selectedFiles.addAll(paths);
        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < paths.size(); i++) {
            if (i > 0) {
                joined.append('\n');
            }
            joined.append(paths.get(i));
        }
        savedPath = joined.toString();
        if (pathInput != null) {
            pathInput.setText(savedPath);
        }
        importCookies();
    }

    /**
     * Imports raw Localts payloads (tokens or cookie text) with the same spacing,
     * rate-limit retries, and failure list as a multi-file import.
     *
     * @param payloads Account texts, one delivery per entry
     */
    public void beginPayloads(List<String> payloads) {
        if (payloads == null || payloads.isEmpty() || importing) {
            return;
        }
        importing = true;
        showFailures = false;
        failureScroll = 0;
        failures.clear();
        failureDisplayLines.clear();
        statusLines.clear();
        statusLines.add(I18n.format("ias.login.cookiesToMsaMsr"));
        final List<PayloadJob> jobs = new ArrayList<PayloadJob>();
        for (String payload : payloads) {
            if (payload == null || payload.trim().isEmpty()) {
                continue;
            }
            List<String> tokens = ru.vidtu.iasfork.cookie.TokenImporter.extractValues(payload);
            if (tokens.isEmpty()) {
                jobs.add(new PayloadJob(false, payload, detectType(payload), usernameHint(payload)));
            } else {
                String payloadUser = usernameHint(payload);
                for (String token : tokens) {
                    String user = payloadUser.isEmpty() ? tokenUsername(token) : payloadUser;
                    jobs.add(new PayloadJob(true, token, detectType(token), user));
                }
            }
        }
        if (jobs.isEmpty()) {
            importing = false;
            showError(I18n.format("ias.error.cookie.invalid"));
            initGui();
            return;
        }
        beginBatch(jobs.size());
        postStatus(I18n.format("ias.cookie.multi.progress", 1, jobs.size()));
        initGui();
        new Thread(new Runnable() {
            @Override
            public void run() {
                importThread = Thread.currentThread();
                try {
                    importPayloads(jobs);
                } catch (Throwable t) {
                    // An uncaught exception would silently kill this thread and leave the screen
                    // stuck on the last status line with importing == true.
                    failImport(t);
                } finally {
                    importThread = null;
                }
            }
        }, "IAS/LocaltsImport").start();
    }

    /** Shows an unexpected worker-thread failure on the game thread and unlocks the screen. */
    private void failImport(final Throwable t) {
        t.printStackTrace();
        try {
            Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    // Unlock first: this screen instance is reused after the file browser closes it.
                    importing = false;
                    resetBatch();
                    if (closed) {
                        return;
                    }
                    showError(formatError(t));
                    initGui();
                }
            });
        } catch (Throwable ignored) {
            importing = false;
            resetBatch();
        }
    }

    private void importPayloads(List<PayloadJob> jobs) {
        final List<CookieAuth.MinecraftProfile> ok = new ArrayList<CookieAuth.MinecraftProfile>();
        final int total = jobs.size();
        int skipped = 0;
        for (int i = 0; i < total; i++) {
            if (closed) {
                return;
            }
            if (consumeSkip()) {
                skipped++;
                batchSkipped = skipped;
                PayloadJob skippedJob = jobs.get(i);
                beginAccount(i, "Localts #" + (i + 1), skippedJob.type, skippedJob.user);
                accountStartedNanos = 0L;
                accountElapsedNanos = 0L;
                postStatus(I18n.format("ias.cookie.batch.skipped", i + 1, total));
                skipDelay();
                continue;
            }
            final int idx = i;
            PayloadJob job = jobs.get(idx);
            beginAccount(idx, "Localts #" + (idx + 1), job.type, job.user);
            postStatus(I18n.format("ias.cookie.multi.progress", idx + 1, total));
            CookieAuth.MinecraftProfile profile = null;
            Throwable lastError = null;
            int rateLimitRetries = 0;
            boolean skippedJob = false;
            while (profile == null) {
                if (closed) {
                    return;
                }
                if (consumeSkip()) {
                    skippedJob = true;
                    break;
                }
                try {
                    if (job.token) {
                        profile = ru.vidtu.iasfork.cookie.TokenImporter.importSingleToken(job.value);
                    } else {
                        profile = CookieAuth.authenticate(CookieParser.fromText(job.value));
                    }
                } catch (Throwable t) {
                    if (consumeSkip()) {
                        skippedJob = true;
                        break;
                    }
                    if (isRateLimited(t) && rateLimitRetries < 2) {
                        rateLimitRetries++;
                        postStatus(I18n.format("ias.error.rateLimited"));
                        try {
                            Thread.sleep(30000L);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            if (closed) {
                                return;
                            }
                            if (consumeSkip()) {
                                skippedJob = true;
                                break;
                            }
                            return;
                        }
                        continue;
                    }
                    lastError = t;
                    break;
                }
            }
            if (consumeSkip()) {
                skippedJob = true;
            }
            if (skippedJob) {
                skipped++;
                batchSkipped = skipped;
                batchIndex = idx + 1;
                postStatus(I18n.format("ias.cookie.batch.skipped", idx + 1, total));
            } else if (profile != null) {
                ok.add(profile);
                batchImported = ok.size();
                batchUser = profile.name;
                postStatus(I18n.format("ias.cookie.batch.imported", profile.name));
            } else {
                failures.add(EnumChatFormatting.RED + ("Account " + (idx + 1)) + EnumChatFormatting.GRAY + ": "
                        + formatError(lastError != null ? lastError : new Exception(I18n.format("ias.error.cookie.invalid"))));
                batchFailed = failures.size();
                postStatus(I18n.format("ias.cookie.batch.failed"));
            }
            if (accountStartedNanos > 0L) {
                accountElapsedNanos = System.nanoTime() - accountStartedNanos;
                accountStartedNanos = 0L;
            }
            if (idx + 1 < total) {
                try {
                    Thread.sleep(6000L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    if (closed) {
                        return;
                    }
                    if (!skipRequested) {
                        return;
                    }
                }
            }
        }
        if (closed) {
            return;
        }
        final List<CookieAuth.MinecraftProfile> done = ok;
        final int skippedCount = skipped;
        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            @Override
            public void run() {
                if (closed) {
                    return;
                }
                try {
                    int dup = 0;
                    for (CookieAuth.MinecraftProfile p : done) {
                        if (saveAccount(p)) {
                            dup++;
                        }
                    }
                    batchDuplicate = dup;
                    CookieAuth.MinecraftProfile last = done.isEmpty() ? null : done.get(done.size() - 1);
                    Config.save();
                    if (last != null) {
                        MR.setSession(new Session(last.name, last.uuid, last.token, "mojang"));
                    }
                    if (last != null && failures.isEmpty() && done.size() == 1 && skippedCount == 0) {
                        importing = false;
                        resetBatch();
                        mc.displayGuiScreen(returnToAccountListOnSuccess ? new GuiAccountSelector() : parent);
                        return;
                    }
                    statusLines.clear();
                    int reportFailed = failures.size();
                    String key = skippedCount > 0 ? "ias.cookie.multi.done.skipped" : "ias.cookie.multi.done";
                    statusLines.add(I18n.format(key, done.size(), done.size() + reportFailed, reportFailed, dup, skippedCount));
                    importing = false;
                    showFailures = !failures.isEmpty();
                    failureScroll = 0;
                    resetBatch();
                    initGui();
                } catch (Throwable t) {
                    showError(formatError(t));
                    importing = false;
                    resetBatch();
                    initGui();
                }
            }
        });
    }

    private void postStatus(final String line) {
        try {
            Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    if (!closed) {
                        statusLines.clear();
                        statusLines.add(line);
                    }
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private static final class PayloadJob {
        private final boolean token;
        private final String value;
        private final String type;
        private final String user;

        private PayloadJob(boolean token, String value, String type, String user) {
            this.token = token;
            this.value = value;
            this.type = type;
            this.user = user == null ? "" : user;
        }
    }

    /**
     * Signals that the user skipped a multi-record file while it was being imported.
     */
    private static final class ImportSkippedException extends Exception {
        private ImportSkippedException() {
            super("Import skipped by user.", null, false, false);
        }
    }

    private void beginBatch(int total) {
        batchStartedNanos = System.nanoTime();
        batchTotal = Math.max(0, total);
        batchIndex = 0;
        batchImported = 0;
        batchFailed = 0;
        batchSkipped = 0;
        batchDuplicate = 0;
        accountStartedNanos = 0L;
        accountElapsedNanos = 0L;
        batchLabel = "";
        batchType = "ias.cookie.batch.type.unknown";
        batchUser = "";
        skipRequested = false;
        lastSkipNanos = 0L;
    }

    private void beginAccount(int index, String label, String type, String user) {
        batchIndex = index + 1;
        batchLabel = label == null ? "" : label;
        batchType = type == null || type.isEmpty() ? "ias.cookie.batch.type.unknown" : type;
        batchUser = user == null ? "" : user;
        accountStartedNanos = System.nanoTime();
        accountElapsedNanos = 0L;
    }

    private void setBatchInfo(String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        String type = detectType(text);
        if (type != null && !type.isEmpty()) {
            batchType = type;
        }
        String user = usernameHint(text);
        if (user != null && !user.isEmpty()) {
            batchUser = user;
        }
    }

    private void skipCurrentAccount() {
        if (!importing) {
            return;
        }
        long now = System.nanoTime();
        if (now - lastSkipNanos < SKIP_CLICK_COOLDOWN_NANOS) {
            return;
        }
        lastSkipNanos = now;
        skipRequested = true;
        Thread worker = importThread;
        if (worker != null) {
            worker.interrupt();
        }
    }

    private boolean consumeSkip() {
        if (skipRequested) {
            skipRequested = false;
            // Clear the interrupt raised by the Skip button; it must not abort the whole batch.
            Thread.interrupted();
            return true;
        }
        return false;
    }

    /**
     * Brief pause so the "Skipped account" status is visible before the next job starts.
     */
    private void skipDelay() {
        try {
            Thread.sleep(1000L);
        } catch (InterruptedException ie) {
            if (closed || skipRequested) {
                Thread.currentThread().interrupt();
            } else {
                Thread.interrupted();
            }
        }
    }

    private void resetBatch() {
        batchTotal = 0;
        batchIndex = 0;
        batchImported = 0;
        batchFailed = 0;
        batchSkipped = 0;
        batchDuplicate = 0;
        batchStartedNanos = 0L;
        accountStartedNanos = 0L;
        accountElapsedNanos = 0L;
        batchLabel = "";
        batchType = "ias.cookie.batch.type.unknown";
        batchUser = "";
        skipRequested = false;
        importThread = null;
    }

    private static Path importPath(String source) {
        String value = source == null ? "" : source.trim();
        if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
            value = value.substring(1, value.length() - 1).trim();
        }
        return Paths.get(value);
    }

    private static String formatElapsed(long nanos) {
        long seconds = Math.max(0L, nanos / 1_000_000_000L);
        long minutes = seconds / 60L;
        long rest = seconds % 60L;
        if (minutes >= 60L) {
            long hours = minutes / 60L;
            return String.format(Locale.ROOT, "%dh %02dm %02ds", hours, minutes % 60L, rest);
        }
        return minutes > 0L ? String.format(Locale.ROOT, "%dm %02ds", minutes, rest) : rest + "s";
    }

    /**
     * Formats a per-account duration with sub-second precision, so fast accounts don't all show 0s.
     */
    private static String formatAccountElapsed(long nanos) {
        long millis = Math.max(0L, nanos / 1_000_000L);
        if (millis < 1000L) {
            return millis + "ms";
        }
        if (millis < 10000L) {
            return String.format(Locale.ROOT, "%.1fs", millis / 1000.0D);
        }
        return formatElapsed(nanos);
    }

    private static String detectType(String text) {
        if (text == null) {
            return "ias.cookie.batch.type.unknown";
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("__host-msaauth") || lower.contains("mspauth=") || lower.contains("\tmspauth\t")
                || lower.contains("\tmspauthp\t") || lower.contains("mspprof=")) {
            return "ias.cookie.batch.type.cookie";
        }
        List<String> tokens = ru.vidtu.iasfork.cookie.TokenImporter.extractValues(text);
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

    private static String usernameHint(String text) {
        if (text == null) {
            return "";
        }
        String cookieName = cookieValue(text, "JSH");
        if (cookieName == null || cookieName.trim().isEmpty()) {
            cookieName = cookieValue(text, "JSHP");
        }
        if (cookieName != null && !cookieName.trim().isEmpty()) {
            String[] fields = cookieName.split("\\$", -1);
            if (fields.length > 2 && !fields[2].trim().isEmpty()) {
                return fields[2].trim();
            }
            if (fields.length > 1 && !fields[1].trim().isEmpty()) {
                return decodeUrl(fields[1].trim());
            }
        }
        String pre = cookieValue(text, "MSPPre");
        if (pre != null && !pre.trim().isEmpty()) {
            String value = decodeUrl(pre);
            int sep = value.indexOf('|');
            if (sep > 0) {
                value = value.substring(0, sep);
            }
            value = value.trim();
            if (!value.isEmpty()) {
                return value;
            }
        }

        for (String line : text.split("\n")) {
            String stripped = line.trim();
            String lower = stripped.toLowerCase(Locale.ROOT);
            if (lower.startsWith("email:") || lower.startsWith("username:") || lower.startsWith("user:")
                    || lower.startsWith("login:") || lower.startsWith("account:")) {
                String rest = stripped.substring(stripped.indexOf(':') + 1);
                if (ru.vidtu.iasfork.cookie.TokenImporter.extractValues(rest.trim()).isEmpty()) {
                    String value = cleanMetadataUsername(rest);
                    if (value != null) {
                        return value;
                    }
                }
            }
        }
        for (String line : text.split("\n")) {
            String stripped = line.trim();
            String lower = stripped.toLowerCase(Locale.ROOT);
            if (lower.startsWith("source:")) {
                String rest = stripped.substring(stripped.indexOf(':') + 1);
                if (!ru.vidtu.iasfork.cookie.TokenImporter.extractValues(rest.trim()).isEmpty()) {
                    continue;
                }
                String value = cleanMetadataUsername(rest);
                if (value != null && value.contains("@")) {
                    return value;
                }
            }
        }
        for (String line : text.split("\n")) {
            String stripped = line.trim();
            String lower = stripped.toLowerCase(Locale.ROOT);
            for (String prefix : new String[]{"# username:", "# user:", "# email:", "# login:", "# account:"}) {
                if (lower.startsWith(prefix)) {
                    String rest = stripped.substring(prefix.length());
                    if (!ru.vidtu.iasfork.cookie.TokenImporter.extractValues(rest.trim()).isEmpty()) {
                        continue;
                    }
                    String value = cleanMetadataUsername(rest);
                    if (value != null) {
                        return value;
                    }
                }
            }
        }

        for (String line : text.split("\n")) {
            String stripped = line.trim();
            String lower = stripped.toLowerCase(Locale.ROOT);
            if (stripped.isEmpty() || stripped.startsWith("#") || stripped.startsWith("//")
                    || lower.contains("localts") || lower.contains("thanks for choosing")) {
                continue;
            }
            int sep = stripped.indexOf(':');
            if (sep > 0 && sep <= 64) {
                String prefix = stripped.substring(0, sep).trim();
                String rest = stripped.substring(sep + 1).trim();
                if (!prefix.isEmpty() && !prefix.contains(" ") && !prefix.contains("\t")
                        && !prefix.contains("/") && !prefix.contains("\\")
                        && !ru.vidtu.iasfork.cookie.TokenImporter.extractValues(rest).isEmpty()) {
                    return prefix;
                }
            }
            break;
        }

        List<String> tokens = ru.vidtu.iasfork.cookie.TokenImporter.extractValues(text);
        for (String token : tokens) {
            String username = tokenUsername(token);
            if (username != null && !username.isEmpty()) {
                return username;
            }
        }
        return "";
    }

    private static String cleanMetadataUsername(String raw) {
        String value = raw == null ? "" : raw.trim();
        int cut = value.indexOf('|');
        if (cut < 0) {
            cut = value.indexOf('\t');
        }
        if (cut > 0) {
            value = value.substring(0, cut).trim();
        }
        int colon = value.indexOf(':');
        if (colon > 0) {
            value = value.substring(0, colon).trim();
        }
        if (value.isEmpty() || value.length() > 64 || value.contains("/") || value.contains("\\")) {
            return null;
        }
        return value;
    }

    private static String cookieValue(String text, String name) {
        for (String line : text.split("\n")) {
            String stripped = line.trim();
            if (stripped.regionMatches(true, 0, "#HttpOnly_", 0, 10)) {
                stripped = stripped.substring(10).trim();
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
                String part = segment.trim();
                int eq = part.indexOf('=');
                if (eq > 0 && part.substring(0, eq).trim().equalsIgnoreCase(name)) {
                    return part.substring(eq + 1).trim();
                }
            }
        }
        return null;
    }

    private static String decodeUrl(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (Throwable t) {
            return value;
        }
    }

    private static String tokenUsername(String token) {
        return looksLikeMinecraftAccessToken(token) ? jwtUsername(token) : "";
    }

    private static String jwtUsername(String token) {
        int first = token.indexOf('.');
        if (first < 0) {
            return "";
        }
        int second = token.indexOf('.', first + 1);
        if (second < 0) {
            return "";
        }
        String payload = token.substring(first + 1, second);
        if (payload.isEmpty()) {
            return "";
        }
        try {
            int padding = (4 - payload.length() % 4) % 4;
            for (int i = 0; i < padding; i++) {
                payload += "=";
            }
            byte[] decoded = Base64.getUrlDecoder().decode(payload);
            Matcher matcher = JWT_NAME.matcher(new String(decoded, StandardCharsets.UTF_8));
            return matcher.find() ? matcher.group(1) : "";
        } catch (Throwable t) {
            return "";
        }
    }

    private static boolean looksLikeMinecraftAccessToken(String token) {
        if (token == null || !token.startsWith("eyJ")) {
            return false;
        }
        int firstDot = token.indexOf('.');
        return firstDot > 3 && token.indexOf('.', firstDot + 1) > firstDot + 1;
    }

    private void importCookies() {
        if (importing) {
            return;
        }
        showFailures = false;
        failureScroll = 0;
        failures.clear();
        failureDisplayLines.clear();
        String raw;
        if (pasteMode) {
            raw = resolvePasteSource();
            if (raw == null || raw.trim().isEmpty()) {
                return;
            }
        } else {
            if (pathInput == null) {
                return;
            }
            raw = pathInput.getText().trim();
            if (raw.isEmpty()) {
                return;
            }
        }
        importing = true;
        if (pasteMode) {
            savedPaste = raw;
        } else {
            savedPath = raw;
        }
        statusLines.clear();
        statusLines.add(I18n.format("ias.login.cookiesToMsaMsr"));
        final String source = raw;
        final boolean fromPath = !pasteMode;
        // Snapshot the multi-file selection; manual edits fall back to parsing the field.
        final List<String> files;
        if (fromPath) {
            List<String> typed = splitPaths(source);
            if (!selectedFiles.isEmpty() && typed.size() == selectedFiles.size()
                    && selectedFiles.containsAll(typed)) {
                files = new ArrayList<String>(selectedFiles);
            } else {
                files = typed;
                selectedFiles.clear();
                selectedFiles.addAll(typed);
            }
        } else {
            files = new ArrayList<String>();
        }
        final boolean multiFile = fromPath && files.size() > 1;
        beginBatch(multiFile ? files.size() : 1);
        if (multiFile) {
            initGui();
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                importThread = Thread.currentThread();
                try {
                    if (fromPath && files.size() > 1) {
                        importMultipleFiles(files);
                        return;
                    }
                    final List<CookieAuth.MinecraftProfile> profiles = new ArrayList<CookieAuth.MinecraftProfile>();
                    if (fromPath) {
                        try {
                            Path file = importPath(source);
                            if (!Files.isRegularFile(file)) {
                                throw new CookieAuthException("Not a regular file: " + file, "ias.error.cookie.file");
                            }
                            CookieParser.ParsedCookies cookies = CookieParser.fromPath(source);
                            if (closed) {
                                return;
                            }
                            profiles.add(CookieAuth.authenticate(cookies));
                        } catch (Throwable cookieFailed) {
                            // Fall back to bare-token file (modern TokenImporter parity).
                            List<String> tokens = readTokenFileFallback(source);
                            if (tokens.isEmpty()) {
                                throw cookieFailed;
                            }
                            importTokensWithDelay(tokens, profiles);
                        }
                    } else {
                        boolean cookieOk = false;
                        try {
                            CookieParser.ParsedCookies cookies = CookieParser.fromText(source);
                            if (closed) {
                                return;
                            }
                            profiles.add(CookieAuth.authenticate(cookies));
                            cookieOk = true;
                        } catch (Throwable ignored) {
                            cookieOk = false;
                        }
                        if (!cookieOk) {
                            if (closed) {
                                return;
                            }
                            List<String> tokens = ru.vidtu.iasfork.cookie.TokenImporter.extractValues(source);
                            if (tokens.isEmpty()) {
                                throw new CookieAuthException(
                                        "Unrecognized cookie format. Use a Netscape cookie file, semicolon-separated cookie header, Localts token, or pasted session/refresh token.",
                                        "ias.error.cookie.invalid");
                            }
                            importTokensWithDelay(tokens, profiles);
                        }
                    }
                    if (closed) {
                        return;
                    }
                    if (profiles.isEmpty()) {
                        throw new CookieAuthException("No accounts were imported.", "ias.error.cookie.invalid");
                    }
                    Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                        @Override
                        public void run() {
                            if (closed) {
                                return;
                            }
                            try {
                                int dup = 0;
                                for (CookieAuth.MinecraftProfile p : profiles) {
                                    if (saveAccount(p)) {
                                        dup++;
                                    }
                                }
                                CookieAuth.MinecraftProfile last = profiles.get(profiles.size() - 1);
                                MR.setSession(new Session(last.name, last.uuid, last.token, "mojang"));
                                Config.save();
                                if (profiles.size() > 1) {
                                    statusLines.clear();
                                    statusLines.add(I18n.format("ias.cookie.multi.done", profiles.size(), profiles.size(), 0, dup));
                                    importing = false;
                                    resetBatch();
                                    initGui();
                                } else {
                                    importing = false;
                                    resetBatch();
                                    mc.displayGuiScreen(returnToAccountListOnSuccess ? new GuiAccountSelector() : parent);
                                }
                            } catch (Throwable t) {
                                showError(formatError(t));
                                importing = false;
                                resetBatch();
                                initGui();
                            }
                        }
                    });
                } catch (Throwable t) {
                    if (!closed) {
                        final Throwable err = t;
                        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                            @Override
                            public void run() {
                                showError(formatError(err));
                                importing = false;
                                resetBatch();
                                initGui();
                            }
                        });
                    }
                } finally {
                    importThread = null;
                }
            }

            private void importTokensWithDelay(List<String> tokens, List<CookieAuth.MinecraftProfile> out) throws Exception {
                Throwable lastError = null;
                int rateLimitRetries = 0;
                for (int i = 0; i < tokens.size(); i++) {
                    if (closed) {
                        return;
                    }
                    if (consumeSkip()) {
                        throw new ImportSkippedException();
                    }
                    try {
                        out.add(ru.vidtu.iasfork.cookie.TokenImporter.importSingleToken(tokens.get(i)));
                        rateLimitRetries = 0;
                    } catch (Throwable t) {
                        if (isRateLimited(t) && rateLimitRetries < 2) {
                            rateLimitRetries++;
                            try {
                                Thread.sleep(30000L);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                if (closed) {
                                    return;
                                }
                                if (consumeSkip()) {
                                    throw new ImportSkippedException();
                                }
                                throw ie;
                            }
                            i--;
                            continue;
                        }
                        lastError = t;
                        // Continue with remaining tokens; single-token case rethrows below.
                        if (tokens.size() == 1) {
                            throw t instanceof Exception ? (Exception) t : new Exception(t);
                        }
                    }
                    if (consumeSkip()) {
                        throw new ImportSkippedException();
                    }
                    if (i + 1 < tokens.size()) {
                        try {
                            Thread.sleep(6000L);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            if (closed) {
                                return;
                            }
                            if (consumeSkip()) {
                                throw new ImportSkippedException();
                            }
                            break;
                        }
                    }
                }
                if (out.isEmpty() && lastError != null) {
                    throw lastError instanceof Exception ? (Exception) lastError : new Exception(lastError);
                }
            }

            private List<String> readTokenFileFallback(String path) {
                try {
                    Path file = importPath(path);
                    if (!Files.isRegularFile(file)) {
                        return new ArrayList<String>();
                    }
                    byte[] bytes = Files.readAllBytes(file);
                    String text = new String(bytes, StandardCharsets.UTF_8);
                    setBatchInfo(text);
                    return ru.vidtu.iasfork.cookie.TokenImporter.extractValues(text);
                } catch (Throwable ignored) {
                    return new ArrayList<String>();
                }
            }

            /**
             * Imports one cookie/token file per list entry with rate-limit
             * spacing (modern multi-cookie parity): 6s between files, 30s
             * retry (x2) on 429, per-file progress, imported/failed/dup counts.
             */
            private void importMultipleFiles(List<String> paths) {
                final List<CookieAuth.MinecraftProfile> ok = new ArrayList<CookieAuth.MinecraftProfile>();
                int failed = 0;
                int skipped = 0;
                final int total = paths.size();
                for (int i = 0; i < total; i++) {
                    if (closed) {
                        return;
                    }
                    if (consumeSkip()) {
                        skipped++;
                        batchSkipped = skipped;
                        beginAccount(i, sourceName(paths.get(i)), "ias.cookie.batch.type.file", "");
                        accountStartedNanos = 0L;
                        accountElapsedNanos = 0L;
                        postStatus(I18n.format("ias.cookie.batch.skipped", i + 1, total));
                        skipDelay();
                        continue;
                    }
                    final int idx = i;
                    String path = paths.get(idx);
                    beginAccount(idx, sourceName(path), "ias.cookie.batch.type.file", "");
                    postStatus(I18n.format("ias.cookie.multi.progress", idx + 1, total));
                    List<CookieAuth.MinecraftProfile> profiles = null;
                    Throwable lastError = null;
                    int rateLimitRetries = 0;
                    boolean skippedJob = false;
                    while (profiles == null) {
                        if (closed) {
                            return;
                        }
                        if (consumeSkip()) {
                            skippedJob = true;
                            break;
                        }
                        try {
                            profiles = importOneFile(path);
                        } catch (Throwable t) {
                            if (t instanceof ImportSkippedException) {
                                skippedJob = true;
                                break;
                            }
                            if (consumeSkip()) {
                                skippedJob = true;
                                break;
                            }
                            if (isRateLimited(t) && rateLimitRetries < 2) {
                                rateLimitRetries++;
                                postStatus(I18n.format("ias.error.rateLimited"));
                                try {
                                    Thread.sleep(30000L);
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                    if (closed) {
                                        return;
                                    }
                                    if (consumeSkip()) {
                                        skippedJob = true;
                                        break;
                                    }
                                    return;
                                }
                                continue;
                            }
                            lastError = t;
                            break;
                        }
                    }
                    if (consumeSkip()) {
                        skippedJob = true;
                    }
                    if (skippedJob) {
                        skipped++;
                        batchSkipped = skipped;
                        batchIndex = idx + 1;
                        postStatus(I18n.format("ias.cookie.batch.skipped", idx + 1, total));
                    } else if (profiles != null && !profiles.isEmpty()) {
                        ok.addAll(profiles);
                        batchImported = ok.size();
                        batchUser = profiles.get(profiles.size() - 1).name;
                        postStatus(I18n.format("ias.cookie.batch.imported", batchUser));
                    } else {
                        failed++;
                        batchFailed = failed;
                        failures.add(failureText(path, 0, lastError != null ? lastError : new Exception(I18n.format("ias.error.cookie.invalid"))));
                        postStatus(I18n.format("ias.cookie.batch.failed"));
                    }
                    if (accountStartedNanos > 0L) {
                        accountElapsedNanos = System.nanoTime() - accountStartedNanos;
                        accountStartedNanos = 0L;
                    }
                    if (idx + 1 < total) {
                        try {
                            Thread.sleep(6000L);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            if (closed) {
                                return;
                            }
                            if (!skipRequested) {
                                return;
                            }
                        }
                    }
                }
                if (closed) {
                    return;
                }
                final List<CookieAuth.MinecraftProfile> done = ok;
                final int failedCount = failed;
                final int skippedCount = skipped;
                Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        if (closed) {
                            return;
                        }
                        int dup = 0;
                        try {
                            for (CookieAuth.MinecraftProfile p : done) {
                                if (saveAccount(p)) {
                                    dup++;
                                }
                            }
                            batchDuplicate = dup;
                            CookieAuth.MinecraftProfile last = done.isEmpty() ? null : done.get(done.size() - 1);
                            Config.save();
                            if (last != null && failedCount == 0 && done.size() == 1 && failures.isEmpty() && skippedCount == 0) {
                                importing = false;
                                resetBatch();
                                MR.setSession(new Session(last.name, last.uuid, last.token, "mojang"));
                                mc.displayGuiScreen(returnToAccountListOnSuccess ? new GuiAccountSelector() : parent);
                                return;
                            }
                            if (last != null) {
                                MR.setSession(new Session(last.name, last.uuid, last.token, "mojang"));
                            }
                            statusLines.clear();
                            int reportFailed = failures.size();
                            String key = skippedCount > 0 ? "ias.cookie.multi.done.skipped" : "ias.cookie.multi.done";
                            statusLines.add(I18n.format(key, done.size(), done.size() + reportFailed, reportFailed, dup, skippedCount));
                            importing = false;
                            showFailures = !failures.isEmpty();
                            failureScroll = 0;
                            resetBatch();
                            initGui();
                        } catch (Throwable t) {
                            showError(formatError(t));
                            importing = false;
                            resetBatch();
                            initGui();
                        }
                    }
                });
            }

            private List<CookieAuth.MinecraftProfile> importOneFile(String path) throws Exception {
                try {
                    Path file = importPath(path);
                    if (!Files.isRegularFile(file)) {
                        throw new CookieAuthException("Not a regular file: " + file, "ias.error.cookie.file");
                    }
                    String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                    setBatchInfo(text);
                    List<String> records = CookieParser.splitRecords(text);
                    if (records.size() > 1) {
                        List<CookieAuth.MinecraftProfile> out = new ArrayList<CookieAuth.MinecraftProfile>();
                        int blobFailed = 0;
                        Throwable last = null;
                        List<String> partialFailures = new ArrayList<String>();
                        for (int blobIndex = 0; blobIndex < records.size(); blobIndex++) {
                            if (closed) {
                                break;
                            }
                            if (consumeSkip()) {
                                throw new ImportSkippedException();
                            }
                            String blob = records.get(blobIndex);
                            try {
                                out.add(CookieAuth.authenticate(CookieParser.fromText(blob)));
                            } catch (Throwable t) {
                                List<String> tokens = ru.vidtu.iasfork.cookie.TokenImporter.extractValues(blob);
                                if (tokens.size() == 1) {
                                    try {
                                        out.add(ru.vidtu.iasfork.cookie.TokenImporter.importSingleToken(tokens.get(0)));
                                    } catch (Throwable tt) {
                                        blobFailed++;
                                        last = tt;
                                        partialFailures.add(failureText(path, blobIndex + 1, tt));
                                    }
                                } else {
                                    blobFailed++;
                                    last = t;
                                    partialFailures.add(failureText(path, blobIndex + 1, t));
                                }
                            }
                            if (consumeSkip()) {
                                throw new ImportSkippedException();
                            }
                        }
                        if (out.isEmpty()) {
                            if (last instanceof Exception) {
                                throw (Exception) last;
                            }
                            throw new Exception(last == null ? "No accounts in dump" : last.getMessage());
                        }
                        failures.addAll(partialFailures);
                        if (blobFailed > 0) {
                            // Surface partial-dump failures in the log; the file still
                            // counts once, but no blob is silently dropped.
                            System.err.println("IAS: " + blobFailed + " of " + records.size()
                                    + " dump blobs failed in " + path + (last == null ? "" : ": " + last.getMessage()));
                        }
                        return out;
                    }
                    List<CookieAuth.MinecraftProfile> single = new ArrayList<CookieAuth.MinecraftProfile>(1);
                    single.add(CookieAuth.authenticate(CookieParser.fromPath(path)));
                    return single;
                } catch (Throwable cookieFailed) {
                    if (cookieFailed instanceof ImportSkippedException) {
                        throw (ImportSkippedException) cookieFailed;
                    }
                    List<String> tokens = readTokenFileFallback(path);
                    if (tokens.isEmpty()) {
                        if (cookieFailed instanceof Exception) {
                            throw (Exception) cookieFailed;
                        }
                        throw new Exception(cookieFailed);
                    }
                    if (tokens.size() == 1) {
                        List<CookieAuth.MinecraftProfile> single = new ArrayList<CookieAuth.MinecraftProfile>(1);
                        single.add(ru.vidtu.iasfork.cookie.TokenImporter.importSingleToken(tokens.get(0)));
                        return single;
                    }
                    List<CookieAuth.MinecraftProfile> out = new ArrayList<CookieAuth.MinecraftProfile>();
                    importTokensWithDelay(tokens, out);
                    if (out.isEmpty()) {
                        if (cookieFailed instanceof Exception) {
                            throw (Exception) cookieFailed;
                        }
                        throw new Exception(cookieFailed);
                    }
                    return out;
                }
            }

            private void postStatus(final String line) {
                try {
                    Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                        @Override
                        public void run() {
                            if (!closed) {
                                statusLines.clear();
                                statusLines.add(line);
                            }
                        }
                    });
                } catch (Throwable ignored) {
                }
            }
        }, "IAS/Cookie").start();
    }

    /**
     * @return true when an existing entry was replaced (duplicate).
     */
    private boolean saveAccount(CookieAuth.MinecraftProfile profile) {
        ExtendedAccountData data = ExtendedAccountData.cookieSession(profile.name, profile.token, profile.uuid, profile.refreshToken);
        data.premium = EnumBool.TRUE;
        return ExtendedAccountData.replaceOrAddCookieAccount(AltDatabase.getInstance(), data);
    }

    private static boolean isRateLimited(Throwable t) {
        while (t != null) {
            String m = t.getMessage();
            if (m != null && (m.contains("429") || m.toLowerCase().contains("rate-limit")
                    || m.toLowerCase().contains("rate limit") || m.toLowerCase().contains("rate_limit"))) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    private void saveAndLogin(CookieAuth.MinecraftProfile profile) throws Exception {
        ExtendedAccountData data = ExtendedAccountData.cookieSession(profile.name, profile.token, profile.uuid, profile.refreshToken);
        data.premium = EnumBool.TRUE;
        ExtendedAccountData.replaceOrAddCookieAccount(AltDatabase.getInstance(), data);
        Config.save();
        MR.setSession(new Session(profile.name, profile.uuid, profile.token, "mojang"));
    }

    private String resolvePasteSource() {
        String box = pasteInput != null ? pasteInput.getText() : "";
        if (box == null) {
            box = "";
        }
        String clip = clipboardContents();
        // Box content always wins when non-empty; clipboard is only a fallback.
        // The old tab-preference returned stale clipboard data for Localts/JWT pastes.
        if (!box.trim().isEmpty()) {
            return box;
        }
        return clip;
    }

    private String clipboardContents() {
        try {
            String clipboard = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            return clipboard != null ? clipboard : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private void showError(String message) {
        statusLines.clear();
        for (String line : fontRendererObj.listFormattedStringToWidth(EnumChatFormatting.RED + message, width - 40)) {
            statusLines.add(line);
        }
    }

    private static String failureText(String path, int index, Throwable error) {
        String label = sourceName(path) + (index > 0 ? " #" + index : "");
        return EnumChatFormatting.RED + label + EnumChatFormatting.GRAY + ": " + formatError(error);
    }

    private static String sourceName(String path) {
        try {
            return new java.io.File(path.trim()).getName();
        } catch (Throwable t) {
            return path;
        }
    }

    private void rebuildFailureLines() {
        failureDisplayLines.clear();
        for (String failure : failures) {
            failureDisplayLines.addAll(fontRendererObj.listFormattedStringToWidth(failure, width - 80));
        }
    }

    private int failureListTop() {
        return height / 2 - 18;
    }

    private int failureListBottom() {
        return height / 2 + 52;
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        if (!showFailures || failureDisplayLines.isEmpty() || mc == null) {
            return;
        }
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        int mouseX = Mouse.getEventX() * width / Math.max(1, mc.displayWidth);
        int mouseY = height - Mouse.getEventY() * height / Math.max(1, mc.displayHeight) - 1;
        int left = width / 2 - 125;
        int right = width / 2 + 125;
        if (mouseX < left || mouseX > right || mouseY < failureListTop() || mouseY > failureListBottom()) {
            return;
        }
        int visible = Math.max(1, (failureListBottom() - failureListTop()) / 10);
        int maxScroll = Math.max(0, failureDisplayLines.size() - visible);
        failureScroll = Math.max(0, Math.min(maxScroll, failureScroll - (wheel > 0 ? 1 : -1)));
    }

    private static String formatError(Throwable t) {
        if (isRateLimited(t)) {
            try {
                return I18n.format("ias.error.rateLimited");
            } catch (Throwable ignored) {
            }
        }
        if (t instanceof CookieAuthException) {
            CookieAuthException e = (CookieAuthException) t;
            if (e.langKey() != null) {
                return I18n.format(e.langKey());
            }
            return e.getMessage();
        }
        Throwable cause = t.getCause();
        if (cause != null) {
            return formatError(cause);
        }
        return t.getMessage() != null ? t.getMessage() : t.toString();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (pasteMode && pasteInput != null) {
            pasteInput.textboxKeyTyped(typedChar, keyCode);
        } else if (!pasteMode && pathInput != null) {
            pathInput.textboxKeyTyped(typedChar, keyCode);
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (pasteMode && pasteInput != null) {
            pasteInput.mouseClicked(mouseX, mouseY, mouseButton);
        } else if (!pasteMode && pathInput != null) {
            pathInput.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    public void updateScreen() {
        if (pasteMode && pasteInput != null) {
            pasteInput.updateCursorCounter();
        } else if (!pasteMode && pathInput != null) {
            pathInput.updateCursorCounter();
        }
    }

    private void drawBatchStatus() {
        int centerX = width / 2;
        long now = System.nanoTime();
        long totalNanos = batchStartedNanos > 0L ? now - batchStartedNanos : 0L;
        long accountNanos = accountStartedNanos > 0L ? now - accountStartedNanos : accountElapsedNanos;
        int total = Math.max(1, batchTotal);
        drawCenteredString(fontRendererObj, I18n.format("ias.cookie.batch.progress",
                        Math.max(1, batchIndex), total, formatElapsed(totalNanos), formatAccountElapsed(accountNanos)),
                centerX, height / 2 - 24, 0xFFFF55);

        String info = batchLabel == null ? "" : batchLabel;
        if (batchType != null && !batchType.isEmpty()) {
            info += " \u00b7 " + I18n.format(batchType);
        }
        if (batchUser != null && !batchUser.isEmpty()) {
            info += " \u00b7 " + I18n.format("ias.cookie.batch.user", batchUser);
        }
        int y = height / 2 - 12;
        for (String line : fontRendererObj.listFormattedStringToWidth(info, width - 40)) {
            drawCenteredString(fontRendererObj, line, centerX, y, 0xAAAAAA);
            y += 10;
        }
        y = Math.max(y, height / 2);
        for (String line : statusLines) {
            drawCenteredString(fontRendererObj, line, centerX, y, 0xFFFFFF);
            y += 10;
        }
        y = Math.max(y + 2, height / 2 + 12);
        drawCenteredString(fontRendererObj, I18n.format("ias.cookie.batch.counts",
                        batchImported, batchFailed, batchDuplicate, batchSkipped),
                centerX, y, 0xAAAAAA);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (parent != null) {
            parent.drawScreen(0, 0, partialTicks);
        }
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, I18n.format("ias.cookie"), width / 2, height / 2 - 70, 0xFFFFFF);
        if (importing && batchTotal > 1) {
            drawBatchStatus();
            super.drawScreen(mouseX, mouseY, partialTicks);
            return;
        }
        if (showFailures) {
            int y = height / 2 - 48;
            for (String line : statusLines) {
                drawCenteredString(fontRendererObj, line, width / 2, y, 0xFFFFFF);
                y += 10;
            }
            drawCenteredString(fontRendererObj, I18n.format("ias.cookie.failures", failures.size()),
                    width / 2, height / 2 - 28, 0xFF5555);
            int top = failureListTop();
            int bottom = failureListBottom();
            int visible = Math.max(1, (bottom - top) / 10);
            int maxScroll = Math.max(0, failureDisplayLines.size() - visible);
            failureScroll = Math.max(0, Math.min(maxScroll, failureScroll));
            int lineY = top;
            for (int i = failureScroll; i < failureDisplayLines.size() && i < failureScroll + visible; i++) {
                drawString(fontRendererObj, failureDisplayLines.get(i), width / 2 - 120, lineY, 0xFFFFFF);
                lineY += 10;
            }
            if (maxScroll > 0) {
                int trackX = width / 2 + 127;
                drawRect(trackX, top, trackX + 2, bottom, 0x80FFFFFF);
                int trackHeight = bottom - top;
                int thumbHeight = Math.max(10, trackHeight * visible / failureDisplayLines.size());
                int thumbY = top + (trackHeight - thumbHeight) * failureScroll / maxScroll;
                drawRect(trackX, thumbY, trackX + 2, thumbY + thumbHeight, 0xFFFFFFFF);
            }
            super.drawScreen(mouseX, mouseY, partialTicks);
            return;
        }
        int y = height / 2 + 20;
        for (String line : statusLines) {
            drawCenteredString(fontRendererObj, line, width / 2, y, 0xFFFFFF);
            y += 10;
        }
        if (pasteMode && pasteInput != null) {
            pasteInput.drawTextBox();
        } else if (!pasteMode && pathInput != null) {
            pathInput.drawTextBox();
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
