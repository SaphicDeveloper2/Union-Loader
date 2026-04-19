package dev.union.mixin.service;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.spongepowered.asm.launch.platform.container.IContainerHandle;

/**
 * Simple {@link IContainerHandle} backed by a map of attributes. Used to represent:
 * <ul>
 *   <li>The primary container — the Union loader JAR itself.</li>
 *   <li>Each mod JAR as a mixin container.</li>
 * </ul>
 */
public final class UnionContainerHandle implements IContainerHandle {
	private final String name;
	private final Map<String, String> attributes;
	private final Collection<IContainerHandle> nested;

	public UnionContainerHandle(String name) {
		this(name, Collections.emptyMap(), Collections.emptyList());
	}

	public UnionContainerHandle(String name, Map<String, String> attributes) {
		this(name, attributes, Collections.emptyList());
	}

	public UnionContainerHandle(String name, Map<String, String> attributes, Collection<IContainerHandle> nested) {
		this.name = name;
		this.attributes = new LinkedHashMap<>(attributes);
		this.nested = nested;
	}

	public String getName() {
		return name;
	}

	@Override
	public String getId() {
		return name;
	}

	@Override
	public String getDescription() {
		return name;
	}

	@Override
	public String getAttribute(String attributeName) {
		return attributes.get(attributeName);
	}

	@Override
	public Collection<IContainerHandle> getNestedContainers() {
		return nested;
	}

	@Override
	public String toString() {
		return "UnionContainer(" + name + ")";
	}
}
