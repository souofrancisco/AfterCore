package com.afterlands.core.inventory.action;

import com.afterlands.core.actions.ActionService;
import com.afterlands.core.concurrent.SchedulerService;
import com.afterlands.core.conditions.ConditionContext;
import com.afterlands.core.inventory.InventoryContext;
import com.afterlands.core.inventory.InventoryService;
import com.afterlands.core.conditions.ConditionService;
import com.afterlands.core.inventory.click.ClickContext;
import com.afterlands.core.inventory.click.ClickHandler;
import com.afterlands.core.inventory.click.ClickHandlers;
import com.afterlands.core.inventory.item.GuiItem;
import com.afterlands.core.inventory.item.PlaceholderResolver;
import com.afterlands.core.inventory.view.InventoryViewHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handler de actions para inventários do AfterCore.
 *
 * <p>
 * Integra o ActionService existente com clicks de inventário,
 * adicionando actions específicas para navegação de inventários.
 *
 * <p>
 * Thread safety: Todos os métodos são thread-safe.
 * Click events sempre rodam na main thread (garantido pelo Bukkit).
 */
public class InventoryActionHandler {

    private final ActionService actionService;
    private final ConditionService conditionService;
    private final PlaceholderResolver placeholderResolver;
    private final SchedulerService scheduler;
    private final Logger logger;
    private final boolean debug;

    // Custom action handlers específicos de inventário
    private final Map<String, BiConsumer<Player, String>> customHandlers;

    // Type-based click handlers (item type -> handler)
    private final Map<String, ClickHandler> typeHandlers;

    private InventoryService inventoryService;

    public InventoryActionHandler(
            ActionService actionService,
            ConditionService conditionService,
            PlaceholderResolver placeholderResolver,
            SchedulerService scheduler,
            Logger logger,
            boolean debug) {
        this.actionService = actionService;
        this.conditionService = conditionService;
        this.placeholderResolver = placeholderResolver;
        this.scheduler = scheduler;
        this.logger = logger;
        this.debug = debug;
        this.customHandlers = new ConcurrentHashMap<>();
        this.typeHandlers = new ConcurrentHashMap<>();

        registerDefaultHandlers();
    }

