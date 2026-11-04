package dev.eclipxia.pluginupdater;

import org.rusherhack.client.api.feature.command.Command;
import org.rusherhack.core.command.annotations.CommandExecutor;

import java.util.stream.Collectors;

/**
 * *updater command
 */
public class UpdaterCommand extends Command {
	
	private final PluginUpdaterPlugin plugin;
	
	public UpdaterCommand(PluginUpdaterPlugin plugin) {
		super("updater", "Download and auto-update plugins from GitHub releases");
		this.plugin = plugin;
	}
	
	@CommandExecutor
	private String status() {
		return "Usage: *updater <add|remove|list|update> [owner/repo]";
	}
	
	@CommandExecutor(subCommand = "list")
	private String list() {
		if(this.plugin.getConfig().repos.isEmpty()) {
			return "No repos tracked. Use *updater add owner/repo";
		}
		return this.plugin.getConfig().repos.entrySet().stream()
				.map(e -> e.getKey() + " [" + (e.getValue().installedTag.isEmpty() ? "not installed" : e.getValue().installedTag) + "]")
				.collect(Collectors.joining(", "));
	}
	
	@CommandExecutor(subCommand = "add")
	@CommandExecutor.Argument("owner/repo")
	private String add(String repo) {
		if(!Updater.isValidRepo(repo)) {
			return "Invalid repo, expected owner/repo";
		}
		this.plugin.getConfig().repos.putIfAbsent(repo, new UpdaterConfig.Entry());
		this.plugin.saveConfig();
		this.plugin.runAsync(() -> this.plugin.getUpdater().update(repo, this.plugin::notifyUser));
		return "Tracking " + repo + ", downloading latest release...";
	}
	
	@CommandExecutor(subCommand = {"remove", "del"})
	@CommandExecutor.Argument("owner/repo")
	private String remove(String repo) {
		if(this.plugin.getConfig().repos.remove(repo) == null) {
			return repo + " is not tracked";
		}
		this.plugin.saveConfig();
		return "Stopped tracking " + repo + " (the installed jar was left in place)";
	}
	
	@CommandExecutor(subCommand = "update")
	private String update() {
		this.plugin.runUpdateAll();
		return "Checking for updates...";
	}
}
