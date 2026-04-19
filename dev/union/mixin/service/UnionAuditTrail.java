package dev.union.mixin.service;

import org.spongepowered.asm.service.IMixinAuditTrail;

import dev.union.impl.util.Log;

/**
 * No-op {@link IMixinAuditTrail} for v0. Tracks mixin applications only when
 * {@code -Dunion.debug=true}.
 */
public final class UnionAuditTrail implements IMixinAuditTrail {
	private static final String CATEGORY = "Mixin/Audit";

	@Override
	public void onApply(String className, String mixinName) {
		Log.debug(CATEGORY, "apply  " + className + " <- " + mixinName);
	}

	@Override
	public void onPostProcess(String className) {
		Log.debug(CATEGORY, "post   " + className);
	}

	@Override
	public void onGenerate(String className, String generatorName) {
		Log.debug(CATEGORY, "gen    " + className + " by " + generatorName);
	}
}
