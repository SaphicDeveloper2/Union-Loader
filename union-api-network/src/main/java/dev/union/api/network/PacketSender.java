package dev.union.api.network;

import java.util.Collection;

/**
 * Outbound payload dispatch. The MC-version integration module supplies the concrete
 * implementation — this interface is what mods call.
 *
 * <p>On the client, {@link #sendToServer} is the usual method. On the server, all the
 * {@code sendTo*} variants are available.
 */
public interface PacketSender {
	/** Access the current platform's packet sender. Installed by the integration module. */
	static PacketSender get() {
		PacketSender impl = Holder.impl;
		if (impl == null) throw new IllegalStateException("PacketSender has not been installed yet");
		return impl;
	}

	/**
	 * Install the platform-specific implementation. Called once, from the integration module's
	 * setup phase. Calling twice replaces the previous installation — useful for tests but
	 * should never happen in production.
	 */
	static void install(PacketSender impl) {
		Holder.impl = impl;
	}

	// --- client side ---------------------------------------------------------------------

	/** Send a payload to the server. No-op on the dedicated server. */
	void sendToServer(Payload payload);

	// --- server side ---------------------------------------------------------------------

	/**
	 * Send {@code payload} to the player identified by the MC-side player object (passed as
	 * {@code Object} to keep this API MC-free; integration layer type-checks).
	 */
	void sendToPlayer(Object player, Payload payload);

	/** Broadcast to every currently connected player. */
	void sendToAll(Payload payload);

	/** Send to a filtered set of players. Useful for per-world or per-dimension messaging. */
	void sendToPlayers(Collection<?> players, Payload payload);

	// --- holder --------------------------------------------------------------------------

	final class Holder {
		private Holder() { }
		static volatile PacketSender impl;
	}
}
