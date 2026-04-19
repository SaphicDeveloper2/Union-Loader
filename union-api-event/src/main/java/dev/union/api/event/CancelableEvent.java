package dev.union.api.event;

/**
 * Ready-to-extend base for events that need cancellation semantics. Equivalent to NeoForge's
 * pattern of marking an event class with {@code @Cancelable}.
 */
public abstract class CancelableEvent extends Event implements Event.Cancelable {
	private boolean canceled;

	@Override
	public boolean isCanceled() {
		return canceled;
	}

	@Override
	public void setCanceled(boolean canceled) {
		this.canceled = canceled;
	}
}
