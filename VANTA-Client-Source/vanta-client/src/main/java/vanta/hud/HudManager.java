package vanta.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import vanta.metrics.InputStats;
import vanta.modules.ModuleRegistry;

/** Draws enabled HUD widgets through Fabric's HUD element API (below chat, above the hotbar). */
public final class HudManager {
	public static final Identifier HUD_ID = Identifier.fromNamespaceAndPath("vanta", "hud");

	private HudManager() {
	}

	public static void register() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, HUD_ID, HudManager::render);
	}

	private static void render(GuiGraphics g, DeltaTracker tracker) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options.hideGui || mc.screen instanceof HudEditorScreen) return;
		int w = g.guiWidth();
		int h = g.guiHeight();
		boolean needInput = false;
		for (HudModule m : ModuleRegistry.hud()) {
			if (!m.isEnabled()) continue; // hidden widgets are never drawn or updated
			m.render(g, w, h, false);
			needInput |= m.needsInputStats();
		}
		InputStats.setActive(needInput);
	}
}
