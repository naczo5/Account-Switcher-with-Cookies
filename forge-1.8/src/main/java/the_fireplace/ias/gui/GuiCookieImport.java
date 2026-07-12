package the_fireplace.ias.gui;

import com.github.mrebhan.ingameaccountswitcher.MR;
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
            pathInput.setMaxStringLength(512);
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
                    String path = CookieFileDialogs.pickFile(I18n.format("ias.cookie.browse"), start);
                    if (path == null || closed) {
                        return;
                    }
                    Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                        @Override
                        public void run() {
                            if (closed) {
                                return;
                            }
                            savedPath = path;
                            if (pathInput != null) {
                                pathInput.setText(path);
                            }
                        }
                    });
                } catch (Throwable t) {
                    showError(I18n.format("ias.cookie.browse.failed"));
                }
            }
        }, "IAS/CookieBrowse").start();
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
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    CookieParser.ParsedCookies cookies = fromPath
                            ? CookieParser.fromPath(source)
                            : CookieParser.fromText(source);
                    if (closed) {
                        return;
                    }
                    CookieAuth.MinecraftProfile profile = CookieAuth.authenticate(cookies);
                    if (closed) {
                        return;
                    }
                    Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                        @Override
                        public void run() {
                            if (closed) {
                                return;
                            }
                            try {
                                saveAndLogin(profile);
                                mc.displayGuiScreen(returnToAccountListOnSuccess ? new GuiAccountSelector() : parent);
                            } catch (Throwable t) {
                                showError(formatError(t));
                                importing = false;
                            }
                        }
                    });
                } catch (Throwable t) {
                    if (!closed) {
                        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                            @Override
                            public void run() {
                                showError(formatError(t));
                                importing = false;
                            }
                        });
                    }
                }
            }
        }, "IAS/Cookie").start();
    }

    private void saveAndLogin(CookieAuth.MinecraftProfile profile) throws Exception {
        ExtendedAccountData data = ExtendedAccountData.cookieSession(profile.name, profile.token, profile.uuid);
        data.premium = EnumBool.TRUE;
        AltDatabase.getInstance().getAlts().add(data);
        MR.setSession(new Session(profile.name, profile.uuid, profile.token, "mojang"));
    }

    private String resolvePasteSource() {
        String box = pasteInput != null ? pasteInput.getText() : "";
        String clip = "";
        try {
            clip = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
        } catch (Exception ignored) {
        }
        if (!box.isEmpty() && box.contains("\t")) {
            return box;
        }
        if (clip != null && !clip.isEmpty() && clip.contains("\t")) {
            return clip;
        }
        if (!box.isEmpty()) {
            return box;
        }
        return clip != null ? clip : "";
    }

    private void showError(String message) {
        statusLines.clear();
        for (String line : fontRendererObj.listFormattedStringToWidth(EnumChatFormatting.RED + message, width - 40)) {
            statusLines.add(line);
        }
    }

    private static String formatError(Throwable t) {
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
