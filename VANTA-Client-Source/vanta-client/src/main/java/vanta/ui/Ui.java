package vanta.ui;

import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import vanta.branding.Theme;

/**
 * Shared drawing primitives used by every VANTA screen and the HUD.
 * Everything is built from plain rectangles and text, which is the cheapest
 * thing the 1.21.11 GUI renderer can draw — no blur, no shaders, no textures.
 */
public final class Ui {
	/** Pixel insets for rounded corners, per radius. */
	private static final int[][] CORNER = {
			{},
			{1},
			{2, 1},
			{3, 1, 1},
			{4, 2, 1, 1},
			{5, 3, 2, 1, 1},
			{6, 4, 3, 2, 1, 1}
	};

	private static final Map<String[], int[][]> ICON_RUNS = new IdentityHashMap<>();

	private Ui() {
	}

	public static Font font() { return Minecraft.getInstance().font; }

	// ---------- Shapes ----------

	public static void rect(GuiGraphics g, int x, int y, int w, int h, int color) {
		if (w <= 0 || h <= 0 || Colors.alpha(color) == 0) return;
		g.fill(x, y, x + w, y + h, color);
	}

	public static void roundRect(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
		if (w <= 0 || h <= 0 || Colors.alpha(color) == 0) return;
		int r = clampRadius(radius, w, h);
		if (r == 0) {
			g.fill(x, y, x + w, y + h, color);
			return;
		}
		int[] inset = CORNER[r];
		g.fill(x, y + r, x + w, y + h - r, color);
		for (int i = 0; i < r; i++) {
			g.fill(x + inset[i], y + i, x + w - inset[i], y + i + 1, color);
			g.fill(x + inset[i], y + h - 1 - i, x + w - inset[i], y + h - i, color);
		}
	}

	public static void roundOutline(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
		if (w <= 1 || h <= 1 || Colors.alpha(color) == 0) return;
		int r = clampRadius(radius, w, h);
		if (r == 0) {
			g.fill(x, y, x + w, y + 1, color);
			g.fill(x, y + h - 1, x + w, y + h, color);
			g.fill(x, y + 1, x + 1, y + h - 1, color);
			g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
			return;
		}
		int[] inset = CORNER[r];
		g.fill(x + inset[0], y, x + w - inset[0], y + 1, color);
		g.fill(x + inset[0], y + h - 1, x + w - inset[0], y + h, color);
		g.fill(x, y + r, x + 1, y + h - r, color);
		g.fill(x + w - 1, y + r, x + w, y + h - r, color);
		for (int i = 1; i < r; i++) {
			int len = Math.max(1, inset[i - 1] - inset[i]);
			g.fill(x + inset[i], y + i, x + inset[i] + len, y + i + 1, color);
			g.fill(x + w - inset[i] - len, y + i, x + w - inset[i], y + i + 1, color);
			g.fill(x + inset[i], y + h - 1 - i, x + inset[i] + len, y + h - i, color);
			g.fill(x + w - inset[i] - len, y + h - 1 - i, x + w - inset[i], y + h - i, color);
		}
	}

	/** Standard VANTA panel: themed fill with a subtle border. */
	public static void panel(GuiGraphics g, int x, int y, int w, int h, int fill) {
		roundRect(g, x, y, w, h, Theme.radius(), fill);
		roundOutline(g, x, y, w, h, Theme.radius(), Theme.border());
	}

	public static void hGradient(GuiGraphics g, int x, int y, int w, int h, int left, int right) {
		// GuiGraphics only has vertical gradients; approximate with a few vertical strips.
		int steps = Math.max(1, Math.min(24, w / 4));
		for (int i = 0; i < steps; i++) {
			int x0 = x + w * i / steps;
			int x1 = x + w * (i + 1) / steps;
			g.fill(x0, y, x1, y + h, Colors.mix(left, right, steps == 1 ? 0 : i / (float) (steps - 1)));
		}
	}

	private static int clampRadius(int radius, int w, int h) {
		int r = Math.max(0, Math.min(radius, CORNER.length - 1));
		return Math.min(r, Math.min(w, h) / 2);
	}

	// ---------- Text ----------

	public static void text(GuiGraphics g, String s, int x, int y, int color) {
		g.drawString(font(), s, x, y, color, false);
	}

	public static void textShadow(GuiGraphics g, String s, int x, int y, int color) {
		g.drawString(font(), s, x, y, color, true);
	}

