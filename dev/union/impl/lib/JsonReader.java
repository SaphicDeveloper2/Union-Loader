package dev.union.impl.lib;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal hand-rolled JSON reader. Supports objects, arrays, strings, numbers, booleans, and null.
 * <p>Parses into standard Java types: {@code Map<String,Object>}, {@code List<Object>},
 * {@link String}, {@link Long} / {@link Double}, {@link Boolean}, {@code null}.
 * <p>Intentionally zero-dependency so the loader can read metadata before any library JAR is on
 * the classpath.
 */
public final class JsonReader {
	private final Reader in;
	private int peek = -2;
	private int line = 1;
	private int column = 0;

	private JsonReader(Reader in) {
		this.in = in;
	}

	public static Object parse(Reader in) throws IOException {
		JsonReader r = new JsonReader(in);
		r.skipWs();
		Object result = r.readValue();
		r.skipWs();

		if (r.peek() != -1) {
			throw r.err("trailing content after root value");
		}

		return result;
	}

	@SuppressWarnings("unchecked")
	public static Map<String, Object> parseObject(Reader in) throws IOException {
		Object v = parse(in);

		if (!(v instanceof Map)) {
			throw new IOException("expected top-level JSON object, got " + (v == null ? "null" : v.getClass().getSimpleName()));
		}

		return (Map<String, Object>) v;
	}

	private Object readValue() throws IOException {
		int c = peek();

		switch (c) {
		case '{': return readObject();
		case '[': return readArray();
		case '"': return readString();
		case 't': case 'f': return readBool();
		case 'n': return readNull();
		case -1: throw err("unexpected end of input");
		default:
			if (c == '-' || (c >= '0' && c <= '9')) return readNumber();
			throw err("unexpected character '" + (char) c + "'");
		}
	}

	private Map<String, Object> readObject() throws IOException {
		expect('{');
		Map<String, Object> obj = new LinkedHashMap<>();
		skipWs();

		if (peek() == '}') { read(); return obj; }

		while (true) {
			skipWs();
			String key = readString();
			skipWs();
			expect(':');
			skipWs();
			Object value = readValue();
			obj.put(key, value);
			skipWs();
			int c = read();
			if (c == ',') continue;
			if (c == '}') return obj;
			throw err("expected ',' or '}' in object, got '" + (char) c + "'");
		}
	}

	private List<Object> readArray() throws IOException {
		expect('[');
		List<Object> arr = new ArrayList<>();
		skipWs();

		if (peek() == ']') { read(); return arr; }

		while (true) {
			skipWs();
			arr.add(readValue());
			skipWs();
			int c = read();
			if (c == ',') continue;
			if (c == ']') return arr;
			throw err("expected ',' or ']' in array, got '" + (char) c + "'");
		}
	}

	private String readString() throws IOException {
		expect('"');
		StringBuilder sb = new StringBuilder();

		while (true) {
			int c = read();

			if (c == -1) throw err("unterminated string");
			if (c == '"') return sb.toString();

			if (c == '\\') {
				int esc = read();

				switch (esc) {
				case '"':  sb.append('"');  break;
				case '\\': sb.append('\\'); break;
				case '/':  sb.append('/');  break;
				case 'b':  sb.append('\b'); break;
				case 'f':  sb.append('\f'); break;
				case 'n':  sb.append('\n'); break;
				case 'r':  sb.append('\r'); break;
				case 't':  sb.append('\t'); break;
				case 'u': {
					int cp = 0;

					for (int i = 0; i < 4; i++) {
						int h = read();

						if (h == -1) throw err("unterminated \\u escape");
						cp <<= 4;

						if      (h >= '0' && h <= '9') cp |= h - '0';
						else if (h >= 'a' && h <= 'f') cp |= h - 'a' + 10;
						else if (h >= 'A' && h <= 'F') cp |= h - 'A' + 10;
						else throw err("invalid hex in \\u escape");
					}

					sb.append((char) cp);
					break;
				}
				default: throw err("invalid escape \\" + (char) esc);
				}
			} else {
				sb.append((char) c);
			}
		}
	}

	private Object readNumber() throws IOException {
		StringBuilder sb = new StringBuilder();
		boolean floating = false;

		while (true) {
			int c = peek();

			if (c == -1) break;

			if (c == '-' || c == '+' || (c >= '0' && c <= '9')) {
				sb.append((char) c); read();
			} else if (c == '.' || c == 'e' || c == 'E') {
				floating = true;
				sb.append((char) c); read();
			} else {
				break;
			}
		}

		String s = sb.toString();

		try {
			return floating ? (Object) Double.valueOf(s) : (Object) Long.valueOf(s);
		} catch (NumberFormatException nfe) {
			throw err("invalid number literal: " + s);
		}
	}

	private Boolean readBool() throws IOException {
		if (peek() == 't') {
			expectLiteral("true");
			return Boolean.TRUE;
		}

		expectLiteral("false");
		return Boolean.FALSE;
	}

	private Object readNull() throws IOException {
		expectLiteral("null");
		return null;
	}

	private void expectLiteral(String lit) throws IOException {
		for (int i = 0; i < lit.length(); i++) {
			int c = read();

			if (c != lit.charAt(i)) throw err("expected literal '" + lit + "'");
		}
	}

	private void expect(int expected) throws IOException {
		int c = read();

		if (c != expected) throw err("expected '" + (char) expected + "', got " + (c == -1 ? "<eof>" : "'" + (char) c + "'"));
	}

	private void skipWs() throws IOException {
		while (true) {
			int c = peek();

			if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
				read();
			} else {
				return;
			}
		}
	}

	private int peek() throws IOException {
		if (peek == -2) peek = in.read();
		return peek;
	}

	private int read() throws IOException {
		int c;

		if (peek == -2) {
			c = in.read();
		} else {
			c = peek;
			peek = -2;
		}

		if (c == '\n') { line++; column = 0; }
		else column++;

		return c;
	}

	private IOException err(String msg) {
		return new IOException("JSON parse error at line " + line + ", column " + column + ": " + msg);
	}
}
