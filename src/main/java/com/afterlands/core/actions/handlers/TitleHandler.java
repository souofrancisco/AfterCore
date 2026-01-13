package com.afterlands.core.actions.handlers;

import com.afterlands.core.actions.ActionHandler;
import com.afterlands.core.actions.ActionSpec;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;

/**
 * Handler para enviar títulos (title + subtitle).
 *
 * <p>
 * Formato: {@code title: <title>;<subtitle>;<fadeIn>;<stay>;<fadeOut>}
 * </p>
 * <p>
 * Suporta:
 * </p>
 * <ul>
 * <li>Title e subtitle separados por semicolon (;)</li>
 * <li>Timings opcionais (fadeIn, stay, fadeOut em ticks)</li>
 * <li>Color codes (&a, &b, etc.)</li>
 * <li>PlaceholderAPI (se disponível)</li>
 * </ul>
 *
 * <p>
 * Exemplos:
 * </p>
 * 
 * <pre>
 * title: &aOlá!;&7Bem-vindo
 * title: &cAlerta;&7Cuidado!;10;40;10
 * title: %player_name%;Level %player_level%
 * title: &eTitle sem subtitle
 * </pre>
 *
 * <p>
 * Timings padrão: fadeIn=10, stay=70, fadeOut=20 (em ticks)
 * </p>
 * <p>
 * Compatibilidade: Spigot 1.8.8+ (usa reflection para suportar ambas APIs)
 * </p>
 */
public final class TitleHandler implements ActionHandler {

    private static final int DEFAULT_FADE_IN = 10;
    private static final int DEFAULT_STAY = 70;
    private static final int DEFAULT_FADE_OUT = 20;

    // Reflection para suportar ambas versões do Spigot
    private static final Method SEND_TITLE_METHOD = findSendTitleMethod();

    @Override
    public void execute(@NotNull Player target, @NotNull ActionSpec spec) {
        String args = spec.rawArgs();
        if (args == null || args.isEmpty()) {
            return;
        }

        // Parse: title;subtitle;fadeIn;stay;fadeOut
        // Support semicolon separator for consistency with other handlers
        String[] parts = args.split(";");

        String title = parts.length > 0 ? parts[0].trim() : "";
        String subtitle = parts.length > 1 ? parts[1].trim() : "";

        int fadeIn = DEFAULT_FADE_IN;
        int stay = DEFAULT_STAY;
        int fadeOut = DEFAULT_FADE_OUT;

        // Parse timings (optional)
        if (parts.length >= 5) {
            try {
                fadeIn = Integer.parseInt(parts[2].trim());
                stay = Integer.parseInt(parts[3].trim());
                fadeOut = Integer.parseInt(parts[4].trim());
            } catch (NumberFormatException ignored) {
                // Use defaults
            }
        }

        // Processar PlaceholderAPI e color codes
        if (!title.isEmpty()) {
            title = PlaceholderUtil.process(target, title);
            title = ChatColor.translateAlternateColorCodes('&', title);
        }

        if (!subtitle.isEmpty()) {
            subtitle = PlaceholderUtil.process(target, subtitle);
            subtitle = ChatColor.translateAlternateColorCodes('&', subtitle);
        }

        // Enviar title (Spigot 1.8.8+)
        if (title.isEmpty() && subtitle.isEmpty()) {
            return; // Nada para enviar
        }

        sendTitle(target, title.isEmpty() ? " " : title, subtitle.isEmpty() ? " " : subtitle, fadeIn, stay, fadeOut);
    }

    /**
     * Detecta qual método sendTitle está disponível.
     */
    private static Method findSendTitleMethod() {
        try {
            // Tentar API moderna (1.11+): sendTitle(String, String, int, int, int)
            return Player.class.getMethod("sendTitle", String.class, String.class, int.class, int.class, int.class);
        } catch (NoSuchMethodException e) {
            // Fallback para API antiga (1.8.8): sendTitle(String, String)
            try {
                return Player.class.getMethod("sendTitle", String.class, String.class);
            } catch (NoSuchMethodException ex) {
                return null; // Sem suporte
            }
        }
    }

    /**
     * Envia title usando reflection para compatibilidade.
     */
    private void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        if (SEND_TITLE_METHOD == null) {
            return; // Sem suporte
        }

        try {
            int paramCount = SEND_TITLE_METHOD.getParameterCount();
            if (paramCount == 5) {
                // API moderna
                SEND_TITLE_METHOD.invoke(player, title, subtitle, fadeIn, stay, fadeOut);
            } else if (paramCount == 2) {
                // API antiga (sem timing control)
                SEND_TITLE_METHOD.invoke(player, title, subtitle);
            }
        } catch (Exception ignored) {
            // Falhou silenciosamente
        }
    }
}
