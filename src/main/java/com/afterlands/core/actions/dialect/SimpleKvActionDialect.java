package com.afterlands.core.actions.dialect;

import com.afterlands.core.actions.ActionScope;
import com.afterlands.core.actions.ActionSpec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Dialeto default (estilo AfterBlockAnimations):
 * - "message: oi"
 * - "time: 20, play_sound: LEVEL_UP;1;1"
 * - "frame: 5, teleport: world;x;y;z;yaw;pitch"
 */
public final class SimpleKvActionDialect implements ActionDialect {

    @Override
    public boolean supports(@NotNull String line) {
        return !line.trim().startsWith("@");
    }

    @Override
    public @Nullable ActionSpec parse(@NotNull String line) {
        String s = line.trim();
        if (s.isEmpty())
            return null;

        Long timeTicks = null;
        Integer frameIndex = null;

        String foundType = null;
        String foundArgs = null;

        // Split by comma (primary separator for key-value pairs)
        // Format: "frame: 2, sound: LEVEL_UP;1;1" or "time: 100, message: Hello"
        String[] parts = s.split("\\s*,\\s*");
        for (String p : parts) {
            String[] kv = p.split("\\s*:\\s*", 2);
            if (kv.length != 2)
                continue;
            String k = kv[0].trim().toLowerCase();
            String v = kv[1].trim();

            if (k.equals("time")) {
                // suporta "10s" (segundos) e "200" (ticks)
                if (v.endsWith("s")) {
                    try {
                        long sec = Long.parseLong(v.substring(0, v.length() - 1));
                        timeTicks = sec * 20L;
                    } catch (NumberFormatException ignored) {
                    }
                } else {
                    try {
                        timeTicks = Long.parseLong(v);
                    } catch (NumberFormatException ignored) {
                    }
                }
            } else if (k.equals("frame")) {
                try {
                    frameIndex = Integer.parseInt(v);
                } catch (NumberFormatException ignored) {
                }
            } else if (k.equals("wait")) {
                // Wait is a timing control - store as timeTicks for later processing
                try {
                    timeTicks = Long.parseLong(v);
                } catch (NumberFormatException ignored) {
                }
                // Mark as a wait action type
                foundType = "wait";
                foundArgs = v;
            } else {
                foundType = k;
                foundArgs = v;
            }
        }

        if (foundType == null || foundArgs == null) {
            return null;
        }

        return new ActionSpec(
                foundType,
                foundArgs,
                timeTicks,
                frameIndex,
                null,
                null,
                ActionScope.VIEWER,
                0,
                line);
    }
}
