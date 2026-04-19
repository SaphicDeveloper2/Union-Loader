package dev.union.installer.server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import dev.union.installer.Constants;
import dev.union.installer.util.LoaderExtractor;
import dev.union.installer.util.Progress;

/**
 * Headless Union server install. Produces a directory that looks like:
 *
 * <pre>
 *   &lt;serverDir&gt;/
 *   ├── libraries/dev/union/union-loader/&lt;ver&gt;/union-loader-&lt;ver&gt;.jar
 *   ├── mods/                     (empty, user drops mods here)
 *   ├── minecraft_server.&lt;mcVer&gt;.jar  (copied in if --server-jar given)
 *   ├── start.sh                  (Unix launch script)
 *   ├── start.bat                 (Windows launch script)
 *   └── eula.txt                  (if missing, prompts "eula=false")
 * </pre>
 *
 * <p>Running the server is then just {@code ./start.sh} (or {@code start.bat}). The scripts
 * invoke {@code UnionLauncher} directly, side-detection autodetects SERVER, Mixin + AW +
 * JiJ pipeline runs identically to the client path.
 *
 * <p>No vanilla JAR is downloaded automatically — Union assumes the user already has a server
 * JAR and either (a) passes it via {@code --server-jar}, or (b) drops it into {@code serverDir}
 * themselves before running the script.
 */
public final class ServerInstaller {
	private ServerInstaller() { }

	/** ASM artifacts embedded in the installer jar that must be extracted for the server classpath. */
	private static final String[] ASM_ARTIFACTS = { "asm", "asm-tree", "asm-commons", "asm-util", "asm-analysis" };
	private static final String ASM_VERSION = "9.7";

	public static InstallResult install(Path serverDir, String mcVersion, Path vanillaServerJar,
			Progress progress) throws IOException {
		String loaderVersion = LoaderExtractor.readEmbeddedLoaderVersion();

		progress.update("Preparing " + serverDir);
		Files.createDirectories(serverDir);
		Files.createDirectories(serverDir.resolve("mods"));

		progress.update("Extracting loader " + loaderVersion);
		Path loaderJar = LoaderExtractor.extractLoaderJar(serverDir, loaderVersion);

		progress.update("Extracting ASM " + ASM_VERSION);
		for (String artifact : ASM_ARTIFACTS) {
			extractEmbeddedAsm(serverDir, artifact);
		}

		Path serverJarInDir = null;

		if (vanillaServerJar != null) {
			progress.update("Copying vanilla server JAR");

			if (!Files.isRegularFile(vanillaServerJar)) {
				throw new IOException("Vanilla server JAR not found: " + vanillaServerJar);
			}

			String name = "minecraft_server." + mcVersion + ".jar";
			serverJarInDir = serverDir.resolve(name);
			Files.copy(vanillaServerJar, serverJarInDir, StandardCopyOption.REPLACE_EXISTING);
		}

		progress.update("Writing launch scripts");
		String vanillaJarName = serverJarInDir != null
				? serverJarInDir.getFileName().toString()
				: "minecraft_server." + mcVersion + ".jar";

		Path startSh = serverDir.resolve("start.sh");
		Path startBat = serverDir.resolve("start.bat");

		Files.writeString(startSh, buildStartSh(loaderVersion, vanillaJarName), StandardCharsets.UTF_8);
		setExecutable(startSh);

		Files.writeString(startBat, buildStartBat(loaderVersion, vanillaJarName), StandardCharsets.UTF_8);

		// EULA stub — users must opt in before Mojang's server runs.
		Path eula = serverDir.resolve("eula.txt");
		if (!Files.exists(eula)) {
			Files.writeString(eula,
					"# Mojang requires you to accept the EULA to run a Minecraft server.\n"
							+ "# See https://aka.ms/MinecraftEULA and edit this line to 'eula=true'.\n"
							+ "eula=false\n",
					StandardCharsets.UTF_8);
		}

		progress.update("Done");
		return new InstallResult(loaderVersion, loaderJar, startSh, startBat, serverJarInDir);
	}

