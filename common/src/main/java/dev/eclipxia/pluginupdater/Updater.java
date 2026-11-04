package dev.eclipxia.pluginupdater;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.rusherhack.client.api.RusherHackAPI;

import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Checks GitHub releases and downloads jars into the rusherhack plugins folder.
 */
public class Updater {
	
	private static final Pattern REPO_PATTERN = Pattern.compile("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+");
	
	private final HttpClient http = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NORMAL)
			.connectTimeout(Duration.ofSeconds(15))
			.build();
	
	private final HttpClient noRedirectHttp = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NEVER)
			.connectTimeout(Duration.ofSeconds(15))
			.build();
	
	private final UpdaterConfig config;
	private final Path configFile;
	private final String minecraftVersion;
	
	public Updater(UpdaterConfig config, Path configFile, String minecraftVersion) {
		this.config = config;
		this.configFile = configFile;
		this.minecraftVersion = minecraftVersion;
	}
	
	public static boolean isValidRepo(String repo) {
		return REPO_PATTERN.matcher(repo).matches();
	}
	
	public static Path pluginsDir() {
		return RusherHackAPI.getPath().resolve("plugins");
	}
	
	/**
	 * Checks (and installs) updates for every tracked repo. Blocking; run off the game thread.
	 *
	 * @return number of plugins that were installed or updated
	 */
	public int updateAll(Consumer<String> out) {
		int changed = 0;
		for(String repo : new ArrayList<>(this.config.repos.keySet())) {
			if(this.update(repo, out)) {
				changed++;
			}
		}
		return changed;
	}
	
	/**
	 * @return true if a new jar was installed
	 */
	public boolean update(String repo, Consumer<String> out) {
		final UpdaterConfig.Entry entry = this.config.repos.get(repo);
		if(entry == null) {
			out.accept(repo + " is not tracked");
			return false;
		}
		
		try {
			final Path target = entry.fileName.isEmpty() ? null : pluginsDir().resolve(entry.fileName);
			final boolean installed = !entry.installedTag.isEmpty() && target != null && Files.exists(target);
			
			//the web redirect is not rate limited, so only spend an API request when the tag changed
			if(installed && entry.installedTag.equals(this.latestTagFromWeb(repo))) {
				out.accept(repo + " is up to date (" + entry.installedTag + ")");
				return false;
			}
			
			final JsonObject release = this.fetchLatestRelease(repo);
			final String tag = release.get("tag_name").getAsString();
			
			if(tag.equals(entry.installedTag) && installed) {
				out.accept(repo + " is up to date (" + tag + ")");
				return false;
			}
			
			final JsonObject asset = this.pickJarAsset(release);
			if(asset == null) {
				out.accept(repo + " " + tag + ": no .jar asset for Minecraft " + this.minecraftVersion + " in release");
				return false;
			}
			
			final String fileName = safeFileName(asset.get("name").getAsString());
			final Path dest = pluginsDir().resolve(fileName);
			this.download(asset, dest);
			
			//remove the previous jar if the file name changed (e.g. version in the name)
			if(!entry.fileName.isEmpty() && !entry.fileName.equals(fileName)) {
				Files.deleteIfExists(pluginsDir().resolve(entry.fileName));
			}
			
			final String old = entry.installedTag.isEmpty() ? "none" : entry.installedTag;
			entry.installedTag = tag;
			entry.fileName = fileName;
			this.config.save(this.configFile);
			
			out.accept(repo + ": " + old + " -> " + tag + " (" + fileName + ")");
			return true;
		} catch(Exception e) {
			out.accept(repo + ": update failed - " + e.getMessage());
			return false;
		}
	}
	
	/**
	 * github.com/owner/repo/releases/latest redirects to .../releases/tag/<tag>. That goes through the website,
	 * not the API, so it does not use the rate limit. Returns null if it can't be determined (e.g. private repo).
	 */
	private String latestTagFromWeb(String repo) {
		try {
			final HttpResponse<Void> res = this.noRedirectHttp.send(
					HttpRequest.newBuilder(URI.create("https://github.com/" + repo + "/releases/latest"))
							.timeout(Duration.ofSeconds(30))
							.header("User-Agent", "rusherhack-plugin-updater")
							.GET().build(),
					HttpResponse.BodyHandlers.discarding());
			final String location = res.headers().firstValue("Location").orElse("");
			final int i = location.indexOf("/releases/tag/");
			return res.statusCode() / 100 == 3 && i >= 0 ? URLDecoder.decode(location.substring(i + "/releases/tag/".length()), StandardCharsets.UTF_8) : null;
		} catch(Exception e) {
			return null;
		}
	}
	
	private JsonObject fetchLatestRelease(String repo) throws Exception {
		final HttpResponse<String> res = this.http.send(
				this.request(URI.create("https://api.github.com/repos/" + repo + "/releases/latest"), "application/vnd.github+json"),
				HttpResponse.BodyHandlers.ofString());
		switch(res.statusCode()) {
			case 200:
				return JsonParser.parseString(res.body()).getAsJsonObject();
			case 404:
				throw new IllegalStateException("repo or release not found (private repos need a githubToken)");
			case 403:
			case 429:
				if("0".equals(res.headers().firstValue("X-RateLimit-Remaining").orElse(""))) {
					final long reset = res.headers().firstValue("X-RateLimit-Reset").map(Long::parseLong).orElse(0L);
					final long minutes = Math.max(1, (reset - System.currentTimeMillis() / 1000 + 59) / 60);
					throw new IllegalStateException("GitHub rate limit reached, try again in " + minutes + " min (a githubToken in plugin-updater.json raises the limit)");
				}
				throw new IllegalStateException("GitHub refused the request (HTTP " + res.statusCode() + ")");
			default:
				throw new IllegalStateException("GitHub returned HTTP " + res.statusCode());
		}
	}
	
	/**
	 * Picks the .jar asset for the running Minecraft version. Releases that ship one jar per
	 * version (name contains the version) get the matching one; sources/javadoc/dev jars are ignored.
	 * A release with a single jar is used as-is.
	 */
	private JsonObject pickJarAsset(JsonObject release) {
		final JsonArray assets = release.getAsJsonArray("assets");
		if(assets == null) return null;
		
		final List<JsonObject> jars = new ArrayList<>();
		for(JsonElement el : assets) {
			final JsonObject a = el.getAsJsonObject();
			final String n = a.get("name").getAsString().toLowerCase(Locale.ROOT);
			if(n.endsWith(".jar") && !n.contains("sources") && !n.contains("javadoc") && !n.contains("-dev")) {
				jars.add(a);
			}
		}
		
		final Pattern version = Pattern.compile("(?<![0-9.])" + Pattern.quote(this.minecraftVersion) + "(?![0-9])");
		for(JsonObject a : jars) {
			if(version.matcher(a.get("name").getAsString()).find()) {
				return a;
			}
		}
		return jars.size() == 1 ? jars.get(0) : null;
	}
	
	private void download(JsonObject asset, Path dest) throws Exception {
		Files.createDirectories(dest.getParent());
		final Path part = dest.resolveSibling(dest.getFileName() + ".part");
		
		//public repos: the plain download url is not rate limited. With a token use the API url so private repos work
		final String url = this.config.githubToken.isBlank() ? asset.get("browser_download_url").getAsString() : asset.get("url").getAsString();
		final HttpResponse<InputStream> res = this.http.send(
				this.request(URI.create(url), "application/octet-stream"),
				HttpResponse.BodyHandlers.ofInputStream());
		try(InputStream in = res.body()) {
			if(res.statusCode() != 200) {
				throw new IllegalStateException("download returned HTTP " + res.statusCode());
			}
			Files.copy(in, part, StandardCopyOption.REPLACE_EXISTING);
		}
		
		//sanity check: jars are zip files
		try(InputStream in = Files.newInputStream(part)) {
			if(in.read() != 'P' || in.read() != 'K') {
				Files.deleteIfExists(part);
				throw new IllegalStateException("downloaded file is not a valid jar");
			}
		}
		
		Files.move(part, dest, StandardCopyOption.REPLACE_EXISTING);
	}
	
	private HttpRequest request(URI uri, String accept) {
		final HttpRequest.Builder b = HttpRequest.newBuilder(uri)
				.timeout(Duration.ofSeconds(60))
				.header("Accept", accept)
				.header("User-Agent", "rusherhack-plugin-updater")
				.GET();
		if(!this.config.githubToken.isBlank()) {
			b.header("Authorization", "Bearer " + this.config.githubToken.trim());
		}
		return b.build();
	}
	
	/** prevents path traversal from a malicious asset name */
	private static String safeFileName(String name) {
		return Path.of(name).getFileName().toString();
	}
}
