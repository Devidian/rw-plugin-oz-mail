# OZMail

OZMail is an in-game Rising World mail plugin for messages, recoverable item
attachments, optional cash on delivery, and plugin-originated mail.

It requires OZ Tools. Wallet and Discord Connect integrations are optional.
Item custody and COD are introduced only with durable recovery support.

## Plugin mail bridge

`de.omegazirkel.risingworld.mail.MailBridge` provides API version 1 for
text-only plugin-originated mail. Consumers must use a stable correlation ID;
repeated requests with the same ID return the original completed result.
The operator must explicitly list each sender plugin in
`trustedPluginSenders` in `settings.properties`. Attachments and COD are not
accepted through this bridge. The request sender name must match the consumer
plugin's own `plugin.yml` name; the bridge rejects caller-supplied aliases.

## Operational limits

- `enableCod=false` must remain set until OZ Wallet exposes an atomic,
  idempotent debit/credit operation with correlation lookup.
- Failed or partial inventory mutations are quarantined and shown to
  administrators; they are never retried automatically.
- The current expiry pass processes at most ten due attachment mails when the
  sender logs in. Operators should use the reconciliation queue for any
  quarantined operation.

See [operations.md](docs/operations.md) for deployment, backup, rollback and
reconciliation procedures.

## Commands

- `/ozmail` — open the mailbox (initially reports implementation status)
- `/ozmail info` — show plugin information

## Development

```sh
mvn -B -DskipTests package
```
