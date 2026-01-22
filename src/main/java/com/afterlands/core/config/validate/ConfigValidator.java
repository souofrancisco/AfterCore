package com.afterlands.core.config.validate;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Valida a configuração do AfterCore, retornando lista de erros e avisos.
 *
 * <p>
 * Design: fail-fast em parâmetros críticos, tolerante em opcionais.
 * </p>
 */
public final class ConfigValidator {

    private final List<ValidationError> errors = new ArrayList<>();

    public ConfigValidator() {
    }

    /**
     * Valida toda a configuração do AfterCore.
     *
     * @param config ConfigurationSection raiz do config.yml
     * @return ValidationResult com erros e avisos
     */
    @NotNull
    public ValidationResult validate(@Nullable ConfigurationSection config) {
        errors.clear();

        if (config == null) {
            errors.add(ValidationError.error("root", "Configuração nula ou não carregada"));
            return ValidationResult.of(errors);
        }

        // Validar versão do config
        validateConfigVersion(config);

        // Validar seção de database
        validateDatabase(config.getConfigurationSection("database"));

        // Validar seção de concurrency
        validateConcurrency(config.getConfigurationSection("concurrency"));

        return ValidationResult.of(errors);
    }

    private void validateConfigVersion(@NotNull ConfigurationSection config) {
        if (!config.contains("config-version")) {
            errors.add(ValidationError.warning("config-version",
                    "Versão do config não especificada. Considere adicionar 'config-version: 1'"));
            return;
        }

        int version = config.getInt("config-version", -1);
        if (version < 1) {
            errors.add(ValidationError.error("config-version",
                    "Versão do config inválida", "número >= 1", String.valueOf(version)));
        }
    }

    private void validateDatabase(@Nullable ConfigurationSection db) {
        String basePath = "database";

        if (db == null) {
            errors.add(ValidationError.warning(basePath, "Seção 'database' não encontrada"));
            return;
        }

        // Check for external management
        if (db.getBoolean("managed", false)) {
            return; // Managed externally (e.g., by AfterCore context), skip validation
        }

        // Validar habilitação
        boolean enabled = db.getBoolean("enabled", false);
        if (!enabled) {
            // Não é erro, apenas aviso
            errors.add(ValidationError.warning(basePath + ".enabled",
                    "Database desabilitado. Funcionalidades que dependem de DB não funcionarão"));
            return; // Não validar o resto se desabilitado
        }

        // NEW V2 VALIDATION: Check for datasources
        ConfigurationSection datasources = db.getConfigurationSection("datasources");
        if (datasources == null) {
            // Fallback/Legacy check (para não quebrar startups antigos se a migration
            // falhar)
            // mas como estamos na V2, se não tiver datasources, é erro se não tiver type
            // antigo também.
            // Porém o erro reportado foi na falta de type. Assumimos estrutura V2.
            // Se não tem datasources, é erro crítico na V2.
            errors.add(ValidationError.error(basePath + ".datasources", "Seção 'datasources' não encontrada"));
            return;
        }

        // Validate 'default' datasource
        if (!datasources.contains("default")) {
            errors.add(ValidationError.error(basePath + ".datasources.default",
                    "Datasource 'default' obrigatório não encontrado"));
        }

        // Validate all defined datasources
        for (String dsName : datasources.getKeys(false)) {
            validateDatasource(datasources.getConfigurationSection(dsName), basePath + ".datasources." + dsName);
        }

        // Validar pool
        validatePoolConfig(db.getConfigurationSection("pool"));
    }

    private void validateDatasource(ConfigurationSection ds, String basePath) {
        if (ds == null)
            return;

        // Validar tipo
        String type = ds.getString("type", "");
        if (type.isEmpty()) {
            errors.add(ValidationError.error(basePath + ".type",
                    "Tipo de database não especificado", "mysql ou sqlite", "vazio"));
            return;
        }

        Set<String> validTypes = Set.of("mysql", "sqlite");
        if (!validTypes.contains(type.toLowerCase())) {
            errors.add(ValidationError.error(basePath + ".type",
                    "Tipo de database inválido", "mysql ou sqlite", type));
            return;
        }

        // Validar configuração específica do tipo
        switch (type.toLowerCase()) {
            case "mysql" -> validateMySqlConfig(ds.getConfigurationSection("mysql"), basePath + ".mysql");
            case "sqlite" -> validateSqliteConfig(ds.getConfigurationSection("sqlite"), basePath + ".sqlite");
        }
    }

    private void validateMySqlConfig(@Nullable ConfigurationSection mysql, String basePath) {
        if (mysql == null) {
            errors.add(ValidationError.error(basePath, "Configuração MySQL não encontrada"));
            return;
        }

        // Validar campos obrigatórios
        validateRequiredString(mysql, basePath, "host");
        validateRequiredInt(mysql, basePath, "port", 1, 65535);
        validateRequiredString(mysql, basePath, "database");
        validateRequiredString(mysql, basePath, "username");

        // Password pode ser vazia (root sem senha em dev), apenas avisar
        if (!mysql.contains("password") || mysql.getString("password", "").isEmpty()) {
            errors.add(ValidationError.warning(basePath + ".password",
                    "Senha vazia. Certifique-se que isto é intencional (apenas dev/local)"));
        }
    }

