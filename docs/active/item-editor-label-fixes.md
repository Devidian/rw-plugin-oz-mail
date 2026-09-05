# Item editor and label fixes

## Objective and ownership
Object-kit attachment candidates resolve their object definition before generic item names. Selected and received attachments use the viewing player’s language without changing stored item identity or custody.

Owned by this plugin, using existing Tools/Wallet APIs where applicable. No dependency or database schema changes.

## Progress
- [x] Implement focused fixes.
- [x] Tests, package, API and entrypoint checks.
- [x] Development reload proof.
- [x] Manual player acceptance in German and English (user confirmed 2026-09-06).

## Risks and rollback
Validate object-kit selection and variant display without changing inventory identity. Restore previous plugin artifacts if needed; persisted data remains compatible. Shop removal metadata is optional and ignored by older readers; Wallet transactions are retained.

## Validation results (2026-09-05)
22 tests passed; package and entrypoint verification passed. PluginAPI calls verified directly with javap (no local API verification script). Development artifact SHA-256 matched; startup/listener registration and complete reload confirmed at 21:09:33 UTC. No new plugin errors were observed. An existing container HTTP healthcheck failure predates the upload; infrastructure diagnosis remains separate.

Implementation and in-game acceptance complete. Patch release: 0.1.17.