	private static String buildStartSh(String loaderVer, String vanillaJarName) {
		StringBuilder cp = new StringBuilder();
		cp.append("libraries/dev/union/union-loader/").append(loaderVer).append("/union-loader-").append(loaderVer).append(".jar");
		for (String a : ASM_ARTIFACTS) {
			cp.append(":libraries/org/ow2/asm/").append(a).append("/").append(ASM_VERSION)
					.append("/").append(a).append("-").append(ASM_VERSION).append(".jar");
		}
		cp.append(":").append(vanillaJarName);

		return "#!/usr/bin/env sh\n"
				+ "# Union server launch script. Edit JAVA_OPTS as needed.\n"
				+ "cd \"$(dirname \"$0\")\"\n"
				+ "JAVA_OPTS=\"${JAVA_OPTS:--Xmx4G}\"\n"
				+ "exec java $JAVA_OPTS \\\n"
				+ "  -Dunion.side=server \\\n"
				+ "  -cp \"" + cp + "\" \\\n"
				+ "  dev.union.impl.launch.UnionLauncher \\\n"
				+ "  nogui \"$@\"\n";
	}

	private static String buildStartBat(String loaderVer, String vanillaJarName) {
		StringBuilder cp = new StringBuilder();
		cp.append("libraries\\dev\\union\\union-loader\\").append(loaderVer).append("\\union-loader-").append(loaderVer).append(".jar");
		for (String a : ASM_ARTIFACTS) {
			cp.append(";libraries\\org\\ow2\\asm\\").append(a).append("\\").append(ASM_VERSION)
					.append("\\").append(a).append("-").append(ASM_VERSION).append(".jar");
		}
		cp.append(";").append(vanillaJarName);

		return "@echo off\r\n"
				+ "REM Union server launch script. Edit JAVA_OPTS as needed.\r\n"
				+ "cd /d \"%~dp0\"\r\n"
				+ "if \"%JAVA_OPTS%\"==\"\" set JAVA_OPTS=-Xmx4G\r\n"
				+ "java %JAVA_OPTS% ^\r\n"
				+ "  -Dunion.side=server ^\r\n"
				+ "  -cp \"" + cp + "\" ^\r\n"
				+ "  dev.union.impl.launch.UnionLauncher ^\r\n"
				+ "  nogui %*\r\n";
	}

	/**
	 * Extract an ASM artifact bundled at {@code /<artifact>.jar} in the installer jar into the
	 * Maven-layout location the start scripts expect.
	 */
	private static void extractEmbeddedAsm(Path serverDir, String artifact) throws IOException {
		Path target = serverDir.resolve("libraries/org/ow2/asm/" + artifact + "/" + ASM_VERSION
				+ "/" + artifact + "-" + ASM_VERSION + ".jar");
		Files.createDirectories(target.getParent());

		try (java.io.InputStream in = ServerInstaller.class.getResourceAsStream("/" + artifact + ".jar")) {
			if (in == null) {
				throw new IOException("Missing embedded ASM resource: /" + artifact + ".jar");
			}
			Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static void setExecutable(Path path) {
		try {
			java.util.Set<java.nio.file.attribute.PosixFilePermission> perms =
					java.nio.file.attribute.PosixFilePermissions.fromString("rwxr-xr-x");
			Files.setPosixFilePermissions(path, perms);
		} catch (UnsupportedOperationException | IOException ignored) {
			// Non-POSIX filesystem (Windows) — the .bat covers that path instead.
		}
	}

	public record InstallResult(
			String loaderVersion,
			Path loaderJar,
			Path startScriptSh,
			Path startScriptBat,
			Path vanillaServerJar) {
	}

	// Referenced from Constants for symmetry with client paths.
	@SuppressWarnings("unused")
	private static String loaderLibraryName(String loaderVersion) {
		return Constants.loaderLibraryName(loaderVersion);
	}

	// Accept a String path for CLI convenience.
	public static InstallResult install(String serverDir, String mcVersion, String vanillaServerJar,
			Progress progress) throws IOException {
		return install(Paths.get(serverDir), mcVersion,
				vanillaServerJar != null ? Paths.get(vanillaServerJar) : null, progress);
	}
}
