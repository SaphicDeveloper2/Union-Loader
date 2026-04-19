package dev.union.installer.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import dev.union.installer.Constants;

/**
 * Responsible for pulling the embedded {@code /union-loader.jar} resource out of the installer
 * JAR and placing it where the vanilla Minecraft launcher expects libraries to live.
 *
 * <p>Layout, Maven-style:
 * <pre>
 *   &lt;mcDir&gt;/libraries/dev/union/union-loader/&lt;version&gt;/union-loader-&lt;version&gt;.jar
 * </pre>
 */
public final class LoaderExtractor {
	private LoaderExtractor() { }

	/**
	 * @return the detected embedded-loader version, read from the manifest of
	 *         {@code /union-loader.jar}.
	 * @throws IOException if the embedded resource is missing or its manifest is unreadable.
	 */
	public static String readEmbeddedLoaderVersion() throws IOException {
		try (InputStream in = LoaderExtractor.class.getResourceAsStream(Constants.EMBEDDED_LOADER_RESOURCE)) {
			if (in == null) {
				throw new IOException("Embedded loader JAR not found at "
						+ Constants.EMBEDDED_LOADER_RESOURCE + " — installer was built incorrectly.");
			}

			try (JarInputStream jar = new JarInputStream(in)) {
				Manifest mf = jar.getManifest();

				if (mf == null) {
					throw new IOException("Embedded loader JAR has no manifest.");
				}

				Attributes a = mf.getMainAttributes();
				String v = a.getValue("Implementation-Version");

				if (v == null || v.isEmpty()) {
					throw new IOException("Embedded loader JAR manifest missing Implementation-Version.");
				}

				return v;
			}
		}
	}

	/**
	 * Extract the embedded loader JAR into the Maven-style library path under {@code mcDir}.
	 *
	 * @return absolute path of the written JAR.
	 */
	public static Path extractLoaderJar(Path mcDir, String loaderVersion) throws IOException {
		Path target = loaderLibraryPath(mcDir, loaderVersion);
		Files.createDirectories(target.getParent());

		try (InputStream in = LoaderExtractor.class.getResourceAsStream(Constants.EMBEDDED_LOADER_RESOURCE)) {
			if (in == null) {
				throw new IOException("Embedded loader JAR not found at "
						+ Constants.EMBEDDED_LOADER_RESOURCE + " — installer was built incorrectly.");
			}

			Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
		}

		return target;
	}

	public static Path loaderLibraryPath(Path mcDir, String loaderVersion) {
		return mcDir.resolve("libraries")
				.resolve("dev").resolve("union").resolve("union-loader").resolve(loaderVersion)
				.resolve("union-loader-" + loaderVersion + ".jar");
	}
}
