# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

VoxelCam is a **client-only Fabric mod** for Minecraft 26.2: an in-game screenshot browser with
rename/delete, keyless sharing, and oversized capture. Version 2.0.0 was a ground-up port of a
LiteLoader mod; the pre-2.0 implementation (Twitter/Reddit/Facebook/Imgur uploaders, an image
editor, a settings panel, a "big screenshot" capture mode) lived in `src/com/` and was **deleted but
preserved in git history** —
`git show 95c1b19:src/com/thatapplefreak/voxelcam/<path>` is the reference when porting anything that
was left behind.

## Commands

```bash
./gradlew build          # compile + jar (build/libs/voxelcam-<version>.jar)
./gradlew runClient      # dev client with the mod loaded
./gradlew vscode         # regenerate .vscode/launch.json (Loom-owned; hand edits are lost)
```

**Tests exist and are the first thing to run.** `./gradlew test` is a plain JUnit suite over the
logic that runs without a live client — mostly Minecraft-free classes, though `Util.OS` was checked
and does load outside the game; `./gradlew runClientGameTest` launches a real client and runs
the Fabric client game tests in `src/gametest/java`, which cover the title-screen and pause-screen
buttons, the manager's render path, both capture paths (including an in-world oversized capture
asserted at exactly 2x the window), the share popup, the rename dialog's focus, its failure
path, and the selection following a rename, the settings screen and the config file it writes, and
the automatic-capture path from an armed moment to a tagged PNG.

**`./gradlew build` does not run the client game tests.** `check` pulls in `test` and vanilla's
server-side `runGameTest`; `runClientGameTest` is outside `build` entirely, and CI
(`.github/workflows/`) gets both suites only by invoking `test`, `runClientGameTest` and `build` as
three separate steps. A green `build` locally says nothing about the client tests — run the task by
name.

The client game test API is **not bundled in fabric-api** and is pinned separately in
`gradle.properties` as `client_gametest_version`; its `+515ac5339e` build suffix is the one 26.2's
bundled fabric-api modules carry. `fabricApi { configureTests { ... } }` is what creates the
`gametest` source set and the `runClientGameTest` task.

`splitEnvironmentSourceSets()` leaves the `test` source set extending `main`, which holds only
resources — `build.gradle` adds the client output to its classpath explicitly. Without that the
tests compile against nothing and the task silently stays `NO-SOURCE`, so check reported test
counts rather than the exit code.

`CatboxUploaderTest` stubs catbox with a `com.sun.net.httpserver` server on loopback rather than
touching the network, which is also the only way to reproduce its refusal-as-HTTP-200. Where a
class wraps something untestable, the pattern has been to extract a package-private seam beside it
(`CatboxUploader.upload(File, URI)`, `NativeShare.copyTo`/`targetPath`/`revealCommand`,
`ScreenshotHandler.writeOrDiscard`, `BigScreenshot.deferRestore`/`beginReadback`/`completeReadback`,
`ScreenshotImageCache.dispositionOf`/`isLoading`, `VoxelCamIO.nameCollides`/`selectionFor`,
`AutoCapture.gateFor`/`shouldArm`/`markVisited`, `VoxelCamConfig.snapshot`) rather
than mock the world. The last two pairs live on `VoxelCamIO` rather than in the popup that uses them
for exactly this reason.

`AutoCapture.gateFor` takes a `ScreenKind` rather than a `Screen` for the same reason, and
`BossBarTracker` holds no Minecraft types at all — `BossBarSignal` flattens the packet's
`Component` name to a `String` on the way in, which is what leaves the kill-detection rule
reachable from a no-client suite.

`SharePopupTest` presses only "Copy file path". The other three targets each escape a test: the save
dialog blocks on a native window, revealing spawns the platform file manager, and the link button
would upload to the real catbox.

**Still untested, and not by oversight:** the native Save-As dialog itself, the real `open -R` /
`explorer.exe` reveal and the `Util.OS.openFile` fallback it lands on, and the upload-result branch
of `SharePopup` (which would need an injectable endpoint the popup does not have). Those are manual
checks before a release.

