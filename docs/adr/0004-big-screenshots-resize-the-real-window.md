# Take big screenshots by resizing the real render target for one frame

A big screenshot resizes the window's own framebuffer and main render target to the requested
size, renders one frame at that size, reads it back, and restores the original size. It spans two
frames, driven from two injections into `Minecraft.renderFrame`. The LiteLoader mod did the same
thing through reflection. The modern pipeline has no verified hook for rendering the world into an
off-screen target of a different size, and the 2.0 port deliberately refused to guess at one.
Resizing the real target reuses vanilla's own render path, so the big screenshot is the frame the
game would draw at that size, not a reimplementation of it.

## Considered Options

- **Off-screen render into a separate oversized target.** Rejected: 26.x's GPU-command pipeline
  offers no verified entry point for it, and a reimplementation could not be checked against what
  vanilla draws.
- **Ship without it.** Done briefly in 2.0 (`f12a200`) and restored in 2.1 (`f1a3a6a`).

## Consequences

- The oversized frame is presented to the window for exactly one frame. A single zoomed-corner
  flash is expected.
- The size has to be clamped to the device's max texture size by hand, since creating the buffers
  throws above it.
- This is a state machine with hazards, and each was found by a bug: no restore while a readback
  is in flight, a generation tag on each readback, the window size saved on only one transition,
  and the capture gate checked twice. `CLAUDE.md` documents each. Changing the approach means
  rediscovering them.
