package dev.union.api.event;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Trivial {@link RegionScheduler} that treats the entire runtime as one region. Used on
 * dedicated servers without Folia and on all client runtimes.
 *
 * <ul>
 *   <li>{@link #isOnRegion(RegionKey)} always returns {@code true}.</li>
 *   <li>{@link #submit(RegionKey, Supplier)} runs the task inline on the calling thread
 *       and returns an already-completed future. If the task throws, the returned future
 *       completes exceptionally instead of propagating.</li>
 *   <li>{@link #submitAndWait(RegionKey, Supplier, Duration)} also runs inline; the
 *       timeout is ignored (inline execution can't exceed it).</li>
 *   <li>{@link #keyOf(Object, int, int)} and {@link #keyOf(Object)} inherit the default
 *       {@link RegionKey#GLOBAL} answer from the interface — no per-location key needed
 *       when there's only one region.</li>
 * </ul>
 *
 * <p>This means async mods written against {@link EventBus#postAsync(Event)} work
 * unmodified on non-Folia runtimes: the future always completes immediately, the caller
 * thread ran every handler, and everything is observationally identical to a synchronous
 * {@link EventBus#post(Event)}.
 */
public class SingleThreadedScheduler implements RegionScheduler {
	@Override
	public boolean isOnRegion(RegionKey region) {
		return true;
	}

	@Override
	public <T> CompletableFuture<T> submit(RegionKey region, Supplier<T> task) {
		try {
			return CompletableFuture.completedFuture(task.get());
		} catch (Throwable t) {
			return CompletableFuture.failedFuture(t);
		}
	}

	@Override
	public <T> T submitAndWait(RegionKey region, Supplier<T> task, Duration timeout) {
		// Inline execution — a timeout can't apply.
		return task.get();
	}
}
