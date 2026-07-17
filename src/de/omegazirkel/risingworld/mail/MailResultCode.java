package de.omegazirkel.risingworld.mail;

/** Stable, locale-independent outcomes for UI and bridge consumers. */
public enum MailResultCode {
    SUCCESS,
    INVALID_REQUEST,
    MAILBOX_FULL,
    INVENTORY_INVALID,
    COOLDOWN_ACTIVE,
    SENDER_UNAVAILABLE,
    PLUGIN_NOT_TRUSTED,
    DATABASE_UNAVAILABLE,
    PAYMENT_FAILED,
    OPERATION_FAILED,
    RECONCILIATION_REQUIRED,
    UNSUPPORTED_IDEMPOTENCY
}
