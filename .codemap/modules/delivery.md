# Mail delivery and custody
<!-- codemap-id: rw-plugin-oz-mail/delivery -->

## Responsibility

Own message, attachment and operation states in SQLite. Item and COD operations use durable journal, idempotency and recovery.

## Source and tests

- [`MailService`](../../src/de/omegazirkel/risingworld/mail/MailService.java) — send and claim workflows.
- [`MailDatabase`](../../src/de/omegazirkel/risingworld/mail/MailDatabase.java) — SQLite schema and operations.
- [`MailInventoryTransfer`](../../src/de/omegazirkel/risingworld/mail/MailInventoryTransfer.java) — attachment inventory transfer.
- [`OZMailRuntime`](../../src/de/omegazirkel/risingworld/OZMailRuntime.java) — trusted sibling-plugin mail facade.
- [Tests](../../tests/de/omegazirkel/risingworld/mail/) cover repository behavior. Verify relevant scenarios before implementing changes.
- [Repository index](../README.md) and [settings defaults](../../src/settings.default.json).

## Dependencies

- `rw-plugin-oz-wallet/ledger` — Wallet transfers for COD via Tools bridge. [Integration source](../../src/de/omegazirkel/risingworld/mail/MailService.java)
- `rw-plugin-oz-shop/api` — optional mailbox capacity offer registration. [Integration source](../../src/de/omegazirkel/risingworld/mail/MailboxShopIntegration.java)
- `rw-plugin-oz-tools/shared-api` — optional mail audit events through EventBridge. [Call site](../../src/de/omegazirkel/risingworld/OZMailRuntime.java)
