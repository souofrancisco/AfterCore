package com.afterlands.core.config.update;

import com.afterlands.core.config.io.AtomicConfigWriter;
import com.afterlands.core.config.io.ConfigBackupManager;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Atualiza arquivos YAML preservando valores do usuário e comentários.
 *
 * <p>
 * Suporta dois modos:
 * <ul>
 * <li><b>Versionado (ex: config.yml):</b> Gerencia migrations e verifica
 * versão.</li>
 * <li><b>Genérico (ex: messages.yml):</b> Apenas adiciona chaves faltantes
 * (Smart Merge).</li>
 * </ul>
 * </p>
 */
public final class ConfigUpdater {

    private final Logger logger;
    private final File configFile;
    private final ConfigBackupManager backupManager;
    private final Map<Integer, ConfigMigration> migrations = new HashMap<>();

    public ConfigUpdater(@NotNull Logger logger, @NotNull File configFile) {
        this.logger = logger;
        this.configFile = configFile;
        this.backupManager = new ConfigBackupManager(logger);
    }

    /**
     * Registra uma migration para uma versão específica.
     */
    public void registerMigration(int version, ConfigMigration migration) {
        migrations.put(version, migration);
    }

    /**
     * Atualiza o arquivo se necessário.
     * Versão simplificada (sem options)
     */
    public boolean update(@NotNull FileConfiguration userConfig,
            @Nullable InputStream defaultStream,
            @NotNull FileConfiguration defaultConfig) {
        return update(userConfig, defaultStream, defaultConfig, null);
    }

    /**
     * Atualiza o arquivo com opções de personalização (registro de migrations).
     */
    public boolean update(@NotNull FileConfiguration userConfig,
            @Nullable InputStream defaultStream,
            @NotNull FileConfiguration defaultConfig,
            @Nullable java.util.function.Consumer<ConfigUpdater> options) {

        // Aplica configurações extras (ex: registrar migrations externas)
        if (options != null) {
            options.accept(this);
        }

        double targetVersion = defaultConfig.getDouble("config-version", 0.0);

        if (targetVersion > 0) {
            return updateVersioned(userConfig, defaultStream, defaultConfig, targetVersion);
        } else {
            return updateGeneric(userConfig, defaultStream, defaultConfig);
        }
    }

    private boolean updateVersioned(FileConfiguration userConfig, InputStream defaultStream,
            FileConfiguration defaultConfig, double targetVersion) {
        double currentVersion = userConfig.getDouble("config-version", 0.0);
        boolean diskChanged = false;

        // 1. Caso crítico: Sem versão -> Assumir nova versão
        if (currentVersion == 0.0) {
            logger.info("[Config] " + configFile.getName() + " sem versão. Definindo v" + targetVersion);
            backupManager.createBackup(configFile);
            updateVersionInFile(targetVersion);
            diskChanged = true;
            currentVersion = targetVersion;
        }

        // 2. Migrations
        if (currentVersion < targetVersion) {
            logger.info(
                    "[Config] Atualizando " + configFile.getName() + ": v" + currentVersion + " -> v" + targetVersion);
            backupManager.createBackup(configFile);

            // Migrations continuam sendo integer-based para compatibilidade
            int currentMajor = (int) Math.floor(currentVersion);
            int targetMajor = (int) Math.floor(targetVersion);

            for (int version = currentMajor + 1; version <= targetMajor; version++) {
                ConfigMigration migration = migrations.get(version);
                if (migration != null) {
                    logger.info("Aplicando migration v" + version);
                    migration.migrate(userConfig, defaultConfig);
                    // Migration pode modificar estrutura, precisa salvar
                    safeSave(userConfig);
                }
            }
            diskChanged = true;
        } else if (currentVersion > targetVersion) {
            // Nota: Removemos o forceMigration hardcoded daqui.
            // Para lógica customizada de legacy check, o ideal seria um callback específico
            // no futuro,
            // mas por enquanto, manter simples.
            logger.warning("[Config] Versão futura detectada em " + configFile.getName() + " (" + currentVersion + " > "
                    + targetVersion + ")");
            return false;
        }

        // 3. Smart Merge (ANTES de atualizar versão)
        if (performSmartMerge(userConfig, defaultStream, defaultConfig, diskChanged)) {
            diskChanged = true;
        }

        // 4. Atualizar versão via texto (ÚLTIMA operação para preservar comentários)
        if (currentVersion < targetVersion) {
            updateVersionInFile(targetVersion);
        }

        return diskChanged;
    }

    private boolean updateGeneric(FileConfiguration userConfig, InputStream defaultStream,
            FileConfiguration defaultConfig) {
        return performSmartMerge(userConfig, defaultStream, defaultConfig, false);
    }

