package dev.union.api.registry;

import java.util.Objects;
import java.util.function.Supplier;

import dev.union.api.base.Identifier;

/**
 * Lazy handle to a registered object. Returned by {@link DeferredRegister#register}. Holds
 * the intended {@link Identifier} and (after {@link #resolve} has run) the actual object.
 *
 * <p>Accessing {@link #get()} before the registry has been fired throws
 * {@link IllegalStateException} — this mirrors NeoForge's {@code DeferredHolder} semantics
 * and prevents accidental null reads during mod init.
 *
 * @param <T> the registered element type.
 */
public final class RegistryObject<T> implements Supplier<T> {
	private final Identifier id;
	private volatile T value;

	RegistryObject(Identifier id) {
		this.id = Objects.requireNonNull(id);
	}

	public Identifier id() { return id; }

	@Override
	public T get() {
		T v = value;

		if (v == null) {
			throw new IllegalStateException(
					"RegistryObject " + id + " accessed before registration fired. "
							+ "Access it from an @SubscribeEvent handler or inside an entrypoint that runs "
							+ "after REGISTRATION phase, not in class initialisers.");
		}

		return v;
	}

	public boolean isPresent() {
		return value != null;
	}

	/**
	 * Called by {@link DeferredRegister} once the backing registry has produced the value.
	 */
	void resolve(T value) {
		if (value == null) throw new NullPointerException("resolved value must be non-null for " + id);

		if (this.value != null) {
			throw new IllegalStateException("RegistryObject " + id + " resolved twice");
		}

		this.value = value;
	}

	@Override
	public String toString() {
		return "RegistryObject(" + id + (value == null ? ", unresolved)" : ", resolved)");
	}
}
