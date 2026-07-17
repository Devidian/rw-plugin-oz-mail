package de.omegazirkel.risingworld.mail;

/** Durable mail lifecycle states. State changes are recorded in mail_operations. */
public enum MailMessageState {
    PENDING_SEND,
    DELIVERED,
    READ,
    ARCHIVED,
    CLAIMING,
    CLAIMED,
    RETURNING,
    RETURNED,
    EXPIRED,
    DELETED,
    QUARANTINED
}
