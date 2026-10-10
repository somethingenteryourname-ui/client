package vanta.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import vanta.branding.Theme;
import vanta.client.VantaInfo;
import vanta.compat.CompatDetector;
import vanta.config.ConfigManager;
import vanta.config.Setting;
import vanta.config.SettingRegistry;
import vanta.gui.items.BasicItems;
import vanta.gui.items.Item;
import vanta.gui.items.LayoutBuilder;
import vanta.gui.items.MenuHost;
import vanta.gui.items.ModuleCard;
import vanta.gui.items.SettingRow;
import vanta.hud.HudEditorScreen;
import vanta.hud.HudModule;
import vanta.input.VantaKeys;
import vanta.metrics.FrameMetrics;
import vanta.modules.Category;
import vanta.modules.Module;
import vanta.modules.ModuleRegistry;
import vanta.ui.Anim;
import vanta.ui.Colors;
import vanta.ui.Icons;
import vanta.ui.Ui;

/** The VANTA client menu (RIGHT SHIFT). Also opened from the home screen. */
public final class VantaMenuScreen extends VantaScreen implements MenuHost {
	private static MenuPage lastPage = MenuPage.HOME;

	private MenuPage page;
	private Module moduleView;
	private Category categoryFilter;
	private String query = "";
	private int selectedResult;
	private final List<BasicItems.ResultRow> results = new ArrayList<>();

	private List<Item> items = List.of();
	private int contentHeight;
	private float scroll;
	private float scrollTarget;
	private Item dragItem;
	private boolean needsRebuild = true;
	private long pageSwitchedAt;
	private float navIndicatorY = -1;
	private long lastFrameNanos;
	private int layoutSignature;

	private String highlightKey;
	private long highlightUntil;

	private EditBox search;

	// Layout (virtual pixels)
	private int panelX, panelY, panelW, panelH, sidebarW;
	private int bodyX, bodyY, bodyW, bodyH;
	private int searchX, searchY, searchW;
	private int offsetY;
	private int bodyOffsetX;

	public VantaMenuScreen(Screen parent) {
		this(parent, MenuSettings.REMEMBER_PAGE.on() ? lastPage : MenuPage.HOME);
	}

	public VantaMenuScreen(Screen parent, MenuPage page) {
		super(Component.literal("VANTA"), parent);
		this.page = page;
	}

	public static VantaMenuScreen forModule(Screen parent, Module module) {
		VantaMenuScreen s = new VantaMenuScreen(parent, MenuPage.MODULES);
		s.moduleView = module;
		return s;
	}

	@Override protected int minVirtualWidth() { return 400; }
	@Override protected int minVirtualHeight() { return 240; }

	private static int computeSignature() {
		int h = Theme.UI_SCALE.value();
		h = h * 31 + Theme.MENU_WIDTH.value();
		h = h * 31 + Theme.SIDEBAR_WIDTH.value();
		h = h * 31 + Theme.DENSITY.get().ordinal();
		h = h * 31 + Theme.LAYOUT.get().ordinal();
		h = h * 31 + Theme.MENU_POSITION.get().ordinal();
		h = h * 31 + (MenuSettings.SHOW_DESCRIPTIONS.on() ? 1 : 0);
		return h;
	}

	@Override
	protected void layout() {
		layoutSignature = computeSignature();
		sidebarW = Math.min(Theme.SIDEBAR_WIDTH.value(), vw / 3);
		panelW = Math.max(Math.min(380, vw - 8), Math.min(vw - 16, Math.round(vw * Theme.MENU_WIDTH.value() / 100f)));
		panelH = Math.min(vh - 16, Math.max(230, Math.round(vh * 0.86f)));
		panelX = switch (Theme.MENU_POSITION.get()) {
			case LEFT -> 8;
			case RIGHT -> vw - panelW - 8;
			case CENTER -> (vw - panelW) / 2;
		};
		panelY = (vh - panelH) / 2;
		int contentX = panelX + sidebarW;
		int contentW = panelW - sidebarW;
		bodyX = contentX + 10;
		bodyY = panelY + 40;
		bodyW = contentW - 20;
		bodyH = panelH - 40 - 18;

		searchW = Math.max(90, Math.min(170, contentW * 2 / 5));
		searchX = contentX + contentW - 10 - searchW;
		searchY = panelY + 11;

		String value = search != null ? search.getValue() : query;
		search = new EditBox(font, toReal(searchX + 16), toReal(searchY + 4), toReal(searchW - 20), toReal(10), Component.literal("Search"));
		search.setBordered(false);
		search.setMaxLength(48);
		search.setTextColor(Theme.text());
		search.setHint(Component.literal("Search everything"));
		search.setValue(value);
		search.setResponder(this::onSearchChanged);
		addRenderableWidget(search);
		needsRebuild = true;
	}

