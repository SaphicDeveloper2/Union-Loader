package dev.union.impl;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.union.api.ModMetadata;
import dev.union.impl.lib.JsonReader;

public final class ModMetadataImpl implements ModMetadata {
	private final String id;
	private final String version;
	private final String name;
	private final String description;
	private final List<String> authors;
	private final String environment;
	private final Map<String, List<String>> entrypoints;
	private final Map<String, String> dependencies;
	private final List<String> mixinConfigs;
	private final String accessWidener;
	private final List<String> nestedJars;
	private final List<String> providedMods;
	private final List<String> license;
	private final dev.union.api.ContactInformation contact;
	private final String iconPath;
	private final Map<String, Object> custom;

	private ModMetadataImpl(Builder b) {
		this.id = b.id;
		this.version = b.version;
		this.name = b.name;
		this.description = b.description;
		this.authors = b.authors;
		this.environment = b.environment;
		this.entrypoints = b.entrypoints;
		this.dependencies = b.dependencies;
		this.mixinConfigs = b.mixinConfigs;
		this.accessWidener = b.accessWidener;
		this.nestedJars = b.nestedJars;
		this.providedMods = b.providedMods;
		this.license = b.license;
		this.contact = b.contact;
		this.iconPath = b.iconPath;
		this.custom = b.custom;
	}

	/** Package-private mutable builder; {@link #parse(Reader)} is the public entry. */
	private static final class Builder {
		String id, version, name = "", description = "", environment = "*", accessWidener, iconPath;
		List<String> authors = List.of();
		Map<String, List<String>> entrypoints = Map.of();
		Map<String, String> dependencies = Map.of();
		List<String> mixinConfigs = List.of();
		List<String> nestedJars = List.of();
		List<String> providedMods = List.of();
		List<String> license = List.of();
		dev.union.api.ContactInformation contact = dev.union.api.ContactInformation.empty();
		Map<String, Object> custom = Map.of();
	}

