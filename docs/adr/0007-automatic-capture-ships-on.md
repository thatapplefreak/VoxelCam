# Automatic capture ships switched on

All four triggers (advancement, boss defeat, death, first arrival in a dimension) are on by
default, each with its own toggle. Issue #26 specified off by default. That was reversed on the
issue's own argument: the feature exists to catch moments the player did not think to capture, and
a player who has to know it exists and switch it on is one who was already thinking about
screenshots. The global cooldown, which limits it to one automatic capture per window however many
moments arrive, is what makes on-by-default tolerable rather than noisy.

The persisted config file (`voxelcam.json`, 2.4.0) was the prerequisite. Before it existed, any
default was a default the player could not durably change.
