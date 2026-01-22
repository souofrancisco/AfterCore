package com.afterlands.core.database.impl;

import com.afterlands.core.concurrent.SchedulerService;
import com.afterlands.core.database.*;
import com.afterlands.core.database.impl.dialect.DatabaseDialect;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Datasource individual baseado em HikariCP.
 *
 * <p>
 * Representa um único pool de conexões para um database específico.
 * Múltiplas instâncias podem coexistir para diferentes bancos.
 * </p>
 *
 * @since 2.0.0
 */
public final class HikariSqlDataSource implements SqlDataSource {

    private final String name;
    private final Plugin plugin;
    private final Logger logger;
    private final SchedulerService scheduler;
    private final boolean debug;
    private final DatabaseType type;

    private volatile boolean enabled;
    private volatile HikariDataSource dataSource;

    /**
     * Cria um novo datasource HikariCP.
     *
     * @param name         nome identificador do datasource
     * @param plugin       plugin do core
     * @param scheduler    serviço de agendamento para operações async
     * @param dialect      dialeto do database (MySQL, SQLite)
     * @param dsConfig     configuração específica do datasource
     * @param poolDefaults configuração default do pool (pode ser null)
     * @param debug        modo debug
     */
    public HikariSqlDataSource(
            @NotNull String name,
            @NotNull Plugin plugin,
            @NotNull SchedulerService scheduler,
            @NotNull DatabaseDialect dialect,
            @NotNull ConfigurationSection dsConfig,
            @Nullable ConfigurationSection poolDefaults,
            boolean debug) {
        this.name = Objects.requireNonNull(name, "name");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = plugin.getLogger();
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.debug = debug;
        this.type = dialect.type();

        initialize(dialect, dsConfig, poolDefaults);
    }