**So are all four automatic triggers' own hooks.** `MomentCaptureTest` calls `AutoCapture.fire`
directly, because earning an advancement, killing a boss, dying and walking a portal are
server-driven events a client game test cannot stage — staging them would be testing Minecraft. So
`ToastManagerMixin`'s `instanceof AdvancementToast` branch, `BossBarSignal`'s three overrides, and
the `watchDeath`/`watchDimension` tick watches have never executed under test, even though the
injectors themselves are proven to resolve (`defaultRequire: 1` fails the launch otherwise). Earn
an advancement, die, portal and kill a boss by hand before a release; watch the death path in
particular, since `DEATH_PENDING_TIMEOUT_FRAMES` is the one constant in the design picked without
measurement and its failure is silent.

**A `Checkbox` cannot be clicked by a client game test.** `clickScreenButton` resolves the key to a
string and then walks the screen's renderables, but its matcher handles `Button` and `CycleButton`
and nothing else — a checkbox is an `AbstractButton`, passes the outer filter, and is then silently
skipped. That is why `GuiAutoCaptureSettings`' per-trigger toggles are `CycleButton`s: the matcher
reads a cycle button's *name* (the plain translation) rather than its rendered "Death: ON" label, so
the label can carry live state and still be findable. The related rule is that a plain `Button`
whose label carries a substitution is equally unfindable — see the big-screenshot size button in
`SettingsScreenTest`, which is only asserted on, never pressed. `copyPath` *is* covered, end to end through the real GLFW clipboard, in
`SharePopupTest`, and `NativeShare.reveal`'s choice between revealing, opening the folder and giving
up is covered in `NativeShareTest` by running harmless commands with known exit codes.

Anything a test cannot express still means running `runClient` and looking at the result. The usual
pattern for that is to temporarily add a tick counter in `VoxelCamClient` that calls
`client.setScreenAndShow(...)` and then
`Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget(), img -> img.writeToFile(file))`,
run the client, read the PNG, and remove the scaffolding before finishing. Prefer adding a game
test instead where one can express the check.

## Environment gotchas

**Target Java 25.** Minecraft 26.1 onward requires and bundles Java 25, so `options.release` and
`fabric.mod.json`'s `depends.java` both sit at 25 and a release jar should be class version 69.
This was the opposite before 2.2.0, when the target was 1.21.11 and Java 21 — publishing a jar
compiled for the wrong one is rejected by Fabric Loader on a stock install while still running fine
in dev, because Loom inherits whatever `JAVA_HOME` it was given. Worth a `javap -v` check before
publishing either way.

Newer JDKs are fine for *running* Gradle. If a build ever fails with
`error: release version N not supported`, the JDK running Gradle is older than the target — in
VS Code that is usually the Java extension's bundled JDK, pinned via `java.import.gradle.java.home`
in the gitignored `.vscode/settings.json`.

**Minecraft is unobfuscated from 26.1 onward, so there are no mappings and `javap` is the reference.**
Yarn is gone — its last release ever was `1.21.11+build.6` — and there is no `mappings` line in
`build.gradle` at all. The vanilla client jar is the source of truth for any API question:

```bash
# the client jar for the version in gradle.properties
URL=$(curl -s https://piston-meta.mojang.com/mc/game/version_manifest_v2.json \
  | python3 -c "import json,sys;d=json.load(sys.stdin);print([v['url'] for v in d['versions'] if v['id']=='26.2'][0])")
curl -s "$(curl -s "$URL" | python3 -c "import json,sys;print(json.load(sys.stdin)['downloads']['client']['url'])")" -o mc.jar
javap -p -classpath mc.jar net.minecraft.client.gui.components.Button
```

`javap` without `-p` hides private members, which is how the frame method and the window size
setters look "removed" when they are only non-public.

## Source layout

Four source sets, none of which is where a newcomer first looks:

```
src/main/resources/     fabric.mod.json, lang, textures   <- no java at all
src/client/java/        every line of the mod
src/client/resources/   voxelcam.mixins.json
src/test/java/          JUnit, no game
src/gametest/java/      client game tests
src/gametest/resources/ fabric.mod.json for the test mod (id voxelcam-gametest)
```

**`src/main/java` does not exist, and that is not an oversight.** Loom's
`splitEnvironmentSourceSets()` is on, which splits code into a common `main` and a client-only
`client`. VoxelCam is client-only (`"environment": "client"`), so everything lands in `client` and
`main` is left holding resources — `fabric.mod.json` and the assets have to live there because
`main` is the primary resource root that ends up at the jar root. `voxelcam.mixins.json` sits in
`client/resources` instead, next to the two mixins it registers.

