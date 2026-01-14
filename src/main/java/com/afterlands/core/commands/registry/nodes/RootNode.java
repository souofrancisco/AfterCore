package com.afterlands.core.commands.registry.nodes;

import com.afterlands.core.commands.CommandSpec;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Represents a root command node (e.g., /mycommand).
 *
 * <p>Root nodes are the top-level entry points for commands and contain:</p>
 * <ul>
 *   <li>The owning plugin for lifecycle management</li>
 *   <li>Command name and aliases</li>
 *   <li>Description and usage information</li>
 *   <li>Permission requirements</li>
 *   <li>Child subcommands</li>
 *   <li>Direct executor (if the root command itself is executable)</li>
 * </ul>
 *
 * <p>This class is immutable and thread-safe.</p>
 */
public record RootNode(
        @NotNull Plugin owner,
        @NotNull String name,
        @NotNull Set<String> aliases,
        @Nullable String description,
        @Nullable String usage,
        @Nullable String permission,
        boolean playerOnly,
        @NotNull Map<String, SubNode> children,
        @NotNull List<CommandSpec.ArgumentSpec> arguments,
        @NotNull List<CommandSpec.FlagSpec> flags,
        @Nullable CompiledExecutor executor
) implements CommandNode {

    /**
     * Creates a new RootNode with defensive copying.
     */
    public RootNode {
        name = name.toLowerCase(Locale.ROOT);
        aliases = aliases.stream()
                .map(a -> a.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        children = Map.copyOf(children);
        arguments = List.copyOf(arguments);
        flags = List.copyOf(flags);
    }

    /**
     * Creates a new builder for RootNode.
     *
     * @param owner Plugin owner
     * @param name  Command name
     * @return A new builder
     */
    @NotNull
    public static Builder builder(@NotNull Plugin owner, @NotNull String name) {
        return new Builder(owner, name);
    }

    /**
     * Gets all names (primary + aliases) for this command.
     *
     * @return Set containing the name and all aliases
     */
    @NotNull
    public Set<String> allNames() {
        Set<String> all = new LinkedHashSet<>();
        all.add(name);
        all.addAll(aliases);
        return Collections.unmodifiableSet(all);
    }

    /**
     * Checks if this root matches a given name (case-insensitive).
     *
     * @param input The input to check
     * @return true if input matches name or any alias
     */
    public boolean matches(@NotNull String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return name.equals(lower) || aliases.contains(lower);
    }

    /**
     * Builder for RootNode.
     */
    public static final class Builder {
        private final Plugin owner;
        private final String name;
        private final Set<String> aliases = new LinkedHashSet<>();
        private String description;
        private String usage;
        private String permission;
        private boolean playerOnly = false;
        private final Map<String, SubNode> children = new LinkedHashMap<>();
        private final List<CommandSpec.ArgumentSpec> arguments = new ArrayList<>();
        private final List<CommandSpec.FlagSpec> flags = new ArrayList<>();
        private CompiledExecutor executor;

        private Builder(Plugin owner, String name) {
            this.owner = Objects.requireNonNull(owner, "owner");
            this.name = Objects.requireNonNull(name, "name").toLowerCase(Locale.ROOT);
        }

        @NotNull
        public Builder aliases(@NotNull String... aliases) {
            for (String alias : aliases) {
                this.aliases.add(alias.toLowerCase(Locale.ROOT));
            }
            return this;
        }

        @NotNull
        public Builder aliases(@NotNull Collection<String> aliases) {
            for (String alias : aliases) {
                this.aliases.add(alias.toLowerCase(Locale.ROOT));
            }
            return this;
        }

        @NotNull
        public Builder description(@Nullable String description) {
            this.description = description;
            return this;
        }

        @NotNull
        public Builder usage(@Nullable String usage) {
            this.usage = usage;
            return this;
        }

        @NotNull
        public Builder permission(@Nullable String permission) {
            this.permission = permission;
            return this;
        }

        @NotNull
        public Builder playerOnly(boolean playerOnly) {
            this.playerOnly = playerOnly;
            return this;
        }

        @NotNull
        public Builder child(@NotNull SubNode child) {
            this.children.put(child.name(), child);
            return this;
        }

        @NotNull
        public Builder children(@NotNull Collection<SubNode> children) {
            for (SubNode child : children) {
                this.children.put(child.name(), child);
            }
            return this;
        }

        @NotNull
        public Builder argument(@NotNull CommandSpec.ArgumentSpec argument) {
            this.arguments.add(argument);
            return this;
        }

        @NotNull
        public Builder arguments(@NotNull Collection<CommandSpec.ArgumentSpec> arguments) {
            this.arguments.addAll(arguments);
            return this;
        }

        @NotNull
        public Builder flag(@NotNull CommandSpec.FlagSpec flag) {
            this.flags.add(flag);
            return this;
        }

        @NotNull
        public Builder flags(@NotNull Collection<CommandSpec.FlagSpec> flags) {
            this.flags.addAll(flags);
            return this;
        }

        @NotNull
        public Builder executor(@Nullable CompiledExecutor executor) {
            this.executor = executor;
            return this;
        }

        @NotNull
        public RootNode build() {
            return new RootNode(
                    owner, name, aliases, description, usage, permission,
                    playerOnly, children, arguments, flags, executor
            );
        }
    }
}
