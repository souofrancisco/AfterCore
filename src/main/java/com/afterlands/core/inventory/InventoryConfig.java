package com.afterlands.core.inventory;

import com.afterlands.core.inventory.animation.AnimationConfig;
import com.afterlands.core.inventory.item.GuiItem;
import com.afterlands.core.inventory.pagination.PaginationConfig;
import com.afterlands.core.inventory.tab.TabConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Configuração de um inventário.
 *
 * <p>
 * Carregado de inventories.yml ou registrado programaticamente.
 * </p>
 *
 * <p>
 * Imutável (record) para thread safety.
 * </p>
 */
public record InventoryConfig(
        @NotNull String id,
        @NotNull String title,
        int size,
        @NotNull List<GuiItem> items,
        @NotNull List<TabConfig> tabs,
        @Nullable PaginationConfig pagination,
        @NotNull List<AnimationConfig> animations,
        @NotNull PersistenceConfig persistence,
        boolean shared,
        int titleUpdateInterval, // Intervalo de update do título em ticks (0 = disabled)
        @NotNull Map<String, Object> metadata,
        @NotNull Map<String, GuiItem> variantItems) { // New field for variants

    /**
     * Construtor compacto com validação.
     */
    public InventoryConfig {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id cannot be null or blank");
        }
        if (title == null) {
            title = "";
        }
        if (size < 1 || size > 6) {
            throw new IllegalArgumentException("size must be between 1 and 6 (lines)");
        }
        if (items == null) {
            items = List.of();
        }
        if (tabs == null) {
            tabs = List.of();
        }
        if (animations == null) {
            animations = List.of();
        }
        if (persistence == null) {
            persistence = PersistenceConfig.disabled();
        }
        if (titleUpdateInterval < 0) {
            throw new IllegalArgumentException("titleUpdateInterval cannot be negative");
        }
        if (metadata == null) {
            metadata = Map.of();
        }
        if (variantItems == null) {
            variantItems = Map.of();
        }
    }

    /**
     * Configuração de persistência.
     */
    public record PersistenceConfig(
            boolean enabled,
            boolean autoSave,
            int saveIntervalSeconds) {
        public static PersistenceConfig disabled() {
            return new PersistenceConfig(false, false, 0);
        }

        public static PersistenceConfig enabled(boolean autoSave, int saveIntervalSeconds) {
            return new PersistenceConfig(true, autoSave, saveIntervalSeconds);
        }
    }

    /**
     * Obtém metadata.
     *
     * @param key Chave
     * @return Valor ou null
     */
    @Nullable
    public Object getMetadata(@NotNull String key) {
        return metadata.get(key);
    }

    /**
     * Obtém metadata com cast type-safe.
     *
     * @param key  Chave
     * @param type Classe do tipo
     * @param <T>  Tipo
     * @return Valor ou null
     */
    @Nullable
    public <T> T getMetadata(@NotNull String key, @NotNull Class<T> type) {
        Object value = metadata.get(key);
        if (value != null && type.isInstance(value)) {
            return type.cast(value);
        }
        return null;
    }

    /**
     * Creates a copy of this config with a different ID.
     *
     * @param newId New inventory ID
     * @return New InventoryConfig with the specified ID
     */
    @NotNull
    public InventoryConfig withId(@NotNull String newId) {
        return new InventoryConfig(
                newId,
                title,
                size,
                items,
                tabs,
                pagination,
                animations,
                persistence,
                shared,
                titleUpdateInterval,
                metadata,
                variantItems);
    }

    /**
     * Calcula tamanho em slots (linhas * 9).
     *
     * @return Número de slots
     */
    public int getSizeInSlots() {
        return size * 9;
    }

    @Override
    public String toString() {
        return "InventoryConfig{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", size=" + size +
                ", items=" + items.size() +
                ", tabs=" + tabs.size() +
                ", animations=" + animations.size() +
                ", shared=" + shared +
                '}';
    }
}
