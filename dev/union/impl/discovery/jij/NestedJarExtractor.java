package dev.union.impl.discovery.jij;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import dev.union.impl.util.Log;

/**
 * Extracts Jar-in-Jar entries from a parent mod JAR into a content-addressed cache under the
 * game directory, returning the disk path of the extracted file so the classloader can pick
 * it up.
 *
 * <p>Cache layout:
 * <pre>
 *   &lt;gameDir&gt;/.cache/union/jars/&lt;sha1-of-bytes&gt;/&lt;basename&gt;
 * </pre>
 *
 * <p>Content addressing means re-running the loader with an unchanged mod JAR is a no-op —
 * the hash already exists on disk, we skip the write. When a mod is updated its nested JARs
 * get fresh hashes and a fresh extraction. Old hashes stay around (cleanup is TODO for v0).
 *
 * <p>Errors on any single entry log and return {@code null} — a broken nested jar must not
 * prevent the rest of the mod from loading.
 */
public final class NestedJarExtractor {
	private static final String CATEGORY = "JiJ";
	private static final int COPY_BUF = 16 * 1024;

	private final Path cacheRoot;

	public NestedJarExtractor(Path gameDir) {
		this.cacheRoot = gameDir.resolve(".cache").resolve("union").resolve("jars");
	}

	/**
	 * Extract a single nested JAR from {@code parentJar} at the given internal path.
	 *
	 * @return on-disk path of the extracted JAR, or {@code null} if the entry was missing or
	 *         extraction failed.
	 */
	public Path extract(Path parentJar, String internalPath) {
		try (ZipFile zip = new ZipFile(parentJar.toFile())) {
			ZipEntry entry = zip.getEntry(internalPath);

			if (entry == null) {
				Log.warn(CATEGORY, parentJar.getFileName() + ": declared nested jar '" + internalPath + "' is missing");
				return null;
			}

			byte[] bytes;

			try (InputStream in = zip.getInputStream(entry)) {
				bytes = readAll(in, (int) Math.max(entry.getSize(), 1024));
			}

			String basename = internalPath.substring(internalPath.lastIndexOf('/') + 1);

			if (!basename.toLowerCase(java.util.Locale.ROOT).endsWith(".jar")) {
				Log.warn(CATEGORY, parentJar.getFileName() + ": declared nested jar '" + internalPath
						+ "' does not end in .jar; skipping");
				return null;
			}

			String hash = sha1(bytes);
			Path outDir = cacheRoot.resolve(hash);
			Path outFile = outDir.resolve(basename);

			if (Files.isRegularFile(outFile) && Files.size(outFile) == bytes.length) {
				// Already extracted — content-addressed cache hit.
				Log.debug(CATEGORY, "cache hit: " + basename + " (" + hash.substring(0, 8) + "…)");
				return outFile;
			}

			Files.createDirectories(outDir);

			// Atomic write: tmp file + move. Prevents partial files on crash / concurrent run.
			Path tmp = outDir.resolve(basename + ".tmp");

			try (OutputStream out = Files.newOutputStream(tmp)) {
				out.write(bytes);
			}

			Files.move(tmp, outFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

			Log.debug(CATEGORY, "extracted " + basename + " → " + outFile);
			return outFile;
		} catch (IOException e) {
			Log.error(CATEGORY, "Failed to extract nested jar '" + internalPath + "' from " + parentJar.getFileName(), e);
			return null;
		}
	}

	private static byte[] readAll(InputStream in, int hint) throws IOException {
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(hint);
		byte[] buf = new byte[COPY_BUF];
		int n;

		while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
		return out.toByteArray();
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
			// SHA-1 is mandated on every conformant JRE; this cannot happen.
			throw new IllegalStateException("SHA-1 unavailable", e);
		}
	}
}
