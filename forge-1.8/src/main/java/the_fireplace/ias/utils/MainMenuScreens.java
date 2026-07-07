package the_fireplace.ias.utils;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Detects main-menu-like screens, including Lunar Client's custom title screen.
 */
public final class MainMenuScreens {
    private static final Set<String> MAIN_MENU_KEYS = new HashSet<String>(Arrays.asList(
            "menu.singleplayer",
            "menu.multiplayer",
            "menu.online"
    ));

    private MainMenuScreens() {
        throw new AssertionError("No instances.");
    }

    public static boolean isMainMenu(GuiScreen screen) {
        if (screen == null) {
            return false;
        }
        if (screen instanceof GuiMainMenu) {
            return true;
        }
        if (GuiMainMenu.class.isAssignableFrom(screen.getClass())) {
            return true;
        }
        if (screen instanceof GuiMultiplayer) {
            return false;
        }
        if (isExcluded(screen)) {
            return false;
        }
        if (hasMainMenuButton(screen)) {
            return true;
        }
        return matchesMainMenuClassName(screen);
    }

    public static boolean isTitleTextScreen(GuiScreen screen) {
        if (screen == null) {
            return false;
        }
        return isMainMenu(screen);
    }

    /**
     * Returns whether the account-switcher open key should be blocked on this screen.
     * A {@code null} screen is allowed so Lunar Client's home UI keeps working.
     */
    public static boolean blocksAccountSwitcherOpen(GuiScreen screen) {
        if (screen == null) {
            return false;
        }
        if (screen instanceof GuiMultiplayer) {
            return false;
        }
        if (isMainMenu(screen)) {
            return false;
        }
        String full = screen.getClass().getName().toLowerCase(Locale.ROOT);
        String simple = screen.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        if (full.contains("modmenu")) {
            return true;
        }
        if (isExcluded(screen)) {
            return true;
        }
        if (full.startsWith("the_fireplace.ias.gui")) {
            return true;
        }
        boolean lunarLike = full.contains("moonsworth") || full.contains("lunar")
                || full.contains("ichor") || full.contains("genesis");
        return lunarLike && (simple.contains("mod") || simple.contains("setting")
                || simple.contains("config") || simple.contains("search"));
    }

    private static boolean isExcluded(GuiScreen screen) {
        String simple = screen.getClass().getSimpleName();
        return simple.contains("Options")
                || simple.contains("SelectWorld")
                || simple.contains("CreateWorld")
                || simple.contains("Connect")
                || simple.contains("Loading")
                || simple.contains("Progress")
                || simple.contains("Datapack")
                || simple.contains("Experiments")
                || simple.contains("Accessibility")
                || simple.contains("Language")
                || simple.contains("Credits")
                || simple.contains("Confirm")
                || simple.contains("Popup")
                || simple.contains("Account")
                || simple.contains("Config")
                || simple.contains("Cookie");
    }

    private static boolean hasMainMenuButton(GuiScreen screen) {
        for (GuiButton button : getButtons(screen)) {
            String text = button.displayString;
            if (text == null) {
                continue;
            }
            String lower = text.toLowerCase(Locale.ROOT);
            if (lower.contains("singleplayer") || lower.contains("multiplayer")) {
                return true;
            }
            for (String key : MAIN_MENU_KEYS) {
                if (text.equals(net.minecraft.client.resources.I18n.format(key))) {
                    return true;
                }
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static List<GuiButton> getButtons(GuiScreen screen) {
        try {
            Field field = GuiScreen.class.getDeclaredField("buttonList");
            field.setAccessible(true);
            return (List<GuiButton>) field.get(screen);
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private static boolean matchesMainMenuClassName(GuiScreen screen) {
        String simple = screen.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        String full = screen.getClass().getName().toLowerCase(Locale.ROOT);
        boolean lunarLike = full.contains("moonsworth") || full.contains("lunar")
                || full.contains("ichor") || full.contains("genesis");
        if (!lunarLike && !simple.contains("title") && !simple.contains("mainmenu") && !simple.contains("main_menu")) {
            return false;
        }
        if (simple.contains("subtitle") || simple.contains("world")) {
            return false;
        }
        return simple.contains("title")
                || simple.contains("mainmenu")
                || simple.contains("main_menu")
                || simple.contains("homescreen")
                || simple.contains("home_screen")
                || simple.equals("home")
                || lunarLike && (simple.contains("menu") || simple.contains("screen") || simple.contains("gui"));
    }
}
