package dev.union.api.base;

import java.util.Objects;

/**
 * Namespaced identifier analogous to Minecraft's {@code ResourceLocation}. Used across the
 * Union API wherever a stable, hashable, namespaced key is needed — registry keys, event
 * phase identifiers, network channel IDs, attachment keys, etc.
 *
 * <p>Union's {@code Identifier} is deliberately MC-free so API modules can be written without
 * a Minecraft compile dependency. Mods that want to convert to/from {@code ResourceLocation}
 * can do so at the boundary in one line.
 *
 * <h2>Format</h2>
 * <pre>
 *   &lt;namespace&gt;:&lt;path&gt;
 * </pre>
 *
 * <p>Namespace and path must be non-empty. Namespace matches {@code [a-z0-9_.-]+}; path matches
 * {@code [a-z0-9_./-]+}. Matching MC's rules keeps the two formats round-trippable.
 */
public final class Identifier implements Comparable<Identifier> {
	private final String namespace;
	private final String path;

	private Identifier(String namespace, String path) {
		this.namespace = namespace;
		this.path = path;
	}

	public static Identifier of(String namespace, String path) {
		validate(namespace, "namespace", "[a-z0-9_.-]+");
		validate(path, "path", "[a-z0-9_./-]+");
		return new Identifier(namespace, path);
	}

	/**
	 * Parse the {@code namespace:path} form. Throws {@link IllegalArgumentException} on any
	 * malformed input.
	 */
	public static Identifier parse(String raw) {
		int colon = raw.indexOf(':');

		if (colon < 0) throw new IllegalArgumentException("Identifier missing namespace: " + raw);
		if (colon == 0) throw new IllegalArgumentException("Identifier empty namespace: " + raw);
		if (colon == raw.length() - 1) throw new IllegalArgumentException("Identifier empty path: " + raw);

		return of(raw.substring(0, colon), raw.substring(colon + 1));
	}

	public String namespace() { return namespace; }

	public String path() { return path; }

	@Override
	public String toString() {
		return namespace + ':' + path;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof Identifier i && namespace.equals(i.namespace) && path.equals(i.path);
	}

	@Override
	public int hashCode() {
		return Objects.hash(namespace, path);
	}

	@Override
	public int compareTo(Identifier o) {
		int c = namespace.compareTo(o.namespace);
		return c != 0 ? c : path.compareTo(o.path);
	}

	private static void validate(String s, String kind, String pattern) {
		if (s == null || s.isEmpty()) throw new IllegalArgumentException(kind + " must be non-empty");
		if (!s.matches(pattern)) {
			throw new IllegalArgumentException(kind + " '" + s + "' contains illegal characters (must match " + pattern + ")");
		}
	}
}
