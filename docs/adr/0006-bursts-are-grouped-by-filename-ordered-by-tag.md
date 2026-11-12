# Group a burst by filename, order it by embedded tag, and set the key by swapping bytes

A burst's frames are written as one key file plus `_burstNN` siblings. Their names are reserved up
front, so overlapping captures can never collide. The manager folds a burst into one row by
filename alone, because the list is rebuilt on every search keystroke and must cost zero file
reads. The order within a burst comes from the `BurstFrame` tags embedded at capture time
(ADR-0002), not from the names. **Set as key** swaps the two files' bytes and leaves their names
alone.

Names decide membership because names are free to read. Tags decide order because they are what
was true at capture time. The key is a byte swap because the alternative, renaming every frame,
would have to rewrite tags or reshuffle the filmstrip. A swap changes which image stands for the
group and nothing else.

## Consequences

- A frame's name stops telling you its capture position after a Set as key. The `burstindex` tag
  is authoritative, and the filmstrip marks the key with its own badge for that reason.
- No frame count is embedded: an aborted burst would make it a lie. The count comes from the
  directory scan.
- Rename and delete act on the whole group, with rollback when part of it fails. Touching only the
  key would orphan its frames as ungrouped screenshots.
