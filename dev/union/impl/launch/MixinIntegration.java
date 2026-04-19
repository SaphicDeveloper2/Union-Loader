package dev.union.impl.launch;

import java.util.List;

import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;

import dev.union.api.EnvType;
import dev.union.api.ModContainer;
import dev.union.impl.util.Log;

/**
 * Handles the Mixin engine bootstrap sequence for Union. Call order:
 * <ol>
 *   <li>{@link #bootstrap(EnvType)} — once, after the classloader exists and before any mod
 *       entrypoints run. Initialises the Mixin engine and sets the environment side.</li>
 *   <li>{@link #registerConfigs(List)} — registers each mod's declared mixin config JSONs.</li>
 *   <li>{@link #goToDefault()} — switches Mixin into the {@code DEFAULT} phase so
 *       transformation is active for subsequent class loads.</li>
 * </ol>
 *
 * <p>Any step failing is logged but does not abort boot — Mixin-less mods should still work.
 */
public final class MixinIntegration {
	private static final String CATEGORY = "Mixin";

	private MixinIntegration() { }

	public static void bootstrap(EnvType envType) {
		Log.info(CATEGORY, "Bootstrapping Mixin (side=" + envType + ")");

		try {
			MixinBootstrap.init();
			MixinEnvironment env = MixinEnvironment.getDefaultEnvironment();
			env.setSide(envType == EnvType.CLIENT
					? MixinEnvironment.Side.CLIENT
					: MixinEnvironment.Side.SERVER);
			Log.debug(CATEGORY, "Mixin bootstrap complete");
		} catch (Throwable t) {
			Log.error(CATEGORY, "Mixin bootstrap failed — Mixin features will be disabled", t);
		}
	}

	public static void registerConfigs(List<? extends ModContainer> mods) {
		int count = 0;

		// Use the 2-arg Mixins.addConfiguration(configFile, source) — it internally passes
		// MixinEnvironment.getDefaultEnvironment() as the fallback phase. The 1-arg version
		// passes null, which combined with a config JSON that declares no "selector" field
		// lets MixinConfig.onLoad leave this.env == null and NPE at the first option lookup.
		// The symptom is a misleading "resource was invalid or could not be read" error
		// wrapping that NPE. Passing null for source is fine; Mixin accepts it.
		for (ModContainer mod : mods) {
			for (String config : mod.getMetadata().getMixinConfigs()) {
				try {
					Mixins.addConfiguration(config, null);
					Log.debug(CATEGORY, "Registered mixin config '" + config
							+ "' from mod " + mod.getMetadata().getId());
					count++;
				} catch (Throwable t) {
					Log.error(CATEGORY, "Failed to register mixin config '" + config
							+ "' from mod " + mod.getMetadata().getId(), t);
				}
			}
		}

		Log.info(CATEGORY, "Registered " + count + " mixin config" + (count == 1 ? "" : "s"));
	}

	public static void goToDefault() {
		try {
			MixinEnvironment.getDefaultEnvironment().audit();
		} catch (Throwable t) {
			Log.debug(CATEGORY, "Mixin audit skipped: " + t.getMessage());
		}
	}
}
