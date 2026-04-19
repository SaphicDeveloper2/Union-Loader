package dev.union.impl.discovery;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import dev.union.impl.util.Log;

/**
 * Extracts bundled built-in mods from inside the loader jar to the game directory so the
 * normal {@link ModDiscoverer} picks them up on the same pass as user-installed mods.
 *
 * <p>Layout:
 * <pre>
 *   &lt;gameDir&gt;/.cache/union/builtin/
 *   ├── .sha1                         (hash of last-extracted bundle)
 *   └── union-api.jar                 (extracted on first run or when .sha1 changes)
 * </pre>
 *
 * <p>At launch Union ships the combined {@code union-api.jar} as a resource at
 * {@code /builtin/union-api.jar} inside the loader jar. This class looks the resource up on
 * the classloader, SHA-1s the bytes, compares to {@code .sha1}, and if different writes the
 * fresh bundle out atomically. After extraction the returned paths are added to the discovery
 * pass as if they were top-level mods.
 *
 * <p>If a user wants to pin an older or forked API, they can drop their own
 * {@code union-api-*.jar} into {@code mods/}. The duplicate-id handling in
 * {@link ModDiscoverer} keeps the first-seen entry (ours from builtin, which is scanned first)
 * — mods who want to override can also add {@code -Dunion.skipBuiltinApi=true} to skip
 * extraction entirely.
 */
public final class BuiltinModExtractor {
	private static final String CATEGORY = "Builtin";
	private static final String PROP_SKIP = "union.skipBuiltinApi";

	/** Resources inside the loader jar to extract, mapped to their output filename. */
	private static final String[][] BUILTINS = {
			{ "/builtin/union-api.jar", "union-api.jar" }
	};

	private BuiltinModExtractor() { }

	/**
	 * Extract every bundled mod to {@code <gameDir>/.cache/union/builtin/} and return their
	 * on-disk paths. Each returned path is a fully-formed Union mod jar that
	 * {@link ModDiscoverer} will process as normal — JiJ support, dependency resolution, the
	 * lot.
	 *
	 * @return extracted jar paths, or empty list if the feature was disabled via
	 *         {@value #PROP_SKIP} or no built-in resources were found on the classpath.
	 */
	public static List<Path> extract(Path gameDir) {
		if (Boolean.getBoolean(PROP_SKIP)) {
			Log.info(CATEGORY, "skipping built-in mod extraction (-D" + PROP_SKIP + "=true)");
			return List.of();
		}

		Path outDir = gameDir.resolve(".cache").resolve("union").resolve("builtin");
		List<Path> extracted = new ArrayList<>();

		for (String[] row : BUILTINS) {
			String resource = row[0];
			String filename = row[1];

			try (InputStream in = BuiltinModExtractor.class.getResourceAsStream(resource)) {
				if (in == null) {
					Log.debug(CATEGORY, "resource " + resource + " not bundled; skipping");
					continue;
				}

				byte[] bytes = in.readAllBytes();
				Path target = outDir.resolve(filename);
				Path hashFile = outDir.resolve(filename + ".sha1");

				String freshHash = sha1(bytes);
				String existingHash = readHashFile(hashFile);

				if (freshHash.equals(existingHash) && Files.isRegularFile(target) && Files.size(target) == bytes.length) {
					Log.debug(CATEGORY, "builtin " + filename + " unchanged (" + freshHash.substring(0, 8) + "…)");
					extracted.add(target);
					continue;
				}

				Files.createDirectories(outDir);

				Path tmp = outDir.resolve(filename + ".tmp");

				try (OutputStream out = Files.newOutputStream(tmp)) {
					out.write(bytes);
				}

				Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
				Files.writeString(hashFile, freshHash);

				Log.info(CATEGORY, "extracted builtin " + filename + " → " + target);
				extracted.add(target);
			} catch (IOException e) {
				Log.error(CATEGORY, "Failed to extract builtin " + resource, e);
			}
		}

		return extracted;
	}

	private static String readHashFile(Path p) {
		try {
			return Files.isRegularFile(p) ? Files.readString(p).trim() : "";
		} catch (IOException e) {
			return "";
		}
	}

	private static String sha1(byte[] data) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			byte[] digest = md.digest(data);
			StringBuilder sb = new StringBuilder(digest.length * 2);

			for (byte b : digest) {
				sb.append(Character.forDigit((b >> 4) & 0xF, 16));
				sb.append(Character.forDigit(b & 0xF, 16));
			}

			return sb.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-1 unavailable", e);
		}
	}
}
