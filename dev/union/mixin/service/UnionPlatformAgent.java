package dev.union.mixin.service;

import java.util.ArrayList;
import java.util.Collection;

import org.spongepowered.asm.launch.platform.IMixinPlatformServiceAgent;
import org.spongepowered.asm.launch.platform.MixinPlatformAgentAbstract;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.mixin.MixinEnvironment.Phase;
import org.spongepowered.asm.util.IConsumer;

import dev.union.api.EnvType;
import dev.union.api.ModContainer;
import dev.union.api.UnionLoader;

/**
 * Union platform service agent. Reports the Minecraft side ({@code CLIENT}/{@code SERVER})
 * and enumerates per-mod mixin containers for Mixin's discovery pass.
 *
 * <p>Referenced by name from {@link UnionMixinService#getPlatformAgents()}.
 */
public final class UnionPlatformAgent extends MixinPlatformAgentAbstract implements IMixinPlatformServiceAgent {
	@Override
	public void init() {
		// Nothing to do — UnionLauncher already set up the environment before Mixin was
		// bootstrapped.
	}

	@Override
	public String getSideName() {
		try {
			EnvType side = UnionLoader.get().getEnvironmentType();
			if (side == null) return null;
			return side == EnvType.CLIENT ? "CLIENT" : "SERVER";
		} catch (Throwable t) {
			return null;
		}
	}

	@Override
	public Collection<IContainerHandle> getMixinContainers() {
		Collection<IContainerHandle> out = new ArrayList<>();

		try {
			for (ModContainer mod : UnionLoader.get().getAllMods()) {
				out.add(new UnionContainerHandle(mod.getMetadata().getId()));
			}
		} catch (Throwable ignored) {
			// Loader not yet initialised — return empty.
		}

		return out;
	}

	@Override
	public void wire(Phase phase, IConsumer<Phase> phaseConsumer) {
		// No-op.
	}

	@Override
	public void unwire() {
		// No-op.
	}
}
