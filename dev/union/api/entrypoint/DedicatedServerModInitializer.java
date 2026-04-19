package dev.union.api.entrypoint;

/**
 * Dedicated-server-only entrypoint. Referenced from {@code union.mod.json} under the
 * {@code "server"} entrypoint key.
 */
@FunctionalInterface
public interface DedicatedServerModInitializer {
	void onInitializeServer();
}
