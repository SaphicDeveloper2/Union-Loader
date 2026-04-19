package dev.union.api.event;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import dev.union.api.base.Identifier;

/**
 * Central event dispatcher. NeoForge-style: events are Java objects, handlers are
 * {@code Consumer<E>}. Fabric-style: phases via {@link Phases}. Priorities via
 * {@link Priority}.
 *
 * <h2>Two common bus instances</h2>
 * <ul>
 *   <li>{@link #MAIN} — game runtime events (tick, server start, block place, …).</li>
 *   <li>{@link #MOD}  — mod lifecycle events fired during loader boot
 *       (registration, common setup, client setup, data generation). See
 *       {@code union-api-registry} and {@code union-api-lifecycle}.</li>
 * </ul>
 *
 * <p>Create additional buses for isolated subsystems by instantiating directly.
 *
 * <h2>Registration</h2>
 * <ul>
 *   <li>Explicit: {@link #addListener(Class, Consumer)} / {@link #addListener(Class, Priority, boolean, Identifier, Consumer)}.</li>
 *   <li>Reflection: {@link #register(Object)} / {@link #register(Class)} — scans for
 *       {@code @SubscribeEvent}-annotated methods on the target.</li>
 * </ul>
 *
 * <h2>Dispatch</h2>
 * <p>{@link #post(Event)} runs every registered handler for the event's concrete class, plus
 * handlers registered for any of its superclasses / interfaces. Handlers execute in this
 * order:
 * <ol>
 *   <li>Priority rank (HIGHEST → LOWEST).</li>
 *   <li>Within a priority level, phase topological order (see {@link Phases}).</li>
 *   <li>Within a phase, registration order.</li>
 * </ol>
 *
 * <p>If a handler throws, the exception is logged and dispatch continues — a misbehaving
 * third-party mod must never take the whole pipeline down. The thrown exception is stored on
 * the event (via {@link #getLastException()} helper on the bus, for tests). In production code
 * you should use {@code try/catch} around {@link #post} if you need guaranteed-clean dispatch.
 *
 * <p>Thread-safe: {@link #post(Event)} is read-only against the handler list; registration
 * compacts under write lock.
 */
public final class EventBus {
	public static final EventBus MAIN = new EventBus("main");
	public static final EventBus MOD  = new EventBus("mod");

	private final String name;
	private final Map<Class<? extends Event>, List<Handler<?>>> handlers = new ConcurrentHashMap<>();
	private final AtomicLong registrationSequence = new AtomicLong();

	// Error sink for observability; never relied on for correctness.
	private volatile Throwable lastException;
	private volatile Consumer<Throwable> errorHandler;

	public EventBus(String name) {
		this.name = name;
		this.errorHandler = t -> {
			System.err.println("[union:event/" + this.name + "] Handler threw: " + t);
			t.printStackTrace(System.err);
		};
	}

	public String getName() {
		return name;
	}

	public void setErrorHandler(Consumer<Throwable> handler) {
		if (handler != null) this.errorHandler = handler;
	}

	public Throwable getLastException() {
		return lastException;
	}

	// -------------------------------------------------------------------------------------
	// Registration
	// -------------------------------------------------------------------------------------

	public <E extends Event> void addListener(Class<E> eventClass, Consumer<E> handler) {
		addListener(eventClass, Priority.NORMAL, false, Phases.DEFAULT_PHASE, handler);
	}

	public <E extends Event> void addListener(Class<E> eventClass, Priority priority, Consumer<E> handler) {
		addListener(eventClass, priority, false, Phases.DEFAULT_PHASE, handler);
	}

	public <E extends Event> void addListener(Class<E> eventClass, Priority priority,
			boolean acceptsCanceled, Identifier phase, Consumer<E> handler) {
		if (eventClass == null || handler == null) throw new NullPointerException("eventClass/handler");
		if (phase == null) phase = Phases.DEFAULT_PHASE;

		Handler<E> h = new Handler<>(eventClass, priority, acceptsCanceled, phase,
				handler, registrationSequence.incrementAndGet());

		handlers.compute(eventClass, (k, existing) -> {
			List<Handler<?>> list = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
			list.add(h);
			sort(list, eventClass);
			return Collections.unmodifiableList(list);
		});
	}

	/**
	 * Register every {@code @SubscribeEvent}-annotated method on {@code target}. Instance
	 * methods bind to {@code target}; static methods are invoked statically (though scanning
	 * an instance for static methods works too).
	 */
	public void register(Object target) {
		registerScanned(target, target.getClass(), false);
	}

	/** Register static {@code @SubscribeEvent} methods on the given class. */
	public void register(Class<?> clazz) {
		registerScanned(null, clazz, true);
	}

