package com.afterlands.core.bootstrap;

import com.afterlands.core.AfterCorePlugin;
import com.afterlands.core.actions.ActionExecutor;
import com.afterlands.core.actions.ActionService;
import com.afterlands.core.actions.handlers.*;
import com.afterlands.core.actions.handlers.inventory.ClosePanelHandler;
import com.afterlands.core.actions.handlers.inventory.OpenPanelHandler;
import com.afterlands.core.actions.handlers.inventory.RefreshPanelHandler;
import com.afterlands.core.actions.impl.DefaultActionService;
import com.afterlands.core.commands.CommandService;
import com.afterlands.core.commands.impl.DefaultCommandService;
import com.afterlands.core.concurrent.SchedulerService;
import com.afterlands.core.concurrent.impl.DefaultSchedulerService;
import com.afterlands.core.conditions.ConditionService;
import com.afterlands.core.conditions.impl.DefaultConditionService;
import com.afterlands.core.config.ConfigService;
import com.afterlands.core.config.MessageService;
import com.afterlands.core.config.impl.DefaultConfigService;
import com.afterlands.core.config.impl.DefaultMessageService;
import com.afterlands.core.config.update.ConfigUpdater;
import com.afterlands.core.config.validate.ConfigValidator;
import com.afterlands.core.config.validate.ValidationResult;
import com.afterlands.core.database.SqlService;
import com.afterlands.core.database.impl.HikariSqlService;
import com.afterlands.core.diagnostics.DiagnosticsService;
import com.afterlands.core.diagnostics.impl.DefaultDiagnosticsService;
import com.afterlands.core.inventory.InventoryService;
import com.afterlands.core.inventory.config.InventoryConfigManager;
import com.afterlands.core.inventory.impl.DefaultInventoryService;
import com.afterlands.core.inventory.migrations.CreateInventoryStatesMigration;
import com.afterlands.core.metrics.MetricsService;
import com.afterlands.core.metrics.impl.DefaultMetricsService;
import com.afterlands.core.protocol.ProtocolService;
import com.afterlands.core.protocol.impl.DefaultProtocolService;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * Dependency Injection container for AfterCore.
 * Initializes and manages all core services.
 */
public class PluginRegistry {

    private final AfterCorePlugin plugin;
    private final Logger logger;

    // Services
    private SchedulerService scheduler;
    private ConfigService config;
    private MessageService messages;
    private SqlService sql;
    private ConditionService conditions;
    private ActionService actions;
    private ActionExecutor actionExecutor;
    private CommandService commands;
    private ProtocolService protocol;
    private DiagnosticsService diagnostics;
    private MetricsService metrics;
    private InventoryService inventory;

    public PluginRegistry(AfterCorePlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void initialize() {
        // 1. Config loading & validation
        plugin.saveDefaultConfig();
        updateConfigFile("config.yml");

        saveDefaultMessages();
        updateConfigFile("messages.yml");

        // Validate config
        ConfigValidator validator = new ConfigValidator();
        ValidationResult validation = validator.validate(plugin.getConfig());

        if (!validation.isValid()) {
            if (validation.hasErrors()) {
                logger.severe("Erros encontrados na configuração:");
                validation.getErrors().forEach(error -> logger.severe("  " + error.toString()));
                logger.severe("Plugin não pode iniciar com configuração inválida!");
                throw new IllegalStateException("Invalid configuration");
            }
            if (validation.hasWarnings()) {
                logger.warning("Avisos encontrados na configuração:");
                validation.getWarnings().forEach(warning -> logger.warning("  " + warning.toString()));
            }
        }

        boolean debug = plugin.getConfig().getBoolean("debug", false);

        // 2. Base Services
        this.scheduler = new DefaultSchedulerService(plugin, debug);
        this.config = new DefaultConfigService(plugin, debug);
        this.messages = new DefaultMessageService(plugin, config, debug);

        // 3. Database
        this.sql = new HikariSqlService(plugin, scheduler, debug);
        registerMigrations();
        this.sql.reloadFromConfig(plugin.getConfig().getConfigurationSection("database"));

        // 4. Logic Services
        this.conditions = new DefaultConditionService(plugin, scheduler, debug);
        this.actions = new DefaultActionService(conditions, debug);
        registerDefaultActionHandlers(debug);

        this.actionExecutor = new ActionExecutor(plugin, conditions, actions.getHandlers(), debug);

        // 5. Metrics & Diagnostics
        this.metrics = new DefaultMetricsService();
        int ioThreads = plugin.getConfig().getInt("concurrency.io-threads", 8);
        int cpuThreads = plugin.getConfig().getInt("concurrency.cpu-threads", 4);
        this.diagnostics = new DefaultDiagnosticsService(plugin, sql, ioThreads, cpuThreads);

        // 6. Commands (depends on config, messages, scheduler, metrics)
        this.commands = new DefaultCommandService(plugin, config, messages, scheduler, metrics, debug);

        // 7. Protocol
        long batchIntervalMs = plugin.getConfig().getLong("protocol.batch-interval-ms", 50);
        int maxChunksPerBatch = plugin.getConfig().getInt("protocol.max-chunks-per-batch", 16);
        this.protocol = new DefaultProtocolService(plugin, scheduler, metrics, debug, batchIntervalMs,
                maxChunksPerBatch);
        this.protocol.start();

        // 8. Inventory Framework
        InventoryConfigManager invConfigManager = new InventoryConfigManager(plugin, config);
        this.inventory = new DefaultInventoryService(plugin, scheduler, sql, actions, invConfigManager);
    }

    public void shutdown() {
        if (commands != null) {
            try {
                commands.unregisterAll();
            } catch (Throwable ignored) {
            }
        }
        if (protocol != null) {
            try {
                protocol.stop();
            } catch (Throwable ignored) {
            }
        }
        if (inventory != null && inventory instanceof DefaultInventoryService) {
            try {
                ((DefaultInventoryService) inventory).shutdown();
            } catch (Throwable ignored) {
            }
        }
        if (sql != null) {
            try {
                sql.close();
            } catch (Throwable ignored) {
            }
        }
        if (scheduler != null) {
            try {
                scheduler.shutdown();
            } catch (Throwable ignored) {
            }
        }
    }

    // ==================== Private Helpers ====================

    private void updateConfigFile(String filename) {
        InputStream defaultStream = plugin.getResource(filename);
        if (defaultStream == null)
            return;

        File configFile = new File(plugin.getDataFolder(), filename);
        if (!configFile.exists())
            return;

        YamlConfiguration currentConfig = YamlConfiguration.loadConfiguration(configFile);
        YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultStream, StandardCharsets.UTF_8));

