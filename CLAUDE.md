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
asserted at exactly 2x the window), the share popup, and the rename dialog's focus, its failure
path, and the selection following a rename.

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
`ScreenshotImageCache.dispositionOf`/`isLoading`, `VoxelCamIO.nameCollides`/`selectionFor`) rather
than mock the world. The last two pairs live on `VoxelCamIO` rather than in the popup that uses them
for exactly this reason.

`SharePopupTest` presses only "Copy file path". The other three targets each escape a test: the save
dialog blocks on a native window, revealing spawns the platform file manager, and the link button
would upload to the real catbox.

**Still untested, and not by oversight:** the native Save-As dialog itself, the real `open -R` /
`explorer.exe` reveal and the `Util.OS.openFile` fallback it lands on, and the upload-result branch
of `SharePopup` (which would need an injectable endpoint the popup does not have). Those are manual
checks before a release. `copyPath` *is* covered, end to end through the real GLFW clipboard, in
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
expected and not a bug. Sizes are session-only in `BigScreenshot`, never written to disk. PNG
encoding runs on `Util.ioPool()`, so `ScreenshotHandler.saving` clears in that task, not
at the call site, and its chat feedback is bounced back through `client.execute`.

**Manager UI** — `GuiScreenShotManager` is the hub: `ScreenshotListWidget` (rows) on the left,
preview on the right, actions along the bottom. `VoxelCamIO` owns the file list, current selection,
rename, and delete. `ScreenshotMetadata` caches per-file dimensions/size/display names, the
embedded capture context, and the starred flag. Everything the extract pass shows goes through it —
nothing else in the GUI calls `Favorite` or `PngDimensions` itself, and `VoxelCamIO.isSelectedFavorite`
stays uncached only because the game test wants the flag from the file rather than from the cache.

Four invariants that are easy to break:

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

- **No config file exists.** `VoxelCamConfig` was deleted once nothing read it, and the big-screenshot
  size deliberately stayed session-only rather than bringing it back; no `voxelcam.json` is written.
  The version pins in `gradle.properties` are build-time only and are not runtime config.
- **There is no data generation.** `configureDataGeneration()` was removed in 2.2.0: no
  `fabric-datagen` entrypoint was ever declared and `src/main/java` has no sources, so its
  `runDatagen` task only ever generated nothing. The mod ships hand-written assets. Do not add the
  call back expecting it to do something on its own.
- **`fabricApi { configureTests { ... } }` is load-bearing** — it creates the `gametest` source set
  and the `runClientGameTest` task, so the block that survived is not the same kind of decoration
  the datagen call was. The `clientGameTest` run config must be tuned in a *separate* `loom` block
  placed after it; naming it in the `loom` block above instead fails with a duplicate-name error,
  because `configureTests` is what creates that run config.
