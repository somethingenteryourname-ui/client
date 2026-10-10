package vanta.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;

import vanta.client.VantaClient;

/**
 * Loads and saves all VANTA settings to config/vanta/vanta.json.
 * <ul>
 * <li>Missing file: defaults are written.</li>
 * <li>Malformed file: it is copied to vanta.json.broken-&lt;time&gt; and defaults are used. Nothing crashes.</li>
 * <li>Unknown or invalid individual values are ignored and keep their defaults.</li>
 * <li>Saves are atomic (write temp file, then move) and debounced; the file is never read per frame.</li>
 * </ul>
 */
public final class ConfigManager {
	public static final int CONFIG_VERSION = 1;
	private static final ConfigManager INSTANCE = new ConfigManager();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final long SAVE_DELAY_MS = 1500;

	private final Path dir = FabricLoader.getInstance().getConfigDir().resolve("vanta");
	private final Path file = dir.resolve("vanta.json");
	private boolean dirty;
	private long dirtySince;
	private boolean loading;
	private String lastProblem;

	private ConfigManager() {
	}

	public static ConfigManager get() { return INSTANCE; }

	public Path directory() { return dir; }

	/** A human-readable description of the last load/save problem, or null. Shown on the dashboard. */
	public String lastProblem() { return lastProblem; }

	public void markDirty() {
		if (loading) return;
		if (!dirty) dirtySince = System.currentTimeMillis();
		dirty = true;
	}

	public void load() {
		loading = true;
		try {
			Files.createDirectories(dir);
			if (!Files.exists(file)) {
				VantaClient.LOG.info("[VANTA] No config found, writing defaults to {}", file);
				loading = false;
				saveNow();
				return;
			}

			JsonObject root;
			try {
				String text = Files.readString(file, StandardCharsets.UTF_8);
				JsonElement parsed = JsonParser.parseString(text);
				if (!parsed.isJsonObject()) throw new IllegalStateException("top level is not a JSON object");
				root = parsed.getAsJsonObject();
			} catch (Exception e) {
				Path broken = dir.resolve("vanta.json.broken-" + stamp());
				Files.copy(file, broken, StandardCopyOption.REPLACE_EXISTING);
				lastProblem = "Config file was unreadable (" + shortMessage(e) + "). Saved a copy as " + broken.getFileName() + " and loaded defaults.";
				VantaClient.LOG.warn("[VANTA] {}", lastProblem);
				loading = false;
				saveNow();
				return;
			}

			int version = root.has("configVersion") && root.get("configVersion").isJsonPrimitive()
					? root.get("configVersion").getAsInt() : 0;
			if (version > CONFIG_VERSION) {
				// Written by a newer VANTA. Keep a backup so a downgrade never destroys it.
				Files.copy(file, dir.resolve("vanta.json.from-v" + version), StandardCopyOption.REPLACE_EXISTING);
				lastProblem = "Config was made by a newer VANTA version; loaded what this version understands.";
			}
			JsonObject settings = migrate(root, version);

			int ignored = 0;
			for (Map.Entry<String, JsonElement> entry : settings.entrySet()) {
				Setting<?> setting = SettingRegistry.byId(entry.getKey());
				if (setting == null) continue; // removed or renamed setting
				if (!setting.load(entry.getValue())) {
					setting.setDefaultSilently();
					ignored++;
				}
			}
			if (ignored > 0) {
				lastProblem = ignored + " setting value(s) were invalid and were reset to defaults.";
				VantaClient.LOG.warn("[VANTA] {}", lastProblem);
			}

			// Keep a copy of the last config that loaded successfully.
			Files.copy(file, dir.resolve("vanta.json.bak"), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			lastProblem = "Could not read config: " + e.getMessage();
			VantaClient.LOG.error("[VANTA] Config load failed", e);
		} finally {
			loading = false;
			dirty = false;
		}
	}

	/** Upgrades older config layouts to the current one. */
	private static JsonObject migrate(JsonObject root, int fromVersion) {
		if (root.has("settings") && root.get("settings").isJsonObject()) {
			return root.getAsJsonObject("settings");
		}
		// Version 0 / hand-written files: settings at the top level.
		return root;
	}

	/** Called every client tick; writes the file once changes have settled. */
	public void tick() {
		if (dirty && System.currentTimeMillis() - dirtySince >= SAVE_DELAY_MS) saveNow();
	}

	public void saveIfDirty() {
		if (dirty) saveNow();
	}

	public void saveNow() {
		JsonObject root = new JsonObject();
		root.addProperty("configVersion", CONFIG_VERSION);
		root.addProperty("comment", "VANTA Client settings. Edit in-game with RIGHT SHIFT. Invalid values are reset to defaults.");
		JsonObject settings = new JsonObject();
		for (Setting<?> s : SettingRegistry.all()) settings.add(s.id(), s.toJson());
		root.add("settings", settings);

		try {
			Files.createDirectories(dir);
			Path tmp = dir.resolve("vanta.json.tmp");
			Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
			try {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			}
			dirty = false;
		} catch (IOException e) {
			lastProblem = "Could not save settings: " + e.getMessage();
			VantaClient.LOG.error("[VANTA] Config save failed", e);
		}
	}

	public boolean hasBackup() {
		return Files.exists(dir.resolve("vanta.json.bak"));
	}

	/** Copies the current settings file to vanta.json.&lt;label&gt;-&lt;time&gt;. */
	public void backupCurrent(String label) {
		try {
			saveIfDirty();
			if (Files.exists(file)) {
				Files.copy(file, dir.resolve("vanta.json." + label + "-" + stamp()), StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			VantaClient.LOG.warn("[VANTA] Could not back up settings", e);
		}
	}

	/** Restores vanta.json.bak (the copy made at startup). The current file is backed up first. */
	public void restoreBackup() {
		Path bak = dir.resolve("vanta.json.bak");
		if (!Files.exists(bak)) return;
		try {
			backupCurrent("before-restore");
			// Defaults first, so settings missing from the backup don't keep current values.
			loading = true;
			for (Setting<?> s : SettingRegistry.all()) s.setDefaultSilently();
			loading = false;
			Files.copy(bak, file, StandardCopyOption.REPLACE_EXISTING);
			lastProblem = null;
			load();
		} catch (IOException e) {
			lastProblem = "Could not restore backup: " + e.getMessage();
			VantaClient.LOG.error("[VANTA] Restore failed", e);
		} finally {
			loading = false;
		}
	}

	private static String shortMessage(Exception e) {
		String m = String.valueOf(e.getMessage());
		int nl = m.indexOf('\n');
		if (nl >= 0) m = m.substring(0, nl);
		int colon = m.lastIndexOf("Exception: ");
		if (colon >= 0) m = m.substring(colon + "Exception: ".length());
		return m.length() > 80 ? m.substring(0, 80) + "..." : m;
	}

	private static String stamp() {
		return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
	}
}
