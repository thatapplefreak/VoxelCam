# Save-As follows Minecraft onto SDL, and becomes asynchronous

26.3 moved the client off GLFW onto SDL and dropped `org.lwjgl:lwjgl-tinyfd` from Minecraft's
library set. `NativeShare.saveCopy` was built on `TinyFileDialogs.tinyfd_saveFileDialog`, so the
mechanism behind one of the four share targets stopped existing — not deprecated, not moved,
simply absent from the classpath.

**Save-As is kept, on SDL's own dialog.** `SDLDialog.SDL_ShowSaveFileDialog` is the direct
successor, and `org.lwjgl:lwjgl-sdl` is already a Minecraft library on 26.3, so keeping the feature
costs no new dependency and ships no native of our own.

The consequence is that the dialog is now **asynchronous**. tinyfd blocked its calling thread,
which is why `saveCopy` ran the whole thing on `Util.ioPool()` — the comment there said so. SDL
instead wants to be shown from the main thread and answers through a callback invoked later — SDL
documents the dialog as non-blocking but not which thread answers, so nothing here assumes either.
So the shape inverts: the dialog opens on the calling (render) thread,
and only the copy that follows runs on the IO worker. `saveCopy` still returns
`CompletableFuture<Path>` completing with null on cancel, which is what kept `SharePopup`
unchanged — the popup already routed everything through `whenComplete` and `minecraft.execute`.

Three details of the SDL path are load-bearing, and each is a silent failure if missed:

- **Nothing handed to SDL may live on the `MemoryStack`.** The callback can arrive long after any
  stack frame would have been popped, and SDL does not copy the filters. The filter struct, its two
  strings and the default location are allocated off-heap and freed in the callback, which is also
  the only place the callback can free itself.
- **An empty file list is a cancel; a null list is a failure.** Conflating them turns "the user
  changed their mind" into an error message.
- **SDL's simple entry point carries no title.** The OS default caption shows instead of
  `voxelcam.share.savecopy.title`. Restoring it means `SDL_ShowFileDialogWithProperties` and
  `SDL_PROP_FILE_DIALOG_TITLE_STRING`, which is more surface than a caption is worth. The lang key
  is deliberately left in place and the parameter deliberately left in the signature, so that
  reversing this costs one call rather than a signature change.

## Alternatives rejected

**Dropping the target** would have left three share routes — reveal, copy path, catbox link — and
removed all unverifiable native code in one stroke. It was rejected because Save-As is the only
route that puts the file where the player wants it, including a synced Dropbox/iCloud/OneDrive
folder, and losing a shipped feature on a version bump is worse than carrying code a test cannot
reach. The repo already carries that cost knowingly: the dialog has always been on the manual
pre-release list.

**AWT's `FileDialog`** needs no dependency and no native pointers, which makes it look like the
tame option. It is not: AWT alongside SDL wants the macOS main thread and is a documented hang risk
in games. Trading pointer arithmetic for a deadlock is not an improvement.

## What this leaves untested

The same thing it left untested before, plus the callback. No game test can open a native window,
so the dialog, the cancel path and the free-in-callback discipline are all manual checks before a
release. `copyTo` and `targetPath` remain the tested seams on the far side of the dialog, and they
did not change.
