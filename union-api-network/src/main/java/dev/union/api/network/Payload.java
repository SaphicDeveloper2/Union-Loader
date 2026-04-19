package dev.union.api.network;

import java.util.Objects;
import java.util.function.Function;

import dev.union.api.base.Identifier;

/**
 * A piece of data that travels across the client↔server boundary. Union networking is
 * modelled after NeoForge 1.20.5+ and vanilla 1.20.5+ payload channels: each message type is
 * a {@link Payload} implementation with a stable {@link Identifier} and a self-contained
 * encode/decode pair.
 *
 * <p>Payloads are declared once at init via {@link NetworkRegistry#register} and then sent
 * using {@link PacketSender#send}. The concrete transport (vanilla networking or something
 * else) is supplied by the MC-version integration module.
 */
public interface Payload {
	Identifier id();

	/**
	 * Write this payload to the given output. Implementations should use simple primitive
	 * operations; the integration layer wraps the output with whatever buffer type MC wants.
	 */
	void write(PayloadWriter out);

	/**
	 * Type token for a payload class. Used as the registry key and for type-safe dispatch.
	 *
	 * @param id        stable identifier (channel name).
	 * @param decoder   constructs a new payload from a reader.
	 * @param <P>       payload type.
	 */
	record Type<P extends Payload>(Identifier id, Function<PayloadReader, P> decoder) {
		public Type {
			Objects.requireNonNull(id);
			Objects.requireNonNull(decoder);
		}
	}
}
