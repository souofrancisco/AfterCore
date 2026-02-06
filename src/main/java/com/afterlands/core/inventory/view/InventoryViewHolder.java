package com.afterlands.core.inventory.view;

import com.afterlands.core.concurrent.SchedulerService;
import com.afterlands.core.inventory.InventoryConfig;
import com.afterlands.core.inventory.InventoryContext;
import com.afterlands.core.conditions.ConditionContext;
import com.afterlands.core.inventory.InventoryState;
import com.afterlands.core.inventory.action.InventoryActionHandler;
import com.afterlands.core.inventory.animation.AnimationConfig;
import com.afterlands.core.inventory.animation.InventoryAnimator;
import com.afterlands.core.inventory.drag.DragAndDropHandler;
import com.afterlands.core.inventory.item.GuiItem;
import com.afterlands.core.inventory.item.ItemCompiler;
import com.afterlands.core.inventory.pagination.PaginatedView;
import com.afterlands.core.inventory.pagination.PaginationEngine;
import com.afterlands.core.inventory.tab.TabManager;
import com.afterlands.core.inventory.tab.TabState;
import com.afterlands.core.inventory.title.TitleUpdateSupport;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import com.afterlands.core.conditions.ConditionService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holder de um inventário aberto.
 *
 * <p>
 * Gerencia o Bukkit Inventory, estado, renderização de itens e eventos.
 * </p>
 *
 * <p>
 * <b>Thread:</b> Main thread para operações de inventário.
 * </p>
 */
public class InventoryViewHolder implements Listener {

    // Static registry for active view holders
    private static final Map<UUID, InventoryViewHolder> activeHolders = new ConcurrentHashMap<>();

    private final Plugin plugin;
    private final Player player;
    private final InventoryConfig config;
    private final InventoryContext context;
    private final SchedulerService scheduler;
    private final ItemCompiler itemCompiler;
    private final ConditionService conditionService;

    // Pagination & Tabs
    private final PaginationEngine paginationEngine;
    private final TabManager tabManager;

    // Action & Drag handlers
    private final InventoryActionHandler actionHandler;
    private final DragAndDropHandler dragHandler;

    // Animation handler
    private final InventoryAnimator animator;

    // Title update support
    private final TitleUpdateSupport titleSupport;

    private InventoryState state;
    private String currentTitle; // Cache do último título
    private TabState tabState;
    private Inventory inventory;
    private boolean shared;
    private String sharedSessionId; // ID da sessão compartilhada (se aplicável)

    // Current page for pagination
    private int currentPage = 1;
    private List<GuiItem> contentItems = new ArrayList<>();
    private boolean contentItemsLoaded = false; // Flag to prevent reloading on page change

    // Map slot -> GuiItem for quick lookup
    private final Map<Integer, GuiItem> slotToGuiItem = new HashMap<>();

