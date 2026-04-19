package dev.union.api.client.keymapping;

import java.util.Objects;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;

import dev.union.impl.client.keymapping.KeyMappingRegistryImpl;
import dev.union.mixin.client.keymapping.KeyMappingAccessor;

/**
 * Helper for registering modded {@link KeyMapping}s with Minecraft's keybind system. A
 * thin, ergonomic front-end over the loader's keybind machinery.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * KeyMapping left = KeyMappingHelper.registerKeyMapping(
 *     new KeyMapping("key.example.left",
 *                    InputConstants.Type.KEYSYM,
 *                    GLFW.GLFW_KEY_P,
 *                    KeyMapping.Category.MISC));
 *
 * KeyMapping right = KeyMappingHelper.registerKeyMapping(
 *     new KeyMapping("key.example.right",
 *                    InputConstants.Type.KEYSYM,
 *                    GLFW.GLFW_KEY_U,
 *                    KeyMapping.Category.MISC));
 * }</pre>
 *
 * <p>Registration must happen before {@code Minecraft}'s options have been loaded — during
 * {@code ClientModInitializer#onInitializeClient}. Attempts after that point throw
 * {@link IllegalStateException}.
 *
 * <p>Ported from {@code union-key-mapping-api-v1} in the Fabric-equivalent API zip. The
 * public surface is identical to the original aside from the package rename.
 *
 * @see KeyMapping
 * @see net.minecraft.client.ToggleKeyMapping
 */
public final class KeyMappingHelper {
	private KeyMappingHelper() {
	}

	/**
	 * Registers a key mapping with Minecraft's options system. If the mapping's category is a
	 * modded one, it's automatically placed after all vanilla categories in the controls
	 * screen via {@code CategoryComparator}.
	 *
	 * @param keyMapping the keymapping; must not be {@code null}
	 * @return the same keymapping instance, for chaining
	 * @throws IllegalArgumentException if a key mapping with the same name ID is already
	 *                                  registered or if {@code keyMapping} has already been
	 *                                  registered
	 * @throws IllegalStateException    if {@code Minecraft}'s options have already initialised
	 */
	public static KeyMapping registerKeyMapping(KeyMapping keyMapping) {
		Objects.requireNonNull(keyMapping, "key mapping cannot be null");
		return KeyMappingRegistryImpl.registerKeyMapping(keyMapping);
	}

	/**
	 * Reads the key currently bound to the given mapping from the player's saved options.
	 * Useful when you need to know what key a modded mapping is currently listening to —
	 * for example to display an on-screen hint.
	 *
	 * @param keyMapping the keymapping
	 * @return the configured key, from {@link KeyMapping#key} via an accessor mixin
	 */
	public static InputConstants.Key getBoundKeyOf(KeyMapping keyMapping) {
		Objects.requireNonNull(keyMapping, "key mapping cannot be null");
		return ((KeyMappingAccessor) (Object) keyMapping).union_getBoundKey();
	}
}
