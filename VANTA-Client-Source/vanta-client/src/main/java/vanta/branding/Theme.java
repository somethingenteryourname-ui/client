package vanta.branding;

import java.util.ArrayList;
import java.util.List;

import vanta.config.BoolSetting;
import vanta.config.ChoiceSetting;
import vanta.config.ColorSetting;
import vanta.config.IntSetting;
import vanta.config.Labeled;
import vanta.config.Setting;
import vanta.config.SettingRegistry;
import vanta.config.TextSetting;
import vanta.gui.MenuPage;
import vanta.ui.Colors;

/**
 * The single source of truth for how every VANTA screen looks.
 * The title screen, the RIGHT SHIFT menu, the HUD editor and dialogs all read from here,
 * so a change in one place shows up everywhere immediately.
 */
public final class Theme {
	public enum Layout implements Labeled {
		GRID("Grid"), LIST("List");
		private final String label;
		Layout(String label) { this.label = label; }
		@Override public String label() { return label; }
	}

	public enum Density implements Labeled {
		COMPACT("Compact", 0.8f), COMFORTABLE("Comfortable", 1f), SPACIOUS("Spacious", 1.25f);
		private final String label;
		public final float factor;
		Density(String label, float factor) { this.label = label; this.factor = factor; }
		@Override public String label() { return label; }
	}

	public enum MenuPosition implements Labeled {
		CENTER("Center"), LEFT("Left"), RIGHT("Right");
		private final String label;
		MenuPosition(String label) { this.label = label; }
		@Override public String label() { return label; }
	}

	public enum Palette {
		ELECTRIC("Electric", 0xFF2F8CFF, 0xFF27D3E6),
		VIOLET("Violet", 0xFF8B5CF6, 0xFFC084FC),
		CRIMSON("Crimson", 0xFFEF4444, 0xFFF97373),
		EMBER("Ember", 0xFFF97316, 0xFFFBBF24),
		MINT("Mint", 0xFF10B981, 0xFF5EEAD4),
		MONO("Mono", 0xFFD4D7DD, 0xFF8A909C);

		public final String label;
		public final int accent;
		public final int secondary;

		Palette(String label, int accent, int secondary) {
			this.label = label;
			this.accent = accent;
			this.secondary = secondary;
		}
	}

	private static final List<Setting<?>> SETTINGS = new ArrayList<>();
	private static final MenuPage OWNER = MenuPage.THEME;

	public static final TextSetting CLIENT_NAME = add(new TextSetting("theme.client_name", "Client name",
			"The name shown in the logo and headers.", "VANTA", 16));
	public static final ColorSetting ACCENT = add(new ColorSetting("theme.accent", "Accent color",
			"Main highlight color for buttons, toggles and selection.", Palette.ELECTRIC.accent, false));
	public static final ColorSetting SECONDARY = add(new ColorSetting("theme.secondary", "Secondary color",
			"Second accent used in gradients and the logo.", Palette.ELECTRIC.secondary, false));
	public static final ColorSetting BACKGROUND = add(new ColorSetting("theme.background", "Background color",
			"Base color behind menus.", 0xFF0A0C10, false));
	public static final ColorSetting PANEL = add(new ColorSetting("theme.panel", "Panel color",
			"Color of cards and panels.", 0xFF14171D, false));
	public static final ColorSetting BORDER = add(new ColorSetting("theme.border", "Border color",
			"Thin outline around panels and cards.", 0xFF252A33, false));
	public static final ColorSetting TEXT = add(new ColorSetting("theme.text", "Text color",
			"Primary text color.", 0xFFE7EAF0, false));
	public static final IntSetting PANEL_OPACITY = add(new IntSetting("theme.panel_opacity", "Panel opacity",
			"How solid panels look. Lower values let the background show through.", 92, 40, 100, 1, "%"));
	public static final IntSetting CORNER_RADIUS = add(new IntSetting("theme.corner_radius", "Corner radius",
			"Roundness of panels, cards and buttons.", 3, 0, 6, 1, "px"));
	public static final IntSetting UI_SCALE = add(new IntSetting("theme.ui_scale", "Interface scale",
			"Size of VANTA menus relative to Minecraft's GUI scale. Shrinks automatically if the window is too small.", 100, 70, 150, 5, "%"));
	public static final IntSetting FONT_SCALE = add(new IntSetting("theme.font_scale", "Heading scale",
			"Size of large headings and the logo.", 100, 75, 150, 5, "%"));
	public static final ChoiceSetting<Layout> LAYOUT = add(new ChoiceSetting<>("theme.layout", "Module layout",
			"Show modules as a grid of cards or as a list.", Layout.GRID));
	public static final ChoiceSetting<Density> DENSITY = add(new ChoiceSetting<>("theme.density", "Density",
			"Spacing between cards and rows.", Density.COMFORTABLE));
	public static final ChoiceSetting<MenuPosition> MENU_POSITION = add(new ChoiceSetting<>("theme.menu_position", "Menu position",
			"Where the RIGHT SHIFT menu sits on screen.", MenuPosition.CENTER));
	public static final IntSetting MENU_WIDTH = add(new IntSetting("theme.menu_width", "Menu width",
			"Width of the RIGHT SHIFT menu as a share of the screen.", 84, 55, 100, 1, "%"));
	public static final IntSetting SIDEBAR_WIDTH = add(new IntSetting("theme.sidebar_width", "Sidebar width",
			"Width of the navigation sidebar.", 96, 72, 150, 2, "px"));
	public static final IntSetting BACKGROUND_DIM = add(new IntSetting("theme.background_dim", "Background dimming",
			"How much the game is darkened behind the in-game menu.", 55, 0, 95, 1, "%"));
	public static final IntSetting ANIMATION_SPEED = add(new IntSetting("theme.animation_speed", "Animation speed",
			"Speed of menu transitions. Higher is faster.", 100, 50, 300, 10, "%"));
	public static final IntSetting ANIMATION_INTENSITY = add(new IntSetting("theme.animation_intensity", "Animation intensity",
			"How far elements slide when menus open.", 100, 0, 200, 10, "%"));
	public static final BoolSetting REDUCED_MOTION = add(new BoolSetting("theme.reduced_motion", "Reduced motion",
			"Turns off all menu animations and moving backgrounds.", false));
	public static final BoolSetting SHOW_LOGO = add(new BoolSetting("theme.show_logo", "Show logo",
			"Show the client name in sidebars.", true));
	public static final BoolSetting SHOW_MENU_FPS = add(new BoolSetting("theme.show_menu_fps", "FPS in menus",
			"Show a small live FPS readout in the corner of VANTA menus.", true));

