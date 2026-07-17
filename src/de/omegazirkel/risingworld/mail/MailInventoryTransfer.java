package de.omegazirkel.risingworld.mail;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.risingworld.api.definitions.Definitions;
import net.risingworld.api.definitions.Items.ItemDefinition;
import net.risingworld.api.definitions.Objects.ObjectDefinition;
import net.risingworld.api.objects.Inventory;
import net.risingworld.api.objects.Inventory.SlotType;
import net.risingworld.api.objects.Item;
import net.risingworld.api.objects.Player;

/**
 * Inventory boundary used by the mail saga. It performs a complete preflight
 * before removal and reports a partial mutation explicitly for reconciliation.
 */
public final class MailInventoryTransfer {
    private MailInventoryTransfer() {
    }

    /** Read-only, aggregated inventory view used by the compose selector. */
    public static List<AttachmentCandidate> attachmentCandidates(Player player) {
        if (player == null || player.getInventory() == null) return List.of();
        Map<String, AttachmentCandidate> grouped = new LinkedHashMap<>();
        Item[] items = player.getInventory().getAllItems();
        if (items == null) return List.of();
        for (Item item : items) {
            if (item == null || !item.isValid() || item.getStack() <= 0) continue;
            String itemName = storedName(item);
            if (itemName.isBlank()) continue;
            String key = key(itemName, item.getVariant());
            AttachmentCandidate current = grouped.get(key);
            String label = MailItemNames.label(itemName, item.getVariant());
            grouped.put(key, current == null
                    ? new AttachmentCandidate(itemName, label, item.getVariant(), item.getStack())
                    : new AttachmentCandidate(current.itemName(), current.displayName(), current.variant(),
                            current.availableAmount() + item.getStack()));
        }
        List<AttachmentCandidate> result = new ArrayList<>(grouped.values());
        result.sort(Comparator.comparing(AttachmentCandidate::displayName, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }

    public static TransferResult removeAll(Player player, List<MailAttachment> attachments) {
        if (player == null || player.getInventory() == null) {
            return TransferResult.failed("Player inventory is unavailable");
        }
        List<MailAttachment> normalized = normalized(attachments);
        if (!hasAll(player, normalized)) {
            return TransferResult.failed("Inventory does not contain all requested attachments");
        }
        Inventory inventory = player.getInventory();
        int removed = 0;
        for (MailAttachment attachment : normalized) {
            int remaining = attachment.amount();
            for (SlotType type : SlotType.values()) {
                for (int slot = 0; slot < inventory.getSlotCount(type); slot++) {
                    Item item = inventory.getItem(slot, type);
                    if (!matches(item, attachment)) {
                        continue;
                    }
                    int amount = Math.min(remaining, item.getStack());
                    if (!inventory.removeItem(slot, type, amount)) {
                        return TransferResult.partial(removed, "Inventory removal failed after preflight");
                    }
                    removed += amount;
                    remaining -= amount;
                    if (remaining == 0) {
                        break;
                    }
                }
                if (remaining == 0) {
                    break;
                }
            }
            if (remaining != 0) {
                return TransferResult.partial(removed, "Inventory changed during attachment removal");
            }
        }
        inventory.syncWithClient();
        return TransferResult.completed(removed);
    }

    public static TransferResult restoreAll(Player player, List<MailAttachment> attachments) {
        if (player == null || player.getInventory() == null) {
            return TransferResult.failed("Player inventory is unavailable");
        }
        int restored = 0;
        for (MailAttachment attachment : normalized(attachments)) {
            ItemDefinition item = Definitions.getItemDefinition(attachment.itemName());
            ObjectDefinition object = objectDefinition(attachment.itemName(), attachment.variant());
            Item added = object != null
                    ? player.getInventory().addObjectItem(object.id, attachment.variant(), attachment.amount())
                    : item == null ? null : player.getInventory().addItem(item.id, attachment.variant(), attachment.amount());
            if (added == null || !added.isValid()) {
                return restored == 0
                        ? TransferResult.failed("Could not restore attachment")
                        : TransferResult.partial(restored, "Could not restore every attachment");
            }
            restored += attachment.amount();
        }
        player.getInventory().syncWithClient();
        return TransferResult.completed(restored);
    }

    private static boolean hasAll(Player player, List<MailAttachment> attachments) {
        Map<String, Integer> available = new LinkedHashMap<>();
        Item[] items = player.getInventory().getAllItems();
        if (items == null) {
            return attachments.isEmpty();
        }
        for (Item item : items) {
            if (item == null || !item.isValid() || item.getStack() <= 0) {
                continue;
            }
            String name = storedName(item);
            if (name.isBlank()) {
                continue;
            }
            available.merge(key(name, item.getVariant()), item.getStack(), Integer::sum);
        }
        for (MailAttachment attachment : attachments) {
            if (available.getOrDefault(key(attachment.itemName(), attachment.variant()), 0) < attachment.amount()) {
                return false;
            }
        }
        return true;
    }

    private static List<MailAttachment> normalized(List<MailAttachment> attachments) {
        Map<String, MailAttachment> grouped = new LinkedHashMap<>();
        for (MailAttachment attachment : attachments == null ? List.<MailAttachment>of() : attachments) {
            String key = key(attachment.itemName(), attachment.variant());
            MailAttachment prior = grouped.get(key);
            grouped.put(key, prior == null ? attachment
                    : new MailAttachment(prior.itemName(), prior.variant(), prior.amount() + attachment.amount(),
                            prior.checksum()));
        }
        return List.copyOf(grouped.values());
    }

    private static boolean matches(Item item, MailAttachment attachment) {
        return item != null && item.isValid() && item.getVariant() == attachment.variant()
                && attachment.itemName().equalsIgnoreCase(storedName(item));
    }

    private static String storedName(Item item) {
        if (item instanceof Item.ObjectItem object && object.getObjectName() != null && !object.getObjectName().isBlank()) {
            return object.getObjectName().trim();
        }
        ItemDefinition definition = item.getDefinition();
        if (definition == null || definition.name == null || definition.name.isBlank()) {
            definition = Definitions.getItemDefinition(item.getTypeID());
        }
        return definition == null || definition.name == null ? "" : definition.name.trim();
    }

    private static ObjectDefinition objectDefinition(String itemName, int variant) {
        ObjectDefinition direct = Definitions.getObjectDefinition(itemName);
        if (direct != null) {
            return direct;
        }
        ItemDefinition item = Definitions.getItemDefinition(itemName);
        return item != null && item.getVariant(variant) != null
                ? Definitions.getObjectDefinition(item.getVariant(variant).name)
                : null;
    }

    private static String key(String itemName, int variant) {
        return itemName.trim().toLowerCase(Locale.ROOT) + ':' + variant;
    }

    public record TransferResult(boolean complete, boolean partiallyMutated, int affectedAmount, String detail) {
        static TransferResult completed(int amount) {
            return new TransferResult(true, false, amount, "");
        }

        static TransferResult partial(int amount, String detail) {
            return new TransferResult(false, true, amount, detail);
        }

        static TransferResult failed(String detail) {
            return new TransferResult(false, false, 0, detail);
        }
    }

    public record AttachmentCandidate(String itemName, String displayName, int variant, int availableAmount) {
        public MailAttachment snapshot(int amount) {
            return new MailAttachment(itemName, variant, amount, itemName + ":" + variant + ":" + amount);
        }
    }

    public static String displayName(String itemName, int variant) {
        return MailItemNames.label(itemName, variant);
    }
}