    public InventoryViewHolder(
            @NotNull Plugin plugin,
            @NotNull Player player,
            @NotNull InventoryConfig config,
            @NotNull InventoryContext context,
            @NotNull InventoryState state,
            @NotNull SchedulerService scheduler,
            @NotNull ItemCompiler itemCompiler,
            @NotNull PaginationEngine paginationEngine,
            @NotNull TabManager tabManager,
            @NotNull InventoryActionHandler actionHandler,
            @NotNull DragAndDropHandler dragHandler,
            @NotNull InventoryAnimator animator,
            @NotNull TitleUpdateSupport titleSupport,
            @NotNull ConditionService conditionService) {
        this.plugin = plugin;
        this.player = player;
        this.config = config;
        this.context = context;
        this.state = state;
        this.scheduler = scheduler;
        this.itemCompiler = itemCompiler;
        this.paginationEngine = paginationEngine;
        this.tabManager = tabManager;
        this.actionHandler = actionHandler;
        this.dragHandler = dragHandler;
        this.animator = animator;
        this.titleSupport = titleSupport;
        this.conditionService = conditionService;
        this.shared = false;

        // Cache initial title
        this.currentTitle = context.resolvePlaceholders(config.title());

        // Initialize tab state
        this.tabState = tabManager.createInitialState(config, player.getUniqueId());

        // Restore tab state from inventory state if available
        restoreTabStateFromInventoryState();

        createInventory();

        // Cleanup any existing holder for this player to prevent orphaned holders
        InventoryViewHolder existingHolder = activeHolders.get(player.getUniqueId());
        if (existingHolder != null && existingHolder != this) {
            existingHolder.cleanupWithoutClose();
        }

        // Register in static map
        activeHolders.put(player.getUniqueId(), this);

        // Register event listener
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Cria o Bukkit Inventory.
     */
    private void createInventory() {
        String title = context.resolvePlaceholders(config.title()).replace("&", "§");
        int size = config.getSizeInSlots();

        // Validate title length (1.8.8 limit: 32 chars)
        title = validateTitle(title, config.title());

        this.inventory = Bukkit.createInventory(null, size, title);

        // Renderiza itens
        renderItems();
    }

    /**
     * Validates and fixes inventory title to fit 1.8.8 limit (32 chars).
     *
     * <p>
     * Does NOT truncate. Instead, tries fallback strategies:
     * </p>
     * <ol>
     * <li>If resolved title exceeds 32 chars, log warning and try raw config
     * title</li>
     * <li>If raw title also exceeds, use simple fallback "Inventory"</li>
     * </ol>
     *
     * @param resolvedTitle Title with placeholders resolved and formatted
     * @param rawTitle      Raw title from config (before resolution)
     * @return Valid title (max 32 chars)
     */
    @NotNull
    private String validateTitle(@NotNull String resolvedTitle, @NotNull String rawTitle) {
        final int MAX_TITLE_LENGTH = 32;

        // Ensure we are working with § codes for correct length check
        String titleToCheck = resolvedTitle.contains("&") ? resolvedTitle.replace("&", "§") : resolvedTitle;

        // Strip color codes for length calculation (color codes don't count towards
        // limit)
        String strippedTitle = org.bukkit.ChatColor.stripColor(titleToCheck);

        if (strippedTitle.length() <= MAX_TITLE_LENGTH) {
            return titleToCheck; // OK
        }

        // Title exceeds limit - log warning and truncate
        boolean debug = plugin.getConfig().getBoolean("debug", false);
        if (debug) {
            plugin.getLogger().warning(
                    "[InventoryViewHolder] Title exceeds 32 chars (" + strippedTitle.length() + "): '"
                            + strippedTitle + "'. Truncating.");
        }

        // Truncate logic:
        // 1. We start from the titleToCheck (with § codes)
        // 2. We allow at most 32 VISIBLE characters
        // But Bukkit 1.8.8 limit is actually 32 CHARACTERS TOTAL in the packet string
        // for window title?
        // Wait, standard 1.7/1.8 limits are 32 chars INCLUDING color codes usually for
        // custom names,
        // but for Inventory Titles it allows 32 chars value.
        // Actually, Spigot 1.8.8 verifies absolute length of the string passed to
        // packet.

        // Safe bet: Truncate the string absolute length to 32.
        // But preventing color code split (e.g. at position 31 matches '§')

        if (titleToCheck.length() > MAX_TITLE_LENGTH) {
            String truncated = titleToCheck.substring(0, MAX_TITLE_LENGTH);
            if (truncated.endsWith("§")) {
                truncated = truncated.substring(0, truncated.length() - 1);
            }
            return truncated;
        }

        return titleToCheck;
    }

    /**
     * Resolve a variante correta para o item base com base nas condições.
     *
     * <p>
     * Se nenhuma variante for aplicável (ou se o item não tiver variantes),
     * retorna o próprio item base.
     * </p>
     *
     * @param item Item base
     * @return Item resolvido (base ou variante)
     */
    @NotNull
    private GuiItem resolveVariant(@NotNull GuiItem item) {
        if (!item.hasVariants()) {
            return item;
        }

        // 1. Tentar inline variants
        for (GuiItem variant : item.getInlineVariants()) {
            if (areConditionsMet(variant.getViewConditions())) {
                return variant;
            }
        }

        // 2. Tentar variants por referência
        for (String ref : item.getVariantRefs()) {
            GuiItem variant = config.variantItems().get(ref);
            if (variant != null) {
                if (areConditionsMet(variant.getViewConditions())) {
                    return variant;
                }
            } else {
                // Log warning if variant not found?
                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().warning("[ViewHolder] Variant ref not found: " + ref);
                }
            }
        }

        // Fallback: retorna o item base
        return item;
    }

