package de.omegazirkel.risingworld.mail;

/** Journal state for a cross-boundary mail operation. */
public enum MailOperationState {
    PREPARED,
    APPLYING,
    COMPLETED,
    COMPENSATING,
    COMPENSATED,
    NEEDS_RECONCILIATION
}