Three consequences worth recognising rather than re-diagnosing:

- `compileJava` is `NO-SOURCE` and `build/classes/java/main` never exists, so the
  "Class path entries reference missing files" warning at launch is expected, not a regression.
- The `test` source set extends `main`, so it sees no code until `build.gradle` puts the client
  output on its classpath explicitly.
- Mod code is imported as `com.thatapplefreak.voxelcam.client.*` even though nothing is under a
  `common` package, because the whole mod is the client half.

**The split is kept deliberately.** For a client-only mod it buys nothing at runtime, and collapsing
everything into `src/main/java` would remove both the launch warning and the test classpath wiring.
It stays because it is the layout Fabric's own 26.2 example mod ships, so it is what anyone who has
seen another Fabric mod expects — and rearranging every file changes nothing a player can observe.
Worth revisiting some time that is not immediately before a release.

## Architecture

**Capture** — `ScreenshotRecorderMixin` injects at `HEAD` of `Screenshot.grab` and cancels vanilla's
save, handing off to `ScreenshotHandler`, which names the file via `ScreenshotNamer` and writes it.

**Oversized capture** — Shift+F2 takes a "big screenshot"; `/bigscreenshot <size>` (aliased `/bs`)
sets how big. `BigScreenshot` is a state machine spanning two frames, driven by two
`MinecraftClientMixin` injections into `renderFrame(Z)V`: at `HEAD` it resizes, and just before
`GpuSurface.blitFromTexture(...)` it reads the finished frame back.

26.2 split the old single frame method: `runTick` keeps the game tick, `renderFrame` runs from
acquiring the surface through presenting it, and both injections belong in the latter. The present
is no longer `RenderTarget.blitToScreen` — that method is gone — but a blit of the main render
target's texture onto the window's swapchain surface.

The resize is `Window.setWidth/setHeight` (which write `framebufferWidth/Height`, so they are the
framebuffer setters despite the names) followed by **both** `GameRenderer.resize` and
`Minecraft.framebufferSizeChanged()`. On 26.x `framebufferSizeChanged` only recalculates the GUI
scale; nothing in `Minecraft` resizes the main render target for you, so omitting
`GameRenderer.resize` leaves the frame rendering at the old size. `framebufferSizeChanged` is the
`WindowEventHandler` entry point GLFW's own callback uses; `resizeGui` is a narrower GUI-only path
and is **not** a substitute.

Five things here are load-bearing and were each found the hard way:

- **No restore may run while a readback is outstanding, on either path.** `copyTextureToBuffer` does
  its `glReadPixels` immediately but finishes in a `queueFencedTask` that runs next frame, and
  restoring calls `RenderTarget.resize` → `destroyBuffers()`, which would close the texture it is still
  reading. So the success path restores *inside* the readback consumer, which runs during
  `executePendingTasks()` before the next frame's clear. Two corollaries that each cost a bug:
  `beforeBlit`'s failure path may **not** restore on the spot either (vanilla has
  `getColorTextureView()` on the stack there and `blitFromTexture` never checks `isClosed()`), so it
  sets `RESTORE_PENDING` and the next frame's head does the resize; and `STALE_FRAMES` applies **only
  to `CAPTURING`**. `AWAITING_READBACK` has no frame budget at all — only the consumer knows the GPU
  is done, and an oversized window for a few extra frames beats tearing down a texture mid-read.
- **Each readback carries a generation tag, and only the matching one may finish the capture.**
  Consumers arrive from a fenced task with no other way of telling whether the capture they were
  issued for is still in flight, so finishing on the strength of the state alone lets a late consumer
  end somebody else's capture and restore the window to a size the newer one has not saved yet.
- **Clamp to `RenderSystem.getDevice().getDeviceInfo().limits().maxTextureSize()` yourself.**
  `RenderTarget.createBuffers` (was `initFbo`) *throws* above it, and the window's target does not
  override `resize`, so its forgiving size search never runs on this path.
- **A second request mid-capture must be refused,** and the saved window size snapshotted only on
  the `REQUESTED → CAPTURING` transition. Otherwise the saved size is overwritten with the oversized
  one and every restore path leaves the window permanently huge.
