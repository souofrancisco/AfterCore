package com.afterlands.core.actions.handlers.inventory;

import com.afterlands.core.actions.ActionHandler;
import com.afterlands.core.actions.ActionSpec;
import com.afterlands.core.inventory.event.OpenPanelRequestEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Handler para abrir painéis/GUIs de outros plugins.
 *
 * <p>Formato: {@code open-panel: <panel_key> [placeholder=value ...]}</p>
 *
 * <p>Exemplos:</p>
 * <pre>
 * open-panel: main-menu
 * open-panel: animation-list
 * open-panel: editor animationId=test
 * open-panel: placement-editor placementId=spawn_portal
 * </pre>
 *
 * <p>Este handler dispara um {@link OpenPanelRequestEvent} que outros plugins
 * podem escutar para abrir suas GUIs customizadas.</p>
 *
 * <p><b>Thread:</b> Sempre executa na main thread (garantido pelo ActionExecutor).</p>
 */
public final class OpenPanelHandler implements ActionHandler {

    @Override
    public void execute(@NotNull Player target, @NotNull ActionSpec spec) {
        String args = spec.rawArgs();
        if (args == null || args.isEmpty()) {
            return;
        }

        // Parse: panel_key [placeholder=value ...]
        String[] parts = args.split("\\s+");
        if (parts.length == 0) {
            return;
        }

        String panelKey = parts[0].trim();
        Map<String, String> placeholders = new HashMap<>();

        // Parse placeholders (formato: key=value)
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            int eqIndex = part.indexOf('=');
            if (eqIndex > 0 && eqIndex < part.length() - 1) {
                String key = part.substring(0, eqIndex);
                String value = part.substring(eqIndex + 1);
                placeholders.put(key, value);
            }
        }

        // Criar e disparar evento
        OpenPanelRequestEvent event = new OpenPanelRequestEvent(target, panelKey, placeholders);
        Bukkit.getPluginManager().callEvent(event);

        // Se nenhum listener tratou, logar warning
        if (!event.isHandled()) {
            Bukkit.getLogger().warning("[AfterCore] No handler found for panel: " + panelKey);
        }
    }
}
