# OZMail Operations / Betrieb

## Deployment / Bereitstellung

Install OZ Tools before OZMail. Keep `enableCod=false` until a compatible OZ
Wallet release provides the required atomic, idempotent transfer contract.

OZMail uses the standard OZ Tools SQLite location:
`<OZMail plugin directory>/<world name>.db`.

Installiere OZ Tools vor OZMail. `enableCod=false` muss gesetzt bleiben, bis
eine kompatible OZ-Wallet-Version den erforderlichen atomaren, idempotenten
Transfervertrag bereitstellt.

OZMail verwendet den Standard-SQLite-Pfad von OZ Tools:
`<OZMail-Pluginverzeichnis>/<Weltname>.db`.

## Backup and rollback / Sicherung und Rollback

Stop the server cleanly before copying the database file and
`settings.properties`. Restore both from the same backup point. Do not delete
or modify `mail_operations`, `mail_attachments`, or `mail_audit_events` to
work around an unresolved operation.

Den Server vor dem Kopieren der Datenbankdatei und von
`settings.properties` sauber stoppen. Beide Dateien immer aus demselben
Sicherungsstand wiederherstellen. `mail_operations`, `mail_attachments` und
`mail_audit_events` dürfen nicht gelöscht oder manuell verändert werden, um
einen ungeklärten Vorgang zu umgehen.

## Reconciliation / Prüfung ungeklärter Vorgänge

On startup OZMail quarantines every incomplete external inventory boundary.
Administrators can use the Administration tab to inspect metrics, locate a
mail by mail/correlation ID, and review its localized timeline. A matching
quarantined operation is opened directly for review. A quarantine
is deliberate evidence that custody could not be proven; do not infer a
successful delivery or return from the mail text alone.

For a quarantined `SEND`, an administrator can document exactly one externally
verified outcome: either the items are held in mail custody or they were
returned to the sender. The UI requires a verification reason. This action
records database state and audit evidence only; it does not call an inventory
or Wallet API.

The Administration tab can export the current queue to
`exports/reconciliation-<timestamp>.csv` below the OZMail plugin directory.
The CSV contains only operational identifiers, states and timestamps; it does
not contain mail bodies, player names or diagnostic details.

The queue can be filtered locally by operation type. Filters do not change the
database, inventory custody or the exported full queue.

Beim Start quarantänisiert OZMail jede unvollständige externe
Inventargrenze. Administratoren können im Administrations-Tab Kennzahlen
prüfen, eine Mail über Mail-/Korrelations-ID finden und ihren lokalisierten
Verlauf ansehen. Ein passender quarantänisierter Vorgang wird direkt zur
Prüfung geöffnet. Eine Quarantäne ist ein absichtlicher Nachweis dafür, dass
die Verwahrung nicht bewiesen werden konnte; aus dem Mailtext allein darf
keine erfolgreiche Zustellung oder Rückgabe abgeleitet werden.

Für einen quarantänisierten `SEND` kann ein Administrator genau ein extern
verifiziertes Ergebnis dokumentieren: Die Gegenstände befinden sich entweder
im Mailbestand oder wurden an den Absender zurückgegeben. Die Oberfläche
verlangt einen Prüfgrund. Diese Aktion dokumentiert ausschließlich
Datenbankzustand und Audit-Nachweis; sie ruft keine Inventar- oder Wallet-API
auf.

Der Administrations-Tab kann die aktuelle Queue nach
`exports/reconciliation-<timestamp>.csv` unterhalb des OZMail-
Pluginverzeichnisses exportieren. Die CSV enthält nur operative Kennungen,
Zustände und Zeitstempel; Mailtexte, Spielernamen und Diagnosedetails sind
nicht enthalten.

Die Queue kann lokal nach Vorgangstyp gefiltert werden. Filter verändern weder
die Datenbank noch die Inventarverwahrung oder den vollständigen Queue-Export.

## Optional Discord audit / Optionale Discord-Prüfung

Set a positive `discordAuditChannelId` only when OZ - Discord Connect is installed;
channel `0` disables delivery. OZMail forwards successful, verified `SEND`
resolutions as a localized operational notice. The notice deliberately excludes
mail contents, player names and the administrator's verification reason.

Eine positive `discordAuditChannelId` nur setzen, wenn OZ - Discord Connect
installiert ist; Kanal `0` deaktiviert die Weiterleitung. OZMail leitet erfolgreiche,
verifizierte `SEND`-Auflösungen als lokalisierten Betriebshinweis weiter. Der
Hinweis enthält bewusst keine Mailinhalte, Spielernamen oder den Prüfgrund der
Administration.

## Current limits / Aktuelle Grenzen

- Plugin mail is text-only and requires an explicit `trustedPluginSenders`
  allowlist entry. / Plugin-Mail ist rein textbasiert und benötigt einen
  expliziten `trustedPluginSenders`-Allowlist-Eintrag.
- The bridge requires the request sender name to match the consuming plugin's
  own `plugin.yml` name; aliases are rejected. / Die Bridge verlangt, dass der
  Sendername der Anfrage dem eigenen `plugin.yml`-Namen des konsumierenden
  Plugins entspricht; Aliase werden abgelehnt.
- A failed or partial inventory operation is quarantined, not retried. /
  Fehlgeschlagene oder teilweise Inventaroperationen werden quarantänisiert
  und nicht erneut ausgeführt.
- COD is available only when `enableCod=true` and a compatible OZ Wallet
  exposes `transferIdempotent`. Wallet payment is completed before a recipient
  inventory grant; a rejected payment restores the mail to claimable custody,
  while an uncertain post-payment inventory boundary is quarantined for
  reconciliation. / Nachnahme ist nur mit `enableCod=true` und einer
  kompatiblen OZ Wallet mit `transferIdempotent` verfügbar. Die Walletzahlung
  erfolgt vor der Inventarübergabe; eine abgelehnte Zahlung setzt die Mail
  wieder abholbar zurück, eine unklare Inventargrenze nach Zahlung wird zur
  Prüfung quarantänisiert.
- For a quarantined post-payment COD claim, administrators must first verify
  custody. They can document a completed claim, or after verifying that no
  inventory grant occurred, initiate the idempotent Wallet refund and restore
  the mail. / Bei einem quarantänisierten COD-Claim nach Zahlung muss die
  Administration zuerst die Verwahrung prüfen. Sie kann eine erfolgte Abholung
  dokumentieren oder nach bestätigter fehlender Inventarübergabe die idempotente
  Wallet-Erstattung auslösen und die Mail wiederherstellen.
- Expiry processing is bounded to ten due mails whenever the sender joins. /
  Die Ablaufverarbeitung ist beim Login des Absenders auf zehn fällige Mails
  begrenzt.
