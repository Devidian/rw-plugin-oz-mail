package de.omegazirkel.risingworld.mail;

/** Immutable attachment snapshot. Item identity is the definition/object name plus variant. */
public record MailAttachment(String itemName, int variant, int amount, String checksum) {
    public MailAttachment {
        if (itemName == null || itemName.isBlank()) {
            throw new IllegalArgumentException("itemName is required");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        checksum = checksum == null ? "" : checksum;
    }
}
