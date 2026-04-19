package dev.union.impl.accesswidener;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import dev.union.api.accesswidener.AccessType;

/**
 * Aggregated set of access-widener rules across all loaded mods. Keyed by internal-form class
 * names (i.e. {@code net/minecraft/server/MinecraftServer}, with slashes — matching the form
 * Mixin, ASM, and Fabric all use).
 *
 * <p>The registry is populated once during launch and then read-only during class loading.
 * All maps are {@link ConcurrentHashMap}-backed so concurrent reads from the classloader are
 * safe without external locking.
 */
public final class AccessWidener {
	/** Class-level widening: internal name → set of types applied. */
	private final Map<String, Set<AccessType>> classes = new ConcurrentHashMap<>();

	/** Method-level widening: (owner, name, desc) → set of types. */
	private final Map<MemberKey, Set<AccessType>> methods = new ConcurrentHashMap<>();

	/** Field-level widening: (owner, name, desc) → set of types. */
	private final Map<MemberKey, Set<AccessType>> fields = new ConcurrentHashMap<>();

	public void addClass(AccessType type, String internalName) {
		classes.computeIfAbsent(internalName, k -> EnumSet.noneOf(AccessType.class)).add(type);
	}

	public void addMethod(AccessType type, String owner, String name, String descriptor) {
		methods.computeIfAbsent(new MemberKey(owner, name, descriptor),
				k -> EnumSet.noneOf(AccessType.class)).add(type);
	}

	public void addField(AccessType type, String owner, String name, String descriptor) {
		fields.computeIfAbsent(new MemberKey(owner, name, descriptor),
				k -> EnumSet.noneOf(AccessType.class)).add(type);
	}

	public Set<AccessType> getClassEntry(String internalName) {
		Set<AccessType> v = classes.get(internalName);
		return v == null ? Collections.emptySet() : v;
	}

	public Set<AccessType> getMethodEntry(String owner, String name, String descriptor) {
		Set<AccessType> v = methods.get(new MemberKey(owner, name, descriptor));
		return v == null ? Collections.emptySet() : v;
	}

	public Set<AccessType> getFieldEntry(String owner, String name, String descriptor) {
		Set<AccessType> v = fields.get(new MemberKey(owner, name, descriptor));
		return v == null ? Collections.emptySet() : v;
	}

	/**
	 * @return every class-internal-name that has any widening applied. Used by the
	 *         classloader as a cheap membership check — if a class isn't in here we skip the
	 *         ASM read/write round-trip entirely.
	 */
	public Set<String> getTargets() {
		Set<String> all = new HashSet<>(classes.keySet());
		for (MemberKey k : methods.keySet()) all.add(k.owner);
		for (MemberKey k : fields.keySet()) all.add(k.owner);
		return all;
	}

	public boolean isEmpty() {
		return classes.isEmpty() && methods.isEmpty() && fields.isEmpty();
	}

	public int size() {
		return classes.size() + methods.size() + fields.size();
	}

	/** Immutable (owner, name, desc) triple. */
	static final class MemberKey {
		final String owner;
		final String name;
		final String desc;

		MemberKey(String owner, String name, String desc) {
			this.owner = owner;
			this.name = name;
			this.desc = desc;
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof MemberKey)) return false;
			MemberKey k = (MemberKey) o;
			return owner.equals(k.owner) && name.equals(k.name) && desc.equals(k.desc);
		}

		@Override
		public int hashCode() {
			return Objects.hash(owner, name, desc);
		}

		@Override
		public String toString() {
			return owner + "#" + name + desc;
		}
	}
}
