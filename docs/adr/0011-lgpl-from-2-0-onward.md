# License 2.0.0 onward under LGPL-3.0, and leave 1.x out of the grant

From 2.0.0, VoxelCam is LGPL-3.0, with both the GPLv3 and LGPLv3 texts shipped in the repository
and inside the jar. The jar carries both because LGPLv3 is defined as additions to GPLv3, and
someone who receives only the binary should receive the terms with it. The LGPL rather than the
GPL lets modpacks include VoxelCam freely while still requiring modifications to VoxelCam itself
to stay open.

1.x was released under a closed arrangement, and some of its history was contributed by other
authors who never agreed to relicensing. So the grant starts at the 2.0 rewrite and does not
reach back. This is also why reviving 1.x code from history (`git show 95c1b19:src/com/...`)
means porting the idea, not the text.
