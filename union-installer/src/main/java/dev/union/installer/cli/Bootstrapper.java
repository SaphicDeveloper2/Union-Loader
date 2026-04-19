package dev.union.installer.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import dev.union.installer.Constants;
import dev.union.installer.client.ClientInstaller;
import dev.union.installer.server.ServerInstaller;
import dev.union.installer.util.MinecraftDirs;
import dev.union.installer.util.Progress;

/**
 * CLI/headless bootstrap entry. Invoked either explicitly via
 * {@code union-installer install …} / {@code install-server …} or implicitly when the
 * installer JAR is started in a headless JVM.
 */
public final class Bootstrapper {
	private Bootstrapper() { }

	public static int run(String[] args) {
		Map<String, String> opts = parseOpts(args);

		if (opts.containsKey("help") || opts.containsKey("h")) {
			printHelp();
			return 0;
		}

		// Implicit client install when no subcommand mode is set (--server absent).
		if (opts.containsKey("server-dir") || opts.containsKey("server")) {
			return runServer(opts);
		}

		return runClient(opts);
	}

	private static int runClient(Map<String, String> opts) {
		String mcVersion = opts.get("mc-version");

		if (mcVersion == null || mcVersion.isEmpty()) {
			System.err.println("error: --mc-version <version> is required");
			printHelp();
			return 2;
		}

		Path mcDir = opts.containsKey("mc-dir")
				? Paths.get(opts.get("mc-dir"))
				: MinecraftDirs.defaultMinecraftDir();

		if (!MinecraftDirs.looksLikeMinecraftDir(mcDir)) {
			System.err.println("error: " + mcDir + " does not look like a .minecraft directory "
					+ "(no 'versions' subfolder). Pass --mc-dir <path> to override.");
			return 2;
		}

		boolean addLauncherProfile = !opts.containsKey("no-profile");

		Progress progress = Progress.STDOUT;
		progress.update("Installing Union for Minecraft " + mcVersion + " into " + mcDir);

		try {
			ClientInstaller.InstallResult result = ClientInstaller.install(mcDir, mcVersion, addLauncherProfile, progress);
			System.out.println();
			System.out.println("Installed Union " + result.loaderVersion() + " for Minecraft " + mcVersion);
			System.out.println("  profile name : " + result.profileName());
			System.out.println("  profile JSON : " + result.profileJson());
			System.out.println("  loader JAR   : " + result.loaderJar());
			return 0;
		} catch (IOException e) {
			System.err.println("Install failed: " + e.getMessage());
			e.printStackTrace(System.err);
			return 1;
		}
	}

	private static int runServer(Map<String, String> opts) {
		String mcVersion = opts.get("mc-version");

		if (mcVersion == null || mcVersion.isEmpty()) {
			System.err.println("error: --mc-version <version> is required");
			printHelp();
			return 2;
		}

		String serverDir = opts.get("server-dir");

		if (serverDir == null || serverDir.isEmpty()) {
			System.err.println("error: --server-dir <path> is required for server install");
			printHelp();
			return 2;
		}

		String vanillaJar = opts.get("server-jar");

		Progress progress = Progress.STDOUT;
		progress.update("Installing Union server for Minecraft " + mcVersion + " into " + serverDir);

		try {
			ServerInstaller.InstallResult result = ServerInstaller.install(serverDir, mcVersion, vanillaJar, progress);
			System.out.println();
			System.out.println("Installed Union " + result.loaderVersion() + " server for Minecraft " + mcVersion);
			System.out.println("  loader JAR        : " + result.loaderJar());
			System.out.println("  Unix launcher     : " + result.startScriptSh());
			System.out.println("  Windows launcher  : " + result.startScriptBat());
			if (result.vanillaServerJar() != null) {
				System.out.println("  vanilla server    : " + result.vanillaServerJar());
			} else {
				System.out.println();
				System.out.println("NOTE: no vanilla server JAR was copied in. Download the "
						+ "Minecraft " + mcVersion + " server JAR from Mojang and place it in");
				System.out.println("      " + serverDir + "/minecraft_server." + mcVersion + ".jar");
				System.out.println("      before running start.sh / start.bat.");
			}
			System.out.println();
			System.out.println("Remember to accept Mojang's EULA in eula.txt (set 'eula=true').");
			return 0;
		} catch (IOException e) {
			System.err.println("Server install failed: " + e.getMessage());
			e.printStackTrace(System.err);
			return 1;
		}
	}

	private static Map<String, String> parseOpts(String[] args) {
		Map<String, String> opts = new HashMap<>();

		for (int i = 0; i < args.length; i++) {
			String a = args[i];

			if (a.startsWith("--")) {
				String key = a.substring(2);

				if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
					opts.put(key, args[++i]);
				} else {
					opts.put(key, "");
				}
			} else if (a.startsWith("-")) {
				opts.put(a.substring(1), "");
			}
		}

		return opts;
	}

	private static void printHelp() {
		System.out.println("Union Installer " + Constants.installerVersion());
		System.out.println();
		System.out.println("Client install:");
		System.out.println("  java -jar union-installer.jar [install] --mc-version <ver> [options]");
		System.out.println();
		System.out.println("  --mc-version <ver>    Target Minecraft version, e.g. 26.1.2 (required)");
		System.out.println("  --mc-dir <path>       Override .minecraft directory (default: OS-specific)");
		System.out.println("  --no-profile          Do not patch launcher_profiles.json");
		System.out.println();
		System.out.println("Server install:");
		System.out.println("  java -jar union-installer.jar install-server --mc-version <ver> --server-dir <path> [--server-jar <path>]");
		System.out.println();
		System.out.println("  --mc-version <ver>    Target Minecraft version (required)");
		System.out.println("  --server-dir <path>   Where to create the server install (required)");
		System.out.println("  --server-jar <path>   Path to the vanilla minecraft_server.<ver>.jar");
		System.out.println("                        (optional — you can drop it in manually afterwards)");
		System.out.println();
		System.out.println("Global:");
		System.out.println("  -h, --help            Show this message");
		System.out.println();
		System.out.println("Running with no args opens the GUI (unless the JVM is headless).");
	}
}
