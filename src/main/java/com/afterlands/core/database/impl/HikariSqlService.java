package com.afterlands.core.database.impl;

import com.afterlands.core.concurrent.SchedulerService;
import com.afterlands.core.database.SqlConsumer;
import com.afterlands.core.database.SqlFunction;
import com.afterlands.core.database.SqlMigration;
import com.afterlands.core.database.SqlService;
import com.afterlands.core.database.DatabaseType;
import com.afterlands.core.database.impl.dialect.DatabaseDialect;
import com.afterlands.core.database.impl.dialect.MySqlDialect;
import com.afterlands.core.database.impl.dialect.SqliteDialect;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementação baseada em HikariCP.
 *
 * <p>
 * Referência: README oficial do HikariCP:
 * {@link <a href="https://github.com/brettwooldridge/HikariCP">HikariCP</a>}
 * </p>
 */
public final class HikariSqlService implements SqlService {
    // MySQL driver é relocado via shade. SQLite não deve ser relocado (sqlite-jdbc
    // depende de resources em org/sqlite).

    private final Plugin plugin;
    private final Logger logger;
    private final SchedulerService scheduler;
    private final boolean debug;

    private final Map<String, SqlMigration> migrations = new LinkedHashMap<>();
    private final Map<DatabaseType, DatabaseDialect> dialects = new LinkedHashMap<>();

    private volatile boolean enabled;
    private volatile HikariDataSource dataSource;

    public HikariSqlService(@NotNull Plugin plugin, @NotNull SchedulerService scheduler, boolean debug) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.scheduler = scheduler;
        this.debug = debug;

