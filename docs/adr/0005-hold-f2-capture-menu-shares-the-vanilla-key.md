---
status: accepted (supersedes the Shift+F2 big screenshot, removed in `c5a80c6`)
---

# One key for every capture mode: tap F2 for a screenshot, hold it for the capture menu

Every capture mode is reached from `F2`. A tap still takes a plain screenshot exactly as vanilla
does. A hold opens a mouse-aimed capture menu with one wedge per mode. This replaced Shift+F2 for
big screenshots. A modifier per mode does not scale past two modes, and burst capture was about to
be the third. A player who never holds the key sees no change at all.

This needs a second, VoxelCam-owned `KeyMapping` bound to the same physical key as vanilla's
screenshot key. The plain capture also has to move from key-down to key-up, because at key-down
nobody can tell a tap from a hold.

## Consequences

- Vanilla's key map delivers a shared key to exactly one of the two bindings, and which one is not
  ours to choose. So the press is read from every route it can arrive by (the mapping's clicks, its
  down-state, and the platform's raw key state), and arming is idempotent. Two earlier attempts each picked
  one route and were wrong in-game. Only a game test pressing the real key settled it.
- `ScreenshotRecorderMixin` still cancels vanilla's save unconditionally, but it ignores the grab
  only while our binding is held or the menu is armed. Anything else that calls `Screenshot.grab`
  still captures.
- Escape has to be swallowed at the head of key handling while the menu is open. Otherwise the
  pause screen opens first, and a cancel that pauses the game is not a cancel.
- Rebinding the capture menu elsewhere leaves vanilla's `F2` as an ordinary screenshot. The held
  check resolves the key from the binding rather than hardcoding `F2` for that reason.
