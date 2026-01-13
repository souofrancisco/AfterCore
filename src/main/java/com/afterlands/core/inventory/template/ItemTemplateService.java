package com.afterlands.core.inventory.template;

import com.afterlands.core.inventory.item.GuiItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Service generic to load item templates from inventory configurations.
 */
public interface ItemTemplateService {

    /**
     * Loads a template item from a registered inventory.
     *
     * @param inventoryId  The ID of the inventory containing the template.
     * @param itemId       The ID/Type of the item to use as a template.
     * @param placeholders Key-value pairs for placeholder replacement in name and
     *                     lore.
     * @return A builder initialized with the template's properties, or null if not
     *         found.
     */
    @Nullable
    GuiItem.Builder loadTemplate(@NotNull String inventoryId, @NotNull String itemId,
            @NotNull Map<String, String> placeholders);
}
