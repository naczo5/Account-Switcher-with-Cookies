package the_fireplace.ias.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

/**
 * Account providers selection hub for 1.8.9.
 * Currently supports the Localts Store, with room for additional providers in future updates.
 */
public class GuiProviders extends GuiScreen {
    private final GuiScreen parent;

    public GuiProviders(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Localts Store provider
        this.buttonList.add(new GuiButton(0, centerX - 100, centerY - 24, 200, 20, I18n.format("ias.providers.localts")));

        // Future providers placeholder
        GuiButton moreBtn = new GuiButton(1, centerX - 100, centerY + 2, 200, 20, I18n.format("ias.providers.more"));
        moreBtn.enabled = false;
        this.buttonList.add(moreBtn);

        // Back
        this.buttonList.add(new GuiButton(2, centerX - 100, centerY + 36, 200, 20, I18n.format("gui.back")));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (!button.enabled) return;

        if (button.id == 0) {
            this.mc.displayGuiScreen(new GuiLocalts(this));
        } else if (button.id == 2) {
            this.mc.displayGuiScreen(this.parent);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(this.parent);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        super.onGuiClosed();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (this.parent != null) {
            this.parent.drawScreen(0, 0, partialTicks);
        }
        this.drawDefaultBackground();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.drawCenteredString(this.fontRendererObj, I18n.format("ias.providers.title"), centerX, centerY - 64, 0xFFFFFF);
        this.drawCenteredString(this.fontRendererObj, I18n.format("ias.providers.subtitle"), centerX, centerY - 46, 0xAAAAAA);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
