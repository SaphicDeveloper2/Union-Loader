package dev.union.api.resource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import dev.union.api.base.Identifier;

/**
 * Register built-in resource packs (bundled with a mod, shown in the user's resource-pack list
 * as toggleable packs). Matches Fabric's {@code ResourceManagerHelper.registerBuiltinResourcePack}
 * in intent.
 *
 * <p>Usage — during client init:
 * <pre>
 * ResourceManagerHelper.registerBuiltinResourcePack(
 *     Identifier.of("mymod", "programmer_art"),
 *     "programmer_art",
 *     ActivationType.NORMAL);
 * </pre>
 *
 * <p>The integration layer reads this registry at pack-source discovery time and synthesises
 * a pack entry for each. Where the pack's files live on disk is discovered by the integration
 * layer — typically the owning mod's JAR root + the {@code subPath} parameter — so API users
 * don't name file paths.
 */
public final class ResourceManagerHelper {
	private ResourceManagerHelper() { }

	public enum ActivationType {
		/** Disabled by default; user toggles on in the resource pack menu. */
		NORMAL,
		/** Enabled by default; user can toggle off. */
		DEFAULT_ENABLED,
		/** Always active; cannot be disabled. Used for mandatory asset overlays. */
		ALWAYS_ENABLED
	}

	public record BuiltinPack(Identifier id, String subPath, ActivationType activation) {
		public BuiltinPack {
			Objects.requireNonNull(id);
			Objects.requireNonNull(subPath);
			Objects.requireNonNull(activation);
		}
	}

	private static final List<BuiltinPack> PACKS = new ArrayList<>();

	/**
	 * Register a bundled pack. Safe to call multiple times; duplicates (by id) are ignored
	 * with a warning from the integration layer.
	 *
	 * @param id          unique identifier, typically {@code <modid>:<path>}.
	 * @param subPath     JAR-root-relative folder containing {@code pack.mcmeta}.
	 * @param activation  user-toggle semantics.
	 */
	public static void registerBuiltinResourcePack(Identifier id, String subPath, ActivationType activation) {
		synchronized (PACKS) {
			PACKS.add(new BuiltinPack(id, subPath, activation));
		}
	}

	/** Called by the integration layer during pack source discovery. Not for mod use. */
	public static List<BuiltinPack> all() {
		synchronized (PACKS) { return Collections.unmodifiableList(new ArrayList<>(PACKS)); }
	}
}
