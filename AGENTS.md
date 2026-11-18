# ultra_wireless_terminal_2

A NeoForge mod for **Minecraft 1.21.1** (NeoForge `21.1.251`, Java 21). It adds a handheld
*Ultra Wireless Terminal 2* item that links to **multiple Tom's Storage terminals** and, when
the Create mod is installed, **Create stock tickers**.

Unlike v1 (`ultra_wireless_terminal`, which merged terminals into a custom Combined Terminal
View), v2 **opens one bound terminal at a time — Tom's Storage's real terminal GUI** — exactly
like Tom's Storage' *advanced wireless terminal*, but with **in-GUI buttons to switch which
bound in-range terminal is active**.

Tom's Storage Github Link:
https://github.com/tom5454/Toms-Storage

Create Mod Github Link:
https://github.com/Creators-of-Create/Create

Curios Github Link:
https://github.com/TheIllusiveC4/Curios

## Feature summary

- Item `ultra_wireless_terminal_2:ultra_wireless_terminal` (stacks to 1):
  - Implements Tom's Storage' `WirelessTerminal` capability (`open` / `canOpen` / `getRange`
    with range 64), so Tom's Storage' own open keybind works while the item is in the
    inventory. `open()` is called **server-side** by Tom's Storage (`NetworkHandler
    handleTermServer`) and routes into `UltraWirelessTerminalNetworkHandler.openActiveTerminal`.
  - **Link/unlink terminals**: Sneak + right-click a terminal block (block tag
    `toms_storage:remote_activate`). Links stored in the `bound_terminals` data component
    (`List<WorldPos>`). Unlinking the active terminal clears the active selection.
  - **Active terminal**: single `WorldPos` stored in the `active_terminal` data component.
    The chosen terminal stays active until another is selected **or it goes out of range**.
  - **Open** (right-click in air, or Tom's Storage keybind): opens the **active** terminal;
    if it is out of range / unlinked / broken, the **nearest in-range terminal is
    auto-selected** (persisted as active) and opened. Never opens more than one terminal.
  - **Switch buttons** (`TerminalOverlay`): a column of buttons rendered beside any open
    container GUI (Tom's Storage terminal, inventory, chests) — one per bound terminal.
    Active = green ▶, inactive in-range = clickable (switches active + opens it in place),
    out-of-range = dimmed/not clickable. Nothing renders while fewer than two terminals are
    bound. **M** keybind (`key.ultra_wireless_terminal_2.open_selector`) fetches the list.
  - **Clear all**: Sneak + right-click in air (also clears active).
  - **Create stock tickers** (only when Create is loaded): Sneak + right-click a stock ticker
    to link/unlink (`bound_stock_tickers`). The **N** keybind opens the **closest** linked
    stock ticker via reflection — unchanged from v1: always nearest, no active selection.
  - Wearable in a **Curios belt** slot; appears in the **Tom's Storage creative tab**.
  - Craftable: `toms_storage:adv_wireless_terminal` + `minecraft:ender_eye` (shapeless).

Create and Curios are integrated **via reflection only** (`Class.forName`) on purpose, so the
mod compiles without declaring them as dependencies. They are optional at runtime.
`neoforge.mods.toml` declares only: neoforge (required), minecraft (required), toms_storage
(required, AFTER).

## Repository layout

```
build.gradle              # Moddev plugin + test configuration
settings.gradle           # rootProject.name = "ultra_wireless_terminal_2"
gradle.properties         # -Xmx3G, daemon off
libs/toms_storage_stubs.jar   # Stub jar: WorldPos + WirelessTerminal interface only
src/main/java/com/ultrawireless/terminal/
src/main/resources/
  META-INF/neoforge.mods.toml     # modId ultra_wireless_terminal_2
  assets/ultra_wireless_terminal_2/
    lang/en_us.json
    textures/item/ultra_wireless_terminal.png
    models/item/ultra_wireless_terminal.json
  data/curios/tags/item/belt.json
  data/ultra_wireless_terminal_2/recipe/ultra_wireless_terminal.json
  pack.mcmeta
