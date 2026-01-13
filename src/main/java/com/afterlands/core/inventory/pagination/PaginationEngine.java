package com.afterlands.core.inventory.pagination;

import com.afterlands.core.inventory.InventoryConfig;
import com.afterlands.core.inventory.config.InventoryConfigManager;
import com.afterlands.core.inventory.item.GuiItem;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Motor híbrido de paginação para inventários.
 *
 * <p>
 * <b>Responsabilidades:</b>
 * </p>
 * <ul>
 * <li>Gerenciar paginação de itens em 3 modos (NATIVE_ONLY, LAYOUT_ONLY,
 * HYBRID)</li>
 * <li>Parse de layouts configuráveis (caracteres 'O' = content slots)</li>
 * <li>Renderizar navigation controls (next/prev buttons)</li>
 * <li>Distribuir items pelos slots de conteúdo</li>
 * </ul>
 *
 * <p>
 * <b>Thread Safety:</b> Todas as operações são thread-safe. O cache de layouts
 * é compartilhado.
 * </p>
 *
 * <p>
 * <b>Performance:</b> Cache de parsed layouts para evitar re-parsing repetido.
 * </p>
 */
public class PaginationEngine {

    private static final Logger LOGGER = Logger.getLogger(PaginationEngine.class.getName());

    // Cache de layouts parseados (inventory ID -> content slots)
    private final Cache<String, List<Integer>> layoutCache = Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(Duration.ofMinutes(10))
            .build();

    // Navegação default icons (podem ser sobrescritos pela config)
    private static final String DEFAULT_NEXT_NAME = "&aNext Page";
    private static final String DEFAULT_PREV_NAME = "&cPrevious Page";

    // Reference to config manager for default item lookups
    private final InventoryConfigManager configManager;

