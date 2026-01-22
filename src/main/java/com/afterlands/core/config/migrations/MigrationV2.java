package com.afterlands.core.config.migrations;

import com.afterlands.core.config.update.ConfigUpdater;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

public class MigrationV2 implements ConfigUpdater.ConfigMigration {

    @Override
    public void migrate(@NotNull ConfigurationSection userConfig, @NotNull ConfigurationSection defaultConfig) {
        // Detectar se banco de dados está no formato antigo (v1)
        if (userConfig.contains("database.type") && !userConfig.contains("database.datasources")) {

            // Mover configurações antigas para o datasource 'default'
            String type = userConfig.getString("database.type");
            ConfigurationSection defaultDs = userConfig.createSection("database.datasources.default");

            // Copiar tipo
            defaultDs.set("type", type);

            // Copiar seção específica (mysql ou sqlite)
            if (userConfig.isConfigurationSection("database.mysql")) {
                ConfigUpdater.moveKey(userConfig, "database.mysql", "database.datasources.default.mysql");
            }
            if (userConfig.isConfigurationSection("database.sqlite")) {
                ConfigUpdater.moveKey(userConfig, "database.sqlite", "database.datasources.default.sqlite");
            }

            // Excluir chaves antigas da raiz 'database' que já foram movidas ou são
            // obsoletas
            userConfig.set("database.type", null);

            // Pool (se existir override na raiz, movemos para o default?
            // No v1, pool era global. No v2, 'database.pool' é global (mantemos)
            // e 'database.datasources.default.pool' é override.
            // Então não precisamos mover 'database.pool' pois ele continua válido como
            // padrão global.
        }
    }
}
