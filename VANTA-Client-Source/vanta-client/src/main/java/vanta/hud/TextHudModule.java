package vanta.hud;

import net.minecraft.client.gui.GuiGraphics;

import vanta.modules.Category;
import vanta.ui.Ui;

/**
 * A HUD widget made of "Label value" lines. Text is rebuilt only when the underlying
 * value changes, and widths are cached, so drawing does not allocate every frame.
 */
public abstract class TextHudModule extends HudModule {
	protected static final int PAD = 4;
	protected static final int LINE = 10;

	private String[] labels = new String[0];
	private String[] values = new String[0];
	private int[] labelWidths = new int[0];
	private int contentWidth;

	protected TextHudModule(String id, String name, String description, Category category, boolean enabledByDefault, int defaultX, int defaultY) {
		super(id, name, description, category, enabledByDefault, defaultX, defaultY);
	}

	/** Replace the displayed lines. Call only when something changed. */
	protected final void setLines(String[] newLabels, String[] newValues) {
		labels = newLabels;
		values = newValues;
		labelWidths = new int[labels.length];
		int max = 0;
		for (int i = 0; i < labels.length; i++) {
			labelWidths[i] = labels[i].isEmpty() ? 0 : Ui.width(labels[i]) + 4;
			max = Math.max(max, labelWidths[i] + Ui.width(values[i]));
		}
		contentWidth = max;
	}

	protected final void setLine(String label, String value) {
		if (labels.length == 1 && labels[0].equals(label) && values[0].equals(value)) return;
		setLines(new String[]{label}, new String[]{value});
	}

	@Override public int width() { return Math.max(20, contentWidth + PAD * 2); }
	@Override public int height() { return Math.max(1, labels.length) * LINE + PAD * 2 - 2; }

	@Override
	protected void renderContent(GuiGraphics g, boolean editor) {
		int y = PAD;
		int labelColor = labelColor();
		int valueColor = textColor.argb();
		for (int i = 0; i < labels.length; i++) {
			if (!labels[i].isEmpty()) drawText(g, labels[i], PAD, y, labelColor);
			drawText(g, values[i], PAD + labelWidths[i], y, valueColor);
			y += LINE;
		}
	}
}
