package dev.union.installer;

import java.awt.GraphicsEnvironment;

import dev.union.installer.cli.Bootstrapper;
import dev.union.installer.gui.InstallerGui;

/**
 * Installer entry point.
 *
 * <p>Dispatch rules:
 * <ul>
 *   <li>No args and display available → launch {@link InstallerGui}.</li>
 *   <li>No args, headless → print CLI help.</li>
 *   <li>Any args, or first arg is {@code install} → run the CLI {@link Bootstrapper}.</li>
 * </ul>
 */
public final class Main {
	public static void main(String[] args) {
		if (args.length == 0 && !GraphicsEnvironment.isHeadless()) {
			InstallerGui.launch();
			return;
		}

		// Tolerate leading subcommands: 'install' or 'install-server'
		if (args.length > 0 && "install".equalsIgnoreCase(args[0])) {
			String[] tail = new String[args.length - 1];
			System.arraycopy(args, 1, tail, 0, tail.length);
			System.exit(Bootstrapper.run(tail));
			return;
		}

		if (args.length > 0 && "install-server".equalsIgnoreCase(args[0])) {
			String[] tail = new String[args.length];
			System.arraycopy(args, 1, tail, 0, args.length - 1);
			tail[tail.length - 1] = "--server";
			System.exit(Bootstrapper.run(tail));
			return;
		}

		System.exit(Bootstrapper.run(args));
	}
}
