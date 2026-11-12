# Share only through targets that need no account

The 1.x LiteLoader mod posted to Twitter, Facebook, Reddit, Imgur, Dropbox and Google Drive. The
2.0 Fabric port drops all of them. Sharing is limited to a native Save-As dialog, revealing the file
in the file manager, copying its path, and an anonymous catbox.moe upload. Every credentialed
integration needs a registered app, a key that goes stale (the 2014 Imgur client ID already had),
and an API contract that changes under you. All of that work buys a feature most players never
configure. The keyless targets work on first launch with no setup.

## Considered Options

- **Port the uploaders.** Rejected: every one needed a fresh app registration outside the code,
  and the bundled Twitter, Facebook and Reddit client jars were long dead.
- **Imgur with a client ID the player supplies.** Built during the port (`9c7c126`) and then
  removed (`cb8f37c`). An upload that only works after a trip to api.imgur.com is not "share".
- **Dropbox/Google Drive "uploads".** These were never API integrations, only file copies into
  the sync client's watched folder. Save-As covers them, and it covers every other sync client too.

## Consequences

- catbox.moe signals a refused upload with HTTP 200 and an error sentence in the body, so a link
  is accepted only if it starts with `https://`. Keep that check.
- `java.net.http` has no multipart publisher, so `MultipartBody` is hand-rolled.
- A proposal to add a credentialed target reopens this decision. It is not an incremental feature.