        ConfigUpdater updater = new ConfigUpdater(logger);
        boolean updated = updater.update(currentConfig, defaultConfig);

        if (updated) {
            try {
                currentConfig.save(configFile);
                logger.info(filename + " atualizado com novas chaves.");
                if ("config.yml".equals(filename)) {
                    plugin.reloadConfig();
                }
            } catch (IOException e) {
                logger.warning("Falha ao salvar " + filename + ": " + e.getMessage());
            }
        }
    }

    private void saveDefaultMessages() {
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
    }

    private void registerMigrations() {
        sql.registerMigration(
                CreateInventoryStatesMigration.getMigrationId(),
                new CreateInventoryStatesMigration());
        logger.info("Registered 1 database migration(s)");
    }

    private void registerDefaultActionHandlers(boolean debug) {
        // Basic handlers
        actions.registerHandler("message", new MessageHandler());
        actions.registerHandler("actionbar", new ActionBarHandler());

        SoundHandler soundHandler = new SoundHandler();
        actions.registerHandler("sound", soundHandler);
        actions.registerHandler("play_sound", soundHandler);

        actions.registerHandler("title", new TitleHandler());
        actions.registerHandler("teleport", new TeleportHandler());
        actions.registerHandler("potion", new PotionHandler());

        ConsoleCommandHandler consoleHandler = new ConsoleCommandHandler();
        actions.registerHandler("console", consoleHandler);
        actions.registerHandler("console_command", consoleHandler);

        actions.registerHandler("player_command", new PlayerCommandHandler());

        ResourcePackSoundHandler rpSoundHandler = new ResourcePackSoundHandler();
        actions.registerHandler("resource_pack_sound", rpSoundHandler);
        actions.registerHandler("play_resourcepack_sound", rpSoundHandler);

        actions.registerHandler("centered_message", new CenteredMessageHandler());
        actions.registerHandler("global_message", new GlobalMessageHandler());
        actions.registerHandler("global_centered_message", new GlobalCenteredMessageHandler());

        OpenPanelHandler openPanelHandler = new OpenPanelHandler();
        actions.registerHandler("open-panel", openPanelHandler);
        actions.registerHandler("open_panel", openPanelHandler);
        actions.registerHandler("open", openPanelHandler);

        ClosePanelHandler closePanelHandler = new ClosePanelHandler();
        actions.registerHandler("close-panel", closePanelHandler);
        actions.registerHandler("close_panel", closePanelHandler);
        actions.registerHandler("close", closePanelHandler);

        actions.registerHandler("refresh", new RefreshPanelHandler());

        if (debug) {
            logger.info("Registered " + actions.getHandlers().size() + " default action handlers");
        }
    }

    // ==================== Getters ====================

    public SchedulerService getScheduler() {
        return scheduler;
    }

    public ConfigService getConfig() {
        return config;
    }

    public MessageService getMessages() {
        return messages;
    }

    public SqlService getSql() {
        return sql;
    }

    public ConditionService getConditions() {
        return conditions;
    }

    public ActionService getActions() {
        return actions;
    }

    public ActionExecutor getActionExecutor() {
        return actionExecutor;
    }

    public CommandService getCommands() {
        return commands;
    }

    public ProtocolService getProtocol() {
        return protocol;
    }

    public DiagnosticsService getDiagnostics() {
        return diagnostics;
    }

    public MetricsService getMetrics() {
        return metrics;
    }

    public InventoryService getInventory() {
        return inventory;
    }

    public AfterCorePlugin getPlugin() {
        return plugin;
    }
}
