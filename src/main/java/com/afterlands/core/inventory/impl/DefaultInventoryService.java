package com.afterlands.core.inventory.impl;

import com.afterlands.core.actions.ActionExecutor;
import com.afterlands.core.actions.ActionService;
import com.afterlands.core.concurrent.SchedulerService;
import com.afterlands.core.conditions.ConditionService;
import com.afterlands.core.database.SqlService;
import com.afterlands.core.inventory.*;
import com.afterlands.core.inventory.action.InventoryActionHandler;
import com.afterlands.core.inventory.animation.InventoryAnimator;
import com.afterlands.core.inventory.click.ClickHandler;
import com.afterlands.core.inventory.cache.ItemCache;
import com.afterlands.core.inventory.config.InventoryConfigManager;
import com.afterlands.core.inventory.drag.DragAndDropHandler;
import com.afterlands.core.inventory.item.ItemCompiler;
import com.afterlands.core.inventory.item.PlaceholderResolver;
import com.afterlands.core.inventory.pagination.PaginationEngine;
import com.afterlands.core.inventory.shared.SharedInventoryManager;
import com.afterlands.core.inventory.state.InventoryStateManager;
import com.afterlands.core.inventory.tab.TabManager;
import com.afterlands.core.inventory.title.TitleUpdateSupport;
import com.afterlands.core.inventory.template.DefaultItemTemplateService;
import com.afterlands.core.inventory.template.ItemTemplateService;
import com.afterlands.core.inventory.view.InventoryViewHolder;
import com.afterlands.core.inventory.InventoryConfig;
import com.afterlands.core.config.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Implementação padrão do InventoryService.
 *
 * <p>
 * <b>Thread Safety:</b> Thread-safe. Operações de abertura na main thread,
 * DB operations async.
 * </p>
 *
 * <p>
 * <b>Performance:</b> Cache agressivo, batch operations quando possível.
 * </p>
 */
public class DefaultInventoryService implements InventoryService {

    private final Plugin plugin;
    private final SchedulerService scheduler;
    private final SqlService sql;
    private final ActionService actionService;
    private final ConditionService conditionService;
    private final MessageService messageService;
    private final InventoryConfigManager configManager;
    private final InventoryStateManager stateManager;

    private final boolean debug;

    // Phase 2: Cache + Compilation
    private final ItemCache itemCache;
    private final PlaceholderResolver placeholderResolver;
    private final ItemCompiler itemCompiler;

    // Phase 3: Pagination + Tabs
    private final PaginationEngine paginationEngine;
    private final TabManager tabManager;

    // Phase 4: Actions + Drag
    private final InventoryActionHandler actionHandler;
    private final DragAndDropHandler dragHandler;

    // Phase 5: Animations
    private final InventoryAnimator animator;
    // stateManager removed (duplicate)
    private final SharedInventoryManager sharedManager;
    private final TitleUpdateSupport titleSupport;
    private final DefaultItemTemplateService templateService;

    // Registry de inventários customizados (programáticos)
    private final Map<String, InventoryConfig> customInventories;

    // Active inventory holders por player
    private final Map<UUID, InventoryViewHolder> activeInventories;

    public DefaultInventoryService(
            @NotNull Plugin plugin,
            @NotNull SchedulerService scheduler,
            @NotNull SqlService sql,
            @NotNull ActionService actionService,
            @NotNull ActionExecutor actionExecutor,
            @NotNull ConditionService conditions,
            @NotNull MessageService messageService,
            @NotNull InventoryConfigManager configManager
    ) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.sql = sql;
        this.actionService = actionService;
        this.conditionService = conditions;
        this.messageService = messageService;
        this.configManager = configManager;

        // Phase 2: Initialize cache + compilation pipeline
        this.debug = plugin.getConfig().getBoolean("debug", false);
        this.itemCache = new ItemCache(plugin.getLogger(), debug);
        this.placeholderResolver = new PlaceholderResolver(scheduler, messageService, debug);
        this.itemCompiler = new ItemCompiler(scheduler, itemCache, placeholderResolver, messageService, plugin.getLogger(), debug);

