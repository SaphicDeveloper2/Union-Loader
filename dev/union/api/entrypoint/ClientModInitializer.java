package dev.union.api.entrypoint;

/**
 * Client-only entrypoint. Referenced from {@code union.mod.json} under the
 * {@code "client"} entrypoint key.
 */
@FunctionalInterface
public interface ClientModInitializer {
	void onInitializeClient();
}
