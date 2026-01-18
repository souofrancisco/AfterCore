package com.afterlands.core.inventory.action;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Representa uma ação configurada com condições, sucesso e falha.
 *
 * <p>
 * Substitui a antiga lista simples de strings para actions.
 * Se apenas actions forem fornecidas (legado), elas são mapeadas para success.
 * </p>
 *
 * @param conditions Lista de condições (placeholders ou permissões)
 * @param success    Ações a executar se condições forem atendidas
 * @param fail       Ações a executar se condições falharem
 */
public record ConfiguredAction(
        @NotNull List<String> conditions,
        @NotNull List<String> success,
        @NotNull List<String> fail) {

    /**
     * Cria uma action simples sem condições (legado).
     *
     * @param actions Lista de actions
     * @return ConfiguredAction apenas com success
     */
    public static ConfiguredAction simple(@NotNull List<String> actions) {
        return new ConfiguredAction(List.of(), actions, List.of());
    }

    /**
     * Verifica se tem condições.
     *
     * @return true se tiver condições
     */
    public boolean hasConditions() {
        return !conditions.isEmpty();
    }
}