src/test/java/com/ultrawireless/terminal/    # JUnit 5 unit tests
tools/UWT2_Selection.java  # dependency-free mirror of Selection.java + self test
```

## Source classes

All in `com.ultrawireless.terminal`:

| Class | Responsibility |
|---|---|
| `UltraWirelessTerminalMod` | `@Mod` entry. Registers item + `DataComponentType`s (`bound_terminals`, `bound_stock_tickers`, `active_terminal`, `terminal_names`, `clipboard_tag`), network handler, client (client dist only). Creative-tab hook into `toms_storage:tab`. |
| `UltraWirelessTerminalItem` | The item; implements `WirelessTerminal` (`getRange`=64, `open`, `canOpen`). `use` = open active-or-nearest (sneak = clear). `useOn` = link/unlink terminal or stock ticker. Tooltip shows link counts + active terminal. Static `selectClosest(...)` (v1 contract, kept for stock tickers + tests). Static `activateStockTicker`. |
| `UltraWirelessTerminalNetworkHandler` | Payload registration + server logic. `openActiveTerminal` = `Selection.resolveOnOpen` then `openTerminalAt` + persist active. `handleActivate` = `Selection.requestSwitch` then open + resend list. `handleRefresh` / `handleOpenSelector` send `TerminalListPacket`. `handleSaveClipboard` saves clipboard content to terminal item. Range checks via `InRangeChecker` + `InRangeResolver` (per-terminal block entity beacon level). `findTerminal` searches hands, inventory, curios (reflection). |
| `TerminalOverlay` | Client-only. Coordinates selector screen opening, layer stacking (`openAsLayer`), and retry watchdog (`tickRetry`). |
| `TerminalSelectorScreen` | Centred terminal selection GUI with terminal rows, renaming support, and Create Clipboard button. |
| `TerminalClipboardScreen` | Adapts Create's `ClipboardScreen` for the terminal item, persisting changes via `ClipboardSavePacket`. |
| `CreateStockTickerAutofill` | Client listener that autofills `#` address entries from the terminal's stored clipboard into Create's `StockKeeperRequestScreen`. |
| `Selection` | **Pure selection rules** (no world access): `resolveOnOpen` (keep active / auto-nearest fallback), `requestSwitch` (only bound + in-range), `selectClosest`, `cycle`. Mirrored dependency-free in `tools/UWT2_Selection.java`. |
| `InRangeContext` | Immutable player/dimension/range/beacon context. Mirrors Tom's Storage `canInteractWith`. |
| `InRangeResolver` | Pure range logic (Tom's Storage `canInteractWith` replica: base reach 6, terminal reach, +25% menu-check bonus, beacon same-dim / cross-dim thresholds). |
| `InRangeChecker` | Server-side: level loaded at terminal pos (any dimension) + block still `remote_activate` + beacon level via reflection (`getBeaconLevel()`) + `InRangeResolver`. |
| `BeaconConfig` | Reflective read of Tom's Storage config `wirelessTermBeaconLvl` / `wirelessTermBeaconLvlCrossDim` (`-1` = disabled). |
| `StockTickerProxy` | Runtime reflection helper for opening Create stock tickers and reach management. |
| `StockTickerReach` | Pure decision logic for stock ticker reach expansion. |
| `StockTickerBuffListener` | Server-side container listener managing player reach buffs while ticker menus are open. |
| `UltraWirelessTerminalClient` | Registers keybinds (`open_stock_ticker`, `open_selector`, `cycle_terminal_next`, `cycle_terminal_previous`); registers client tick handler; registers `CreateStockTickerAutofill` if Create is loaded. |
| `EmptyPayload` | Shared `StreamCodec` factory for empty payloads. |

Packets:

| Class | Direction | Purpose |
|---|---|---|
| `OpenActivePacket` | client→server | (unused by item use, kept for external use) open active/nearest |
| `OpenSelectorPacket` | client→server | M keybind: reply with the terminal list |
| `RefreshListPacket` | client→server | container GUI opened: re-sync the overlay list |
| `ActivatePacket` | client→server | overlay button click: set active + open (dim string + coords) |
| `TerminalListPacket` | server→client | all bound terminals with `inRange` / `active` flags |
| `UltraWirelessTerminalPacket` | client→server | open closest stock ticker |
| `RenamePacket` | client→server | persist terminal custom name |
| `CyclePacket` | client→server | cycle active terminal forward/backward |
| `StockSummaryPacket` | server→client | fallback stock summary when remote ticker GUI cannot open |
| `ClipboardSavePacket` | client→server | save edited Create clipboard content onto terminal item |

`ActivatePacket.worldPos()` rebuilds `ResourceKey<Level>` with
`ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimName))` (note: `create`,
not `of`, in 1.21.1).

## Build configuration

- **Build tool:** Gradle, plugin `net.neoforged.moddev:1.0.17`. Wrapper pinned to Gradle
  **9.8.1** (currently `distributionUrl=file:///tmp/gradle-9.8.1-bin.zip` — see Gotchas).
- `gradle.properties`: `org.gradle.jvmargs=-Xmx3G`, daemon disabled.
- **`libs/toms_storage_stubs.jar`**: only `com.tom.storagemod.components.WorldPos` and
  `com.tom.storagemod.item.WirelessTerminal` — compile against Tom's Storage without a
  runtime dependency. **Not** Tom's real classes at runtime; reflection bridges everything else.
- **Test classpath** (see `build.gradle`): stubs jar + `build/moddev/artifacts/*.jar` +
  the user's Gradle module cache (`caches/modules-2/**.jar` via `gradle.gradleUserHomeDir`,
  the one machine-specific line).
