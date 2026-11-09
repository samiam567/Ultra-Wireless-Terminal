# ultra_wireless_terminal

A NeoForge mod for **Minecraft 1.21.1** (NeoForge `21.1.251`, Java 21). It adds a handheld
*Ultra Wireless Terminal* item that links to **multiple Tom's Storage terminals** and, when the
Create mod is installed, **Create stock tickers**, then opens the closest available one from
anywhere.

## Feature summary

- Item `ultra_wireless_terminal:ultra_wireless_terminal` (stacks to 1):
  - Implements Tom's Storage' `WirelessTerminal` capability (`open` / `canOpen`), so it can
    be opened via Tom's Storage' open keybind.
  - **Link/unlink** Tom's Storage terminals: Sneak + right-click a terminal block (block tag
    `toms_storage:remote_activate`). Links are stored as a `bound_terminals` data component
    (`List<WorldPos>`).
  - **Clear all**: Sneak + right-click in air.
  - **Open closest**: right-click the item (server-side). The selected terminal must be in a
    loaded level and its block must still belong to the `remote_activate` tag.
  - **Create stock tickers** (only when Create is loaded): Sneak + right-click a stock ticker
    to link/unlink (stored in the `bound_stock_tickers` component). The **N** keybind sends a
    custom packet to the server, which opens the closest linked stock ticker via reflection.
  - Wearable in a **Curios belt** slot; appears in the **Tom's Storage creative tab**.
  - Craftable: `toms_storage:adv_wireless_terminal` + `minecraft:ender_eye` (shapeless).
- Create and Curios are integrated **via reflection only** (`Class.forName`) on purpose, so
  the mod compiles without declaring them as dependencies. They are optional at runtime.
  `neoforge.mods.toml` declares only: neoforge (required), minecraft (required), toms_storage
  (required, AFTER), jei (optional).
- Dependencies are optional for Create; every Create-related UI line (keybind hint, stock
  ticker tooltip lines, count) is gated on `isCreateInstalled()`.

## Repository layout

```
build.gradle              # Moddev plugin + test configuration
settings.gradle           # Plugin repositories (mavenLocal, portal, neoforge maven)
gradle.properties         # -Xmx3G, daemon off
libs/toms_storage_stubs.jar   # Stub jar: WorldPos + WirelessTerminal interface only
src/main/java/com/ultrawireless/terminal/   # 5 source classes (see below)
src/main/resources/
  META-INF/neoforge.mods.toml     # mod metadata (version/mcversion expanded at build time)
  assets/ultra_wireless_terminal/ # lang/en_us.json, item model + png texture
  data/curios/tags/item/belt.json # puts item in Curios belt slot
  data/ultra_wireless_terminal/recipe/ultra_wireless_terminal.json
  pack.mcmeta
src/test/java/com/ultrawireless/terminal/    # JUnit 5 unit tests (see "Testing")
```

## Source classes

All in `com.ultrawireless.terminal`:

| Class | Responsibility |
|---|---|
| `UltraWirelessTerminalMod` | `@Mod` entry. Constructor (mod event bus) registers the item, the two `DataComponentType`s (`bound_terminals`, `bound_stock_tickers`, both `List<WorldPos>`), the network handler, and the client (client dist only). Also hooks `BuildCreativeModeTabContentsEvent` to add the item to the `toms_storage` creative tab. |
| `UltraWirelessTerminalItem` | The item; implements `WirelessTerminal`. Tooltip (Shift for details), `use`/`useOn` (link, unlink, clear-all), terminal activation logic (closest available, server-side, re-checks block after selection), Create stock-ticker activation (reflection on `com.simibubi.create.content.logistics.stockTicker.StockTickerInteractionHandler#interactWithLogisticsManagerAt`), and the static, tested helper `selectClosest(...)`. |
| `UltraWirelessTerminalClient` | Registers the **N** keybinding `key.ultra_wireless_terminal.open_stock_ticker` (category `GAMEPLAY`) and a `ClientTickEvent.Post` handler that consumes the key and calls `UltraWirelessTerminalNetworkHandler.sendOpenStockTicker()`. |
| `UltraWirelessTerminalNetworkHandler` | Registers payload `ultra_wireless_terminal:open_stock_ticker` (registry version `1`) and its server-side handler: finds the player's terminal (main inventory, then Curios belt via reflection) and calls `UltraWirelessTerminalItem.activateStockTicker`. Also exposes `sendOpenStockTicker()` for the client handler. |
| `UltraWirelessTerminalPacket` | Empty `CustomPacketPayload` (`open_stock_ticker`) used to request the server to open the closest linked stock ticker. |

## Build configuration

- **Build tool:** Gradle, plugin `net.neoforged.moddev:1.0.17` (the Moddev plugin). A Gradle
  wrapper is included (`./gradlew`, `gradlew.bat`, `gradle/wrapper/`), pinned to Gradle **9.8.1**.
- `gradle.properties`: `org.gradle.jvmargs=-Xmx3G`, daemon disabled.
- **`libs/toms_storage_stubs.jar`** contains only `com.tom.storagemod.components.WorldPos`
  and `com.tom.storagemod.item.WirelessTerminal` — enough to compile against Tom's Storage
  without depending on the actual mod.
- **Test classpath** (see `build.gradle` `dependencies` block) intentionally includes:
  - `libs/toms_storage_stubs.jar`
  - `build/moddev/artifacts/*.jar` — the NeoForge/Minecraft jars the Moddev plugin downloads
    (`neoforge-21.1.251-minecraft*.jar`), needed so tests can reference MC/NeoForge APIs
  - `fileTree('${gradle.gradleUserHomeDir}/caches/modules-2', '**/*.jar')` — portable reference
    to each developer's Gradle module cache (populated by the NeoForge moddev plugin on first
    build). No hardcoded `/home/agent` path; works anywhere `~/.gradle` is populated.
