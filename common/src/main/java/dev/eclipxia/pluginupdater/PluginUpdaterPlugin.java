package dev.eclipxia.pluginupdater;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.plugin.Plugin;
import org.rusherhack.client.api.utils.ChatUtils;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Downloads plugins from GitHub releases and keeps them updated.
 */
public class PluginUpdaterPlugin extends Plugin {
	
	private Path configFile;
	private UpdaterConfig config;
	private Updater updater;
	
	@Override
	public void onLoad() {
		this.configFile = RusherHackAPI.getConfigPath().resolve("plugin-updater.json");
		this.config = UpdaterConfig.load(this.configFile);
		this.updater = new Updater(this.config, this.configFile, this.readMinecraftVersion());
		
		//write defaults so the user can find and edit the file
		this.saveConfig();
		
		RusherHackAPI.getCommandManager().registerFeature(new UpdaterCommand(this));
		RusherHackAPI.getModuleManager().registerFeature(new UpdaterModule(this));
		
		if(this.config.checkOnStartup && !this.config.repos.isEmpty()) {
			this.runUpdateAll();
		}
	}
	
	/** the Minecraft version this jar was built for, taken from the expanded rusherhack-plugin.json */
	private String readMinecraftVersion() {
		try(InputStream in = this.getClass().getResourceAsStream("/rusherhack-plugin.json")) {
			final JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			return json.getAsJsonArray("Minecraft-Versions").get(0).getAsString();
		} catch(Exception e) {
			this.getLogger().warn("Could not read Minecraft version from plugin json: " + e.getMessage());
			return "";
		}
	}
	
	@Override
	public void onUnload() {
		this.getLogger().info("Plugin updater unloaded");
	}
	
	public UpdaterConfig getConfig() {
		return this.config;
	}
	
	public Updater getUpdater() {
		return this.updater;
	}
	
	public void saveConfig() {
		this.config.save(this.configFile);
	}
	
	public void runUpdateAll() {
		this.runAsync(() -> {
			final int changed = this.updater.updateAll(this::notifyUser);
			if(changed > 0) {
				this.notifyUser(changed + " plugin(s) updated. Run *reload (or restart) to apply.");
			}
		});
	}
	
	public void runAsync(Runnable task) {
		final Thread t = new Thread(task, "plugin-updater");
		t.setDaemon(true);
		t.start();
	}
	
	/** logs, and prints to chat when in a world. Safe to call from any thread. */
	public void notifyUser(String message) {
		this.getLogger().info(message);
		Minecraft.getInstance().execute(() -> {
			if(Minecraft.getInstance().player != null) {
				ChatUtils.print("[Updater] " + message);
			}
		});
	}
}
