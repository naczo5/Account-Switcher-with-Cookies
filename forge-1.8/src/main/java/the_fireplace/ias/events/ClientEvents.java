package the_fireplace.ias.events;

import com.github.mrebhan.ingameaccountswitcher.tools.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import the_fireplace.ias.IAS;
import the_fireplace.ias.gui.GuiAccountSelector;
import the_fireplace.ias.gui.GuiButtonWithImage;
import the_fireplace.ias.input.LunarInput;
import the_fireplace.ias.tools.Reference;
import the_fireplace.ias.utils.MainMenuScreens;

import java.lang.reflect.Field;

/**
 * @author The_Fireplace
 */
public class ClientEvents {
    private static final int BUTTON_ID = 20;

    private static boolean lunarHintShown;
    private static long menuOverlayUntil;

    static {
        menuOverlayUntil = System.currentTimeMillis() + 15000L;
    }

    @SubscribeEvent
    public void guiEvent(GuiScreenEvent.InitGuiEvent.Post event) {
        GuiScreen gui = event.gui;
        if (MainMenuScreens.isMainMenu(gui)) {
            event.buttonList.add(new GuiButtonWithImage(BUTTON_ID, gui.width / 2 + 104,
                    (gui.height / 4 + 48) + 72 + 12, 20, 20, ""));
        } else if (gui instanceof GuiMultiplayer) {
            event.buttonList.add(new GuiButtonWithImage(BUTTON_ID, gui.width / 2 + 158, gui.height - 30, 20, 20, ""));
        }
    }

    @SubscribeEvent
    public void onClick(GuiScreenEvent.ActionPerformedEvent event) {
        if (event.button.id == BUTTON_ID && canOpenFromScreen(event.gui)) {
            openAccountSwitcher();
        }
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        LunarInput.onForgeKeyInput(Minecraft.getMinecraft());
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        LunarInput.tick(mc);
        maybeShowLunarHint(mc);
    }

    @SubscribeEvent
    public void onTick(TickEvent.RenderTickEvent t) {
        if (t.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        LunarInput.tick(mc);
        GuiScreen screen = mc.currentScreen;
        if (screen != null && MainMenuScreens.isTitleTextScreen(screen)) {
            screen.drawCenteredString(mc.fontRendererObj,
                    I18n.format("ias.loggedinas") + mc.getSession().getUsername() + ".",
                    screen.width / 2, screen.height / 4 + 48 + 72 + 12 + 22, 0xFFCC8888);
        } else if (screen instanceof GuiMultiplayer) {
            if (mc.getSession().getToken().equals("0")) {
                screen.drawCenteredString(mc.fontRendererObj, I18n.format("ias.offlinemode"),
                        screen.width / 2, 10, 16737380);
            }
        }
        if (shouldDrawMenuOverlay(mc)) {
            mc.fontRendererObj.drawStringWithShadow(I18n.format("ias.lunar.overlay"), 4, 4, 0x55FF55);
        }
    }

    @SubscribeEvent
    public void configChanged(ConfigChangedEvent event) {
        if (event.modID.equals(Reference.MODID)) {
            IAS.syncConfig();
        }
    }

    /**
     * Entry point for keyboard handlers (Forge key event, LWJGL poll, AWT listener).
     */
    public static void tryOpenFromInput(Minecraft mc) {
        if (!canOpenAccountSwitcher(mc)) {
            return;
        }
        openAccountSwitcher();
    }

    private static void openAccountSwitcher() {
        Minecraft mc = Minecraft.getMinecraft();
        if (Config.getInstance() == null) {
            Config.load();
        }
        mc.displayGuiScreen(new GuiAccountSelector());
    }

    private static boolean canOpenAccountSwitcher(Minecraft mc) {
        // In-game with no menu open.
        if (mc.thePlayer != null && mc.currentScreen == null) {
            return false;
        }
        return canOpenFromScreen(mc.currentScreen);
    }

    private static boolean canOpenFromScreen(GuiScreen screen) {
        if (screen instanceof GuiAccountSelector) {
            return false;
        }
        if (screen != null && MainMenuScreens.blocksAccountSwitcherOpen(screen)) {
            return false;
        }
        if (screen != null && hasFocusedTextField(screen)) {
            return false;
        }
        return true;
    }

    private static boolean hasFocusedTextField(GuiScreen screen) {
        for (Field field : screen.getClass().getDeclaredFields()) {
            if (!GuiTextField.class.isAssignableFrom(field.getType())) {
                continue;
            }
            try {
                field.setAccessible(true);
                Object value = field.get(screen);
                if (value instanceof GuiTextField && ((GuiTextField) value).isFocused()) {
                    return true;
                }
            } catch (Exception ignored) {
                // Try the next field.
            }
        }
        return false;
    }

    private static void maybeShowLunarHint(Minecraft mc) {
        if (lunarHintShown) {
            return;
        }
        if (mc.thePlayer != null && mc.currentScreen == null) {
            return;
        }
        lunarHintShown = true;
        if (mc.ingameGUI == null || mc.ingameGUI.getChatGUI() == null) {
            return;
        }
        mc.ingameGUI.getChatGUI().printChatMessage(new ChatComponentText(
                EnumChatFormatting.GOLD + "In-Game Account Switcher: "
                        + EnumChatFormatting.GRAY + I18n.format("ias.lunar.hint")));
    }

    private static boolean shouldDrawMenuOverlay(Minecraft mc) {
        if (System.currentTimeMillis() > menuOverlayUntil) {
            return false;
        }
        if (mc.thePlayer != null && mc.currentScreen == null) {
            return false;
        }
        return mc.currentScreen == null || MainMenuScreens.isMainMenu(mc.currentScreen);
    }
}
