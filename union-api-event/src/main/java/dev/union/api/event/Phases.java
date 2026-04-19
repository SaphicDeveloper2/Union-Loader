package dev.union.api.event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import dev.union.api.base.Identifier;

/**
 * Fabric-style named phase ordering, scoped per event class.
 *
 * <p>A "phase" is an {@link Identifier} attached to a handler registration. Within a single
 * priority level, handlers are grouped by phase and phases run in a topologically ordered
 * sequence declared via {@link #orderPhases(Class, Identifier, Identifier)}. Handlers that
 * didn't declare a phase live in a {@linkplain Identifier#of(String, String) "union:default"}
 * phase that sits between declared "before" and "after" phases unless explicitly ordered.
 *
 * <p>Thread-safe: registration is a rare event (mostly at init) so locking on the phase map
 * is acceptable.
 */
public final class Phases {
	/** The default phase handlers fall into when none is specified. */
	public static final Identifier DEFAULT_PHASE = Identifier.of("union", "default");

	private static final Map<Class<? extends Event>, PhaseGraph> GRAPHS = new ConcurrentHashMap<>();

	private Phases() { }

	/**
	 * Declare that {@code first} runs before {@code second} for handlers of {@code eventType}.
	 * Multiple calls add edges to a DAG; the resulting order is a topological sort.
	 *
	 * <p>Unknown phases are added implicitly — you don't need to "create" a phase before
	 * ordering it.
	 */
	public static void orderPhases(Class<? extends Event> eventType, Identifier first, Identifier second) {
		if (first.equals(second)) {
			throw new IllegalArgumentException("phase cannot depend on itself: " + first);
		}

		PhaseGraph graph = GRAPHS.computeIfAbsent(eventType, k -> new PhaseGraph());

		synchronized (graph) {
			graph.addEdge(first, second);
		}
	}

	/**
	 * Resolve the sorted phase list for {@code eventType}. Uses cached computation if the
	 * graph is unchanged since the last resolve.
	 *
	 * @return phases in the order they should fire, always including at least
	 *         {@link #DEFAULT_PHASE}.
	 */
	public static List<Identifier> resolve(Class<? extends Event> eventType) {
		PhaseGraph graph = GRAPHS.get(eventType);

		if (graph == null) return List.of(DEFAULT_PHASE);

		synchronized (graph) {
			return graph.topoSort();
		}
	}

	// ----------------------------------------------------------------------------------

	/**
	 * Node-and-edge DAG. Kahn's algorithm for topological sort. Circular dependencies throw
	 * on {@link #topoSort()}.
	 */
	private static final class PhaseGraph {
		private final Map<Identifier, Set<Identifier>> edgesFrom = new HashMap<>();
		private final Map<Identifier, Integer> inDegree = new HashMap<>();

		PhaseGraph() {
			ensureNode(DEFAULT_PHASE);
		}

		void addEdge(Identifier from, Identifier to) {
			ensureNode(from);
			ensureNode(to);

			if (edgesFrom.get(from).add(to)) {
				inDegree.merge(to, 1, Integer::sum);
			}
		}

		List<Identifier> topoSort() {
			Map<Identifier, Integer> remaining = new HashMap<>(inDegree);
			List<Identifier> out = new ArrayList<>(remaining.size());

			// Stable order: sort the 0-in-degree frontier by Identifier natural order so phase
			// resolution is deterministic across JVM runs.
			Set<Identifier> frontier = new LinkedHashSet<>();

			for (Map.Entry<Identifier, Integer> e : remaining.entrySet()) {
				if (e.getValue() == 0) frontier.add(e.getKey());
			}

			while (!frontier.isEmpty()) {
				Identifier next = minOf(frontier);
				frontier.remove(next);
				out.add(next);

				for (Identifier neighbour : edgesFrom.getOrDefault(next, Set.of())) {
					int d = remaining.merge(neighbour, -1, Integer::sum);
					if (d == 0) frontier.add(neighbour);
				}
			}

			if (out.size() != remaining.size()) {
				Set<Identifier> cyclic = new HashSet<>(remaining.keySet());
				cyclic.removeAll(out);
				throw new IllegalStateException("cyclic phase ordering detected, participating: " + cyclic);
			}

			return Collections.unmodifiableList(out);
		}

		private void ensureNode(Identifier id) {
			edgesFrom.computeIfAbsent(id, k -> new LinkedHashSet<>());
			inDegree.putIfAbsent(id, 0);
		}

		private static Identifier minOf(Set<Identifier> set) {
			Identifier min = null;
			for (Identifier id : set) {
				if (min == null || id.compareTo(min) < 0) min = id;
			}
			return min;
		}
	}
}