    /**
     * Avalia lista de condições de visualização.
     *
     * @param conditions Lista de condições
     * @return true se todas forem satisfeitas (ou lista vazia)
     */
    private boolean areConditionsMet(@NotNull List<String> conditions) {
        if (conditions.isEmpty()) {
            return true;
        }

        ConditionContext conditionContext = java.util.Collections::emptyMap;
        for (String condition : conditions) {
            if (!conditionService.evaluateSync(player, condition, conditionContext)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Renderiza todos os itens no inventário.
     *
     * <p>
     * Usa ItemCompiler para compilação otimizada com cache.
     * </p>
     * <p>
     * Suporta paginação e tabs.
     * </p>
     *
     * <p>
     * <b>Thread:</b> MAIN THREAD
     * </p>
     */
    private void renderItems() {
        // Clear inventory and slot map
        inventory.clear();
        slotToGuiItem.clear();

        Map<Integer, GuiItem> itemsToRender = new HashMap<>();

        // DEBUG: Log config items count
        boolean debug = plugin.getConfig().getBoolean("debug", false);
        if (debug) {
            plugin.getLogger().info("[ViewHolder] DEBUG: renderItems() - config.items().size()=" + config.items().size()
                    + ", inventoryId=" + config.id());
        }

        // Load contentItems from context if available (for pagination)
        // Only load ONCE per holder lifecycle to prevent reset on page navigation
        if (!contentItemsLoaded) {
            @SuppressWarnings("unchecked")
            List<GuiItem> contextContentItems = context.getData("contentItems", List.class).orElse(null);
            if (contextContentItems != null && !contextContentItems.isEmpty()) {
                this.contentItems = new ArrayList<>(contextContentItems);
                this.contentItemsLoaded = true;
                if (debug) {
                    plugin.getLogger()
                            .info("[ViewHolder] DEBUG: Loaded " + contentItems.size()
                                    + " content items from context");
                }
            }
        }

        // 1. Static items from config (borders, decorations, etc.)
        // Track filler items with duplicate: all for later processing
        GuiItem fillerItem = null;

        for (GuiItem rawItem : config.items()) {
            // Resolver variants (alternativas baseadas em condição)
            GuiItem guiItem = resolveVariant(rawItem);

            // Special handling: conditionally hide navigation items if not needed
            // This allows defining them in YAML but having them disappear when invalid
            if (config.pagination() != null) {
                String type = guiItem.getType();
                if (type != null) {
                    if ("prev-page".equalsIgnoreCase(type) || "previous-page".equalsIgnoreCase(type)) {
                        if (currentPage <= 1) {
                            continue; // Skip rendering previous button on first page
                        }
                    } else if ("next-page".equalsIgnoreCase(type)) {
                        // Calculate total pages just-in-time or reuse if cached
                        // We use contentItems.size() which is already loaded
                        int total = paginationEngine.getTotalPages(config, contentItems.size());
                        if (currentPage >= total) {
                            continue; // Skip rendering next button on last page
                        }
                    }
                }
            }

            // Check for duplicate: all marker (-1 in duplicateSlots) using RAW item for
            // positioning
            List<Integer> dupSlots = rawItem.getDuplicateSlots();
            if (!dupSlots.isEmpty() && dupSlots.get(0) == -1) {
                // This is a filler item with duplicate: all
                fillerItem = guiItem;
                itemsToRender.put(rawItem.getSlot(), guiItem);
            } else {
                itemsToRender.put(rawItem.getSlot(), guiItem);
                // Handle explicit duplicate slots
                for (int dupSlot : dupSlots) {
                    itemsToRender.put(dupSlot, guiItem);
                }
            }

            // DEBUG: Log each item
            if (debug) {
                plugin.getLogger().info("[ViewHolder] DEBUG: Item at slot " + rawItem.getSlot()
                        + " - type=" + guiItem.getType()
                        + ", material=" + guiItem.getMaterial()
                        + ", actions=" + guiItem.getActions().size()
                        + ", hasClickHandlers=" + guiItem.hasClickHandlers()
                        + ", duplicate=" + (dupSlots.isEmpty() ? "none" : (dupSlots.get(0) == -1 ? "all" : dupSlots))
                        + ", variant=" + (guiItem != rawItem));
            }
        }

        // 2. Programmatic items from context (injected by plugins) - OVERRIDE YAML
        // items
        @SuppressWarnings("unchecked")
        List<GuiItem> programmaticItems = context.getData("programmaticItems", List.class)
                .orElse(Collections.emptyList());
        if (!programmaticItems.isEmpty()) {
            for (GuiItem guiItem : programmaticItems) {
                itemsToRender.put(guiItem.getSlot(), guiItem);
                if (debug) {
                    plugin.getLogger()
                            .info("[ViewHolder] DEBUG: Programmatic item OVERRIDE at slot " + guiItem.getSlot()
                                    + " - type=" + guiItem.getType()
                                    + ", hasClickHandlers=" + guiItem.hasClickHandlers());
                }
            }
        }

        // 2. Tab system integration
        if (!config.tabs().isEmpty())

        {

            Map<Integer, GuiItem> tabItems = tabManager.renderComplete(tabState, config);
            itemsToRender.putAll(tabItems); // Tab
                                            // items
                                            // can
                                            // override
                                            // static
                                            // items
        }

        // 3. Pagination integration
        if (config.pagination() != null) {
            PaginatedView page = paginationEngine.createPage(config, currentPage, contentItems);

            // Add page items
            itemsToRender.putAll(page.pageItems());

            // Add navigation controls
            itemsToRender.putAll(page.navigationItems());

            // CRITICAL: Reserve all content slots (even empty ones) so duplicate:all
            // doesn't fill them
            // This ensures pagination content slots have priority over filler items
            for (Integer contentSlot : page.contentSlots()) {
                if (!itemsToRender.containsKey(contentSlot)) {
                    // Mark slot as occupied (with AIR) to prevent duplicate:all from filling it
                    itemsToRender.put(contentSlot, null); // null = AIR in Bukkit
                }
            }

            // Update context with pagination placeholders
            context.withPlaceholder("page", String.valueOf(page.currentPage()));
            context.withPlaceholder("nextpage", String.valueOf(page.currentPage() + 1));
            context.withPlaceholder("total_pages", String.valueOf(page.totalPages()));
            context.withPlaceholder("lastpage", String.valueOf(page.totalPages()));
            context.withPlaceholder("has_next_page", String.valueOf(page.hasNextPage()));
            context.withPlaceholder("has_previous_page", String.valueOf(page.hasPreviousPage()));
        }

        // 4. Fill empty slots with filler item if duplicate: all was used (AFTER
        // pagination)
        if (fillerItem != null) {
            int inventorySize = config.getSizeInSlots();
            for (int slot = 0; slot < inventorySize; slot++) {
                if (!itemsToRender.containsKey(slot)) {
                    itemsToRender.put(slot, fillerItem);
                }
            }
            if (debug) {
                plugin.getLogger().info("[ViewHolder] DEBUG: Applied duplicate:all filler to empty slots, total items="
                        + itemsToRender.size());
            }
        } else if (debug) {
            plugin.getLogger().info("[ViewHolder] DEBUG: No duplicate:all filler item found.");
        }

        // Store slot -> GuiItem mapping for event handling
        slotToGuiItem.putAll(itemsToRender);

        // 4. Compile and render all items
        List<CompletableFuture<Void>> compilationFutures = new ArrayList<>();

        for (Map.Entry<Integer, GuiItem> entry : itemsToRender.entrySet()) {
            int slot = entry.getKey();
            GuiItem guiItem = entry.getValue();

            // Skip null items (reserved slots for pagination, will remain as AIR)
            if (guiItem == null) {
                continue;
            }

            // Check view conditions using empty context (placeholders handled by PAPI in
            // evaluateSync)
            if (!guiItem.getViewConditions().isEmpty()) {
                ConditionContext conditionContext = java.util.Collections::emptyMap;
                boolean canView = true;
                for (String condition : guiItem.getViewConditions()) {
                    if (!conditionService.evaluateSync(player, condition, conditionContext)) {
                        canView = false;
                        break;
                    }
                }
                if (!canView) {
                    continue;
                }
            }

            CompletableFuture<Void> future = itemCompiler.compile(guiItem, player, context)
                    .thenAccept(item -> {
                        if (slot >= 0 && slot < inventory.getSize()) {
                            inventory.setItem(slot, item);
                        }
                    });

            compilationFutures.add(future);
        }

        // Wait for all compilations (we're already on main thread)
        CompletableFuture.allOf(compilationFutures.toArray(new CompletableFuture[0])).join();
    }

    /**
     * Abre o inventário para o player.
     */
    public void open() {
        if (!Bukkit.isPrimaryThread()) {
            scheduler.runSync(() -> open());
            return;
        }

        // Check if title needs update due to context changes (e.g. pagination {page})
        // This avoids opening with wrong title then swapping (flicker/empty bug)
        String resolved = context.resolvePlaceholders(config.title());
        if (!resolved.equals(currentTitle)) {
            // Update cache
            currentTitle = resolved;

            // Recreate inventory with correct title
            // Translate colors BEFORE validation to ensure counts are correct
            String translated = resolved.replace("&", "§");
            String formatted = validateTitle(translated, config.title());
            this.inventory = Bukkit.createInventory(null, inventory.getSize(), formatted);

            // Re-render items into new inventory
            renderItems();
        }

        player.openInventory(inventory);

        // Inicia animações após abrir inventário
        startAnimations();
    }

    /**
     * Fecha o inventário.
     */
    public void close() {
        if (!Bukkit.isPrimaryThread()) {
            scheduler.runSync(() -> close());
            return;
        }

        if (player.getOpenInventory() != null && player.getOpenInventory().getTopInventory().equals(inventory)) {
            player.closeInventory();
        }

        // Cleanup
        cleanup();
    }

    /**
     * Cleanup de recursos quando o inventário é fechado.
     */
    private void cleanup() {
        // Para todas as animações deste inventário
        stopAnimations();

        // Remove from static registry ONLY if this holder is still the registered one
        // This prevents race condition where new holder was registered before old one's
        // close event
        activeHolders.remove(player.getUniqueId(), this);

        // Cancel any active drag session
        dragHandler.cancelDrag(player.getUniqueId());

        // Unregister event listener
        InventoryCloseEvent.getHandlerList().unregister(this);
        InventoryClickEvent.getHandlerList().unregister(this);
        InventoryDragEvent.getHandlerList().unregister(this);
    }

    /**
     * Cleanup resources without closing the player's inventory.
     *
     * <p>
     * Used when replacing a holder with a new one for the same player.
     * This prevents the old holder from interfering with the new one.
     * </p>
     */
    private void cleanupWithoutClose() {
        // Stop animations for this holder
        stopAnimations();

        // Cancel any active drag session
        dragHandler.cancelDrag(player.getUniqueId());

        // Unregister event listeners to prevent double-handling
        InventoryCloseEvent.getHandlerList().unregister(this);
        InventoryClickEvent.getHandlerList().unregister(this);
        InventoryDragEvent.getHandlerList().unregister(this);

        // Note: Don't remove from activeHolders - the new holder will overwrite
    }

    /**
     * Inicia animações ao abrir inventário.
     *
     * <p>
     * Itera sobre todos os items configurados e inicia animações para aqueles
     * que possuem AnimationConfig.
     * </p>
     */
    private void startAnimations() {
        for (GuiItem item : config.items()) {
            if (item.hasAnimations()) {
                for (AnimationConfig anim : item.getAnimations()) {
                    animator.startAnimation(config.id(), player.getUniqueId(), item.getSlot(), anim);
                }
            }
        }
    }

    /**
     * Para animações ao fechar inventário.
     *
     * <p>
     * Remove todas as animações ativas deste inventário.
     * </p>
     */
    private void stopAnimations() {
        animator.stopAllAnimations(config.id(), player.getUniqueId());
    }

    // ========== Dynamic Title Methods ==========

    /**
     * Atualiza o título do inventário dinamicamente via packets.
     *
     * <p>
     * Se ProtocolLib disponível, usa packet. Caso contrário, reabre inventário.
     * </p>
     *
     * <p>
     * <b>Thread:</b> Main thread only.
     * </p>
     *
     * @param newTitle Novo título (suporta placeholders e color codes)
     */
    public void updateTitle(@NotNull String newTitle) {
        if (!Bukkit.isPrimaryThread()) {
            scheduler.runSync(() -> updateTitle(newTitle));
            return;
        }

        // Resolver placeholders
        String resolved = context.resolvePlaceholders(newTitle);

        // Evitar update desnecessário (cache optimization)
        if (resolved.equals(currentTitle)) {
            return;
        }

        // Atualizar via packet
        boolean success = titleSupport.updateTitle(player, inventory, resolved);

        if (success) {
            currentTitle = resolved;
        } else {
            // Fallback: reabrir inventário (menos smooth)
            reopenWithNewTitle(resolved);
        }
    }

    /**
     * Fallback: reabre inventário com novo título.
     *
     * <p>
     * Usado quando ProtocolLib não está disponível.
     * </p>
     *
     * <p>
     * <b>Thread:</b> Main thread only.
     * </p>
     */
    private boolean reopening = false;

    /**
     * Fallback: reabre inventário com novo título.
     *
     * <p>
     * Usado quando ProtocolLib não está disponível.
     * </p>
     *
     * <p>
     * <b>Thread:</b> Main thread only.
     * </p>
     */
    private void reopenWithNewTitle(@NotNull String newTitle) {
        // Set reopening flag to prevent cleanup in onInventoryClose
        this.reopening = true;

        try {
            // Para animações antes de fechar
            stopAnimations();

            // NÃO chamar cleanInventory() explicitamente, pois openInventory()
            // já faz o swap corretamente no Bukkit e evita packets redundantes.

            // Criar novo inventário com novo título
            String formatted = newTitle.replace("&", "§");
            this.inventory = Bukkit.createInventory(null, inventory.getSize(), formatted);

            // Re-renderizar itens
            renderItems();

            // Reabrir (fecha o anterior automaticamente)
            player.openInventory(inventory);

            // Reiniciar animações
            startAnimations();
        } finally {
            // Reset flag (in case logic runs synchronously)
            // Note: InventoryCloseEvent fires synchronously during openInventory() usually
            this.reopening = false;
        }

        // Atualizar cache
        currentTitle = newTitle;
    }

    /**
     * Inicia task para atualizar título periodicamente (para placeholders
     * dinâmicos).
     *
     * <p>
     * <b>Performance:</b> ~0.1ms/tick overhead per active inventory.
     * </p>
     *
     * @param interval Intervalo em ticks (20 ticks = 1 segundo)
     */
    public void startTitleUpdateTask(int interval) {
        if (interval <= 0) {
            return;
        }

        // Usar Bukkit scheduler diretamente para repeating task
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // Verificar se inventário ainda está aberto
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() != inventory) {
                // Inventário fechado - task será cancelada no cleanup
                return;
            }

            // Re-resolver título com placeholders atualizados
            String title = config.title();
            updateTitle(title);
        }, interval, interval);
    }

