# An automatic capture that cannot fire now is dropped, not deferred

When a moment arrives but the player has a screen open, or the level or player is gone, the
pending automatic capture is thrown away rather than held until the view clears. A screenshot of
the inventory the player opened ten seconds after an advancement is not the moment. It is a
confusing file, and a feature that produces those is one nobody leaves switched on (see ADR-0007).
A capture that never happens costs nothing. One that happens at the wrong time costs the player's
trust in the whole feature.

Two exceptions, each because the obstruction is not the player's doing:

- **A level-loading screen** holds the capture rather than dropping it. That screen clears by
  itself, and the frame countdown is paused under it so a slow chunk load cannot silently lose a
  portal arrival.
- **Death** inverts the rule. It waits for the death screen and captures under it, since that
  screen arrives on a server packet after a variable delay. Racing it would give either a clean
  frame or the red-overlay frame for the same trigger.

A dropped capture hands its cooldown window back, so a moment that never became a screenshot does
not suppress the next one.