    /**
     * Registra handlers padrão para actions de inventário.
     */
    private void registerDefaultHandlers() {
        // switch_tab: <tabId>
        registerCustomAction("switch_tab", (player, args) -> {
            InventoryViewHolder holder = InventoryViewHolder.get(player);
            if (holder != null && !args.isBlank()) {
                holder.switchTab(args.trim());
            }
        });

        // next_page
        registerCustomAction("next_page", (player, args) -> {
            InventoryViewHolder holder = InventoryViewHolder.get(player);
            if (holder != null) {
                holder.nextPage();
            }
        });

        // prev_page
        registerCustomAction("prev_page", (player, args) -> {
            InventoryViewHolder holder = InventoryViewHolder.get(player);
            if (holder != null) {
                holder.previousPage();
            }
        });

        // close
        registerCustomAction("close", (player, args) -> {
            scheduler.runSync(() -> player.closeInventory());
        });

        // refresh
        registerCustomAction("refresh", (player, args) -> {
            InventoryViewHolder holder = InventoryViewHolder.get(player);
            if (holder != null) {
                holder.refresh();
            }
        });

        // open_panel (handled locally to preserve plugin namespace)
        BiConsumer<Player, String> openHandler = (player, args) -> {
            if (inventoryService == null || args.isBlank())
                return;

            InventoryViewHolder holder = InventoryViewHolder.get(player);
            if (holder != null) {
                // Parse ID from args (referenced by space)
                String[] parts = args.trim().split("\\s+");
                if (parts.length > 0) {
                    String panelId = parts[0];
                    Plugin plugin = holder.getPlugin();

                    // Get current context and build navigation history
                    InventoryContext currentCtx = holder.getContext();

                    // Get existing navigation history or create new one
                    @SuppressWarnings("unchecked")
                    java.util.List<com.afterlands.core.inventory.navigation.NavigationEntry> history = currentCtx
                            .getData("navigation_history", java.util.List.class)
                            .map(list -> new java.util.ArrayList<>(
                                    (java.util.List<com.afterlands.core.inventory.navigation.NavigationEntry>) list))
                            .orElseGet(java.util.ArrayList::new);

                    // Add current panel to history
                    history.add(new com.afterlands.core.inventory.navigation.NavigationEntry(
                            currentCtx.getInventoryId(),
                            currentCtx.getPlaceholders()));

                    // Create new context with propagated placeholders and history
                    InventoryContext ctx = new InventoryContext(player.getUniqueId(), panelId)
                            .withPlaceholders(currentCtx.getPlaceholders())
                            .withData("navigation_history", history);

                    // Open with plugin scope
                    inventoryService.openInventory(plugin, player, panelId, ctx);
                }
            }
        };
        registerCustomAction("open_panel", openHandler);
        registerCustomAction("open-panel", openHandler);
        registerCustomAction("open", openHandler);

        // previous_panel (navigate back in history)
        // NOTE: This action now executes immediately (not scheduled), so holder is
        // still available
        registerCustomAction("previous_panel", (player, argsStr) -> {

            if (inventoryService == null) {
                logger.warning("[InventoryAction] inventoryService is null!");
                return;
            }

            InventoryViewHolder holder = InventoryViewHolder.get(player);
            if (holder != null) {
                InventoryContext currentCtx = holder.getContext();
                Plugin plugin = holder.getPlugin();

                // Get navigation history
                @SuppressWarnings("unchecked")
                java.util.Optional<java.util.List<com.afterlands.core.inventory.navigation.NavigationEntry>> historyOpt = currentCtx
                        .getData("navigation_history", java.util.List.class)
                        .map(list -> (java.util.List<com.afterlands.core.inventory.navigation.NavigationEntry>) list);

                if (historyOpt.isPresent()) {
                    java.util.List<com.afterlands.core.inventory.navigation.NavigationEntry> history = new java.util.ArrayList<>(
                            historyOpt.get());

                    // Check if there's a previous panel in history
                    if (!history.isEmpty()) {
                        // Pop the last entry (go back)
                        com.afterlands.core.inventory.navigation.NavigationEntry previous = history
                                .remove(history.size() - 1);

                        // Create new context with previous panel's placeholders and updated history
                        InventoryContext ctx = new InventoryContext(player.getUniqueId(), previous.getPanelId())
                                .withPlaceholders(previous.getPlaceholders())
                                .withData("navigation_history", history);

                        // Open the previous panel
                        inventoryService.openInventory(plugin, player, previous.getPanelId(), ctx);
                    } else {
                        // No history - close the inventory instead
                        player.closeInventory();
                    }
                } else {
                    // No history - close the inventory instead
                    player.closeInventory();
                }
            } else {
                logger.warning("[InventoryAction] No InventoryViewHolder found for player");
            }
        });
        registerCustomAction("previous-panel", customHandlers.get("previous_panel"));
        registerCustomAction("back", customHandlers.get("previous_panel"));
    }

    /**
     * Processa click em item de inventário com suporte a tipos de click.
     *
     * <p>
     * IMPORTANTE: Este método sempre roda na main thread (garantido pelo Bukkit).
     *
     * @param event   InventoryClickEvent do Bukkit
     * @param item    GuiItem configurado
     * @param context InventoryContext do player
     * @param holder  InventoryViewHolder do inventário
     */
    public void handleClick(InventoryClickEvent event, GuiItem item, InventoryContext context,
            InventoryViewHolder holder) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ClickType clickType = event.getClick();
        ClickHandlers handlers = item.getClickHandlers();

        // DEBUG: Log click handling
        if (debug) {
            logger.info("[InventoryAction] DEBUG: Click received - player=" + player.getName()
                    + ", slot=" + event.getRawSlot()
                    + ", clickType=" + clickType
                    + ", itemType=" + item.getType()
                    + ", hasHandlers=" + handlers.hasProgrammaticHandlers()
                    + ", hasClickFor=" + handlers.hasHandlerFor(clickType));
        }

