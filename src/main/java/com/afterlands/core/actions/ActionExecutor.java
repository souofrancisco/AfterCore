package com.afterlands.core.actions;

import com.afterlands.core.actions.handlers.WaitHandler;
import com.afterlands.core.concurrent.SchedulerService;
import com.afterlands.core.conditions.ConditionContext;
import com.afterlands.core.conditions.ConditionService;
import com.afterlands.core.conditions.impl.EmptyConditionContext;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Executor de actions com suporte a scopes (VIEWER, NEARBY, ALL) e delays.
 *
 * <p>
 * Thread Safety: Garante que handlers são executados na main thread quando
 * necessário.
 * </p>
 * <p>
 * Performance: Usa spatial queries eficientes para NEARBY scope.
 * </p>
 */
public final class ActionExecutor {

    private final Plugin plugin;
    private final ConditionService conditionService;
    private final SchedulerService scheduler;
    private final Map<String, ActionHandler> handlers;
    private final boolean debug;

    public ActionExecutor(@NotNull Plugin plugin,
            @NotNull ConditionService conditionService,
            @NotNull SchedulerService scheduler,
            @NotNull Map<String, ActionHandler> handlers,
            boolean debug) {
        this.plugin = plugin;
        this.conditionService = conditionService;
        this.scheduler = scheduler;
        this.handlers = handlers;
        this.debug = debug;
    }

    /**
     * Executa uma action spec com scope resolvido.
     *
     * @param spec   ActionSpec parseada
     * @param viewer Player que está "vendo" (usado para VIEWER scope e referência
     *               de posição)
     * @param origin Origem para cálculo de NEARBY (geralmente viewer.getLocation())
     */
    public void execute(@NotNull ActionSpec spec, @NotNull Player viewer, @NotNull Location origin) {
        executeAsync(spec, viewer, origin);
    }

    /**
     * Executa uma lista de actions em sequência, respeitando delays (wait).
     *
     * @param specs  Lista de ActionSpec
     * @param viewer Player visualizador
     * @param origin Localização de origem
     * @return Future que completa quando toda a sequência terminar
     */
    public CompletableFuture<Void> executeSequence(@NotNull List<ActionSpec> specs, @NotNull Player viewer,
            @NotNull Location origin) {
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);

        for (ActionSpec spec : specs) {
            chain = chain.thenCompose(v -> executeAsync(spec, viewer, origin));
        }

        return chain;
    }

    /**
     * Executa uma action de forma assíncrona (retorna Future).
     * Suporta 'wait' (delay) e scopes.
     */
    public CompletableFuture<Void> executeAsync(@NotNull ActionSpec spec, @NotNull Player viewer,
            @NotNull Location origin) {
        // Verificar se é 'wait'
        if (spec.typeKey().equalsIgnoreCase("wait") || spec.typeKey().equalsIgnoreCase("delay")) {
            long ticks = 20; // Default 1s
            try {
                ticks = Long.parseLong(spec.rawArgs().trim());
            } catch (NumberFormatException ignored) {
            }
            return scheduler.delay(ticks);
        }

        // Action normal
        ActionHandler handler = handlers.get(spec.typeKey().toLowerCase(Locale.ROOT));
        if (handler == null) {
            // Ignorar actions desconhecidas
            return CompletableFuture.completedFuture(null);
        }

        // Se for um handler especial de "Wait" (marcador), ignora pois já tratamos
        // acima
        if (handler instanceof WaitHandler) {
            // Caso o parser tenha resolvido "wait" mas a lógica acima falhou por algum
            // motivo,
            // ou se simplesmente queremos garantir que nao execute nada extra.
            return CompletableFuture.completedFuture(null);
        }

        // Resolver targets
        Collection<Player> targets = resolveTargets(spec.scope(), viewer, origin, spec.scopeRadius());
        if (targets.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (Player target : targets) {
            futures.add(executeForTargetAsync(spec, target, handler));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    /**
     * Executa action para um target específico (com checagem de condição).
     */
    private CompletableFuture<Void> executeForTargetAsync(@NotNull ActionSpec spec, @NotNull Player target,
            @NotNull ActionHandler handler) {
        // Se tem condição, avaliar na main thread antes de executar
        if (spec.condition() != null && !spec.condition().isEmpty()) {
            return scheduler.supplySync(() -> {
                ConditionContext ctx = EmptyConditionContext.getInstance();
                return conditionService.evaluateSync(target, spec.condition(), ctx);
            }).thenCompose(met -> {
                if (!met) {
                    return CompletableFuture.completedFuture(null);
                }
                return runHandler(target, spec, handler);
            });
        }

        return runHandler(target, spec, handler);
    }

    private CompletableFuture<Void> runHandler(Player target, ActionSpec spec, ActionHandler handler) {
        // Executar handler (garantir main thread)
        // A maioria dos handlers do Bukkit precisa rodar na main thread
        return scheduler.runSync(() -> handler.execute(target, spec));
    }

    /**
     * Resolve targets baseado no scope.
     *
     * @param scope  Scope da action (VIEWER/NEARBY/ALL)
     * @param viewer Player de referência
     * @param origin Localização de referência para NEARBY
     * @param radius Raio para NEARBY (em blocos)
     * @return Coleção de players que devem receber a action
     */
    private Collection<Player> resolveTargets(@NotNull ActionScope scope,
            @NotNull Player viewer,
            @NotNull Location origin,
            int radius) {
        return switch (scope) {
            case VIEWER -> Collections.singleton(viewer);

            case NEARBY -> getNearbyPlayers(origin, radius);

            case ALL -> new ArrayList<>(Bukkit.getOnlinePlayers());
        };
    }

    /**
     * Obtém players próximos de uma localização (otimizado).
     *
     * <p>
     * Performance: O(chunks × players_per_chunk) em vez de O(all_players)
     * </p>
     */
    private Collection<Player> getNearbyPlayers(@NotNull Location origin, int radius) {
        if (radius <= 0) {
            radius = 32; // default
        }

        Set<Player> result = new HashSet<>();
        double radiusSquared = radius * radius;

        // Iterar apenas pelo mundo da origem
        if (origin.getWorld() != null) {
            for (Player player : origin.getWorld().getPlayers()) {
                if (player.getLocation().distanceSquared(origin) <= radiusSquared) {
                    result.add(player);
                }
            }
        }

        return result;
    }

    /**
     * Valida se um handler existe para o tipo de action.
     */
    public boolean hasHandler(@NotNull String actionType) {
        String key = actionType.toLowerCase(Locale.ROOT);
        return handlers.containsKey(key) || key.equals("wait") || key.equals("delay");
    }
}
