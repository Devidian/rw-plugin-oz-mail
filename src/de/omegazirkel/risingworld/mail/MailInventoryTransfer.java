package de.omegazirkel.risingworld.mail;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.risingworld.api.definitions.Clothing.ClothingDefinition;
import net.risingworld.api.definitions.Constructions.ConstructionDefinition;
import net.risingworld.api.definitions.Definitions;
import net.risingworld.api.definitions.Items.ItemDefinition;
import net.risingworld.api.definitions.Items.Modifier;
import net.risingworld.api.definitions.Objects.ObjectDefinition;
import net.risingworld.api.objects.Inventory;
import net.risingworld.api.objects.Inventory.SlotType;
import net.risingworld.api.objects.Item;
import net.risingworld.api.objects.Player;

/** Inventory boundary that snapshots and restores mutable item state. */
public final class MailInventoryTransfer {
    private MailInventoryTransfer() { }

    public static List<AttachmentCandidate> attachmentCandidates(Player player) {
        if (player == null || player.getInventory() == null) return List.of();
        Map<String, AttachmentCandidate> grouped = new LinkedHashMap<>();
        Item[] items = player.getInventory().getAllItems();
        if (items == null) return List.of();
        for (Item item : items) {
            if (item == null || !item.isValid() || item.getStack() <= 0) continue;
            String itemName = storedName(item);
            if (itemName.isBlank()) continue;
            AttachmentCandidate candidate = snapshot(itemName, item, player.getLanguage());
            String key = key(candidate.itemName(), candidate.variant(), candidate.durability(), candidate.status(),
                    candidate.modifier(), candidate.color());
            AttachmentCandidate prior = grouped.get(key);
            grouped.put(key, prior == null ? candidate : candidate.withAmount(prior.availableAmount() + candidate.availableAmount()));
        }
        List<AttachmentCandidate> result = new ArrayList<>(grouped.values());
        result.sort(Comparator.comparing(AttachmentCandidate::displayName, String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(AttachmentCandidate::durability));
        return List.copyOf(result);
    }

    public static TransferResult removeAll(Player player, List<MailAttachment> attachments) {
        if (player == null || player.getInventory() == null) return TransferResult.failed("Player inventory is unavailable");
        List<MailAttachment> normalized = normalized(attachments);
        if (!hasAll(player, normalized)) return TransferResult.failed("Inventory does not contain all requested attachments");
        Inventory inventory = player.getInventory();
        int removed = 0;
        for (MailAttachment attachment : normalized) {
            int remaining = attachment.amount();
            for (SlotType type : SlotType.values()) {
                for (int slot = 0; slot < inventory.getSlotCount(type); slot++) {
                    Item item = inventory.getItem(slot, type);
                    if (!matches(item, attachment)) continue;
                    int amount = Math.min(remaining, item.getStack());
                    if (!inventory.removeItem(slot, type, amount)) return TransferResult.partial(removed, "Inventory removal failed after preflight");
                    removed += amount;
                    remaining -= amount;
                    if (remaining == 0) break;
                }
                if (remaining == 0) break;
            }
            if (remaining != 0) return TransferResult.partial(removed, "Inventory changed during attachment removal");
        }
        inventory.syncWithClient();
        return TransferResult.completed(removed);
    }

    public static TransferResult restoreAll(Player player, List<MailAttachment> attachments) {
        if (player == null || player.getInventory() == null) return TransferResult.failed("Player inventory is unavailable");
        int restored = 0;
        for (MailAttachment attachment : normalized(attachments)) {
            ItemDefinition item = Definitions.getItemDefinition(attachment.itemName());
            ObjectDefinition object = objectDefinition(attachment.itemName(), attachment.variant());
            ConstructionDefinition construction = Definitions.getConstructionDefinition(attachment.itemName());
            ClothingDefinition clothing = Definitions.getClothingDefinition(attachment.itemName());
            Item added = object != null ? player.getInventory().addObjectItem(object.id, attachment.variant(), attachment.amount())
                    : construction != null
                            ? player.getInventory().addConstructionItem(construction.id, attachment.variant(),
                                    attachment.color(), attachment.amount())
                            : clothing != null
                                    ? player.getInventory().addClothingItem(clothing.id, attachment.variant(), 0,
                                            attachment.amount(), 0L)
                                    : item == null ? null : player.getInventory().addItem(item.id,
                                            attachment.variant(), attachment.amount());
            if (added == null || !added.isValid()) return restored == 0 ? TransferResult.failed("Could not restore attachment")
                    : TransferResult.partial(restored, "Could not restore every attachment");
            added.setDurability(attachment.durability());
            added.setStatus(attachment.status());
            Modifier modifier = modifier(attachment.modifier());
            if (modifier != null) added.setModifier(modifier);
            restored += attachment.amount();
        }
        player.getInventory().syncWithClient();
        return TransferResult.completed(restored);
    }

    private static boolean hasAll(Player player, List<MailAttachment> attachments) {
        Map<String, Integer> available = new LinkedHashMap<>();
        Item[] items = player.getInventory().getAllItems();
        if (items == null) return attachments.isEmpty();
        for (Item item : items) {
            if (item == null || !item.isValid() || item.getStack() <= 0) continue;
            String name = storedName(item);
            if (!name.isBlank()) available.merge(key(name, item.getVariant(), item.getDurability(), item.getStatus(),
                    modifierName(item), constructionColor(item)), item.getStack(), Integer::sum);
        }
        for (MailAttachment attachment : attachments) if (available.getOrDefault(key(attachment.itemName(),
                attachment.variant(), attachment.durability(), attachment.status(), attachment.modifier(),
                attachment.color()), 0) < attachment.amount()) return false;
        return true;
    }

    private static List<MailAttachment> normalized(List<MailAttachment> attachments) {
        Map<String, MailAttachment> grouped = new LinkedHashMap<>();
        for (MailAttachment attachment : attachments == null ? List.<MailAttachment>of() : attachments) {
            String key = key(attachment.itemName(), attachment.variant(), attachment.durability(), attachment.status(),
                    attachment.modifier(), attachment.color());
            MailAttachment prior = grouped.get(key);
            grouped.put(key, prior == null ? attachment : new MailAttachment(prior.itemName(), prior.variant(), prior.amount() + attachment.amount(),
                    prior.checksum(), prior.durability(), prior.status(), prior.modifier(), prior.color()));
        }
        return List.copyOf(grouped.values());
    }

    private static AttachmentCandidate snapshot(String itemName, Item item, String language) {
        String localizedName = MailItemNames.objectDefinition(itemName, item.getVariant()) == null
                ? item.getLocalizedName(language)
                : MailItemNames.localizedName(itemName, item.getVariant(), language);
        if (localizedName == null || localizedName.isBlank()) localizedName = item.getLocalizedName(language);
        String displayName = localizedName == null || localizedName.isBlank()
                ? MailItemNames.label(itemName, item.getVariant())
                : localizedName.trim();
        return new AttachmentCandidate(itemName, displayName, item.getVariant(), item.getStack(),
                item.getDurability(), item.getStatus(), modifierName(item), constructionColor(item));
    }

    private static boolean matches(Item item, MailAttachment attachment) {
        return item != null && item.isValid() && item.getVariant() == attachment.variant()
                && attachment.itemName().equalsIgnoreCase(storedName(item)) && item.getDurability() == attachment.durability()
                && item.getStatus() == attachment.status() && modifierName(item).equals(attachment.modifier())
                && constructionColor(item) == attachment.color();
    }

    private static String storedName(Item item) {
        if (item instanceof Item.ConstructionItem construction) {
            String constructionName = construction.getConstructionName();
            if (constructionName != null && !constructionName.isBlank()) return constructionName.trim();
        }
        if (item instanceof Item.ClothingItem clothing) {
            String clothingName = clothing.getClothingName();
            if (clothingName != null && !clothingName.isBlank()) return clothingName.trim();
        }
        if (item instanceof Item.ObjectItem object && object.getObjectName() != null && !object.getObjectName().isBlank()) return object.getObjectName().trim();
        ItemDefinition definition = item.getDefinition();
        if (definition == null || definition.name == null || definition.name.isBlank()) definition = Definitions.getItemDefinition(item.getTypeID());
        return definition == null || definition.name == null ? "" : definition.name.trim();
    }

    private static ObjectDefinition objectDefinition(String itemName, int variant) {
        ObjectDefinition direct = Definitions.getObjectDefinition(itemName);
        if (direct != null) return direct;
        ItemDefinition item = Definitions.getItemDefinition(itemName);
        return item != null && item.getVariant(variant) != null ? Definitions.getObjectDefinition(item.getVariant(variant).name) : null;
    }

    private static String key(String name, int variant, int durability, short status, String modifier, int color) {
        return name.trim().toLowerCase(Locale.ROOT) + ':' + variant + ':' + durability + ':' + status + ':'
                + (modifier == null ? "" : modifier) + ':' + color;
    }

    private static String modifierName(Item item) { return item.getModifier() == null ? "" : item.getModifier().name(); }

    private static int constructionColor(Item item) {
        return item instanceof Item.ConstructionItem construction ? construction.getColor() : 0;
    }

    private static Modifier modifier(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Modifier.valueOf(value); } catch (IllegalArgumentException ignored) { return null; }
    }

