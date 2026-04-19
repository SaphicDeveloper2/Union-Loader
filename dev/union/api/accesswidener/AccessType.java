package dev.union.api.accesswidener;

/**
 * What an access widener does to its target.
 *
 * <ul>
 *   <li>{@link #ACCESSIBLE} — widen visibility to {@code public}. For classes/methods, also
 *       strips {@code final} so the member can be extended/overridden. For fields, just
 *       widens visibility.</li>
 *   <li>{@link #EXTENDABLE} — strip {@code final}. Classes become {@code public}+non-final
 *       so they can be subclassed; methods become {@code protected}+non-final so subclasses
 *       in different packages can override them. Not applicable to fields.</li>
 *   <li>{@link #MUTABLE} — strip {@code final} from a field so it can be written to at
 *       runtime. Not applicable to classes or methods.</li>
 * </ul>
 */
public enum AccessType {
	ACCESSIBLE,
	EXTENDABLE,
	MUTABLE
}
