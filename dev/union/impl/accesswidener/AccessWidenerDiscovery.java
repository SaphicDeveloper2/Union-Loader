package dev.union.impl.accesswidener;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import dev.union.api.ModContainer;
import dev.union.impl.util.Log;

/**
 * Collects every mod's declared access-widener file and merges them into a single
 * {@link AccessWidener}.
 *
 * <p>For each mod that declares an {@code accessWidener} in {@code union.mod.json}, opens the
 * mod JAR, reads the named entry, and feeds it to {@link AccessWidenerReader}. Errors on any
 * single AW are logged but do not abort boot — a malformed AW from one mod must not take the
 * whole game down.
 */
public final class AccessWidenerDiscovery {
	private static final String CATEGORY = "AccessWidener";
	private static final String NAMESPACE = "named";

	private AccessWidenerDiscovery() { }

	public static AccessWidener collect(Collection<? extends ModContainer> mods) {
		AccessWidener merged = new AccessWidener();

		for (ModContainer mod : mods) {
			String entryName = mod.getMetadata().getAccessWidener();
			if (entryName == null || entryName.isEmpty()) continue;

			try (ZipFile zip = new ZipFile(mod.getOrigin().toFile())) {
				ZipEntry entry = zip.getEntry(entryName);

				if (entry == null) {
					Log.warn(CATEGORY, mod.getMetadata().getId()
							+ " declares accessWidener '" + entryName + "' but the entry is not in the JAR");
					continue;
				}

				try (InputStream in = zip.getInputStream(entry);
						Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
					int before = merged.size();
					AccessWidenerReader.read(reader, merged, NAMESPACE,
							mod.getMetadata().getId() + ":" + entryName);
					int added = merged.size() - before;
					Log.debug(CATEGORY, mod.getMetadata().getId()
							+ ": added " + added + " widening rule" + (added == 1 ? "" : "s"));
				}
			} catch (IOException e) {
				Log.error(CATEGORY, "Failed to load access widener '" + entryName
						+ "' from mod " + mod.getMetadata().getId(), e);
			}
		}

		if (!merged.isEmpty()) {
			Log.info(CATEGORY, "Loaded " + merged.size() + " access widening rule"
					+ (merged.size() == 1 ? "" : "s") + " across "
					+ merged.getTargets().size() + " class"
					+ (merged.getTargets().size() == 1 ? "" : "es"));
		}

		return merged;
	}
}