	// ------------------------------------------------------------------ content

	private void onSearchChanged(String text) {
		String q = text.strip();
		if (q.equals(query)) return;
		query = q;
		selectedResult = 0;
		scroll = scrollTarget = 0;
		needsRebuild = true;
	}

	@Override
	public void rebuild() { needsRebuild = true; }

	private void buildContent() {
		needsRebuild = false;
		int gap = Math.max(3, Math.round(6 * Theme.density()));
		LayoutBuilder lb = new LayoutBuilder(bodyW, gap);
		if (!query.isEmpty()) buildSearch(lb);
		else if (moduleView != null) buildModule(lb, moduleView);
		else {
			switch (page) {
				case HOME -> buildHome(lb);
				case MODULES -> buildModules(lb);
				case HUD -> buildHud(lb);
				case THEME -> buildTheme(lb);
				case SETTINGS -> buildSettings(lb);
				case ABOUT -> buildAbout(lb);
			}
		}
		items = lb.items;
		contentHeight = lb.contentHeight();
		clampScroll();

		if (highlightKey != null) {
			for (Item it : items) {
				if (highlightKey.equals(it.key())) {
					scrollTarget = Math.max(0, it.y - 12);
					clampScroll();
					scroll = scrollTarget;
					break;
				}
			}
		}
	}

	private void section(LayoutBuilder lb, String title, String sub) {
		lb.full(new BasicItems.SectionTitle(title, sub), BasicItems.SectionTitle.height(sub));
	}

	private void paragraph(LayoutBuilder lb, String text, int color) {
		BasicItems.Paragraph p = new BasicItems.Paragraph(text, bodyW, color);
		lb.full(p, p.preferredHeight());
	}

	private void settingRows(LayoutBuilder lb, List<? extends Setting<?>> settings) {
		for (Setting<?> s : settings) lb.full(new SettingRow(s, this), SettingRow.height());
	}

	private void moduleCards(LayoutBuilder lb, List<? extends Module> modules) {
		boolean list = Theme.LAYOUT.get() == Theme.Layout.LIST;
		List<Item> cards = new ArrayList<>();
		for (Module m : modules) cards.add(new ModuleCard(m, list, this));
		if (list) {
			for (Item c : cards) lb.full(c, ModuleCard.listHeight());
		} else {
			lb.grid(cards, Math.round(150 * Theme.density()), ModuleCard.gridHeight());
		}
	}

	private void buildHome(LayoutBuilder lb) {
		String problem = ConfigManager.get().lastProblem();
		if (problem != null) paragraph(lb, "! " + problem, 0xFFFBBF24);
		String conflict = CompatDetector.titleScreenMods();
		if (conflict != null) paragraph(lb, "! " + conflict + " also changes the title screen. If it looks wrong, turn off Home Screen in Modules.", 0xFFFBBF24);

		section(lb, "Performance", "Measured from real frame times on this machine");
		List<Item> stats = List.of(
				new BasicItems.StatCard("FPS", () -> String.valueOf(FrameMetrics.fps()),
						() -> "avg " + Math.round(FrameMetrics.averageFps()), null),
				new BasicItems.StatCard("Frame time", () -> String.format(Locale.ROOT, "%.1f ms", FrameMetrics.averageFrameMs()),
						() -> String.format(Locale.ROOT, "worst %.1f ms", FrameMetrics.worstFrameMs()), null),
				new BasicItems.StatCard("1% low", () -> Float.isNaN(FrameMetrics.onePercentLow()) ? "..." : String.valueOf(Math.round(FrameMetrics.onePercentLow())),
						() -> "slowest 1% of frames", null),
				new BasicItems.StatCard("Memory", () -> {
					Runtime rt = Runtime.getRuntime();
					return ((rt.totalMemory() - rt.freeMemory()) >> 20) + " MB";
				}, null, () -> {
					Runtime rt = Runtime.getRuntime();
					return (rt.totalMemory() - rt.freeMemory()) / (double) rt.maxMemory();
				}));
		lb.grid(stats, 84, 44);
		lb.full(new BasicItems.GraphCard(), 58);

		section(lb, "Quick controls", null);
		Module fps = ModuleRegistry.byId("fps");
		List<Item> quick = new ArrayList<>();
		quick.add(new BasicItems.ButtonItem("HUD editor", true, this::openHudEditor, "Drag HUD widgets into place."));
		quick.add(new BasicItems.ButtonItem(() -> "FPS overlay: " + (fps.isEnabled() ? "On" : "Off"), false, fps::toggle, "Show or hide the FPS widget."));
		quick.add(new BasicItems.ButtonItem("Theme", false, () -> openPage(MenuPage.THEME), "Colors, layout and animation."));
		quick.add(new BasicItems.ButtonItem("Search", false, this::focusSearch, "Find any module or setting (Tab)."));
		quick.add(new BasicItems.ButtonItem("Restore backup", false, this::confirmRestoreBackup, "Go back to the settings from when the game started."));
		lb.row(quick, 92, 18);

		List<Module> recent = ModuleRegistry.recentlyChanged(3);
		if (!recent.isEmpty()) {
			section(lb, "Recently changed", null);
			for (Module m : recent) lb.full(new ModuleCard(m, true, this), ModuleCard.listHeight());
		}

		section(lb, "Client status", "Everything here is local. VANTA sends no telemetry.");
		statusRows(lb);
	}

