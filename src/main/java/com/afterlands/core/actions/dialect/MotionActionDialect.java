package com.afterlands.core.actions.dialect;

import com.afterlands.core.actions.ActionScope;
import com.afterlands.core.actions.ActionSpec;
import com.afterlands.core.actions.ActionTrigger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dialeto avançado compatível com AfterMotion:
 * "@<trigger> [?<condition>] <action>[scope]: <params>"
 */
public final class MotionActionDialect implements ActionDialect {

    private static final Pattern TRIGGER_PATTERN =
            Pattern.compile("^@(tick|event):([A-Za-z_]+|\\d+)(?::(\\d+))?\\s*");

    private static final Pattern ACTION_PATTERN =
            Pattern.compile("([a-z_]+)(?:\\[([A-Z]+(?::\\d+)?)\\])?(?::\\s*(.+))?$");

    @Override
    public boolean supports(@NotNull String line) {
        return line.trim().startsWith("@");
    }

    @Override
    public @Nullable ActionSpec parse(@NotNull String line) {
        String trimmed = line.trim();
        if (!trimmed.startsWith("@")) return null;

        try {
            Matcher triggerMatcher = TRIGGER_PATTERN.matcher(trimmed);
            if (!triggerMatcher.find()) return null;

            ActionTrigger trigger = parseTrigger(
                    triggerMatcher.group(1),
                    triggerMatcher.group(2),
                    triggerMatcher.group(3)
            );
            if (trigger == null) return null;

            String remaining = trimmed.substring(triggerMatcher.end()).trim();

            // condition optional: "[? ... ]" ou legacy "?..."
            String condition = null;
            if (remaining.startsWith("[?") || remaining.startsWith("?")) {
                if (remaining.startsWith("[?")) {
                    int end = remaining.indexOf(']', 2);
                    if (end <= 2) return null;
                    condition = remaining.substring(2, end).trim();
                    remaining = remaining.substring(end + 1).trim();
                } else {
                    // legacy: ?cond <action>...
                    int actionStart = findActionStart(remaining);
                    if (actionStart > 1) {
                        condition = remaining.substring(1, actionStart).trim();
                        remaining = remaining.substring(actionStart).trim();
                    }
                }
            }

            Matcher actionMatcher = ACTION_PATTERN.matcher(remaining);
            if (!actionMatcher.find()) return null;

            String typeKey = actionMatcher.group(1).trim().toLowerCase();
            String scopeStr = actionMatcher.group(2);
            String args = actionMatcher.group(3);
            // Convert null args to empty string (for actions without parameters like "pause")
            if (args == null) {
                args = "";
            }

            ActionScope scope = ActionScope.VIEWER;
            int radius = 30;
            if (scopeStr != null) {
                String upper = scopeStr.toUpperCase();
                if (upper.startsWith("NEARBY")) {
                    scope = ActionScope.NEARBY;
                    int idx = upper.indexOf(':');
                    if (idx > 0) {
                        try {
                            radius = Integer.parseInt(upper.substring(idx + 1));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                } else if (upper.startsWith("ALL")) {
                    scope = ActionScope.ALL;
                } else {
                    scope = ActionScope.VIEWER;
                }
            }

            return new ActionSpec(
                    typeKey,
                    args,
                    null,
                    null,
                    trigger,
                    condition,
                    scope,
                    radius,
                    line
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private static @Nullable ActionTrigger parseTrigger(String type, String value, String actorIndex) {
        if ("tick".equalsIgnoreCase(type)) {
            try {
                return ActionTrigger.atTick(Integer.parseInt(value));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if ("event".equalsIgnoreCase(type)) {
            if (actorIndex != null && !actorIndex.isEmpty()) {
                try {
                    return ActionTrigger.onActorEvent(value, Integer.parseInt(actorIndex));
                } catch (NumberFormatException e) {
                    return ActionTrigger.onEvent(value);
                }
            }
            return ActionTrigger.onEvent(value);
        }
        return null;
    }

    private static int findActionStart(String str) {
        String[] actionTypes = {
                "sound:", "resource_pack_sound:", "title:", "message:",
                "actionbar:", "player_command:", "console_command:",
                "teleport:", "potion:"
        };
        int earliest = str.length();
        String lower = str.toLowerCase();
        for (String type : actionTypes) {
            int idx = lower.indexOf(type);
            if (idx > 0 && idx < earliest) earliest = idx;
        }
        for (String type : actionTypes) {
            String withBracket = type.replace(":", "[");
            int idx = lower.indexOf(withBracket);
            if (idx > 0 && idx < earliest) earliest = idx;
        }
        return earliest;
    }
}

