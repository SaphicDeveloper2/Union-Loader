package dev.union.mixin.client.keymapping;

import com.mojang.blaze3d.platform.InputConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.KeyMapping;

/**
 * Public accessor for {@link KeyMapping#key} — needed by
 * {@code KeyMappingHelper.getBoundKeyOf}. The field is private in vanilla; an
 * {@link Accessor} mixin exposes it.
 */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
	@Accessor("key")
	InputConstants.Key union_getBoundKey();
}