	private Theme() {
	}

	private static <S extends Setting<?>> S add(S setting) {
		SETTINGS.add(setting);
		return SettingRegistry.register(setting, OWNER);
	}

	/** Forces class loading so the settings are registered before the config loads. */
	public static void init() {
	}

	public static List<Setting<?>> settings() { return SETTINGS; }

	public static void applyPalette(Palette palette) {
		ACCENT.set(palette.accent);
		SECONDARY.set(palette.secondary);
	}

	public static void restoreDefaults() {
		for (Setting<?> s : SETTINGS) s.reset();
	}

	// ---- Derived colors (cheap; computed on demand from cached setting values) ----

	public static int accent() { return ACCENT.argb(); }
	public static int secondary() { return SECONDARY.argb(); }
	public static int text() { return TEXT.argb(); }
	public static int textDim() { return Colors.mix(TEXT.argb(), PANEL.argb(), 0.42f); }
	public static int textFaint() { return Colors.mix(TEXT.argb(), PANEL.argb(), 0.62f); }
	public static int border() { return BORDER.argb(); }
	public static int background() { return BACKGROUND.argb(); }

	public static int panel() {
		return Colors.withAlpha(PANEL.argb(), PANEL_OPACITY.value() / 100f);
	}

	public static int panelRaised() {
		return Colors.withAlpha(Colors.mix(PANEL.argb(), TEXT.argb(), 0.05f), PANEL_OPACITY.value() / 100f);
	}

	public static int panelHover() {
		return Colors.withAlpha(Colors.mix(PANEL.argb(), TEXT.argb(), 0.10f), PANEL_OPACITY.value() / 100f);
	}

	public static int sidebar() {
		return Colors.withAlpha(Colors.mix(BACKGROUND.argb(), PANEL.argb(), 0.5f), Math.min(1f, PANEL_OPACITY.value() / 100f + 0.04f));
	}

	public static int radius() { return CORNER_RADIUS.value(); }
	public static float uiScale() { return UI_SCALE.value() / 100f; }
	public static float headingScale() { return FONT_SCALE.value() / 100f; }
	public static float density() { return DENSITY.get().factor; }
	public static boolean reducedMotion() { return REDUCED_MOTION.on() || ANIMATION_INTENSITY.value() == 0; }

	/** Duration multiplier for animations; 0 when motion is reduced. */
	public static float animationTimeScale() {
		return reducedMotion() ? 0f : 100f / ANIMATION_SPEED.value();
	}

	public static float animationDistance() {
		return reducedMotion() ? 0f : ANIMATION_INTENSITY.value() / 100f;
	}

	public static String clientName() {
		String name = CLIENT_NAME.get();
		return name.isEmpty() ? "VANTA" : name;
	}
}
