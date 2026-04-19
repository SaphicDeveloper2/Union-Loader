package dev.union.api;

import java.util.Map;
import java.util.Optional;

final class EmptyContactInformation implements ContactInformation {
	static final EmptyContactInformation INSTANCE = new EmptyContactInformation();

	private EmptyContactInformation() { }

	@Override public Optional<String> homepage() { return Optional.empty(); }
	@Override public Optional<String> sources()  { return Optional.empty(); }
	@Override public Optional<String> issues()   { return Optional.empty(); }
	@Override public Optional<String> email()    { return Optional.empty(); }
	@Override public Optional<String> get(String key) { return Optional.empty(); }
	@Override public Map<String, String> asMap() { return Map.of(); }
}
