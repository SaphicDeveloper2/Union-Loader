package dev.union.api.lifecycle;

import dev.union.api.base.Identifier;
import dev.union.api.event.Event;

/**
 * Event shapes for the Minecraft lifecycle. Kept deliberately MC-free: the {@code server},
 * {@code world}, {@code player} fields are typed as {@link Object} (or not carried at all)
 * so this module compiles without Minecraft. The concrete wiring — reading the actual
 * {@code MinecraftServer}/{@code Level}/{@code Player} instance — happens in the MC-version
 * integration module that extends these and populates the fields.
 *
 * <h2>Events</h2>
 * <ul>
 *   <li>{@link ServerLifecycleEvent.Starting} / {@link ServerLifecycleEvent.Started} /
 *       {@link ServerLifecycleEvent.Stopping} / {@link ServerLifecycleEvent.Stopped} —
 *       fired around the {@code MinecraftServer} lifecycle.</li>
 *   <li>{@link TickEvent.ServerTick} / {@link TickEvent.ClientTick} /
 *       {@link TickEvent.WorldTick}.</li>
 *   <li>{@link WorldLifecycleEvent.Load} / {@link WorldLifecycleEvent.Unload} — per-world
 *       load / unload.</li>
 * </ul>
 */
public final class LifecycleEvents {
	private LifecycleEvents() { }

	// -------------------------------------------------------------------------------------

	public static sealed class ServerLifecycleEvent extends Event permits
			ServerLifecycleEvent.Starting, ServerLifecycleEvent.Started,
			ServerLifecycleEvent.Stopping, ServerLifecycleEvent.Stopped {

		private final Object server;

		protected ServerLifecycleEvent(Object server) { this.server = server; }

		public Object server() { return server; }

		public static final class Starting extends ServerLifecycleEvent {
			public Starting(Object server) { super(server); }
		}

		public static final class Started extends ServerLifecycleEvent {
			public Started(Object server) { super(server); }
		}

		public static final class Stopping extends ServerLifecycleEvent {
			public Stopping(Object server) { super(server); }
		}

		public static final class Stopped extends ServerLifecycleEvent {
			public Stopped(Object server) { super(server); }
		}
	}

	// -------------------------------------------------------------------------------------

	public static sealed class TickEvent extends Event permits
			TickEvent.ServerTick, TickEvent.ClientTick, TickEvent.WorldTick {

		public enum Phase { START, END }

		private final Phase phase;

		protected TickEvent(Phase phase) { this.phase = phase; }

		public Phase phase() { return phase; }

		public static final class ServerTick extends TickEvent {
			private final Object server;

			public ServerTick(Phase phase, Object server) {
				super(phase);
				this.server = server;
			}

			public Object server() { return server; }
		}

		public static final class ClientTick extends TickEvent {
			private final Object client;

			public ClientTick(Phase phase, Object client) {
				super(phase);
				this.client = client;
			}

			public Object client() { return client; }
		}

		public static final class WorldTick extends TickEvent {
			private final Object world;
			private final Identifier dimensionId;

			public WorldTick(Phase phase, Object world, Identifier dimensionId) {
				super(phase);
				this.world = world;
				this.dimensionId = dimensionId;
			}

			public Object world() { return world; }
			public Identifier dimensionId() { return dimensionId; }
		}
	}

	// -------------------------------------------------------------------------------------

	public static sealed class WorldLifecycleEvent extends Event permits
			WorldLifecycleEvent.Load, WorldLifecycleEvent.Unload {

		private final Object world;
		private final Identifier dimensionId;

		protected WorldLifecycleEvent(Object world, Identifier dimensionId) {
			this.world = world;
			this.dimensionId = dimensionId;
		}

		public Object world() { return world; }
		public Identifier dimensionId() { return dimensionId; }

		public static final class Load extends WorldLifecycleEvent {
			public Load(Object world, Identifier dimensionId) { super(world, dimensionId); }
		}

		public static final class Unload extends WorldLifecycleEvent {
			public Unload(Object world, Identifier dimensionId) { super(world, dimensionId); }
		}
	}
}
