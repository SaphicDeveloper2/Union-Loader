package dev.union.installer.client;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.union.installer.Constants;

/**
 * Builds the per-profile JSON document that the vanilla Minecraft launcher consumes
 * from {@code versions/<profileName>/<profileName>.json}.
 *
 * <p>We use {@code inheritsFrom} to delegate everything (assets, natives, authlib, etc.) back
 * to the vanilla release profile, and override only what Union needs: the main class plus our
 * own loader library. The launcher will download vanilla libraries normally; our loader library
 * is placed on disk directly and uses a local-file URL hint (Mojang's launcher tolerates
 * libraries that are already present and will not try to re-download if the file exists).
 */
public final class ProfileWriter {
	private ProfileWriter() { }

	public static Map<String, Object> build(String loaderVersion, String mcVersion) {
		String profileName = Constants.profileName(loaderVersion, mcVersion);

		Map<String, Object> root = new LinkedHashMap<>();
		root.put("id", profileName);
		root.put("inheritsFrom", mcVersion);
		root.put("type", "release");
		root.put("mainClass", Constants.PROFILE_MAIN_CLASS);
		// Empty arg lists — everything else inherits from the parent profile. The loader
		// autodetects side via the classpath heuristic in UnionLauncher.resolveEnvType().
		Map<String, Object> args = new LinkedHashMap<>();
		args.put("game", List.of());
		args.put("jvm", List.of("-Dunion.side=client"));
		root.put("arguments", args);

		// Libraries the Mojang launcher will download + add to the classpath. Our embedded
		// Mixin code references ASM types; Minecraft itself does not put ASM on the classpath,
		// so the launcher must fetch our own copy. Using Maven Central via the standard
		// Minecraft launcher library-schema URL field.
		List<Map<String, Object>> libs = new java.util.ArrayList<>();
		libs.add(buildLoaderLibrary(loaderVersion));
		libs.add(mavenLibrary("org.ow2.asm", "asm",          "9.7", "https://repo.maven.apache.org/maven2/"));
		libs.add(mavenLibrary("org.ow2.asm", "asm-analysis", "9.7", "https://repo.maven.apache.org/maven2/"));
		libs.add(mavenLibrary("org.ow2.asm", "asm-commons",  "9.7", "https://repo.maven.apache.org/maven2/"));
		libs.add(mavenLibrary("org.ow2.asm", "asm-tree",     "9.7", "https://repo.maven.apache.org/maven2/"));
		libs.add(mavenLibrary("org.ow2.asm", "asm-util",     "9.7", "https://repo.maven.apache.org/maven2/"));
		root.put("libraries", libs);

		root.put("releaseTime", "1970-01-01T00:00:00+00:00");
		root.put("time", "1970-01-01T00:00:00+00:00");
		return root;
	}

	private static Map<String, Object> buildLoaderLibrary(String loaderVersion) {
		Map<String, Object> lib = new LinkedHashMap<>();
		lib.put("name", Constants.loaderLibraryName(loaderVersion));
		// Empty URL tells the launcher not to try to fetch it remotely; the file will be
		// present on disk because the installer wrote it there.
		lib.put("url", "");
		return lib;
	}

	/**
	 * Build a Mojang-launcher "library" descriptor pointing at a Maven-hosted jar. The launcher
	 * resolves {@code name} in Maven gradle-coordinate form and fetches from {@code url} at the
	 * standard group/artifact/version path.
	 */
	private static Map<String, Object> mavenLibrary(String group, String artifact, String version, String baseUrl) {
		Map<String, Object> lib = new LinkedHashMap<>();
		lib.put("name", group + ":" + artifact + ":" + version);
		lib.put("url", baseUrl);
		return lib;
	}
}
