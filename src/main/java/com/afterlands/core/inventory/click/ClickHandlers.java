package com.afterlands.core.inventory.click;

import com.afterlands.core.inventory.action.ConfiguredAction;
import org.bukkit.event.inventory.ClickType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Container imutável de handlers por tipo de click.
 *
 * <p>
 * Suporta tanto handlers programáticos (ClickHandler) quanto
 * ações configuradas (ConfiguredAction) para YAML.
 * </p>
 */
public class ClickHandlers {

    // Actions por tipo de click (YAML)
    private final Map<ClickType, ConfiguredAction> actionsByType;

    // Handlers programáticos (API Java)
    private final Map<ClickType, ClickHandler> handlersByType;

    // Fallback actions (executadas se tipo específico não definido)
    private final ConfiguredAction defaultActions;

    // Fallback handler
    private final ClickHandler defaultHandler;

    private ClickHandlers(Builder builder) {
        this.actionsByType = Map.copyOf(builder.actionsByType);
        this.handlersByType = Map.copyOf(builder.handlersByType);
        this.defaultActions = builder.defaultActions != null ? builder.defaultActions
                : ConfiguredAction.simple(List.of());
        this.defaultHandler = builder.defaultHandler;
    }

    /**
     * Obtém actions para um tipo de click.
     *
     * @param clickType Tipo do click
     * @return ConfiguredAction ou defaultActions se não definido
     */
    @NotNull
    public ConfiguredAction getActions(@NotNull ClickType clickType) {
        return actionsByType.getOrDefault(clickType, defaultActions);
    }

    /**
     * Obtém handler para um tipo de click.
     *
     * @param clickType Tipo do click
     * @return Handler ou defaultHandler se não definido (pode ser null)
     */
    @Nullable
    public ClickHandler getHandler(@NotNull ClickType clickType) {
        return handlersByType.getOrDefault(clickType, defaultHandler);
    }

    /**
     * Verifica se tem handler ou actions para um tipo de click.
     *
     * @param clickType Tipo do click
     * @return true se tem handler ou actions
     */
    public boolean hasHandlerFor(@NotNull ClickType clickType) {
        return handlersByType.containsKey(clickType)
                || actionsByType.containsKey(clickType)
                || defaultHandler != null
                || !defaultActions.success().isEmpty() || !defaultActions.fail().isEmpty();
    }

    /**
     * Verifica se usa handlers programáticos.
     *
     * @return true se tem pelo menos um handler programático
     */
    public boolean hasProgrammaticHandlers() {
        return !handlersByType.isEmpty() || defaultHandler != null;
    }

    /**
     * Cria builder vazio.
     *
     * @return Builder para configurar handlers
     */
    @NotNull
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Cria ClickHandlers com apenas actions default (compatibilidade).
     *
     * @param actions Lista de actions padrão
     * @return ClickHandlers configurado
     */
    @NotNull
    public static ClickHandlers ofDefault(@NotNull List<String> actions) {
        return builder().defaultActions(actions).build();
    }

    /**
     * Builder para construir ClickHandlers de forma fluente.
     */
    public static class Builder {
        private final Map<ClickType, ConfiguredAction> actionsByType = new EnumMap<>(ClickType.class);
        private final Map<ClickType, ClickHandler> handlersByType = new EnumMap<>(ClickType.class);
        private ConfiguredAction defaultActions;
        private ClickHandler defaultHandler;

        // ========== YAML-style setters (actions) ==========

        @NotNull
        public Builder onLeftClick(@NotNull ConfiguredAction actions) {
            actionsByType.put(ClickType.LEFT, actions);
            return this;
        }

        @NotNull
        public Builder onLeftClick(@NotNull List<String> actions) {
            return onLeftClick(ConfiguredAction.simple(actions));
        }

        @NotNull
        public Builder onRightClick(@NotNull ConfiguredAction actions) {
            actionsByType.put(ClickType.RIGHT, actions);
            return this;
        }

        @NotNull
        public Builder onRightClick(@NotNull List<String> actions) {
            return onRightClick(ConfiguredAction.simple(actions));
        }

        @NotNull
        public Builder onShiftLeftClick(@NotNull ConfiguredAction actions) {
            actionsByType.put(ClickType.SHIFT_LEFT, actions);
            return this;
        }

        @NotNull
        public Builder onShiftLeftClick(@NotNull List<String> actions) {
            return onShiftLeftClick(ConfiguredAction.simple(actions));
        }

        @NotNull
        public Builder onShiftRightClick(@NotNull ConfiguredAction actions) {
            actionsByType.put(ClickType.SHIFT_RIGHT, actions);
            return this;
        }

        @NotNull
        public Builder onShiftRightClick(@NotNull List<String> actions) {
            return onShiftRightClick(ConfiguredAction.simple(actions));
        }

