package com.afterlands.core.database.impl;

import com.afterlands.core.concurrent.SchedulerService;
import com.afterlands.core.database.*;
import com.afterlands.core.database.impl.dialect.DatabaseDialect;
import com.afterlands.core.database.impl.dialect.MySqlDialect;
import com.afterlands.core.database.impl.dialect.SqliteDialect;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Registry de datasources baseado em HikariCP.
 *
 * <p>
 * Gerencia múltiplos datasources isolados, cada um com seu próprio
 * pool HikariCP e migrations. Mantém retrocompatibilidade total com
 * a API anterior através do datasource "default".
 * </p>
 *
 * @since 2.0.0
 */
public final class HikariSqlService implements SqlService {

    private final Plugin plugin;
    private final Logger logger;
    private final SchedulerService scheduler;
    private final boolean debug;

    private final Map<DatabaseType, DatabaseDialect> dialects = new LinkedHashMap<>();
    private final Map<String, HikariSqlDataSource> datasources = new ConcurrentHashMap<>();
    private final Map<String, Map<String, SqlMigration>> pendingMigrations = new ConcurrentHashMap<>();

    public HikariSqlService(@NotNull Plugin plugin, @NotNull SchedulerService scheduler, boolean debug) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.scheduler = scheduler;
        this.debug = debug;

