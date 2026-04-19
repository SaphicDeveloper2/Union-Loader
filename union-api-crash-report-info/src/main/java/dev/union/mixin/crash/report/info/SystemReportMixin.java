package dev.union.mixin.crash.report.info;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.SystemReport;

import dev.union.api.ModContainer;
import dev.union.api.UnionLoader;

/**
 * Adds a "Union Mods" section to Minecraft's {@link SystemReport} (shown at the top of
 * every crash log) listing every loaded mod with its version.
 *
 * <p>Ported from {@code union-crash-report-info-v1}'s {@code SystemReportMixin}.
 * Differences from the source:
 *
 * <ul>
 *   <li>Package moved from {@code net.fabricmc.union.mixin.*} to {@code dev.union.mixin.*}.</li>
 *   <li>{@code FabricLoader} (spelled {@code UnionLoader} in the zip) replaced with
 *       Union's {@link UnionLoader#get()} accessor.</li>
 *   <li>The source recursed through {@code ModContainer#getContainingMod} /
 *       {@code getContainedMods} to render a hierarchical JiJ tree. Union's
 *       {@link ModContainer} API doesn't currently expose that hierarchy (nested mods get
 *       flattened into the top-level list by {@code ModDiscoverer}), so the Union port
 *       prints a flat list. If hierarchy tracking is added to {@code ModContainer} later
 *       this mixin can be extended without breaking anything.</li>
 *   <li>Section label changed from "Fabric Mods" to "Union Mods" to match the loader
 *       branding.</li>
 * </ul>
 */
@Mixin(SystemReport.class)
public abstract class SystemReportMixin {
	@Shadow
	public abstract void setDetail(String string, Supplier<String> supplier);

	@Inject(at = @At("RETURN"), method = "<init>")
	private void union$fillSystemDetails(CallbackInfo info) {
		setDetail("Union Mods", () -> {
			List<ModContainer> mods = new ArrayList<>(UnionLoader.get().getAllMods());
			StringBuilder modString = new StringBuilder();
			union$appendMods(modString, mods);
			return modString.toString();
		});
	}

	@Unique
	private static void union$appendMods(StringBuilder modString, List<ModContainer> mods) {
		mods.sort(Comparator.comparing(mod -> mod.getMetadata().getId()));

		for (ModContainer mod : mods) {
			modString.append('\n');
			modString.append("\t\t");
			modString.append(mod.getMetadata().getId());
			modString.append(": ");
			modString.append(mod.getMetadata().getName());
			modString.append(' ');
			modString.append(mod.getMetadata().getVersion());
		}
	}
}
