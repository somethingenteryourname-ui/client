package vanta.gui;

import vanta.ui.Icons;

/** The pages in the RIGHT SHIFT menu sidebar. Only pages with real content are listed. */
public enum MenuPage {
	HOME("Home", "Dashboard: live performance, quick controls and client status", Icons.HOME),
	MODULES("Modules", "Turn features on and off and configure them", Icons.GRID),
	HUD("HUD", "Choose HUD widgets and open the drag-and-drop editor", Icons.HUD),
	THEME("Theme", "Colors, layout, scale and animation for every VANTA screen", Icons.PALETTE),
	SETTINGS("Settings", "Menu behaviour, title screen and data", Icons.GEAR),
	ABOUT("About", "Versions, compatibility and known limitations", Icons.INFO);

	public final String title;
	public final String subtitle;
	public final String[] icon;

	MenuPage(String title, String subtitle, String[] icon) {
		this.title = title;
		this.subtitle = subtitle;
		this.icon = icon;
	}
}
