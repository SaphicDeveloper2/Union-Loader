# Union

A minimalist Fabric-inspired modloader for Minecraft 1.21.x. Two modules:

- **`union-loader/`** — runtime. Classloader, mod discovery, entrypoint dispatch. Zero external deps (hand-rolled JSON parser).
- **`union-installer/`** — installer + bootstrapper. Swing GUI + headless CLI. The built loader JAR is embedded as a resource at `/union-loader.jar` inside the installer JAR, so installing needs no network access.

## Build

```
gradle build
```

Output artefacts:

- `union-loader/build/libs/union-loader-<version>.jar`
- `union-installer/build/libs/union-installer-<version>.jar` — this is the one you ship to end users; the loader is embedded.

## Install

GUI:

```
java -jar union-installer-<version>.jar
```

CLI:

```
java -jar union-installer-<version>.jar install --mc-version 26.1.2
# options: --mc-dir <path>, --no-profile, --help
```

The installer:
1. Extracts the embedded loader JAR to `<mcDir>/libraries/dev/union/union-loader/<ver>/union-loader-<ver>.jar`.
2. Writes a profile JSON at `<mcDir>/versions/union-<ver>-<mcVer>/union-<ver>-<mcVer>.json` that `inheritsFrom` the vanilla release profile and overrides `mainClass` to `dev.union.impl.launch.UnionLauncher`.
3. (Optionally) adds an entry to `launcher_profiles.json` so the profile appears in the Minecraft launcher dropdown.

## Writing a mod

Drop `union.mod.json` at the root of your mod JAR. See `docs/example-union.mod.json` for the shape.

```java
public final class ExampleMod implements ModInitializer {
    @Override
    public void onInitialize() {
        System.out.println("Hello from Union!");
    }
}
```

Supported entrypoint keys (out of the box):

| Key | Interface | When |
| --- | --- | --- |
| `main`   | `ModInitializer`               | Always, on both sides |
| `client` | `ClientModInitializer`         | Client only |
| `server` | `DedicatedServerModInitializer`| Dedicated server only |

Custom entrypoint keys are allowed — your code just needs to call `UnionLoader.get()` and fetch them with your own type (a `getEntrypointContainers` API is a TODO).

## Mixin

