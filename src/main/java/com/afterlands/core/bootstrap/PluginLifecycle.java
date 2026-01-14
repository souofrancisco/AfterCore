package com.afterlands.core.bootstrap;

import com.afterlands.core.AfterCorePlugin;
import com.afterlands.core.api.AfterCore;
import com.afterlands.core.api.AfterCoreAPI;
import com.afterlands.core.diagnostics.commands.ACoreCommand;
import com.afterlands.core.util.PluginBanner;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;

import java.util.Objects;

/**
 * Orchestrates plugin enable and disable lifecycle.
 */
public class PluginLifecycle {

    private final AfterCorePlugin plugin;
    private PluginRegistry registry;

    public PluginLifecycle(AfterCorePlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin);
    }

    public void enable() {
        long startTime = System.currentTimeMillis();
        PluginBanner.printBanner(plugin);

        try {
            // Initialize DI container
            registry = new PluginRegistry(plugin);
            registry.initialize();

            // Register commands
            registerCommands();

            // Register Services API
            plugin.getServer().getServicesManager().register(AfterCoreAPI.class, plugin, plugin,
                    ServicePriority.Normal);
            AfterCore.invalidate(); // Force refresh of static accessor

            PluginBanner.printLoadTime(plugin, startTime);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to enable AfterCore: " + e.getMessage());
            e.printStackTrace();
            plugin.getServer().getPluginManager().disablePlugin(plugin);
        }
    }

    public void disable() {
        try {
            plugin.getServer().getServicesManager().unregister(AfterCoreAPI.class, plugin);
        } catch (Throwable ignored) {
        }

        AfterCore.invalidate();

        if (registry != null) {
            registry.shutdown();
        }

        plugin.getLogger().info("AfterCore desabilitado.");
    }

    public PluginRegistry getRegistry() {
        return registry;
    }

    private void registerCommands() {
        PluginCommand acoreCmd = plugin.getCommand("acore");
        if (acoreCmd != null) {
            ACoreCommand acoreHandler = new ACoreCommand(
                    plugin,
                    registry.getDiagnostics(),
                    registry.getScheduler(),
                    registry.getMetrics());
            acoreCmd.setExecutor(acoreHandler);
            acoreCmd.setTabCompleter(acoreHandler);
        }
    }
}
