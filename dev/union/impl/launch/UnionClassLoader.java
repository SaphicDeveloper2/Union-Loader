package dev.union.impl.launch;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import dev.union.impl.accesswidener.AccessWidener;
import dev.union.impl.accesswidener.AccessWidenerTransformer;
import dev.union.impl.util.Log;

/**
 * Union's runtime classloader. Owns the game classpath plus every discovered mod JAR and
 * optionally routes every loaded class through Mixin's {@code IMixinTransformer} before
 * {@code defineClass}.
 *
 * <p>The transformer is looked up lazily via reflection from
 * {@code org.spongepowered.asm.launch.GlobalProperties} under key {@code TRANSFORMER} — it gets
 * populated by {@code MixinBootstrap.init()} during launch. Until then, transformation is a
 * no-op; the classloader can be constructed before Mixin is ready.
 *
 * <p>Exclusions: any class whose fully-qualified name starts with a registered exclusion prefix
 * is delegated directly to the parent classloader, never fetched via our URL search path and
 * never transformed. This is essential for Mixin's own classes and for ASM — transforming them
 * would be circular.
 */
public final class UnionClassLoader extends URLClassLoader {
	private static final String CATEGORY = "ClassLoader";

	/** Packages that must never be routed through the transformer. */
	private static final String[] BUILT_IN_EXCLUSIONS = {
			"java.",
			"jdk.",
			"sun.",
			"com.sun.",
			"javax.",
			// JDK-exported packages under non-"javax." prefixes. Loading any of these via our
			// classloader risks a LinkageError because the bootstrap classloader can also load
			// them — if a mod JAR happens to shade, say, an Xerces fork, the same FQN could
			// resolve to two different Class objects in two classloaders. Log4j's XML config
			// path crosses org.xml.sax during classload, which is exactly where this has been
			// observed to fail. Force-delegation to the parent ensures a single definition.
			"org.xml.",
			"org.w3c.",
			"org.ietf.jgss.",
			// Union loader-internal packages live on the parent (app) classloader as they ship
			// inside union-loader.jar itself. Mixin/ASM similarly live on the parent. These
			// must never be resolved via UnionClassLoader's URL search or we'd define a second
			// copy.
			//
			// DO NOT add "dev.union.api." here: the *public API* modules (union-api-base,
			// union-api-event, union-api-screen, union-api-keybind, union-api-resource, etc.)
			// ship as separate mod jars that are only on UnionClassLoader's URL list, not on
			// the parent. Forcing parent-first on the API packages would make mod code unable
			// to resolve them. The few API types that DO live in the loader jar (UnionLoader,
			// ModMetadata, ModContainer, ContactInformation, EnvType) are reachable via the
			// normal child-first findClass flow; child search hits the loader jar's URL first
			// because the loader jar is first on the parent's classpath and the child fallback
			// succeeds.
			"dev.union.impl.",
			"dev.union.mixin.",
			"org.spongepowered.asm.",
			"org.objectweb.asm.",
			"com.google.common.",
			"com.google.gson.",
	};

	static {
		ClassLoader.registerAsParallelCapable();
	}

	private final Set<String> exclusions = Collections.newSetFromMap(new ConcurrentHashMap<>());
	private final Set<String> loadedClasses = Collections.newSetFromMap(new ConcurrentHashMap<>());

	private volatile TransformerHandle transformer;
	private volatile AccessWidener accessWidener;

	public UnionClassLoader(ClassLoader parent) {
		super("UnionClassLoader", new URL[0], parent);

		for (String e : BUILT_IN_EXCLUSIONS) {
			exclusions.add(e);
		}
	}

	public void addPaths(List<Path> paths) {
		for (Path p : paths) addPath(p);
	}

	public void addPath(Path p) {
		try {
			addURL(p.toUri().toURL());
		} catch (java.net.MalformedURLException e) {
			throw new IllegalArgumentException("not a valid classpath entry: " + p, e);
		}
	}

	/** @return the URLs currently on this classloader's search path, in order. */
	public List<URL> getSearchPath() {
		URL[] urls = getURLs();
		List<URL> out = new ArrayList<>(urls.length);
		for (URL u : urls) out.add(u);
		return out;
	}

	/** Register a package prefix that must bypass the transformer and go straight to the parent. */
	public void addTransformerExclusion(String prefix) {
		exclusions.add(prefix);
	}

	/**
	 * Install the merged access-widener rule set. Called once by {@code UnionLauncher} after
	 * mod discovery. Passing {@code null} or an empty widener disables the pass.
	 */
	public void setAccessWidener(AccessWidener widener) {
		this.accessWidener = (widener != null && !widener.isEmpty()) ? widener : null;
	}

	public AccessWidener getAccessWidener() {
		return accessWidener;
	}

	public boolean isClassLoaded(String className) {
		return loadedClasses.contains(className);
	}

	/**
	 * Read the raw bytecode for {@code className}, optionally running it through the Mixin
	 * transformer. Returns {@code null} if the class is not on our URL search path.
	 *
	 * <p>Crucially this only looks at <b>our</b> URL list via {@link #findResource(String)} —
	 * it does NOT delegate to the parent classloader. If we delegated, classes present on the
	 * parent (like {@code dev.union.api.entrypoint.ClientModInitializer} which lives in the
	 * loader jar on the app classpath) would be fetched, their bytes defined locally under
	 * UnionClassLoader, and the same FQN would end up represented by two distinct Class
	 * objects — breaking {@code instanceof}, {@code isAssignableFrom}, and any reflective
	 * cross-loader operation. {@link #loadClass} handles parent delegation explicitly when our
	 * own search misses.
	 */
	public byte[] getClassBytes(String className, boolean runTransformers) throws IOException {
		String resource = className.replace('.', '/') + ".class";
		byte[] bytes;

		java.net.URL url = findResource(resource);
		if (url == null) return null;

		try (InputStream in = url.openStream()) {
			bytes = readAll(in);
		}

		if (runTransformers) {
			bytes = applyTransformers(className, bytes);
		}

		return bytes;
	}

	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		if (isExcluded(name)) {
			return getParent().loadClass(name);
		}

