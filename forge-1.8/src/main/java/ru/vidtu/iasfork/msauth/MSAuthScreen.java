package ru.vidtu.iasfork.msauth;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.EnumChatFormatting;

public class MSAuthScreen extends GuiScreen {
	public static final String[] symbols = new String[]{"▃ ▄ ▅ ▆ ▇ █ ▇ ▆ ▅ ▄ ▃", "_ ▃ ▄ ▅ ▆ ▇ █ ▇ ▆ ▅ ▄",
			"_ _ ▃ ▄ ▅ ▆ ▇ █ ▇ ▆ ▅", "_ _ _ ▃ ▄ ▅ ▆ ▇ █ ▇ ▆", "_ _ _ _ ▃ ▄ ▅ ▆ ▇ █ ▇", "_ _ _ _ _ ▃ ▄ ▅ ▆ ▇ █",
			"_ _ _ _ ▃ ▄ ▅ ▆ ▇ █ ▇", "_ _ _ ▃ ▄ ▅ ▆ ▇ █ ▇ ▆", "_ _ ▃ ▄ ▅ ▆ ▇ █ ▇ ▆ ▅", "_ ▃ ▄ ▅ ▆ ▇ █ ▇ ▆ ▅ ▄",
			"▃ ▄ ▅ ▆ ▇ █ ▇ ▆ ▅ ▄ ▃", "▄ ▅ ▆ ▇ █ ▇ ▆ ▅ ▄ ▃ _", "▅ ▆ ▇ █ ▇ ▆ ▅ ▄ ▃ _ _", "▆ ▇ █ ▇ ▆ ▅ ▄ ▃ _ _ _",
			"▇ █ ▇ ▆ ▅ ▄ ▃ _ _ _ _", "█ ▇ ▆ ▅ ▄ ▃ _ _ _ _ _", "▇ █ ▇ ▆ ▅ ▄ ▃ _ _ _ _", "▆ ▇ █ ▇ ▆ ▅ ▄ ▃ _ _ _",
			"▅ ▆ ▇ █ ▇ ▆ ▅ ▄ ▃ _ _", "▄ ▅ ▆ ▇ █ ▇ ▆ ▅ ▄ ▃ _"};
	public GuiScreen prev;
	public List<String> text = new ArrayList<String>();
	public boolean endTask = false;
	public int tick;

	public MSAuthScreen(GuiScreen prev) {
		this.prev = prev;
		AuthSys.start(this);
	}

	@Override
	public void initGui() {
		buttonList.add(new GuiButton(0, width / 2 - 50, height - 24, 100, 20, I18n.format("gui.cancel")));
	}

	@Override
	protected void actionPerformed(GuiButton button) throws IOException {
		if (button.id == 0) {
			mc.displayGuiScreen(prev);
		}
	}

	@Override
	public void updateScreen() {
		tick++;
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float delta) {
		drawDefaultBackground();
		drawCenteredString(fontRendererObj, I18n.format("ias.msauth.title"), width / 2, 10, -1);
		for (int i = 0; i < text.size(); i++) {
			drawCenteredString(fontRendererObj, text.get(i), width / 2, height / 2 + i * 10, -1);
		}
		if (!endTask) {
			drawCenteredString(fontRendererObj, symbols[tick % symbols.length], width / 2, height / 3 * 2, -256);
		}
		super.drawScreen(mouseX, mouseY, delta);
	}

	@Override
	public void onGuiClosed() {
		AuthSys.stop();
		super.onGuiClosed();
	}

	public void setState(final String s) {
		final Minecraft minecraft = Minecraft.getMinecraft();
		minecraft.addScheduledTask(new Runnable() {
			@Override
			public void run() {
				text = minecraft.fontRendererObj.listFormattedStringToWidth(I18n.format(s), width);
			}
		});
	}

	public void error(final String error) {
		final Minecraft minecraft = Minecraft.getMinecraft();
		minecraft.addScheduledTask(new Runnable() {
			@Override
			public void run() {
				text = minecraft.fontRendererObj.listFormattedStringToWidth(
						EnumChatFormatting.RED + I18n.format("ias.msauth.error", error), width);
				endTask = true;
			}
		});
	}
}
