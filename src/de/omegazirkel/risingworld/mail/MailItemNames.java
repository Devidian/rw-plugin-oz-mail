package de.omegazirkel.risingworld.mail;

import java.util.Locale;

import net.risingworld.api.definitions.Clothing.ClothingDefinition;
import net.risingworld.api.definitions.Constructions.ConstructionDefinition;
import net.risingworld.api.definitions.Definitions;
import net.risingworld.api.definitions.Items.ItemDefinition;
import net.risingworld.api.definitions.Items.ItemDefinition.Variant;
import net.risingworld.api.definitions.Objects.ObjectDefinition;
import net.risingworld.api.definitions.Plants.PlantDefinition;

/** Definition-aware attachment labels, intentionally matching OZShop's resolution rules. */
public final class MailItemNames {
    private MailItemNames() { }

    public static String label(String itemName, int itemVariant) {
        ObjectDefinition objectDefinition = objectDefinition(itemName, itemVariant);
        int labelVariant = objectDefinition == null ? itemVariant : objectVariant(itemName, itemVariant, objectDefinition);
        ItemDefinition itemDefinition = Definitions.getItemDefinition(itemName);
        if (objectDefinition == null && itemDefinition != null) {
            Variant variant = itemDefinition.getVariant(itemVariant);
            String displayName = blank(itemDefinition.name) ? itemName : itemDefinition.name;
            if (variant != null) return isDefaultVariantName(variant.name) ? derivedBaseName(displayName)
                    : derivedBaseName(displayName) + " " + derivedBaseName(variant.name);
        }
        String baseName = objectDisplayName(itemName, itemVariant);
        if (blank(baseName) && objectDefinition == null) baseName = objectVariantDisplayName(itemName, itemVariant);
        if (blank(baseName)) baseName = itemVariantDisplayName(itemName, itemVariant);
        if (blank(baseName)) baseName = directDefinitionDisplayName(itemName);
        if (blank(baseName)) baseName = itemName;
        return labelVariant == 0 ? derivedBaseName(baseName) : derivedBaseName(baseName) + "-" + labelVariant;
    }

    private static ObjectDefinition objectDefinition(String itemName, int itemVariant) {
        ObjectDefinition direct = Definitions.getObjectDefinition(itemName);
        if (direct != null) return direct;
        ItemDefinition itemDefinition = Definitions.getItemDefinition(itemName);
        if (itemDefinition != null) {
            Variant variant = itemDefinition.getVariant(itemVariant);
            if (variant != null && !isDefaultVariantName(variant.name)) {
                ObjectDefinition byVariantName = Definitions.getObjectDefinition(variant.name);
                if (byVariantName != null) return byVariantName;
            }
        }
        return itemName != null && itemName.toLowerCase(Locale.ROOT).startsWith("objectkit")
                ? Definitions.getObjectDefinition(itemVariant) : null;
    }

    private static int objectVariant(String itemName, int itemVariant, ObjectDefinition objectDefinition) {
        return objectDefinition == null || Definitions.getObjectDefinition(itemName) != null ? itemVariant : 0;
    }

    private static String objectVariantDisplayName(String itemName, int itemVariant) {
        ObjectDefinition definition = objectDefinition(itemName, itemVariant);
        if (definition == null) return "";
        ObjectDefinition.Variant variant = definition.getVariant(objectVariant(itemName, itemVariant, definition));
        return variant == null || isDefaultVariantName(variant.name) ? "" : variant.name;
    }

    private static String objectDisplayName(String itemName, int itemVariant) {
        ObjectDefinition definition = objectDefinition(itemName, itemVariant);
        return definition == null || blank(definition.name) ? "" : definition.name;
    }

    private static String itemVariantDisplayName(String itemName, int itemVariant) {
        ItemDefinition definition = Definitions.getItemDefinition(itemName);
        if (definition == null) return "";
        Variant variant = definition.getVariant(itemVariant);
        return variant == null || isDefaultVariantName(variant.name)
                ? (definition.name == null ? "" : definition.name) : variant.name;
    }

    private static String directDefinitionDisplayName(String itemName) {
        ConstructionDefinition construction = Definitions.getConstructionDefinition(itemName);
        if (construction != null && !blank(construction.name)) return construction.name;
        ClothingDefinition clothing = Definitions.getClothingDefinition(itemName);
        if (clothing != null && !blank(clothing.name)) return clothing.name;
        PlantDefinition plant = Definitions.getPlantDefinition(itemName);
        return plant != null && !blank(plant.name) ? plant.name : "";
    }

    private static boolean isDefaultVariantName(String name) {
        return blank(name) || name.trim().equalsIgnoreCase("default");
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    private static String derivedBaseName(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) return "Item";
        normalized = normalized.replace('_', ' ').replace('-', ' ');
        StringBuilder result = new StringBuilder();
        for (String part : normalized.split("\\s+")) {
            if (part.isBlank()) continue;
            if (result.length() > 0) result.append(' ');
            result.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            if (part.length() > 1) result.append(part.substring(1));
        }
        return result.toString();
    }
}
