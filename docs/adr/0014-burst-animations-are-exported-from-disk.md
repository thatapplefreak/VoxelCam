# Export a burst animation from the files on disk, storing only what changed

A burst becomes an animation after the fact, from the manager, not while it is being captured.
`AnimatedPng` reads the frames that are already saved, one at a time, and writes one APNG beside
the key. The first frame is stored whole, as the `IDAT` any non-APNG reader shows. Every later
frame is a `FramePatch`: the rectangle that changed since the frame before, with unchanged pixels
inside it transparent and blended over. The same patches go into a WebP copy for sharing.

Buffering frames in memory during the burst was the alternative, and the saved files beat it on
three counts:

- **Timing.** A burst aims for one frame every 100 ms, but skips any slot it cannot make while
  `MAX_IN_FLIGHT` frames are still encoding, and each frame lands on the first rendered frame in
  its slot, so frames are not evenly spaced. The `burstoffset` tag records when each frame was
  actually taken, and that is the only honest source of frame delays.
- **Memory.** Every buffered frame is a full-resolution `NativeImage` allocated off-heap. Fifty
  frames at 4K is over 1.5 GiB, and running out of native memory crashes the game instead of
  throwing an error it can catch. The export holds about three frames' pixels at a time.
- **Order.** Frames finish writing out of order, while APNG sequence numbers must only go up. The
  files on disk are complete, and their tags give the order.

APNG rather than GIF because GIF is limited to 256 colours, and Minecraft's lighting dithers badly
when quantised down to that.

An earlier version copied each frame's compressed data into the APNG without decoding it. That
was simpler, but it made the APNG exactly as large as the frames again. A burst's frames are a
tenth of a second apart, so storing only what changed shrinks a still scene's APNG by more than an
order of magnitude.

## Consequences

- The animation is an ordinary `.png`: browsers animate it, and everything else (thumbnails,
  `PngDimensions`, sharing) treats it as a still of frame 0. The manager's preview plays it by
  splitting it into standalone patches and compositing them. The `voxelcam:animationframes` tag
  is what marks it as an animation, for the preview and the details line.
- The APNG is RGBA, because the patches need transparency and every frame shares one IHDR. The
  first frame's alpha is all 255 and compresses to almost nothing.
- Patches only help where the scene holds still. When the camera moves, nearly every pixel
  changes, and a lossless frame cannot get much smaller than the frame itself.
- Each frame stays up until the next one was captured. Delays are clamped to at least 20 ms,
  because browsers handle near-zero delays inconsistently. The last frame holds for the average
  gap so the loop has no stutter.
- Frames of different sizes are refused, never resampled. A burst resized mid-capture cannot be
  exported.
- Discord shows only the first frame of an APNG, and that is where most of these get shared. So
  every export also writes a lossless animated WebP beside the APNG, which is what Share hands
  out. It is a sidecar rather than a replacement: everything in the manager (listing, thumbnails,
  stars, tags, the preview) reads PNG, and the game cannot decode WebP. Rename and delete carry
  the sidecar with its APNG. `AnimatedWebp` is a pure-Java VP8L encoder, since a mod cannot
  reasonably ship libwebp's native builds.
