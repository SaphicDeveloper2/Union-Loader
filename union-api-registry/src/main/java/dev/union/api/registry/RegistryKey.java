package dev.union.api.registry;

import java.util.Objects;

import dev.union.api.base.Identifier;

/**
 * Type-safe handle for a Minecraft registry.
 *
 * <p>Lets us refer to registries (item, block, entity_type, …) without pulling Minecraft's
 * {@code Registries} class onto the API compile classpath. Mods and integration code convert
 * to/from Minecraft's {@code ResourceKey<Registry<?>>} at the boundary.
 *
 * @param <T> the element type this registry contains (e.g. {@code Item}, {@code Block}).
 */
public final class RegistryKey<T> {
	private final Identifier id;

	private RegistryKey(Identifier id) {
		this.id = id;
	}

	public static <T> RegistryKey<T> of(Identifier id) {
		return new RegistryKey<>(id);
	}

	public static <T> RegistryKey<T> of(String namespace, String path) {
		return of(Identifier.of(namespace, path));
	}

	public Identifier id() { return id; }

	@Override
	public boolean equals(Object o) {
		return o instanceof RegistryKey<?> k && id.equals(k.id);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id);
	}

	@Override
	public String toString() {
		return "RegistryKey(" + id + ")";
	}
}
