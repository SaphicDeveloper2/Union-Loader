package dev.union.api.network;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Context supplied to payload handlers. Abstracts the MC-side scheduler so handlers can opt
 * into running on the main game thread via {@link #enqueue(Runnable)}.
 *
 * <p>Fields like {@code player} / {@code connection} are kept as {@link Object} so this
 * module compiles without Minecraft. The integration layer supplies a concrete
 * {@code ConcretePayloadContext} that implements this interface and hands mods typed views.
 */
public interface PayloadContext {
	PacketFlow flow();

	/**
	 * @return the player this payload is associated with, as an {@link Object}. On the server
	 *         this is the sending player; on the client it's always {@code null} (or the
	 *         local-player for integrated-server packets). Cast to {@code Player} at the
	 *         boundary.
	 */
	Object player();

	/**
	 * Schedule {@code task} to run on the appropriate game thread (server or client main).
	 * Returns a future that completes when the task has run — useful for reply flows.
	 */
	CompletableFuture<Void> enqueue(Runnable task);

	/**
	 * Like {@link #enqueue(Runnable)} but returning the supplier's value on completion.
	 */
	<T> CompletableFuture<T> enqueueResult(Supplier<T> task);

	/**
	 * Send a reply payload back along the same connection. Convenience for request/response
	 * flows — equivalent to going through {@link PacketSender} with the current connection's
	 * target.
	 */
	void reply(Payload payload);

	/**
	 * Disconnect the peer with the given message. Useful for rejecting malformed payloads
	 * on the server side.
	 */
	void disconnect(String reason);
}