	public static void textCentered(GuiGraphics g, String s, int cx, int y, int color) {
		g.drawString(font(), s, cx - font().width(s) / 2, y, color, false);
	}

	public static void textRight(GuiGraphics g, String s, int right, int y, int color) {
		g.drawString(font(), s, right - font().width(s), y, color, false);
	}

	public static void textScaled(GuiGraphics g, String s, int x, int y, float scale, int color, boolean shadow) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale);
		g.drawString(font(), s, 0, 0, color, shadow);
		g.pose().popMatrix();
	}

	public static int width(String s) { return font().width(s); }

	/** Shortens text with "..." so it fits in maxWidth pixels. */
	public static String ellipsize(String s, int maxWidth) {
		if (maxWidth <= 0) return "";
		Font f = font();
		if (f.width(s) <= maxWidth) return s;
		String dots = "...";
		int dotsW = f.width(dots);
		int end = s.length();
		while (end > 0 && f.width(s.substring(0, end)) + dotsW > maxWidth) end--;
		return s.substring(0, end).stripTrailing() + dots;
	}

	// ---------- Icons ----------

	public static void icon(GuiGraphics g, String[] icon, int x, int y, int color) {
		int[][] runs = ICON_RUNS.computeIfAbsent(icon, Ui::buildRuns);
		for (int[] run : runs) {
			g.fill(x + run[1], y + run[0], x + run[2], y + run[0] + 1, color);
		}
	}

	private static int[][] buildRuns(String[] icon) {
		java.util.List<int[]> runs = new java.util.ArrayList<>();
		for (int row = 0; row < icon.length; row++) {
			String line = icon[row];
			int start = -1;
			for (int col = 0; col <= line.length(); col++) {
				boolean on = col < line.length() && line.charAt(col) == '#';
				if (on && start < 0) start = col;
				if (!on && start >= 0) {
					runs.add(new int[]{row, start, col});
					start = -1;
				}
			}
		}
		return runs.toArray(new int[0][]);
	}

	// ---------- Controls ----------

	public static void toggle(GuiGraphics g, int x, int y, boolean on, boolean hovered) {
		int w = 20, h = 10;
		int track = on ? Theme.accent() : Colors.mix(Theme.border(), Theme.text(), hovered ? 0.18f : 0.08f);
		roundRect(g, x, y, w, h, 5, track);
		int knob = on ? Colors.contrastText(Theme.accent()) : Theme.textDim();
		int kx = on ? x + w - 9 : x + 1;
		roundRect(g, kx, y + 1, 8, 8, 4, knob);
	}

	public static void slider(GuiGraphics g, int x, int y, int w, double fraction, boolean active) {
		int trackY = y + 3;
		roundRect(g, x, trackY, w, 4, 2, Colors.mix(Theme.border(), Theme.text(), 0.06f));
		int filled = (int) Math.round(w * Math.max(0, Math.min(1, fraction)));
		roundRect(g, x, trackY, filled, 4, 2, Theme.accent());
		int kx = x + filled - 4;
		roundRect(g, kx, y, 8, 10, 3, active ? Theme.secondary() : Theme.text());
	}

	public static void button(GuiGraphics g, int x, int y, int w, int h, String label, boolean hovered, boolean primary) {
		int fill;
		int textColor;
		if (primary) {
			fill = hovered ? Colors.mix(Theme.accent(), 0xFFFFFFFF, 0.12f) : Theme.accent();
			textColor = Colors.contrastText(Theme.accent());
		} else {
			fill = hovered ? Theme.panelHover() : Theme.panelRaised();
			textColor = Theme.text();
		}
		roundRect(g, x, y, w, h, Theme.radius(), fill);
		if (!primary) roundOutline(g, x, y, w, h, Theme.radius(), hovered ? Colors.fade(Theme.accent(), 0.7f) : Theme.border());
		String s = ellipsize(label, w - 6);
		textCentered(g, s, x + w / 2, y + (h - 8) / 2, textColor);
	}

	/** Simple progress/usage bar. */
	public static void bar(GuiGraphics g, int x, int y, int w, int h, double fraction, int color) {
		roundRect(g, x, y, w, h, h / 2, Colors.mix(Theme.border(), Theme.text(), 0.05f));
		roundRect(g, x, y, (int) Math.round(w * Math.max(0, Math.min(1, fraction))), h, h / 2, color);
	}

	public static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && my >= y && mx < x + w && my < y + h;
	}
}
