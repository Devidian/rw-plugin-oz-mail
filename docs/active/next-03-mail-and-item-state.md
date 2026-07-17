# Next 03: Mail player picker and item-state custody

## Objective

Add the recent-player picker and favourite recipients to OZMail, and preserve
item durability/state across mail custody transfers.

## Ownership and dependencies

- OZMail owns the UI, recipient preferences and durable attachment snapshots.
- OZTools `PlayerDatabaseHelper` supplies the authoritative recent-player data.
- Marketplace owns its separate listing custody implementation.

## Risks and rollback

- Attachment columns are additive SQLite migration fields; older snapshots use
  neutral defaults.
- Inventory mutation remains journaled; an unsuccessful restore is still
  quarantined rather than retried.

## Validation

- [x] Build and run tests with Maven (16 tests).
- [ ] Runtime-check a damaged item send, claim and return on development.

## Checklist

- [x] Add configurable recent-player window, favourites and picker UI.
- [x] Persist and restore durability, status and modifier in mail custody.
- [x] Update localized texts and documentation.
- [x] Add claim-specific success feedback, COD payment confirmation, and corrected compose layout.
