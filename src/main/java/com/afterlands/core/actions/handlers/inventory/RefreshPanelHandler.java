package com.afterlands.core.actions.handlers.inventory;

import com.afterlands.core.actions.ActionHandler;
import com.afterlands.core.actions.ActionSpec;
import com.afterlands.core.inventory.view.InventoryViewHolder;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Handler para atualizar/recarregar o inventário atual do jogador.
 *
 * <p>Formato: {@code refresh}</p>
 *
 * <p>Este handler atualiza o inventário sem fechar e reabrir,
 * re-renderizando todos os itens com placeholders atualizados.</p>
 *
 * <p>Exemplos:</p>
 * <pre>
 * refresh
 * </pre>
 *
 * <p><b>Thread:</b> Sempre executa na main thread (garantido pelo ActionExecutor).</p>
 */
public final class RefreshPanelHandler implements ActionHandler {

    @Override
    public void execute(@NotNull Player target, @NotNull ActionSpec spec) {
        // Verifica se o jogador tem um InventoryViewHolder ativo
        InventoryViewHolder holder = InventoryViewHolder.get(target);

        if (holder != null) {
            // Refresh via AfterCore InventoryService
            holder.refresh();
        } else {
            // Fallback: fecha e reabre (se outro sistema de inventory)
            // Nesse caso, não fazemos nada pois não sabemos qual GUI reabrir
            // O plugin específico deve tratar isso via listener
        }
    }
}
