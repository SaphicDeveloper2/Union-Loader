package dev.union.impl.launch;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.union.api.EnvType;
import dev.union.api.entrypoint.ClientModInitializer;
import dev.union.api.entrypoint.DedicatedServerModInitializer;
import dev.union.api.entrypoint.ModInitializer;
import dev.union.impl.ModContainerImpl;
import dev.union.impl.UnionLoaderImpl;
import dev.union.impl.accesswidener.AccessWidener;
import dev.union.impl.accesswidener.AccessWidenerDiscovery;
import dev.union.impl.discovery.BuiltinModExtractor;
import dev.union.impl.discovery.DiscoveryResult;
import dev.union.impl.discovery.ModDiscoverer;
import dev.union.impl.util.Log;

/**
 * Union's runtime bootstrap entry point. The vanilla Minecraft launcher invokes
 * {@link #main(String[])} via the {@code mainClass} field of our installed profile JSON.
 *
 * <p>Responsibilities, in order:
 * <ol>
 *   <li>Resolve env ({@code CLIENT} / {@code SERVER}) from system property or class heuristic.</li>
 *   <li>Resolve the game directory from {@code --gameDir} / system property / {@code user.dir}.</li>
 *   <li>Build a {@link UnionClassLoader}, seed it with the system classpath and discovered mods.</li>
 *   <li>Populate the {@link UnionLoaderImpl} singleton.</li>
 *   <li>Dispatch {@code main} + side-specific entrypoints.</li>
 *   <li>Reflectively invoke Minecraft's real main with the original args, under our classloader.</li>
 * </ol>
 */
public final class UnionLauncher {
	private static final String CATEGORY = "Launch";

	private static final String PROP_SIDE = "union.side";
	private static final String PROP_GAMEDIR = "union.gameDir";
	private static final String PROP_DEV = "union.development";

	private static final String CLIENT_MAIN = "net.minecraft.client.main.Main";
	private static final String SERVER_MAIN_PRIMARY = "net.minecraft.server.Main";
	private static final String SERVER_MAIN_FALLBACK = "net.minecraft.bundler.Main";

	private UnionLauncher() { }

	public static void main(String[] args) throws Throwable {
		Thread.currentThread().setUncaughtExceptionHandler((t, e) -> {
			Log.error(CATEGORY, "Uncaught exception on " + t.getName(), e);
		});

		EnvType envType = resolveEnvType();
		Path gameDir = resolveGameDir(args);
		boolean development = Boolean.getBoolean(PROP_DEV);

		Log.info(CATEGORY, "Union bootstrap: side=" + envType + ", gameDir=" + gameDir + (development ? ", dev=true" : ""));

		UnionLoaderImpl loader = UnionLoaderImpl.INSTANCE;
		loader.bootstrap(envType, gameDir, development);

		// Extract bundled built-in mods (Union API uber-jar) to the game-dir cache. These are
		// treated as top-level mods by ModDiscoverer and win on duplicate-id collisions.
		List<java.nio.file.Path> builtins = BuiltinModExtractor.extract(loader.getGameDir());

		// Discover mods + nested library jars from <gameDir>/mods (+ builtins)
		ModDiscoverer discoverer = new ModDiscoverer(loader.getModsDir(), envType, loader.getGameDir());
		DiscoveryResult result = discoverer.discoverAll(builtins);
		List<ModContainerImpl> mods = result.mods();
		loader.setMods(mods);

		// Build our classloader on top of the app classloader, seeded with mod JARs + any
		// extracted JiJ library JARs.
		UnionClassLoader classLoader = new UnionClassLoader(UnionLauncher.class.getClassLoader());

		for (ModContainerImpl mod : mods) {
			Log.debug(CATEGORY, "CL + mod: " + mod.getMetadata().getId() + " -> " + mod.getOrigin().getFileName());
			classLoader.addPath(mod.getOrigin());
		}

		for (java.nio.file.Path lib : result.libraryJars()) {
			Log.debug(CATEGORY, "CL + lib: " + lib.getFileName());
			classLoader.addPath(lib);
		}

		Thread.currentThread().setContextClassLoader(classLoader);

		// Collect every mod's access-widener file and install the merged rule set on the
		// classloader. Happens before Mixin bootstrap so the AW pass runs first — mixins can
		// rely on widened access.
		AccessWidener widener = AccessWidenerDiscovery.collect(mods);
		classLoader.setAccessWidener(widener);

		// Bootstrap Mixin before any entrypoint dispatch so that subsequent class loads go
		// through the transformer. Register every mod's declared mixin configs.
		MixinIntegration.bootstrap(envType);
		MixinIntegration.registerConfigs(mods);

		// Eagerly initialise MC's version detector BEFORE dispatching entrypoints. Mods can
		// schedule async work during their init (e.g. ModMenu's update checker fires on a
		// background worker the moment its ClientModInitializer runs) and that work often
		// reads SharedConstants#CURRENT_VERSION. If we leave the version-prime until after
		// MC's main(), the worker races and crashes with "Game version not set".
		//
		// SharedConstants.tryDetectVersion() is idempotent — when MC's main() later calls it,
		// the getstatic check short-circuits and nothing happens.
		tryDetectMcVersion(classLoader);

		// Dispatch entrypoints
		dispatchEntrypoints(classLoader, envType);

		// Hand off to Minecraft
		String mainClassName = resolveMinecraftMain(envType, classLoader);
		Log.info(CATEGORY, "Handing off to " + mainClassName);

		Class<?> mcMain = Class.forName(mainClassName, false, classLoader);
		Method main = mcMain.getDeclaredMethod("main", String[].class);
		main.setAccessible(true);
		main.invoke(null, (Object) args);
	}