    /**
     * Constructor with config manager access.
     *
     * @param configManager Config manager for default item lookups
     */
    public PaginationEngine(@Nullable InventoryConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Default constructor (no config manager access).
     */
    public PaginationEngine() {
        this(null);
    }

    /**
     * Cria uma página paginada para um inventário.
     *
     * <p>
     * <b>Thread:</b> Qualquer (thread-safe)
     * </p>
     *
     * @param config       Configuração do inventário
     * @param pageNumber   Número da página (1-indexed)
     * @param contentItems Items a serem paginados
     * @return PaginatedView com items da página + navigation controls
     */
    @NotNull
    public PaginatedView createPage(@NotNull InventoryConfig config, int pageNumber,
            @NotNull List<GuiItem> contentItems) {
        PaginationConfig pagination = config.pagination();
        if (pagination == null) {
            // Sem paginação: retorna todos items
            return new PaginatedView(1, 1, List.of(), mapToSlots(contentItems), Map.of(), Map.of());
        }

        // 1. Extrai content slots baseado no modo
        List<Integer> contentSlots = extractContentSlots(config, pagination);

        // 2. Calcula total de páginas
        int totalPages = calculateTotalPages(contentSlots.size(), contentItems.size());
        int safePage = Math.max(1, Math.min(pageNumber, totalPages));

        // 3. Distribui items pelos slots
        Map<Integer, GuiItem> pageItems = distributeItems(contentItems, contentSlots, safePage, contentSlots.size());

        // 4. Renderiza navigation controls
        Map<Integer, GuiItem> navigationItems = pagination.showNavigation()
                ? renderNavigationControls(safePage, totalPages, pagination, config)
                : Map.of();

        // 5. Items decorativos (bordas, etc.) virão do InventoryConfig.items
        Map<Integer, GuiItem> decorationItems = new HashMap<>();

        return new PaginatedView(safePage, totalPages, contentSlots, pageItems, navigationItems, decorationItems);
    }

    /**
     * Calcula total de páginas necessárias.
     *
     * @param config     Configuração do inventário
     * @param totalItems Total de items a paginar
     * @return Número de páginas (mínimo 1)
     */
    public int getTotalPages(@NotNull InventoryConfig config, int totalItems) {
        PaginationConfig pagination = config.pagination();
        if (pagination == null || totalItems == 0) {
            return 1;
        }

        List<Integer> contentSlots = extractContentSlots(config, pagination);
        return calculateTotalPages(contentSlots.size(), totalItems);
    }

    /**
     * Parse layout e extrai content slots ('O' = content).
     *
     * <p>
     * <b>Layout format:</b>
     * </p>
     * 
     * <pre>
     * layout:
     *   - "xxxxxxxxx"  # Row 0 (slots 0-8): decoração
     *   - "xOOOOOOOx"  # Row 1 (slots 10-16): content
     *   - "xOOOOOOOx"  # Row 2 (slots 19-25): content
     *   - "xxxxNxxxx"  # Row 3 (slot 40): navigation
     * </pre>
     *
     * <p>
     * <b>Caracteres válidos:</b>
     * </p>
     * <ul>
     * <li>'O' = content slot (onde items são colocados)</li>
     * <li>'N' = navigation slot (next/prev buttons)</li>
     * <li>'x', ' ', '-' = decoração/slot vazio</li>
     * </ul>
     *
     * @param layout Layout strings (cada string = 9 chars = 1 row)
     * @return Lista de slots índices onde 'O' foi encontrado
     */
    @NotNull
    public List<Integer> parseContentSlots(@NotNull List<String> layout) {
        List<Integer> slots = new ArrayList<>();

        for (int row = 0; row < layout.size(); row++) {
            String line = layout.get(row);
            if (line.length() != 9) {
                LOGGER.warning("Invalid layout line length: " + line.length() + " (expected 9). Line: " + line);
                continue;
            }

            for (int col = 0; col < 9; col++) {
                char ch = line.charAt(col);
                if (ch == 'O' || ch == 'o') { // Accept both uppercase and lowercase
                    int slot = row * 9 + col;
                    slots.add(slot);
                }
            }
        }

        return slots;
    }

    /**
     * Renderiza navigation controls (next/prev buttons).
     *
     * <p>
     * <b>Placeholders suportados:</b>
     * </p>
     * <ul>
     * <li>{page} - Página atual</li>
     * <li>{total_pages} - Total de páginas</li>
     * </ul>
     *
     * @param currentPage Página atual (1-indexed)
     * @param totalPages  Total de páginas
     * @param pagination  Configuração de paginação
     * @param config      Configuração do inventário (para navigation items
     *                    customizados)
     * @return Map de slot -> GuiItem para navigation
     */
    @NotNull
    public Map<Integer, GuiItem> renderNavigationControls(int currentPage, int totalPages,
            @NotNull PaginationConfig pagination,
            @NotNull InventoryConfig config) {
        Map<Integer, GuiItem> controls = new HashMap<>();

        List<Integer> navSlots = parseNavigationSlots(pagination.layout());

        // Default: 2 navigation slots (prev, next)
        if (navSlots.isEmpty()) {
            // Fallback: use paginationSlots if provided
            navSlots = pagination.paginationSlots();
        }

        if (navSlots.size() < 2) {
            // LOGGER.warning("Not enough navigation slots for pagination. Need at least 2
            // (prev, next).");
            return controls;
        }

        int prevSlot = navSlots.get(0);
        int nextSlot = navSlots.get(1);

        // Previous button
        if (currentPage > 1) {
            GuiItem prevButton = findNavigationItem(config, "prev-page", "previous-page")
                    .orElseGet(() -> createDefaultPrevButton(currentPage, totalPages));
            controls.put(prevSlot, prevButton);
        }

        // Next button
        if (currentPage < totalPages) {
            GuiItem nextButton = findNavigationItem(config, "next-page", "next")
                    .orElseGet(() -> createDefaultNextButton(currentPage, totalPages));
            controls.put(nextSlot, nextButton);
        }

        return controls;
    }

    /**
     * Distribui items pelos slots de conteúdo.
     *
     * <p>
     * Aplica paginação: apenas items da página atual são incluídos.
     * </p>
     *
     * @param items        Todos os items (ordenados)
     * @param contentSlots Slots onde items podem ser colocados
     * @param page         Página atual (1-indexed)
     * @param itemsPerPage Items por página (geralmente = contentSlots.size())
     * @return Map de slot -> GuiItem para a página
     */
    @NotNull
    public Map<Integer, GuiItem> distributeItems(@NotNull List<GuiItem> items,
            @NotNull List<Integer> contentSlots,
            int page, int itemsPerPage) {
        Map<Integer, GuiItem> result = new HashMap<>();

        // Calculate offset for current page
        int offset = (page - 1) * itemsPerPage;
        int limit = Math.min(items.size(), offset + itemsPerPage);

        // Distribute items
        for (int i = offset; i < limit; i++) {
            int slotIndex = i - offset;
            if (slotIndex >= contentSlots.size()) {
                break;
            }

            int targetSlot = contentSlots.get(slotIndex);
            GuiItem item = items.get(i);
            result.put(targetSlot, item);
        }

        return result;
    }

    // ========== Helper Methods ==========

    /**
     * Extrai content slots baseado no modo de paginação.
     */
    @NotNull
    private List<Integer> extractContentSlots(@NotNull InventoryConfig config, @NotNull PaginationConfig pagination) {
        String cacheKey = config.id();

        // Check cache first
        List<Integer> cached = layoutCache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<Integer> slots;

        switch (pagination.mode()) {
            case LAYOUT_ONLY, HYBRID -> {
                // Parse layout
                slots = parseContentSlots(pagination.layout());
                if (slots.isEmpty()) {
                    LOGGER.warning("Layout parsing returned 0 content slots for " + cacheKey
                            + ". Falling back to pagination slots.");
                    slots = pagination.paginationSlots();
                }
            }
            case NATIVE_ONLY -> {
                // Use configured pagination slots
                slots = pagination.paginationSlots();
                if (slots.isEmpty()) {
                    // Fallback: all slots except borders (simple default)
                    slots = generateDefaultContentSlots(config.getSizeInSlots());
                }
            }
            default -> slots = List.of();
        }

        // Cache result
        if (!slots.isEmpty()) {
            layoutCache.put(cacheKey, slots);
        }

        return slots;
    }

    /**
     * Parse navigation slots ('N' markers) do layout.
     */
    @NotNull
    private List<Integer> parseNavigationSlots(@NotNull List<String> layout) {
        List<Integer> slots = new ArrayList<>();

        for (int row = 0; row < layout.size(); row++) {
            String line = layout.get(row);
            if (line.length() != 9)
                continue;

            for (int col = 0; col < 9; col++) {
                char ch = line.charAt(col);
                if (ch == 'N' || ch == 'n') {
                    int slot = row * 9 + col;
                    slots.add(slot);
                }
            }
        }

        return slots;
    }

    /**
     * Calcula total de páginas.
     */
    private int calculateTotalPages(int slotsPerPage, int totalItems) {
        if (slotsPerPage <= 0 || totalItems <= 0) {
            return 1;
        }
        return (int) Math.ceil((double) totalItems / slotsPerPage);
    }

    /**
     * Gera content slots default (todos exceto bordas top/bottom).
     */
    @NotNull
    private List<Integer> generateDefaultContentSlots(int inventorySize) {
        List<Integer> slots = new ArrayList<>();

        // Skip first row (0-8) and last row
        for (int i = 9; i < inventorySize - 9; i++) {
            slots.add(i);
        }

        return slots;
    }

    /**
     * Converte lista de GuiItems para Map<slot, GuiItem> usando seus slots.
     */
    @NotNull
    private Map<Integer, GuiItem> mapToSlots(@NotNull List<GuiItem> items) {
        Map<Integer, GuiItem> map = new HashMap<>();
        items.forEach(item -> map.put(item.getSlot(), item));
        return map;
    }

    /**
     * Busca item de navegação customizado pela config.
     */
    @NotNull
    private Optional<GuiItem> findNavigationItem(@NotNull InventoryConfig config, String... types) {
        // First search in inventory's items by type
        for (String type : types) {
            Optional<GuiItem> found = config.items().stream()
                    .filter(item -> type.equalsIgnoreCase(item.getType()))
                    .findFirst();
            if (found.isPresent()) {
                return found;
            }
        }

        // Fallback: search in default items by key name
        if (configManager != null) {
            for (String type : types) {
                GuiItem defaultItem = configManager.getDefaultItem(type);
                if (defaultItem != null) {
                    return Optional.of(defaultItem);
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Cria botão "Previous" default.
     */
    @NotNull
    private GuiItem createDefaultPrevButton(int currentPage, int totalPages) {
        return new GuiItem.Builder()
                .type("prev-page")
                .material(org.bukkit.Material.ARROW)
                .name(DEFAULT_PREV_NAME.replace("{page}", String.valueOf(currentPage - 1)))
                .addLoreLine("&7Click to go to page " + (currentPage - 1))
                .addAction("pagination:prev")
                .build();
    }

    /**
     * Cria botão "Next" default.
     */
    @NotNull
    private GuiItem createDefaultNextButton(int currentPage, int totalPages) {
        return new GuiItem.Builder()
                .type("next-page")
                .material(org.bukkit.Material.ARROW)
                .name(DEFAULT_NEXT_NAME.replace("{page}", String.valueOf(currentPage + 1)))
                .addLoreLine("&7Click to go to page " + (currentPage + 1))
                .addAction("pagination:next")
                .build();
    }

    /**
     * Limpa cache de layouts.
     *
     * <p>
     * Útil para reload de configurações.
     * </p>
     */
    public void clearCache() {
        layoutCache.invalidateAll();
        LOGGER.info("PaginationEngine layout cache cleared.");
    }

    /**
     * Estatísticas do cache.
     */
    @NotNull
    public String getCacheStats() {
        return "PaginationEngine Cache: " + layoutCache.estimatedSize() + " entries";
    }
}
