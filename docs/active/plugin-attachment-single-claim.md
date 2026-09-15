# Plugin-Anhänge einzeln abholen

## Ziel

Von Plugins erzeugte Mails mit Gegenständen dürfen keine Rücksendeaktion
anzeigen. Stattdessen wird je Klick genau ein noch verwahrter Anhang in das
Inventar überführt.

## Sicherheitsregeln

- Die Reservierung prüft Empfänger, Plugin-Absender, COD=0 und einen zulässigen
  Mail-Zustand in derselben Transaktion.
- Nur die reservierte Attachment-ID wechselt anschließend nach `CLAIMED` oder
  bei einem unvollständigen Inventar-Transfer nach `QUARANTINED`.
- Operation und Custody-Wechsel werden gemeinsam journalisiert.

## Validierung

- Datenbanktests prüfen Reihenfolge, Empfängerbindung und Ausschluss normaler
  Spieler-Mails.
