package dev.union.impl;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import dev.union.api.ContactInformation;

/**
 * Map-backed implementation of {@link ContactInformation}. Structured keys
 * (homepage/sources/issues/email) are surfaced as {@link Optional}s; unknown keys stay
 * accessible via {@link #get(String)} and {@link #asMap()}.
 */
public final class ContactInformationImpl implements ContactInformation {
	private final Map<String, String> data;

	public ContactInformationImpl(Map<String, String> data) {
		this.data = Collections.unmodifiableMap(new LinkedHashMap<>(data));
	}

	@Override public Optional<String> homepage() { return Optional.ofNullable(data.get("homepage")); }
	@Override public Optional<String> sources()  { return Optional.ofNullable(data.get("sources")); }
	@Override public Optional<String> issues()   { return Optional.ofNullable(data.get("issues")); }
	@Override public Optional<String> email()    { return Optional.ofNullable(data.get("email")); }

	@Override
	public Optional<String> get(String key) {
		return Optional.ofNullable(data.get(key));
	}

	@Override
	public Map<String, String> asMap() {
		return data;
	}
}
