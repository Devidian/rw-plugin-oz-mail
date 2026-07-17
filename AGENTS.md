# OZMail Agent Rules

OZMail owns mail state, attachment custody, mail audit/reconciliation, and its
public mail bridge. It is a standalone Java 20 Maven plugin.

- OZ Tools is the hard dependency for shared runtime infrastructure.
- Wallet owns balances and transactions. OZMail may call only Wallet public APIs.
- Discord Connect is optional and must never affect durable mail state.
- Item and COD workflows require a durable journal, idempotency, and recovery;
  do not replace these with best-effort calls.
- Keep all player and administrator texts in both `src/i18n/de.properties` and
  `src/i18n/en.properties`.
- Update README, HISTORY, and local active plans for structural changes.

