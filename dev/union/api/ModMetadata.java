package dev.union.api;

import java.util.List;
import java.util.Map;

/**
 * Parsed {@code union.mod.json} descriptor for a mod.
 */
public interface ModMetadata {
	int SCHEMA_VERSION = 1;

	String getId();

	String getVersion();

	String getName();

	String getDescription();

	List<String> getAuthors();

	/**
	 * @return {@code "*"} (any), {@code "client"}, or {@code "server"}.
	 */
	String getEnvironment();

	/**
	 * @return map from entrypoint key ({@code main}, {@code client}, {@code server}, or custom)
	 *         to the list of fully qualified class names implementing that entrypoint.
	 */
	Map<String, List<String>> getEntrypoints();

	/**
	 * @return map from mod-id to a version predicate (currently opaque; see
	 *         {@link dev.union.impl.discovery.ModDiscoverer} for resolution semantics).
	 */
	Map<String, String> getDependencies();

	/**
	 * @return list of Mixin config JSON resource paths (relative to the mod JAR root) to register
	 *         with the Mixin engine at launch time. Empty if the mod doesn't use Mixins.
	 */
	List<String> getMixinConfigs();

	/**
	 * @return JAR-root-relative path to an access-widener file, or {@code null} if the mod
	 *         doesn't declare one. At most one AW per mod.
	 */
	String getAccessWidener();

	/**
	 * @return list of JAR-root-relative paths to nested library or mod JARs (Jar-in-Jar).
	 *         Each nested JAR is extracted to the Union cache directory and added to the
	 *         classloader. If a nested JAR itself declares {@code union.mod.json} it becomes
	 *         a fully-loaded mod; otherwise it's a plain classpath entry. Empty if unused.
	 */
	List<String> getNestedJars();

	/**
	 * @return list of additional mod ids this jar is "providing" — virtual mods that get
	 *         registered alongside this mod's real id so that other mods' {@code depends}
	 *         requirements are satisfied. Useful for bundling several historical mod ids
	 *         into one combined jar (e.g. a Union API uber-jar provides
	 *         {@code union_api_base}, {@code union_api_event}, etc.). Empty if unused.
	 */
	List<String> getProvidedMods();

	/**
	 * @return free-text license identifier(s) as declared in {@code union.mod.json}. Typically
	 *         an SPDX identifier like {@code "MIT"} or {@code "Apache-2.0"}. Empty list if
	 *         unspecified.
	 */
	List<String> getLicense();

	/**
	 * @return parsed contact block — homepage, sources, issue tracker, etc. Never {@code null};
	 *         returns an empty contact object if nothing was declared.
	 */
	ContactInformation getContact();

	/**
	 * @return JAR-root-relative path to an icon image (usually PNG), or {@code null} if the
	 *         mod didn't ship one. Used by mod-browser UIs for the mod-list icon.
	 */
	String getIconPath();

	/**
	 * @return arbitrary mod-defined custom values from the top-level {@code "custom"} object
	 *         in {@code union.mod.json}. Keys namespace freely (recommended:
	 *         {@code "mymod:something"}). Values are opaque objects — mod code type-checks them
	 *         at read.
	 */
	Map<String, Object> getCustom();
}
