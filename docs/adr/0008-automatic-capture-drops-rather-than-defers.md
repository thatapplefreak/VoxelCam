# An automatic capture that cannot fire now is dropped, not deferred

When a moment arrives but the player has a screen open, or the level or player is gone, the
pending automatic capture is thrown away rather than held until the view clears. A screenshot of
the inventory the player opened ten seconds after an advancement is not the moment. It is a
confusing file, and a feature that produces those is one nobody leaves switched on (see ADR-0007).
A capture that never happens costs nothing. One that happens at the wrong time costs the player's
trust in the whole feature.

Three exceptions, each because the obstruction is not the player's doing:

- **A level-loading screen** holds the capture rather than dropping it. That screen clears by
  itself, and the frame countdown is paused under it so a slow chunk load cannot silently lose a
  portal arrival.
- **A level that has just loaded** holds the capture too, until its terrain is drawn and the
  portal overlay has faded, since vanilla closes the loading screen before either. That wait is
  bounded, and running out of it captures rather than drops: what is on screen by then is mostly
  the place the player arrived, and dropping would lose the arrival silently on a slow machine.
- **Death** inverts the rule. It waits for the death screen and captures under it, since that
  screen arrives on a server packet after a variable delay. Racing it would give either a clean
  frame or the red-overlay frame for the same trigger.

A dropped capture hands its cooldown window back, so a moment that never became a screenshot does
not suppress the next one.

A capture menu the player is holding counts as their own doing, although it is drawn on the HUD and
is not a screen. A moment that arrives while the capture key is down is not armed, and one already
pending when the menu is armed is dropped. None of the exceptions above applies under it.
