package dev.union.api.screen;

import dev.union.api.event.Event;

/**
 * Screen lifecycle events posted on {@link dev.union.api.event.EventBus#MAIN} on the client.
 * Mirrors Fabric's {@code fabric-screen-api-v1} surface but delivered via Union's event bus
 * so handlers use the same {@code @SubscribeEvent}/priority/phase mechanics as everywhere else.
 *
 * <p>Fields are typed as {@link Object} so the API module compiles without a Minecraft
 * classpath. At the mixin integration layer, the posted event wraps the real
 * {@code net.minecraft.client.gui.screens.Screen}; consumers cast at the boundary:
 *
 * <pre>
 * bus.addListener(ScreenEvents.AfterInit.class, e -&gt; {
 *     if (e.screen() instanceof TitleScreen ts) { ... }
 * });
 * </pre>
 *
 * <h2>Events</h2>
 * <ul>
 *   <li>{@link BeforeInit} — fired before the screen's widgets are populated.</li>
 *   <li>{@link AfterInit}  — fired after {@code Screen#init} returns; most mods hook this to
 *       add buttons, inspect the widget list, etc.</li>
 *   <li>{@link Remove}     — fired when the screen is leaving (replaced or closed).</li>
 * </ul>
 */
public final class ScreenEvents {
	private ScreenEvents() { }

	public static sealed class ScreenEvent extends Event permits BeforeInit, AfterInit, Remove {
		private final Object client;
		private final Object screen;
		private final int width;
		private final int height;

		protected ScreenEvent(Object client, Object screen, int width, int height) {
			this.client = client;
			this.screen = screen;
			this.width = width;
			this.height = height;
		}

		public Object client() { return client; }
		public Object screen() { return screen; }
		public int    width()  { return width; }
		public int    height() { return height; }
	}

	/** Fired before a screen's {@code init} populates widgets. */
	public static final class BeforeInit extends ScreenEvent {
		public BeforeInit(Object client, Object screen, int width, int height) {
			super(client, screen, width, height);
		}
	}

	/** Fired after a screen's {@code init} returns. Typical hook-point for adding widgets. */
	public static final class AfterInit extends ScreenEvent {
		public AfterInit(Object client, Object screen, int width, int height) {
			super(client, screen, width, height);
		}
	}

	/** Fired when a screen is being removed (closed or replaced). */
	public static final class Remove extends ScreenEvent {
		public Remove(Object client, Object screen, int width, int height) {
			super(client, screen, width, height);
		}
	}
}