        // Phase 3: Initialize pagination + tabs
        this.paginationEngine = new PaginationEngine(configManager);
        this.tabManager = new TabManager();

        // Phase 4: Initialize action and drag handlers
        this.actionHandler = new InventoryActionHandler(
                actionService,
                actionExecutor,
                conditionService,
                placeholderResolver,
                scheduler,
                plugin.getLogger(),
                debug);
        this.actionHandler.setInventoryService(this); // Inject self reference

        this.dragHandler = new DragAndDropHandler(
                scheduler,
                actionHandler,
                plugin.getLogger(),
                debug);

        // Register core navigation type handlers
        registerNavigationTypeHandlers();

        // Phase 5: Initialize animator
        this.animator = new InventoryAnimator(
                scheduler,
                itemCompiler,
                plugin.getLogger(),
                debug);
        this.animator.start(); // Inicia scheduler de animações

        // Phase 6: Initialize state manager and shared manager
        this.stateManager = new InventoryStateManager(plugin, sql, scheduler, debug);
        this.sharedManager = new SharedInventoryManager(plugin, scheduler, plugin.getLogger(), debug);

        // Phase 8: Initialize title support
        this.titleSupport = new TitleUpdateSupport(plugin.getLogger());
        if (titleSupport.isAvailable()) {
            plugin.getLogger().info("TitleUpdateSupport: ProtocolLib detectado - títulos dinâmicos habilitados");
        } else {
            plugin.getLogger()
                    .warning("TitleUpdateSupport: ProtocolLib não encontrado - usando fallback (reabrir inventário)");
        }

        this.customInventories = new ConcurrentHashMap<>();
        this.activeInventories = new ConcurrentHashMap<>();

