package dev.union.mixin.client.keymapping;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;

import dev.union.impl.client.keymapping.KeyMappingRegistryImpl;

/**
 * Hooks {@code Options#load()} to splice modded bindings into {@code options.keyMappings}
 * on every load — including after a restart, which is why
 * {@link KeyMappingRegistryImpl#process} de-dupes first.
 */
@Mixin(Options.class)
public class OptionsMixin {
	@Mutable
	@Shadow
	@Final
	public KeyMapping[] keyMappings;

	@Inject(at = @At("HEAD"), method = "load()V")
	public void union$loadHook(CallbackInfo info) {
		keyMappings = KeyMappingRegistryImpl.process(keyMappings);
	}
}
