package dev.union.impl.client.keymapping;

import java.util.ArrayList;
import java.util.List;

import it.unimi.dsi.fastutil.objects.ReferenceArrayList;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Internal registry for modded key mappings. Ported from
 * {@code union-key-mapping-api-v1}'s {@code KeyMappingRegistryImpl}.
 *
 * <p>Uses a {@link ReferenceArrayList} — identity-based {@code contains/remove/indexOf} —
 * so that the de-duplication pass in {@link #process(KeyMapping[])} handles two mappings
 * that compare {@code equals} but are distinct instances correctly.
 *
 * <p>On the Union side this coexists with the older {@code dev.union.api.keybind.Keybinds}
 * queue (which is used by the MC integration layer at {@code Minecraft.<init>} to seed
 * {@code options.keyMappings}). This module takes a different approach: it mixes into
 * {@code Options#load()} directly and adjusts the array there, which is what Fabric does
 * and what most Fabric-ported mods expect.
 */
public final class KeyMappingRegistryImpl {
	/**
	 * {@link ReferenceArrayList} so identity-based checks handle correctly when a mod
	 * registers two mappings with equal user-visible names but distinct instances (e.g. a
	 * reload-on-datapack scenario that re-creates mappings).
	 */
	private static final List<KeyMapping> MODDED_KEY_BINDINGS = new ReferenceArrayList<>();

	private KeyMappingRegistryImpl() { }

	public static KeyMapping registerKeyMapping(KeyMapping binding) {
		if (Minecraft.getInstance().options != null) {
			throw new IllegalStateException("GameOptions has already been initialised");
		}

		for (KeyMapping existing : MODDED_KEY_BINDINGS) {
			if (existing == binding) {
				throw new IllegalArgumentException(
						"Attempted to register a key mapping twice: " + binding.getName());
			} else if (existing.getName().equals(binding.getName())) {
				throw new IllegalArgumentException(
						"Attempted to register two key mappings with equal ID: " + binding.getName() + "!");
			}
		}

		MODDED_KEY_BINDINGS.add(binding);
		return binding;
	}

	/**
	 * Called from {@code OptionsMixin#loadHook} to rewrite {@code options.keyMappings}.
	 * Removes any modded bindings already present (so restart-after-add produces no
	 * duplicates), then appends the current registry.
	 */
	public static KeyMapping[] process(KeyMapping[] keysAll) {
		List<KeyMapping> newKeysAll = new ArrayList<>(List.of(keysAll));
		newKeysAll.removeAll(MODDED_KEY_BINDINGS);
		newKeysAll.addAll(MODDED_KEY_BINDINGS);
		return newKeysAll.toArray(new KeyMapping[0]);
	}
}
