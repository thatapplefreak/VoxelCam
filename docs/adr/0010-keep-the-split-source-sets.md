# Keep Loom's split source sets even though the mod is client-only

The build uses `splitEnvironmentSourceSets()`. Every line of Java lives in `src/client/java` and
`src/main` holds only resources. That looks half-finished for a mod with `"environment":
"client"`: collapsing everything into `src/main/java` would remove a launch warning and the manual
test-classpath wiring in `build.gradle`. It stays because it is the layout Fabric's own 26.2
example mod ships, so it is what anyone who has worked on another Fabric mod expects. Moving every
file would change nothing a player can observe.

## Consequences

- `compileJava` is `NO-SOURCE`, and the "Class path entries reference missing files" warning at
  launch is expected.
- The `test` source set sees no mod code unless `build.gradle` adds the client output explicitly.
  A regression there passes with zero tests run, which is why CI asserts a non-zero test count.

Worth revisiting, but not immediately before a release.
