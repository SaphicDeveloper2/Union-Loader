package dev.union.api.event;

/**
 * Base class for events tied to a specific {@linkplain RegionKey region}. Dispatching an
 * {@code AffineEvent} through {@link EventBus#postAsync(Event)} will route the event to the
 * region's owning thread; if the caller is already on that thread the event fires inline.
 *
 * <p>On non-Folia runtimes the entire server is one region ({@link RegionKey#GLOBAL}) and
 * affine events degrade to synchronous dispatch on the caller thread — the API shape is
 * identical but there's no thread crossing to arrange. The same is true for client-side
 * events, which all live on {@link RegionKey#CLIENT}.
 *
 * <h3>When to subclass this vs. {@link FreeEvent}</h3>
 * <ul>
 *   <li>Extend {@code AffineEvent} if your event concerns a specific block, entity, chunk,
 *       or player — i.e. there's a meaningful answer to "which region owns this state?".</li>
 *   <li>Implement {@link FreeEvent} (or just extend {@link Event}) if your event is
 *       server-wide, lifecycle-related, or otherwise location-independent.</li>
 * </ul>
 *
 * <h3>Region derivation</h3>
 * Subclasses implement {@link #region()} to return the {@code RegionKey} that owns the event.
 * Typical implementations use the static factories on {@link RegionKey}:
 *
 * <pre>{@code
 * public final class BlockBreakEvent extends AffineEvent implements Cancelable {
 *     private final Level world;
 *     private final BlockPos pos;
 *     // ...
 *     @Override public RegionKey region() {
 *         return RegionKey.of(world, pos);
 *     }
 * }
 * }</pre>
 */
public abstract class AffineEvent extends Event {
	/**
	 * @return the region that owns the state this event is about. Must never return
	 *         {@code null}; implementations should fall back to {@link RegionKey#GLOBAL}
	 *         if no more specific region is available.
	 */
	public abstract RegionKey region();
}
