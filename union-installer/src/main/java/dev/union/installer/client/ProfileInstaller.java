package dev.union.installer.client;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import dev.union.installer.util.JsonReader;
import dev.union.installer.util.JsonWriter;

/**
 * Patches the vanilla Minecraft launcher's {@code launcher_profiles.json} to add (or update)
 * an entry for the Union profile so it shows up in the launcher dropdown without the user
 * having to create it by hand.
 *
 * <p>Uses the loader's {@code JsonReader} for parsing to avoid pulling a second JSON dep; we
 * access it from the installer classpath.
 */
public final class ProfileInstaller {
	private ProfileInstaller() { }

	@SuppressWarnings("unchecked")
	public static void addOrReplace(Path mcDir, String profileName) throws IOException {
		Path file = mcDir.resolve("launcher_profiles.json");

		Map<String, Object> root;

		if (Files.exists(file)) {
			try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				root = JsonReader.parseObject(r);
			}
		} else {
			root = new LinkedHashMap<>();
		}

		Object profilesRaw = root.get("profiles");
		Map<String, Object> profiles;

		if (profilesRaw instanceof Map) {
			profiles = (Map<String, Object>) profilesRaw;
		} else {
			profiles = new LinkedHashMap<>();
			root.put("profiles", profiles);
		}

		Map<String, Object> entry = new LinkedHashMap<>();
		entry.put("name", profileName);
		entry.put("type", "custom");
		entry.put("created", Instant.now().toString());
		entry.put("lastUsed", Instant.EPOCH.toString());
		entry.put("icon", "Furnace");
		entry.put("lastVersionId", profileName);

		profiles.put(profileName, entry);

		if (!root.containsKey("version")) root.put("version", 3);

		Files.writeString(file, JsonWriter.toJson(root), StandardCharsets.UTF_8);
	}
}
