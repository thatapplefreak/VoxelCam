# Read the Iris shader pack by reflection, with no compile-time dependency

Provenance records the active Iris shader pack. VoxelCam has no build dependency on Iris at all:
it checks `isModLoaded("iris")` and makes every call after that through reflection. A shader mod's
internals are not an API VoxelCam can pin, and a compile-time dependency would couple VoxelCam
releases to Iris releases. Pinning the wrong Iris build would turn an informational tag into a
crash on capture. With reflection, the worst a missing, renamed or reshaped Iris can do is leave
the tag off.

Provenance's mod list is curated by a **denylist** of infrastructure (Fabric API's own modules,
loaders, VoxelCam itself) rather than an allowlist of content mods. An allowlist cannot know about
mods that do not exist yet. Fabric API alone contributes thirty-odd ids that say nothing about
what the player installed.

Jar-in-jar mods are deliberately kept. Fabric API's modules turn out to be top-level siblings, and
real containment is how some modpacks bundle genuine content, so filtering it would drop exactly
what the tag exists to record.