- **Captures are gated on `currentScreen == null && world != null`** — the modern
  `ScreenshotIncapable`. Resizing runs `Screen.resize`, which the manager turns into a full
  `rebuildWidgets`, and with no world `ChatMessages` is silent, so a multi-second freeze would come
  with no explanation. The gate has to be checked **twice**: `request()` runs from
  `RenderSystem.pollEvents()`, `beginCapture()` a whole tick later at the head of `renderFrame`, and a
  disconnect or a screen opening in between would land the resize in exactly the state it forbids.

The oversized frame is still presented for exactly one frame, so a single zoomed-corner frame is
expected and not a bug. `BigScreenshot` holds the size in a session static, which
`VoxelCamConfig` now persists and restores at startup — it was genuinely session-only, never
written to disk, up to and including 2.3.0. PNG
encoding runs on `Util.ioPool()`, so `ScreenshotHandler.saving` clears in that task, not
at the call site, and its chat feedback is bounced back through `client.execute`.

**Automatic capture** — four vanilla-visible signals arm a single plain-size screenshot nobody
pressed a key for: an advancement toast, a boss bar emptying and disappearing, the player's own
death, and a first arrival in a dimension. `AutoCapture` is the dispatcher, `MomentTrigger` the enum
the dispatcher and the settings screen both iterate (so a trigger is never named individually), and
`MomentTag` the PNG tags — `voxelcam:trigger`, `voxelcam:momentsubject` — that let the manager say
afterwards why a file nobody asked for exists. All four ship **on**, each individually toggleable;
the issue originally specified off-by-default, and that was reversed deliberately because
"off by default, re-enable every launch" defeats the point of catching a moment automatically.

Every trigger does the same three things — notice a signal, ask whether it is allowed, arm the next
frame — and only the noticing differs. That is what keeps "one integration point per trigger type"
from becoming one mechanism per trigger. Two arrive from mixins, two are watches in
`AutoCapture.tick`:

- `ToastManagerMixin` at `HEAD` of `addToast`, filtering for `AdvancementToast` and reading the
  holder through `AdvancementToastAccessor` (the field is private with no getter). The toast is the
  hook rather than `ClientAdvancements` because vanilla has already done the filtering by then: it
  only builds one when progress has just completed, the display asks for a toast, and the packet was
  **not** the reset one — which is what keeps the whole advancement list a server sends at login
  from firing a screenshot per entry on every join. Nothing here names a mod or an advancement id,
  so any content mod granting one through the vanilla system arrives with no adapter.
- `BossHealthOverlayMixin` at `HEAD` of `update`, handing the packet to `BossBarSignal`.
  `ClientboundBossEventPacket.Handler` is a public interface whose methods all have defaults, so
  `packet.dispatch(...)` reaches add/progress/remove with no accessor and no reflection.
  **`HEAD`, not the more obvious `TAIL`:** the moment is the *removal*, and whether it was a kill
  depends on the bar's progress just before it vanished — vanilla drops the entry, and that
  progress with it, while handling the packet, so running afterwards leaves nothing to judge by.
  `BossBarTracker` keeping its own copy is the point, not duplication.
- Death and new-dimension need no mixin: both are plain client state.

Four things here are load-bearing:

- **The gate drops rather than defers.** An automatic capture that fires at the wrong moment is
  worse than one that never fires — a shot of the inventory screen the player opened ten seconds
  later is not the moment, and a feature producing those is one nobody leaves switched on. So a
  pending capture is held only when the obstruction will clear on its own (a `LevelLoadingScreen`)
  and dropped when it is the player's own doing (any other screen), or when the level or player is
  gone. This is a *policy* gate, not the technical one `BigScreenshot` and `Burst` need: a
  window-size capture resizes nothing, so none of these states is unsafe to shoot in.
- **The pending countdown does not run under a `LevelLoadingScreen`.** That screen is already
  bounded by its own completion, so counting frames against it only races chunk-load speed — and
  losing that race fails *silently*, with a cold Nether portal producing no screenshot and nothing
  said about why. A load that never finishes ends in a disconnect, which `forgetSession()` handles.
- **`DEATH` waits for `DeathScreen` rather than racing it.** `AutoCapture.tick` runs at
  `END_CLIENT_TICK`, but the screen is opened from `handlePlayerCombatKill`, a server-driven packet
  a variable number of ticks later. Shooting on the first open frame would give a clean shot or the
  red-overlay shot depending on latency — the same trigger, two different screenshots. So `DEATH`
  alone inverts two rows of the gate: no screen means WAIT, `DeathScreen` means CAPTURE.
  `DeathScreen` draws the world behind itself rather than replacing it, so the moment is still in
  the shot.
