# Repository Policy

## Runtime Policy

- Java 20 is the plugin baseline.
- Preserve standalone Maven packaging and GitHub tag-release behavior.
- Keep durable mail state, custody, audit, and recovery compatible.

## Plugin Entry-Point Policy

- The `plugin.yml` main class is the sole Rising World `Listener` and sole
  `registerEventListener(...)` target.
- Keep it limited to lifecycle wiring, settings/event delegation, and thin
  compatibility facades. Put mail workflows, custody, persistence, UI,
  integrations, and timers in focused thematic classes that do not implement
  Rising World's `Listener`.

## Dependency Policy

- OZ Tools is the only hard sibling dependency.
- Wallet and Discord integrations remain reflection based.
- Durable mail state must never depend on optional transport availability.

## Validation Policy

- Run architecture, Maven test/package, plugin API, and runtime smoke checks
  before release.

## Changelog and Release Policy

- Every commit changing `src/`, `pom.xml`, or the bundled PluginAPI must add
  exactly one descriptive Markdown fragment under `changelog/unreleased/`.
- Before a release, review all pending fragments together: remove reverted or
  mutually cancelling work, consolidate the remaining entries into
  `HISTORY.md`, and put only player-relevant highlights in
  `release-notes/<version>.md`. Remove consumed fragments in the same
  release-preparation commit.
- Do not publish a release while pending fragments remain.
