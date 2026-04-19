package dev.union.installer.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import dev.union.installer.Constants;
import dev.union.installer.util.JsonWriter;
import dev.union.installer.util.LoaderExtractor;
import dev.union.installer.util.Progress;

/**
 * Orchestrates a client-side Union install:
 * <ol>
 *   <li>Extract the embedded loader JAR into {@code <mcDir>/libraries/dev/union/...}.</li>
 *   <li>Write the profile JSON under {@code <mcDir>/versions/<profile>/<profile>.json}.</li>
 *   <li>Optionally add a launcher profile entry (see {@link ProfileInstaller}).</li>
 * </ol>
 */
public final class ClientInstaller {
	private ClientInstaller() { }

	public static InstallResult install(Path mcDir, String mcVersion, boolean addLauncherProfile,
			Progress progress) throws IOException {
		String loaderVersion = LoaderExtractor.readEmbeddedLoaderVersion();
		String profileName = Constants.profileName(loaderVersion, mcVersion);

		progress.update("Extracting loader " + loaderVersion);
		Path loaderJar = LoaderExtractor.extractLoaderJar(mcDir, loaderVersion);

		progress.update("Writing profile " + profileName);
		Path profileDir = mcDir.resolve("versions").resolve(profileName);
		Files.createDirectories(profileDir);

		Path profileJson = profileDir.resolve(profileName + ".json");
		Map<String, Object> profile = ProfileWriter.build(loaderVersion, mcVersion);
		Files.writeString(profileJson, JsonWriter.toJson(profile), StandardCharsets.UTF_8);

		// The vanilla launcher also requires an (empty) JAR file at versions/<profile>/<profile>.jar
		// to be present for some versions. Fabric creates an empty one; mirror that.
		Path profileJar = profileDir.resolve(profileName + ".jar");
		if (!Files.exists(profileJar)) {
			Files.write(profileJar, new byte[0]);
		}

		if (addLauncherProfile) {
			progress.update("Updating launcher_profiles.json");
			ProfileInstaller.addOrReplace(mcDir, profileName);
		}

		progress.update("Done");
		return new InstallResult(loaderVersion, profileName, loaderJar, profileJson);
	}

	public record InstallResult(String loaderVersion, String profileName, Path loaderJar, Path profileJson) { }
}
