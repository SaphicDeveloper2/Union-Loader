package dev.union.api.network;

/**
 * Minimal transport-neutral write surface for {@link Payload#write(PayloadWriter)}. Concrete
 * implementations wrap Minecraft's {@code FriendlyByteBuf}, or (in tests) a
 * {@code ByteArrayOutputStream}-backed writer.
 *
 * <p>Keeping this interface MC-free means API modules compile against it without a
 * {@code FriendlyByteBuf} import. The MC-version integration module provides a thin adapter.
 */
public interface PayloadWriter {
	void writeByte(int v);
	void writeShort(int v);
	void writeInt(int v);
	void writeLong(long v);
	void writeFloat(float v);
	void writeDouble(double v);
	void writeBoolean(boolean v);

	/** UTF-8 encoded, length-prefixed (var-int). */
	void writeString(String s);

	void writeBytes(byte[] bytes);

	/** Variable-length int encoding (1–5 bytes). Matches MC's VarInt format. */
	void writeVarInt(int v);

	/** Variable-length long encoding (1–10 bytes). Matches MC's VarLong format. */
	void writeVarLong(long v);
}
