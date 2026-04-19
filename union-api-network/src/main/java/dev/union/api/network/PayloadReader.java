package dev.union.api.network;

/**
 * Read-side counterpart to {@link PayloadWriter}. Used by {@link Payload.Type} decoders.
 */
public interface PayloadReader {
	byte readByte();
	short readShort();
	int readInt();
	long readLong();
	float readFloat();
	double readDouble();
	boolean readBoolean();
	String readString();
	byte[] readBytes(int count);
	int readVarInt();
	long readVarLong();

	/** @return the number of unread bytes remaining in the buffer. */
	int remaining();
}
