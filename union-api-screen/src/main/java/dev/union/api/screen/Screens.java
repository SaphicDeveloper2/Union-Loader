package dev.union.api.screen;

import java.util.List;

/**
 * Utility accessor for screen internals mods commonly need: the Minecraft client instance, the
 * mutable list of widgets (Fabric's "children"/"drawables"), and basic layout dimensions.
 *
 * <p>Union's integration layer supplies the concrete accessor by mixing {@link ScreenAccess}
 * interface injection onto {@code net.minecraft.client.gui.screens.Screen}. All methods on
 * {@link Screens} are static dispatchers that cast to {@code ScreenAccess}; if the integration
 * module isn't loaded the cast throws a clear error.
 *
 * <p>Matches Fabric's {@code net.fabricmc.fabric.api.client.screen.v1.Screens}.
 */
public final class Screens {
	private Screens() { }

	/**
	 * @return the mutable list of focusable child widgets on the screen. Modifications via
	 *         {@link java.util.List#add(Object)} become visible immediately.
	 */
	@SuppressWarnings("unchecked")
	public static List<Object> getWidgets(Object screen) {
		return requireAccess(screen).union$getRenderables();
	}

	/** @deprecated renamed; use {@link #getWidgets(Object)}. */
	@Deprecated
	public static List<Object> getButtons(Object screen) {
		return getWidgets(screen);
	}

	/**
	 * @return the Minecraft client instance associated with this screen. Typed as {@link Object}
	 *         because the API module compiles without MC; cast to {@code Minecraft} at the
	 *         call site.
	 */
	public static Object getClient(Object screen) {
		return requireAccess(screen).union$getMinecraft();
	}

	public static int getWidth(Object screen)  { return requireAccess(screen).union$getScreenWidth(); }
	public static int getHeight(Object screen) { return requireAccess(screen).union$getScreenHeight(); }

	private static ScreenAccess requireAccess(Object screen) {
		if (!(screen instanceof ScreenAccess acc)) {
			throw new IllegalStateException("Screens API called with non-Union-integrated screen "
					+ (screen == null ? "null" : screen.getClass().getName())
					+ "; union-mc integration module must be loaded.");
		}
		return acc;
	}

	/**
	 * Interface injected onto every {@code net.minecraft.client.gui.screens.Screen} by the
	 * integration module's {@code ScreenAccessMixin}. Mods call the static helpers on
	 * {@link Screens} rather than casting to this directly, but it's public for advanced use.
	 */
	public interface ScreenAccess {
		List<Object> union$getRenderables();
		Object union$getMinecraft();
		int    union$getScreenWidth();
		int    union$getScreenHeight();
	}
}
