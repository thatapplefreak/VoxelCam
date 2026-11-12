# Never record the server's address in a screenshot

Capture context names a multiplayer world by its server-list entry name, never by its address.
Context is embedded in the file (ADR-0002), and files exist to be shared. An address baked into
every screenshot is a leak the player cannot see and did not agree to: a private server's IP posted
to a public forum inside an image. The name is enough to tell worlds apart, and an address is not
needed for anything else VoxelCam does.

The same reasoning applies to anything added to capture context or provenance later. If a value
would surprise a player who learned it travels with every shared screenshot, it stays out.
