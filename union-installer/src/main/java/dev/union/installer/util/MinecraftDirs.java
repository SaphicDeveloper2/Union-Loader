package dev.union.installer.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Per-OS lookup for the default {@code .minecraft} install directory.
 */
public final class MinecraftDirs {
	private MinecraftDirs() { }

	public static Path defaultMinecraftDir() {
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		String home = System.getProperty("user.home");

		Path candidate;

		if (os.contains("win")) {
			String appdata = System.getenv("APPDATA");

			if (appdata != null && !appdata.isEmpty()) {
				candidate = Paths.get(appdata, ".minecraft");
			} else {
				candidate = Paths.get(home, "AppData", "Roaming", ".minecraft");
			}
		} else if (os.contains("mac") || os.contains("darwin")) {
			candidate = Paths.get(home, "Library", "Application Support", "minecraft");
		} else {
			candidate = Paths.get(home, ".minecraft");
		}

		return candidate;
	}

	public static boolean looksLikeMinecraftDir(Path path) {
		return path != null && Files.isDirectory(path) && Files.isDirectory(path.resolve("versions"));
	}
}
