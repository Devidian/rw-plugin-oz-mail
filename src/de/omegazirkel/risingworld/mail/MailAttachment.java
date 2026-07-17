package de.omegazirkel.risingworld.mail;

/** Immutable custody snapshot, including mutable Rising World item state. */
public record MailAttachment(String itemName, int variant, int amount, String checksum, int durability,
        short status, String modifier) {
    public MailAttachment(String itemName, int variant, int amount, String checksum) {
        this(itemName, variant, amount, checksum, 0, (short) 0, "");
    }
    public MailAttachment {
        if (itemName == null || itemName.isBlank()) {
            throw new IllegalArgumentException("itemName is required");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        checksum = checksum == null ? "" : checksum;
        modifier = modifier == null ? "" : modifier;
    }
}
