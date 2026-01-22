package com.afterlands.core.config.update;

import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Utilitário para mesclar configurações preservando comentários.
 *
 * <p>
 * Funciona manipulando o arquivo como texto (List<String>) em vez de objetos
 * YAML,
 * permitindo copiar blocos inteiros (chaves + valores + comentários) do config
 * padrão
 * para o config do usuário.
 * </p>
 */
public final class SmartConfigMerger {

    // Regex simplificado para detectar chaves de nível superior (sem indentação)
    private static final Pattern ROOT_KEY_PATTERN = Pattern.compile("^([a-z0-9_-]+):", Pattern.CASE_INSENSITIVE);

    private SmartConfigMerger() {
    }

    /**
     * Mescla o conteúdo padrão no conteúdo do usuário, adicionando chaves
     * faltantes.
     *
     * @param userLines       Linhas do arquivo atual do usuário
     * @param defaultStream   Stream do arquivo padrão (resource do JAR)
     * @param missingRootKeys Lista de chaves raiz que faltam no arquivo do usuário
     * @return Conteúdo mesclado como String única
     * @throws IOException Se erro de leitura
     */
    public static String merge(@NotNull List<String> userLines,
            @NotNull InputStream defaultStream,
            @NotNull List<String> missingRootKeys) throws IOException {

        if (missingRootKeys.isEmpty()) {
            return String.join(System.lineSeparator(), userLines);
        }

        List<String> defaultLines = readLines(defaultStream);
        List<String> mergedLines = new ArrayList<>(userLines);

        // Garantir que termina com nova linha antes de adicionar novos blocos
        if (!mergedLines.isEmpty() && !mergedLines.get(mergedLines.size() - 1).isEmpty()) {
            mergedLines.add("");
        }

        for (String missingKey : missingRootKeys) {
            List<String> block = extractBlock(defaultLines, missingKey);
            if (!block.isEmpty()) {
                mergedLines.add("");
                mergedLines.addAll(block);
            }
        }

        return String.join(System.lineSeparator(), mergedLines);
    }

    private static List<String> readLines(InputStream stream) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    /**
     * Extrai um bloco de configuração (comentários + chave + valor) para uma dada
     * chave raiz.
     *
     * <p>
     * Estratégia:
     * 1. Encontrar a linha onde a chave é definida (ex: "database:").
     * 2. Capturar comentários imediatamente acima dessa linha.
     * 3. Capturar o conteúdo da chave (até encontrar outra chave raiz ou fim do
     * arquivo).
     * </p>
     */
    private static List<String> extractBlock(List<String> lines, String key) {
        int keyLineIndex = -1;
        String keyMarker = key + ":";

        // 1. Encontrar a linha da chave
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.startsWith(keyMarker) || (line.startsWith(key) && line.trim().startsWith(keyMarker))) {
                keyLineIndex = i;
                break;
            }
        }

        if (keyLineIndex == -1) {
            return Collections.emptyList();
        }

        int startBlockIndex = keyLineIndex;

        // 2. Retroceder para capturar comentários vinculados (cima)
        // Pára se encontrar linha em branco ou outra chave raiz
        for (int i = keyLineIndex - 1; i >= 0; i--) {
            String line = lines.get(i).trim();
            if (line.startsWith("#")) {
                startBlockIndex = i;
            } else if (line.isEmpty()) {
                // Se a linha em branco separa este bloco de comentários do anterior, vamos
                // incluir a linha em branco?
                // Melhor: se encontrou linha em branco, o bloco começa DEPOIS dela.
                // Mas queremos incluir comentários de cabeçalho da seção.
                // Simplificação: pegar linhas de comentário contínuas.
                startBlockIndex = i; // Inclui a linha vazia como separador
            } else {
                // Encontrou conteúdo não-comentário (outra config), paramos
                break;
            }
        }

        // 3. Avançar para capturar o corpo do bloco
        int endBlockIndex = lines.size();
        for (int i = keyLineIndex + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            // Se encontrar uma nova chave raiz (sem indentação), acabou o bloco
            // Exceção: chaves dentro de listas ou objetos (têm indentação)
            if (ROOT_KEY_PATTERN.matcher(line).find()) {
                endBlockIndex = i;
                break;
            }
        }

        // Extrair sublista
        // Ajuste fino: Se startBlockIndex apontou para linha vazia, ok.
        return new ArrayList<>(lines.subList(startBlockIndex, endBlockIndex));
    }
}
