package dev.eclipxia.pluginupdater;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persistent config stored in rusherhack/config/plugin-updater.json
 */
public class UpdaterConfig {
	
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	
	/** check for updates automatically when the plugin loads */
	public boolean checkOnStartup = true;
	
	/** optional GitHub token (raises rate limits, allows private repos) */
	public String githubToken = "";
	
	/** "owner/repo" -> state */
	public Map<String, Entry> repos = defaultRepos();
	
	/** repos tracked (and therefore auto-installed) when no config file exists yet */
	private static Map<String, Entry> defaultRepos() {
		final Map<String, Entry> map = new LinkedHashMap<>();
		for(String repo : new String[]{
				"eclipxia/auto-raid",
				"eclipxia/ShulkerViewer",
				"eclipxia/CelestialTheme1.21.11RH",
				"eclipxia/CustomCapes"}) {
			map.put(repo, new Entry());
		}
		return map;
	}
	
	public static class Entry {
		/** tag of the release that is currently installed, empty if never installed */
		public String installedTag = "";
		/** file name of the installed jar inside the plugins folder */
		public String fileName = "";
	}
	
	public static UpdaterConfig load(Path path) {
		try {
			if(Files.exists(path)) {
				final UpdaterConfig cfg = GSON.fromJson(Files.readString(path), UpdaterConfig.class);
				if(cfg != null) {
					if(cfg.repos == null) cfg.repos = new LinkedHashMap<>();
					if(cfg.githubToken == null) cfg.githubToken = "";
					return cfg;
				}
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
		return new UpdaterConfig();
	}
	
	public synchronized void save(Path path) {
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(this));
		} catch(IOException e) {
			e.printStackTrace();
		}
	}
}