Union bundles SpongePowered Mixin `0.8.7` inside the loader JAR (all Forge / ModLauncher / LaunchWrapper service impls were stripped; Union's own service lives under `dev.union.mixin.service`). ASM, Guava, and Gson stay `compileOnly` at build time and are picked up from Minecraft's classpath at runtime.

Declare mixin configs in `union.mod.json`:

```json
{
  "mixins": [
    "yourmod.mixins.json"
  ]
}
```

Each entry is a JAR-root-relative resource path to a standard Mixin config JSON (`required`, `package`, `mixins`, `injectors`, etc. — see the Mixin docs). Union discovers all configs across all loaded mods and registers them with `Mixins.addConfiguration(...)` before any entrypoint runs.

The platform reports side via `dev.union.mixin.service.UnionPlatformAgent` and the loader's `UnionClassLoader` routes class loads through `IMixinTransformer` (resolved lazily from `GlobalProperties.Keys.TRANSFORMER`). Package prefixes that must bypass transformation (Mixin itself, ASM, `dev.union.impl.*`, `dev.union.mixin.*`, `java.*`, etc.) are excluded by default; add more with `UnionClassLoader#addTransformerExclusion`.

## Access wideners

Fabric-compatible access widener files (format v1 and v2). Declare one per mod:

```json
{
  "accessWidener": "yourmod.accesswidener"
}
```

Example `yourmod.accesswidener` at the JAR root:

```
accessWidener v2 named

# Class: make the inventory screen non-final and public
accessible class net/minecraft/client/gui/screens/inventory/InventoryScreen

# Method: open up a private helper on MinecraftServer
accessible method net/minecraft/server/MinecraftServer tickChildren (Ljava/util/function/BooleanSupplier;)V

# Field: drop `final` from a config value so runtime overrides stick
mutable    field net/minecraft/server/MinecraftServer DEFAULT_TICK_TIMEOUT I
```

Semantics match Fabric exactly:

| Directive | Class | Method | Field |
| --- | --- | --- | --- |
| `accessible` | public, non-final | public, non-final (unless static) | public |
| `extendable` | public, non-final | protected (at minimum), non-final | — |
| `mutable`    | — | — | non-final |

All mods' AWs are merged into a single `AccessWidener` at launch and applied during `findClass` **before** the Mixin transformer, so mixins can target the widened members.

The `transitive-` prefix (v2) parses but is currently treated identically to non-transitive — cross-mod AW propagation is TODO.

## Jar-in-Jar (JiJ)

Mods can bundle their dependencies directly inside the mod JAR:

```json
{
  "jars": [
    "META-INF/jars/some-library.jar",
    "META-INF/jars/bundled-mod.jar"
  ]
}
```

Each entry is a JAR-root-relative path to a nested `.jar`. At load time Union:

1. Extracts each nested JAR to `<gameDir>/.cache/union/jars/<sha1-of-bytes>/<filename>` (content-addressed — unchanged nested JARs reuse the cached extraction on subsequent launches).
2. Atomically writes via `<name>.tmp` + `Files.move(ATOMIC_MOVE)` to avoid partial files on crash.
3. Recurses: a nested JAR can itself declare further `jars` entries.
4. Branches per entry:
   - If the nested JAR has its own `union.mod.json`, it's loaded as a full mod with its own entrypoints, mixins, AW, etc.
   - Otherwise it's added to the classloader as a plain library JAR.

Fabric-style object entries (`{"file": "META-INF/jars/foo.jar"}`) are also accepted for ecosystem compatibility.

Cache layout:

```
<gameDir>/.cache/union/jars/
├── d2e6020e4a9c3601fbdff7f63fd4729e19008ef0/
│   └── some-library.jar
└── f2a103f5f99b680aba009f32034013ea3905b2db/
    └── bundled-mod.jar
```

Orphan entries from removed mods aren't cleaned up automatically yet — manual prune if needed. A cleanup pass is TODO.

## Server install

Dedicated server deployments skip the Mojang launcher entirely:

```
java -jar union-installer-<ver>.jar install-server \
  --mc-version 26.1.2 \
  --server-dir ./my-server \
  [--server-jar /path/to/minecraft_server.26.1.2.jar]
```

Produces:

```
my-server/
├── libraries/dev/union/union-loader/<ver>/union-loader-<ver>.jar
├── mods/                     (drop mods here)
├── minecraft_server.<ver>.jar  (present if --server-jar was given)
├── start.sh                  (POSIX 0755 — exec launcher with -Dunion.side=server)
├── start.bat                 (Windows — same classpath with ; separator)
└── eula.txt                  (stub; user must set eula=true before running)
```

`--server-jar` is optional — if omitted, download the vanilla server JAR from Mojang and drop it in manually before running. The launch scripts pass `nogui` by default plus any args given to them; set `JAVA_OPTS` to tune JVM flags (default `-Xmx4G`).

Side detection: `UnionLauncher` falls back to `SERVER` when `net.minecraft.client.main.Main` isn't on the classpath. Scripts also force it explicitly via `-Dunion.side=server` as belt-and-suspenders.

## Union API

Fabric's modularity, NeoForge's depth. Union's public API is split across focused modules — mods pull only what they need via Gradle `implementation`, and each module ships as its own Union mod with its own `union.mod.json`.

### Modules

| Module | Depends on | What it gives you |
| --- | --- | --- |
| `union-api-base` | loader | `Identifier` — namespaced IDs, MC-free |
| `union-api-event` | base | Event bus, `@SubscribeEvent`, `Priority`, `Phases`, `CancelableEvent`, `ResultEvent` |
| `union-api-registry` | base, event | `DeferredRegister<T>`, `RegistryObject<T>`, `RegisterEvent<T>` |
| `union-api-lifecycle` | base, event | `ServerLifecycleEvent`, `TickEvent`, `WorldLifecycleEvent` |
| `union-api-network` | base, event | `Payload`, `PayloadType`, `NetworkRegistry`, `PacketSender`, `PayloadContext` |
| `union-api-attachment` | base, event | `AttachmentType<T>`, `AttachmentRegistry`, `AttachmentHolder` |
| `union-api-bom` | (BOM) | Aggregated version manifest for one-import consumption |

Consumer Gradle fragment:

```groovy
dependencies {
    implementation platform('dev.union:union-api-bom:1.0.0')
    implementation 'dev.union:union-api-event'
    implementation 'dev.union:union-api-registry'
    implementation 'dev.union:union-api-lifecycle'
    // pick only what you need
}
```

### Event bus (the heart)

```java
// Declare an event
public final class BlockPlaceEvent extends CancelableEvent {
    private final Object player;  // typed at the MC integration layer
    private final Object blockPos;
    public BlockPlaceEvent(Object player, Object pos) { this.player = player; this.blockPos = pos; }
    public Object player() { return player; }
}

// Register a handler — reflection style
public final class MyMod {
    @SubscribeEvent(priority = Priority.HIGH)
    public void onBlockPlace(BlockPlaceEvent e) {
        if (/* rule check */) e.setCanceled(true);
    }

    public MyMod() {
        EventBus.MAIN.register(this);
    }
}

// Or functional style
EventBus.MAIN.addListener(BlockPlaceEvent.class, Priority.HIGH, e -> {
    if (e.player() == null) e.setCanceled(true);
});

// Declare phase ordering for finer control
Phases.orderPhases(BlockPlaceEvent.class,
    Identifier.of("protection", "pre"),
    Identifier.of("protection", "post"));
```

Within a priority level handlers fire in phase topological order; within a phase they fire in registration order. Cancelled events skip later handlers unless they opt in via `@SubscribeEvent(acceptsCanceled = true)`.

Two default buses:
- `EventBus.MAIN` — runtime events (tick, block place, entity interact, server start, …).
- `EventBus.MOD` — boot-time events (registry, common-setup, client-setup, data-gen). `DeferredRegister.registerSelf(EventBus.MOD)`.

### DeferredRegister

```java
public final class MyMod {
    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(RegistryKey.of("minecraft", "item"), "mymod");

    public static final RegistryObject<Item> MY_ITEM =
        ITEMS.register("my_item", () -> new Item(...));

    public MyMod() {
        ITEMS.registerSelf(EventBus.MOD);
    }
}
```

`MY_ITEM.get()` throws `IllegalStateException` before registration fires — catches the "accessed static final from a classinit" mistake early rather than producing a null at runtime.

### Attachments

```java
public static final AttachmentType<Long> LAST_LOGIN = AttachmentRegistry.register(
    AttachmentType.builder(
            Identifier.of("mymod", "last_login"),
            AttachmentTarget.ENTITY,
            Long.class,
            () -> 0L)
        .persistent(
            v -> ByteBuffer.allocate(8).putLong(v).array(),
            b -> ByteBuffer.wrap(b).getLong())
        .synced()
        .survivesDeath()
        .build());

// Later, on a Player:
long when = ((AttachmentHolder) player).get(LAST_LOGIN);
((AttachmentHolder) player).set(LAST_LOGIN, System.currentTimeMillis());
```

### Network

```java
public record HelloPayload(String msg) implements Payload {
    public static final Payload.Type<HelloPayload> TYPE = new Payload.Type<>(
        Identifier.of("mymod", "hello"),
        reader -> new HelloPayload(reader.readString()));

    @Override public Identifier id() { return TYPE.id(); }
    @Override public void write(PayloadWriter out) { out.writeString(msg); }
}

// Mod init
NetworkRegistry.forMod("mymod")
    .playServerbound(HelloPayload.TYPE, (payload, ctx) ->
        ctx.enqueue(() -> System.out.println("server got: " + payload.msg())));

// Client-side call
PacketSender.get().sendToServer(new HelloPayload("hi"));
```

### Why split this way

The separation is Fabric's — you pull `union-api-event` without inheriting all of registry, lifecycle, network, and attachment. The depth inside each module is NeoForge's — the event bus isn't a callback list, it's a priority/phase/cancellation/result/reflection dispatcher; `DeferredRegister` is the full declare-early/resolve-late pattern, not a `Consumer<RegisterEvent>`; `AttachmentType` includes `persistent/synced/survivesDeath/copiesOnDimensionChange`, not just "some data stuck on an entity".

Minecraft is deliberately *not* on any API module's compile classpath. Everything uses `Object` at MC boundaries (servers, players, worlds, item stacks) and gets typed by the integration module you write for your target Minecraft version. This means:
- Union API jars are tiny and MC-version-agnostic.
- Adding a new MC version doesn't touch the API modules — just ship a new integration jar.
- Unit-testing API code needs no MC test harness.

## Runtime args / system properties

| Property | Purpose |
| --- | --- |
| `-Dunion.side=client\|server` | Override side detection (normally autodetected via classpath). |
| `-Dunion.gameDir=<path>`      | Override game dir (normally `--gameDir` from args or `user.dir`). |
| `-Dunion.development=true`    | Enables `UnionLoader.isDevelopmentEnvironment()`. |
| `-Dunion.debug=true`          | Verbose loader logging. |

## Design notes

- The loader's mod discovery scans `<gameDir>/mods/*.jar` for `union.mod.json` at the JAR root.
- Env filtering (`"environment": "client"/"server"/"*"`) is applied at discovery time.
- Dependency checking currently verifies presence only. Version predicates are opaque strings — wire up proper semver resolution before 1.0.
- `UnionClassLoader` is a plain URLClassLoader with `registerAsParallelCapable()`. Transformer hooks (Mixin etc.) plug in here — this is where that work goes when you decide on a transformer story.
- The installer and the loader do not share a compile-time dependency; both carry their own copy of the zero-dep `JsonReader`. Keeps the module graph clean and matches the "loader lives as an embedded resource" design.
