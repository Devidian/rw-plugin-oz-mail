# Marketplace trusted attachment delivery

## Objective

Extend the trusted plugin mail API with idempotent item attachments and a
mailbox-capacity probe for OZ Marketplace wanted-listing fulfillment.

## Constraints

- OZ Mail owns persisted attachment custody and correlation idempotency.
- Only configured `trustedPluginSenders` may call the API.
- Player inventory is never read or mutated by this plugin-originated path; the
  caller must already own custody.
- Existing text-only bridge calls remain compatible.

## Checklist

- [x] Add capacity and attachment runtime endpoints.
- [x] Validate attachment snapshots and persist them atomically.
- [x] Add tests and document API version 2.
- [x] Run architecture, Maven test/package, and runtime checks.

## Risks and rollback

Older consumers remain on the text-only method. Do not downgrade while
Marketplace attachment mails remain unclaimed.
