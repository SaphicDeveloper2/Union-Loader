package dev.union.mixin.service;

import org.spongepowered.asm.service.IMixinServiceBootstrap;
import org.spongepowered.asm.service.ServiceInitialisationException;

import dev.union.impl.launch.UnionClassLoader;

/**
 * Pre-init hook Mixin calls before the main service is constructed. Responsible for:
 * <ul>
 *   <li>Declaring which service class should be used ({@link #getServiceClassName()}).</li>
 *   <li>Checking that the Union classloader is alive — if we're not running under Union, fail
 *       fast so Mixin's {@code ServiceLoader} falls through to another registered service.</li>
 *   <li>Adding classloader exclusions so Mixin / ASM / Union launch classes are always resolved
 *       from the bootstrap classloader, never from a transformed path. Mirrors what the
 *       LaunchWrapper and ModLauncher services do.</li>
 * </ul>
 */
public final class UnionMixinServiceBootstrap implements IMixinServiceBootstrap {
	public static final String NAME = "Union";
	public static final String SERVICE_CLASS = "dev.union.mixin.service.UnionMixinService";

	// Packages that must never be routed through the transforming classloader. See the
	// companion comment in UnionClassLoader#BUILT_IN_EXCLUSIONS — these are parent-first
	// delegations, required for types shared between UnionClassLoader and the bootstrap/app
	// classloader (Mixin itself, ASM, loader-internal impl/mixin packages).
	//
	// DO NOT add "dev.union.api." here: public API classes like
	// dev.union.api.keybind.Keybinds ship in separate mod jars on UnionClassLoader's URL
	// list only. Excluding them would force parent-first delegation, and the parent can't
	// find them — the result is ClassNotFoundException at mod-init time.
	private static final String[] DEFAULT_EXCLUSIONS = {
			"org.spongepowered.asm.",
			"org.objectweb.asm.",
			"com.google.gson.",
			"com.google.common.",
			"dev.union.impl.",
			"dev.union.mixin.",
	};

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public String getServiceClassName() {
		return SERVICE_CLASS;
	}

	@Override
	public void bootstrap() {
		ClassLoader ccl = Thread.currentThread().getContextClassLoader();

		if (!(ccl instanceof UnionClassLoader)) {
			throw new ServiceInitialisationException(
					NAME + " service requires the Union class loader (got " + (ccl == null ? "null" : ccl.getClass().getName()) + ")");
		}

		UnionClassLoader ucl = (UnionClassLoader) ccl;

		for (String pkg : DEFAULT_EXCLUSIONS) {
			ucl.addTransformerExclusion(pkg);
		}
	}
}