- **The cooldown is global, charged at arm time, and handed back on a drop.** One automatic capture
  per window whatever caused it: first trigger in a window wins, the rest are dropped silently,
  because a cascade of advancements a second apart is one moment to the player and not five files.
  Charging at arm time is what makes the first one win; handing the window back on a drop is what
  stops a moment that never became a screenshot from spending it. It is clamped on read, not
  trusted from the file — a hand-edited zero would turn a cascade into a screenshot per toast.

`ScreenshotHandler.captureMoment` refuses **silently** rather than reusing `captureNow`, whose
refusal path sends `voxelcam.savingpleasewait` to chat: nobody asked for this screenshot, so nobody
wants to be told at length why it did not happen. `MomentTag` threads through
`saveCapturedImage`/`write`/`embedMetadata` as a fourth nullable parameter, kept separate from
`BurstFrame` rather than bagged with it because `burst` carries *behaviour* (completion accounting,
chat suppression) while `moment` carries only tags and a chat key.

**Manager UI** — `GuiScreenShotManager` is the hub: `ScreenshotListWidget` (rows) on the left,
preview on the right, actions along the bottom. `VoxelCamIO` owns the file list, current selection,
rename, and delete. `ScreenshotMetadata` caches per-file dimensions/size/display names, the
embedded capture context, and the starred flag. Everything the extract pass shows goes through it —
nothing else in the GUI calls `Favorite` or `PngDimensions` itself, and `VoxelCamIO.isSelectedFavorite`
stays uncached only because the game test wants the flag from the file rather than from the cache.

Five invariants that are easy to break:

- **Full-size previews are capped in the cache, not at the call site.** `MAX_FULL_SIZE` of them stay
  resident, evicted least recently used first as a new one is registered; `get` on a hit is what
  marks recency, so what the preview is drawing is never the entry that falls out. Thumbnails are
  deliberately uncapped — the list only asks for the rows the viewport shows. The bound belongs here
  because `LOADED` is the only handle on a registered id: a caller that dropped its own reference
  would strand the texture rather than free it.
- **`ScreenshotImageCache` decodes off-thread but GPU uploads must happen on the render thread.**
  `GuiScreenShotManager.render()` calls `ScreenshotImageCache.uploadPending()` first for that reason.
  Its `extractRenderState()` must **not** call `extractBackground()` —
  `Screen.extractRenderStateWithTooltipAndSubtitles` already does, and the blur pass throws
  "Can only blur once per frame" if repeated.
- **A decode already running cannot be cancelled, so it is tagged and discarded instead.** `IN_FLIGHT`
  maps each key to a claim number rather than being a bare set, and every decode carries the cache
  `generation` it was submitted under; `dispositionOf` is asked twice, on the loader thread before
  queueing and on the render thread before uploading. Uploading a stale result registers a texture
  nothing holds a handle on — `LOADED` is the only one — stranding it in `TextureManager` for the
  session.
- **Popups (`RenamePopup`, `DeletePopup`, `SharePopup`) return via `client.setScreenAndShow(parent)`.** The
  manager overrides `repositionElements()` to `rebuildWidgets()` so it rebuilds — that is what picks
  up files renamed or deleted while a popup was open, not just resize handling.
- **That rebuild is also why the manager's own `selected` field is not authoritative.** It predates
  whatever the popup just did, and after a rename it names a file that no longer exists, so `init()`
  and `refreshFiles()` both resolve through `VoxelCamIO.selectionFor`. Falling back to the head of the
  list instead moves the player onto whichever screenshot the sort puts first — the one the next
  Delete would be aimed at.

**Sharing** — `SharePopup` offers four targets, none needing credentials: `NativeShare.saveCopy`
(LWJGL `TinyFileDialogs` native Save-As, run on `Util.ioPool()` because it blocks and
drives AppleScript on macOS), `NativeShare.revealInFileManager` (`open -R` / `explorer /select,`,
falling back to opening the parent directory), `NativeShare.copyPath` (GLFW clipboard, **text only**),
and `CatboxUploader` (catbox.moe, no key; it signals refusals with HTTP 200 plus an error body, so the
response is validated by checking for a `https://` prefix). `MultipartBody` exists because
`java.net.http` ships no multipart publisher.

