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
     * <p>
     * Placeholders in the template (e.g., {@code {duration}}, {@code {index}})
     * are kept intact and will be resolved later by {@code ItemCompiler} using
     * the {@code InventoryContext}.
     * </p>
     * 
     * <p>
     * This is the recommended method to use. It ensures that dynamic values
     * are properly resolved on each render, preventing caching issues.
     * </p>
     *
     * @param inventoryId The ID of the inventory containing the template.
     * @param itemId      The ID/Type of the item to use as a template.
     * @return A builder initialized with the template's properties (with
     *         placeholders intact),
     *         or null if not found.
     * @since 1.5.3
     */
    @Nullable
    GuiItem.Builder loadTemplate(@NotNull String inventoryId, @NotNull String itemId);

    /**
     * Loads a template item from a registered inventory and resolves placeholders
     * immediately.
     * 
     * <p>
     * <strong>⚠️ DEPRECATED:</strong> This method resolves placeholders at template
     * load time,
     * which causes items to be incorrectly cached as static by
     * {@code ItemCompiler}.
     * This results in dynamic values (like frame duration, player stats, etc.) not
     * updating
     * when they change.
     * </p>
     * 
     * <p>
     * <strong>Use {@link #loadTemplate(String, String)} instead.</strong>
     * </p>
     * 
     * <p>
     * Only use this method if you need to resolve TRULY STATIC placeholders
     * (e.g., plugin version, server name from config) that never change during
     * runtime.
     * </p>
     *
     * @param inventoryId  The ID of the inventory containing the template.
     * @param itemId       The ID/Type of the item to use as a template.
     * @param placeholders Key-value pairs for placeholder replacement in name and
     *                     lore.
     * @return A builder initialized with the template's properties, or null if not
     *         found.
     * @deprecated Use {@link #loadTemplate(String, String)} and let ItemCompiler
     *             resolve
     *             placeholders via InventoryContext to ensure proper dynamic
     *             updates.
     */
    @Deprecated
    @Nullable
    GuiItem.Builder loadTemplate(@NotNull String inventoryId, @NotNull String itemId,
            @NotNull Map<String, String> placeholders);
}
