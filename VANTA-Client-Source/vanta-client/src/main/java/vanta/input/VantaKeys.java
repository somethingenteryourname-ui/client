package vanta.input;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.resources.Identifier;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

/**
 * VANTA key bindings. They appear under "VANTA Client" in Minecraft's Controls
 * screen and can be rebound there.
 */
public final class VantaKeys {
	public static KeyMapping OPEN_MENU;
	public static KeyMapping HUD_EDITOR;

	private VantaKeys() {
	}

	public static void register() {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("vanta", "main"));
		OPEN_MENU = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.vanta.open_menu",
				InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));
		HUD_EDITOR = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.vanta.hud_editor",
				InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));
	}

	/** True if this key press is the (possibly rebound) menu key. Used to close the menu with the same key. */
	public static boolean isMenuKey(KeyEvent event) {
		InputConstants.Key bound = KeyBindingHelper.getBoundKeyOf(OPEN_MENU);
		return bound.getType() == InputConstants.Type.KEYSYM && bound.getValue() == event.key();
	}

	/** Display name of the current menu key, e.g. "Right Shift". */
	public static String menuKeyName() {
		return OPEN_MENU == null ? "Right Shift" : OPEN_MENU.getTranslatedKeyMessage().getString();
	}
}
