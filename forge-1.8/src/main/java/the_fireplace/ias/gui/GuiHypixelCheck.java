package the_fireplace.ias.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import ru.vidtu.ias.auth.hypixel.HypixelBanChecker;
import ru.vidtu.ias.auth.hypixel.HypixelBanResult;
import ru.vidtu.ias.config.IASConfig;
import the_fireplace.ias.account.ExtendedAccountData;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Hypixel ban-check progress for 1.8.9.
 * Safe mode skips accounts that would be joined from this IP.
 * From this IP joins them and waits for Continue after each account.
 */
public class GuiHypixelCheck extends GuiScreen {
    private final GuiAccountSelector parent;
    private GuiButton safeButton;
    private GuiButton directButton;
    private GuiButton continueButton;
    private GuiButton cancelButton;
    private volatile String status = "";
    private volatile boolean running;
    private volatile boolean paused;
    private volatile boolean cancelled;
    private volatile boolean directOverride;
    private final Object pauseLock = new Object();

    public GuiHypixelCheck(GuiAccountSelector parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        safeButton = new GuiButton(1, centerX - 100, centerY + 24, 98, 20, I18n.format("ias.accounts.checkHypixel.confirm.button"));
        directButton = new GuiButton(2, centerX + 2, centerY + 24, 98, 20, I18n.format("ias.accounts.checkHypixel.direct"));
        continueButton = new GuiButton(3, centerX - 100, centerY + 48, 98, 20, I18n.format("ias.hypixel.progress.continue"));
        cancelButton = new GuiButton(4, centerX + 2, centerY + 48, 98, 20, I18n.format("gui.cancel"));
        continueButton.visible = false;
        continueButton.enabled = false;
        buttonList.add(safeButton);
        buttonList.add(directButton);
        buttonList.add(continueButton);
        buttonList.add(cancelButton);
        if (status.isEmpty()) {
            status = wrapCenter(I18n.format("ias.accounts.checkHypixel.confirm"), 50);
        }
    }

    private String wrapCenter(String text, int perLine) {
        if (text == null || text.length() <= perLine) {
            return text;
        }
        StringBuilder out = new StringBuilder();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + perLine, text.length());
            if (end < text.length()) {
                int space = text.lastIndexOf(' ', end);
                if (space > start) {
                    end = space;
                }
            }
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(text.substring(start, end).trim());
            start = end;
            while (start < text.length() && text.charAt(start) == ' ') {
                start++;
            }
        }
        return out.toString();
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (!button.enabled) {
            return;
        }
        if (button.id == 1) {
            start(false);
        } else if (button.id == 2) {
            start(true);
        } else if (button.id == 3) {
            synchronized (pauseLock) {
                paused = false;
                pauseLock.notifyAll();
            }
            continueButton.enabled = false;
        } else if (button.id == 4) {
            cancelled = true;
            synchronized (pauseLock) {
                paused = false;
                pauseLock.notifyAll();
            }
            mc.displayGuiScreen(parent);
        }
    }

    private void start(boolean direct) {
        if (running) {
            return;
        }
        IASConfig.load(mc.mcDataDir);
        running = true;
        directOverride = direct;
        cancelled = false;
        safeButton.visible = false;
        directButton.visible = false;
        parent.setHypixelCheckRunning(true);
        setStatus(I18n.format("ias.hypixel.progress.preparing"));
        final ArrayList<ExtendedAccountData> accounts = parent.accountsForHypixelCheck();
        new Thread(new Runnable() {
            @Override
            public void run() {
                runChecks(accounts);
            }
        }, "IAS-HypixelCheck").start();
    }

    private void runChecks(ArrayList<ExtendedAccountData> accounts) {
        int total = 0;
        for (ExtendedAccountData data : accounts) {
            if (parent.canCheckHypixelAccount(data)) {
                total++;
            }
        }
        int index = 0;
        boolean stopQueue = false;
        for (int i = 0; i < accounts.size(); i++) {
            if (cancelled) {
                break;
            }
            ExtendedAccountData data = accounts.get(i);
            if (!parent.canCheckHypixelAccount(data)) {
                parent.markHypixelNotApplicable(data);
                continue;
            }
            index++;
            String name = parent.hypixelAccountName(data);
            parent.markHypixelChecking(data);
            try {
                HypixelBanResult unsafe = directOverride ? null : HypixelBanChecker.skipIfUnsafe(parent.hypixelAccountUuid(data));
                HypixelBanResult result;
                if (unsafe != null) {
                    setStatus(index + "/" + total + "  " + name + "\n" + (unsafe.errorMessage() == null ? I18n.format("ias.hypixel.progress.skipped") : unsafe.errorMessage()));
                    result = unsafe;
                } else {
                    setStatus(index + "/" + total + "  " + name + "\n" + I18n.format("ias.hypixel.progress.auth"));
                    result = parent.resolveAndCheck(data, directOverride);
                }
                if (cancelled) {
                    break;
                }
                parent.applyHypixelResult(data, result);
                stopQueue = result != null && result.networkBan();
                setStatus(index + "/" + total + "  " + name + "\n" + I18n.format(stopQueue ? "ias.hypixel.progress.stopped" : "ias.hypixel.progress.accountDone"));
                if (stopQueue) {
                    break;
                }
                if (cancelled) {
                    break;
                }
                if (directOverride && index < total) {
                    paused = true;
                    setStatus(I18n.format("ias.hypixel.progress.paused"));
                    mc.addScheduledTask(new Runnable() {
                        @Override
                        public void run() {
                            continueButton.visible = true;
                            continueButton.enabled = true;
                        }
                    });
                    synchronized (pauseLock) {
                        while (paused && !cancelled) {
                            try {
                                pauseLock.wait(500L);
                            } catch (InterruptedException ignored) {
                                Thread.currentThread().interrupt();
                                cancelled = true;
                                break;
                            }
                        }
                    }
                    mc.addScheduledTask(new Runnable() {
                        @Override
                        public void run() {
                            continueButton.enabled = false;
                        }
                    });
                }
            } catch (Throwable t) {
                parent.applyHypixelResult(data, HypixelBanResult.error(t.getMessage() != null ? t.getMessage() : "Unknown error"));
                setStatus(index + "/" + total + "  " + name + "\n" + (t.getMessage() != null ? t.getMessage() : "Unknown error"));
            }
        }
        running = false;
        parent.setHypixelCheckRunning(false);
        try {
            parent.saveHypixelResults();
        } catch (Throwable ignored) {
        }
        if (!cancelled) {
            setStatus(I18n.format("ias.hypixel.progress.done"));
            mc.addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    continueButton.visible = false;
                    cancelButton.displayString = I18n.format("ias.hypixel.progress.close");
                }
            });
        }
    }

    private void setStatus(final String text) {
        status = text;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        this.drawCenteredString(fontRendererObj, I18n.format("ias.accounts.checkHypixel"), this.width / 2, this.height / 2 - 60, 0xFFFFFF);
        int y = this.height / 2 - 36;
        for (String line : status.split("\n")) {
            this.drawCenteredString(fontRendererObj, line, this.width / 2, y, 0xFFFFFF);
            y += 12;
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void onGuiClosed() {
        cancelled = true;
        synchronized (pauseLock) {
            paused = false;
            pauseLock.notifyAll();
        }
        parent.setHypixelCheckRunning(false);
    }
}
