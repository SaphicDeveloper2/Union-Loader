package dev.union.api;

import java.util.Map;
import java.util.Optional;

/**
 * Structured contact info block on {@link ModMetadata}. Mirrors Fabric's
 * {@code net.fabricmc.loader.api.metadata.ContactInformation} so mod-browser UIs can port
 * cleanly.
 *
 * <p>Source form in {@code union.mod.json}:
 * <pre>
 * "contact": {
 *   "homepage": "https://example.com",
 *   "sources":  "https://github.com/example/mod",
 *   "issues":   "https://github.com/example/mod/issues",
 *   "email":    "example@example.com"
 * }
 * </pre>
 *
 * <p>All fields optional; unknown keys preserved in {@link #asMap()}.
 */
public interface ContactInformation {
	Optional<String> homepage();

	Optional<String> sources();

	Optional<String> issues();

	Optional<String> email();

	/** @return lookup of the raw unstructured contact key-value pairs. */
	Optional<String> get(String key);

	/** @return immutable view of every key/value pair in the contact block. */
	Map<String, String> asMap();

	/** @return the empty contact information (all methods return {@link Optional#empty()}). */
	static ContactInformation empty() {
		return EmptyContactInformation.INSTANCE;
	}
}
