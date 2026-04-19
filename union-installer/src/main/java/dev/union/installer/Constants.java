package dev.union.installer;

/**
 * Shared constants for the installer.
 */
public final class Constants {
	private Constants() { }

	public static final String LOADER_GROUP = "dev.union";
	public static final String LOADER_ARTIFACT = "union-loader";

	/** Maven-style coordinate fragment appended to the profile library list. */
	public static String loaderLibraryName(String loaderVersion) {
		return LOADER_GROUP + ":" + LOADER_ARTIFACT + ":" + loaderVersion;
	}

	public static final String PROFILE_MAIN_CLASS = "dev.union.impl.launch.UnionLauncher";

	public static final String EMBEDDED_LOADER_RESOURCE = "/union-loader.jar";

	public static String profileName(String loaderVersion, String mcVersion) {
		return "union-" + loaderVersion + "-" + mcVersion;
	}

	/** Extracted installer version; falls back to {@code "dev"} for unpackaged runs. */
	public static String installerVersion() {
		Package p = Constants.class.getPackage();
		String v = p == null ? null : p.getImplementationVersion();
		return v != null ? v : "dev";
	}
}
