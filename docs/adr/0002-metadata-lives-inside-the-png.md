# Store per-screenshot metadata as iTXt chunks inside the PNG

Capture context, provenance, the favorite flag, burst membership and the automatic-capture moment
are all written into the screenshot file itself, as `iTXt` chunks under `voxelcam:*` keywords.
Nothing is kept in a sidecar file or a database. The metadata then survives rename, copy and share
with no bookkeeping. A sidecar keyed by filename would be orphaned by every rename, leaked by every
delete, and lost the moment a player drags the PNG out of the folder. That last case is the one the
data exists for.

## Considered Options

- **Sidecar `.json` per screenshot.** Rejected: `VoxelCamIO.rename()`/`delete()` would have to
  keep it in sync, and nothing outside VoxelCam would.
- **One index file for the whole folder.** Rejected for the same reason, made worse: it goes stale
  when any other program touches the folder.
- **`tEXt` chunks.** Rejected: `tEXt` is Latin-1 only, and world and server names are exactly the
  values that might not be.

## Consequences

- `NativeImage.writeToFile` only emits `IHDR`/`IDAT`/`IEND`, so embedding is a byte splice into
  the file it has already written, not a re-encode.
- Reading a flag means reading the file, so every metadata read goes through a per-file cache
  (`ScreenshotMetadata`). Reading on the render thread per frame was a real regression twice.
- Starring rewrites a file the player already has. That is the one write that changes a
  screenshot after capture, and it preserves the file's modification time so date sorting is
  unaffected.
- The `voxelcam:*` keywords are a format other tools can read and the player's existing files
  already carry. Renaming one strands every screenshot taken before the rename.
