package dev.union.impl.util;

import java.io.PrintStream;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Stderr-based logger. Keeps the loader dep-free; mods that want log4j should use
 * Minecraft's own logger once the game classpath is up.
 */
public final class Log {
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
	private static final boolean DEBUG = Boolean.getBoolean("union.debug");

	private Log() { }

	public static void info(String category, String msg) {
		print(System.out, "INFO", category, msg);
	}

	public static void warn(String category, String msg) {
		print(System.err, "WARN", category, msg);
	}

	public static void error(String category, String msg) {
		print(System.err, "ERROR", category, msg);
	}

	public static void error(String category, String msg, Throwable t) {
		print(System.err, "ERROR", category, msg);
		t.printStackTrace(System.err);
	}

	public static void debug(String category, String msg) {
		if (DEBUG) print(System.out, "DEBUG", category, msg);
	}

	private static void print(PrintStream out, String level, String category, String msg) {
		out.printf("[%s] [Union/%s] [%s] %s%n", LocalTime.now().format(TIME), category, level, msg);
	}
}
