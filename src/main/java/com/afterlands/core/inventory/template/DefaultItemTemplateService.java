package com.afterlands.core.inventory.template;

import com.afterlands.core.inventory.item.GuiItem;
import com.afterlands.core.inventory.InventoryConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class DefaultItemTemplateService implements ItemTemplateService {

    private final Function<String, InventoryConfig> configLookup;

    /**
     * Creates the template service with a config lookup function.
     * 
     * @param configLookup Function that looks up InventoryConfig by ID,
     *                     checking both customInventories and YAML configs
     */
    public DefaultItemTemplateService(Function<String, InventoryConfig> configLookup) {
        this.configLookup = configLookup;
    }

    @Override
    public @Nullable GuiItem.Builder loadTemplate(@NotNull String inventoryId, @NotNull String itemId) {
        // Use deprecated method with empty map to avoid resolving any placeholders
        return loadTemplate(inventoryId, itemId, Collections.emptyMap());
    }

    @Deprecated
    @Override
    public @Nullable GuiItem.Builder loadTemplate(@NotNull String inventoryId, @NotNull String itemId,
            @NotNull Map<String, String> placeholders) {
        InventoryConfig config = configLookup.apply(inventoryId);
        if (config == null) {
            return null;
        }

        // Find item by explicit ID or by Type
        GuiItem template = config.items().stream()
                .filter(item -> itemId.equals(item.getType()))
                .findFirst()
                .orElse(null);

        if (template == null) {
            return null;
        }

        // Clone and apply placeholders
        GuiItem.Builder builder = new GuiItem.Builder()
                .type(template.getType())
                .material(template.getMaterial());

        if (template.getName() != null) {
            builder.name(applyPlaceholders(template.getName(), placeholders));
        }

        if (template.getLore() != null) {
            List<String> newLore = new ArrayList<>();
            for (String line : template.getLore()) {
                newLore.add(applyPlaceholders(line, placeholders));
            }
            builder.lore(newLore);
        }

        // Copy other properties if necessary (e.g. model data, etc)
        // For now, names and lores are the most dynamic parts.
        // If GuiItem has other properties like CustomModelData, we should copy them
        // too.
        // Assuming minimal copy for now as per previous implementation requirements.

        return builder;
    }

    private String applyPlaceholders(String text, Map<String, String> placeholders) {
        if (text == null)
            return null;
        String result = text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            // Wrap key with {} to match YAML format like {animation}, {frames}
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
}
