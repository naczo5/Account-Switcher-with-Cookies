package the_fireplace.ias.input;

import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;
import the_fireplace.ias.events.ClientEvents;

/** Handles the account-switcher open key on Forge 1.8.9 menus. */
public final class OpenKeyInput {
    private static boolean oWasDown;

    private OpenKeyInput() {
        throw new AssertionError();
    }

    public static void tick(Minecraft mc) {
        boolean oDown = Keyboard.isKeyDown(Keyboard.KEY_O);
        if (oDown && !oWasDown) {
            scheduleOpen(mc);
        }
        oWasDown = oDown;

        while (IASKeyBindings.OPEN.isPressed()) {
            scheduleOpen(mc);
        }
    }

    public static void onForgeKeyInput(Minecraft mc) {
        if (Keyboard.getEventKeyState() && Keyboard.getEventKey() == Keyboard.KEY_O) {
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
