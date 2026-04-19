package dev.union.api.event;

/**
 * Handler priority ordering. Higher-priority handlers run first. Ties broken by phase order
 * (see {@link Phases}), then registration order.
 */
public enum Priority {
	HIGHEST(4),
	HIGH(3),
	NORMAL(2),
	LOW(1),
	LOWEST(0);

	private final int rank;

	Priority(int rank) { this.rank = rank; }

	public int rank() { return rank; }
}