    private void validateSqliteConfig(@Nullable ConfigurationSection sqlite, String basePath) {
        if (sqlite == null) {
            errors.add(ValidationError.error(basePath, "Configuração SQLite não encontrada"));
            return;
        }

        // Validar arquivo
        validateRequiredString(sqlite, basePath, "file");
    }

    private void validatePoolConfig(@Nullable ConfigurationSection pool) {
        String basePath = "database.pool";

        if (pool == null) {
            errors.add(ValidationError.warning(basePath,
                    "Configuração de pool não encontrada. Usando defaults do HikariCP"));
            return;
        }

        // Validar tamanhos de pool
        int maxPoolSize = pool.getInt("maximum-pool-size", 10);
        int minIdle = pool.getInt("minimum-idle", 2);

        if (maxPoolSize < 1) {
            errors.add(ValidationError.error(basePath + ".maximum-pool-size",
                    "Tamanho máximo do pool inválido", ">= 1", String.valueOf(maxPoolSize)));
        }

        if (minIdle < 0) {
            errors.add(ValidationError.error(basePath + ".minimum-idle",
                    "Minimum idle inválido", ">= 0", String.valueOf(minIdle)));
        }

        if (minIdle > maxPoolSize) {
            errors.add(ValidationError.error(basePath + ".minimum-idle",
                    "Minimum idle maior que maximum-pool-size",
                    "<= " + maxPoolSize, String.valueOf(minIdle)));
        }

        // Validar timeouts
        validatePositiveInt(pool, basePath, "connection-timeout");
        validatePositiveInt(pool, basePath, "idle-timeout");
        validatePositiveInt(pool, basePath, "max-lifetime");

        // Avisar se valores são muito baixos ou altos
        int connTimeout = pool.getInt("connection-timeout", 30000);
        if (connTimeout < 5000) {
            errors.add(ValidationError.warning(basePath + ".connection-timeout",
                    "Timeout muito baixo, pode causar falhas em rede lenta"));
        }
    }

    private void validateConcurrency(@Nullable ConfigurationSection concurrency) {
        String basePath = "concurrency";

        if (concurrency == null) {
            errors.add(ValidationError.warning(basePath,
                    "Seção 'concurrency' não encontrada. Usando defaults"));
            return;
        }

        // Validar threads
        int ioThreads = concurrency.getInt("io-threads", 8);
        int cpuThreads = concurrency.getInt("cpu-threads", 4);

        if (ioThreads < 1) {
            errors.add(ValidationError.error(basePath + ".io-threads",
                    "Número de threads I/O inválido", ">= 1", String.valueOf(ioThreads)));
        }

        if (cpuThreads < 1) {
            errors.add(ValidationError.error(basePath + ".cpu-threads",
                    "Número de threads CPU inválido", ">= 1", String.valueOf(cpuThreads)));
        }

        // Avisar se valores são muito altos (possível desperdício)
        int cores = Runtime.getRuntime().availableProcessors();
        if (ioThreads > cores * 4) {
            errors.add(ValidationError.warning(basePath + ".io-threads",
                    "Número alto de threads I/O (" + ioThreads + "). " +
                            "Sistema tem " + cores + " cores. Considere reduzir para evitar overhead"));
        }

        if (cpuThreads > cores * 2) {
            errors.add(ValidationError.warning(basePath + ".cpu-threads",
                    "Número alto de threads CPU (" + cpuThreads + "). " +
                            "Sistema tem " + cores + " cores. Considere ajustar"));
        }
    }

    // Helpers de validação

    private void validateRequiredString(@NotNull ConfigurationSection section,
            @NotNull String basePath,
            @NotNull String key) {
        String fullPath = basePath + "." + key;
        if (!section.contains(key)) {
            errors.add(ValidationError.error(fullPath, "Campo obrigatório ausente"));
            return;
        }

        String value = section.getString(key, "");
        if (value.trim().isEmpty()) {
            errors.add(ValidationError.error(fullPath, "Campo obrigatório vazio"));
        }
    }

    private void validateRequiredInt(@NotNull ConfigurationSection section,
            @NotNull String basePath,
            @NotNull String key,
            int min, int max) {
        String fullPath = basePath + "." + key;
        if (!section.contains(key)) {
            errors.add(ValidationError.error(fullPath, "Campo obrigatório ausente"));
            return;
        }

        int value = section.getInt(key, -1);
        if (value < min || value > max) {
            errors.add(ValidationError.error(fullPath,
                    "Valor fora do intervalo permitido",
                    min + " a " + max,
                    String.valueOf(value)));
        }
    }

    private void validatePositiveInt(@NotNull ConfigurationSection section,
            @NotNull String basePath,
            @NotNull String key) {
        String fullPath = basePath + "." + key;
        if (!section.contains(key)) {
            return; // Opcional
        }

        int value = section.getInt(key, 1);
        if (value <= 0) {
            errors.add(ValidationError.error(fullPath,
                    "Valor deve ser positivo", "> 0", String.valueOf(value)));
        }
    }
}
