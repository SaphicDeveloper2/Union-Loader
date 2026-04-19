package dev.union.mixin.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.mixin.MixinEnvironment.CompatibilityLevel;
import org.spongepowered.asm.service.IClassBytecodeProvider;
import org.spongepowered.asm.service.IClassProvider;
import org.spongepowered.asm.service.IClassTracker;
import org.spongepowered.asm.service.IMixinAuditTrail;
import org.spongepowered.asm.service.ITransformer;
import org.spongepowered.asm.service.ITransformerProvider;
import org.spongepowered.asm.service.MixinServiceAbstract;

import dev.union.api.UnionLoader;
import dev.union.impl.launch.UnionClassLoader;

/**
 * Main Mixin service for Union. Wires the Mixin engine against the Union classloader.
 *
 * <p>Implements:
 * <ul>
 *   <li>{@link IClassProvider} — {@code Class<?>} lookup via our classloader.</li>
 *   <li>{@link IClassBytecodeProvider} — resource-based class byte access + ClassNode parsing.</li>
 *   <li>{@link ITransformerProvider} — Union has no other transformers beyond Mixin itself.</li>
 * </ul>
 *
 * <p>Everything else inherits sane defaults from {@link MixinServiceAbstract}.
 */
public final class UnionMixinService extends MixinServiceAbstract
		implements IClassProvider, IClassBytecodeProvider, ITransformerProvider {
	private final UnionClassTracker classTracker;
	private final UnionAuditTrail auditTrail = new UnionAuditTrail();

	public UnionMixinService() {
		ClassLoader ccl = Thread.currentThread().getContextClassLoader();

		if (!(ccl instanceof UnionClassLoader)) {
			throw new IllegalStateException(
					"UnionMixinService instantiated under non-Union classloader: "
							+ (ccl == null ? "null" : ccl.getClass().getName()));
		}

		this.classTracker = new UnionClassTracker((UnionClassLoader) ccl);
	}

	@Override
	public String getName() {
		return UnionMixinServiceBootstrap.NAME;
	}

	@Override
	public boolean isValid() {
		return Thread.currentThread().getContextClassLoader() instanceof UnionClassLoader;
	}

	@Override
	public CompatibilityLevel getMinCompatibilityLevel() {
		return CompatibilityLevel.JAVA_17;
	}

	@Override
	public CompatibilityLevel getMaxCompatibilityLevel() {
		return CompatibilityLevel.JAVA_21;
	}

	@Override
	public IClassProvider getClassProvider() {
		return this;
	}

	@Override
	public IClassBytecodeProvider getBytecodeProvider() {
		return this;
	}

	@Override
	public ITransformerProvider getTransformerProvider() {
		return this;
	}

	@Override
	public IClassTracker getClassTracker() {
		return classTracker;
	}

	@Override
	public IMixinAuditTrail getAuditTrail() {
		return auditTrail;
	}

	@Override
	public Collection<String> getPlatformAgents() {
		return Collections.singletonList("dev.union.mixin.service.UnionPlatformAgent");
	}

	@Override
	public IContainerHandle getPrimaryContainer() {
		return new UnionContainerHandle("union-loader");
	}

	@Override
	public InputStream getResourceAsStream(String name) {
		return getUnionClassLoader().getResourceAsStream(name);
	}

	@Override
	public synchronized ILogger getLogger(String name) {
		return new UnionLoggerAdapter(name);
	}

	// ------------------------------------------------------------------------------------
	// IClassProvider
	// ------------------------------------------------------------------------------------

	@Override
	public URL[] getClassPath() {
		List<URL> urls = getUnionClassLoader().getSearchPath();
		return urls.toArray(new URL[0]);
	}

	@Override
	public Class<?> findClass(String name) throws ClassNotFoundException {
		return Class.forName(name, false, getUnionClassLoader());
	}

	@Override
	public Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException {
		return Class.forName(name, initialize, getUnionClassLoader());
	}

	@Override
	public Class<?> findAgentClass(String name, boolean initialize) throws ClassNotFoundException {
		// Agent classes resolved from the platform loader (parent of our classloader). This
		// matches what the LaunchWrapper service does for classes that must never be
		// transformed.
		return Class.forName(name, initialize, getUnionClassLoader().getParent());
	}

	// ------------------------------------------------------------------------------------
	// IClassBytecodeProvider
	// ------------------------------------------------------------------------------------

	@Override
	public ClassNode getClassNode(String name) throws ClassNotFoundException, IOException {
		return getClassNode(name, true);
	}

	@Override
	public ClassNode getClassNode(String name, boolean runTransformers) throws ClassNotFoundException, IOException {
		return getClassNode(name, runTransformers, 0);
	}

	@Override
	public ClassNode getClassNode(String name, boolean runTransformers, int readerFlags)
			throws ClassNotFoundException, IOException {
		byte[] bytes = getUnionClassLoader().getClassBytes(name, runTransformers);

		if (bytes == null) {
			throw new ClassNotFoundException(name);
		}

		ClassNode node = new ClassNode();
		new ClassReader(bytes).accept(node, readerFlags);
		return node;
	}

	// ------------------------------------------------------------------------------------
	// ITransformerProvider
	// ------------------------------------------------------------------------------------

	@Override
	public Collection<ITransformer> getTransformers() {
		return Collections.emptyList();
	}

	@Override
	public Collection<ITransformer> getDelegatedTransformers() {
		return Collections.emptyList();
	}

	@Override
	public void addTransformerExclusion(String name) {
		getUnionClassLoader().addTransformerExclusion(name);
	}

	// ------------------------------------------------------------------------------------

	private UnionClassLoader getUnionClassLoader() {
		ClassLoader ccl = Thread.currentThread().getContextClassLoader();

		if (ccl instanceof UnionClassLoader) return (UnionClassLoader) ccl;

		throw new IllegalStateException("UnionMixinService called outside the Union classloader");
	}

	@Override
	public Collection<IContainerHandle> getMixinContainers() {
		List<IContainerHandle> out = new ArrayList<>();
		for (dev.union.api.ModContainer mod : UnionLoader.get().getAllMods()) {
			out.add(new UnionContainerHandle(mod.getMetadata().getId()));
		}
		return out;
	}
}
