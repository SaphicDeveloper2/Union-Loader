package dev.union.mixin.client.keymapping;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

import dev.union.impl.client.keymapping.CategoryComparator;

/**
 * When a new {@link KeyMapping.Category} registers, re-sort the shared {@code SORT_ORDER}
 * so modded categories always fall in a stable position after vanilla ones.
 */
@Mixin(KeyMapping.Category.class)
abstract class KeyMappingCategoryMixin {
	@Shadow
	@Final
	private static List<KeyMapping.Category> SORT_ORDER;

	@Inject(method = "register(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/KeyMapping$Category;",
			at = @At("RETURN"))
	private static void union$onReturnRegister(Identifier id, CallbackInfoReturnable<KeyMapping.Category> cir) {
		SORT_ORDER.sort(CategoryComparator.INSTANCE);
	}
}
