package com.afterlands.core.actions.impl;

import com.afterlands.core.actions.ActionHandler;
import com.afterlands.core.actions.ActionService;
import com.afterlands.core.actions.ActionSpec;
import com.afterlands.core.actions.dialect.ActionDialect;
import com.afterlands.core.actions.dialect.MotionActionDialect;
import com.afterlands.core.actions.dialect.SimpleKvActionDialect;
import com.afterlands.core.conditions.ConditionService;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultActionService implements ActionService {

    @SuppressWarnings("unused")
    private final ConditionService conditions;
    private final boolean debug;

    private final List<ActionDialect> dialects;
    private final Map<String, ActionHandler> handlers = new ConcurrentHashMap<>();

    public DefaultActionService(@NotNull ConditionService conditions, boolean debug) {
        this.conditions = conditions;
        this.debug = debug;
        this.dialects = List.of(
                new MotionActionDialect(),
                new SimpleKvActionDialect());
    }

    @Override
    public @Nullable ActionSpec parse(@NotNull String line) {
        String s = line.trim();
        if (s.isEmpty())
            return null;

        // Normalize action format for compatibility
        s = normalizeAction(s);

        for (ActionDialect dialect : dialects) {
            if (!dialect.supports(s))
                continue;
            ActionSpec spec = dialect.parse(s);
            if (spec != null)
                return spec;
        }

        return null;
    }

    /**
     * Normalizes action format for compatibility.
     * Converts alternative formats to standard format:
     * - For simple actions: replaces ";" with " " in arguments
     * - Skip normalization for MotionActionDialect (actions starting with @)
     */
    private String normalizeAction(String action) {
        // Skip normalization for MotionActionDialect format (@tick:, @event:)
        if (action.startsWith("@")) {
            return action;
        }

        int colonIndex = action.indexOf(':');
        if (colonIndex > 0 && colonIndex < action.length() - 1) {
            String type = action.substring(0, colonIndex + 1);
            String args = action.substring(colonIndex + 1).trim();
            if (args.contains(";")) {
                args = args.replace(";", " ");
                return type + " " + args;
            }
        }

        return action;
    }

    @Override
    public void registerHandler(@NotNull String actionTypeKey, @NotNull ActionHandler handler) {
        String key = actionTypeKey.toLowerCase(Locale.ROOT);
        handlers.put(key, handler);

        if (debug) {
            Bukkit.getLogger().info("[ActionService] Successfully registered action handler: " + key);
        }
    }

    @Override
    public @NotNull Map<String, ActionHandler> getHandlers() {
        return Collections.unmodifiableMap(handlers);
    }
}
