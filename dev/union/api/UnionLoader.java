package dev.union.api;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;

import dev.union.impl.UnionLoaderImpl;

/**
 * Public entry point for mods to query loader state.
 * <p>Obtained via {@link #get()}. All methods are safe to call after {@code onInitialize}
 * entrypoints have begun dispatching.
 */
public interface UnionLoader {
	static UnionLoader get() {
		return UnionLoaderImpl.INSTANCE;
	}

	EnvType getEnvironmentType();

	boolean isDevelopmentEnvironment();

	Path getGameDir();

	Path getConfigDir();

	Path getModsDir();

	Collection<ModContainer> getAllMods();

	Optional<ModContainer> getModContainer(String id);

	boolean isModLoaded(String id);

	String getLoaderVersion();
}