        // Template service uses a lookup that checks customInventories first, then
        // configManager
        this.templateService = new DefaultItemTemplateService(inventoryId -> {
            InventoryConfig config = customInventories.get(inventoryId);
            if (config != null) {
                return config;
            }
            return configManager.getInventoryConfig(inventoryId);
        });
    }

    @Override
    public void openInventory(@NotNull Player player, @NotNull String inventoryId, @NotNull InventoryContext context) {
        // Validação: main thread
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("openInventory must be called from main thread");
        }

        // Obtém configuração
        InventoryConfig config = getConfigOrThrow(inventoryId);

        // Carrega estado do DB (async) e abre quando pronto
        stateManager.loadState(player.getUniqueId(), inventoryId)
                .thenAcceptAsync(state -> {
                    // De volta à main thread para abrir inventário
                    openInventoryWithState(player, config, context, state);
                }, scheduler::runSync)
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.SEVERE, "Failed to open inventory for " + player.getName(), ex);
                    return null;
                });
    }

    @Override
    public void openInventory(@NotNull Plugin ownerPlugin, @NotNull Player player, @NotNull String inventoryId,
            @NotNull InventoryContext context) {
        // Use namespaced ID
        String namespacedId = resolveNamespacedId(ownerPlugin, inventoryId);
        openInventory(player, namespacedId, context);
    }

    /**
     * Resolves a namespaced inventory ID from plugin and local ID.
     *
     * @param ownerPlugin Plugin that owns the inventory
     * @param localId     Local inventory ID (without namespace)
     * @return Namespaced ID in format "pluginName:localId"
     */
    @NotNull
    private String resolveNamespacedId(@NotNull Plugin ownerPlugin, @NotNull String localId) {
        return ownerPlugin.getName() + ":" + localId;
    }

    /**
     * Abre inventário com estado carregado (main thread).
     */
    private void openInventoryWithState(
            @NotNull Player player,
            @NotNull InventoryConfig config,
            @NotNull InventoryContext context,
            @NotNull InventoryState state) {
        // Determine owner plugin from inventory ID (format: PluginName:InventoryId)
        Plugin ownerPlugin = this.plugin; // Default to AfterCore
        String id = config.id();
        if (id.contains(":")) {
            String pluginName = id.split(":")[0];
            Plugin pl = Bukkit.getPluginManager().getPlugin(pluginName);
            if (pl != null) {
                ownerPlugin = pl;
            }
        }

        try {
            // Set plugin namespace for i18n auto-injection
            if (context.getPluginNamespace() == null) {
                context.withPluginNamespace(ownerPlugin.getName().toLowerCase());
            }

            // Invalidate any cached items for this inventory to ensure fresh rendering
            itemCompiler.invalidateCache(config.id());

            // Remove existing active inventory entry to ensure clean state
            // Note: The holder's cleanup is handled by the new holder's constructor
            activeInventories.remove(player.getUniqueId());

            // Cria holder (com ItemCompiler para renderização otimizada)
            InventoryViewHolder holder = new InventoryViewHolder(
                    ownerPlugin,
                    player,
                    config,
                    context,
                    state,
                    scheduler,
                    itemCompiler,
                    paginationEngine,
                    tabManager,
                    actionHandler,
                    dragHandler,
                    animator,
                    titleSupport,
                    conditionService,
                    messageService,
                    placeholderResolver);

            // Iniciar title update task se configurado
            if (config.titleUpdateInterval() > 0) {
                holder.startTitleUpdateTask(config.titleUpdateInterval());
            }

            // Registra como ativo
            activeInventories.put(player.getUniqueId(), holder);

            // Abre para o player
            holder.open();

            plugin.getLogger().fine("Opened inventory '" + config.id() + "' owned by " + ownerPlugin.getName() + " for "
                    + player.getName());
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create inventory view", e);
        }
    }

    @Override
    public @NotNull String openSharedInventory(@NotNull List<Player> players, @NotNull String inventoryId,
            @NotNull InventoryContext context) {
        // Validação: main thread
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("openSharedInventory must be called from main thread");
        }

        if (players.isEmpty()) {
            throw new IllegalArgumentException("players list cannot be empty");
        }

        // Obtém configuração
        InventoryConfig config = getConfigOrThrow(inventoryId);

        if (!config.shared()) {
            throw new IllegalArgumentException("Inventory '" + inventoryId + "' is not configured as shared");
        }

        // Cria sessão compartilhada
        String sessionId = sharedManager.createSharedSession(players, inventoryId, context);

        // Carrega estado (usa primeiro player como referência)
        UUID firstPlayerId = players.get(0).getUniqueId();
        stateManager.loadState(firstPlayerId, inventoryId)
                .thenAcceptAsync(state -> {
                    openSharedInventoryWithState(players, config, context, state, sessionId);
                }, scheduler::runSync)
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.SEVERE, "Failed to open shared inventory", ex);
                    sharedManager.closeSession(sessionId);
                    return null;
                });

        return sessionId;
    }

    /**
     * Abre inventário compartilhado com estado (main thread).
     */
    private void openSharedInventoryWithState(
            @NotNull List<Player> players,
            @NotNull InventoryConfig config,
            @NotNull InventoryContext context,
            @NotNull InventoryState state,
            @NotNull String sessionId) {
        try {
            // Cria holders individuais para cada player
            for (Player player : players) {
                InventoryViewHolder holder = new InventoryViewHolder(
                        plugin,
                        player,
                        config,
                        context,
                        state,
                        scheduler,
                        itemCompiler,
                        paginationEngine,
                        tabManager,
                        actionHandler,
                        dragHandler,
                        animator,
                        titleSupport,
                        conditionService,
                        messageService,
                        placeholderResolver);

                // Iniciar title update task se configurado
                if (config.titleUpdateInterval() > 0) {
                    holder.startTitleUpdateTask(config.titleUpdateInterval());
                }

                holder.setSharedSessionId(sessionId);

                // Registra holder na sessão compartilhada
                sharedManager.registerViewHolder(sessionId, player.getUniqueId(), holder);

                // Registra como ativo
                activeInventories.put(player.getUniqueId(), holder);

                // Abre para o player
                holder.open();
            }

            plugin.getLogger().info("Opened shared inventory '" + config.id() + "' for " + players.size()
                    + " players (session: " + sessionId + ")");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create shared inventory view", e);
            sharedManager.closeSession(sessionId);
        }
    }

    @Override
    public void closeInventory(@NotNull Player player) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("closeInventory must be called from main thread");
        }

        InventoryViewHolder holder = activeInventories.remove(player.getUniqueId());
        if (holder != null) {
            // Se é compartilhado, remove da sessão
            if (holder.isShared() && holder.getSharedSessionId() != null) {
                sharedManager.removePlayer(holder.getSharedSessionId(), player.getUniqueId());
            }

            // Salva estado se persistência habilitada
            if (holder.getConfig().persistence().enabled() && holder.getConfig().persistence().autoSave()) {
                stateManager.saveState(holder.getState())
                        .exceptionally(ex -> {
                            plugin.getLogger().log(Level.WARNING, "Failed to save inventory state on close", ex);
                            return null;
                        });
            }

            holder.close();
        }
    }

    @Override
    public @NotNull CompletableFuture<Void> saveInventoryState(@NotNull UUID playerId, @NotNull String inventoryId,
            @NotNull InventoryState state) {
        return stateManager.saveState(state);
    }

    @Override
    public @NotNull CompletableFuture<InventoryState> loadInventoryState(@NotNull UUID playerId,
            @NotNull String inventoryId) {
        return stateManager.loadState(playerId, inventoryId);
    }

    @Override
    public @NotNull CompletableFuture<Void> reloadConfigurations() {
        return CompletableFuture.runAsync(() -> {
            configManager.reload();
            plugin.getLogger().info("Reloaded inventory configurations");
        }, scheduler.ioExecutor());
    }

    @Override
    public void clearCache() {
        configManager.clearCache();
        itemCompiler.clearCache();
        itemCache.cleanup();
    }

    @Override
    public void clearCache(@NotNull String inventoryId) {
        // Clear specific inventory items
        itemCompiler.invalidateCache(inventoryId);

        // Remove from config manager cache
        configManager.invalidate(inventoryId);
    }

    @Override
    public void invalidateItemCache(@NotNull String inventoryId) {
        // Only clear compiled items, NOT the config registration
        itemCompiler.invalidateCache(inventoryId);
    }

    @Override
    public void clearPlayerCache(@NotNull UUID playerId) {
        itemCompiler.clearPlayerCache(playerId);
    }

    @Override
    public @NotNull ItemTemplateService templates() {
        return templateService;
    }

    @Override
    public boolean isInventoryRegistered(@NotNull String inventoryId) {
        return configManager.hasInventory(inventoryId) || customInventories.containsKey(inventoryId);
    }

    @Override
    public void registerInventory(@NotNull InventoryConfig config) {
        customInventories.put(config.id(), config);
        plugin.getLogger().info("Registered custom inventory: " + config.id());
    }

    /**
     * Obtém configuração ou lança exceção.
     */
    @NotNull
    private InventoryConfig getConfigOrThrow(@NotNull String inventoryId) {
        // Tenta custom primeiro
        InventoryConfig config = customInventories.get(inventoryId);
        if (config != null) {
            return config;
        }

        // Tenta YAML
        config = configManager.getInventoryConfig(inventoryId);
        if (config == null) {
            throw new IllegalArgumentException("Inventory not found: " + inventoryId);
        }

        return config;
    }

    /**
     * Obtém holder ativo de um player.
     */
    @NotNull
    public Optional<InventoryViewHolder> getActiveInventory(@NotNull UUID playerId) {
        InventoryViewHolder holder = activeInventories.get(playerId);

        if (holder == null) {
            if (debug) {
                plugin.getLogger().info("Player " + playerId + " has no active inventory");
            }
            return Optional.empty();
        }

        return Optional.of(holder);
    }

    /**
     * Cleanup ao desabilitar plugin.
     */
    public void shutdown() {
        // Fecha todos os inventários ativos
        activeInventories.values().forEach(InventoryViewHolder::close);
        activeInventories.clear();
        customInventories.clear();

        // Cleanup shared manager
        sharedManager.shutdown();

        // Cleanup state manager (salva estados pendentes)
        stateManager.shutdown();

        // Cleanup drag handler
        dragHandler.shutdown();

        // Cleanup animator
        animator.shutdown();

        // Cleanup cache
        itemCache.invalidateAll();
        itemCompiler.clearCache();

        // Cleanup static view holder registry
        InventoryViewHolder.clearAll();

        plugin.getLogger().info("Inventory service shut down");
    }

    /**
     * Obtém ItemCompiler para uso externo (testes, debugging).
     *
     * @return ItemCompiler instance
     */
    @NotNull
    public ItemCompiler getItemCompiler() {
        return itemCompiler;
    }

    /**
     * Obtém ItemCache para estatísticas.
     *
     * @return ItemCache instance
     */
    @NotNull
    public ItemCache getItemCache() {
        return itemCache;
    }

    /**
     * Obtém InventoryAnimator para estatísticas e debugging.
     *
     * @return InventoryAnimator instance
     */
    @NotNull
    public InventoryAnimator getAnimator() {
        return animator;
    }

    @Override
    public int registerInventories(@NotNull File file) {
        if (!file.exists()) {
            return 0;
        }
        List<InventoryConfig> configs = configManager.loadConfigs(file);
        for (InventoryConfig config : configs) {
            registerInventory(config);
        }
        return configs.size();
    }

    @Override
    public int registerInventories(@NotNull Plugin ownerPlugin, @NotNull File file) {
        if (!file.exists()) {
            return 0;
        }
        List<InventoryConfig> configs = configManager.loadConfigs(file);
        for (InventoryConfig config : configs) {
            // Register with namespaced ID
            String namespacedId = resolveNamespacedId(ownerPlugin, config.id());
            InventoryConfig namespacedConfig = config.withId(namespacedId);
            registerInventory(namespacedConfig);
        }
        return configs.size();
    }

    @Override
    public void registerTypeHandler(@NotNull String itemType,
            @NotNull ClickHandler handler) {
        actionHandler.registerTypeHandler(itemType, handler);
    }

    /**
     * Registers type handlers for core navigation items (prev-page, next-page).
     * 
     * <p>
     * These handlers enable pagination via item type without requiring YAML
     * actions.
     * Sounds: plays CLICK sound at 1.0 volume, 1.5 pitch.
     * </p>
     */
    private void registerNavigationTypeHandlers() {
        // prev-page handler
        actionHandler.registerTypeHandler("prev-page", ctx -> {
            InventoryViewHolder holder = ctx.holder();
            if (holder != null) {
                ctx.player().playSound(ctx.player().getLocation(), org.bukkit.Sound.CLICK, 1.0f, 1.5f);
                holder.previousPage();
            }
        });

        // previous-page alias
        actionHandler.registerTypeHandler("previous-page", ctx -> {
            InventoryViewHolder holder = ctx.holder();
            if (holder != null) {
                ctx.player().playSound(ctx.player().getLocation(), org.bukkit.Sound.CLICK, 1.0f, 1.5f);
                holder.previousPage();
            }
        });

        // next-page handler
        actionHandler.registerTypeHandler("next-page", ctx -> {
            InventoryViewHolder holder = ctx.holder();
            if (holder != null) {
                ctx.player().playSound(ctx.player().getLocation(), org.bukkit.Sound.CLICK, 1.0f, 1.5f);
                holder.nextPage();
            }
        });
    }
}
