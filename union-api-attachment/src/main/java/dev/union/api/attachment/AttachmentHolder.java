package dev.union.api.attachment;

/**
 * Access + mutation surface for attachments on a single host. Implemented by the integration
 * module on every supported {@link AttachmentTarget} (entity, block entity, chunk, level, item
 * stack). Mods obtain one by calling the integration-supplied accessor — typically an
 * interface injection like {@code ((AttachmentHolder) player).getAttachment(MY_TYPE)}.
 *
 * <p>Calling {@link #get(AttachmentType)} on a host that has no value yet returns the type's
 * default-supplier result (lazily) rather than {@code null}. Use {@link #has(AttachmentType)}
 * to check presence without materialising a default.
 */
public interface AttachmentHolder {
	<T> T get(AttachmentType<T> type);

	<T> void set(AttachmentType<T> type, T value);

	<T> boolean has(AttachmentType<T> type);

	<T> T remove(AttachmentType<T> type);
}
