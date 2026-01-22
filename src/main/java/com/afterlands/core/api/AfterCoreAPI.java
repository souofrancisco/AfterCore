package com.afterlands.core.api;

import com.afterlands.core.actions.ActionService;
import com.afterlands.core.actions.ActionSpec;
import com.afterlands.core.concurrent.SchedulerService;
import com.afterlands.core.conditions.ConditionService;
import com.afterlands.core.commands.CommandService;
import com.afterlands.core.config.ConfigService;
import com.afterlands.core.config.MessageService;
import com.afterlands.core.database.SqlDataSource;
import com.afterlands.core.database.SqlService;
import com.afterlands.core.diagnostics.DiagnosticsService;
import com.afterlands.core.inventory.InventoryService;
import com.afterlands.core.metrics.MetricsService;
import com.afterlands.core.protocol.ProtocolService;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Contrato público do AfterCore.
 *
 * <p>
 * <b>Threading:</b> nenhum método deve bloquear a main thread.
 * </p>
 */
public interface AfterCoreAPI {

    @NotNull
    SchedulerService scheduler();

    @NotNull
    ConfigService config();

    @NotNull
    MessageService messages();

    @NotNull
    SqlService sql();

    /**
     * Atalho para obter um datasource específico.
     *
     * <p>
     * Equivalente a {@code sql().datasource(name)}.
     * </p>
     *
     * @param name nome do datasource (ex: "default", "analytics")
     * @return SqlDataSource para o nome especificado
     * @throws IllegalStateException se o datasource não existir
     * @since 2.0.0
     */
    default @NotNull SqlDataSource sql(@NotNull String name) {
        return sql().datasource(name);
    }

    @NotNull
    ConditionService conditions();

    @NotNull
    ActionService actions();

    @NotNull
    CommandService commands();

    @NotNull
    ProtocolService protocol();

    @NotNull
    DiagnosticsService diagnostics();

    @NotNull
    MetricsService metrics();

    @NotNull
    InventoryService inventory();

    /**
     * Executa uma action em um player.
     *
     * <p>
     * A action será executada considerando o scope definido na spec:
     * </p>
     * <ul>
     * <li>VIEWER: apenas o viewer recebe</li>
     * <li>NEARBY: players próximos ao viewer (dentro do radius)</li>
     * <li>ALL: todos os players online</li>
     * </ul>
     *
     * <p>
     * Thread safety: Garante execução na main thread quando necessário.
     * </p>
     *
     * @param spec   Action spec parseada (via {@link ActionService#parse(String)})
     * @param viewer Player que está "vendo" (usado para scope VIEWER e referência
     *               de posição)
     */
    void executeAction(@NotNull ActionSpec spec, @NotNull Player viewer);

    /**
     * Executa uma action com origem customizada.
     *
     * <p>
     * Útil quando a origem da action não é a posição do viewer
     * (ex: explosão em uma localização específica).
     * </p>
     *
     * @param spec   Action spec parseada
     * @param viewer Player de referência
     * @param origin Localização de origem (para cálculo de NEARBY)
     */
    void executeAction(@NotNull ActionSpec spec, @NotNull Player viewer, @NotNull Location origin);
}