        // Criar ClickContext
        ClickContext clickContext = ClickContext.from(event, holder, item, context);

        // 0. Check Click Conditions
        if (!item.getClickConditions().isEmpty()) {
            ConditionContext conditionContext = java.util.Collections::emptyMap;
            boolean canClick = true;
            for (String condition : item.getClickConditions()) {
                if (!conditionService.evaluateSync(player, condition, conditionContext)) {
                    canClick = false;
                    break;
                }
            }
            if (!canClick) {
                if (debug) {
                    logger.info("[InventoryAction] Click denied due to conditions for player " + player.getName());
                }
                // Optional: Play deny sound or message if needed, but for now just return
                return;
            }
        }

        // 1. Tentar handler baseado em tipo de item (registrado via
        // registerTypeHandler)
        String itemType = item.getType();
        if (itemType != null && typeHandlers.containsKey(itemType.toLowerCase())) {
            try {
                typeHandlers.get(itemType.toLowerCase()).handle(clickContext);
                return;
            } catch (Exception e) {
                logger.log(Level.WARNING, "Type handler threw exception for type " + itemType, e);
                return;
            }
        }

        // 2. Tentar handler programático do item
        ClickHandler handler = handlers.getHandler(clickType);
        if (handler != null) {
            try {
                handler.handle(clickContext);
                return;
            } catch (Exception e) {
                logger.log(Level.WARNING, "Click handler threw exception for player " + player.getName(), e);
                return;
            }
        }

        // 2. Executar actions para o tipo de click
        ConfiguredAction configuredAction = handlers.getActions(clickType);
        List<String> actionsToRun = configuredAction.success();

        // Evaluate conditions if present
        if (configuredAction.hasConditions()) {
            ConditionContext conditionContext = java.util.Collections::emptyMap;
            boolean conditionsMet = true;
            for (String condition : configuredAction.conditions()) {
                if (!conditionService.evaluateSync(player, condition, conditionContext)) {
                    conditionsMet = false;
                    break;
                }
            }

            if (!conditionsMet) {
                actionsToRun = configuredAction.fail();
            }
        }

        if (actionsToRun.isEmpty()) {
            return;
        }

