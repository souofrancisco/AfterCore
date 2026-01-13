package com.afterlands.core.inventory.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Evento disparado quando uma action {@code open-panel: <key>} é executada.
 *
 * <p>Plugins podem escutar este evento para abrir suas GUIs customizadas
 * quando o panelKey corresponder ao seu inventário.</p>
 *
 * <p><b>Uso típico:</b></p>
 * <pre>
 * {@code @EventHandler}
 * public void onOpenPanel(OpenPanelRequestEvent event) {
 *     if (event.getPanelKey().equals("animation-list")) {
 *         openAnimationList(event.getPlayer());
 *         event.setHandled(true);
 *     }
 * }
 * </pre>
 *
 * <p><b>Thread:</b> Sempre disparado na main thread.</p>
 */
public class OpenPanelRequestEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String panelKey;
    private final Map<String, String> placeholders;
    private boolean handled = false;

    /**
     * Cria um novo evento de requisição de abertura de painel.
     *
     * @param player   Jogador que solicitou a abertura
     * @param panelKey Chave do painel a ser aberto (ex: "animation-list", "main-menu")
     */
    public OpenPanelRequestEvent(@NotNull Player player, @NotNull String panelKey) {
        this.player = player;
        this.panelKey = panelKey;
        this.placeholders = new ConcurrentHashMap<>();
    }

    /**
     * Cria um novo evento com placeholders customizados.
     *
     * @param player       Jogador que solicitou a abertura
     * @param panelKey     Chave do painel a ser aberto
     * @param placeholders Placeholders adicionais para o contexto do inventário
     */
    public OpenPanelRequestEvent(@NotNull Player player, @NotNull String panelKey,
                                  @Nullable Map<String, String> placeholders) {
        this.player = player;
        this.panelKey = panelKey;
        this.placeholders = placeholders != null
            ? new ConcurrentHashMap<>(placeholders)
            : new ConcurrentHashMap<>();
    }

    /**
     * Obtém o jogador que solicitou a abertura do painel.
     *
     * @return Jogador
     */
    @NotNull
    public Player getPlayer() {
        return player;
    }

    /**
     * Obtém a chave do painel solicitado.
     *
     * @return Chave do painel (ex: "animation-list", "editor")
     */
    @NotNull
    public String getPanelKey() {
        return panelKey;
    }

    /**
     * Obtém os placeholders passados junto com a requisição.
     *
     * <p>Útil para passar contexto adicional como IDs, páginas, etc.</p>
     *
     * @return Mapa de placeholders (nunca null, pode estar vazio)
     */
    @NotNull
    public Map<String, String> getPlaceholders() {
        return placeholders;
    }

    /**
     * Adiciona um placeholder ao contexto.
     *
     * @param key   Chave do placeholder
     * @param value Valor
     * @return this para chaining
     */
    @NotNull
    public OpenPanelRequestEvent withPlaceholder(@NotNull String key, @NotNull String value) {
        this.placeholders.put(key, value);
        return this;
    }

    /**
     * Verifica se o evento já foi tratado por algum listener.
     *
     * @return true se já foi tratado
     */
    public boolean isHandled() {
        return handled;
    }

    /**
     * Marca o evento como tratado.
     *
     * <p>Chame este método após abrir a GUI para indicar
     * que outros listeners não precisam processar.</p>
     *
     * @param handled true se foi tratado
     */
    public void setHandled(boolean handled) {
        this.handled = handled;
    }

    @Override
    @NotNull
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
