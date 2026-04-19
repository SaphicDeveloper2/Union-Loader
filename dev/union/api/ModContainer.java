package dev.union.api;

import java.nio.file.Path;

/**
 * A discovered and loaded mod.
 */
public interface ModContainer {
	ModMetadata getMetadata();

	/**
	 * @return the path to the mod JAR (or directory, for dev workspaces).
	 */
	Path getOrigin();
}