- No JEI dependency (v2 has no custom menu; Tom's Storage's GUI is used as-is, and JEI works
  against it on its own).
- `processResources` expands `version`/`mcversion` into `META-INF/neoforge.mods.toml`
  (`1.0.0` / `1.21.1`). Output jar: `build/libs/ultra_wireless_terminal_2.jar`.

## Commands

```sh
./gradlew test          # JUnit 5 unit tests (headless)
./gradlew build         # Full build -> build/libs/ultra_wireless_terminal_2.jar
./gradlew client        # dev client run
./gradlew server        # dev server run
```

The pure logic mirror also self-tests with plain javac/java:

```sh
javac tools/UWT2_Selection.java && java -cp tools UWT2_Selection   # prints SELFTEST PASS
```

## Testing

Plain **JUnit 5**, headless, against stubs + NeoForge/MC jars.
**10 test classes, 66 tests (all passing)**; report in `build/reports/tests/test/index.html`.

| Test class | Covers |
|---|---|
| `BasicTest` | `WorldPos` equality semantics |
| `SelectionTest` | `selectClosest` — same-dim closest wins |
| `SelectionEdgeCasesTest` | `selectClosest` robustness contract (nulls, ties, null dim) |
| `SelectionLogicTest` | **v2 rules**: keep-active, auto-nearest on open, switch refused out-of-range / unbound, null/empty handling, block-center distance |
| `SelectionCycleTest` | Terminal cycling forward/backward with wraparound and reachability filtering |
| `SelectorScreenLayoutTest` | Selector screen geometry, row scrolling, button layout |
| `ActivationRetryTest` | Client-side activation retry watchdog |
| `CreateStockTickerAutofillTest` | Pure clipboard address extraction, `#` precedence, checked/blank skipping, event bus registration |
| `EventBusRegistrationTest` | `onClientTick` is `@SubscribeEvent` with the NeoForge event type |
| `IsStockTickerTest` | `isStockTicker(null)` → false |

## Key invariants (don't break these)