    /**
     * Atualiza o estado.
     */
    public void updateState(@NotNull InventoryState newState) {
        this.state = newState;
    }

    /**
     * Re-renderiza um slot específico.
     *
     * @param slot Índice do slot
     */
    public void updateSlot(int slot) {
        // TODO: Implementação completa
    }

    /**
     * Re-renderiza todo o inventário.
     */
    public void refresh() {
        if (!Bukkit.isPrimaryThread()) {
            scheduler.runSync(this::refresh);
            return;
        }

        renderItems();

        // Update title to reflect new page or other changes
        // This is safe because updateTitle() has internal caching
        updateTitle(config.title());
    }

    // ========== Pagination Methods ==========

    /**
     * Define items de conteúdo para paginação.
     *
     * @param items Items a serem paginados
     */
    public void setContentItems(@NotNull List<GuiItem> items) {
        this.contentItems = new ArrayList<>(items);
        this.currentPage = 1; // Reset to first page
        refresh();
    }

    /**
     * Vai para a próxima página.
     *
     * @return true se mudou de página, false se já está na última
     */
    public boolean nextPage() {
        if (config.pagination() == null) {
            return false;
        }

        int totalPages = paginationEngine.getTotalPages(config, contentItems.size());
        if (currentPage < totalPages) {
            currentPage++;
            refresh();
            return true;
        }

        return false;
    }

