package com.afterlands.core.commands;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Represents a successful command registration.
 *
 * <p>This immutable record contains metadata about the registered command,
 * including the owning plugin, command name, and aliases. It can be used
 * to track and later unregister specific commands.</p>
 *
 * @param owner    The plugin that owns this command registration
 * @param rootName The primary name of the registered command
 * @param aliases  Additional aliases for the command
 */
public record CommandRegistration(
        @NotNull Plugin owner,
        @NotNull String rootName,
        @NotNull Set<String> aliases
) {
    /**
     * Creates a new command registration.
     *
     * @param owner    The owning plugin
     * @param rootName The root command name
     * @param aliases  The command aliases (defensive copy is made)
     */
    public CommandRegistration {
        aliases = Set.copyOf(aliases); // Immutable defensive copy
    }
}
