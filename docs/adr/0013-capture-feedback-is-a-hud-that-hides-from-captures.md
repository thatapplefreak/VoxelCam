# Capture feedback is a HUD that hides itself from captures

Up to 2.4.0, capture feedback went to chat: "saved as", "please wait", "failed" and why a big
screenshot or burst was refused. From 2.5.0 all of it goes to a small HUD in the top-left corner
(`CaptureStatus`, drawn by `CaptureStatusHud`), and `ChatMessages` is gone.

Chat said nothing with no player, which is the normal case from the title screen. The repo's rule
was already that new feedback belongs in the GUI. Running two channels also meant every new
message needed a decision about which one it went to. The cost is scroll-back: a notice cannot be
re-read once it fades. Notices that carry a file name or a reason are held longer to make up for
it. `/bigscreenshot` still replies through its command source, because a command's reply belongs
where the player typed it.

Two consequences look like mistakes without this record:

- **The HUD is blank on every frame a capture reads back.** HUD extraction runs before the frame
  is drawn, and every capture path reads the frame back after it is drawn, just before the blit.
  Without this, a notice would be burnt into every burst frame after the first, the big screenshot
  itself, and the next shot taken within a few seconds of the last "saved as". At the head of each
  frame, `CaptureStatus.beforeFrame()` asks each path whether it will read this frame back.
  Over-hiding costs a frame of a notice, so every condition errs wide.
- **It is drawn from a mixin on `Hud`, not a Fabric `HudElement`.** Fabric's elements are drawn
  inside the F1 branch. F1 is where players compose screenshots, and as the only feedback channel,
  a HUD hidden there would leave a several-second big screenshot with no sign of life at all.

One path is not covered. A `Screenshot.grab` that VoxelCam does not defer reads the last presented
frame on the spot, so a notice on screen at that moment is in the file. That covers a menu key
rebound off F2, another mod calling `grab`, and F2 over an open screen. Chat was burnt into those
frames in exactly the same way before.
