package dev.union.api.registry;

import dev.union.api.base.Identifier;
import dev.union.api.event.Event;

/**
 * Event posted on {@link dev.union.api.event.EventBus#MOD} once per registry during boot.
 *
 * <p>Handlers should filter on {@link #getRegistryKey()} and register their entries via
 * {@link #register(Identifier, Object)}. The actual registry wiring is implemented by an
 * integration module that hooks Minecraft's registry callbacks — see the Union-side adapter
 * in your specific MC-version support module.
 *
 * @param <T> element type of the registry being filled.
 */
public final class RegisterEvent<T> extends Event {
	/**
	 * Registry-element consumer provided by the integration layer. For each call, the
	 * integration inserts the value into the real MC registry.
	 */
	@FunctionalInterface
	public interface RegistryCallback<T> {
		T accept(Identifier id, T value);
	}

	private final RegistryKey<T> registryKey;
	private final RegistryCallback<T> callback;

	public RegisterEvent(RegistryKey<T> registryKey, RegistryCallback<T> callback) {
		this.registryKey = registryKey;
		this.callback = callback;
	}

	public RegistryKey<T> getRegistryKey() {
		return registryKey;
	}

	/**
	 * Register a value under {@code id}. Returns the same value (after the integration layer
	 * has inserted it into the real registry) so callers can capture it inline.
	 */
	public T register(Identifier id, T value) {
		return callback.accept(id, value);
	}
}