	private void statusRows(LayoutBuilder lb) {
		lb.full(new BasicItems.KeyValueRow("Minecraft", VantaInfo::minecraftVersion, 0), 13);
		lb.full(new BasicItems.KeyValueRow("VANTA", VantaInfo::vantaVersion, 0), 13);
		lb.full(new BasicItems.KeyValueRow("Fabric Loader", VantaInfo::loaderVersion, 0), 13);
		lb.full(new BasicItems.KeyValueRow("Fabric API", VantaInfo::fabricApiVersion, 0), 13);
		String mods = CompatDetector.detected().isEmpty() ? "None detected"
				: String.join(", ", CompatDetector.detected().stream().map(CompatDetector.Detected::name).toList());
		lb.full(new BasicItems.KeyValueRow("Detected mods", () -> mods, 0), 13);
		lb.full(new BasicItems.KeyValueRow("Settings file", () -> ConfigManager.get().lastProblem() == null ? "OK" : "See warning above",
				ConfigManager.get().lastProblem() == null ? 0xFF34D399 : 0xFFFBBF24), 13);
	}

	private void buildModules(LayoutBuilder lb) {
		List<Item> chips = new ArrayList<>();
		chips.add(chip("All", null));
		for (Category c : ModuleRegistry.usedCategories()) chips.add(chip(c.label, c));
		int cx = 0;
		int rowY = lb.y;
		for (Item c : chips) {
			int cw = ((BasicItems.ChipItem) c).preferredWidth();
			if (cx + cw > bodyW && cx > 0) {
				cx = 0;
				rowY += 16;
			}
			c.x = cx;
			c.y = rowY;
			c.w = cw;
			c.h = 13;
			lb.items.add(c);
			cx += cw + 4;
		}
		lb.y = rowY + 13 + lb.gap + 2;
		moduleCards(lb, ModuleRegistry.sorted(categoryFilter));
	}

	private Item chip(String label, Category category) {
		return new BasicItems.ChipItem(label, () -> categoryFilter == category, () -> {
			categoryFilter = category;
			scroll = scrollTarget = 0;
			rebuild();
		});
	}

	private void buildHud(LayoutBuilder lb) {
		lb.row(List.of(
				new BasicItems.ButtonItem("Open HUD editor", true, this::openHudEditor, "Drag, scale and hide widgets on the real screen."),
				new BasicItems.ButtonItem("Reset all positions", false, () -> minecraft.setScreen(new ConfirmDialog(this, "Reset HUD layout?",
						"Moves every widget back to its default position and size.", "Reset", () -> {
							for (HudModule m : ModuleRegistry.hud()) {
								m.posX.reset();
								m.posY.reset();
								m.scale.reset();
							}
						})), "Put every widget back where it started.")), 120, 18);
		paragraph(lb, "Turn widgets on here, then place them in the editor. Hidden widgets are not drawn or updated, so they cost nothing.", 0);
		moduleCards(lb, new ArrayList<>(ModuleRegistry.hud()));
	}