	private void registerScanned(Object instance, Class<?> clazz, boolean staticOnly) {
		for (Method m : clazz.getDeclaredMethods()) {
			SubscribeEvent ann = m.getAnnotation(SubscribeEvent.class);
			if (ann == null) continue;

			boolean isStatic = Modifier.isStatic(m.getModifiers());

			if (staticOnly && !isStatic) continue;
			if (!isStatic && instance == null) {
				throw new IllegalArgumentException("non-static @SubscribeEvent on " + clazz.getName()
						+ "#" + m.getName() + " needs an instance");
			}

			if (m.getParameterCount() != 1) {
				throw new IllegalArgumentException("@SubscribeEvent " + clazz.getName() + "#" + m.getName()
						+ " must take exactly one parameter");
			}

			Class<?> paramType = m.getParameterTypes()[0];

			if (!Event.class.isAssignableFrom(paramType)) {
				throw new IllegalArgumentException("@SubscribeEvent " + clazz.getName() + "#" + m.getName()
						+ " parameter must extend Event");
			}

			@SuppressWarnings({"unchecked", "rawtypes"})
			Class<? extends Event> eventType = (Class<? extends Event>) paramType;

			Identifier phase = ann.phase().isEmpty() ? Phases.DEFAULT_PHASE : Identifier.parse(ann.phase());

			try {
				m.setAccessible(true);
			} catch (RuntimeException ignored) { }

			Object boundInstance = isStatic ? null : instance;

			@SuppressWarnings({"rawtypes", "unchecked"})
			Consumer lambda = (Consumer) event -> {
				try {
					m.invoke(boundInstance, event);
				} catch (ReflectiveOperationException e) {
					Throwable cause = e.getCause() != null ? e.getCause() : e;
					sneaky(cause);
				}
			};

			@SuppressWarnings("unchecked")
			Class<Event> et = (Class<Event>) eventType;
			addListener(et, ann.priority(), ann.acceptsCanceled(), phase, lambda);
		}
	}

	// -------------------------------------------------------------------------------------
	// Dispatch
	// -------------------------------------------------------------------------------------

	/**
	 * Default timeout for {@link #post(Event)} when it needs to cross a region boundary. The
	 * current-thread caller blocks for up to this long waiting for the target region to tick
	 * and flush the telegram. Can be overridden via {@link #setCrossRegionPostTimeout}.
	 */
	private volatile Duration crossRegionPostTimeout = Duration.ofSeconds(5);

	public void setCrossRegionPostTimeout(Duration timeout) {
		if (timeout == null) throw new NullPointerException("timeout");
		this.crossRegionPostTimeout = timeout;
	}

	/**
	 * Post {@code event} synchronously to every registered handler (respecting
	 * priority/phase/registration order). Returns the same event instance for convenient
	 * chaining like {@code if (!bus.post(e).isCanceled()) { … }}.
	 *
	 * <p><b>Region-affinity rules</b>:
	 * <ul>
	 *   <li>Plain {@link Event} and events implementing {@link FreeEvent}: dispatched on
	 *       the calling thread, unchanged from earlier versions.</li>
	 *   <li>{@link AffineEvent} where the caller is already on the event's region: same as
	 *       above, dispatched inline.</li>
	 *   <li>{@link AffineEvent} where the caller is NOT on the event's region: the call
	 *       submits a telegram to the owner region's thread and blocks via
	 *       {@link RegionScheduler#submitAndWait}. If the telegram doesn't complete within
	 *       the configured {@link #setCrossRegionPostTimeout timeout} a
	 *       {@link java.util.concurrent.CompletionException} is thrown.</li>
	 * </ul>
	 *
	 * <p>On non-Folia runtimes every event is effectively same-region (see
	 * {@link SingleThreadedScheduler}) so this method retains its historical synchronous
	 * semantics.
	 */
	@SuppressWarnings("unchecked")
	public <E extends Event> E post(E event) {
		if (event == null) throw new NullPointerException("event");

		if (event instanceof AffineEvent) {
			AffineEvent affine = (AffineEvent) event;
			RegionKey region = affine.region();
			RegionScheduler sched = RegionScheduler.current();

			if (!sched.isOnRegion(region)) {
				// Cross-region sync post. Package the inline dispatch as a Supplier and run
				// it on the target region's thread, waiting for the result.
				sched.submitAndWait(region, () -> { dispatch(event); return null; }, crossRegionPostTimeout);
				return event;
			}
		}

		dispatch(event);
		return event;
	}

