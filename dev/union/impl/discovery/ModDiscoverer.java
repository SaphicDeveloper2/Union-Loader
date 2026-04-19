package dev.union.impl.discovery;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import dev.union.api.EnvType;
import dev.union.impl.ModContainerImpl;
import dev.union.impl.ModMetadataImpl;
import dev.union.impl.discovery.jij.NestedJarExtractor;
import dev.union.impl.util.Log;

/**
 * Discovers mods in {@code <gameDir>/mods/} and recursively in their declared Jar-in-Jar
 * entries. Each JAR that declares {@code union.mod.json} becomes a loaded mod; each one that
 * doesn't is added to the loader classpath as a plain library.
 */
public final class ModDiscoverer {
	private static final String CATEGORY = "Discovery";
	private static final String METADATA_NAME = "union.mod.json";

	private final Path modsDir;
	private final EnvType envType;
	private final NestedJarExtractor jij;

	public ModDiscoverer(Path modsDir, EnvType envType) {
		this(modsDir, envType, null);
	}

	/**
	 * @param modsDir  root mods directory to scan.
	 * @param envType  current side — used to skip mods restricted to the other side.
	 * @param gameDir  game directory for the JiJ cache. Pass {@code null} to disable JiJ.
	 */
	public ModDiscoverer(Path modsDir, EnvType envType, Path gameDir) {
		this.modsDir = modsDir;
		this.envType = envType;
		this.jij = gameDir != null ? new NestedJarExtractor(gameDir) : null;
	}