	private void buildTheme(LayoutBuilder lb) {
		section(lb, "Palettes", "One click sets the accent and secondary colors");
		List<Item> swatches = new ArrayList<>();
		for (Theme.Palette p : Theme.Palette.values()) swatches.add(new BasicItems.SwatchItem(p));
		lb.grid(swatches, 78, 20);

		section(lb, "Colors", "Shared by the home screen, this menu and the HUD editor");
		settingRows(lb, List.of(Theme.CLIENT_NAME, Theme.ACCENT, Theme.SECONDARY, Theme.BACKGROUND, Theme.PANEL, Theme.BORDER,
				Theme.TEXT, Theme.PANEL_OPACITY, Theme.CORNER_RADIUS));
		section(lb, "Layout", null);
		settingRows(lb, List.of(Theme.UI_SCALE, Theme.FONT_SCALE, Theme.LAYOUT, Theme.DENSITY, Theme.MENU_POSITION,
				Theme.MENU_WIDTH, Theme.SIDEBAR_WIDTH, Theme.BACKGROUND_DIM, Theme.SHOW_LOGO, Theme.SHOW_MENU_FPS));
		section(lb, "Motion", null);
		settingRows(lb, List.of(Theme.ANIMATION_SPEED, Theme.ANIMATION_INTENSITY, Theme.REDUCED_MOTION));
		lb.space(4);
		lb.row(List.of(new BasicItems.ButtonItem("Restore default theme", false, () -> minecraft.setScreen(new ConfirmDialog(this,
				"Restore default theme?", "Resets every color, layout and motion setting on this page.", "Restore", Theme::restoreDefaults)),
				"Undo all theme changes.")), 140, 18);
	}

	private void buildSettings(LayoutBuilder lb) {
		section(lb, "Menu", null);
		settingRows(lb, MenuSettings.settings());

		section(lb, "Home screen", "VANTA's replacement for the title screen");
		lb.row(List.of(new BasicItems.ButtonItem("Home screen options", false, () -> openModule(ModuleRegistry.TITLE_SCREEN),
				"Background, sidebar, buttons and welcome text.")), 140, 18);

		section(lb, "Controls", null);
		paragraph(lb, "Menu key: " + VantaKeys.menuKeyName() + ". Change it in Options > Controls > Key Binds > VANTA Client. "
				+ "A HUD editor key can be set there too. Tab focuses search, Esc goes back.", 0);

		section(lb, "Data", "Settings are saved automatically to your config folder");
		paragraph(lb, ConfigManager.get().directory().toAbsolutePath().toString(), Theme.textFaint());
		List<Item> data = new ArrayList<>();
		data.add(new BasicItems.ButtonItem("Save now", false, () -> ConfigManager.get().saveNow(), "Write settings to disk immediately."));
		data.add(new BasicItems.ButtonItem("Restore backup", false, this::confirmRestoreBackup, "Load the copy saved when the game started."));
		data.add(new BasicItems.ButtonItem("Reset everything", false, () -> minecraft.setScreen(new ConfirmDialog(this,
				"Reset all VANTA settings?", "Every module, widget, theme and menu setting goes back to default.\nThe current file is backed up first.",
				"Reset all", () -> {
					ConfigManager.get().backupCurrent("before-reset");
					SettingRegistry.resetAll();
					ConfigManager.get().saveNow();
				})), "Start fresh. Your current settings are backed up first."));
		lb.row(data, 100, 18);
	}

	private void buildAbout(LayoutBuilder lb) {
		section(lb, Theme.clientName() + " Client", "Version " + VantaInfo.vantaVersion() + " for Minecraft " + VantaInfo.minecraftVersion());
		statusRows(lb);
		section(lb, "Privacy", null);
		paragraph(lb, "VANTA works fully offline. It needs no account, sends no telemetry, and only stores settings in your config folder.", 0);
		section(lb, "Fair play", null);
		paragraph(lb, "VANTA only changes what you see. Keystrokes and CPS display input; nothing clicks, aims or changes reach for you.", 0);
		section(lb, "Compatibility", null);
		if (CompatDetector.detected().isEmpty()) {
			paragraph(lb, "No known optimization or menu mods detected. VANTA works on its own; Sodium, Lithium and similar mods can be added alongside it.", 0);
		} else {
			for (CompatDetector.Detected d : CompatDetector.detected()) {
				paragraph(lb, d.name() + ": " + d.note(), 0);
			}
		}
		section(lb, "This build", null);
		paragraph(lb, "Included: home screen, client menu with search, module system, 8 HUD widgets with a drag-and-drop editor, "
				+ "themes, live FPS / frame-time / 1% low stats, and safe settings storage with backups.\n"
				+ "Coming next: crosshair editor, performance presets, particle controls, profiles with import/export.", 0);
	}

