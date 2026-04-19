package dev.union.api.event;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Runtime service that answers two questions the {@link EventBus} needs for async dispatch:
 *
 * <ol>
 *   <li>"Is the current thread allowed to run handlers for events on region <em>K</em>?"</li>
 *   <li>"If not, how do I hand a task off to <em>K</em>'s owning thread?"</li>
 * </ol>
 *
 * <p>The service is installed once per runtime via {@link #install(RegionScheduler)} and
 * read with {@link #current()}. Three implementations are anticipated:
 *
 * <ul>
 *   <li>{@link SingleThreadedScheduler} — default. One region ({@link RegionKey#GLOBAL} or
 *       {@link RegionKey#CLIENT}), the calling thread is always on-region, tasks run inline.
 *       Used on dedicated servers without Folia and on all client runtimes.</li>
 *   <li>{@code FoliaRegionScheduler} — future, delegates to Folia's region scheduler.</li>
 *   <li>Test doubles — {@link SingleThreadedScheduler} also serves tests.</li>
 * </ul>
 *
 * <h3>Bridging to typed Minecraft objects</h3>
 * The key extraction methods ({@link #keyOf(Object, int, int)}, {@link #keyOf(Object)}) are
 * intentionally untyped so that this API has no compile-time Minecraft dependency. A
 * platform integration (e.g. {@code union-mc-26.1.2}) provides the scheduler implementation
 * that knows how to cast the {@code Object} back into a {@code Level}/{@code Entity} and
 * read a dimension id + block coordinates off it.
 */
public interface RegionScheduler {
	/**
	 * @return {@code true} if the calling thread is allowed to execute handlers for events
	 *         keyed to {@code region}. On the {@link SingleThreadedScheduler} this always
	 *         returns {@code true}.
	 */
	boolean isOnRegion(RegionKey region);

	/**
	 * Schedule {@code task} to run on the thread that owns {@code region}. If the current
	 * thread already owns {@code region} the implementation may run the task inline and
	 * return an already-completed future.
	 *
	 * <p>If the task throws, the returned future completes exceptionally with the thrown
	 * cause.
	 */
	<T> CompletableFuture<T> submit(RegionKey region, Supplier<T> task);

	/**
	 * Schedule {@code task} on {@code region} and block the caller until it returns. Used
	 * by {@link EventBus#post(Event)} when a synchronous contract must be honoured across a
	 * region boundary.
	 *
	 * <p>If {@code timeout} elapses before the task runs or completes, throws
	 * {@link java.util.concurrent.TimeoutException} wrapped in a
	 * {@link java.util.concurrent.CompletionException}. Implementations that always execute
	 * inline (i.e. {@link SingleThreadedScheduler}) ignore the timeout.
	 */
	<T> T submitAndWait(RegionKey region, Supplier<T> task, Duration timeout);

	/**
	 * Extract a {@code RegionKey} from a world + block coordinates. See
	 * {@link RegionKey#of(Object, int, int)} for usage rules.
	 *
	 * <p>The default implementation returns {@link RegionKey#GLOBAL} for any input — fine
	 * for non-Folia runtimes. Folia integrations should override this to produce per-region
	 * keys using Folia's region tile size.
	 */
	default RegionKey keyOf(Object world, int blockX, int blockZ) {
		return RegionKey.GLOBAL;
	}

	/**
	 * Extract a {@code RegionKey} from a single location-bearing object. See
	 * {@link RegionKey#of(Object)}.
	 *
	 * <p>The default implementation returns {@link RegionKey#GLOBAL}. Runtime integrations
	 * override this to recognise {@code Entity}, {@code BlockEntity} etc. and delegate to
	 * {@link #keyOf(Object, int, int)} with the extracted coordinates.
	 */
	default RegionKey keyOf(Object locationBearer) {
		return RegionKey.GLOBAL;
	}

	// ---------------------------------------------------------------------------------------
	// Global accessor
	// ---------------------------------------------------------------------------------------

	/**
	 * Replace the active scheduler. Called once by the platform layer during loader init,
	 * before any mod code runs.
	 */
	static void install(RegionScheduler scheduler) {
		if (scheduler == null) throw new NullPointerException("scheduler");
		Holder.CURRENT = scheduler;
	}

	/** @return the currently installed scheduler. Never {@code null}. */
	static RegionScheduler current() {
		return Holder.CURRENT;
	}

	/** Hidden holder so {@code current()} and {@code install()} can share a volatile slot. */
	final class Holder {
		private Holder() { }
		static volatile RegionScheduler CURRENT = new SingleThreadedScheduler();
	}
}