    /**
     * Vai para a página anterior.
     *
     * @return true se mudou de página, false se já está na primeira
     */
    public boolean previousPage() {
        if (config.pagination() == null) {
            return false;
        }

        if (currentPage > 1) {
            currentPage--;
            refresh();
            return true;
        }

        return false;
    }

    /**
     * Vai para uma página específica.
     *
     * @param page Número da página (1-indexed)
     * @return true se mudou de página
     */
    public boolean goToPage(int page) {
        if (config.pagination() == null) {
            return false;
        }

        int totalPages = paginationEngine.getTotalPages(config, contentItems.size());
        int safePage = Math.max(1, Math.min(page, totalPages));

        if (safePage != currentPage) {
            currentPage = safePage;
            refresh();
            return true;
        }

        return false;
    }

    /**
     * Obtém página atual.
     *
     * @return Número da página atual
     */
    public int getCurrentPage() {
        return currentPage;
    }

    // ========== Tab Methods ==========

    /**
     * Troca aba ativa.
     *
     * @param tabId ID da tab alvo
     * @return true se trocou, false se tab inválida
     */
    public boolean switchTab(@NotNull String tabId) {
        if (!tabManager.isValidTab(config, tabId)) {
            return false;
        }

        tabState = tabManager.switchTab(tabState, tabId);
        persistTabState();
        refresh();
        return true;
    }

