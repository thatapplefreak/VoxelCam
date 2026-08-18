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
* Share a screenshot by saving a copy through your platform's native file
  dialog, showing it in your file manager, copying its path, or uploading it to
  [catbox.moe](https://catbox.moe) for a link. No account or API key is needed
  for any of these.

Requirements
------------

* Minecraft 1.21.11
* [Fabric Loader](https://fabricmc.net/) 0.19.3 or newer
* [Fabric API](https://modrinth.com/mod/fabric-api)
* Java 25

Building
--------

    ./gradlew build

The built jar lands in `build/libs/`. `./gradlew runClient` launches a
development client with the mod loaded.

History
-------

Versions before 2.0.0 were a LiteLoader mod that also posted screenshots to
Twitter, Facebook, Reddit, Imgur, Dropbox, and Google Drive, and included an
image editor. Those depended on credentialed APIs and a LiteLoader-era
rendering stack; the Fabric port replaces them with keyless sharing. The old
implementation remains in this repository's Git history.