		byte[] bytes;

		try {
			bytes = getClassBytes(name, true);
		} catch (IOException e) {
			throw new ClassNotFoundException(name, e);
		}

		if (bytes == null) {
			throw new ClassNotFoundException(name);
		}

		URL source = getResource(name.replace('.', '/') + ".class");
		ProtectionDomain pd;

		if (source != null) {
			CodeSource cs = new CodeSource(source, (Certificate[]) null);
			pd = new ProtectionDomain(cs, null, this, null);
		} else {
			pd = new ProtectionDomain(new CodeSource(null, (CodeSigner[]) null), null, this, null);
		}

		definePackageIfNeeded(name);

		Class<?> cls = defineClass(name, bytes, 0, bytes.length, pd);
		loadedClasses.add(name);
		return cls;
	}

	@Override
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		synchronized (getClassLoadingLock(name)) {
			Class<?> cached = findLoadedClass(name);

			if (cached != null) {
				if (resolve) resolveClass(cached);
				return cached;
			}

			if (isExcluded(name)) {
				Class<?> cls = getParent().loadClass(name);
				if (resolve) resolveClass(cls);
				return cls;
			}

			Class<?> cls;

			try {
				cls = findClass(name);
			} catch (ClassNotFoundException cnf) {
				cls = getParent().loadClass(name);
			} catch (Throwable other) {
				// Non-CNF throwables from findClass (LinkageError, stray NoClassDefFoundError)
				// were being implicitly wrapped to CNF by callers. Surface them so misconfigs
				// are visible, not silently turned into "missing class".
				Log.error(CATEGORY, "findClass threw " + other.getClass().getSimpleName() + " for " + name, other);
				throw new ClassNotFoundException(name, other);
			}

			if (resolve) resolveClass(cls);
			return cls;
		}
	}

	private boolean isExcluded(String className) {
		for (String prefix : exclusions) {
			if (className.startsWith(prefix)) return true;
		}
		return false;
	}

	private void definePackageIfNeeded(String className) {
		int dot = className.lastIndexOf('.');
		if (dot < 0) return;

		String pkg = className.substring(0, dot);

		if (getDefinedPackage(pkg) == null) {
			try {
				definePackage(pkg, null, null, null, null, null, null, null);
			} catch (IllegalArgumentException ignored) {
				// race with another thread — fine
			}
		}
	}

	private byte[] applyTransformers(String className, byte[] bytes) {
		// Access widener first — mixins may reference widened members, so they need access to
		// see the post-AW shape.
		AccessWidener aw = this.accessWidener;

		if (aw != null) {
			try {
				bytes = AccessWidenerTransformer.transform(className, bytes, aw);
			} catch (Throwable t) {
				Log.error(CATEGORY, "Access widener threw for " + className, t);
			}
		}

		TransformerHandle handle = getTransformer();
		if (handle == null) return bytes;

		try {
			return handle.transform(className, bytes);
		} catch (Throwable t) {
			Log.error(CATEGORY, "Transformer threw for " + className, t);
			return bytes;
		}
	}

	/**
	 * Lazily look up the Mixin transformer via {@code GlobalProperties.get(Keys.TRANSFORMER)}.
	 * Reflection keeps the linkage loose — if Mixin isn't bootstrapped yet this silently
	 * returns {@code null} and we pass bytes through unchanged.
	 */
	private TransformerHandle getTransformer() {
		TransformerHandle cached = this.transformer;
		if (cached != null) return cached;

		synchronized (this) {
			if (this.transformer != null) return this.transformer;

			try {
				Class<?> keysClass = Class.forName("org.spongepowered.asm.launch.GlobalProperties$Keys", true, this);
				Object key = keysClass.getField("TRANSFORMER").get(null);

				Class<?> globalProps = Class.forName("org.spongepowered.asm.launch.GlobalProperties", true, this);
				Object tx = globalProps.getMethod("get", key.getClass()).invoke(null, key);

				if (tx == null) return null;

				TransformerHandle h = new TransformerHandle(tx);
				this.transformer = h;
				return h;
			} catch (Throwable t) {
				Log.debug(CATEGORY, "Mixin transformer not yet available: " + t.getMessage());
				return null;
			}
		}
	}

	private static byte[] readAll(InputStream in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(in.available(), 1024));
		byte[] buf = new byte[8192];
		int n;

		while ((n = in.read(buf)) > 0) {
			out.write(buf, 0, n);
		}

		return out.toByteArray();
	}

	/**
	 * Reflective holder for {@code IMixinTransformer#transformClassBytes(String, String, byte[])}.
	 * Loaded via reflection so the classloader does not have a direct link-time dependency on
	 * the Mixin transformer package.
	 */
	private static final class TransformerHandle {
		private final Object transformer;
		private final java.lang.reflect.Method transformClassBytes;

		TransformerHandle(Object transformer) throws NoSuchMethodException {
			this.transformer = transformer;
			this.transformClassBytes = transformer.getClass().getMethod(
					"transformClassBytes", String.class, String.class, byte[].class);
		}

		byte[] transform(String className, byte[] bytes) throws Throwable {
			Object result = transformClassBytes.invoke(transformer, className, className, bytes);
			return result instanceof byte[] ? (byte[]) result : bytes;
		}
	}
}
