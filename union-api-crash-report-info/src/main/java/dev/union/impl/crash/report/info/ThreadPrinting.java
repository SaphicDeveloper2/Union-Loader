package dev.union.impl.crash.report.info;

import java.lang.management.LockInfo;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;

/**
 * Modified copy of {@link ThreadInfo#toString}'s format, but with every frame in the stack
 * trace instead of the default MAX_FRAMES cap. Used by the Union watchdog mixin to make
 * crash reports dump the full thread state rather than the truncated view.
 *
 * <p>Ported in-place from {@code union-crash-report-info-v1} (Fabric-equivalent API zip,
 * Samantha's version). Package moved from {@code net.fabricmc.union.impl.crash.report.info}
 * to {@code dev.union.impl.crash.report.info} to match Union's convention. Behaviour is
 * unchanged — this class has no external dependencies beyond the JDK.
 */
public final class ThreadPrinting {
	private ThreadPrinting() { }

	public static String fullThreadInfoToString(ThreadInfo threadInfo) {
		StringBuilder sb = new StringBuilder("\"" + threadInfo.getThreadName() + "\""
				+ (threadInfo.isDaemon() ? " daemon" : "")
				+ " prio=" + threadInfo.getPriority()
				+ " Id=" + threadInfo.getThreadId() + " "
				+ threadInfo.getThreadState());

		if (threadInfo.getLockName() != null) {
			sb.append(" on ").append(threadInfo.getLockName());
		}

		if (threadInfo.getLockOwnerName() != null) {
			sb.append(" owned by \"").append(threadInfo.getLockOwnerName())
					.append("\" Id=").append(threadInfo.getLockOwnerId());
		}

		if (threadInfo.isSuspended()) {
			sb.append(" (suspended)");
		}

		if (threadInfo.isInNative()) {
			sb.append(" (in native)");
		}

		sb.append('\n');

		StackTraceElement[] stackTraceElements = threadInfo.getStackTrace();

		for (int i = 0; i < stackTraceElements.length; i++) {
			StackTraceElement ste = stackTraceElements[i];
			sb.append("\tat ").append(ste.toString());
			sb.append('\n');

			if (i == 0 && threadInfo.getLockInfo() != null) {
				Thread.State ts = threadInfo.getThreadState();
				switch (ts) {
					case BLOCKED -> {
						sb.append("\t-  blocked on ").append(threadInfo.getLockInfo());
						sb.append('\n');
					}
					case WAITING, TIMED_WAITING -> {
						sb.append("\t-  waiting on ").append(threadInfo.getLockInfo());
						sb.append('\n');
					}
					default -> {
					}
				}
			}

			for (MonitorInfo mi : threadInfo.getLockedMonitors()) {
				if (mi.getLockedStackDepth() == i) {
					sb.append("\t-  locked ").append(mi);
					sb.append('\n');
				}
			}
		}

		LockInfo[] locks = threadInfo.getLockedSynchronizers();

		if (locks.length > 0) {
			sb.append("\n\tNumber of locked synchronizers = ").append(locks.length);
			sb.append('\n');

			for (LockInfo li : locks) {
				sb.append("\t- ").append(li);
				sb.append('\n');
			}
		}

		sb.append('\n');
		return sb.toString();
	}
}
