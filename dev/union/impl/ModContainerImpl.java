package dev.union.impl;

import java.nio.file.Path;

import dev.union.api.ModContainer;
import dev.union.api.ModMetadata;

public final class ModContainerImpl implements ModContainer {
	private final ModMetadata metadata;
	private final Path origin;

	public ModContainerImpl(ModMetadata metadata, Path origin) {
		this.metadata = metadata;
		this.origin = origin;
	}

	@Override public ModMetadata getMetadata() { return metadata; }
	@Override public Path getOrigin() { return origin; }

	@Override
	public String toString() {
		return metadata + " (" + origin.getFileName() + ")";
	}
}
