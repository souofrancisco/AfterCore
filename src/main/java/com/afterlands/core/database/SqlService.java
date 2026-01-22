package com.afterlands.core.database;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Serviço de SQL com suporte a múltiplos datasources.
 *
 * <p>
 * <b>Multi-Datasource (v2.0+):</b> O SqlService agora suporta múltiplos
 * datasources isolados, cada um com seu próprio pool HikariCP.
 * </p>
 *
 * <p>
 * <b>Retrocompatibilidade:</b> Todos os métodos existentes continuam
 * funcionando e operam no datasource "default".
 * </p>
 *
 * <p>
 * <b>Exemplo:</b>
 * </p>
 * 
 * <pre>{@code
 * // Datasource default (retrocompatível)
 * core.sql().runAsync(conn -> { ... });
 *
 * // Datasource específico
 * core.sql().datasource("analytics").runAsync(conn -> { ... });
 * }</pre>
 *
 * @since 1.0.0
 */
public interface SqlService extends AutoCloseable {

    /**
     * Nome do datasource default.
     */
    String DEFAULT_DATASOURCE = "default";

    /**
     * Obtém um datasource específico por nome.
     *
     * @param name nome do datasource (ex: "default", "analytics", "rankup")
     * @return SqlDataSource para o nome especificado
     * @throws IllegalStateException se o datasource não existir
     * @since 1.4.0
     */
    @NotNull
    SqlDataSource datasource(@NotNull String name);

    /**
     * Verifica se um datasource existe e está registrado.
     *
     * @param name nome do datasource
     * @return true se o datasource existe
     * @since 1.4.0
     */
    boolean hasDatasource(@NotNull String name);

    /**
     * Lista todos os datasources registrados.
     *
     * @return Set com nomes dos datasources
     * @since 1.4.0
     */
    @NotNull
    Set<String> getDatasourceNames();

    /**
     * Registra uma migration em um datasource específico.
     *
     * <p>
     * A migration será executada quando o datasource for inicializado.
     * Deve ser idempotente (CREATE TABLE IF NOT EXISTS, etc.).
     * </p>
     *
     * @param datasourceName nome do datasource alvo
     * @param id             identificador único da migration (ex:
     *                       "myplugin:001_create_tables")
     * @param migration      migration a executar
     * @since 1.4.0
     */
    void registerMigration(@NotNull String datasourceName, @NotNull String id, @NotNull SqlMigration migration);

    /**
     * Obtém estatísticas agregadas de todos os datasources.
     *
     * @return Map com estatísticas por datasource
     * @since 1.4.0
     */
    @NotNull
    Map<String, Map<String, Object>> getAllPoolStats();

    /**
     * Obtém o datasource configurado para um plugin específico.
     *
     * <p>
     * Lê a chave {@code database.datasource} do config.yml do plugin.
     * Se não encontrada, usa o datasource "default".
     * </p>
     *
     * <p>
     * <b>Exemplo no config.yml do plugin:</b>
     * </p>
     * 
     * <pre>{@code
     * database:
     *   datasource: "analytics"  # Nome do datasource definido no AfterCore
     * }</pre>
     *
     * @param plugin plugin que quer seu datasource
     * @return SqlDataSource configurado para o plugin
     * @throws IllegalStateException se o datasource configurado não existir
     * @since 1.4.1
     */
    @NotNull
    SqlDataSource forPlugin(@NotNull org.bukkit.plugin.Plugin plugin);

    // ==================== Default Datasource API (Retrocompatível)
    // ====================

    /**
     * Recarrega todos os datasources a partir do config.
     *
     * @param section "database" do config.yml (pode ser null -> desabilita)
     */
    void reloadFromConfig(@Nullable ConfigurationSection section);

    /**
     * Verifica se o datasource default está habilitado.
     *
     * @return true se habilitado
     */
    boolean isEnabled();

    /**
     * Verifica se o datasource default está inicializado.
     *
     * @return true se inicializado
     */
    boolean isInitialized();

    /**
     * Obtém o DataSource JDBC do datasource default.
     *
     * @return DataSource
     * @throws IllegalStateException se não inicializado
     */
    @NotNull
    DataSource dataSource();

    /**
     * Obtém conexão do datasource default.
     *
     * @return conexão JDBC
     * @throws SQLException se erro
     */
    @NotNull
    Connection getConnection() throws SQLException;

    /**
     * Executa operação assíncrona no datasource default.
     *
     * @param fn  função SQL
     * @param <T> tipo do resultado
     * @return CompletableFuture
     */
    @NotNull
    <T> CompletableFuture<T> supplyAsync(@NotNull SqlFunction<Connection, T> fn);

    /**
     * Executa operação assíncrona sem retorno no datasource default.
     *
     * @param fn consumer SQL
     * @return CompletableFuture
     */
    @NotNull
    CompletableFuture<Void> runAsync(@NotNull SqlConsumer<Connection> fn);

    /**
     * Registra uma migration no datasource default.
     *
     * <p>
     * Equivalente a {@code registerMigration("default", id, migration)}.
     * </p>
     *
     * @param id        identificador da migration
     * @param migration migration a executar
     */
    void registerMigration(@NotNull String id, @NotNull SqlMigration migration);

    /**
     * Executa operação em transação no datasource default.
     *
     * @param fn  função SQL
     * @param <T> tipo do resultado
     * @return CompletableFuture
     */
    @NotNull
    <T> CompletableFuture<T> inTransaction(@NotNull SqlFunction<Connection, T> fn);

    /**
     * Verifica disponibilidade do datasource default.
     *
     * @return CompletableFuture com resultado do ping
     */
    @NotNull
    CompletableFuture<Boolean> isAvailable();

    /**
     * Obtém estatísticas do pool do datasource default.
     *
     * @return Map com estatísticas
     */
    @NotNull
    Map<String, Object> getPoolStats();

    @Override
    void close();
}
