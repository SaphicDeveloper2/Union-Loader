package dev.union.api.event;

/**
 * Ready-to-extend base for events that carry a handler-overridable {@link Event.Result} and
 * also support cancellation.
 */
public abstract class ResultEvent extends CancelableEvent implements Event.HasResult {
	private Result result = Result.DEFAULT;

	@Override
	public Result getResult() {
		return result;
	}

	@Override
	public void setResult(Result result) {
		if (result == null) throw new IllegalArgumentException("result must not be null");
		this.result = result;
	}
}
