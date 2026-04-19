package dev.union.api.attachment;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import dev.union.api.base.Identifier;

/**
 * Central registry of declared {@link AttachmentType}s. Mods register their types at mod-init
 * (during the boot phase) and the integration layer reads this registry to know what to look
 * for on each target's NBT and what sync packets to wire up.
 */
public final class AttachmentRegistry {
	private static final Map<Identifier, AttachmentType<?>> BY_ID = new ConcurrentHashMap<>();

	private AttachmentRegistry() { }

	public static <T> AttachmentType<T> register(AttachmentType<T> type) {
		Objects.requireNonNull(type);

		AttachmentType<?> prev = BY_ID.putIfAbsent(type.id(), type);

		if (prev != null) {
			throw new IllegalArgumentException("attachment " + type.id()
					+ " already registered as " + prev);
		}

		return type;
	}

	public static AttachmentType<?> get(Identifier id) {
		return BY_ID.get(id);
	}

	public static Collection<AttachmentType<?>> all() {
		return Collections.unmodifiableCollection(BY_ID.values());
	}

	public static int size() {
		return BY_ID.size();
	}
}
