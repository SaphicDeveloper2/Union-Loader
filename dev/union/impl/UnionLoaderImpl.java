package dev.union.impl;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.union.api.EnvType;
import dev.union.api.ModContainer;
import dev.union.api.UnionLoader;
import dev.union.impl.entrypoint.EntrypointStorage;

/**
 * Implementation of {@link UnionLoader}. Populated by {@code UnionLauncher} during boot.
 */
public final class UnionLoaderImpl implements UnionLoader {
	public static final UnionLoaderImpl INSTANCE = new UnionLoaderImpl();

	private static final String LOADER_VERSION = loaderVersion();

	private EnvType envType;
	private Path gameDir;
	private Path configDir;
	private Path modsDir;
	private boolean development;
	private final Map<String, ModContainer> mods = new LinkedHashMap<>();
	private final EntrypointStorage entrypoints = new EntrypointStorage();

	private UnionLoaderImpl() { }

	public void bootstrap(EnvType envType, Path gameDir, boolean development) {
		this.envType = envType;
		this.gameDir = gameDir;
		this.configDir = gameDir.resolve("config");
		this.modsDir = gameDir.resolve("mods");
		this.development = development;

		// Eagerly create config/ and mods/ so mods can read/write config files during init
		// without every mod having to defensively mkdir -p. Vanilla MC creates gameDir itself
		// but not subdirs; Union owns these.
		try {
			java.nio.file.Files.createDirectories(configDir);
			java.nio.file.Files.createDirectories(modsDir);
		} catch (java.io.IOException e) {
			// Non-fatal: if we can't create these (permissions, readonly FS) mods that need
			// them will fail later with a clearer error from their own code path.
			dev.union.impl.util.Log.warn("Launch", "could not pre-create config/mods dirs: " + e.getMessage());
		}
	}

	public void setMods(List<? extends ModContainer> containers) {
		mods.clear();
		for (ModContainer c : containers) mods.put(c.getMetadata().getId(), c);
		entrypoints.ingest(mods.values());
	}

	public EntrypointStorage entrypoints() { return entrypoints; }

	@Override public EnvType getEnvironmentType() { return envType; }
	@Override public boolean isDevelopmentEnvironment() { return development; }
	@Override public Path getGameDir() { return gameDir; }
	@Override public Path getConfigDir() { return configDir; }
	@Override public Path getModsDir() { return modsDir; }

	@Override
	public Collection<ModContainer> getAllMods() {
		return Collections.unmodifiableCollection(mods.values());
	}

	@Override
	public Optional<ModContainer> getModContainer(String id) {
		return Optional.ofNullable(mods.get(id));
	}

	@Override
	public boolean isModLoaded(String id) {
		return mods.containsKey(id);
	}

	@Override
	public String getLoaderVersion() {
		return LOADER_VERSION;
	}

	private static String loaderVersion() {
		Package p = UnionLoaderImpl.class.getPackage();
		String v = p == null ? null : p.getImplementationVersion();
		return v != null ? v : "dev";
	}
}