	/**
	 * Reflectively invoke {@code net.minecraft.SharedConstants.tryDetectVersion()} under our
	 * classloader. Swallows failure — if the method signature changes in a future MC snapshot
	 * we'd rather ship a warning than a hard crash; the real MC main will still run.
	 */
	private static void tryDetectMcVersion(ClassLoader cl) {
		try {
			Class<?> sc = Class.forName("net.minecraft.SharedConstants", true, cl);
			Method m = sc.getDeclaredMethod("tryDetectVersion");
			m.invoke(null);
			Log.debug(CATEGORY, "primed SharedConstants.tryDetectVersion()");
		} catch (ClassNotFoundException | NoSuchMethodException e) {
			Log.debug(CATEGORY, "SharedConstants.tryDetectVersion not available on this MC: " + e.getClass().getSimpleName());
		} catch (Throwable t) {
			Log.warn(CATEGORY, "SharedConstants.tryDetectVersion() failed (non-fatal): " + t);
		}
	}

	private static void dispatchEntrypoints(ClassLoader cl, EnvType envType) {
		UnionLoaderImpl loader = UnionLoaderImpl.INSTANCE;

		List<ModInitializer> main = loader.entrypoints().getEntrypoints("main", ModInitializer.class, cl);
		Log.info(CATEGORY, "Dispatching 'main' to " + main.size() + " entrypoint(s)");

		for (ModInitializer m : main) {
			try {
				m.onInitialize();
			} catch (Throwable t) {
				Log.error(CATEGORY, "Entrypoint 'main' threw in " + m.getClass().getName(), t);
			}
		}

		if (envType == EnvType.CLIENT) {
			List<ClientModInitializer> client = loader.entrypoints().getEntrypoints("client", ClientModInitializer.class, cl);
			Log.info(CATEGORY, "Dispatching 'client' to " + client.size() + " entrypoint(s)");

			for (ClientModInitializer m : client) {
				try {
					m.onInitializeClient();
				} catch (Throwable t) {
					Log.error(CATEGORY, "Entrypoint 'client' threw in " + m.getClass().getName(), t);
				}
			}
		} else {
			List<DedicatedServerModInitializer> server = loader.entrypoints().getEntrypoints("server", DedicatedServerModInitializer.class, cl);
			Log.info(CATEGORY, "Dispatching 'server' to " + server.size() + " entrypoint(s)");

			for (DedicatedServerModInitializer m : server) {
				try {
					m.onInitializeServer();
				} catch (Throwable t) {
					Log.error(CATEGORY, "Entrypoint 'server' threw in " + m.getClass().getName(), t);
				}
			}
		}
	}

	private static EnvType resolveEnvType() {
		String raw = System.getProperty(PROP_SIDE);

		if (raw != null && !raw.isEmpty()) {
			switch (raw.toLowerCase(Locale.ROOT)) {
			case "client": return EnvType.CLIENT;
			case "server": return EnvType.SERVER;
			default: throw new IllegalArgumentException("invalid " + PROP_SIDE + ": " + raw);
			}
		}

		// Fall back to class-path heuristic
		try {
			Class.forName(CLIENT_MAIN, false, UnionLauncher.class.getClassLoader());
			return EnvType.CLIENT;
		} catch (ClassNotFoundException ignored) { }

		return EnvType.SERVER;
	}

	private static Path resolveGameDir(String[] args) {
		// --gameDir <path>
		for (int i = 0; i < args.length - 1; i++) {
			if ("--gameDir".equals(args[i])) return Paths.get(args[i + 1]).toAbsolutePath();
		}

		String prop = System.getProperty(PROP_GAMEDIR);
		if (prop != null) return Paths.get(prop).toAbsolutePath();

		return Paths.get(System.getProperty("user.dir")).toAbsolutePath();
	}

	private static String resolveMinecraftMain(EnvType envType, ClassLoader cl) {
		List<String> candidates = new ArrayList<>();

		if (envType == EnvType.CLIENT) {
			candidates.add(CLIENT_MAIN);
		} else {
			candidates.add(SERVER_MAIN_PRIMARY);
			candidates.add(SERVER_MAIN_FALLBACK);
		}

		for (String c : candidates) {
			try {
				Class.forName(c, false, cl);
				return c;
			} catch (ClassNotFoundException ignored) { }
		}

		throw new IllegalStateException("Could not locate Minecraft main class for side " + envType
				+ "; tried " + candidates);
	}
}