- **`Selection.resolveOnOpen` contract**: active terminal kept only while it is *still bound*
  and *in range*; otherwise nearest in-range candidate; `null` when nothing is reachable.
  Never auto-selects on its own without an open attempt (persistence happens in the network
  handler, via `stack.set(ACTIVE_TERMINAL, ...)` only when the resolved value changed).
- **`Selection.requestSwitch`**: click accepted only if clicked ∈ bound **and** in range;
  refused clicks leave the current active terminal untouched and just re-send the list.
- **`selectClosest(List<WorldPos>, playerDim, x, y, z)` contract (v1, kept):** null candidates
  skipped; same-dim always beats cross-dim (cross-dim dist = `MAX_VALUE`); strict `<` so the
  earliest tie wins; null player dim must not throw; returns null when nothing valid.
- **`InRangeResolver.isInRange()` contract (mirrors Tom's Storage):** base reach 6 or terminal
  reach; `menuCheck` adds 25%; beacon ≥ threshold (range > 0): same-dim, and cross-dim only if
  ≥ cross-dim threshold; no beacon: cross-dim never in range.
- **Open path never bypasses Tom's Storage range checks**: activation goes through the block's
  own `useWithoutItem`, and `canInteractWith(player, false)` re-validates server-side — our
  `InRangeChecker` mirrors `canInteractWith` so the pre-check and Tom's check agree (no
  teleporting through out-of-range terminals).
- **Stock tickers always open the *nearest*** (no active-terminal concept) — unchanged from v1.
- **Server-side guards**: linking/unlinking/clearing/activation behind `!level.isClientSide`;
  player messages via `displayClientMessage`.
- **Client event wiring**: `TerminalOverlay` and `UltraWirelessTerminalClient` are registered
  on `NeoForge.EVENT_BUS` (client events), not the mod bus.
- **Optional-mod reflection**: Create/Curios via `Class.forName` only; failures non-fatal.
- **`tools/UWT2_Selection.java` and `Selection.java` must stay in sync** (same rules,
  dependency-free mirror; run its self test after changing either).

## Gotchas

- **Sandbox SSL caveat:** the sandbox JVM trust store is broken; `GRADLE_OPTS` must point at a
  working PKCS12 trust store and `JAVA_TOOL_OPTIONS` (proxy flags) must be unset, e.g.
  `env -u JAVA_TOOL_OPTIONS GRADLE_OPTS='-Djavax.net.ssl.trustStore=/tmp/java-trust/cacerts.p12
  -Djavax.net.ssl.trustStoreType=PKCS12 -Djavax.net.ssl.trustStorePassword=changeit'
  ./gradlew --no-daemon build`. The wrapper `distributionUrl` is a local file:// zip for the
  same reason.
- **`git` pager is not installed** in the sandbox: use `git --no-pager ...`.
- `build/`, `.gradle/`, `run/`, `logs/`, `bin/`, `.eclipse/` are gitignored.
- **No `net/minecraft` classes may ship in the jar.** There is deliberately *no*
  `net/minecraft/world/item/Item.java` override anywhere in this repo (a stale one from an old
  debugging session was removed — it shadowed the real class on some classpaths).
- **NeoForge 21.1 API names:** `ResourceKey.create(...)` (not `ResourceKey.of`),
  `PacketDistributor.sendToServer/sendToPlayer` (no `ClientPlayNetworking`),
  `net.neoforged.neoforge.network.PacketDistributor` import (not
  `net.minecraft.network.protocol.PacketDistributor`), payload registration via
  `RegisterPayloadHandlersEvent` + `PayloadRegistrar.playToServer/playToClient`.
