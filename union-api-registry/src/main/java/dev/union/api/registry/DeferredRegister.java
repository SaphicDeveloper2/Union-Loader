package dev.union.api.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import dev.union.api.base.Identifier;
import dev.union.api.event.EventBus;

/**
 * NeoForge-style deferred registry helper. Usage pattern:
 *
 * <pre>
 * public final class MyMod {
 *     public static final DeferredRegister&lt;Item&gt; ITEMS = DeferredRegister.create(Registries.ITEM, "mymod");
 *     public static final RegistryObject&lt;Item&gt; MY_ITEM = ITEMS.register("my_item", () -&gt; new Item(...));
 *
 *     public MyMod() {
 *         ITEMS.registerSelf(EventBus.MOD);
 *     }
 * }
 * </pre>
 *
 * <p>Entries are <em>declared</em> (as suppliers) at class-init time and <em>created</em> only
 * when the registration phase of the corresponding registry fires. This way mods can declare
 * entries as static finals without worrying about lookup order or class-load timing.
 *
 * @param <T> element type (Item, Block, Entity, etc.).
 */
public final class DeferredRegister<T> {
	private final RegistryKey<T> registryKey;
	private final String modid;
	private final Map<Identifier, Entry<T>> entries = new LinkedHashMap<>();
	private volatile boolean fired;

	private DeferredRegister(RegistryKey<T> registryKey, String modid) {
		this.registryKey = Objects.requireNonNull(registryKey);
		this.modid = Objects.requireNonNull(modid);
	}

	public static <T> DeferredRegister<T> create(RegistryKey<T> registryKey, String modid) {
		return new DeferredRegister<>(registryKey, modid);
	}

	/**
	 * Declare an entry. Returns an unresolved {@link RegistryObject} that becomes populated
	 * when the registry fires. Safe to call from class-init / enum-init.
	 */
	public <I extends T> RegistryObject<I> register(String name, Supplier<? extends I> supplier) {
		if (fired) {
			throw new IllegalStateException(registryKey + " registration for " + modid
					+ " has already fired; can't add " + name + " after the fact.");
		}

		Identifier id = Identifier.of(modid, name);

		if (entries.containsKey(id)) {
			throw new IllegalArgumentException("duplicate entry id for " + modid + ": " + id);
		}

		@SuppressWarnings("unchecked")
		RegistryObject<I> ref = (RegistryObject<I>) new RegistryObject<Object>(id);
		@SuppressWarnings({"rawtypes", "unchecked"})
		Entry<T> entry = new Entry<>((RegistryObject) ref, (Supplier) supplier);
		entries.put(id, entry);
		return ref;
	}

	/**
	 * Hook this register onto the given bus (usually {@link EventBus#MOD}). Once fired it
	 * resolves every declared {@link RegistryObject} against the incoming
	 * {@link RegisterEvent}.
	 */
	public void registerSelf(EventBus bus) {
		bus.addListener(RegisterEvent.class, event -> {
			if (!event.getRegistryKey().equals(registryKey)) return;

			@SuppressWarnings("unchecked")
			RegisterEvent<T> typed = (RegisterEvent<T>) event;

			for (Entry<T> e : entries.values()) {
				T value = e.supplier.get();

				if (value == null) {
					throw new IllegalStateException("Supplier for " + e.ref.id() + " returned null");
				}

				T registered = typed.register(e.ref.id(), value);
				e.ref.resolve(registered);
			}

			fired = true;
		});
	}

	public RegistryKey<T> getRegistryKey() { return registryKey; }

	public String getModId() { return modid; }

	public List<RegistryObject<? extends T>> getEntries() {
		List<RegistryObject<? extends T>> out = new ArrayList<>(entries.size());
		for (Entry<T> e : entries.values()) out.add(e.ref);
		return Collections.unmodifiableList(out);
	}

	// ---------------------------------------------------------------------

	private static final class Entry<T> {
		final RegistryObject<T> ref;
		final Supplier<? extends T> supplier;

		Entry(RegistryObject<T> ref, Supplier<? extends T> supplier) {
			this.ref = ref;
			this.supplier = supplier;
		}
	}
}