The image is never read into the heap: `MultipartBody.streamFile` hands back only the framing either
side of the file part, and `CatboxUploader` splices the file in with
`BodyPublishers.concat(ofByteArray, ofFile, ofByteArray)` so the read happens on the HTTP client's
executor rather than on the thread that pressed the button. That leaves `MultipartBody.addFile`
without a production caller — it stays deliberately, as the oracle
`MultipartBodyTest.streamedFramingSplicesBackIntoTheBufferedBody` compares the streamed halves
against, which is what keeps one set of byte-for-byte framing tests covering the shipped path.
`ofFile` checks existence eagerly, so a missing file still fails the future before any request is
sent.

**Title-screen button** — `VoxelCamClient` registers `ScreenEvents.AFTER_INIT` and appends a
`PhotoButton` via `Screens.getWidgets(screen).add(...)`. It goes in vanilla's row of square icon
buttons (friends, language, accessibility), found **by shape at runtime** — square and
`PhotoButton.SIZE` — because those vanilla button classes are not public.

Vanilla centres that row, so the whole row is re-laid-out rather than appended to: the existing
icons shift left by half a slot and the camera takes the new right-hand end. Appending without
moving them leaves the group off-centre by half a slot, which looks fine in isolation and wrong
under the menu above it. `TitleScreenButtonTest` guards exactly that. The slot pitch is read from
the row's own spacing, and there is a fallback to the old position past the bottom full-width row
for menu-replacing mods.

**Config** — `VoxelCamConfig` persists nine settings to `voxelcam.json` (Gson, under
`FabricLoader.getConfigDir()`): burst length, big-screenshot size, the manager's sort mode, its
favourites-only filter, and the five automatic-capture settings (four per-trigger booleans plus
`autoCaptureCooldownSeconds`). Only three mirror an existing session-static holder
(`Burst.getLength()`, `BigScreenshot.getSize()`, `SortMode.current()`). The other six live *only* on
`VoxelCamConfig.current()` — `favoritesOnly` is read in the manager's `init()` and written in
`toggleFavoritesOnly()`, and `MomentTrigger`'s per-constant getter/setter lambdas point straight at
their own flat field — rather than keeping a second copy that could drift. The fields stay flat, and
the lambdas are what buy uniform iteration on top of them, so `voxelcam.json` stays readable and
`GuiAutoCaptureSettings` can still loop over `MomentTrigger.values()`.

**The trap is `saveCurrent()`.** It builds a *fresh* `VoxelCamConfig` and copies the
no-session-static settings across from `current`; miss one and toggling it writes its default
straight back to disk, silently undoing what the player just did. That copying is extracted into the
package-private `snapshot()` seam precisely so it can be tested without `FabricLoader` —
`roundTripsThroughDisk` cannot catch it, since it builds its object directly and never comes through
`saveCurrent()` at all. Add a field, add a line to `snapshot()`, add it to
`snapshotKeepsTheSettingsThatHaveNoSessionStatic`.

Defaults are written as **field initialisers**, not applied after the parse: Gson leaves a field it
finds no key for at whatever the constructor set, so a `voxelcam.json` written before a setting
existed keeps that setting's default instead of reading as `false`/`0`.

`Burst.setLength`, `BigScreenshot.setSize`, and `SortMode.setCurrent` deliberately do **not** call
`VoxelCamConfig.saveCurrent()` themselves — that would touch `FabricLoader.getInstance()` from
`BurstTest`'s and `BigScreenshotTest`'s no-client unit suites, which currently exercise `setLength`
directly. `saveCurrent()` is instead called explicitly from every place a setting actually changes
at the player's hand: the capture menu's outer ring, `BigScreenshotCommand`, and the manager's sort
button and favourites toggle — all of which already need a live client to run at all. `load()` (once,
from `VoxelCamClient.onInitializeClient`) applies the three session-static settings directly and
does not itself trigger a save, since restoring what was already on disk is not a change worth
writing straight back.

A settings screen (`GuiSettings`, reachable from a gear button in the manager, or from Mod Menu if
installed — `VoxelCamModMenu`, `compileOnly` in `build.gradle` so it costs nothing for a player who
does not have Mod Menu) reaches burst length across its full range and every `BigScreenshotSize`
preset, complementing the ring's five-value ladders for each. An "Automatic capture…" row opens
`GuiAutoCaptureSettings` — its own screen rather than four more rows, because `GuiSettings`
positions its rows by hand off `height / 2` with no scrolling and would sit at the edge of the
smallest GUI scale a player can pick.