        executeActions(actionsToRun, player, context)
                .exceptionally(ex -> {
                    logger.log(Level.WARNING, "Failed to execute actions for player " + player.getName(), ex);
                    return null;
                });
    }

    /**
     * Executa lista de actions configuradas.
     *
     * <p>
     * Actions do ActionService (play_sound, message, etc.) são executadas via
     * ActionService.
     * Actions customizadas de inventário (switch_tab, next_page, etc.) são
     * executadas diretamente.
     *
     * @param actions Lista de strings de actions (formato AfterCore)
     * @param player  Player alvo
     * @param context Contexto com placeholders
     * @return CompletableFuture que completa quando todas as actions foram
     *         executadas
     */
    public CompletableFuture<Void> executeActions(
            List<String> actions,
            Player player,
            InventoryContext context) {
        if (actions == null || actions.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        // Resolve placeholders em todas as actions
        List<String> resolvedActions = actions.stream()
                .map(action -> placeholderResolver.resolve(action, player, context))
                .toList();

        // Execute todas as actions em sequência
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);

        for (String actionString : resolvedActions) {
            future = future.thenCompose(v -> executeSingleAction(actionString, player, context));
        }

        return future;
    }

    /**
     * Executa uma única action.
     *
     * @param actionString String da action
     * @param player       Player alvo
     * @param context      Contexto
     * @return CompletableFuture que completa quando a action foi executada
     */
    private CompletableFuture<Void> executeSingleAction(
            String actionString,
            Player player,
            InventoryContext context) {
        try {
            ParsedAction action = parseAction(actionString)
                    .orElseThrow(() -> new IllegalArgumentException("Failed to parse action: " + actionString));

            // Check if it's a custom inventory action
            BiConsumer<Player, String> customHandler = customHandlers.get(action.actionType());
            if (customHandler != null) {
                // Custom inventory action - execute immediately if already on main thread
                // This is important for navigation actions that need access to the current
                // holder
                if (org.bukkit.Bukkit.isPrimaryThread()) {
                    customHandler.accept(player, action.arguments());
                    return CompletableFuture.completedFuture(null);
                } else {
                    return scheduler.runSync(() -> customHandler.accept(player, action.arguments()));
                }
            }

            // Delegate to ActionService for standard actions
            return scheduler.runSync(() -> {
                try {
                    // Parse action using ActionService
                    var spec = actionService.parse(actionString);
                    if (spec != null) {
                        // Get handler for this action type
                        var handler = actionService.getHandlers().get(spec.typeKey());
                        if (handler != null) {
                            handler.execute(player, spec);
                        } else {
                            if (debug) {
                                logger.warning("No handler found for action type: " + spec.typeKey());
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "ActionService failed to execute: " + actionString, e);
                }
            });

        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to execute action: " + actionString, e);
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Parse e valida action string.
     *
     * @param actionString String no formato "action: args" ou "action"
     * @return Parsed action ou Optional.empty() se inválida
     */
    public Optional<ParsedAction> parseAction(String actionString) {
        try {
            return Optional.of(ParsedAction.parse(actionString));
        } catch (IllegalArgumentException e) {
            if (debug) {
                logger.warning("Failed to parse action: " + actionString + " - " + e.getMessage());
            }
            return Optional.empty();
        }
    }

    /**
     * Registra action customizada para inventários.
     *
     * <p>
     * IMPORTANTE: O handler será executado na main thread.
     *
     * @param actionName Nome da action (ex: "switch_tab", "next_page")
     * @param handler    Handler customizado que aceita (Player, arguments)
     */
    public void registerCustomAction(String actionName, BiConsumer<Player, String> handler) {
        if (actionName == null || actionName.isBlank()) {
            throw new IllegalArgumentException("Action name cannot be null or empty");
        }
        if (handler == null) {
            throw new IllegalArgumentException("Handler cannot be null");
        }

        customHandlers.put(actionName.toLowerCase(), handler);

        if (debug) {
            logger.info("[InventoryAction] Registered custom action: " + actionName);
        }
    }

    /**
     * Remove action customizada.
     *
     * @param actionName Nome da action
     * @return true se a action foi removida
     */
    public boolean unregisterCustomAction(String actionName) {
        return customHandlers.remove(actionName.toLowerCase()) != null;
    }

    /**
     * Verifica se uma action é customizada de inventário.
     *
     * @param actionType Tipo da action
     * @return true se é uma action customizada
     */
    public boolean isCustomAction(String actionType) {
        return customHandlers.containsKey(actionType.toLowerCase());
    }

    /**
     * Registra handler para um tipo de item específico.
     *
     * <p>
     * Quando um item com o tipo especificado for clicado, o handler será
     * chamado automaticamente, independente das actions YAML configuradas.
     *
     * <p>
     * IMPORTANTE: O handler será executado na main thread.
     *
     * @param itemType Nome do tipo de item (value do campo 'type' no YAML)
     * @param handler  Handler a ser executado quando itens desse tipo são clicados
     */
    public void registerTypeHandler(String itemType, ClickHandler handler) {
        if (itemType == null || itemType.isBlank()) {
            throw new IllegalArgumentException("Item type cannot be null or empty");
        }
        if (handler == null) {
            throw new IllegalArgumentException("Handler cannot be null");
        }

        typeHandlers.put(itemType.toLowerCase(), handler);

        if (debug) {
            logger.info("[InventoryAction] Registered type handler for: " + itemType);
        }
    }

    /**
     * Remove handler de tipo.
     *
     * @param itemType Tipo de item
     * @return true se o handler foi removido
     */
    public boolean unregisterTypeHandler(String itemType) {
        return typeHandlers.remove(itemType.toLowerCase()) != null;
    }

    /**
     * Retorna todos os nomes de actions customizadas registradas.
     *
     * @return Set de nomes de actions
     */
    public java.util.Set<String> getRegisteredActions() {
        return java.util.Set.copyOf(customHandlers.keySet());
    }

    public void setInventoryService(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }
}
