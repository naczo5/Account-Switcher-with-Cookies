package the_fireplace.ias.input;

import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;
import the_fireplace.ias.events.ClientEvents;

import java.awt.Component;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.lang.reflect.Method;

/**
 * Captures the open key on Lunar Client, where the home UI may not pump LWJGL keyboard events.
 */
public final class LunarInput {
    private static boolean awtListenerInstalled;
    private static boolean oWasDown;
    private static Class<?> keyboardClass;
    private static Method isKeyDownMethod;
    private static Method getEventKeyMethod;
    private static Method getEventKeyStateMethod;

    private LunarInput() {
        throw new AssertionError();
    }

    public static void tick(Minecraft mc) {
        ensureKeyboardReflection(mc);
        tryInstallAwtListener();

        boolean oDown = isKeyDown(Keyboard.KEY_O);
        if (oDown && !oWasDown) {
            scheduleOpen(mc);
        }
        oWasDown = oDown;

        while (IASKeyBindings.OPEN.isPressed()) {
            scheduleOpen(mc);
        }
    }

    public static void onForgeKeyInput(Minecraft mc) {
        ensureKeyboardReflection(mc);
        if (!getEventKeyState()) {
            return;
        }
        if (getEventKey() != Keyboard.KEY_O) {
            return;
        }
        scheduleOpen(mc);
    }

    private static void tryInstallAwtListener() {
        if (awtListenerInstalled) {
            return;
        }
        try {
            Component parent = Display.getParent();
            if (parent == null) {
                return;
            }
            parent.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent event) {
                    if (event.getKeyCode() != KeyEvent.VK_O) {
                        return;
                    }
                    scheduleOpen(Minecraft.getMinecraft());
                }
            });
            awtListenerInstalled = true;
        } catch (Throwable ignored) {
            // Display not ready yet.
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

    private static void ensureKeyboardReflection(Minecraft mc) {
        if (keyboardClass != null || mc == null) {
            return;
        }
        try {
            ClassLoader loader = mc.getClass().getClassLoader();
            keyboardClass = Class.forName("org.lwjgl.input.Keyboard", true, loader);
            isKeyDownMethod = keyboardClass.getMethod("isKeyDown", int.class);
            getEventKeyMethod = keyboardClass.getMethod("getEventKey");
            getEventKeyStateMethod = keyboardClass.getMethod("getEventKeyState");
        } catch (Throwable ignored) {
            keyboardClass = null;
        }
    }

    private static boolean isKeyDown(int key) {
        try {
            if (isKeyDownMethod != null) {
                return (Boolean) isKeyDownMethod.invoke(null, key);
            }
        } catch (Throwable ignored) {
            // Fall through.
        }
        return Keyboard.isKeyDown(key);
    }

    private static int getEventKey() {
        try {
            if (getEventKeyMethod != null) {
                return (Integer) getEventKeyMethod.invoke(null);
            }
        } catch (Throwable ignored) {
            // Fall through.
        }
        return Keyboard.getEventKey();
    }

    private static boolean getEventKeyState() {
        try {
            if (getEventKeyStateMethod != null) {
                return (Boolean) getEventKeyStateMethod.invoke(null);
            }
        } catch (Throwable ignored) {
            // Fall through.
        }
        return Keyboard.getEventKeyState();
    }
}
