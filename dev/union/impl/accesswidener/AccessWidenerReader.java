package dev.union.impl.accesswidener;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Locale;

import dev.union.api.accesswidener.AccessType;

/**
 * Parses Fabric-compatible access widener text files into an {@link AccessWidener}. Supports
 * format version {@code v1} and {@code v2}.
 *
 * <h2>Format</h2>
 * <pre>
 * accessWidener &lt;version&gt; &lt;namespace&gt;
 * # comments start with '#' and run to end of line
 * &lt;access&gt; class    &lt;owner&gt;
 * &lt;access&gt; method   &lt;owner&gt; &lt;name&gt; &lt;descriptor&gt;
 * &lt;access&gt; field    &lt;owner&gt; &lt;name&gt; &lt;descriptor&gt;
 * </pre>
 *
 * <p>{@code &lt;access&gt;} is {@code accessible}, {@code extendable}, or {@code mutable}.
 * v2 adds an optional {@code transitive-} prefix — Union ignores it for now (treats transitive
 * same as non-transitive), since we don't yet support cross-mod AW propagation rules.
 *
 * <p>The {@code &lt;namespace&gt;} header field is validated against the expected namespace
 * passed to {@link #read}; mods targeting a different mapping namespace are refused loudly.
 * For 1.21.x runtime this should always be {@code named}.
 *
 * <p>Class/method/field owners are given in {@code a.b.Foo} dotted form in the file and are
 * internally normalised to {@code a/b/Foo} for ASM consistency.
 */
public final class AccessWidenerReader {
	private AccessWidenerReader() { }

	public static void read(Reader in, AccessWidener sink, String expectedNamespace, String sourceHint)
			throws IOException {
		try (BufferedReader reader = (in instanceof BufferedReader) ? (BufferedReader) in : new BufferedReader(in)) {
			String headerLine = firstNonEmpty(reader);

			if (headerLine == null) {
				throw ex(sourceHint, 1, "empty file");
			}

			String[] header = tokens(headerLine);

			if (header.length < 3 || !"accessWidener".equals(header[0])) {
				throw ex(sourceHint, 1, "missing 'accessWidener <version> <namespace>' header");
			}

			String version = header[1];

			if (!"v1".equals(version) && !"v2".equals(version)) {
				throw ex(sourceHint, 1, "unsupported access widener version: " + version);
			}

			String namespace = header[2];

			if (expectedNamespace != null && !expectedNamespace.equals(namespace)) {
				throw ex(sourceHint, 1,
						"namespace mismatch: file declares '" + namespace + "', loader expected '" + expectedNamespace + "'");
			}

			int lineNo = 1;
			String line;

			while ((line = reader.readLine()) != null) {
				lineNo++;
				String trimmed = stripComment(line).trim();
				if (trimmed.isEmpty()) continue;

				String[] tokens = tokens(trimmed);
				int i = 0;

				// Optional 'transitive-' prefix: absorb into the access word below.
				boolean transitive = false;
				String accessRaw = tokens[i++].toLowerCase(Locale.ROOT);

				if (accessRaw.startsWith("transitive-")) {
					transitive = true;
					accessRaw = accessRaw.substring("transitive-".length());
				}

				AccessType access;

				switch (accessRaw) {
				case "accessible": access = AccessType.ACCESSIBLE; break;
				case "extendable": access = AccessType.EXTENDABLE; break;
				case "mutable":    access = AccessType.MUTABLE; break;
				default:
					throw ex(sourceHint, lineNo, "unknown access '" + accessRaw + "'");
				}

				if (i >= tokens.length) throw ex(sourceHint, lineNo, "missing target kind");
				String targetKind = tokens[i++].toLowerCase(Locale.ROOT);

				switch (targetKind) {
				case "class": {
					if (i >= tokens.length) throw ex(sourceHint, lineNo, "missing class name");
					if (access == AccessType.MUTABLE) {
						throw ex(sourceHint, lineNo, "'mutable' does not apply to classes");
					}
					String owner = tokens[i++].replace('.', '/');
					sink.addClass(access, owner);
					break;
				}
				case "method": {
					if (i + 2 >= tokens.length) throw ex(sourceHint, lineNo, "method requires: <owner> <name> <descriptor>");
					if (access == AccessType.MUTABLE) {
						throw ex(sourceHint, lineNo, "'mutable' does not apply to methods");
					}
					String owner = tokens[i++].replace('.', '/');
					String name = tokens[i++];
					String desc = tokens[i++];
					sink.addMethod(access, owner, name, desc);
					break;
				}
				case "field": {
					if (i + 2 >= tokens.length) throw ex(sourceHint, lineNo, "field requires: <owner> <name> <descriptor>");
					if (access == AccessType.EXTENDABLE) {
						throw ex(sourceHint, lineNo, "'extendable' does not apply to fields");
					}
					String owner = tokens[i++].replace('.', '/');
					String name = tokens[i++];
					String desc = tokens[i++];
					sink.addField(access, owner, name, desc);
					break;
				}
				default:
					throw ex(sourceHint, lineNo, "unknown target kind '" + targetKind + "'");
				}

				// 'transitive' is accepted but currently applied identically to non-transitive.
				// Suppress unused-flag warnings without adding behaviour.
				if (transitive) { /* no-op for v0 */ }
			}
		}
	}

	private static String firstNonEmpty(BufferedReader reader) throws IOException {
		String line;
		while ((line = reader.readLine()) != null) {
			String trimmed = stripComment(line).trim();
			if (!trimmed.isEmpty()) return trimmed;
		}
		return null;
	}

	private static String stripComment(String line) {
		int hash = line.indexOf('#');
		return hash >= 0 ? line.substring(0, hash) : line;
	}

	private static String[] tokens(String line) {
		return line.trim().split("\\s+");
	}

	private static IOException ex(String source, int line, String msg) {
		return new IOException("AccessWidener parse error in " + source + " line " + line + ": " + msg);
	}
}
