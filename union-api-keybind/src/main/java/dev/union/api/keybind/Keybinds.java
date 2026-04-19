package dev.union.api.keybind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Registry for custom key mappings. Mirrors Fabric's {@code KeyMappingHelper.registerKeyBinding}
 * entry-point semantics, but decoupled from MC. Mods register their key-mapping Object (typed
 * loosely here to keep the module MC-free) during client init; the integration layer reads the
 * registrations and splices them into vanilla's {@code options.keyMappings} array.
 *
 * <p>Usage:
 * <pre>
 * KeyMapping openMyUi = new KeyMapping("key.mymod.open", InputConstants.KEY_G, "key.categories.misc");
 * Keybinds.register(openMyUi);
 * </pre>
 */
public final class Keybinds {
	private static final List<Object> PENDING = new ArrayList<>();
	private static volatile boolean frozen;

	private Keybinds() { }

	/**
	 * Register a {@code net.minecraft.client.KeyMapping} for injection into the active option
	 * set. Must be called before the integration layer fires its key-setup hook (typically at
	 * {@code Minecraft.<init>} TAIL). Later calls throw.
	 *
	 * @param keyMapping a {@code KeyMapping} instance. Typed as {@link Object} so this API
	 *                   module compiles without MC.
	 * @return the same instance for call-site chaining.
	 */
	public static <T> T register(T keyMapping) {
		if (keyMapping == null) throw new NullPointerException("keyMapping");

		synchronized (PENDING) {
			if (frozen) {
				throw new IllegalStateException("Keybinds registry is frozen; register during client init before key setup fires");
			}
			PENDING.add(keyMapping);
		}

		return keyMapping;
	}

	/**
	 * Called by the integration layer to drain the pending registrations and lock further
	 * additions. Not intended for mod use.
	 */
	public static List<Object> drainAndFreeze() {
		synchronized (PENDING) {
			frozen = true;
			List<Object> snapshot = List.copyOf(PENDING);
			PENDING.clear();
			return snapshot;
		}
	}

	public static List<Object> pending() {
		synchronized (PENDING) { return Collections.unmodifiableList(new ArrayList<>(PENDING)); }
	}

	public static boolean isFrozen() { return frozen; }
}
