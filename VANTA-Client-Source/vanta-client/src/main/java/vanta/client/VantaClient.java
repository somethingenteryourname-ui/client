package vanta.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import vanta.branding.Theme;
import vanta.compat.CompatDetector;
import vanta.config.ConfigManager;
import vanta.gui.VantaMenuScreen;
import vanta.hud.HudEditorScreen;
import vanta.hud.HudManager;
import vanta.input.VantaKeys;
import vanta.modules.ModuleRegistry;

/** VANTA Client entry point. Everything VANTA does starts here. */
public final class VantaClient implements ClientModInitializer {
	public static final String MOD_ID = "vanta";
	public static final Logger LOG = LoggerFactory.getLogger("VANTA");

	@Override
	public void onInitializeClient() {
		// 1. Register every setting (theme + modules) so the config knows what to load.
		Theme.init();
		vanta.gui.MenuSettings.init();
		ModuleRegistry.init();
		// 2. Load saved values (safe: a broken file falls back to defaults).
		ConfigManager.get().load();
		// 3. Hooks.
		VantaKeys.register();
		HudManager.register();
		CompatDetector.scan();
		ClientTickEvents.END_CLIENT_TICK.register(VantaClient::onTick);
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ConfigManager.get().saveIfDirty());
		LOG.info("[VANTA] {} ready for Minecraft {}", VantaInfo.vantaVersion(), VantaInfo.minecraftVersion());
	}

	private static void onTick(Minecraft client) {
		while (VantaKeys.OPEN_MENU.consumeClick()) {
			if (client.screen == null) client.setScreen(new VantaMenuScreen(null));
		}
		while (VantaKeys.HUD_EDITOR.consumeClick()) {
			if (client.screen == null) client.setScreen(new HudEditorScreen(null));
		}
		ConfigManager.get().tick();
	}
}
