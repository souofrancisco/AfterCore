package com.afterlands.core.config.io;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Utilitário para gravação atômica de arquivos.
 *
 * <p>
 * Escreve em um arquivo temporário (.tmp) primeiro e depois move atomicamente
 * o arquivo temporário para o destino final. Isso garante que o arquivo de
 * destino
 * nunca fique em estado corrompido ou parcial.
 * </p>
 */
public final class AtomicConfigWriter {

    private AtomicConfigWriter() {
        // Utils class
    }

    /**
     * Escreve o conteúdo para o arquivo de forma atômica.
     *
     * @param target  Arquivo de destino
     * @param content Conteúdo a ser escrito
     * @throws IOException Se houver erro de I/O
     */
    public static void write(@NotNull File target, @NotNull String content) throws IOException {
        File tempFile = new File(target.getParentFile(), target.getName() + ".tmp");

        try {
            // 1. Escrever no arquivo temporário
            Files.writeString(tempFile.toPath(), content, StandardCharsets.UTF_8);

            // 2. Mover atomicamente (substituindo existente)
            Files.move(tempFile.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);

        } catch (IOException e) {
            // Se falhar o move atômico (ex: filesystem diferente), tentar move simples
            if (tempFile.exists()) {
                try {
                    Files.move(tempFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException moveEx) {
                    // Tentar limpar o temp se tudo falhar
                    tempFile.delete();
                    throw new IOException(
                            "Falha ao mover arquivo temporário para destino final: " + target.getAbsolutePath(),
                            moveEx);
                }
            } else {
                throw e;
            }
        }
    }
}
