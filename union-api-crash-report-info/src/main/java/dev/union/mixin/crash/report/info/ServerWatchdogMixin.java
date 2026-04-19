package dev.union.mixin.crash.report.info;

import java.lang.management.ThreadInfo;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import net.minecraft.server.dedicated.ServerWatchdog;

import dev.union.impl.crash.report.info.ThreadPrinting;

/**
 * Patches {@code ServerWatchdog#createWatchdogCrashReport} to print the entire thread dump
 * instead of the default-truncated view.
 *
 * <p>Ported from {@code union-crash-report-info-v1}'s {@code ServerWatchdogMixin}. Package
 * moved from {@code net.fabricmc.union.mixin.*} to {@code dev.union.mixin.*}. The
 * {@code @ModifyArg} target is unchanged and still matches MC 26.1.2's
 * {@code StringBuilder.append(Object)} call at ordinal 0 inside the method.
 */
@Mixin(ServerWatchdog.class)
public class ServerWatchdogMixin {
	@ModifyArg(method = "createWatchdogCrashReport(Ljava/lang/String;J)Lnet/minecraft/CrashReport;",
			at = @At(value = "INVOKE",
					target = "Ljava/lang/StringBuilder;append(Ljava/lang/Object;)Ljava/lang/StringBuilder;",
					ordinal = 0)
	)
	private static Object printEntireThreadDump(Object object) {
		if (object instanceof ThreadInfo threadInfo) {
			return ThreadPrinting.fullThreadInfoToString(threadInfo);
		}

		return object;
	}
}
