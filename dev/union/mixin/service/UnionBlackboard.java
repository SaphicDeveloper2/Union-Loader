package dev.union.mixin.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.spongepowered.asm.service.IGlobalPropertyService;
import org.spongepowered.asm.service.IPropertyKey;

/**
 * Simple {@link IGlobalPropertyService} backed by a thread-safe map. Mirrors LaunchWrapper's
 * {@code Launch.blackboard} or ModLauncher's service-scoped blackboard.
 */
public final class UnionBlackboard implements IGlobalPropertyService {
	private static final Map<String, Object> STORE = new ConcurrentHashMap<>();

	static final class Key implements IPropertyKey {
		private final String name;

		Key(String name) { this.name = name; }

		@Override
		public String toString() { return name; }

		@Override
		public int hashCode() { return name.hashCode(); }

		@Override
		public boolean equals(Object other) {
			return other instanceof Key && ((Key) other).name.equals(name);
		}
	}

	@Override
	public IPropertyKey resolveKey(String name) {
		return new Key(name);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T getProperty(IPropertyKey key) {
		return (T) STORE.get(key.toString());
	}

	@Override
	public void setProperty(IPropertyKey key, Object value) {
		if (value == null) STORE.remove(key.toString());
		else STORE.put(key.toString(), value);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T getProperty(IPropertyKey key, T defaultValue) {
		Object v = STORE.get(key.toString());
		return v != null ? (T) v : defaultValue;
	}

	@Override
	public String getPropertyString(IPropertyKey key, String defaultValue) {
		Object v = STORE.get(key.toString());
		return v != null ? v.toString() : defaultValue;
	}
}