	@SuppressWarnings("unchecked")
	public static ModMetadataImpl parse(Reader in) throws IOException {
		Map<String, Object> root = JsonReader.parseObject(in);

		Object schema = root.get("schemaVersion");

		if (!(schema instanceof Long) || ((Long) schema).intValue() != SCHEMA_VERSION) {
			throw new IOException("unsupported schemaVersion: " + schema + " (expected " + SCHEMA_VERSION + ")");
		}

		String id = requireString(root, "id");
		String version = requireString(root, "version");
		String name = stringOr(root, "name", id);
		String description = stringOr(root, "description", "");

		List<String> authors = new ArrayList<>();
		Object rawAuthors = root.get("authors");

		if (rawAuthors instanceof List) {
			for (Object a : (List<Object>) rawAuthors) {
				if (a instanceof String) authors.add((String) a);
			}
		}

		String environment = stringOr(root, "environment", "*");

		Map<String, List<String>> entrypoints = new LinkedHashMap<>();
		Object rawEntrypoints = root.get("entrypoints");

		if (rawEntrypoints instanceof Map) {
			Map<String, Object> m = (Map<String, Object>) rawEntrypoints;

			for (Map.Entry<String, Object> e : m.entrySet()) {
				List<String> classes = new ArrayList<>();

				if (e.getValue() instanceof List) {
					for (Object c : (List<Object>) e.getValue()) {
						if (c instanceof String) classes.add((String) c);
					}
				} else if (e.getValue() instanceof String) {
					classes.add((String) e.getValue());
				}

				entrypoints.put(e.getKey(), Collections.unmodifiableList(classes));
			}
		}

		Map<String, String> dependencies = new LinkedHashMap<>();
		Object rawDepends = root.get("depends");

		if (rawDepends instanceof Map) {
			Map<String, Object> m = (Map<String, Object>) rawDepends;

			for (Map.Entry<String, Object> e : m.entrySet()) {
				dependencies.put(e.getKey(), String.valueOf(e.getValue()));
			}
		}

		List<String> mixinConfigs = new ArrayList<>();
		Object rawMixins = root.get("mixins");

		if (rawMixins instanceof List) {
			for (Object m : (List<Object>) rawMixins) {
				if (m instanceof String) mixinConfigs.add((String) m);
			}
		} else if (rawMixins instanceof String) {
			mixinConfigs.add((String) rawMixins);
		}

		String accessWidener = null;
		Object rawAw = root.get("accessWidener");

		if (rawAw instanceof String && !((String) rawAw).isEmpty()) {
			accessWidener = (String) rawAw;
		}

		List<String> nestedJars = new ArrayList<>();
		Object rawJars = root.get("jars");

		if (rawJars instanceof List) {
			for (Object j : (List<Object>) rawJars) {
				if (j instanceof String) {
					nestedJars.add((String) j);
				} else if (j instanceof Map) {
					// Accept Fabric-style { "file": "META-INF/jars/foo.jar" } entries too.
					Object file = ((Map<String, Object>) j).get("file");
					if (file instanceof String) nestedJars.add((String) file);
				}
			}
		} else if (rawJars instanceof String) {
			nestedJars.add((String) rawJars);
		}

		List<String> providedMods = new ArrayList<>();
		Object rawProvided = root.get("providedMods");

		if (rawProvided instanceof List) {
			for (Object p : (List<Object>) rawProvided) {
				if (p instanceof String) providedMods.add((String) p);
			}
		} else if (rawProvided instanceof String) {
			providedMods.add((String) rawProvided);
		}

		// license: string or list of strings
		List<String> license = new ArrayList<>();
		Object rawLicense = root.get("license");

		if (rawLicense instanceof List) {
			for (Object l : (List<Object>) rawLicense) {
				if (l instanceof String) license.add((String) l);
			}
		} else if (rawLicense instanceof String) {
			license.add((String) rawLicense);
		}

		// icon: string path
		String iconPath = null;
		Object rawIcon = root.get("icon");
		if (rawIcon instanceof String && !((String) rawIcon).isEmpty()) iconPath = (String) rawIcon;

		// contact: {key: value, …}
		dev.union.api.ContactInformation contact = dev.union.api.ContactInformation.empty();
		Object rawContact = root.get("contact");
		if (rawContact instanceof Map) {
			Map<String, String> flat = new LinkedHashMap<>();
			for (Map.Entry<String, Object> e : ((Map<String, Object>) rawContact).entrySet()) {
				if (e.getValue() instanceof String) flat.put(e.getKey(), (String) e.getValue());
			}
			contact = new ContactInformationImpl(flat);
		}

		// custom: arbitrary top-level object, stored as-is
		Map<String, Object> custom = Map.of();
		Object rawCustom = root.get("custom");
		if (rawCustom instanceof Map) {
			custom = Collections.unmodifiableMap(new LinkedHashMap<>((Map<String, Object>) rawCustom));
		}

		Builder b = new Builder();
		b.id = id; b.version = version; b.name = name; b.description = description;
		b.authors = Collections.unmodifiableList(authors);
		b.environment = environment;
		b.entrypoints = Collections.unmodifiableMap(entrypoints);
		b.dependencies = Collections.unmodifiableMap(dependencies);
		b.mixinConfigs = Collections.unmodifiableList(mixinConfigs);
		b.accessWidener = accessWidener;
		b.nestedJars = Collections.unmodifiableList(nestedJars);
		b.providedMods = Collections.unmodifiableList(providedMods);
		b.license = Collections.unmodifiableList(license);
		b.contact = contact;
		b.iconPath = iconPath;
		b.custom = custom;
		return new ModMetadataImpl(b);
	}

	private static String requireString(Map<String, Object> root, String key) throws IOException {
		Object v = root.get(key);

		if (!(v instanceof String) || ((String) v).isEmpty()) {
			throw new IOException("missing or empty required string field: " + key);
		}

		return (String) v;
	}

	private static String stringOr(Map<String, Object> root, String key, String fallback) {
		Object v = root.get(key);
		return v instanceof String ? (String) v : fallback;
	}

	@Override public String getId() { return id; }
	@Override public String getVersion() { return version; }
	@Override public String getName() { return name; }
	@Override public String getDescription() { return description; }
	@Override public List<String> getAuthors() { return authors; }
	@Override public String getEnvironment() { return environment; }
	@Override public Map<String, List<String>> getEntrypoints() { return entrypoints; }
	@Override public Map<String, String> getDependencies() { return dependencies; }
	@Override public List<String> getMixinConfigs() { return mixinConfigs; }
	@Override public String getAccessWidener() { return accessWidener; }
	@Override public List<String> getNestedJars() { return nestedJars; }
	@Override public List<String> getProvidedMods() { return providedMods; }
	@Override public List<String> getLicense() { return license; }
	@Override public dev.union.api.ContactInformation getContact() { return contact; }
	@Override public String getIconPath() { return iconPath; }
	@Override public Map<String, Object> getCustom() { return custom; }

	@Override
	public String toString() {
		return id + "@" + version;
	}
}
