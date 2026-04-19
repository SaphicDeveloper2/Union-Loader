package dev.union.api.event;

/**
 * Base type for everything posted on the {@link EventBus}. Two opt-in behaviours:
 *
 * <ul>
 *   <li>{@link Cancelable}     — later handlers observe {@link #isCanceled()}; a canceled event
 *       may still invoke handlers that declared {@code acceptsCanceled=true}.</li>
 *   <li>{@link HasResult}      — handlers can set a {@link Result} of ALLOW/DENY/DEFAULT.
 *       The poster inspects {@link #getResult()} after dispatch.</li>
 * </ul>
 *
 * <p>Concrete subclasses that want one or both semantics should extend the abstract
 * {@code CancelableEvent} / {@code ResultEvent} helpers in this package, or mix in by hand
 * via the marker interfaces.
 */
public abstract class Event {
	/**
	 * @return {@code true} if {@link #setCanceled(boolean)} has been called on this event.
	 *         Non-{@link Cancelable} events always return {@code false}.
	 */
	public boolean isCanceled() {
		return false;
	}

	/**
	 * @throws UnsupportedOperationException if this event is not {@link Cancelable}.
	 */
	public void setCanceled(boolean canceled) {
		throw new UnsupportedOperationException(getClass().getSimpleName() + " is not Cancelable");
	}

	/**
	 * @return the current result. {@link Result#DEFAULT} for events that don't implement
	 *         {@link HasResult}.
	 */
	public Result getResult() {
		return Result.DEFAULT;
	}

	/**
	 * @throws UnsupportedOperationException if this event does not implement {@link HasResult}.
	 */
	public void setResult(Result result) {
		throw new UnsupportedOperationException(getClass().getSimpleName() + " does not have a Result");
	}

	/** Marker: this event can be canceled via {@link #setCanceled(boolean)}. */
	public interface Cancelable { }

	/** Marker: this event carries a {@link Result} that handlers may override. */
	public interface HasResult { }

	public enum Result {
		DEFAULT,
		ALLOW,
		DENY
	}
}