	/**
	 * Discover mods + transitive library JARs. Built-in jars (e.g. the bundled Union API
	 * uber-jar) are processed before user mods so that on duplicate-id collisions the
	 * built-in version is kept (user mods can still override via {@code -Dunion.skipBuiltinApi=true}).
	 *
	 * @param builtinJars on-disk paths of extra top-level jars to treat as if they lived in
	 *                    the mods folder. Typically produced by
	 *                    {@link BuiltinModExtractor#extract(Path)}.
	 */
	public DiscoveryResult discoverAll(List<Path> builtinJars) throws IOException {
		if (!Files.isDirectory(modsDir)) {
			Files.createDirectories(modsDir);
			Log.info(CATEGORY, "Created mods directory at " + modsDir);
		}

		Map<String, ModContainerImpl> byId = new LinkedHashMap<>();
		List<Path> libraryJars = new ArrayList<>();
		Set<Path> seenJars = new HashSet<>();

		// Built-ins first. Because we scan these before user mods, any collision on id keeps
		// the built-in and warns about the discarded user copy — which is what we want for
		// auto-shipped API jars. Users who truly want to override should skip via system prop.
		for (Path jar : builtinJars) {
			processJar(jar.toAbsolutePath().normalize(), byId, libraryJars, seenJars, true);
		}

		if (Files.isDirectory(modsDir)) {
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDir, "*.jar")) {
				for (Path jar : stream) {
					processJar(jar.toAbsolutePath().normalize(), byId, libraryJars, seenJars, true);
				}
			}
		}

		validateDependencies(byId);

		Log.info(CATEGORY, "Loaded " + byId.size() + " mod" + (byId.size() == 1 ? "" : "s")
				+ (libraryJars.isEmpty() ? "" : " + " + libraryJars.size() + " nested library jar"
						+ (libraryJars.size() == 1 ? "" : "s")));
		return new DiscoveryResult(new ArrayList<>(byId.values()), libraryJars);
	}

	/** Convenience overload without built-ins. */
	public DiscoveryResult discoverAll() throws IOException {
		return discoverAll(List.of());
	}

	/** Backwards-compat entry returning only the mod list. */
	public List<ModContainerImpl> discover() throws IOException {
		return discoverAll().mods();
	}

	/**
	 * Process a single JAR: look for {@code union.mod.json}, branch into either mod or
	 * library path, then recurse into its declared nested jars.
	 *
	 * @param topLevel whether this JAR came from the root mods folder (as opposed to JiJ
	 *                 recursion). A top-level jar without {@code union.mod.json} is skipped
	 *                 (with a warning); a nested jar without it is treated as a library.
	 */
	private void processJar(Path jar, Map<String, ModContainerImpl> byId,
			List<Path> libraryJars, Set<Path> seenJars, boolean topLevel) {
		if (!seenJars.add(jar)) return;

		ModContainerImpl mod = tryLoadMod(jar);

		if (mod == null) {
			if (topLevel) {
				Log.debug(CATEGORY, "No " + METADATA_NAME + " in " + jar.getFileName() + "; skipping");
			} else {
				Log.debug(CATEGORY, jar.getFileName() + ": no " + METADATA_NAME + " (library-only jar)");
				libraryJars.add(jar);
			}

			return;
		}

		String env = mod.getMetadata().getEnvironment();

		if (!matchesEnv(env)) {
			Log.debug(CATEGORY, "Skipping " + mod + " (environment=" + env + ", side=" + envType + ")");
			return;
		}

		ModContainerImpl prev = byId.put(mod.getMetadata().getId(), mod);

		if (prev != null) {
			Log.warn(CATEGORY, "Duplicate mod id '" + mod.getMetadata().getId()
					+ "' — keeping " + mod + ", discarded " + prev);
		}

		// Recurse into declared nested jars.
		List<String> nested = mod.getMetadata().getNestedJars();

		if (!nested.isEmpty()) {
			if (jij == null) {
				Log.warn(CATEGORY, mod + " declares " + nested.size()
						+ " nested jar(s) but JiJ is disabled (no gameDir passed to ModDiscoverer)");
				return;
			}

			for (String internal : nested) {
				Path extracted = jij.extract(jar, internal);
				if (extracted != null) processJar(extracted.toAbsolutePath().normalize(), byId, libraryJars, seenJars, false);
			}
		}
	}

	private ModContainerImpl tryLoadMod(Path jar) {
		try (ZipFile zip = new ZipFile(jar.toFile())) {
			ZipEntry entry = zip.getEntry(METADATA_NAME);

			if (entry == null) return null;

			try (InputStream in = zip.getInputStream(entry);
					Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
				ModMetadataImpl metadata = ModMetadataImpl.parse(reader);
				return new ModContainerImpl(metadata, jar);
			}
		} catch (IOException e) {
			Log.error(CATEGORY, "Failed to load mod " + jar.getFileName(), e);
			return null;
		}
	}

	private boolean matchesEnv(String env) {
		if (env == null || env.isEmpty() || "*".equals(env)) return true;
		if ("client".equalsIgnoreCase(env)) return envType == EnvType.CLIENT;
		if ("server".equalsIgnoreCase(env)) return envType == EnvType.SERVER;
		return true;
	}

	private void validateDependencies(Map<String, ModContainerImpl> byId) {
		Set<String> available = new HashSet<>(byId.keySet());
		available.add("union"); // loader itself is always present

		// Fold in providedMods so aggregator jars satisfy depends on their constituent ids.
		for (ModContainerImpl mod : byId.values()) {
			for (String provided : mod.getMetadata().getProvidedMods()) {
				if (!available.add(provided)) {
					Log.warn(CATEGORY, mod.getMetadata().getId() + " provides '" + provided
							+ "' which is already declared by another mod; keeping first declaration");
				}
			}
		}

		Map<String, String> missing = new HashMap<>();

		for (ModContainerImpl mod : byId.values()) {
			for (String depId : mod.getMetadata().getDependencies().keySet()) {
				if (!available.contains(depId)) {
					missing.put(mod.getMetadata().getId(), depId);
				}
			}
		}

		if (!missing.isEmpty()) {
			StringBuilder sb = new StringBuilder("Unresolved mod dependencies:\n");

			for (Map.Entry<String, String> e : missing.entrySet()) {
				sb.append("  ").append(e.getKey()).append(" requires ").append(e.getValue()).append('\n');
			}

			throw new RuntimeException(sb.toString());
		}
	}
}
