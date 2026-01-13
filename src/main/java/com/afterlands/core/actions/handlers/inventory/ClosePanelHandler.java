package com.afterlands.core.actions.handlers.inventory;

import com.afterlands.core.actions.ActionHandler;
import com.afterlands.core.actions.ActionSpec;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Handler para fechar o inventário do jogador.
 *
 * <p>Formato: {@code close-panel} ou {@code close}</p>
 *
 * <p>Exemplos:</p>
 * <pre>
 * close-panel
 * close
 * </pre>
 *
 * <p><b>Thread:</b> Sempre executa na main thread (garantido pelo ActionExecutor).</p>
 */
public final class ClosePanelHandler implements ActionHandler {

    @Override
    public void execute(@NotNull Player target, @NotNull ActionSpec spec) {
        // Fecha o inventário do jogador
        target.closeInventory();
    }
}
