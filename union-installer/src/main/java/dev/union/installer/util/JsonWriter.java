package dev.union.installer.util;

/**
 * Tiny hand-rolled JSON serialiser. Only supports the shapes we need to write profile /
 * launcher_profiles JSON: objects, arrays, strings, numbers, booleans, null, and nested
 * instances of itself.
 *
 * <p>Usage:
 * <pre>
 *     String s = JsonWriter.toJson(Map.of("a", 1, "b", List.of("x", "y")));
 * </pre>
 */
public final class JsonWriter {
	private JsonWriter() { }

	public static String toJson(Object value) {
		StringBuilder sb = new StringBuilder();
		write(sb, value, 0, true);
		return sb.toString();
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void write(StringBuilder sb, Object value, int indent, boolean pretty) {
		if (value == null) {
			sb.append("null");
		} else if (value instanceof String) {
			writeString(sb, (String) value);
		} else if (value instanceof Boolean || value instanceof Number) {
			sb.append(value);
		} else if (value instanceof java.util.Map) {
			writeObject(sb, (java.util.Map) value, indent, pretty);
		} else if (value instanceof Iterable) {
			writeArray(sb, (Iterable) value, indent, pretty);
		} else if (value instanceof Object[]) {
			writeArray(sb, java.util.Arrays.asList((Object[]) value), indent, pretty);
		} else {
			writeString(sb, value.toString());
		}
	}

	@SuppressWarnings("rawtypes")
	private static void writeObject(StringBuilder sb, java.util.Map m, int indent, boolean pretty) {
		if (m.isEmpty()) { sb.append("{}"); return; }

		sb.append('{');
		int i = 0;

		for (Object e : m.entrySet()) {
			java.util.Map.Entry entry = (java.util.Map.Entry) e;
			if (i++ > 0) sb.append(',');
			if (pretty) { sb.append('\n'); indent(sb, indent + 1); }
			writeString(sb, String.valueOf(entry.getKey()));
			sb.append(pretty ? ": " : ":");
			write(sb, entry.getValue(), indent + 1, pretty);
		}

		if (pretty) { sb.append('\n'); indent(sb, indent); }
		sb.append('}');
	}

	private static void writeArray(StringBuilder sb, Iterable<?> iter, int indent, boolean pretty) {
		java.util.Iterator<?> it = iter.iterator();
		if (!it.hasNext()) { sb.append("[]"); return; }

		sb.append('[');
		boolean first = true;

		while (it.hasNext()) {
			if (!first) sb.append(',');
			first = false;
			if (pretty) { sb.append('\n'); indent(sb, indent + 1); }
			write(sb, it.next(), indent + 1, pretty);
		}

		if (pretty) { sb.append('\n'); indent(sb, indent); }
		sb.append(']');
	}

	private static void writeString(StringBuilder sb, String s) {
		sb.append('"');

		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);

			switch (c) {
			case '"':  sb.append("\\\""); break;
			case '\\': sb.append("\\\\"); break;
			case '\b': sb.append("\\b"); break;
			case '\f': sb.append("\\f"); break;
			case '\n': sb.append("\\n"); break;
			case '\r': sb.append("\\r"); break;
			case '\t': sb.append("\\t"); break;
			default:
				if (c < 0x20) {
					sb.append(String.format("\\u%04x", (int) c));
				} else {
					sb.append(c);
				}
			}
		}

		sb.append('"');
	}

	private static void indent(StringBuilder sb, int level) {
		for (int i = 0; i < level; i++) sb.append("  ");
	}
}