	/**
	 * Asynchronous dispatch. Returns a future that completes once every handler has run. The
	 * caller thread is not blocked.
	 *
	 * <ul>
	 *   <li>Free/plain events and same-region affine events: the handlers run inline on the
	 *       caller thread before this method returns, and the returned future is
	 *       already-completed. Behaviour identical to {@link #post(Event)} for sync-aware
	 *       callers; useful when chaining continuations.</li>
	 *   <li>Cross-region {@code AffineEvent}: a telegram is queued on the owner region and
	 *       fired on its next tick. The returned future completes on the target region's
	 *       thread once dispatch is done.</li>
	 * </ul>
	 *
	 * <p>If any handler throws, the exception is logged via the bus's error handler and
	 * dispatch continues — the same contract as {@link #post(Event)}. The returned future
	 * completes normally unless the telegram itself couldn't be scheduled.
	 */
	public <E extends Event> CompletableFuture<E> postAsync(E event) {
		if (event == null) throw new NullPointerException("event");

		if (event instanceof AffineEvent) {
			AffineEvent affine = (AffineEvent) event;
			RegionKey region = affine.region();
			RegionScheduler sched = RegionScheduler.current();

			if (!sched.isOnRegion(region)) {
				return sched.submit(region, () -> { dispatch(event); return event; });
			}
		}

		dispatch(event);
		return CompletableFuture.completedFuture(event);
	}

	/**
	 * Fire-and-forget dispatch. The event is queued (or run inline when possible); no future
	 * is returned and no exception is propagated to the caller. Used for broadcast-style
	 * events where the caller has no interest in cancellation or completion.
	 *
	 * <p>On non-Folia runtimes this is exactly {@link #post(Event)}. On Folia or similar
	 * region-threaded runtimes, this avoids allocating a {@link CompletableFuture} per post
	 * when the caller doesn't need one.
	 */
	public <E extends Event> void postAndForget(E event) {
		if (event == null) throw new NullPointerException("event");

		if (event instanceof AffineEvent) {
			AffineEvent affine = (AffineEvent) event;
			RegionKey region = affine.region();
			RegionScheduler sched = RegionScheduler.current();

			if (!sched.isOnRegion(region)) {
				// Fire the telegram, drop the future. The scheduler is responsible for
				// logging any scheduling failure; handler exceptions are already absorbed
				// by dispatch().
				sched.submit(region, () -> { dispatch(event); return null; });
				return;
			}
		}

		dispatch(event);
	}

	/**
	 * Run every registered handler for {@code event} on the current thread. Shared core of
	 * {@link #post}, {@link #postAsync}, {@link #postAndForget}. Handler exceptions are
	 * absorbed via the bus's error handler; the method itself never throws.
	 */
	@SuppressWarnings("unchecked")
	private <E extends Event> void dispatch(E event) {
		// Walk class hierarchy: exact class then superclasses then implemented interfaces.
		// Collect handler lists in hierarchy order and sort together so priority + phase
		// behave globally across the whole type tree.
		List<Handler<?>> merged = null;

		for (Class<?> c = event.getClass(); c != null && Event.class.isAssignableFrom(c); c = c.getSuperclass()) {
			List<Handler<?>> list = handlers.get(c);

			if (list != null && !list.isEmpty()) {
				if (merged == null) merged = new ArrayList<>(list);
				else merged.addAll(list);
			}
		}

		if (merged == null || merged.isEmpty()) return;

		if (merged.size() > 1) sort(merged, event.getClass());

		for (Handler<?> h : merged) {
			if (event.isCanceled() && !h.acceptsCanceled) continue;

			try {
				((Consumer<Event>) h.action).accept(event);
			} catch (Throwable t) {
				lastException = t;
				errorHandler.accept(t);
			}
		}
	}

	// -------------------------------------------------------------------------------------
	// Sorting
	// -------------------------------------------------------------------------------------

	private static void sort(List<Handler<?>> list, Class<? extends Event> forPhaseResolve) {
		List<Identifier> phaseOrder = Phases.resolve(forPhaseResolve);
		Map<Identifier, Integer> phaseRank = new HashMap<>();

		for (int i = 0; i < phaseOrder.size(); i++) phaseRank.put(phaseOrder.get(i), i);

		list.sort(Comparator
				.comparingInt((Handler<?> h) -> -h.priority.rank())        // higher priority first
				.thenComparingInt(h -> phaseRank.getOrDefault(h.phase, Integer.MAX_VALUE))
				.thenComparingLong(h -> h.sequence));                      // stable in registration order
	}

	// -------------------------------------------------------------------------------------

	/** Internal handler record. */
	private static final class Handler<E extends Event> {
		final Class<E> eventType;
		final Priority priority;
		final boolean acceptsCanceled;
		final Identifier phase;
		final Consumer<E> action;
		final long sequence;

		Handler(Class<E> eventType, Priority priority, boolean acceptsCanceled, Identifier phase,
				Consumer<E> action, long sequence) {
			this.eventType = eventType;
			this.priority = priority;
			this.acceptsCanceled = acceptsCanceled;
			this.phase = phase;
			this.action = action;
			this.sequence = sequence;
		}
	}

	@SuppressWarnings("unchecked")
	private static <T extends Throwable> void sneaky(Throwable t) throws T {
		throw (T) t;
	}
}
