package dev.union.mixin.service;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.spongepowered.asm.service.IClassTracker;

import dev.union.impl.launch.UnionClassLoader;

/**
 * {@link IClassTracker} implementation backed by the Union classloader. Mixin uses this to
 * enforce "you cannot mixin into a class that has already been loaded" and to record classes
 * that the transformer refused to process.
 */
public final class UnionClassTracker implements IClassTracker {
	private final UnionClassLoader classLoader;
	private final Set<String> invalidClasses = Collections.newSetFromMap(new ConcurrentHashMap<>());

	public UnionClassTracker(UnionClassLoader classLoader) {
		this.classLoader = classLoader;
	}

	@Override
	public void registerInvalidClass(String className) {
		invalidClasses.add(className);
	}

	@Override
	public boolean isClassLoaded(String className) {
		return classLoader.isClassLoaded(className);
	}

	@Override
	public String getClassRestrictions(String className) {
		// Union has no module-layer or package restrictions beyond normal classloader rules.
		return "";
	}
}
