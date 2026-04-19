package dev.union.api.event;

import java.util.Objects;

/**
 * Opaque identifier for a region. The {@link RegionScheduler} uses these to decide whether a
 * caller is on the right thread for a given {@link AffineEvent} and, if not, to queue a
 * telegram to the owning thread.
 *
 * <h3>Runtime profiles</h3>
 * <ul>
 *   <li><b>Dedicated server, no Folia</b> — {@link #GLOBAL} is the only region. The server
 *       main thread is always on-region.</li>
 *   <li><b>Client</b> — {@link #CLIENT} is the only region. The render thread is on-region;
 *       worker threads are not.</li>
 *   <li><b>Dedicated server with Folia (future)</b> — {@link #GLOBAL} still exists for
 *       server-lifecycle events, plus one {@code RegionKey} per Folia region (coarse tile of
 *       chunks). Each region thread is on-region only for its own key.</li>
 * </ul>
 *
 * <p>Key equality is by value: two {@code RegionKey}s with the same world-id and region
 * coordinates compare equal regardless of instance. This lets mods cache per-region state in
 * maps without worrying about identity.
 *
 * <h3>Construction</h3>
 * Prefer the static factories:
 *
 * <pre>{@code
 * RegionKey.of(world, blockPos);   // infer from location
 * RegionKey.of(entity);            // infer from entity position
 * RegionKey.GLOBAL;                // server-wide scope
 * RegionKey.CLIENT;                // client main-thread scope
 * }</pre>
 *
 * Direct construction is available for advanced use (e.g. region-aware test harnesses) but
 * most code should use the factories.
 */
public final class RegionKey {
	/**
	 * The server-wide scope. Used for events with no spatial affinity (server start,
	 * config reload, datapack reload) and as the default on non-Folia runtimes.
	 */
	public static final RegionKey GLOBAL = new RegionKey("__global__", 0, 0);

	/**
	 * The client main-thread scope. Used for all client-side affine events. On the server
	 * side this key is unused.
	 */
	public static final RegionKey CLIENT = new RegionKey("__client__", 0, 0);

	private final String worldId;
	private final int regionX;
	private final int regionZ;

	/**
	 * Direct construction. Prefer {@link #of(Object, int, int)} or {@link #of(Object)} when
	 * a world + position are available. {@code worldId} must uniquely identify the world
	 * (typically the registry id of the dimension).
	 */
	public RegionKey(String worldId, int regionX, int regionZ) {
		this.worldId = Objects.requireNonNull(worldId, "worldId");
		this.regionX = regionX;
		this.regionZ = regionZ;
	}

	/**
	 * Derive a region key from a world + block position. The concrete region granularity is
	 * runtime-dependent: on non-Folia runtimes both coords collapse to 0 (the whole world is
	 * one region); on Folia the coords correspond to Folia's region tile size.
	 *
	 * <p>This lives on the API layer so it has no compile-time Minecraft dependency — the
	 * parameters are untyped to the API and the runtime scheduler is responsible for
	 * bridging them. The integration layer (e.g. {@code union-mc-26.1.2}) provides typed
	 * overloads that call through here.
	 *
	 * @param world     a token uniquely identifying the world (usually a {@code Level} ref
	 *                  or dimension registry key). The bridge reads the id from it.
	 * @param blockX    world-space X
	 * @param blockZ    world-space Z
	 */
	public static RegionKey of(Object world, int blockX, int blockZ) {
		RegionScheduler sched = RegionScheduler.current();
		return sched.keyOf(world, blockX, blockZ);
	}

	/**
	 * Derive a region key from a single location-bearing object — an {@code Entity}, a
	 * {@code BlockEntity}, or similar. The active {@link RegionScheduler} knows how to
	 * extract world + coordinates from recognised types.
	 */
	public static RegionKey of(Object locationBearer) {
		RegionScheduler sched = RegionScheduler.current();
		return sched.keyOf(locationBearer);
	}

	public String worldId() { return worldId; }
	public int regionX()    { return regionX; }
	public int regionZ()    { return regionZ; }

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof RegionKey)) return false;
		RegionKey that = (RegionKey) o;
		return regionX == that.regionX && regionZ == that.regionZ && worldId.equals(that.worldId);
	}

	@Override
	public int hashCode() {
		int h = worldId.hashCode();
		h = 31 * h + regionX;
		h = 31 * h + regionZ;
		return h;
	}

	@Override
	public String toString() {
		if (this == GLOBAL) return "RegionKey.GLOBAL";
		if (this == CLIENT) return "RegionKey.CLIENT";
		return "RegionKey[" + worldId + "@" + regionX + "," + regionZ + "]";
	}
}
