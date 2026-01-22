package com.afterlands.core.actions.handlers;

import com.afterlands.core.actions.ActionHandler;
import com.afterlands.core.actions.ActionSpec;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Handler marcador para a action 'wait'.
 *
 * <p>
 * A lógica de espera é tratada diretamente pelo
 * {@link com.afterlands.core.actions.ActionExecutor}
 * para permitir execução assíncrona e sequencial.
 * </p>
 *
 * <p>
 * Este handler existe apenas para que a action seja reconhecida como válida
 * pelo ActionService.
 * </p>
 */
public final class WaitHandler implements ActionHandler {

    @Override
    public void execute(@NotNull Player target, @NotNull ActionSpec spec) {
        // No-op: Logic handled by ActionExecutor
    }
}
