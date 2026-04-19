package dev.union.api.network;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import dev.union.api.base.Identifier;

/**
 * Mod-facing registry for payload types. Modelled on NeoForge's {@code PayloadRegistrar} and
 * vanilla's payload registration, but decoupled from the concrete MC channel implementation.
 *
 * <p>Usage:
 * <pre>
 * NetworkRegistry net = NetworkRegistry.forMod("mymod");
 *
 * net.playBidirectional(MY_PAYLOAD, (payload, ctx) -&gt; {
 *     ctx.enqueue(() -&gt; handleOnGameThread(payload, ctx.player()));
 * });
 *
 * net.serverbound(REQUEST_TP, (req, ctx) -&gt; { ... });
 * </pre>
 *
 * <p>The MC-version integration module attaches the actual vanilla channel callbacks during
 * its own setup phase — mods declare their types here and the integration layer plumbs them
 * in.
 *
 * <p>{@code play*} registers on the PLAY (in-game) protocol. Configuration-phase payloads can
 * be added later with a separate {@code configuration*} family if required.
 */
public final class NetworkRegistry {
	private static final Map<String, NetworkRegistry> BY_MOD = new java.util.concurrent.ConcurrentHashMap<>();

	private final String modid;
	private final Map<Identifier, Binding<?>> playBindings = new LinkedHashMap<>();

	private NetworkRegistry(String modid) {
		this.modid = Objects.requireNonNull(modid);
	}

	public static NetworkRegistry forMod(String modid) {
		return BY_MOD.computeIfAbsent(modid, NetworkRegistry::new);
	}

	public String getModId() { return modid; }

	public <P extends Payload> NetworkRegistry playClientbound(Payload.Type<P> type, PayloadHandler<P> handler) {
		return register(type, PacketFlow.CLIENTBOUND, handler);
	}

	public <P extends Payload> NetworkRegistry playServerbound(Payload.Type<P> type, PayloadHandler<P> handler) {
		return register(type, PacketFlow.SERVERBOUND, handler);
	}

	/**
	 * Bind one handler for both directions. Handlers should check {@link PayloadContext#flow()}
	 * if the two paths need different behaviour.
	 */
	public <P extends Payload> NetworkRegistry playBidirectional(Payload.Type<P> type, PayloadHandler<P> handler) {
		register(type, PacketFlow.CLIENTBOUND, handler);
		register(type, PacketFlow.SERVERBOUND, handler);
		return this;
	}

	private <P extends Payload> NetworkRegistry register(Payload.Type<P> type, PacketFlow flow, PayloadHandler<P> handler) {
		Objects.requireNonNull(type);
		Objects.requireNonNull(handler);

		if (!type.id().namespace().equals(modid)) {
			throw new IllegalArgumentException("payload " + type.id() + " does not belong to mod '" + modid
					+ "' (namespace mismatch)");
		}

		playBindings.put(type.id(), new Binding<>(type, flow, handler));
		return this;
	}

	/**
	 * @return every binding declared on this registry. The integration layer iterates this
	 *         after the mod's common-setup phase and wires each into MC's channel manager.
	 */
	public Map<Identifier, Binding<?>> getBindings() {
		return Collections.unmodifiableMap(playBindings);
	}

	/**
	 * Single registered payload + its handler. Package-visible record so the integration
	 * layer can pattern-match on it; mods don't construct these directly.
	 */
	public record Binding<P extends Payload>(Payload.Type<P> type, PacketFlow flow, PayloadHandler<P> handler) {
		public Binding {
			Objects.requireNonNull(type);
			Objects.requireNonNull(flow);
			Objects.requireNonNull(handler);
		}
	}
}