    /**
     * Vai para próxima tab.
     *
     * @return true se trocou
     */
    public boolean nextTab() {
        tabState = tabManager.switchToNextTab(tabState, config);
        persistTabState();
        refresh();
        return true;
    }

    /**
     * Vai para tab anterior.
     *
     * @return true se trocou
     */
    public boolean previousTab() {
        tabState = tabManager.switchToPreviousTab(tabState, config);
        persistTabState();
        refresh();
        return true;
    }

    /**
     * Obtém ID da tab ativa.
     *
     * @return ID da tab ativa
     */
    @NotNull
    public String getActiveTabId() {
        return tabState.activeTabId();
    }

    /**
     * Obtém TabState atual.
     *
     * @return TabState
     */
    @NotNull
    public TabState getTabState() {
        return tabState;
    }

    // ========== State Persistence Helpers ==========

    /**
     * Restaura tab state de InventoryState.
     */
    private void restoreTabStateFromInventoryState() {
        Object tabStateData = state.getStateData("tabState");
        if (tabStateData instanceof Map<?, ?>) {
            @SuppressWarnings("unchecked")
            Map<String, Object> tabStateMap = (Map<String, Object>) tabStateData;
            String defaultTabId = tabManager.getDefaultTabId(config);
            this.tabState = TabState.fromMap(player.getUniqueId(), config.id(), tabStateMap, defaultTabId);
        }

        // Restore current page from tab state
        if (tabState != null) {
            this.currentPage = Math.max(1, tabState.getActiveScrollPosition());
        }
    }

