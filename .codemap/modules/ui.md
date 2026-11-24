# Mailbox UI and capacity
<!-- codemap-id: rw-plugin-oz-mail/ui -->

## Responsibility

Player mailbox overlays and settings, with optional capacity purchases.

## Source and tests

- [`MailOverlay`](../../src/de/omegazirkel/risingworld/mail/ui/MailOverlay.java) — mail UI.
- [`MailboxCapacityService`](../../src/de/omegazirkel/risingworld/mail/MailboxCapacityService.java) — mailbox capacity.
- [`MailboxShopIntegration`](../../src/de/omegazirkel/risingworld/mail/MailboxShopIntegration.java) — Shop offer integration.
- [`MailSettings`](../../src/de/omegazirkel/risingworld/mail/MailSettings.java) — settings.
- [Tests](../../tests/de/omegazirkel/risingworld/mail/) cover repository behavior. Verify relevant scenarios before implementing changes.
- [Repository index](../README.md) and [settings defaults](../../src/settings.default.json).