	private void buildModule(LayoutBuilder lb, Module m) {
		paragraph(lb, m.description(), 0);
		lb.full(new SettingRow(m.enabledSetting(), this), SettingRow.height());
		List<Setting<?>> settings = m.visibleSettings();
		if (!settings.isEmpty()) {
			section(lb, "Settings", null);
			settingRows(lb, settings);
		}
		lb.space(4);
		List<Item> actions = new ArrayList<>();
		if (m instanceof HudModule) actions.add(new BasicItems.ButtonItem("Open HUD editor", true, this::openHudEditor, "Place this widget on screen."));
		actions.add(new BasicItems.ButtonItem("Reset module", false, () -> minecraft.setScreen(new ConfirmDialog(this,
				"Reset " + m.name() + "?", "All of its settings go back to default.", "Reset", m::resetToDefaults)),
				"Restore this module's defaults."));
		lb.row(actions, 110, 18);
	}

	private void buildSearch(LayoutBuilder lb) {
		results.clear();
		String q = query.toLowerCase(Locale.ROOT);
		List<Object[]> scored = new ArrayList<>();
		for (MenuPage p : MenuPage.values()) {
			int s = score(q, p.title, p.subtitle);
			if (s > 0) scored.add(new Object[]{s, p.icon, p.title, "Page", (Runnable) () -> openPage(p)});
		}
		for (Module m : ModuleRegistry.all()) {
			int s = score(q, m.name(), m.description() + " " + m.category().label);
			if (s > 0) scored.add(new Object[]{s + 1, m instanceof HudModule ? Icons.HUD : Icons.GRID, m.name(),
					"Module - " + m.category().label, (Runnable) () -> openModule(m)});
		}
		for (Setting<?> st : SettingRegistry.all()) {
			if (st.isHidden()) continue;
			String ownerName = ownerName(st.owner());
			int s = score(q, st.name(), st.description() + " " + ownerName);
			if (s > 0) scored.add(new Object[]{s, Icons.GEAR, st.name(), "Setting - " + ownerName, (Runnable) () -> jumpToSetting(st)});
		}
		scored.sort((a, b) -> Integer.compare((int) b[0], (int) a[0]));
		if (scored.isEmpty()) {
			paragraph(lb, "Nothing matches \"" + query + "\".", 0);
			return;
		}
		int n = Math.min(40, scored.size());
		section(lb, n + (scored.size() > n ? "+" : "") + " results", "Up/Down to choose, Enter to open, Esc to clear");
		for (int i = 0; i < n; i++) {
			Object[] r = scored.get(i);
			final int index = i;
			BasicItems.ResultRow row = new BasicItems.ResultRow((String[]) r[1], (String) r[2], (String) r[3], (Runnable) r[4], () -> selectedResult == index);
			results.add(row);
			lb.full(row, 20);
		}
	}

	private static int score(String q, String name, String extra) {
		String n = name.toLowerCase(Locale.ROOT);
		if (n.equals(q)) return 100;
		if (n.startsWith(q)) return 80;
		if (n.contains(q)) return 60;
		if (extra != null && extra.toLowerCase(Locale.ROOT).contains(q)) return 20;
		return 0;
	}

	private static String ownerName(Object owner) {
		if (owner instanceof Module m) return m.name();
		if (owner instanceof MenuPage p) return p.title;
		return "VANTA";
	}

	private void jumpToSetting(Setting<?> s) {
		Object owner = s.owner();
		clearSearch();
		if (owner instanceof Module m) openModule(m);
		else if (owner instanceof MenuPage p) openPage(p);
		highlight(s.id());
	}

	private void highlight(String key) {
		highlightKey = key;
		highlightUntil = System.currentTimeMillis() + 2500;
		needsRebuild = true;
	}

	private void clearSearch() {
		query = "";
		if (search != null) search.setValue("");
		setFocused(null);
		needsRebuild = true;
	}

	// ------------------------------------------------------------------ MenuHost

	@Override
	public void openPage(MenuPage p) {
		page = p;
		lastPage = p;
		moduleView = null;
		scroll = scrollTarget = 0;
		pageSwitchedAt = System.currentTimeMillis();
		if (!query.isEmpty()) clearSearch();
		needsRebuild = true;
	}

	@Override
	public void openModule(Module m) {
		if (!query.isEmpty()) clearSearch();
		moduleView = m;
		scroll = scrollTarget = 0;
		pageSwitchedAt = System.currentTimeMillis();
		highlight(m.id());
		needsRebuild = true;
	}

	@Override public void beginDrag(Item item) { dragItem = item; }
	@Override public Screen asScreen() { return this; }

	@Override
	public boolean isHighlighted(String key) {
		return highlightKey != null && highlightKey.equals(key) && System.currentTimeMillis() < highlightUntil;
	}

