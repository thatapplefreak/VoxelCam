# VoxelCam

An in-game screenshot browser for Minecraft: capturing screenshots in several modes, finding and
organising them afterwards, and sharing them without an account.

## Language

### Capturing

**Screenshot**:
A single PNG the player took, or that VoxelCam took on their behalf, in the screenshots folder.
_Avoid_: Capture (as a noun for the file), image, photo

**Plain screenshot**:
A screenshot exactly the size of the game window, the same thing vanilla's `F2` produces.
_Avoid_: Normal screenshot, regular screenshot

**Big screenshot**:
A screenshot rendered larger than the window, up to whatever the GPU allows.
_Avoid_: Oversized screenshot, high-res screenshot, panorama, Shift+F2 screenshot

**Big screenshot size**:
The dimensions the next big screenshot will be: a named preset (`4k`, `imax`), a multiple of the
window (`4x`), or exact dimensions (`3840x2160`).
_Avoid_: Resolution, scale

**Burst**:
A run of plain screenshots taken across consecutive frames and kept together as one group.
_Avoid_: Sequence, series, live photo

**Burst length**:
How many frames a burst takes.
_Avoid_: Burst count, burst size

**Frame** (of a burst):
One screenshot within a burst.
_Avoid_: Sub-frame, sub-image

**Key frame**:
The frame that stands for its whole burst in the manager's list.
_Avoid_: Cover, thumbnail, primary

**Set as key**:
Making a different frame the key frame of its burst.
_Avoid_: Promote, make cover

**Capture menu**:
The radial dial that opens while the capture key is held, offering one wedge per capture mode.
_Avoid_: Radial menu, wheel, picker, dial (as the feature's name)

**Tap**:
Pressing and releasing the capture key before the capture menu opens, which takes a plain
screenshot.

**Hold**:
Keeping the capture key down long enough for the capture menu to open.

**Wedge**:
One capture mode's sector of the capture menu.
_Avoid_: Slice, segment

**Outer ring**:
The quick options (burst lengths, big screenshot sizes) that appear past a wedge's edge when the
player aims far enough out.
_Avoid_: Sub-menu

### Automatic capture

**Moment**:
An in-game event worth a screenshot nobody pressed a key for: earning an advancement, defeating a
boss, dying, or arriving in a dimension for the first time.
_Avoid_: Event, highlight, auto-screenshot

**Trigger**:
One kind of moment, which the player can switch on or off on its own.
_Avoid_: Hook, signal

**Cooldown**:
The window after an automatic capture during which further moments are ignored, so a cascade is
one screenshot rather than several.
_Avoid_: Throttle, rate limit

### What a screenshot carries

**Capture context**:
Where a screenshot was taken: the dimension, the coordinates, and the world or server's name. It
travels inside the file.
_Avoid_: Location, tags, EXIF

**Provenance**:
What was installed when a screenshot was taken: the content mods and the shader pack, if one was
active.
_Avoid_: Mod list, loadout

**Favorite**:
A screenshot the player has starred to find again. "Star" and "unstar" are the actions, and
player-facing text says "starred" rather than "favorite".
_Avoid_: Pinned, bookmarked, liked

### Browsing

**Manager**:
The screen for browsing screenshots: a list of rows, a preview, and the actions along the bottom.
_Avoid_: Gallery, browser, viewer

**Preview**:
The large view of the selected screenshot in the manager.

**Filmstrip**:
The strip of a burst's frames beneath the preview, for picking one frame out of a group.

**Sort mode**:
The order the manager lists screenshots in: by date, name, file size or dimensions, each either
way round.

**Favorites only**:
The manager's filter that hides every screenshot that is not a favorite ("Show only starred
screenshots" to the player).

### Sharing

**Share target**:
One of the ways a screenshot leaves VoxelCam: save a copy, reveal it in the file manager, copy its
path, or get a link. None needs an account.
_Avoid_: Uploader, destination

**Link**:
The public URL catbox.moe returns for an uploaded screenshot.
_Avoid_: Upload (as a noun), post

**Reveal**:
Showing a screenshot selected in the platform's own file manager.
_Avoid_: Open folder, show in Finder
