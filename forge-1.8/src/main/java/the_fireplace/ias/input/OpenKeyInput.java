package the_fireplace.ias.input;

import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import the_fireplace.ias.events.ClientEvents;

/** Handles the account-switcher open keybind on Forge 1.8.9 menus. */
public final class OpenKeyInput {
    private static boolean boundWasDown;

    private OpenKeyInput() {
        throw new AssertionError();
    }

    public static void tick(Minecraft mc) {
        int bound = IASKeyBindings.OPEN.getKeyCode();
        if (bound == Keyboard.KEY_NONE) {
            boundWasDown = false;
        } else {
            boolean down = bound >= 0 ? Keyboard.isKeyDown(bound) : Mouse.isButtonDown(bound + 100);
            if (down && !boundWasDown) {
                scheduleOpen(mc);
            }
            boundWasDown = down;
        }

        while (IASKeyBindings.OPEN.isPressed()) {
            scheduleOpen(mc);
        }
    }

    public static void onForgeKeyInput(Minecraft mc) {
        int bound = IASKeyBindings.OPEN.getKeyCode();
        if (bound == Keyboard.KEY_NONE || bound < 0) {
            return;
        }
        if (Keyboard.getEventKeyState() && Keyboard.getEventKey() == bound) {
            scheduleOpen(mc);
        }
    }

    private static void scheduleOpen(final Minecraft mc) {
        if (mc == null) {
            return;
        }
        mc.addScheduledTask(new Runnable() {
            @Override
            public void run() {
                ClientEvents.tryOpenFromInput(mc);
            }
        });
    }
}
