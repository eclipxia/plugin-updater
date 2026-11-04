package dev.eclipxia.pluginupdater;

import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.feature.module.ModuleCategory;
import org.rusherhack.client.api.feature.module.ToggleableModule;

/**
 * Button-style module in the External tab: enabling it checks all tracked repos for updates, then switches itself off.
 */
public class UpdaterModule extends ToggleableModule {
	
	private final PluginUpdaterPlugin plugin;
	
	public UpdaterModule(PluginUpdaterPlugin plugin) {
		super("PluginUpdater", "Enable to check tracked plugins for updates", ModuleCategory.EXTERNAL);
		this.plugin = plugin;
	}
	
	@Override
	public void onEnable() {
		this.plugin.runUpdateAll();
		RusherHackAPI.schedule(() -> this.setToggled(false));
	}
}