	@Override
	public void focusSearch() {
		if (search == null) return;
		setFocused(search);
		search.setFocused(true);
	}

	private void openHudEditor() {
		minecraft.setScreen(new HudEditorScreen(this));
	}

	private void confirmRestoreBackup() {
		if (!ConfigManager.get().hasBackup()) {
			minecraft.setScreen(new ConfirmDialog(this, "No backup yet", "A backup is made each time the game starts with a valid settings file.", "OK", () -> { }));
			return;
		}
		minecraft.setScreen(new ConfirmDialog(this, "Restore backup?", "Replaces current VANTA settings with the copy made when the game started.\nCurrent settings are backed up first.",
				"Restore", () -> {
					ConfigManager.get().restoreBackup();
					rebuild();
				}));
	}

	// ------------------------------------------------------------------ rendering

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		float dim = Theme.BACKGROUND_DIM.value() / 100f;
		if (minecraft.level == null) {
			// Opened from the home screen: draw a solid themed backdrop.
			g.fill(0, 0, width, height, Theme.background());
		} else if (dim > 0) {
			g.fill(0, 0, width, height, Colors.withAlpha(Theme.background(), dim * openProgress()));
		}
	}

	@Override
	protected void renderScaled(GuiGraphics g, int mx, int my, float delta) {
		long now = System.nanoTime();
		float dt = lastFrameNanos == 0 ? 0f : Math.min(0.1f, (now - lastFrameNanos) / 1e9f);
		lastFrameNanos = now;

		if (dragItem == null && computeSignature() != layoutSignature) {
			resize(width, height); // theme layout changed: re-run layout at the new size
			return;
		}
		if (needsRebuild) buildContent();

		float open = openProgress();
		offsetY = Math.round((1f - open) * 14f * Theme.animationDistance());
		float pageT = Anim.easeOutCubic(Anim.progress(pageSwitchedAt, 160f));
		bodyOffsetX = Math.round((1f - pageT) * 10f * Theme.animationDistance());

		int py = panelY + offsetY;
		// Panel
		Ui.roundRect(g, panelX + 2, py + 3, panelW, panelH, Theme.radius() + 1, 0x40000000);
		Ui.roundRect(g, panelX, py, panelW, panelH, Theme.radius() + 1, Theme.panel());
		Ui.roundRect(g, panelX, py, sidebarW, panelH, Theme.radius() + 1, Theme.sidebar());
		Ui.rect(g, panelX + sidebarW, py, 1, panelH, Theme.border());
		Ui.roundOutline(g, panelX, py, panelW, panelH, Theme.radius() + 1, Theme.border());

		renderSidebar(g, mx, my, py, dt);
		renderHeader(g, mx, my, py);
		renderBody(g, mx, my, py, dt);
	}

	private void renderSidebar(GuiGraphics g, int mx, int my, int py, float dt) {
		int x = panelX;
		int y = py + 10;
		if (Theme.SHOW_LOGO.on()) {
			String name = Theme.clientName();
			float s = Math.min(1.6f * Theme.headingScale(), (sidebarW - 18) / (float) Math.max(1, Ui.width(name)));
			Ui.textScaled(g, name, x + 10, y, s, Theme.accent(), false);
			Ui.rect(g, x + 10, y + Math.round(9 * s) + 2, Math.min(sidebarW - 20, 18), 2, Theme.secondary());
		}
		int navY = py + 36;
		MenuPage active = moduleView != null ? MenuPage.MODULES : page;
		int itemH = 18;
		int targetY = navY + active.ordinal() * (itemH + 2);
		if (navIndicatorY < 0) navIndicatorY = targetY;
		navIndicatorY = Anim.approach(navIndicatorY, targetY, dt, 18f);
		Ui.roundRect(g, x + 6, Math.round(navIndicatorY), sidebarW - 12, itemH, Theme.radius(), Colors.fade(Theme.accent(), 0.18f));
		Ui.rect(g, x + 6, Math.round(navIndicatorY) + 4, 2, itemH - 8, Theme.accent());

		for (MenuPage p : MenuPage.values()) {
			int iy = navY + p.ordinal() * (itemH + 2);
			boolean hov = Ui.inside(mx, my, x + 6, iy, sidebarW - 12, itemH);
			if (hov && p != active) Ui.roundRect(g, x + 6, iy, sidebarW - 12, itemH, Theme.radius(), Theme.panelHover());
			int color = p == active ? Theme.text() : hov ? Theme.text() : Theme.textDim();
			Ui.icon(g, p.icon, x + 13, iy + 5, p == active ? Theme.accent() : color);
			Ui.text(g, Ui.ellipsize(p.title, sidebarW - 40), x + 28, iy + 5, color);
		}
		String hint = VantaKeys.menuKeyName() + " to close";
		Ui.text(g, Ui.ellipsize(hint, sidebarW - 16), x + 10, py + panelH - 14, Theme.textFaint());
	}

	private void renderHeader(GuiGraphics g, int mx, int my, int py) {
		int contentX = panelX + sidebarW;
		int titleX = contentX + 10;
		String title;
		String sub;
		if (!query.isEmpty()) {
			title = "Search";
			sub = "Modules, settings and pages";
		} else if (moduleView != null) {
			boolean backHover = Ui.inside(mx, my, titleX - 2, py + 10, 16, 16);
			Ui.roundRect(g, titleX - 2, py + 10, 16, 16, Theme.radius(), backHover ? Theme.panelHover() : Theme.panelRaised());
			Ui.icon(g, Icons.BACK, titleX + 2, py + 14, backHover ? Theme.accent() : Theme.textDim());
			titleX += 20;
			title = moduleView.name();
			sub = moduleView.category().label + (moduleView.isExperimental() ? " - Experimental" : "");
		} else {
			title = page.title;
			sub = page.subtitle;
		}
		int maxW = searchX - titleX - 8;
		Ui.textScaled(g, Ui.ellipsize(title, (int) (maxW / (1.25f * Theme.headingScale()))), titleX, py + 9, 1.25f * Theme.headingScale(), Theme.text(), false);
		Ui.text(g, Ui.ellipsize(sub, maxW), titleX, py + 23, Theme.textFaint());

		// Search box frame (the vanilla EditBox draws the text on top).
		int sy = searchY + offsetY;
		boolean focused = search != null && search.isFocused();
		Ui.roundRect(g, searchX, sy, searchW, 16, Theme.radius(), Colors.withAlpha(Theme.background(), 0.6f));
		Ui.roundOutline(g, searchX, sy, searchW, 16, Theme.radius(), focused ? Theme.accent() : Theme.border());
		Ui.icon(g, Icons.SEARCH, searchX + 4, sy + 4, focused ? Theme.accent() : Theme.textFaint());
	}

	private void renderBody(GuiGraphics g, int mx, int my, int py, float dt) {
		scroll = Anim.approach(scroll, scrollTarget, dt, 22f);
		int by = bodyY + offsetY;
		int ox = bodyX + bodyOffsetX;
		int oy = by - Math.round(scroll);
		boolean mouseInBody = Ui.inside(mx, my, bodyX, by, bodyW, bodyH);
		String hint = null;

		g.enableScissor(bodyX, by, bodyX + bodyW, by + bodyH);
		for (Item it : items) {
			int ax = ox + it.x;
			int ay = oy + it.y;
			if (ay + it.h < by || ay > by + bodyH) continue; // off-screen: skip entirely
			boolean hovered = mouseInBody && Ui.inside(mx, my, ax, ay, it.w, it.h);
			it.render(g, ax, ay, mx, my, hovered);
			if (hovered) hint = it.hint();
		}
		g.disableScissor();

		// Scrollbar
		if (contentHeight > bodyH) {
			int trackX = bodyX + bodyW + 4;
			int thumbH = Math.max(16, bodyH * bodyH / contentHeight);
			int thumbY = by + Math.round((bodyH - thumbH) * (scroll / Math.max(1, contentHeight - bodyH)));
			Ui.roundRect(g, trackX, by, 2, bodyH, 1, Colors.fade(Theme.border(), 0.8f));
			Ui.roundRect(g, trackX, thumbY, 2, thumbH, 1, Theme.accent());
		}

		// Footer: hovered item's explanation.
		int fy = py + panelH - 14;
		int contentX = panelX + sidebarW;
		String footer = hint != null ? hint
				: !query.isEmpty() ? "Esc clears the search"
				: moduleView != null ? "Esc or Backspace to go back"
				: "Tab to search  -  hover anything for details";
		Ui.text(g, Ui.ellipsize(footer, bodyW - 50), contentX + 10, fy, Theme.textDim());
		drawMenuFps(g, panelX + panelW - 10, fy);
	}

	// ------------------------------------------------------------------ input

	private Item itemAt(double mx, double my) {
		int by = bodyY + offsetY;
		if (!Ui.inside(mx, my, bodyX, by, bodyW, bodyH)) return null;
		int ox = bodyX + bodyOffsetX;
		int oy = by - Math.round(scroll);
		for (int i = items.size() - 1; i >= 0; i--) {
			Item it = items.get(i);
			if (Ui.inside(mx, my, ox + it.x, oy + it.y, it.w, it.h)) return it;
		}
		return null;
	}

	@Override
	protected boolean onClick(double mx, double my, int button) {
		int py = panelY + offsetY;
		// Sidebar navigation
		int navY = py + 36;
		for (MenuPage p : MenuPage.values()) {
			int iy = navY + p.ordinal() * 20;
			if (Ui.inside(mx, my, panelX + 6, iy, sidebarW - 12, 18)) {
				MenuSettings.click();
				openPage(p);
				return true;
			}
		}
		// Back button
		if (moduleView != null && query.isEmpty() && Ui.inside(mx, my, panelX + sidebarW + 8, py + 10, 16, 16)) {
			MenuSettings.click();
			goBack();
			return true;
		}
		// Search frame
		if (Ui.inside(mx, my, searchX, searchY + offsetY, searchW, 16)) {
			focusSearch();
			return true;
		}
		Item it = itemAt(mx, my);
		if (it != null) {
			int ax = bodyX + bodyOffsetX + it.x;
			int ay = bodyY + offsetY - Math.round(scroll) + it.y;
			return it.click(ax, ay, mx, my, button);
		}
		// Click outside the panel closes the menu when in-game.
		if (!Ui.inside(mx, my, panelX, py, panelW, panelH) && minecraft.level != null) {
			onClose();
			return true;
		}
		return false;
	}

	@Override
	protected boolean onDrag(double mx, double my, int button) {
		if (dragItem == null) return false;
		int ax = bodyX + bodyOffsetX + dragItem.x;
		int ay = bodyY + offsetY - Math.round(scroll) + dragItem.y;
		return dragItem.drag(ax, ay, mx, my);
	}

	@Override
	protected void onRelease(double mx, double my, int button) {
		if (dragItem != null) {
			dragItem.release();
			dragItem = null;
		}
	}

	@Override
	protected boolean onScroll(double mx, double my, double amount) {
		Item it = itemAt(mx, my);
		if (it != null) {
			int ax = bodyX + bodyOffsetX + it.x;
			int ay = bodyY + offsetY - Math.round(scroll) + it.y;
			if (it.scroll(ax, ay, mx, my, amount)) return true;
		}
		scrollTarget -= (float) (amount * 24);
		clampScroll();
		return true;
	}

	private void clampScroll() {
		float max = Math.max(0, contentHeight - bodyH);
		scrollTarget = Math.max(0, Math.min(max, scrollTarget));
		scroll = Math.max(0, Math.min(max, scroll));
	}

	private void goBack() {
		moduleView = null;
		scroll = scrollTarget = 0;
		pageSwitchedAt = System.currentTimeMillis();
		needsRebuild = true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		boolean searchFocused = search != null && search.isFocused();
		if (key == GLFW.GLFW_KEY_ESCAPE) {
			if (!query.isEmpty()) clearSearch();
			else if (searchFocused) setFocused(null);
			else if (moduleView != null) goBack();
			else onClose();
			return true;
		}
		if (!query.isEmpty() && !results.isEmpty()) {
			if (key == GLFW.GLFW_KEY_DOWN) {
				selectedResult = Math.min(results.size() - 1, selectedResult + 1);
				ensureVisible(results.get(selectedResult));
				return true;
			}
			if (key == GLFW.GLFW_KEY_UP) {
				selectedResult = Math.max(0, selectedResult - 1);
				ensureVisible(results.get(selectedResult));
				return true;
			}
			if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
				results.get(Math.min(selectedResult, results.size() - 1)).activate();
				return true;
			}
		}
		if (!searchFocused) {
			if (key == GLFW.GLFW_KEY_TAB) {
				focusSearch();
				return true;
			}
			if (key == GLFW.GLFW_KEY_BACKSPACE && moduleView != null) {
				goBack();
				return true;
			}
			if (MenuSettings.CLOSE_WITH_KEY.on() && VantaKeys.isMenuKey(event)) {
				onClose();
				return true;
			}
		}
		return super.keyPressed(event);
	}

	private void ensureVisible(Item it) {
		if (it.y < scrollTarget) scrollTarget = it.y;
		else if (it.y + it.h > scrollTarget + bodyH) scrollTarget = it.y + it.h - bodyH;
		clampScroll();
	}

	@Override
	public boolean isPauseScreen() {
		return MenuSettings.PAUSE_SINGLEPLAYER.on();
	}

	@Override
	public void onClose() {
		lastPage = page;
		super.onClose();
	}
}
