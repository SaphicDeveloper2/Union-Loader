package dev.union.impl.accesswidener;

import java.util.Set;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import dev.union.api.accesswidener.AccessType;

/**
 * Applies {@link AccessWidener} rules to a class's bytecode.
 *
 * <p>Runs as a self-contained ASM pass: {@link ClassReader} → visitor → {@link ClassWriter}.
 * Zero external dependencies beyond ASM, which is already on the runtime classpath.
 *
 * <p>Semantics (mirrors Fabric's access widener v2 precisely):
 * <ul>
 *   <li>{@code accessible class} → bump visibility to public, clear {@code ACC_FINAL}.</li>
 *   <li>{@code extendable class} → bump visibility to public, clear {@code ACC_FINAL}.</li>
 *   <li>{@code accessible method} → bump visibility to public, clear {@code ACC_FINAL} (unless
 *       {@code ACC_STATIC}; static finals can stay).</li>
 *   <li>{@code extendable method} → widen to protected (if more restrictive), clear
 *       {@code ACC_FINAL}.</li>
 *   <li>{@code accessible field} → bump visibility to public.</li>
 *   <li>{@code mutable field} → clear {@code ACC_FINAL}.</li>
 * </ul>
 *
 * <p>Inner-class attributes on the class being processed are also rewritten so the
 * {@code InnerClasses} table reflects the new access flags — otherwise older tooling may
 * still see the original access and re-tighten it.
 */
public final class AccessWidenerTransformer {
	private static final int API = Opcodes.ASM9;

	private static final int VISIBILITY_MASK = Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE;

	private AccessWidenerTransformer() { }

	/**
	 * Transform {@code bytes} against {@code widener}. If {@code className} has no rules
	 * registered, returns {@code bytes} unchanged (no ASM round-trip cost).
	 *
	 * @param className fully-qualified class name with dots (e.g. {@code java.lang.String}).
	 * @param bytes     raw class file bytes.
	 * @param widener   aggregated rule registry.
	 * @return transformed bytes, or {@code bytes} if no rules apply.
	 */
	public static byte[] transform(String className, byte[] bytes, AccessWidener widener) {
		String internal = className.replace('.', '/');

		if (!widener.getTargets().contains(internal)) {
			return bytes;
		}

		ClassReader reader = new ClassReader(bytes);
		ClassWriter writer = new ClassWriter(0);
		reader.accept(new Visitor(writer, widener, internal), 0);
		return writer.toByteArray();
	}

	// --- widening helpers ----------------------------------------------------------------

	private static int widenClassAccess(int access, Set<AccessType> types) {
		for (AccessType t : types) {
			switch (t) {
			case ACCESSIBLE:
			case EXTENDABLE:
				access = setVisibility(access, Opcodes.ACC_PUBLIC);
				access &= ~Opcodes.ACC_FINAL;
				break;
			default:
				// MUTABLE rejected for classes at parse time.
			}
		}
		return access;
	}

	private static int widenMethodAccess(int access, Set<AccessType> types) {
		for (AccessType t : types) {
			switch (t) {
			case ACCESSIBLE:
				access = setVisibility(access, Opcodes.ACC_PUBLIC);
				if ((access & Opcodes.ACC_STATIC) == 0) access &= ~Opcodes.ACC_FINAL;
				break;
			case EXTENDABLE:
				access = widenToAtLeast(access, Opcodes.ACC_PROTECTED);
				access &= ~Opcodes.ACC_FINAL;
				break;
			default:
				// MUTABLE rejected for methods at parse time.
			}
		}
		return access;
	}

	private static int widenFieldAccess(int access, Set<AccessType> types) {
		for (AccessType t : types) {
			switch (t) {
			case ACCESSIBLE:
				access = setVisibility(access, Opcodes.ACC_PUBLIC);
				break;
			case MUTABLE:
				access &= ~Opcodes.ACC_FINAL;
				break;
			default:
				// EXTENDABLE rejected for fields at parse time.
			}
		}
		return access;
	}

	private static int setVisibility(int access, int newVisibility) {
		return (access & ~VISIBILITY_MASK) | newVisibility;
	}

	/**
	 * Relax visibility only if {@code target} is weaker than the current visibility. Ordering
	 * from most-restrictive to most-open is: {@code private < package < protected < public}.
	 */
	private static int widenToAtLeast(int access, int target) {
		int current = access & VISIBILITY_MASK;

		// Current open enough?
		if (current == Opcodes.ACC_PUBLIC) return access;
		if (target == Opcodes.ACC_PROTECTED && current == Opcodes.ACC_PROTECTED) return access;

		return setVisibility(access, target);
	}

	// --- ASM visitor ---------------------------------------------------------------------

	private static final class Visitor extends ClassVisitor {
		private final AccessWidener widener;
		private final String className;

		Visitor(ClassVisitor next, AccessWidener widener, String className) {
			super(API, next);
			this.widener = widener;
			this.className = className;
		}

		@Override
		public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
			Set<AccessType> types = widener.getClassEntry(className);
			if (!types.isEmpty()) access = widenClassAccess(access, types);
			super.visit(version, access, name, signature, superName, interfaces);
		}

		@Override
		public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
			Set<AccessType> types = widener.getFieldEntry(className, name, descriptor);
			if (!types.isEmpty()) access = widenFieldAccess(access, types);
			return super.visitField(access, name, descriptor, signature, value);
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
			Set<AccessType> types = widener.getMethodEntry(className, name, descriptor);
			if (!types.isEmpty()) access = widenMethodAccess(access, types);
			return super.visitMethod(access, name, descriptor, signature, exceptions);
		}

		@Override
		public void visitInnerClass(String name, String outerName, String innerName, int access) {
			// If the inner class itself is a widening target, rewrite its access in the
			// InnerClasses attribute of every class that references it.
			Set<AccessType> types = widener.getClassEntry(name);
			if (!types.isEmpty()) access = widenClassAccess(access, types);
			super.visitInnerClass(name, outerName, innerName, access);
		}
	}
}
