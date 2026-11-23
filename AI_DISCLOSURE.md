AI disclosure
=============

This is the one place VoxelCam's use of generative AI is written down. The
README's AI section and the disclosure on the
[Modrinth page](https://modrinth.com/mod/voxelcam) are taken from it, so when
one changes, all three change.

The statement
-------------

This is also the text of the Modrinth disclosure's note, word for word.

> Since 2.0.0, most of VoxelCam's code, tests and documentation have been
> written with Claude Code, an AI coding assistant, under my direction. I
> decide what the mod does and how it behaves, review every change before it
> is merged, and test each release in game. Versions before 2.0.0 (2013–2014)
> were written by hand.
>
> Text: the in-game English strings, this page's description and the
> changelogs were drafted with Claude. The pirate translation is mine; strings
> added to it since were drafted with Claude.
>
> Assets: the 16×16 star icon for starred screenshots was drawn by a script
> Claude wrote. The camera icon is hand-made, carried over from the 2014 mod.
>
> The mod does not use generative AI while you play.

What each part covers
---------------------

**Code.** The Fabric port that became 2.0.0 and everything since. Commits made
with Claude Code carry a `Co-Authored-By: Claude` trailer, so the history is
the detailed record. `b03820a` is the port's first commit:

    git log --no-merges --format='%h %s%n  %(trailers:key=Co-Authored-By,valueonly)' b03820a^..

The repository's `CLAUDE.md` and `.claude/skills/` are the instructions that
work runs under.

**Text.** The README, `CLAUDE.md`, `GLOSSARY.md`, the ADRs in `docs/adr/`, the
GitHub release notes (which Modrinth shows as each version's changelog), the
Modrinth summary and description, and the English strings in `en_us.json`.
`en_pt.json` (Pirate Speak) was written by hand when it was added; strings for
features since then were drafted with Claude alongside the English ones.

**Assets.** The mod ships two images:

* `textures/photo.png`, the camera icon, hand-made for the 1.x mod in 2014.
* `textures/star.png`, the star badge, added in 2.3.0 and drawn by a script
  Claude wrote rather than by hand.

No image on the Modrinth page (icon, gallery or description) may be
AI-generated under Modrinth's rules, and none is.

**Functionality.** None. Nothing in the mod calls a generative model. The one
network feature, the share link, uploads the screenshot you picked to
[catbox.moe](https://catbox.moe) when you press the button.

On Modrinth
-----------

Project Settings → **Disclosures**. Notes take plain text and inline Markdown;
HTML gets the project flagged.

| Disclosure | Setting |
|---|---|
| Contains AI-generated content | **On.** Uses: **Code**, **Text**, **Assets**. Note: the statement above, without the `>` quote markers. |
| Contains generative AI functionality | Off. |
| Contains external system interactions | Off. The save dialog and the file-manager reveal are things the player asks for, and Modrinth's [disclosures FAQ](https://support.modrinth.com/en/articles/16567675) excludes "saving screenshots to alternative locations" and "functionality that is naturally expected from the intended behavior of a project". |
| Contains telemetry | Off. Nothing is sent unless the player presses the share-link button, and the FAQ excludes data transfers a feature exists to make. |
| Everything else | Off. |

The disclosure is a project setting, not part of a version, so
`release.yml` cannot set it and does not check it.

Keeping it current
------------------

Modrinth's content rules (§5) require disclosures to be accurate, kept up to
date, and consistent with what the project says elsewhere. Anything that
changes what the statement says changes this file, the README's AI section
and the Modrinth disclosure together. That includes an image or translation
made with AI, or one replaced by hand. Compare the three before publishing
each release.
