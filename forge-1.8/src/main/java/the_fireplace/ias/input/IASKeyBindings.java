package the_fireplace.ias.input;

import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

/**
 * Open-account-switcher keybind for Forge 1.8.9.
 */
public final class IASKeyBindings {
    public static final KeyBinding OPEN = new KeyBinding("key.ias.open", Keyboard.KEY_O, "key.categories.ias");

    private IASKeyBindings() {
        throw new AssertionError();
    }
}
