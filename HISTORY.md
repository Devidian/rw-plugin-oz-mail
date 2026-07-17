# History

## [0.1.3] - 2026-07-17 | Release package validation

- fix: validate the distributable ZIP in CI using the Maven package lifecycle

## [0.1.2] - 2026-07-17 | Shared runtime dependency repair

- fix: require OZTools 0.22.2 for exact persisted-player lookup

## [0.1.1] - 2026-07-17 | Release packaging repair

- fix: install the bundled PluginAPI in CI before resolving dependencies

## [0.1.0] - 2026-07-16 | Initial secure mail release

- Added durable mail journal, recovery quarantine, mailbox UI, attachment custody send/claim/return/expiry flows, settings, operational administration and localized UI.
- Added text-only MailBridge v1 with trusted sender configuration and caller-correlation idempotency.
- Added SQLite persistence regression tests.
- Added bilingual operator runbook for deployment, backup, rollback and reconciliation.
- Added an administrator-only, auditable resolution for quarantined send operations. It requires an explicit verification reason and only records an externally confirmed custody outcome; it never moves inventory or Wallet funds.
- Added direct lookup of quarantined operations by mail or correlation ID for administrator review.
- Added read-only reconciliation queue filters by operation type.
- Added optional, default-disabled Discord audit forwarding for verified send resolutions without mail contents or player names.
- Added attachment item and amount details to inbox and outbox mail views.
- Hardened MailBridge sender identity: caller-supplied aliases are rejected.
- Added optional COD compose fields and Wallet-backed, idempotent COD claim settlement.
- Added audited administrator reconciliation for quarantined post-payment COD claims, including idempotent refunds.
