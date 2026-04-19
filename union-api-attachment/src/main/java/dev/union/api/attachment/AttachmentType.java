package dev.union.api.attachment;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

import dev.union.api.base.Identifier;

/**
 * Type token for a data attachment. Built via {@link Builder} — typically as a {@code static
 * final} on your mod class — and then registered via {@link AttachmentRegistry#register}.
 *
 * <p>Each attachment type knows:
 * <ul>
 *   <li>Its stable {@link Identifier} (namespace + path).</li>
 *   <li>Its host kind ({@link AttachmentTarget}).</li>
 *   <li>How to produce a default value (used on first access if nothing has been set).</li>
 *   <li>Whether it serialises ({@link #isPersistent()}) and how.</li>
 *   <li>Whether it syncs to the client ({@link #isSynced()}).</li>
 *   <li>Whether it survives entity death / dimension change ({@link #survivesDeath()} /
 *       {@link #copiesOnDimensionChange()}).</li>
 * </ul>
 *
 * <p>Serialisation is expressed as plain {@code byte[]} round-trips. The integration module
 * wraps those into NBT with the appropriate MC tag type. This keeps the API module MC-free.
 *
 * @param <T> attachment value type.
 */
public final class AttachmentType<T> {
	private final Identifier id;
	private final AttachmentTarget target;
	private final Class<T> valueType;
	private final Supplier<T> defaultSupplier;

	/** {@code null} for non-persistent attachments. */
	private final Function<T, byte[]> serializer;
	private final Function<byte[], T> deserializer;

	private final boolean synced;
	private final boolean survivesDeath;
	private final boolean copiesOnDimensionChange;

	private AttachmentType(Builder<T> b) {
		this.id = b.id;
		this.target = b.target;
		this.valueType = b.valueType;
		this.defaultSupplier = b.defaultSupplier;
		this.serializer = b.serializer;
		this.deserializer = b.deserializer;
		this.synced = b.synced;
		this.survivesDeath = b.survivesDeath;
		this.copiesOnDimensionChange = b.copiesOnDimensionChange;
	}

	public Identifier id() { return id; }
	public AttachmentTarget target() { return target; }
	public Class<T> valueType() { return valueType; }
	public Supplier<T> defaultSupplier() { return defaultSupplier; }
	public boolean isPersistent() { return serializer != null; }
	public boolean isSynced() { return synced; }
	public boolean survivesDeath() { return survivesDeath; }
	public boolean copiesOnDimensionChange() { return copiesOnDimensionChange; }

	public byte[] serialize(T value) {
		if (serializer == null) throw new UnsupportedOperationException(id + " is non-persistent");
		return serializer.apply(value);
	}

	public T deserialize(byte[] bytes) {
		if (deserializer == null) throw new UnsupportedOperationException(id + " is non-persistent");
		return deserializer.apply(bytes);
	}

	@Override
	public String toString() {
		return "AttachmentType(" + id + ", " + target + ")";
	}

	// -------------------------------------------------------------------------------------

	public static <T> Builder<T> builder(Identifier id, AttachmentTarget target,
			Class<T> valueType, Supplier<T> defaultSupplier) {
		return new Builder<>(id, target, valueType, defaultSupplier);
	}

	public static final class Builder<T> {
		private final Identifier id;
		private final AttachmentTarget target;
		private final Class<T> valueType;
		private final Supplier<T> defaultSupplier;

		private Function<T, byte[]> serializer;
		private Function<byte[], T> deserializer;
		private boolean synced;
		private boolean survivesDeath;
		private boolean copiesOnDimensionChange;

		Builder(Identifier id, AttachmentTarget target, Class<T> valueType, Supplier<T> defaultSupplier) {
			this.id = Objects.requireNonNull(id);
			this.target = Objects.requireNonNull(target);
			this.valueType = Objects.requireNonNull(valueType);
			this.defaultSupplier = Objects.requireNonNull(defaultSupplier);
		}

		/**
		 * Enable persistence by providing encode/decode functions. The integration module is
		 * responsible for wrapping the byte array into whatever MC NBT tag fits.
		 */
		public Builder<T> persistent(Function<T, byte[]> serializer, Function<byte[], T> deserializer) {
			this.serializer = Objects.requireNonNull(serializer);
			this.deserializer = Objects.requireNonNull(deserializer);
			return this;
		}

		/** Sync changes to the client automatically. Requires {@link #persistent}. */
		public Builder<T> synced() {
			this.synced = true;
			return this;
		}

		/** Preserve the value when an entity dies (useful for stats, tokens, etc.). */
		public Builder<T> survivesDeath() {
			this.survivesDeath = true;
			return this;
		}

		public Builder<T> copiesOnDimensionChange() {
			this.copiesOnDimensionChange = true;
			return this;
		}

		public AttachmentType<T> build() {
			if (synced && serializer == null) {
				throw new IllegalStateException("attachment " + id + ": cannot sync a non-persistent attachment");
			}

			return new AttachmentType<>(this);
		}
	}
}