    public record TransferResult(boolean complete, boolean partiallyMutated, int affectedAmount, String detail) {
        static TransferResult completed(int amount) { return new TransferResult(true, false, amount, ""); }
        static TransferResult partial(int amount, String detail) { return new TransferResult(false, true, amount, detail); }
        static TransferResult failed(String detail) { return new TransferResult(false, false, 0, detail); }
    }

    public record AttachmentCandidate(String itemName, String displayName, int variant, int availableAmount, int durability,
            short status, String modifier, int color) {
        public AttachmentCandidate(String itemName, String displayName, int variant, int availableAmount,
                int durability, short status, String modifier) {
            this(itemName, displayName, variant, availableAmount, durability, status, modifier, 0);
        }

        AttachmentCandidate withAmount(int amount) {
            return new AttachmentCandidate(itemName, displayName, variant, amount, durability, status, modifier, color);
        }

        public MailAttachment snapshot(int amount) { return new MailAttachment(itemName, variant, amount,
                itemName + ":" + variant + ":" + durability + ":" + status + ":" + modifier + ":" + color,
                durability, status, modifier, color); }
    }

    public static String displayName(String itemName, int variant, String language) { return MailItemNames.label(itemName, variant, language); }

    public static String displayName(String itemName, int variant) { return MailItemNames.label(itemName, variant); }
}
