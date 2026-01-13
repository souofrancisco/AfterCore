package com.afterlands.core.bootstrap;

import com.afterlands.core.actions.ActionExecutor;
import com.afterlands.core.actions.ActionService;
import com.afterlands.core.actions.ActionSpec;
import com.afterlands.core.actions.handlers.*;
import com.afterlands.core.actions.handlers.inventory.ClosePanelHandler;
import com.afterlands.core.actions.handlers.inventory.OpenPanelHandler;
import com.afterlands.core.actions.handlers.inventory.RefreshPanelHandler;
import com.afterlands.core.actions.impl.DefaultActionService;
import com.afterlands.core.api.AfterCore;
import com.afterlands.core.api.AfterCoreAPI;
import com.afterlands.core.concurrent.SchedulerService;
import com.afterlands.core.concurrent.impl.DefaultSchedulerService;
import com.afterlands.core.conditions.ConditionService;
import com.afterlands.core.conditions.impl.DefaultConditionService;
import com.afterlands.core.commands.CommandService;
import com.afterlands.core.commands.impl.DefaultCommandService;
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
import com.afterlands.core.diagnostics.commands.ACoreCommand;
import com.afterlands.core.diagnostics.impl.DefaultDiagnosticsService;
import com.afterlands.core.inventory.InventoryService;
import com.afterlands.core.inventory.config.InventoryConfigManager;
import com.afterlands.core.inventory.impl.DefaultInventoryService;
import com.afterlands.core.inventory.migrations.CreateInventoryStatesMigration;
import com.afterlands.core.metrics.MetricsService;
import com.afterlands.core.metrics.impl.DefaultMetricsService;
import com.afterlands.core.protocol.ProtocolService;
import com.afterlands.core.protocol.impl.DefaultProtocolService;
import com.afterlands.core.util.PluginBanner;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class AfterCorePlugin extends JavaPlugin implements AfterCoreAPI {

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

    @Override
    public void onEnable() {
        long startTime = System.currentTimeMillis();
        PluginBanner.printBanner(this);

        // 1. Carregar config do usuário
        saveDefaultConfig();

        // 2. Aplicar updater para config.yml (mesclar defaults novos, aplicar
        // migrations)
        updateConfigFile("config.yml");

        // 2.1 Aplicar updater para messages.yml
        saveDefaultMessages();
        updateConfigFile("messages.yml");

        // 3. Validar configuração
        ConfigValidator validator = new ConfigValidator();
        ValidationResult validation = validator.validate(getConfig());

        if (!validation.isValid()) {
            if (validation.hasErrors()) {
                getLogger().severe("Erros encontrados na configuração:");
                validation.getErrors().forEach(error -> getLogger().severe("  " + error.toString()));

                // Fail-fast se houver erros críticos
                getLogger().severe("Plugin não pode iniciar com configuração inválida!");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            if (validation.hasWarnings()) {
                getLogger().warning("Avisos encontrados na configuração:");
                validation.getWarnings().forEach(warning -> getLogger().warning("  " + warning.toString()));
            }
        }

        boolean debug = getConfig().getBoolean("debug", false);

        scheduler = new DefaultSchedulerService(this, debug);
        config = new DefaultConfigService(this, debug);
        messages = new DefaultMessageService(this, config, debug);

        sql = new HikariSqlService(this, scheduler, debug);

        // Registrar migrations ANTES de reloadFromConfig (que executa as migrations)
        registerMigrations();

        sql.reloadFromConfig(getConfig().getConfigurationSection("database"));

        conditions = new DefaultConditionService(this, scheduler, debug);
        actions = new DefaultActionService(conditions, debug);

        // Registrar handlers padrão de actions
        registerDefaultActionHandlers();

        // Criar ActionExecutor
        actionExecutor = new ActionExecutor(this, conditions, actions.getHandlers(), debug);

        commands = new DefaultCommandService(this, messages, debug);

        // Metrics (before protocol, which uses metrics)
        metrics = new DefaultMetricsService();

        // Protocol - read config
        long batchIntervalMs = getConfig().getLong("protocol.batch-interval-ms", 50);
        int maxChunksPerBatch = getConfig().getInt("protocol.max-chunks-per-batch", 16);
        protocol = new DefaultProtocolService(this, scheduler, metrics, debug, batchIntervalMs, maxChunksPerBatch);
        protocol.start(); // degrada graceful se ProtocolLib não existir

        // Diagnostics
        int ioThreads = getConfig().getInt("concurrency.io-threads", 8);
        int cpuThreads = getConfig().getInt("concurrency.cpu-threads", 4);
        diagnostics = new DefaultDiagnosticsService(this, sql, ioThreads, cpuThreads);

        // Inventory Framework
        InventoryConfigManager invConfigManager = new InventoryConfigManager(this, config);
        inventory = new DefaultInventoryService(this, scheduler, sql, actions, invConfigManager);

        // Registrar comando /acore
        org.bukkit.command.PluginCommand acoreCmd = getCommand("acore");
        if (acoreCmd != null) {
            ACoreCommand acoreHandler = new ACoreCommand(this, diagnostics, scheduler, metrics);
            acoreCmd.setExecutor(acoreHandler);
            acoreCmd.setTabCompleter(acoreHandler);
        }

        // Service discovery
        getServer().getServicesManager().register(AfterCoreAPI.class, this, this, ServicePriority.Normal);
        AfterCore.invalidate();

        PluginBanner.printLoadTime(this, startTime);
    }

    @Override
    public void onDisable() {
        try {
            getServer().getServicesManager().unregister(AfterCoreAPI.class, this);
        } catch (Throwable ignored) {
        }

        AfterCore.invalidate();

        try {
            if (protocol != null)
                protocol.stop();
        } catch (Throwable ignored) {
        }

        try {
            if (inventory != null && inventory instanceof DefaultInventoryService) {
                ((DefaultInventoryService) inventory).shutdown();
            }
        } catch (Throwable ignored) {
        }

        try {
            if (sql != null)
                sql.close();
        } catch (Throwable ignored) {
        }

        try {
            if (scheduler != null)
                scheduler.shutdown();
        } catch (Throwable ignored) {
        }

        getLogger().info("AfterCore desabilitado.");
    }

    @Override
    @NotNull
    public SchedulerService scheduler() {
        return scheduler;
    }

    @Override
    @NotNull
    public ConfigService config() {
        return config;
    }

    @Override
    @NotNull
    public MessageService messages() {
        return messages;
    }

    @Override
    @NotNull
    public SqlService sql() {
        return sql;
    }

    @Override
    @NotNull
    public ConditionService conditions() {
        return conditions;
    }

    @Override
    @NotNull
    public ActionService actions() {
        return actions;
    }

    @Override
    @NotNull
    public CommandService commands() {
        return commands;
    }

    @Override
    @NotNull
    public ProtocolService protocol() {
        return protocol;
    }

    @Override
    @NotNull
    public DiagnosticsService diagnostics() {
        return diagnostics;
    }

    @Override
    @NotNull
    public MetricsService metrics() {
        return metrics;
    }

    @Override
    @NotNull
    public InventoryService inventory() {
        return inventory;
    }

    /**
     * Retorna o ActionExecutor (para uso interno e plugins dependentes).
     */
    @NotNull
    public ActionExecutor actionExecutor() {
        return actionExecutor;
    }

    @Override
    public void executeAction(@NotNull ActionSpec spec, @NotNull Player viewer) {
        executeAction(spec, viewer, viewer.getLocation());
    }

    @Override
    public void executeAction(@NotNull ActionSpec spec, @NotNull Player viewer, @NotNull Location origin) {
        if (actionExecutor == null) {
            throw new IllegalStateException("ActionExecutor not initialized!");
        }
        actionExecutor.execute(spec, viewer, origin);
    }

    /**
     * Registra handlers padrão de actions.
     * Estes handlers são reutilizáveis por todos os plugins.
     */
    private void registerDefaultActionHandlers() {
        boolean debug = getConfig().getBoolean("debug", false);

        // Handlers básicos
        actions.registerHandler("message", new MessageHandler());
        actions.registerHandler("actionbar", new ActionBarHandler());

        // Sound handler with aliases for backward compatibility
        SoundHandler soundHandler = new SoundHandler();
        actions.registerHandler("sound", soundHandler);
        actions.registerHandler("play_sound", soundHandler); // Alias

        actions.registerHandler("title", new TitleHandler());
        actions.registerHandler("teleport", new TeleportHandler());
        actions.registerHandler("potion", new PotionHandler());

        // Console command handler with aliases
        ConsoleCommandHandler consoleHandler = new ConsoleCommandHandler();
        actions.registerHandler("console", consoleHandler);
        actions.registerHandler("console_command", consoleHandler); // Alias

        actions.registerHandler("player_command", new PlayerCommandHandler());

        // Resource pack sound handler with aliases
        ResourcePackSoundHandler rpSoundHandler = new ResourcePackSoundHandler();
        actions.registerHandler("resource_pack_sound", rpSoundHandler);
        actions.registerHandler("play_resourcepack_sound", rpSoundHandler); // Alias

        // Handlers de mensagens avançadas
        actions.registerHandler("centered_message", new CenteredMessageHandler());
        actions.registerHandler("global_message", new GlobalMessageHandler());
        actions.registerHandler("global_centered_message", new GlobalCenteredMessageHandler());

        // Handlers de inventário/painel
        OpenPanelHandler openPanelHandler = new OpenPanelHandler();
        actions.registerHandler("open-panel", openPanelHandler);
        actions.registerHandler("open_panel", openPanelHandler); // Alias
        actions.registerHandler("open", openPanelHandler); // Alias curto

        ClosePanelHandler closePanelHandler = new ClosePanelHandler();
        actions.registerHandler("close-panel", closePanelHandler);
        actions.registerHandler("close_panel", closePanelHandler); // Alias
        actions.registerHandler("close", closePanelHandler); // Alias curto

        actions.registerHandler("refresh", new RefreshPanelHandler());

        if (debug) {
            getLogger().info("Registered " + actions.getHandlers().size() + " default action handlers");
        }
    }

    /**
     * Atualiza um arquivo de configuração mesclando novas chaves dos defaults.
     *
     * @param filename Nome do arquivo (ex: "config.yml", "messages.yml")
     */
    private void updateConfigFile(String filename) {
        InputStream defaultStream = getResource(filename);
        if (defaultStream == null) {
            return;
        }

        File configFile = new File(getDataFolder(), filename);
        if (!configFile.exists()) {
            return;
        }

        YamlConfiguration currentConfig = YamlConfiguration.loadConfiguration(configFile);
        YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultStream, StandardCharsets.UTF_8));

        ConfigUpdater updater = new ConfigUpdater(getLogger());
        boolean updated = updater.update(currentConfig, defaultConfig);

        if (updated) {
            try {
                currentConfig.save(configFile);
                getLogger().info(filename + " atualizado com novas chaves.");

                // Reload config.yml if it was updated
                if ("config.yml".equals(filename)) {
                    reloadConfig();
                }
            } catch (IOException e) {
                getLogger().warning("Falha ao salvar " + filename + ": " + e.getMessage());
            }
        }
    }

    /**
     * Salva o messages.yml padrão se não existir.
     */
    private void saveDefaultMessages() {
        File messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
        }
    }

    /**
     * Registra migrations do AfterCore.
     */
    private void registerMigrations() {
        // Note: sql may not be enabled yet (called before reloadFromConfig)
        // Migrations are registered here and executed during reloadFromConfig

        // Inventory states migration
        sql.registerMigration(
                CreateInventoryStatesMigration.getMigrationId(),
                new CreateInventoryStatesMigration());

        getLogger().info("Registered " + 1 + " database migration(s)");
    }
}