- **NEVER read Minecraft fields reflectively by mojmap name.** `AbstractContainerScreen.leftPos`
  /`topPos`/`imageWidth`/`imageHeight` are obfuscated in production but dev-mapped in
  gradle runs, so reflective `getDeclaredField("leftPos")` *works in dev, silently returns
  null in a packaged jar* — this is exactly why the original selection overlay never drew
  for the user. Use the public getters `getGuiLeft()`/`getGuiTop()` (bodies compiled
  against the field, so they're stable), or don't need the panel at all.
- **Selection UI is a real Screen, not just painted buttons**: `TerminalSelectorScreen`
  opens via `Minecraft.pushGuiLayer(...)` (NeoForge extension). The M keybind sets a
  `selectorRequested` flag, server replies with `TerminalListPacket`, the packet handler
  pushes the GUI layer. `Screen.onClose()` → `popGuiLayer()`: pops back to whatever was
  underneath (terminal GUI stays open server-side; from the world it closes to the world).
  Keybind clicks do NOT fire while a screen is open (`KeyboardHandler` gates on
  `screen == null`), so in-GUI M is caught via `ScreenEvent.KeyPressed.Pre` + cancel.
  Side buttons on Tom's screens remain as an in-place shortcut only.
- **Fallback cycle keybinds**: `cycle_terminal_next` / `cycle_terminal_previous` are
  registered with `InputConstants.UNKNOWN` (unbound by default). `consumeClick()` polling
  in `ClientTickEvent.Post` (no screen) + `ScreenEvent.KeyPressed.Pre`
  `isActiveAndMatches(InputConstants.getKey(code, sc))` (container screens; UNKNOWN-bound
  mappings never match). Server rule = `Selection.cycle` (bound-list order, wraps,
  in-range only) — mirrored dependency-free in `tools/UWT2_Selection.java`.
- **Never clear the client terminal list on `ScreenEvent.Closing`** (fires mid close→open
  swaps). The list is a cache replaced by every `TerminalListPacket`.
- **Side-button overlay lives LEFT of the panel only** (`TerminalOverlay.computeLayout`):
  Tom's Storage screens are 194 px wide (not vanilla 176) and `imageWidth`/`imageHeight`
  have no public getter, so the layout may only use `getGuiLeft()`/`getGuiTop()`. Left
  column → shrunk left column (≥ `MIN_BUTTON_WIDTH`) → strip above the panel. The left
  margin is the one region guaranteed empty for every panel size; a right column based
  on an assumed 176 would overlap Tom's scrollbar.
- **GUI layers need mouse care**: `pushGuiLayer` does NOT release the grabbed mouse;
  open from the world with `setScreen`, push as a layer only over an existing screen
  (`TerminalOverlay.openAsLayer`).
- **`TerminalListPacket`/`StockSummaryPacket` must be registered `playToClient`**;
  `RefreshListPacket`/`ActivatePacket`/`OpenSelectorPacket`/`OpenActivePacket`/
  `CyclePacket` `playToServer`.
- Switching terminals while a terminal GUI is open re-runs `state.useWithoutItem`, which
  re-opens Tom's menu in place. If that open is lost, the `ActivationRetry` watchdog
  (driven from `pollKeybinds`, 20-tick deadline, max 2 attempts) re-sends the activation.
- **Create stock ticker menus are reach-limited by Create itself** (verified against
  Create mc1.21.1 source): `MenuBase.stillValid` → `SmartBlockEntity.canPlayerUse` →
  `player.canInteractWithBlock(worldPosition, 8)`; `ServerPlayer.doTick` closes any menu
  whose `stillValid` goes false, so a far ticker's GUI showed for exactly one frame.
  **`mixin/StockTickerReachMixin` now fixes this**: `@Inject` at the HEAD of
  `SmartBlockEntity.canPlayerUse` returns `true` — but only for
  `StockTickerBlockEntity` (matched by class *name*, since Create is optional) and only
  while Create's own precondition still holds (`level.getBlockEntity(pos) == be`), so a
  broken/replaced ticker or an unloaded chunk is still refused. Everything else — locking
  (`behaviour.mayInteract` runs before `openMenu`), other Create machines, vanilla — is
  untouched. `StockTickerProxy.openRemote` skips its 8-block pre-check only when the patch
  is live (`StockTickerReach.isActive()`); otherwise it keeps the pre-check and falls back
  to `StockSummaryPacket` → `StockSummaryScreen` (lines read from `getRecentSummary()
  .getItemMap()`, `Map<Item, List<BigItemStack>>`, public fields `stack`/`count`).
- **Mixin plumbing (moddev 1.0.17 has no mixin tooling, and that's fine):**
  - Config registered in `neoforge.mods.toml` via `[[mixins]] config=...`; JSON has
    `"required": false`, `"plugin": ...MixinPlugin`.
  - Target is a *mod* class → `@Mixin(targets="…SmartBlockEntity", remap=false)`: mod names
    are never obfuscated (only `net.minecraft` is), so no refmap for Create is needed and
    the mod compiles without a Create dependency.
  - `@Inject(method="canPlayerUse", …, require=0, expect=0)` so a future Create change is a
    warning, not a crashed client.
  - `UltraWirelessTerminalMixinPlugin` gates on Create being present (resource lookup with
    `initialize=false` — never run Create statics this early) and, in `postApply`, **scans
    the transformed `ClassNode` for a real call to the callback method** before setting the
    system property. `require=0` means a silently-skipped injector would otherwise leave a
    copied-but-uncalled method and a false "active" reading.
  - Cross-classloader signalling uses JVM **system properties**
    (`uwt2.stockTickerReachRelaxed` / `uwt2.disableStockTickerMixin`): the transformed
    `SmartBlockEntity` and the mod-loaded `StockTickerProxy` may not share a classloader,
    so a static field would not be shared. Pure decision logic lives in
    `StockTickerReach` (no Minecraft types → unit-tested in `StockTickerReachTest`).
  - Build deps: `repositories { maven neoforged }` + `compileOnly
    'net.fabricmc:sponge-mixin:0.15.2+mixin.0.8.7'` + `compileOnly asm-tree` — mixin is on
    NeoForge's own POM (`compile` scope) at runtime, so nothing is shipped in the jar
    (verified: 0 `org/spongepowered` / `org/objectweb` entries).
  - Verified against Create source: `canPlayerUse` is declared **only** on
    `SmartBlockEntity` (`StockTickerBlockEntity → StockCheckingBlockEntity →
    SmartBlockEntity → CachedRenderBBBlockEntity → SyncedBlockEntity → BlockEntity`), so
    the patch is inherited and nothing shadows it; the `(BlockEntity) (Object) this` cast
    is therefore legal. Keep `StockSummaryScreen` as the fallback for when the patch is
    not live (Create absent / property disabled / verification failed) — do not delete it.
  - Live updates at range are **not** gated by reach (checked): `StockKeeperRequestScreen`
    re-requests every 15+ client ticks via `LogisticalStockRequestPacket`, whose
    `applySettings` calls `getRecentSummary().divideAndSendTo(player, pos)` — no proximity
    filter — with `maxRange() = 4096` on that packet. So the real ticker GUI keeps
    refreshing remotely (beyond ~4096 blocks it keeps the last snapshot instead).
- **1.21.1 specifics**: `KeyMapping` lives in `net.minecraft.client`, `InputConstants` in
  `com.mojang.blaze3d.platform` (`InputConstants.UNKNOWN` = the unbound key);
  `IPayloadContext` has **no** `minecraft()` method — use `Minecraft.getInstance()` inside
  `enqueueWork`; `Component.append` is on `MutableComponent` (`Component.literal` already
  returns it); NeoForge GUI layers: `IMinecraftExtension.pushGuiLayer/popGuiLayer`.

## Status (at time of writing)

- v2 complete: `./gradlew test` green (**66 tests, 0 failures**), `./gradlew build` green,
  jar contains no `net/minecraft` entries. `tools/UWT2_Selection.java` self-test: 15/15.
- v1 `ultra_wireless_terminal` remains untouched in its own folder.
- In-game verification not possible in the sandbox; GUI behavior is reasoned from
  Tom's Storage sources (`AbstractStorageTerminalScreen`, `NetworkHandler.handleTermServer`)
  and NeoForge event APIs verified against the 21.1.251 jars.
