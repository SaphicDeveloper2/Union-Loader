package dev.union.impl.entrypoint;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.union.api.ModContainer;
import dev.union.impl.util.Log;

/**
 * Stores and dispatches entrypoints discovered from {@code union.mod.json} files.
 */
public final class EntrypointStorage {
	private static final String CATEGORY = "Entrypoint";

	private final Map<String, List<Entry>> byKey = new HashMap<>();

	public void register(String key, String className, ModContainer owner) {
		byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(new Entry(className, owner));
	}

	public void ingest(Collection<? extends ModContainer> mods) {
		for (ModContainer mod : mods) {
			for (Map.Entry<String, List<String>> e : mod.getMetadata().getEntrypoints().entrySet()) {
				for (String className : e.getValue()) {
					register(e.getKey(), className, mod);
				}
			}
		}
	}

	/**
	 * Instantiate every entrypoint class registered under {@code key} and cast each to {@code type}.
	 * Errors are logged but do not abort the run.
	 */
	@SuppressWarnings("unchecked")
	public <T> List<T> getEntrypoints(String key, Class<T> type, ClassLoader classLoader) {
		List<Entry> entries = byKey.getOrDefault(key, List.of());
		List<T> instances = new ArrayList<>(entries.size());

		for (Entry entry : entries) {
			try {
				Class<?> clazz = Class.forName(entry.className, true, classLoader);

				if (!type.isAssignableFrom(clazz)) {
					Log.error(CATEGORY, entry.owner.getMetadata().getId() + ": entrypoint class "
							+ entry.className + " does not implement " + type.getName());
					continue;
				}

				Constructor<?> ctor = clazz.getDeclaredConstructor();
				ctor.setAccessible(true);
				instances.add((T) ctor.newInstance());
			} catch (ReflectiveOperationException | LinkageError e) {
				Log.error(CATEGORY, "Failed to instantiate '" + key + "' entrypoint "
						+ entry.className + " from mod " + entry.owner.getMetadata().getId(), e);
			}
		}

		return instances;
	}

	private static final class Entry {
		final String className;
		final ModContainer owner;

		Entry(String className, ModContainer owner) {
			this.className = className;
			this.owner = owner;
		}
	}
}
