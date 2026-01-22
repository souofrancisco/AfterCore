package com.afterlands.core.config.io;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.logging.Logger;

/**
 * Gerencia backups automáticos de arquivos de configuração.
 * Cria cópias com timestamp e rotaciona (limite de 5 backups).
 */
public class ConfigBackupManager {

    private final Logger logger;
    private static final int MAX_BACKUPS = 5;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");

    public ConfigBackupManager(Logger logger) {
        this.logger = logger;
    }

    /**
     * Cria uma cópia de backup do arquivo especificado.
     * Exemplo: config.yml -> config.yml.2023-10-27_10-00-00.bak
     */
    public void createBackup(File targetFile) {
        if (!targetFile.exists())
            return;

        String timestamp = DATE_FORMAT.format(new Date());
        File backupFile = new File(targetFile.getParent(), targetFile.getName() + "." + timestamp + ".bak");

        try {
            Files.copy(targetFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            logger.info("[Backup] Criado: " + backupFile.getName());

            cleanOldBackups(targetFile);

        } catch (IOException e) {
            logger.warning("[Backup] Falha ao criar backup de " + targetFile.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Remove backups antigos se exceder MAX_BACKUPS.
     */
    private void cleanOldBackups(File targetFile) {
        File dir = targetFile.getParentFile();
        if (dir == null)
            return;

        // Lista arquivos que começam com o nome do arquivo alvo e terminam com .bak
        File[] backups = dir
                .listFiles((d, name) -> name.startsWith(targetFile.getName() + ".") && name.endsWith(".bak"));

        if (backups != null && backups.length > MAX_BACKUPS) {
            // Ordena por data de modificação (mais antigos primeiro)
            Arrays.sort(backups, Comparator.comparingLong(File::lastModified));

            int toDelete = backups.length - MAX_BACKUPS;
            for (int i = 0; i < toDelete; i++) {
                if (backups[i].delete()) {
                    logger.info("[Backup] Rotacionado/Removido antigo: " + backups[i].getName());
                }
            }
        }
    }
}
