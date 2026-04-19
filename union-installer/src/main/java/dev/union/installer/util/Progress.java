package dev.union.installer.util;

/**
 * Minimal progress sink for installer steps. GUI wires this to a label + progress bar;
 * CLI wires it to stdout.
 */
public interface Progress {
	void update(String message);

	default void update(String message, int percent) {
		update(message + " (" + percent + "%)");
	}

	Progress STDOUT = msg -> System.out.println("[union-installer] " + msg);
}