        // Dialects suportados
        registerDialect(new MySqlDialect());
        registerDialect(new SqliteDialect());
    }

    private void registerDialect(@NotNull DatabaseDialect dialect) {
        dialects.put(dialect.type(), dialect);
    }

    @Override
    public void reloadFromConfig(@Nullable ConfigurationSection section) {
        close();

        if (section == null) {
            enabled = false;
            logger.info("[AfterCore] database: seção ausente (desabilitado)");
            return;
        }

        enabled = section.getBoolean("enabled", true);
        if (!enabled) {
            logger.info("[AfterCore] database: disabled");
            return;
        }

        try {
            HikariConfig hikari = new HikariConfig();

            DatabaseType type = DatabaseType.fromString(section.getString("type", "mysql"));
            DatabaseDialect dialect = dialects.get(type);
            if (dialect == null) {
                throw new IllegalArgumentException("Database type não suportado: " + type);
            }

            // Config do tipo (jdbcUrl/driver/propriedades)
            dialect.configure(plugin, section, hikari);

            // Pool settings (genéricos). Para SQLite, o default é forçar pool pequeno se
            // não configurado.
            ConfigurationSection pool = section.getConfigurationSection("pool");
            if (pool != null) {
                hikari.setMaximumPoolSize(pool.getInt("maximum-pool-size", type == DatabaseType.SQLITE ? 1 : 10));
                hikari.setMinimumIdle(pool.getInt("minimum-idle", type == DatabaseType.SQLITE ? 1 : 2));
                hikari.setConnectionTimeout(pool.getLong("connection-timeout", 30000));
                hikari.setIdleTimeout(pool.getLong("idle-timeout", 600000));
                hikari.setMaxLifetime(pool.getLong("max-lifetime", 1800000));
            } else {
                // Defaults seguros
                hikari.setMaximumPoolSize(type == DatabaseType.SQLITE ? 1 : 10);
                hikari.setMinimumIdle(type == DatabaseType.SQLITE ? 1 : 2);
            }

            dataSource = new HikariDataSource(hikari);

            // Aplica migrations (idempotentes)
            applyMigrationsSync();

            logger.info("[AfterCore] MySQL pool inicializado com sucesso.");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[AfterCore] Falha ao inicializar pool MySQL", e);
            enabled = false;
            dataSource = null;
            throw new RuntimeException("Falha ao inicializar database", e);
        }
    }

    private void applyMigrationsSync() throws Exception {
        if (migrations.isEmpty()) {
            logger.info("[AfterCore] No database migrations registered");
            return;
        }

        logger.info("[AfterCore] Applying " + migrations.size() + " database migration(s)...");
        int success = 0;

        try (Connection conn = getConnection()) {
            for (Map.Entry<String, SqlMigration> entry : migrations.entrySet()) {
                String id = entry.getKey();
                SqlMigration migration = entry.getValue();
                try {
                    migration.apply(conn);
                    success++;
                    logger.info("[AfterCore] ✓ Migration executed: " + id);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "[AfterCore] ✗ Migration FAILED: " + id, e);
                    throw e;
                }
            }
        }

        logger.info("[AfterCore] Successfully applied " + success + "/" + migrations.size() + " migration(s)");
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean isInitialized() {
        return enabled && dataSource != null;
    }

    @Override
    public @NotNull DataSource dataSource() {
        HikariDataSource ds = dataSource;
        if (ds == null) {
            throw new IllegalStateException("DataSource não inicializado.");
        }
        return ds;
    }

    @Override
    public @NotNull Connection getConnection() throws SQLException {
        return dataSource().getConnection();
    }

    @Override
    public @NotNull <T> CompletableFuture<T> supplyAsync(@NotNull SqlFunction<Connection, T> fn) {
        Objects.requireNonNull(fn, "fn");
        if (!isInitialized()) {
            CompletableFuture<T> cf = new CompletableFuture<>();
            cf.completeExceptionally(new IllegalStateException("Database desabilitado/não inicializado."));
            return cf;
        }

        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection()) {
                return fn.apply(conn);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, scheduler.ioExecutor());
    }

    @Override
    public @NotNull CompletableFuture<Void> runAsync(@NotNull SqlConsumer<Connection> fn) {
        Objects.requireNonNull(fn, "fn");
        return supplyAsync(conn -> {
            fn.accept(conn);
            return null;
        });
    }

    @Override
    public void registerMigration(@NotNull String id, @NotNull SqlMigration migration) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(migration, "migration");
        migrations.put(id, migration);
    }

    @Override
    public @NotNull <T> CompletableFuture<T> inTransaction(@NotNull SqlFunction<Connection, T> fn) {
        Objects.requireNonNull(fn, "fn");
        if (!isInitialized()) {
            CompletableFuture<T> cf = new CompletableFuture<>();
            cf.completeExceptionally(new IllegalStateException("Database desabilitado/não inicializado."));
            return cf;
        }

        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection()) {
                boolean originalAutoCommit = conn.getAutoCommit();
                try {
                    conn.setAutoCommit(false);
                    T result = fn.apply(conn);
                    conn.commit();
                    return result;
                } catch (Exception e) {
                    try {
                        conn.rollback();
                    } catch (SQLException rollbackEx) {
                        logger.log(Level.WARNING, "[AfterCore] Rollback failed", rollbackEx);
                    }
                    throw new RuntimeException(e);
                } finally {
                    try {
                        conn.setAutoCommit(originalAutoCommit);
                    } catch (SQLException ex) {
                        logger.log(Level.WARNING, "[AfterCore] Failed to restore auto-commit", ex);
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, scheduler.ioExecutor());
    }

    @Override
    public @NotNull CompletableFuture<Boolean> isAvailable() {
        if (!isInitialized()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                    var stmt = conn.createStatement()) {
                // Ping test - funciona tanto para MySQL quanto SQLite
                stmt.execute("SELECT 1");
                return true;
            } catch (Exception e) {
                if (debug) {
                    logger.log(Level.WARNING, "[AfterCore] Database ping failed", e);
                }
                return false;
            }
        }, scheduler.ioExecutor());
    }

    @Override
    public @NotNull Map<String, Object> getPoolStats() {
        HikariDataSource ds = dataSource;
        if (ds == null || ds.isClosed()) {
            return Map.of();
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        try {
            var poolMXBean = ds.getHikariPoolMXBean();
            if (poolMXBean != null) {
                stats.put("active_connections", poolMXBean.getActiveConnections());
                stats.put("idle_connections", poolMXBean.getIdleConnections());
                stats.put("total_connections", poolMXBean.getTotalConnections());
                stats.put("threads_awaiting_connection", poolMXBean.getThreadsAwaitingConnection());
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "[AfterCore] Failed to get pool stats", e);
        }
        return stats;
    }

    @Override
    public void close() {
        HikariDataSource ds = dataSource;
        dataSource = null;
        enabled = false;

        if (ds == null)
            return;

        try {
            preloadShutdownClasses();
        } catch (Throwable ignored) {
        }

        try {
            logger.info("AfterCore-MySQL - Shutdown initiated...");
            ds.close();

            // Wait for background threads to complete before classloader is destroyed
            // HikariCP spawns connection closer threads that need time to finish
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            logger.info("AfterCore-MySQL - Shutdown completed.");
        } catch (Throwable t) {
            logger.warning("[AfterCore] Erro ao fechar pool: " + t.getMessage());
        }
    }

    /**
     * Workaround inspirado no shutdown do AfterBlockState: pré-carregar classes
     * do driver relocado que podem ser carregadas durante close, para evitar
     * ClassNotFoundException em unload do classloader.
     */
    private void preloadShutdownClasses() {
        String[] classesToLoad = {
                "com.afterlands.core.libs.mysql.cj.protocol.ExportControlled",
                "com.afterlands.core.libs.mysql.cj.protocol.NetworkResources",
                "com.afterlands.core.libs.mysql.cj.exceptions.ExceptionInterceptor",
                "com.afterlands.core.libs.mysql.cj.log.Log",
                "com.afterlands.core.libs.mysql.cj.protocol.a.NativeProtocol",
                "com.afterlands.core.libs.mysql.cj.NativeSession",
                "com.afterlands.core.libs.mysql.cj.jdbc.ConnectionImpl"
        };

        for (String className : classesToLoad) {
            try {
                Class.forName(className);
            } catch (ClassNotFoundException ignored) {
            }
        }
    }
}
