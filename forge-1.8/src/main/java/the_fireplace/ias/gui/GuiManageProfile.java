package the_fireplace.ias.gui;

import com.github.mrebhan.ingameaccountswitcher.tools.Config;
import com.github.mrebhan.ingameaccountswitcher.tools.alt.AltDatabase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;
import ru.vidtu.iasfork.profile.ProfileManager;
import the_fireplace.ias.account.ExtendedAccountData;
import the_fireplace.ias.tools.SkinTools;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;

/**
 * Screen allowing skin upload and Mojang IGN change for 1.8.9.
 */
public class GuiManageProfile extends GuiScreen {
    private final GuiScreen parent;
    private final ExtendedAccountData account;

    private GuiTextField skinPathField;
    private GuiTextField nameField;

    private GuiButton modelButton;
    private GuiButton applySkinButton;
    private GuiButton applyNameButton;
    private GuiButton browseButton;
    private GuiButton backButton;

    private boolean isSlim = false;
    private volatile String status = "";
    private volatile int statusColor = 0xFFFFFF;
    private volatile boolean busy = false;

    public GuiManageProfile(GuiScreen parent, ExtendedAccountData account) {
        this.parent = parent;
        this.account = account;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Skin change section
        skinPathField = new GuiTextField(0, this.fontRendererObj, centerX - 100, centerY - 65, 145, 20);
        skinPathField.setMaxStringLength(256);

        this.buttonList.add(browseButton = new GuiButton(1, centerX + 50, centerY - 65, 50, 20, "Browse..."));
        this.buttonList.add(modelButton = new GuiButton(2, centerX - 100, centerY - 40, 95, 20, "Model: Classic"));
        this.buttonList.add(applySkinButton = new GuiButton(3, centerX + 5, centerY - 40, 95, 20, "Apply Skin"));

        // Name change section
        nameField = new GuiTextField(4, this.fontRendererObj, centerX - 100, centerY + 15, 200, 20);
        nameField.setMaxStringLength(16);
        nameField.setText(account.alias);

        this.buttonList.add(applyNameButton = new GuiButton(5, centerX - 100, centerY + 40, 200, 20, "Change Name"));

        // Back button
        this.buttonList.add(backButton = new GuiButton(6, centerX - 100, centerY + 75, 200, 20, I18n.format("gui.cancel")));

        updateButtons();
    }

    private void updateButtons() {
        if (browseButton != null) browseButton.enabled = !busy;
        if (modelButton != null) {
            modelButton.enabled = !busy;
            modelButton.displayString = isSlim ? "Model: Slim" : "Model: Classic";
        }
        if (applySkinButton != null) {
            String path = skinPathField != null ? skinPathField.getText().trim() : "";
            applySkinButton.enabled = !busy && !path.isEmpty();
        }
        if (applyNameButton != null) {
            String name = nameField != null ? nameField.getText().trim() : "";
            applyNameButton.enabled = !busy && !name.isEmpty() && !name.equalsIgnoreCase(account.alias);
        }
        if (backButton != null) backButton.enabled = !busy;
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void updateScreen() {
        if (skinPathField != null) skinPathField.updateCursorCounter();
        if (nameField != null) nameField.updateCursorCounter();
        updateButtons();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE && !busy) {
            this.mc.displayGuiScreen(parent);
            return;
        }
        if (skinPathField != null && skinPathField.isFocused()) {
            skinPathField.textboxKeyTyped(typedChar, keyCode);
        }
        if (nameField != null && nameField.isFocused()) {
            nameField.textboxKeyTyped(typedChar, keyCode);
        }
        updateButtons();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws java.io.IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (skinPathField != null) skinPathField.mouseClicked(mouseX, mouseY, mouseButton);
        if (nameField != null) nameField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (!button.enabled || busy) return;

        if (button.id == 1) { // Browse
            browseSkinFile();
        } else if (button.id == 2) { // Toggle model
            isSlim = !isSlim;
            updateButtons();
        } else if (button.id == 3) { // Apply Skin
            applySkin();
        } else if (button.id == 5) { // Apply Name
            applyName();
        } else if (button.id == 6) { // Back
            this.mc.displayGuiScreen(parent);
        }
    }

