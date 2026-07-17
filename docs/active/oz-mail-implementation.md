# OZMail Implementation

## Objective

Implement OZMail incrementally with recoverable item custody and optional,
idempotent Wallet COD settlement.

## Current Slice

- [x] Create standalone Maven/Java 20 plugin baseline.
- [x] Implement schema, operation journal, startup quarantine, plain mail, item custody send/claim/return and bounded expiry return.
- [x] Implement mailbox UI, localized player/admin settings, radial menu, quickbar, operational metrics and read-only reconciliation timeline.
- [x] Align mailbox UX with OZTools tables: reply flow, compose reset after successful sends, COD visibility from settings and currency availability, confirmation before attachment-free deletion, and archive only attachment-free mails.
- [x] Implement text-only MailBridge v1 with trusted sender configuration and caller-correlation idempotency.
- [x] Add SQLite persistence tests for completion, restart quarantine and evidence-preserving deletion.
- [ ] Verify batch inventory capacity/atomicity on a running target Rising World runtime and add crash-boundary tests for every external inventory mutation. The OZMail and OZWallet 0.4.2 builds were deployed locally on 2026-07-16 with existing settings and SQLite databases preserved; this checkout has no runnable local Dedicated Server, so no runtime smoke test was possible.
- [x] Implement Wallet's atomic idempotent transfer contract and bind COD claim settlement to it.
- [x] Add administrator-assisted COD refund/claim reconciliation: verified claims are documented, and a verified failed grant can perform an idempotent Wallet refund before restoring mail custody.
- [x] Add the first safe administrator reconciliation action: externally verified quarantined SEND outcome, mandatory reason and audit evidence; it never performs an inventory or Wallet action.
- [x] Add a privacy-minimized reconciliation queue export; it contains only operational identifiers, states and timestamps.
- [x] Add local administration queue filters by operation type; they are read-only and do not change operational state.
- [ ] Add further reconciliation actions only after their external custody contract is verified, plus retention procedures.
- [x] Add optional, default-disabled Discord audit delivery for successful verified SEND resolutions; it excludes mail contents and player names.
- [x] Synchronize the stable MailBridge, DiscordBridge and WalletBridge contract into MavenTemplate; update WalletBridge consumers in GPS, Shop and Marketplace.

## Constraints

- All player and admin texts require German and English translations.
- COD remains disabled until Wallet has an atomic idempotent transfer API.
- Uncertain inventory boundaries must be quarantined for reconciliation.
- The current inventory API exposes no verified batch-capacity reservation. A failed or partial grant/removal is quarantined; automatic retry is intentionally avoided.