        @NotNull
        public Builder onMiddleClick(@NotNull ConfiguredAction actions) {
            actionsByType.put(ClickType.MIDDLE, actions);
            return this;
        }

        @NotNull
        public Builder onMiddleClick(@NotNull List<String> actions) {
            return onMiddleClick(ConfiguredAction.simple(actions));
        }

        @NotNull
        public Builder onDoubleClick(@NotNull ConfiguredAction actions) {
            actionsByType.put(ClickType.DOUBLE_CLICK, actions);
            return this;
        }

        @NotNull
        public Builder onDoubleClick(@NotNull List<String> actions) {
            return onDoubleClick(ConfiguredAction.simple(actions));
        }

        @NotNull
        public Builder onDrop(@NotNull ConfiguredAction actions) {
            actionsByType.put(ClickType.DROP, actions);
            return this;
        }

        @NotNull
        public Builder onDrop(@NotNull List<String> actions) {
            return onDrop(ConfiguredAction.simple(actions));
        }

        @NotNull
        public Builder onControlDrop(@NotNull ConfiguredAction actions) {
            actionsByType.put(ClickType.CONTROL_DROP, actions);
            return this;
        }

        @NotNull
        public Builder onControlDrop(@NotNull List<String> actions) {
            return onControlDrop(ConfiguredAction.simple(actions));
        }

        @NotNull
        public Builder onNumberKey(@NotNull ConfiguredAction actions) {
            actionsByType.put(ClickType.NUMBER_KEY, actions);
            return this;
        }

        @NotNull
        public Builder onNumberKey(@NotNull List<String> actions) {
            return onNumberKey(ConfiguredAction.simple(actions));
        }

        @NotNull
        public Builder onClickType(@NotNull ClickType type, @NotNull ConfiguredAction actions) {
            actionsByType.put(type, actions);
            return this;
        }

        @NotNull
        public Builder onClickType(@NotNull ClickType type, @NotNull List<String> actions) {
            return onClickType(type, ConfiguredAction.simple(actions));
        }

        // ========== API-style setters (handlers) ==========

        @NotNull
        public Builder onLeftClick(@NotNull ClickHandler handler) {
            handlersByType.put(ClickType.LEFT, handler);
            return this;
        }

        @NotNull
        public Builder onRightClick(@NotNull ClickHandler handler) {
            handlersByType.put(ClickType.RIGHT, handler);
            return this;
        }

        @NotNull
        public Builder onShiftLeftClick(@NotNull ClickHandler handler) {
            handlersByType.put(ClickType.SHIFT_LEFT, handler);
            return this;
        }

        @NotNull
        public Builder onShiftRightClick(@NotNull ClickHandler handler) {
            handlersByType.put(ClickType.SHIFT_RIGHT, handler);
            return this;
        }

        @NotNull
        public Builder onMiddleClick(@NotNull ClickHandler handler) {
            handlersByType.put(ClickType.MIDDLE, handler);
            return this;
        }

        @NotNull
        public Builder onDoubleClick(@NotNull ClickHandler handler) {
            handlersByType.put(ClickType.DOUBLE_CLICK, handler);
            return this;
        }

        @NotNull
        public Builder onDrop(@NotNull ClickHandler handler) {
            handlersByType.put(ClickType.DROP, handler);
            return this;
        }

        @NotNull
        public Builder onControlDrop(@NotNull ClickHandler handler) {
            handlersByType.put(ClickType.CONTROL_DROP, handler);
            return this;
        }

        @NotNull
        public Builder onNumberKey(@NotNull ClickHandler handler) {
            handlersByType.put(ClickType.NUMBER_KEY, handler);
            return this;
        }

        @NotNull
        public Builder onClickType(@NotNull ClickType type, @NotNull ClickHandler handler) {
            handlersByType.put(type, handler);
            return this;
        }

        // ========== Default fallbacks ==========

        @NotNull
        public Builder defaultActions(@NotNull ConfiguredAction actions) {
            this.defaultActions = actions;
            return this;
        }

        @NotNull
        public Builder defaultActions(@NotNull List<String> actions) {
            return defaultActions(ConfiguredAction.simple(actions));
        }

        @NotNull
        public Builder defaultHandler(@NotNull ClickHandler handler) {
            this.defaultHandler = handler;
            return this;
        }

        // Aliases for compatibility

        @NotNull
        public Builder actions(@NotNull List<String> actions) {
            return defaultActions(actions);
        }

        @NotNull
        public Builder onClick(@NotNull ClickHandler handler) {
            return defaultHandler(handler);
        }

        @NotNull
        public ClickHandlers build() {
            return new ClickHandlers(this);
        }
    }
}