    /**
     * Persiste tab state em InventoryState.
     */
    private void persistTabState() {
        // Update scroll position for current tab
        tabState = tabState.withScrollPosition(tabState.activeTabId(), currentPage);

        // Save to inventory state
        state = state.withStateData("tabState", tabState.toMap());
    }

    // ========== Event Handlers ==========

    /**
     * Handler de click em inventário.
     *
     * <p>
     * IMPORTANTE: Sempre roda na main thread (garantido pelo Bukkit).
     * </p>
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClick(InventoryClickEvent event) {
        // DEBUG: Log event receipt
        boolean debug = plugin.getConfig().getBoolean("debug", false);
        if (debug) {
            plugin.getLogger().info("[ViewHolder] DEBUG: Click event received - inventoryMatch="
                    + event.getInventory().equals(inventory)
                    + ", ourInvTitle=" + (inventory != null ? inventory.getTitle() : "null")
                    + ", eventInvTitle=" + event.getView().getTitle());
        }

        // Check if it's our inventory
        if (!event.getInventory().equals(inventory)) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player clickedPlayer)) {
            return;
        }

        // Check if it's the correct player
        if (!clickedPlayer.getUniqueId().equals(player.getUniqueId())) {
            return;
        }

        // Get clicked slot
        int slot = event.getRawSlot();

        // Check if clicked in top inventory
        if (slot < 0 || slot >= inventory.getSize()) {
            // Clicked in player inventory - cancel by default to prevent item theft
            event.setCancelled(true);
            return;
        }

        // Get GuiItem at slot
        GuiItem guiItem = slotToGuiItem.get(slot);

        // DEBUG: Log slot lookup
        if (debug) {
            plugin.getLogger().info("[ViewHolder] DEBUG: Slot " + slot
                    + " lookup - found=" + (guiItem != null)
                    + ", slotMapSize=" + slotToGuiItem.size()
                    + ", slotMapKeys=" + slotToGuiItem.keySet());
        }

        // Cancel by default if item is configured (prevent item removal)
        if (guiItem != null) {
            event.setCancelled(true);

            // Execute actions with click type support
            actionHandler.handleClick(event, guiItem, context, this);
        }
    }

    /**
     * Handler de drag em inventário.
     *
     * <p>
     * IMPORTANTE: Sempre roda na main thread (garantido pelo Bukkit).
     * </p>
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryDrag(InventoryDragEvent event) {
        // Check if it's our inventory
        if (!event.getInventory().equals(inventory)) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player draggedPlayer)) {
            return;
        }

        // Check if it's the correct player
        if (!draggedPlayer.getUniqueId().equals(player.getUniqueId())) {
            return;
        }

        // Check if any dragged slot is in top inventory
        boolean affectsTopInventory = event.getRawSlots().stream()
                .anyMatch(slot -> slot >= 0 && slot < inventory.getSize());

        if (!affectsTopInventory) {
            return;
        }

        // Get target slot (if single slot drag)
        int targetSlot = -1;
        if (event.getRawSlots().size() == 1) {
            targetSlot = event.getRawSlots().iterator().next();
        }

        // Get GuiItem at target slot (if exists)
        GuiItem targetGuiItem = targetSlot >= 0 ? slotToGuiItem.get(targetSlot) : null;

        // Start drag session and validate
        boolean allowed = dragHandler.startDrag(event, targetGuiItem);

        if (!allowed) {
            event.setCancelled(true);
            return;
        }

        // Complete drag validation
        boolean valid = dragHandler.completeDrag(event, targetSlot);

        if (!valid) {
            event.setCancelled(true);
        }
    }

    /**
     * Handler de fechamento de inventário.
     *
     * <p>
     * IMPORTANTE: Sempre roda na main thread (garantido pelo Bukkit).
     * </p>
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        // Check if it's our inventory
        if (!event.getInventory().equals(inventory)) {
            return;
        }

        if (!(event.getPlayer() instanceof Player closedPlayer)) {
            return;
        }

        // Check if it's the correct player
        if (!closedPlayer.getUniqueId().equals(player.getUniqueId())) {
            return;
        }

        // Check if we are reopening (replacing inventory)
        if (reopening) {
            return;
        }

        // Cleanup resources
        cleanup();
    }

    // ========== Static Methods ==========

    /**
     * Obtém InventoryViewHolder ativo para um player.
     *
     * @param player Player
     * @return InventoryViewHolder ou null se player não tem inventário aberto
     */
    @Nullable
    public static InventoryViewHolder get(@NotNull Player player) {
        return activeHolders.get(player.getUniqueId());
    }

