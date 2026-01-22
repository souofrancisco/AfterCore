package com.afterlands.core.database;

import org.jetbrains.annotations.NotNull;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Representa um datasource individual dentro do SqlService.
 *
 * <p>
 * Cada datasource tem seu próprio pool HikariCP, migrations,
 * e configurações isoladas. Múltiplos datasources permitem separar
 * dados de gameplay de dados de analytics/auditoria.
 * </p>
 *
 * <p>
 * <b>Exemplo de uso:</b>
 * </p>
 * 
 * <pre>{@code
 * // Obter datasource específico
 * SqlDataSource analytics = core.sql().datasource("analytics");
 * analytics.runAsync(conn -> {
 *     // Operações no banco de analytics
 * });
 * }</pre>
 *
 * @since 2.0.0
 * @see SqlService#datasource(String)
 */
public interface SqlDataSource {

    /**
     * Nome deste datasource (ex: "default", "analytics").
     *
     * @return nome identificador do datasource
     */
    @NotNull
    String name();

    /**
     * Tipo do database (MYSQL, SQLITE).
     *
     * @return tipo do database
     */
    @NotNull
    DatabaseType type();

    /**
     * Verifica se este datasource está habilitado na configuração.
     *
     * @return true se habilitado
     */
    boolean isEnabled();

    /**
     * Verifica se este datasource está inicializado e pronto para uso.
     *
     * @return true se inicializado e com pool ativo
     */
    boolean isInitialized();

    /**
     * Obtém o DataSource JDBC subjacente.
     *
     * @return DataSource para acesso direto (usar com cautela)
     * @throws IllegalStateException se não inicializado
     */
    @NotNull
    DataSource dataSource();

    /**
     * Obtém uma conexão do pool.
     *
     * <p>
     * <b>ATENÇÃO:</b> Prefira usar {@link #runAsync} ou {@link #supplyAsync}
     * para garantir execução fora da main thread.
     * </p>
     *
     * @return conexão do pool (deve ser fechada após uso)
     * @throws SQLException se erro ao obter conexão
     */
    @NotNull
    Connection getConnection() throws SQLException;

    /**
     * Executa operação SQL assíncrona que retorna resultado.
     *
     * @param fn  função que recebe conexão e retorna resultado
     * @param <T> tipo do resultado
     * @return CompletableFuture com resultado
     */
    @NotNull
    <T> CompletableFuture<T> supplyAsync(@NotNull SqlFunction<Connection, T> fn);

    /**
     * Executa operação SQL assíncrona sem retorno.
     *
     * @param fn consumer que recebe conexão
     * @return CompletableFuture que completa quando operação termina
     */
    @NotNull
    CompletableFuture<Void> runAsync(@NotNull SqlConsumer<Connection> fn);

    /**
     * Executa operação dentro de uma transação.
     *
     * <p>
     * Auto-commit desabilitado, commit automático em sucesso,
     * rollback automático em exceção.
     * </p>
     *
     * @param fn  função que recebe conexão e retorna resultado
     * @param <T> tipo do resultado
     * @return CompletableFuture com resultado
     */
    @NotNull
    <T> CompletableFuture<T> inTransaction(@NotNull SqlFunction<Connection, T> fn);

    /**
     * Verifica se o database está disponível (ping test).
     *
     * @return CompletableFuture com true se disponível
     */
    @NotNull
    CompletableFuture<Boolean> isAvailable();

    /**
     * Obtém estatísticas do pool de conexões.
     *
     * @return Map com estatísticas (active_connections, idle_connections, etc.)
     */
    @NotNull
    Map<String, Object> getPoolStats();

    /**
     * Fecha este datasource e libera recursos.
     */
    void close();
}
