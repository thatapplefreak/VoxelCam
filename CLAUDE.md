# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

VoxelCam is a **client-only Fabric mod** for Minecraft 1.21.11: an in-game screenshot browser with
rename/delete and keyless sharing. Version 2.0.0 is a ground-up port of a LiteLoader mod; the pre-2.0
implementation (Twitter/Reddit/Facebook/Imgur uploaders, an image editor, a settings panel, a
"big screenshot" capture mode) lived in `src/com/` and was **deleted but preserved in git history** —
`git show 95c1b19:src/com/thatapplefreak/voxelcam/<path>` is the reference when porting anything that
was left behind.

## Commands

```bash
./gradlew build          # compile + jar (build/libs/voxelcam-<version>.jar)
./gradlew runClient      # dev client with the mod loaded
./gradlew vscode         # regenerate .vscode/launch.json (Loom-owned; hand edits are lost)
```

**There is no test suite** — `test` and `check` are `NO-SOURCE`. Verification means running
`runClient` and looking at the result. A common pattern for UI work is to temporarily add a tick
counter in `VoxelCamClient` that calls `client.setScreen(...)` and then
`ScreenshotRecorder.takeScreenshot(client.getFramebuffer(), img -> img.writeTo(file))`, run the
client, read the PNG, and remove the scaffolding before finishing.

## Environment gotchas

**JDK 25 is mandatory** (`options.release = 25`, `fabric.mod.json` requires `java >=25`). The
VS Code Java extension bundles JDK 21 and will run Gradle with it, failing as
`error: release version 25 not supported` while the terminal build succeeds. `.vscode/settings.json`
pins `java.import.gradle.java.home` to a JDK 25 to prevent this; that file is gitignored, so a fresh
clone needs it re-added.

**`.gradle/loom-cache/minecraftMaven/` can hold more than one Minecraft version.** Reading the wrong
one produces confidently wrong API conclusions. Always pin the jar matching `gradle.properties`
before `javap`:

```bash
# match the minecraft_version from gradle.properties, not just "*.jar"
CP=$(find .gradle/loom-cache/minecraftMaven -path "*/1.21.11-net.fabricmc.yarn*" -name "*.jar" ! -name "*sources*" | tr '\n' ':')
javap -classpath "$CP" net.minecraft.client.gui.widget.ButtonWidget
```

## Source layout

Loom `splitEnvironmentSourceSets()` is on, but **all code lives in `src/client/java`**; `src/main`
holds resources only (`fabric.mod.json`, lang, textures). `compileJava` is `NO-SOURCE` and
`build/classes/java/main` never exists — the "Class path entries reference missing files" warning at
launch is expected, not a regression.

`src/client/resources/voxelcam.mixins.json` registers the single mixin.

## Architecture

**Capture** — `ScreenshotRecorderMixin` injects at `HEAD` of `saveScreenshot` and cancels vanilla's
save, handing off to `ScreenshotHandler`, which names the file via `ScreenshotNamer` and writes it.
Shift+F2 is reserved for the unported oversized-capture mode and currently reports
`voxelcam.bigscreenshotunsupported`.

**Manager UI** — `GuiScreenShotManager` is the hub: `ScreenshotListWidget` (rows) on the left,
preview on the right, actions along the bottom. `VoxelCamIO` owns the file list, current selection,
rename, and delete. `ScreenshotMetadata` caches per-file dimensions/size/display names.

Two invariants that are easy to break:

- **`ScreenshotImageCache` decodes off-thread but GPU uploads must happen on the render thread.**
  `GuiScreenShotManager.render()` calls `ScreenshotImageCache.uploadPending()` first for that reason.
  Its `render()` must **not** call `renderBackground()` — `Screen.renderWithTooltip` already does, and
  the blur pass throws "Can only blur once per frame" if repeated.
- **Popups (`RenamePopup`, `DeletePopup`, `SharePopup`) return via `client.setScreen(parent)`.** The
  manager overrides `refreshWidgetPositions()` to `clearAndInit()` so it rebuilds — that is what picks
  up files renamed or deleted while a popup was open, not just resize handling.

**Sharing** — `SharePopup` offers four targets, none needing credentials: `NativeShare.saveCopy`
(LWJGL `TinyFileDialogs` native Save-As, run on `Util.getIoWorkerExecutor()` because it blocks and
drives AppleScript on macOS), `NativeShare.revealInFileManager` (`open -R` / `explorer /select,`,
falling back to opening the parent directory), `NativeShare.copyPath` (GLFW clipboard, **text only**),
and `CatboxUploader` (catbox.moe, no key; it signals refusals with HTTP 200 plus an error body, so the
response is validated by checking for a `https://` prefix). `MultipartBody` exists because
`java.net.http` ships no multipart publisher.

**Title-screen button** — `VoxelCamClient` registers `ScreenEvents.AFTER_INIT` and appends a
`PhotoButton` via `Screens.getButtons(screen).add(...)`. It **measures the bottom button row at
runtime** rather than hardcoding a position: modern vanilla puts its accessibility button exactly
where the LiteLoader build placed the camera (`width/2 + 104`).

## Version-specific API traps

These cost real debugging time and are not guessable from the class names:

- **`PressableWidget.renderWidget` is `final` and calls only `drawIcon` + `setCursor`.** It does not
  paint the button plate. A custom button must call `drawButton(context)` itself inside `drawIcon`,
  the way `ButtonWidget$Text` does.
- **`ButtonWidget` has a nested class named `Text`.** An inherited member type shadows a single-type
  import, so inside any `ButtonWidget` subclass `Text.translatable(...)` fails to resolve — write
  `net.minecraft.text.Text` in full.

## Conventions

Files are **tab-indented**. Comments explain *why* — a constraint, a vanilla behaviour, a rejected
alternative — not what the line does; match that rather than annotating mechanics.

**`ChatMessages` silently does nothing when `client.player == null`**, which is the normal case since
the manager is reachable from the title screen. New user-facing feedback belongs in the GUI, not chat.

`GuiScreenShotManager` splits its content area on the golden ratio (`preview : list == φ : 1`) with a
`MIN_LIST_WIDTH` floor that wins below ~430px of GUI width.

## Known dead ends

- **No config file exists.** `VoxelCamConfig` was deleted once nothing read it; there is nothing to
  configure and no `voxelcam.json` is written.
- `fabricApi { configureDataGeneration() }` in `build.gradle` is inert — no `fabric-datagen`
  entrypoint is declared and `src/main/java` has no sources, so `runDatagen` generates nothing.
