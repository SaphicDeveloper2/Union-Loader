package dev.union.impl.discovery;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import dev.union.impl.ModContainerImpl;

/**
 * Outcome of {@link ModDiscoverer#discover()}. Contains:
 * <ul>
 *   <li>{@link #mods()} — fully loaded mods with {@code union.mod.json}.</li>
 *   <li>{@link #libraryJars()} — extracted nested JARs that don't themselves declare
 *       {@code union.mod.json} (pure libraries); these go on the classloader's search path
 *       without being treated as mods.</li>
 * </ul>
 */
public record DiscoveryResult(List<ModContainerImpl> mods, List<Path> libraryJars) {
	public DiscoveryResult(List<ModContainerImpl> mods, List<Path> libraryJars) {
		this.mods = List.copyOf(mods);
		this.libraryJars = List.copyOf(libraryJars);
	}

	public static DiscoveryResult empty() {
		return new DiscoveryResult(Collections.emptyList(), Collections.emptyList());
	}
}