## Version-specific API traps

These cost real debugging time and are not guessable from the class names:

- **The GUI is retained-mode from 26.x on: widgets no longer draw, they extract render state.**
  `Screen.render` is `extractRenderState(GuiGraphicsExtractor, ...)`, `Button.renderContents` is
  `extractContents`, list entries implement `extractContent`, and the drawing calls are `text`,
  `centeredText`, `blit`, `fill`. Extraction still runs on the render thread, which is why
  `ScreenshotImageCache.uploadPending()` is still safe there.
- **`AbstractButton.extractWidgetRenderState` is `final` and does not paint the button plate.**
  A custom button must call `extractDefaultSprite(context)` itself inside `extractContents`, the
  way vanilla's own `Button` does before drawing its label.
- **The current screen is `Minecraft.gui.screen()`.** There is no `screen` field on `Minecraft`
  any more, and no getter that returns one.
- **`Minecraft` has no `getMainRenderTarget()`.** It is `Minecraft.gameRenderer.mainRenderTarget()`;
  `Minecraft.windowSurface()` is the swapchain, which is a different thing.
- **To focus a text field on open, override the no-arg `setInitialFocus()`** — do not call
  `setInitialFocus(field)` from `init()`. Both `Screen.init(int,int)` and `rebuildWidgets()` invoke
  the no-arg hook *after* `init()` returns, and after a keyboard input it tab-navigates forward from
  the already-focused field onto the next active widget, leaving the dialog inert. Vanilla's own
  screens (`DirectJoinServerScreen`, `AnvilScreen`, `CreateWorldScreen`) all override the hook.
  Keeping both would make the override a no-op: `AbstractWidget.nextFocusPath` returns null for an
  already-focused widget.

## Conventions

Files are **tab-indented**. Comments explain *why* — a constraint, a vanilla behaviour, a rejected
alternative — not what the line does; match that rather than annotating mechanics.

**`ChatMessages` silently does nothing when `client.player == null`**, which is the normal case since
the manager is reachable from the title screen. New user-facing feedback belongs in the GUI, not chat.
A refused rename keeps `RenamePopup` open with the typed name; a refused delete goes to the manager's
details line via `reportDeleteResult`. Both add strings to **both** `en_us.json` and `en_pt.json`.

**Anything parsed or written by the machine takes `Locale.ROOT` explicitly.** The default locale folds
`"IMAX"` to a dotless `ı` under `tr`, and a bare `SimpleDateFormat` writes Arabic-Indic digits or a
Buddhist year into a filename that later has to match an ASCII `\d` regex. Display strings that should
follow the player's system locale are the exception, not the rule.

`GuiScreenShotManager` splits its content area on the golden ratio (`preview : list == φ : 1`) with a
`MIN_LIST_WIDTH` floor that wins below ~430px of GUI width.

## Known dead ends

- **"No config file exists" is no longer true, and should not be treated as a default to fall back
  on.** `VoxelCamConfig` was deleted once nothing read it (2.0.0-era), and big-screenshot size
  deliberately stayed session-only through 2.3.0 rather than bringing it back. That held only as
  long as nothing needed to persist — the capture menu's outer ring (2.4.0) gave burst length and
  big-screenshot size the first two settings actually worth remembering across a restart, which is
  the entire justification for `voxelcam.json` existing again. Automatic capture (also 2.4.0) then
  made it load-bearing rather than merely convenient: its per-trigger toggles have no home but the
  config file, and were the one thing the feature was blocked on while none existed. See the
  Architecture section's **Config** entry for how it works. The version pins in `gradle.properties` are still build-time
  only and are not runtime config.
- **There is no data generation.** `configureDataGeneration()` was removed in 2.2.0: no
  `fabric-datagen` entrypoint was ever declared and `src/main/java` has no sources, so its
  `runDatagen` task only ever generated nothing. The mod ships hand-written assets. Do not add the
  call back expecting it to do something on its own.
- **`fabricApi { configureTests { ... } }` is load-bearing** — it creates the `gametest` source set
  and the `runClientGameTest` task, so the block that survived is not the same kind of decoration
  the datagen call was. The `clientGameTest` run config must be tuned in a *separate* `loom` block
  placed after it; naming it in the `loom` block above instead fails with a duplicate-name error,
  because `configureTests` is what creates that run config.