    private boolean performSmartMerge(FileConfiguration userConfig, InputStream defaultStream,
            FileConfiguration defaultConfig, boolean forceBackup) {
        if (defaultStream == null)
            return false;

        try {
            List<String> userLines = configFile.exists()
                    ? Files.readAllLines(configFile.toPath())
                    : Collections.emptyList();

            // Detectar chaves faltantes (root + nested)
            List<String> missingRootKeys = getMissingRootKeys(userConfig, defaultConfig);
            List<String> missingNestedKeys = getMissingNestedKeys(userConfig, defaultConfig, "");

            if (!missingRootKeys.isEmpty()) {
                if (!forceBackup) {
                    backupManager.createBackup(configFile);
                }

                logger.info("[Config] Adicionando " + missingRootKeys.size() + " novas chaves raiz em "
                        + configFile.getName());
                String mergedContent = SmartConfigMerger.merge(userLines, defaultStream, missingRootKeys);

                AtomicConfigWriter.write(configFile, mergedContent);
                return true;
            } else if (!missingNestedKeys.isEmpty()) {
                // Chaves nested faltando - precisa usar API do Bukkit
                if (!forceBackup) {
                    backupManager.createBackup(configFile);
                }

                logger.info("[Config] Adicionando " + missingNestedKeys.size() + " chaves nested em "
                        + configFile.getName());
                for (String nestedKey : missingNestedKeys) {
                    userConfig.set(nestedKey, defaultConfig.get(nestedKey));
                }
                safeSave(userConfig);
                return true;
            }
        } catch (IOException e) {
            logger.severe("[Config] Falha no Smart Merge de " + configFile.getName() + ": " + e.getMessage());
        }
        return false;
    }

    private List<String> getMissingRootKeys(FileConfiguration userConfig, FileConfiguration defaultConfig) {
        return defaultConfig.getKeys(false).stream()
                .filter(key -> !userConfig.contains(key))
                .collect(Collectors.toList());
    }

    private List<String> getMissingNestedKeys(FileConfiguration userConfig, FileConfiguration defaultConfig,
            String prefix) {
        List<String> missing = new ArrayList<>();
        for (String key : defaultConfig.getKeys(false)) {
            String fullPath = prefix.isEmpty() ? key : prefix + "." + key;
            if (userConfig.contains(key)) {
                // Key existe, verificar sub-keys se for seção
                Object defaultVal = defaultConfig.get(key);
                Object userVal = userConfig.get(key);
                if (defaultVal instanceof ConfigurationSection && userVal instanceof ConfigurationSection) {
                    missing.addAll(getMissingNestedKeysInSection(
                            (ConfigurationSection) userVal,
                            (ConfigurationSection) defaultVal,
                            fullPath));
                }
            }
        }
        return missing;
    }

    private List<String> getMissingNestedKeysInSection(ConfigurationSection userSection,
            ConfigurationSection defaultSection, String prefix) {
        List<String> missing = new ArrayList<>();
        for (String key : defaultSection.getKeys(false)) {
            String fullPath = prefix + "." + key;
            if (!userSection.contains(key)) {
                missing.add(fullPath);
            } else {
                Object defaultVal = defaultSection.get(key);
                Object userVal = userSection.get(key);
                if (defaultVal instanceof ConfigurationSection && userVal instanceof ConfigurationSection) {
                    missing.addAll(getMissingNestedKeysInSection(
                            (ConfigurationSection) userVal,
                            (ConfigurationSection) defaultVal,
                            fullPath));
                }
            }
        }
        return missing;
    }

    private void updateVersionInFile(double newVersion) {
        try {
            List<String> lines = Files.readAllLines(configFile.toPath());
            boolean found = false;
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.trim().startsWith("config-version:")) {
                    // Preservar indentação e comentário na mesma linha se houver
                    int colonIndex = line.indexOf(':');
                    String prefix = line.substring(0, colonIndex + 1);
                    lines.set(i, prefix + " " + newVersion);
                    found = true;
                    break;
                }
            }
            if (found) {
                AtomicConfigWriter.write(configFile, String.join(System.lineSeparator(), lines));
            }
        } catch (IOException e) {
            logger.warning("[Config] Falha ao atualizar versão em " + configFile.getName() + ": " + e.getMessage());
        }
    }

    private void safeSave(FileConfiguration config) {
        try {
            String data = config.saveToString();
            AtomicConfigWriter.write(configFile, data);
        } catch (IOException e) {
            logger.severe("[Config] Falha crítica ao salvar " + configFile.getName() + ": " + e.getMessage());
        }
    }

    @FunctionalInterface
    public interface ConfigMigration {
        void migrate(@NotNull ConfigurationSection userConfig, @NotNull ConfigurationSection defaultConfig);
    }

    // Helpers estáticos para Migrations
    public static void renameKey(@NotNull ConfigurationSection config, @NotNull String oldKey, @NotNull String newKey) {
        if (config.contains(oldKey) && !config.contains(newKey)) {
            config.set(newKey, config.get(oldKey));
            config.set(oldKey, null);
        }
    }

    public static void moveKey(@NotNull ConfigurationSection config, @NotNull String oldPath, @NotNull String newPath) {
        if (config.contains(oldPath) && !config.contains(newPath)) {
            config.set(newPath, config.get(oldPath));
            config.set(oldPath, null);
        }
    }
}
