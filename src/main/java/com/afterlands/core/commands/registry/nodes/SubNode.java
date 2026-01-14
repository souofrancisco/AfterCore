package com.afterlands.core.commands.registry.nodes;

import com.afterlands.core.commands.CommandSpec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Represents a subcommand node (e.g., reload in /mycommand reload).
 *
 * <p>Subcommands can be nested to create command hierarchies like:</p>
 * <ul>
 *   <li>/admin user add [player]</li>
 *   <li>/admin user remove [player]</li>
 *   <li>/admin config reload</li>
 * </ul>
 *
 * <p>This class is immutable and thread-safe.</p>
 */
public record SubNode(
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
     * Creates a new SubNode with defensive copying.
     */
    public SubNode {
        name = name.toLowerCase(Locale.ROOT);
        aliases = aliases.stream()
                .map(a -> a.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        children = Map.copyOf(children);
        arguments = List.copyOf(arguments);
        flags = List.copyOf(flags);
    }

    /**
     * Creates a new builder for SubNode.
     *
     * @param name Subcommand name
     * @return A new builder
     */
    @NotNull
    public static Builder builder(@NotNull String name) {
        return new Builder(name);
    }

    /**
     * Creates a SubNode from a CommandSpec.SubcommandSpec.
     *
     * @param spec   The specification
     * @param lambda Optional lambda executor (from builder)
     * @return A new SubNode
     */
    @NotNull
    public static SubNode fromSpec(@NotNull CommandSpec.SubcommandSpec spec, @Nullable CommandSpec.CommandExecutor lambda) {
        // Recursively build children
        Map<String, SubNode> children = new LinkedHashMap<>();
        for (CommandSpec.SubcommandSpec childSpec : spec.subcommands()) {
            SubNode child = fromSpec(childSpec, childSpec.executor());
            children.put(child.name(), child);
        }

        CompiledExecutor executor = lambda != null ? CompiledExecutor.fromLambda(lambda) : null;

        return new SubNode(
                spec.name(),
                spec.aliases(),
                spec.description(),
                spec.usage(),
                spec.permission(),
                spec.playerOnly(),
                children,
                spec.arguments(),
                spec.flags(),
                executor
        );
    }

    /**
     * Gets all names (primary + aliases) for this subcommand.
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
     * Checks if this subcommand matches a given name (case-insensitive).
     *
     * @param input The input to check
     * @return true if input matches name or any alias
     */
    public boolean matches(@NotNull String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return name.equals(lower) || aliases.contains(lower);
    }

    /**
     * Builder for SubNode.
     */
    public static final class Builder {
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

        private Builder(String name) {
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
        public SubNode build() {
            return new SubNode(
                    name, aliases, description, usage, permission,
                    playerOnly, children, arguments, flags, executor
            );
        }
    }
}
