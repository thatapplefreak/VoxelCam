# Export a burst animation from the files on disk, by reframing, not re-encoding

A burst becomes an animation after the fact, from the manager, not while it is being captured.
`AnimatedPng` reads the frames that are already saved and writes one APNG beside the key. It does
not decode a pixel: the frames share one IHDR, and a PNG's `IDAT` payloads are slices of one zlib
stream. Frame 0's `IDAT` chunks are copied as they are, which makes frame 0 the image any non-APNG
reader shows. Every later frame's payloads become `fdAT` chunks behind a sequence number.

Buffering frames in memory during the burst was the alternative, and the saved files beat it on
three counts:

- **Timing.** A burst is paced by `MAX_IN_FLIGHT`, not by a timer, so its frames are not evenly
  spaced. The `burstoffset` tag records when each frame was actually taken, and that is the only
  honest source of frame delays.
- **Memory.** Every buffered frame is a full-resolution `NativeImage` allocated off-heap. Twenty
  frames at 4K is over 600 MiB, and running out of native memory crashes the game instead of
  throwing an error it can catch. The export holds one compressed frame at a time.
- **Order.** Frames finish writing out of order, while APNG sequence numbers must only go up. The
  files on disk are complete, and their tags give the order.

APNG rather than GIF because GIF is limited to 256 colours, and Minecraft's lighting dithers badly
when quantised down to that.

## Consequences

- The animation is an ordinary `.png`: browsers animate it, and everything else (thumbnails,
  `PngDimensions`, sharing) treats it as a still of frame 0. The manager's preview plays it by
  splitting it back into standalone frames, the same byte reframing in reverse. The
  `voxelcam:animationframes` tag is what marks it as an animation, for the preview and the details
  line.
- Each frame stays up until the next one was captured. At a high frame rate that is a short, fast
  loop. Delays are clamped to at least 20 ms, because browsers handle near-zero delays
  inconsistently. The last frame holds for the average gap so the loop has no stutter.
- Frames of different sizes are refused, never resampled. A burst resized mid-capture cannot be
  exported.
- Discord shows only the first frame of an APNG, and that is where most of these get shared. So
  every export also writes a lossless animated WebP beside the APNG, which is what Share hands
  out. It is a sidecar rather than a replacement: everything in the manager (listing, thumbnails,
  stars, tags, the preview) reads PNG, and the game cannot decode WebP. Rename and delete carry
  the sidecar with its APNG. The WebP does need a real encode, unlike the APNG: `AnimatedWebp` is
  a minimal pure-Java VP8L encoder, since a mod cannot reasonably ship libwebp's native builds.
  Its files are large; better compression is tracked in #70.
