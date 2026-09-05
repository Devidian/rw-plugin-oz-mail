# History

## [0.1.16] - 2026-09-05 | PluginAPI compatibility

- build: update the bundled PluginAPI and JSON settings baseline for the native web release wave.
- change: migrate Mail settings and translations to JSON while retaining existing mailbox data and configuration.

## [0.1.15] - 2026-08-10 | System reports

- feat: deliver trusted system reports without consuming mailbox capacity or triggering a mailbox-full warning.
- fix: show the normal online-delivery notification for trusted quota-exempt system reports.

## [0.1.14] - 2026-08-05 | Trusted plugin senders

- change: trust all canonical OZ plugin senders in the default configuration
- fix: show the trusted-plugin sender list in a tall multiline settings field

## [0.1.13] - 2026-07-31 | Clearer mail feedback

- feat: let players enable or disable the mail-send success confirmation
- fix: show localized success and error message boxes after sending mail
- fix: warn players at login when their mailbox is full

## [0.1.12] - 2026-07-27 | Trusted Marketplace attachments

- feat: add trusted, idempotent plugin attachment delivery for Marketplace wanted listings
- feat: expose a mailbox-capacity probe before external inventory custody changes
- change: trust the canonical `OZ - Marketplace` sender by default

## [0.1.11] - 2026-07-26 | Concrete item attachments

- fix: persist, label, match, and restore concrete clothing definitions instead of `clothingitem`
- fix: persist, label, match, and restore concrete construction definitions instead of `constructionitem`
- fix: preserve custom construction colors across attachment send, claim, return, expiry, and recovery
- db: add a backward-compatible construction color column to Mail schema v3

## [0.1.10] - 2026-07-26 | Highlighted administrator recipients

- feat: highlight administrators in the recipient table with a labeled gold row
- fix: resolve administrator status for online and offline recipients through their persistent player identity

## [0.1.9] - 2026-07-24 | Shared runtime bridges

- refactor: use the synchronized optional Discord bridge
- refactor: keep the plugin entry point limited to lifecycle wiring and event delegation
- change: update the shared OZ Tools dependency to version 0.23.8

## [0.1.8] - 2026-07-21 | Shared Tools update

- change: update the shared OZ Tools dependency to version 0.23.1

## [0.1.7] - 2026-07-20 | Advanced button controls

- change: use the stable shared OZ button controls in mailbox and settings overlays

## [0.1.6] - 2026-07-20 | Settings localization

- fix: localize the recipient-list time-window setting in the admin settings UI

## [0.1.5] - 2026-07-20 | Update metadata

- change: publish the canonical GitHub release source for OZ Tools update management

## [0.1.4] - 2026-07-17 | Mail workflow refinements

- fix: show a claim-specific success message and require confirmation before a COD attachment claim
- fix: align compose recipient picker and static field limits with the Mail overlay layout
- change: rename the player tab to recipients
- feat: select the COD currency from the Wallet currency dropdown
- feat: let players configure their own recipient-list period, defaulting to the server value

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
