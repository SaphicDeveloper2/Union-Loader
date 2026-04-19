package dev.union.api.entrypoint;

/**
 * Entrypoint for mods that should run on both client and server.
 * <p>Referenced from {@code union.mod.json} under the {@code "main"} entrypoint key.
 */
@FunctionalInterface
public interface ModInitializer {
	void onInitialize();
}
