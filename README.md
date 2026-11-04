# Plugin Updater

A [RusherHack](https://rusherhack.org) plugin that downloads your plugins from GitHub releases and keeps them updated when you publish a new release.

## Usage

| Command | Description |
|---|---|
| `*updater add owner/repo` | Track a repo and download its latest release |
| `*updater remove owner/repo` | Stop tracking (the installed jar is left in place) |
| `*updater list` | Show tracked repos and installed tags |
| `*updater update` | Check all tracked repos now |

There is also a `PluginUpdater` module (External tab). Enabling it checks all tracked repos, then switches itself off.

Repos are also checked on startup. After an update, run `*reload` (or restart) to load the new jar. Plugins that use mixins (for example CustomCapes) are applied at game start, so those need a full restart.

For each release it installs the `.jar` asset whose name contains the running Minecraft version (e.g. `auto-raid-26.2-1.0.0.jar`). A release with a single jar is used as-is. `sources`, `javadoc` and `-dev` jars are ignored.

On first run it tracks and installs `eclipxia/auto-raid`, `eclipxia/ShulkerViewer`, `eclipxia/CelestialTheme1.21.11RH` and `eclipxia/CustomCapes`.

Config: `rusherhack/config/plugin-updater.json` (`checkOnStartup`, optional `githubToken`, tracked `repos`).

## Supported versions

1.21.4, 1.21.11 and 26.2, one jar each: `plugin-updater-<minecraft version>-<plugin version>.jar`.

## Project layout

Same multi-project layout as the other multi-version plugins.

```
common/      shared code and resources (dev.eclipxia.pluginupdater)
<version>/   build.gradle + gradle.properties for one Minecraft version
```

## Building

Requires JDK 25.

```sh
./gradlew build              # every version
./gradlew :26.2:build        # one version
```

Jars end up in `<version>/build/libs/`. Install into `rusherhack/plugins` and launch with `-Drusherhack.enablePlugins=true`.