    private void browseSkinFile() {
        new Thread(() -> {
            try {
                JFileChooser chooser = new JFileChooser();
                chooser.setDialogTitle("Select Minecraft Skin PNG");
                chooser.setFileFilter(new FileNameExtensionFilter("PNG Images (*.png)", "png"));
                int result = chooser.showOpenDialog(null);
                if (result == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
                    final String path = chooser.getSelectedFile().getAbsolutePath();
                    mc.addScheduledTask(() -> {
                        if (skinPathField != null) {
                            skinPathField.setText(path);
                            updateButtons();
                        }
                    });
                }
            } catch (Throwable t) {
                status = "File browser failed. Type path manually.";
                statusColor = 0xFF5555;
            }
        }).start();
    }

    private static final java.util.regex.Pattern MINECRAFT_NAME = java.util.regex.Pattern.compile("[A-Za-z0-9_]{3,16}");

    private void applySkin() {
        final String path = skinPathField.getText().trim();
        if (path.isEmpty()) return;
        final File file = new File(path);
        if (!file.exists()) {
            status = "Selected file does not exist!";
            statusColor = 0xFF5555;
            return;
        }
        if (!path.toLowerCase().endsWith(".png")) {
            status = "Skin must be a .png file!";
            statusColor = 0xFF5555;
            return;
        }

        busy = true;
        status = "Uploading skin to Mojang...";
        statusColor = 0xFFFF55;
        updateButtons();

        new Thread(() -> {
            try {
                String token = ProfileManager.getValidToken(account);
                ProfileManager.uploadSkin(token, file, isSlim ? "slim" : "classic");
                SkinTools.cacheSkin(account.alias, true);
                SkinTools.buildSkin(account.alias);
                status = "Skin uploaded successfully!";
                statusColor = 0x55FF55;
            } catch (Throwable t) {
                status = "Skin upload failed: " + t.getMessage();
                statusColor = 0xFF5555;
            } finally {
                busy = false;
                mc.addScheduledTask(this::updateButtons);
            }
        }).start();
    }

    private void applyName() {
        final String newName = nameField.getText().trim();
        if (newName.isEmpty() || newName.equalsIgnoreCase(account.alias)) return;
        if (!MINECRAFT_NAME.matcher(newName).matches()) {
            status = "Invalid name: 3-16 chars, A-Z 0-9 _ only.";
            statusColor = 0xFF5555;
            return;
        }

        busy = true;
        status = "Changing username at Mojang...";
        statusColor = 0xFFFF55;
        updateButtons();

        new Thread(() -> {
            try {
                String token = ProfileManager.getValidToken(account);
                ProfileManager.changeName(token, newName);
                final String oldAlias = account.alias;
                mc.addScheduledTask(() -> {
                    // Alias is the display/login key for cookie accounts;
                    // user holds the original encoded name and tokens/UUID were
                    // already refreshed in getValidToken().
                    account.alias = newName;
                    Config.save();
                    SkinTools.cacheSkin(newName, true);
                    if (nameField != null) {
                        nameField.setText(newName);
                    }
                });
                status = "Username changed from " + oldAlias + " to " + newName + "!";
                statusColor = 0x55FF55;
            } catch (Throwable t) {
                status = "Name change failed: " + t.getMessage();
                statusColor = 0xFF5555;
            } finally {
                busy = false;
                mc.addScheduledTask(this::updateButtons);
            }
        }).start();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();

        this.drawCenteredString(this.fontRendererObj, "Profile Manager (" + account.alias + ")", this.width / 2, 12, 0xFFFFFF);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.drawString(this.fontRendererObj, "Upload Skin (.png):", centerX - 100, centerY - 77, 0xAAAAAA);
        if (skinPathField != null) skinPathField.drawTextBox();

        this.drawString(this.fontRendererObj, "Change Minecraft Name:", centerX - 100, centerY + 3, 0xAAAAAA);
        if (nameField != null) nameField.drawTextBox();

        if (status != null && !status.isEmpty()) {
            // Draw wrapped just above the Back button to avoid overlap on small GUIs.
            java.util.List<String> wrapped = this.fontRendererObj.listFormattedStringToWidth(status, 220);
            int y = centerY + 62 - Math.max(0, wrapped.size() - 1) * 10;
            for (String line : wrapped) {
                this.drawCenteredString(this.fontRendererObj, line, centerX, y, statusColor);
                y += 10;
            }
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}