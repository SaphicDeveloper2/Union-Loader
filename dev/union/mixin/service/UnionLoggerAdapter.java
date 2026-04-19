package dev.union.mixin.service;

import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.logging.Level;
import org.spongepowered.asm.logging.LoggerAdapterAbstract;

import dev.union.impl.util.Log;

/**
 * Bridges Mixin's {@link ILogger} interface to Union's {@link Log}. Formatting uses SLF4J-style
 * {@code {}} placeholders — we emulate the minimum Mixin requires.
 */
public final class UnionLoggerAdapter extends LoggerAdapterAbstract {
	public UnionLoggerAdapter(String name) {
		super(name);
	}

	@Override
	public String getType() {
		return "Union";
	}

	@Override
	public <T extends Throwable> T throwing(T t) {
		Log.error(getId(), "Throwing " + t.getClass().getName() + ": " + t.getMessage(), t);
		return t;
	}

	@Override
	public void catching(Level level, Throwable t) {
		log(level, "Caught exception: " + t.getMessage(), t);
	}

	@Override
	public void log(Level level, String message, Object... params) {
		log0(level, format(message, params), extractThrowable(params));
	}

	@Override
	public void log(Level level, String message, Throwable t) {
		log0(level, message, t);
	}

	private void log0(Level level, String message, Throwable t) {
		String name = getId();

		switch (level) {
		case FATAL:
		case ERROR:
			if (t != null) Log.error(name, message, t);
			else Log.error(name, message);
			break;
		case WARN:
			Log.warn(name, message);
			if (t != null) t.printStackTrace(System.err);
			break;
		case INFO:
			Log.info(name, message);
			if (t != null) t.printStackTrace(System.err);
			break;
		case DEBUG:
		case TRACE:
			Log.debug(name, message);
			break;
		}
	}

	/**
	 * Minimal SLF4J-style {@code {}} formatter.
	 */
	static String format(String pattern, Object... params) {
		if (params == null || params.length == 0 || pattern == null || pattern.isEmpty()) {
			return pattern == null ? "" : pattern;
		}

		StringBuilder sb = new StringBuilder(pattern.length() + params.length * 8);
		int i = 0;
		int p = 0;
		int len = pattern.length();

		while (i < len) {
			char c = pattern.charAt(i);

			if (c == '{' && i + 1 < len && pattern.charAt(i + 1) == '}' && p < params.length) {
				Object v = params[p++];
				// Skip trailing Throwable — it's the cause, not a format argument
				if (p == params.length && v instanceof Throwable) {
					sb.append("{}");
				} else {
					sb.append(v);
				}
				i += 2;
			} else {
				sb.append(c);
				i++;
			}
		}

		return sb.toString();
	}

	private static Throwable extractThrowable(Object[] params) {
		if (params != null && params.length > 0) {
			Object last = params[params.length - 1];
			if (last instanceof Throwable) return (Throwable) last;
		}
		return null;
	}
}
