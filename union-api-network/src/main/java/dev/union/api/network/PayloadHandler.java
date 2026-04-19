package dev.union.api.network;

/**
 * Handler for a registered payload. Invoked on the network thread — if the handler needs to
 * touch game state, it should call {@link PayloadContext#enqueue(Runnable)} to hop onto the
 * main thread.
 *
 * @param <P> payload type.
 */
@FunctionalInterface
public interface PayloadHandler<P extends Payload> {
	void handle(P payload, PayloadContext context);
}
