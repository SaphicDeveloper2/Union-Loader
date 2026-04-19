package dev.union.api.event;

/**
 * Marker interface: this event has no region affinity and may be dispatched on any thread.
 *
 * <p>Most Union events are free: server lifecycle, client ticks, resource reloads, registry
 * events, config changes, etc. They have no ties to a specific world location and their
 * handlers are expected to be thread-safe or use their own synchronization.
 *
 * <p>Events that <em>are</em> tied to a world position should extend {@link AffineEvent}
 * instead. The {@link EventBus} uses the {@code FreeEvent}/{@code AffineEvent} split to
 * decide whether to route a post through the {@link RegionScheduler}.
 *
 * <p>Note: a concrete event that extends {@link Event} but implements neither
 * {@code FreeEvent} nor {@link AffineEvent} is treated as free for dispatch purposes. The
 * marker exists so implementers can be explicit about their intent and so static analysis
 * tools can enforce the classification.
 */
public interface FreeEvent {
}
