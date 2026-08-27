VoxelCam
========

VoxelCam is a mod for Minecraft that makes screenshots easier to handle: it
adds an in-game screenshot browser for finding, renaming, deleting, and sharing
the shots you have taken.

Features
--------

* A screenshot manager with search, thumbnails, and a large preview, opened
  with the `H` key in-game or from the camera button on the title screen.
* Rename and delete screenshots without leaving the game.
* Take screenshots far larger than your window — up to whatever your GPU
  allows — with `Shift`+`F2`. Set the size with `/bigscreenshot` (or `/bs`),
  which takes presets like `4k` and `imax`, a multiple of your window such as
  `4x`, or exact dimensions like `3840x2160`.
* Share a screenshot by saving a copy through your platform's native file
  dialog, showing it in your file manager, copying its path, or uploading it to
  [catbox.moe](https://catbox.moe) for a link. No account or API key is needed
  for any of these.

Requirements
------------

* Minecraft 26.2
* [Fabric Loader](https://fabricmc.net/) 0.19.3 or newer
* [Fabric API](https://modrinth.com/mod/fabric-api)
* Java 25 — the runtime the Minecraft launcher already ships with

Installing
----------

VoxelCam is client-side only. Install it in your own game; servers do not need
it, and you can use it on any server, modded or vanilla.

1. Install [Fabric Loader](https://fabricmc.net/use/installer) 0.19.3 or newer
   for Minecraft 26.2.
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) for 26.2.
3. Download `voxelcam-2.2.0.jar` from the
   [latest release](https://github.com/thatapplefreak/VoxelCam/releases/latest).
4. Put both jars in your `mods` folder, creating it if it does not exist:
   * Windows — `%appdata%\.minecraft\mods`
   * macOS — `~/Library/Application Support/minecraft/mods`
   * Linux — `~/.minecraft/mods`
5. Launch Minecraft using the Fabric profile.

Press `H` in-game to open the screenshot manager, or use the camera button on
the title screen. `F2` takes a screenshot as usual; hold `Shift` for an
oversized one.

VoxelCam targets **Java 25**, the runtime Minecraft 26.2 ships with, so the
official launcher needs no changes.

Building
--------

    ./gradlew build

The built jar lands in `build/libs/`. `./gradlew runClient` launches a
development client with the mod loaded.

There are two test suites, and both gate the build in CI:

    ./gradlew test               # unit tests, no game needed
    ./gradlew runClientGameTest  # drives a real client

History
-------

Versions before 2.0.0 were a LiteLoader mod that also posted screenshots to
Twitter, Facebook, Reddit, Imgur, Dropbox, and Google Drive, and included an
image editor. Those depended on credentialed APIs and a LiteLoader-era
rendering stack; the Fabric port replaces them with keyless sharing. The old
implementation remains in this repository's Git history.

2.2.0 moved from Minecraft 1.21.11 to 26.2. Nothing changed for players beyond
the camera button joining the title screen's row of icon buttons; the work was
following Minecraft's own move to unobfuscated code and a rewritten GUI layer.

Licence
-------

VoxelCam is free software: you can redistribute it and/or modify it under the
terms of the GNU Lesser General Public License version 3, as published by the
Free Software Foundation.

VoxelCam is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
PARTICULAR PURPOSE. See [`COPYING.LESSER`](COPYING.LESSER) and
[`COPYING`](COPYING) for the full terms — LGPLv3 applies on top of GPLv3, so
both texts are included.

You are free to include VoxelCam in a modpack. Modifications to VoxelCam itself
must be released under the same licence.

Versions 1.x were released under a different, closed arrangement ("Copyright ©
2013-2014 Thatapplefreak", modpack use by written permission). The LGPL applies
to 2.0.0 onward. Some 1.x source in this repository's history was contributed by
others and is not covered by this grant.
