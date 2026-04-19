package dev.union.api.network;

/**
 * Direction a payload travels. Matches vanilla's {@code PacketFlow} semantics but is MC-free.
 */
public enum PacketFlow {
	/** Server → client. */
	CLIENTBOUND,

	/** Client → server. */
	SERVERBOUND
}
