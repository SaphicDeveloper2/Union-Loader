package dev.union.api.attachment;

/**
 * What kind of game object an attachment can hang off. Used as part of {@link AttachmentType}'s
 * key so a single mod can register the same attachment name against different host types.
 */
public enum AttachmentTarget {
	ENTITY,
	BLOCK_ENTITY,
	CHUNK,
	LEVEL,
	ITEM_STACK
}