- `processResources` expands `version`/`mcversion` into `META-INF/neoforge.mods.toml`
  (currently `1.0.0` / `1.21.1`). The shipped mod jar ends up in `build/libs/ultra_wireless_terminal.jar`.

## Commands

Run from the project root using the Gradle wrapper (`./gradlew`). No separate `gradle`
install is needed (Gradle 9.8.1 is pinned):

```sh
./gradlew test          # JUnit 5 unit tests (pure JVM — no game world needed)
./gradlew build         # Full build; produces build/libs/ultra_wireless_terminal.jar
./gradlew client        # Moddev run config "client": launch NeoForge client with the mod
./gradlew server        # Moddev run config "server": launch a dev server with the mod
./gradlew clean         # Wipe build/ and re-generated caches
./gradlew test --info   # Useful when debugging the test classpath
```

Run configs (`runs { client { client() } server { server() } }`) are named `client` and
`server` by the Moddev plugin. `run/` and `logs/` are the plugin's output/log dirs.

## Testing

Tests are plain **JUnit 5** in `src/test/java/com/ultrawireless/terminal/`. They run headless
(no Minecraft/NeoForge environment required) against the stubs jar plus the NeoForge/Minecraft
jars on the test classpath. Six classes, 19 tests (all passing; HTML report at
`build/reports/tests/test/index.html`, raw results under `build/test-results/test/`):

| Test class | Covers |
|---|---|
| `BasicTest` | `WorldPos` equality semantics |
| `SelectionTest` | `UltraWirelessTerminalItem.selectClosest` — same-dim closest wins |
| `SelectionEdgeCasesTest` | `selectClosest` robustness contract (see "Key invariants") |
| `StockTickerSelectionTest` | `selectClosest` used for stock ticker selection |
| `EventBusRegistrationTest` | `ClientTickEvent.Post` handler is `@SubscribeEvent` and takes the **NeoForge** event type (bus-bound, not mod-bus) |
| `IsStockTickerTest` | `isStockTicker(Block)` returns `false` for a null block |

## Key invariants (don't break these)

- **`selectClosest(List<WorldPos>, playerDim, x, y, z)` contract:**
  - `null` candidates are skipped (no NPE).
  - A same-dimension candidate **always beats** any cross-dimension candidate — cross-dim
    distance is set to `MAX_VALUE` and never compared.
  - Among same-dim candidates, smallest squared distance wins; on exact ties the
    **earlier list element wins** (strict `<` comparison).
  - Returns `null` when there is no valid candidate.
  - A `null` player dimension must not throw (treated as "no same-dim match").
- **Server-side guards:** linking/unlinking/clearing/activation mutate item data and/or
  interact with levels — always behind `!level.isClientSide` checks; player messages use
  `displayClientMessage`.
- **Client event wiring:** `onClientTick` must keep its `@SubscribeEvent` annotation, take
  `ClientTickEvent.Post` (the NeoForge client event), and be registered on `NeoForge.EVENT_BUS`
  — not the mod event bus.
- **Optional-mod reflection:** Create and Curios access is reflection-based on purpose. Don't
  add them to `compileOnly`/`dependencies`; keep failures non-fatal (return `false`/`null`).
- **Create gating:** every stock-ticker tooltip line/keybind hint must be conditional on
  `isCreateInstalled()`.

## Gotchas

- **Gradle wrapper included** — run with `./gradlew` (Gradle 9.8.1 pinned). No separate `gradle`
  install needed. `./gradlew`, `gradlew.bat`, and `gradle/wrapper/` are checked in.
- **Sandbox SSL caveat:** the sandbox JVM's trust store (`cacerts`) is broken, so the
  wrapper cannot download Gradle from services.gradle.org here. If `./gradlew` fails to
  fetch the distribution, set `gradle-wrapper.properties` `distributionUrl` to a local
  Gradle zip (e.g. `file:///path/to/gradle-9.8.1-bin.zip`) until you're on a machine with
  working HTTPS.
- **`git` pager is not installed** in the sandbox: use `git --no-pager diff/log` or
  `GIT_PAGER=cat`.
- `build/` and `.gradle/` are gitignored (generated caches). `.eclipse/` and `bin/` are local
  IDE residue, not tracked.
- The test classpath fileTree path (`/home/agent/.gradle/caches/modules-2`) is the one
  machine-specific line in `build.gradle`; it will need updating if you build elsewhere.
- `neoforge.mods.toml` version is expanded at build time — change the `version`/`mcversion`
  properties in `build.gradle`, not the toml itself.

## Status (at time of writing)

Latest commit `0bbb5b0` gates all stock-ticker/Create tooltip references on Create and reworks
the keybind tooltip lines. There are also recent, uncommitted changes worth knowing about:
- `build.gradle` adds `testRuntimeOnly 'org.junit.platform:junit-platform-launcher:1.10.2'`
  (needed to run the JUnit 5 suite).
- `UltraWirelessTerminalItem.java` restructures the tooltip (stock ticker keybind + clear-all
  line now gated on Create) and adds the `wp == null` guard in `selectClosest`.
- Untracked tests `IsStockTickerTest.java` and `SelectionEdgeCasesTest.java`.
- `./gradlew test` report shows **19 passed, 0 failures, 0 skipped**.