    /**
     * Obtém InventoryViewHolder ativo para um UUID.
     *
     * @param playerId UUID do player
     * @return InventoryViewHolder ou null se player não tem inventário aberto
     */
    @Nullable
    public static InventoryViewHolder get(@NotNull UUID playerId) {
        return activeHolders.get(playerId);
    }

    /**
     * Verifica se um player tem inventário ativo.
     *
     * @param player Player
     * @return true se tem inventário aberto
     */
    public static boolean hasActive(@NotNull Player player) {
        return activeHolders.containsKey(player.getUniqueId());
    }

    /**
     * Limpa todos os view holders (usado no shutdown).
     */
    public static void clearAll() {
        activeHolders.values().forEach(InventoryViewHolder::cleanup);
        activeHolders.clear();
    }

    // ========== Getters ==========

    @NotNull
    public Player getPlayer() {
        return player;
    }

    @NotNull
    public Plugin getPlugin() {
        return plugin;
    }

    @NotNull
    public InventoryConfig getConfig() {
        return config;
    }

    @NotNull
    public InventoryConfig getInventoryConfig() {
        return config;
    }

    @NotNull
    public InventoryContext getContext() {
        return context;
    }

    @NotNull
    public InventoryState getState() {
        return state;
    }

    @NotNull
    public Inventory getInventory() {
        return inventory;
    }

    public boolean isShared() {
        return shared;
    }

    public void setShared(boolean shared) {
        this.shared = shared;
    }

    /**
     * Obtém session ID compartilhado.
     *
     * @return Session ID ou null se não é compartilhado
     */
    @Nullable
    public String getSharedSessionId() {
        return sharedSessionId;
    }

    /**
     * Define session ID compartilhado.
     *
     * @param sharedSessionId Session ID
     */
    public void setSharedSessionId(@Nullable String sharedSessionId) {
        this.sharedSessionId = sharedSessionId;
        this.shared = (sharedSessionId != null);
    }

    /**
     * Obtém GuiItem configurado em um slot.
     *
     * @param slot Slot
     * @return GuiItem ou null se slot não tem item configurado
     */
    @Nullable
    public GuiItem getGuiItemAt(int slot) {
        return slotToGuiItem.get(slot);
    }
}
