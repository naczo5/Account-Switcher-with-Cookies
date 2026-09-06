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
import ru.vidtu.iasfork.cookie.CookieAuth;
import ru.vidtu.iasfork.cookie.CookieAuthException;
import ru.vidtu.iasfork.cookie.CookieParser;
import the_fireplace.ias.account.ExtendedAccountData;
import the_fireplace.ias.enums.EnumBool;

import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Cookie / Localts import screen for 1.8.9.
 */
public class GuiCookieImport extends GuiScreen {
    private final GuiScreen parent;
    private final boolean returnToAccountListOnSuccess;
    private boolean pasteMode;
    private boolean importing;
    private volatile boolean closed;
    private GuiTextField pathInput;
    private GuiTextField pasteInput;
    private String savedPath = "";
    private String savedPaste = "";
    /** Files picked via Browse (multi-select). Empty = parse pathInput text. */
    private final List<String> selectedFiles = new ArrayList<String>();
    private final List<String> statusLines = new ArrayList<String>();

    public GuiCookieImport(GuiScreen parent) {
        this(parent, false);
    }

    public GuiCookieImport(GuiScreen parent, boolean returnToAccountListOnSuccess) {
        this.parent = parent;
        this.returnToAccountListOnSuccess = returnToAccountListOnSuccess;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        int centerX = width / 2;
        int centerY = height / 2;

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
        super.onGuiClosed();
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (!button.enabled) {
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
                            if (closed) {
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
                    });
                } catch (Throwable t) {
                    showError(I18n.format("ias.cookie.browse.failed"));
                }
            }
        }, "IAS/CookieBrowse").start();
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

    private void importCookies() {
        if (importing) {
            return;
        }
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
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (fromPath && files.size() > 1) {
                        importMultipleFiles(files);
                        return;
                    }
                    final List<CookieAuth.MinecraftProfile> profiles = new ArrayList<CookieAuth.MinecraftProfile>();
                    if (fromPath) {
                        try {
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
                    if (closed || profiles.isEmpty()) {
                        return;
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
                                } else {
                                    mc.displayGuiScreen(returnToAccountListOnSuccess ? new GuiAccountSelector() : parent);
                                }
                            } catch (Throwable t) {
                                showError(formatError(t));
                                importing = false;
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
                            }
                        });
                    }
                }
            }

            private void importTokensWithDelay(List<String> tokens, List<CookieAuth.MinecraftProfile> out) throws Exception {
                Throwable lastError = null;
                int rateLimitRetries = 0;
                for (int i = 0; i < tokens.size(); i++) {
                    if (closed) {
                        return;
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
                    if (i + 1 < tokens.size()) {
                        try {
                            Thread.sleep(6000L);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
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
                    byte[] bytes = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path.trim()));
                    String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
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
                int duplicates = 0;
                final int total = paths.size();
                for (int i = 0; i < total; i++) {
                    if (closed) {
                        return;
                    }
                    final int idx = i;
                    postStatus(I18n.format("ias.cookie.multi.progress", idx + 1, total));
                    String path = paths.get(idx);
                    CookieAuth.MinecraftProfile profile = null;
                    Throwable lastError = null;
                    int rateLimitRetries = 0;
                    while (profile == null) {
                        if (closed) {
                            return;
                        }
                        try {
                            profile = importOneFile(path);
                        } catch (Throwable t) {
                            if (isRateLimited(t) && rateLimitRetries < 2) {
                                rateLimitRetries++;
                                postStatus(I18n.format("ias.error.rateLimited"));
                                try {
                                    Thread.sleep(30000L);
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                    return;
                                }
                                continue;
                            }
                            lastError = t;
                            break;
                        }
                    }
                    if (profile != null) {
                        ok.add(profile);
                    } else {
                        failed++;
                        if (lastError != null) {
                            lastError.printStackTrace();
                        }
                    }
                    if (idx + 1 < total) {
                        try {
                            Thread.sleep(6000L);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
                if (closed) {
                    return;
                }
                final List<CookieAuth.MinecraftProfile> done = ok;
                final int failedCount = failed;
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
                            CookieAuth.MinecraftProfile last = done.isEmpty() ? null : done.get(done.size() - 1);
                            Config.save();
                            if (last != null && failedCount == 0 && done.size() == 1) {
                                MR.setSession(new Session(last.name, last.uuid, last.token, "mojang"));
                                mc.displayGuiScreen(returnToAccountListOnSuccess ? new GuiAccountSelector() : parent);
                                return;
                            }
                            if (last != null) {
                                MR.setSession(new Session(last.name, last.uuid, last.token, "mojang"));
                            }
                            statusLines.clear();
                            statusLines.add(I18n.format("ias.cookie.multi.done", done.size(), total, failedCount, dup));
                            importing = false;
                        } catch (Throwable t) {
                            showError(formatError(t));
                            importing = false;
                        }
                    }
                });
            }

            private CookieAuth.MinecraftProfile importOneFile(String path) throws Exception {
                try {
                    return CookieAuth.authenticate(CookieParser.fromPath(path));
                } catch (Throwable cookieFailed) {
                    List<String> tokens = readTokenFileFallback(path);
                    if (tokens.isEmpty()) {
                        if (cookieFailed instanceof Exception) {
                            throw (Exception) cookieFailed;
                        }
                        throw new Exception(cookieFailed);
                    }
                    if (tokens.size() == 1) {
                        return ru.vidtu.iasfork.cookie.TokenImporter.importSingleToken(tokens.get(0));
                    }
                    List<CookieAuth.MinecraftProfile> out = new ArrayList<CookieAuth.MinecraftProfile>();
                    importTokensWithDelay(tokens, out);
                    if (out.isEmpty()) {
                        if (cookieFailed instanceof Exception) {
                            throw (Exception) cookieFailed;
                        }
                        throw new Exception(cookieFailed);
                    }
                    // First profile counts for this file; extras are saved too.
                    for (int i = 1; i < out.size(); i++) {
                        final CookieAuth.MinecraftProfile extra = out.get(i);
                        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                            @Override
                            public void run() {
                                saveAccount(extra);
                                Config.save();
                            }
                        });
                    }
                    return out.get(0);
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

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (parent != null) {
            parent.drawScreen(0, 0, partialTicks);
        }
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, I18n.format("ias.cookie"), width / 2, height / 2 - 70, 0xFFFFFF);
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