        // Registra dialetos suportados
        registerDialect(new MySqlDialect());
        registerDialect(new SqliteDialect());
    }

    private void registerDialect(@NotNull DatabaseDialect dialect) {
        dialects.put(dialect.type(), dialect);
    }

    // ==================== Multi-Datasource API ====================

    @Override
    public @NotNull SqlDataSource datasource(@NotNull String name) {
        Objects.requireNonNull(name, "name");
        SqlDataSource ds = datasources.get(name);
        if (ds == null) {
            throw new IllegalStateException(
                    "Datasource '" + name + "' não encontrado. Datasources disponíveis: " + getDatasourceNames());
        }
        return ds;
    }

    @Override
    public boolean hasDatasource(@NotNull String name) {
        return datasources.containsKey(name);
    }

    @Override
    public @NotNull Set<String> getDatasourceNames() {
        return Collections.unmodifiableSet(datasources.keySet());
    }

    @Override
    public void registerMigration(@NotNull String datasourceName, @NotNull String id, @NotNull SqlMigration migration) {
        Objects.requireNonNull(datasourceName, "datasourceName");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(migration, "migration");

        pendingMigrations.computeIfAbsent(datasourceName, k -> new LinkedHashMap<>()).put(id, migration);

        if (debug) {
            logger.info("[AfterCore] Registered migration '" + id + "' for datasource '" + datasourceName + "'");
        }

        // Fix: If datasource is already initialized (e.g. plugin loading late), apply
        // immediately
        if (hasDatasource(datasourceName)) {
            HikariSqlDataSource ds = datasources.get(datasourceName);
            if (ds != null && ds.isInitialized()) {
                // Apply only this migration or all pending?
                // Applying all pending for this DS is safer to ensure order
                applyMigrationsForDatasource(ds, pendingMigrations.get(datasourceName));
            }
        }
    }

    @Override
    public @NotNull Map<String, Map<String, Object>> getAllPoolStats() {
        Map<String, Map<String, Object>> allStats = new LinkedHashMap<>();
        for (Map.Entry<String, HikariSqlDataSource> entry : datasources.entrySet()) {
            allStats.put(entry.getKey(), entry.getValue().getPoolStats());
        }
        return allStats;
    }

    // ==================== Configuration & Lifecycle ====================

    @Override
    public void reloadFromConfig(@Nullable ConfigurationSection section) {
        closeAll();

        if (section == null) {
            logger.info("[AfterCore] database: seção ausente (desabilitado)");
            return;
        }

        if (!section.getBoolean("enabled", true)) {
            logger.info("[AfterCore] database: disabled");
            return;
        }

        // Pool defaults (para herança)
        ConfigurationSection poolDefaults = section.getConfigurationSection("pool");

        // Verifica se há configuração multi-datasource
        ConfigurationSection datasourcesSection = section.getConfigurationSection("datasources");

        if (datasourcesSection != null && !datasourcesSection.getKeys(false).isEmpty()) {
            // Modo multi-datasource
            for (String dsName : datasourcesSection.getKeys(false)) {
                ConfigurationSection dsConfig = datasourcesSection.getConfigurationSection(dsName);
                if (dsConfig != null) {
                    initializeDatasource(dsName, dsConfig, poolDefaults);
                }
            }
        } else {
            // Modo legado: usa config raiz como datasource "default"
            initializeLegacyDefault(section, poolDefaults);
        }

        // Aplica migrations pendentes
        applyAllMigrations();

        logger.info("[AfterCore] Datasources inicializados: " + getDatasourceNames());
    }

    private void initializeDatasource(
            @NotNull String name,
            @NotNull ConfigurationSection dsConfig,
            @Nullable ConfigurationSection poolDefaults) {
        try {
            String typeName = dsConfig.getString("type", "mysql");
            DatabaseType type = DatabaseType.fromString(typeName);
            DatabaseDialect dialect = dialects.get(type);

            if (dialect == null) {
                throw new IllegalArgumentException("Database type não suportado: " + type);
            }

            HikariSqlDataSource ds = new HikariSqlDataSource(
                    name, plugin, scheduler, dialect, dsConfig, poolDefaults, debug);

            datasources.put(name, ds);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "[AfterCore] Falha ao inicializar datasource '" + name + "'", e);
            throw new RuntimeException("Falha ao inicializar datasource '" + name + "'", e);
        }
    }

    private void initializeLegacyDefault(
            @NotNull ConfigurationSection section,
            @Nullable ConfigurationSection poolDefaults) {
        try {
            String typeName = section.getString("type", "mysql");
            DatabaseType type = DatabaseType.fromString(typeName);
            DatabaseDialect dialect = dialects.get(type);

            if (dialect == null) {
                throw new IllegalArgumentException("Database type não suportado: " + type);
            }

            HikariSqlDataSource ds = new HikariSqlDataSource(
                    DEFAULT_DATASOURCE, plugin, scheduler, dialect, section, poolDefaults, debug);

            datasources.put(DEFAULT_DATASOURCE, ds);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "[AfterCore] Falha ao inicializar datasource default", e);
            throw new RuntimeException("Falha ao inicializar datasource default", e);
        }
    }

    private void applyAllMigrations() {
        for (Map.Entry<String, Map<String, SqlMigration>> dsEntry : pendingMigrations.entrySet()) {
            String dsName = dsEntry.getKey();
            Map<String, SqlMigration> migrations = dsEntry.getValue();

            if (migrations.isEmpty())
                continue;

            HikariSqlDataSource ds = datasources.get(dsName);
            if (ds == null) {
                logger.warning("[AfterCore] Cannot apply migrations for non-existent datasource '" + dsName + "'");
                continue;
            }

            applyMigrationsForDatasource(ds, migrations);
        }
    }

    private void applyMigrationsForDatasource(
            @NotNull HikariSqlDataSource ds,
            @NotNull Map<String, SqlMigration> migrations) {
        if (!ds.isInitialized()) {
            logger.warning("[AfterCore] Skipping migrations for uninitialized datasource '" + ds.name() + "'");
            return;
        }

        logger.info(
                "[AfterCore] Checking " + migrations.size() + " migration(s) for datasource '" + ds.name() + "'...");
        int success = 0;
        int skipped = 0;

        try (Connection conn = ds.getConnection()) {
            // 1. Ensure migrations tracking table exists
            try (var stmt = conn.createStatement()) {
                stmt.execute("""
                        CREATE TABLE IF NOT EXISTS aftercore_schema_migrations (
                            migration_id VARCHAR(128) PRIMARY KEY,
                            applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        )
                        """);
            }

            for (Map.Entry<String, SqlMigration> entry : migrations.entrySet()) {
                String id = entry.getKey();
                SqlMigration migration = entry.getValue();

                // 2. Check if already applied
                boolean alreadyApplied = false;
                try (var pstmt = conn
                        .prepareStatement("SELECT 1 FROM aftercore_schema_migrations WHERE migration_id = ?")) {
                    pstmt.setString(1, id);
                    try (var rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            alreadyApplied = true;
                        }
                    }
                }

                if (alreadyApplied) {
                    skipped++;
                    continue; // Skip without error
                }

                // 3. Apply
                try {
                    migration.apply(conn);

                    // 4. Record success
                    try (var pstmt = conn
                            .prepareStatement("INSERT INTO aftercore_schema_migrations (migration_id) VALUES (?)")) {
                        pstmt.setString(1, id);
                        pstmt.executeUpdate();
                    }

                    success++;
                    if (debug) {
                        logger.info("[AfterCore] ✓ Migration '" + id + "' executed on '" + ds.name() + "'");
                    }
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "[AfterCore] ✗ Migration '" + id + "' FAILED on '" + ds.name() + "'", e);
                    throw e; // Stop execution
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to apply migrations for datasource '" + ds.name() + "'", e);
        }

        if (success > 0 || skipped > 0) {
            logger.info("[AfterCore] Applied " + success + " new migration(s) to '" + ds.name() + "' (" + skipped
                    + " skipped).");
        }
    }

    private void closeAll() {
        for (HikariSqlDataSource ds : datasources.values()) {
            try {
                ds.close();
            } catch (Throwable ignored) {
            }
        }
        datasources.clear();
    }

    // ==================== Default Datasource API (Retrocompatível)
    // ====================

    private @NotNull SqlDataSource defaultDatasource() {
        if (!hasDatasource(DEFAULT_DATASOURCE)) {
            throw new IllegalStateException(
                    "Datasource default não inicializado. Configure 'database.datasources.default' ou use config legado.");
        }
        return datasource(DEFAULT_DATASOURCE);
    }

    @Override
    public boolean isEnabled() {
        return hasDatasource(DEFAULT_DATASOURCE) && defaultDatasource().isEnabled();
    }

    @Override
    public boolean isInitialized() {
        return hasDatasource(DEFAULT_DATASOURCE) && defaultDatasource().isInitialized();
    }

    @Override
    public @NotNull DataSource dataSource() {
        return defaultDatasource().dataSource();
    }

    @Override
    public @NotNull Connection getConnection() throws SQLException {
        return defaultDatasource().getConnection();
    }

    @Override
    public @NotNull <T> CompletableFuture<T> supplyAsync(@NotNull SqlFunction<Connection, T> fn) {
        return defaultDatasource().supplyAsync(fn);
    }

    @Override
    public @NotNull CompletableFuture<Void> runAsync(@NotNull SqlConsumer<Connection> fn) {
        return defaultDatasource().runAsync(fn);
    }

    @Override
    public void registerMigration(@NotNull String id, @NotNull SqlMigration migration) {
        registerMigration(DEFAULT_DATASOURCE, id, migration);
    }

    @Override
    public @NotNull <T> CompletableFuture<T> inTransaction(@NotNull SqlFunction<Connection, T> fn) {
        return defaultDatasource().inTransaction(fn);
    }

    @Override
    public @NotNull CompletableFuture<Boolean> isAvailable() {
        if (!hasDatasource(DEFAULT_DATASOURCE)) {
            return CompletableFuture.completedFuture(false);
        }
        return defaultDatasource().isAvailable();
    }

    @Override
    public @NotNull Map<String, Object> getPoolStats() {
        if (!hasDatasource(DEFAULT_DATASOURCE)) {
            return Map.of();
        }
        return defaultDatasource().getPoolStats();
    }

    @Override
    public @NotNull SqlDataSource forPlugin(@NotNull org.bukkit.plugin.Plugin plugin) {
        String dsName = plugin.getConfig().getString("database.datasource", DEFAULT_DATASOURCE);
        if (!hasDatasource(dsName)) {
            throw new IllegalStateException(
                    "Datasource '" + dsName + "' não existe. Configure em AfterCore config.yml");
        }
        return datasource(dsName);
    }

    @Override
    public void close() {
        closeAll();
    }
}
