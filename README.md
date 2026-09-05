# OZMail

OZMail is an in-game Rising World mail plugin for messages, recoverable item
attachments, optional cash on delivery, and plugin-originated mail.

It requires OZ Tools. Wallet and Discord Connect integrations are optional.
Item custody and COD are introduced only with durable recovery support.

## Plugin mail bridge

The shared OZ Tools `MailBridge` provides API version 2 for text and trusted
plugin-originated attachments. Consumers must use a stable correlation ID;
repeated requests with the same ID return the original completed result.
The operator must explicitly list each sender plugin in
`trustedPluginSenders` in `settings.<world>.json`; all canonical OZ plugins are
present in the default settings. Attachment callers transfer already-owned custody to
Mail and may probe mailbox capacity before changing external inventory. COD is
not accepted through this bridge. The request sender name must match the
consumer plugin's own `plugin.yml` name.

## Operational limits

- `enableCod=false` must remain set until OZ Wallet exposes an atomic,
  idempotent debit/credit operation with correlation lookup.
- Failed or partial inventory mutations are quarantined and shown to
  administrators; they are never retried automatically.
- Clothing attachments retain their concrete `ClothingItem` definition name
  (for example `mininghelmet`) for labels, custody checks, and restoration.
- Construction attachments retain their concrete `ConstructionItem` definition
  name, texture variant, and custom color for labels, custody checks, and
  restoration.
- The current expiry pass processes at most ten due attachment mails when the
  sender logs in. Operators should use the reconciliation queue for any
  quarantined operation.

See [operations.md](docs/operations.md) for deployment, backup, rollback and
reconciliation procedures.

Existing attachments that already persisted only `constructionitem` or
`clothingitem` cannot be migrated automatically because the concrete definition
ID was not stored. Review those mails through the reconciliation workflow. Do
not downgrade while mails with concrete construction or clothing attachment
names are pending. Mail schema v3 adds a default-zero `item_color` column;
older plugin versions ignore it and would restore pending colored construction
attachments without their color.

## Commands

- `/ozmail` — open the mailbox (initially reports implementation status)
- `/ozmail info` — show plugin information

## Development

```sh
mvn -B -DskipTests package
```

## JSON-only distribution

Settings defaults (`settings.default.json`) and translations (`i18n/*.json`)
are shipped only as JSON. Legacy default and translation `.properties` files
are no longer included. Runtime settings remain world-scoped as
`settings.<world>.json`; migration of an existing `settings.properties` and
its backup remains supported. Updating the package does not delete old files
already present on the server. Use `mvn clean package` for a fresh local
package; ZIP assembly also excludes stale legacy settings and translations.

Object-kit inventory selections use the localized object name. Selected and received attachment summaries follow the viewing player’s language.