    private void initialize(
            @NotNull DatabaseDialect dialect,
            @NotNull ConfigurationSection dsConfig,
            @Nullable ConfigurationSection poolDefaults) {
        try {
            HikariConfig hikari = new HikariConfig();

            // Aplica configurações do dialeto (jdbcUrl, driver, etc.)
            dialect.configure(plugin, dsConfig, hikari);

            // Pool settings: primeiro aplica defaults, depois overrides do datasource
            applyPoolConfig(hikari, poolDefaults, dsConfig.getConfigurationSection("pool"), dialect.type());

            // Nome do pool inclui nome do datasource para identificação
            hikari.setPoolName("AfterCore-" + name + "-" + dialect.type().name());

            dataSource = new HikariDataSource(hikari);
            enabled = true;

            logger.info("[AfterCore] Datasource '" + name + "' (" + dialect.type() + ") inicializado.");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "[AfterCore] Falha ao inicializar datasource '" + name + "'", e);
            enabled = false;
            dataSource = null;
            throw new RuntimeException("Falha ao inicializar datasource '" + name + "'", e);
        }
    }

    private void applyPoolConfig(
            @NotNull HikariConfig hikari,
            @Nullable ConfigurationSection defaults,
            @Nullable ConfigurationSection overrides,
            @NotNull DatabaseType type) {
        // Valores base para SQLite vs outros
        int defaultMaxPool = type == DatabaseType.SQLITE ? 1 : 10;
        int defaultMinIdle = type == DatabaseType.SQLITE ? 1 : 2;
        long defaultConnTimeout = 30000;
        long defaultIdleTimeout = 600000;
        long defaultMaxLifetime = 1800000;

        // Aplica defaults globais se existirem
        int maxPool = defaults != null ? defaults.getInt("maximum-pool-size", defaultMaxPool) : defaultMaxPool;
        int minIdle = defaults != null ? defaults.getInt("minimum-idle", defaultMinIdle) : defaultMinIdle;
        long connTimeout = defaults != null ? defaults.getLong("connection-timeout", defaultConnTimeout)
                : defaultConnTimeout;
        long idleTimeout = defaults != null ? defaults.getLong("idle-timeout", defaultIdleTimeout) : defaultIdleTimeout;
        long maxLifetime = defaults != null ? defaults.getLong("max-lifetime", defaultMaxLifetime) : defaultMaxLifetime;

        // Aplica overrides específicos do datasource
        if (overrides != null) {
            maxPool = overrides.getInt("maximum-pool-size", maxPool);
            minIdle = overrides.getInt("minimum-idle", minIdle);
            connTimeout = overrides.getLong("connection-timeout", connTimeout);
            idleTimeout = overrides.getLong("idle-timeout", idleTimeout);
            maxLifetime = overrides.getLong("max-lifetime", maxLifetime);
        }

        // SQLite força pool pequeno independente de config
        if (type == DatabaseType.SQLITE) {
            maxPool = 1;
            minIdle = 1;
        }

        hikari.setMaximumPoolSize(maxPool);
        hikari.setMinimumIdle(minIdle);
        hikari.setConnectionTimeout(connTimeout);
        hikari.setIdleTimeout(idleTimeout);
        hikari.setMaxLifetime(maxLifetime);

        if (debug) {
            logger.info("[AfterCore] Pool config for '" + name + "': maxPool=" + maxPool +
                    ", minIdle=" + minIdle + ", connTimeout=" + connTimeout);
        }
    }

    @Override
    public @NotNull String name() {
        return name;
    }

    @Override
    public @NotNull DatabaseType type() {
        return type;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean isInitialized() {
        return enabled && dataSource != null && !dataSource.isClosed();
    }

    @Override
    public @NotNull DataSource dataSource() {
        HikariDataSource ds = dataSource;
        if (ds == null || ds.isClosed()) {
            throw new IllegalStateException("Datasource '" + name + "' não inicializado.");
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
            cf.completeExceptionally(new IllegalStateException(
                    "Datasource '" + name + "' desabilitado/não inicializado."));
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
    public @NotNull <T> CompletableFuture<T> inTransaction(@NotNull SqlFunction<Connection, T> fn) {
        Objects.requireNonNull(fn, "fn");
        if (!isInitialized()) {
            CompletableFuture<T> cf = new CompletableFuture<>();
            cf.completeExceptionally(new IllegalStateException(
                    "Datasource '" + name + "' desabilitado/não inicializado."));
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
                        logger.log(Level.WARNING, "[AfterCore] Rollback failed for datasource '" + name + "'",
                                rollbackEx);
                    }
                    throw new RuntimeException(e);
                } finally {
                    try {
                        conn.setAutoCommit(originalAutoCommit);
                    } catch (SQLException ex) {
                        logger.log(Level.WARNING, "[AfterCore] Failed to restore auto-commit for '" + name + "'", ex);
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
                stmt.execute("SELECT 1");
                return true;
            } catch (Exception e) {
                if (debug) {
                    logger.log(Level.WARNING, "[AfterCore] Database ping failed for '" + name + "'", e);
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
        stats.put("datasource", name);
        stats.put("type", type.name());

        try {
            var poolMXBean = ds.getHikariPoolMXBean();
            if (poolMXBean != null) {
                stats.put("active_connections", poolMXBean.getActiveConnections());
                stats.put("idle_connections", poolMXBean.getIdleConnections());
                stats.put("total_connections", poolMXBean.getTotalConnections());
                stats.put("threads_awaiting_connection", poolMXBean.getThreadsAwaitingConnection());
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "[AfterCore] Failed to get pool stats for '" + name + "'", e);
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
            logger.info("[AfterCore] Datasource '" + name + "' - Shutdown initiated...");
            ds.close();

            // Wait for background threads to complete
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            logger.info("[AfterCore] Datasource '" + name + "' - Shutdown completed.");
        } catch (Throwable t) {
            logger.warning("[AfterCore] Erro ao fechar datasource '" + name + "': " + t.getMessage());
        }
    }

    /**
     * Pré-carrega classes do driver para evitar ClassNotFoundException no shutdown.
     */
    private void preloadShutdownClasses() {
        if (type != DatabaseType.MYSQL)
            return;

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
