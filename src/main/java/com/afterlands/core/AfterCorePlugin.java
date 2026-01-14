package com.afterlands.core;

import com.afterlands.core.actions.ActionService;
import com.afterlands.core.actions.ActionSpec;
import com.afterlands.core.api.AfterCoreAPI;
import com.afterlands.core.bootstrap.PluginLifecycle;
import com.afterlands.core.commands.CommandService;
import com.afterlands.core.concurrent.SchedulerService;
import com.afterlands.core.conditions.ConditionService;
import com.afterlands.core.config.ConfigService;
import com.afterlands.core.config.MessageService;
import com.afterlands.core.database.SqlService;
import com.afterlands.core.diagnostics.DiagnosticsService;
import com.afterlands.core.inventory.InventoryService;
import com.afterlands.core.metrics.MetricsService;
import com.afterlands.core.protocol.ProtocolService;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public final class AfterCorePlugin extends JavaPlugin implements AfterCoreAPI {

    private final PluginLifecycle lifecycle;

    public AfterCorePlugin() {
        this.lifecycle = new PluginLifecycle(this);
    }

    @Override
    public void onEnable() {
        lifecycle.enable();
    }

    @Override
    public void onDisable() {
        lifecycle.disable();
    }

    // ==================== API Implementation via Delegate ====================

    @Override
    @NotNull
    public SchedulerService scheduler() {
        return lifecycle.getRegistry().getScheduler();
    }

    @Override
    @NotNull
    public ConfigService config() {
        return lifecycle.getRegistry().getConfig();
    }

    @Override
    @NotNull
    public MessageService messages() {
        return lifecycle.getRegistry().getMessages();
    }

    @Override
    @NotNull
    public SqlService sql() {
        return lifecycle.getRegistry().getSql();
    }

    @Override
    @NotNull
    public ConditionService conditions() {
        return lifecycle.getRegistry().getConditions();
    }

    @Override
    @NotNull
    public ActionService actions() {
        return lifecycle.getRegistry().getActions();
    }

    @Override
    @NotNull
    public CommandService commands() {
        return lifecycle.getRegistry().getCommands();
    }

    @Override
    @NotNull
    public ProtocolService protocol() {
        return lifecycle.getRegistry().getProtocol();
    }

    @Override
    @NotNull
    public DiagnosticsService diagnostics() {
        return lifecycle.getRegistry().getDiagnostics();
    }

    @Override
    @NotNull
    public MetricsService metrics() {
        return lifecycle.getRegistry().getMetrics();
    }

    @Override
    @NotNull
    public InventoryService inventory() {
        return lifecycle.getRegistry().getInventory();
    }

    @Override
    public void executeAction(@NotNull ActionSpec spec, @NotNull Player viewer) {
        executeAction(spec, viewer, viewer.getLocation());
    }

    @Override
    public void executeAction(@NotNull ActionSpec spec, @NotNull Player viewer, @NotNull Location origin) {
        lifecycle.getRegistry().getActionExecutor().execute(spec, viewer, origin);
    }

    /**
     * Retorna o ActionExecutor (para uso interno e plugins dependentes).
     */
    @NotNull
    public com.afterlands.core.actions.ActionExecutor actionExecutor() {
        return lifecycle.getRegistry().getActionExecutor();
    }
}
