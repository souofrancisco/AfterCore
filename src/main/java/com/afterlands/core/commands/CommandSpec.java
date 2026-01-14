package com.afterlands.core.commands;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

/**
 * Specification for building commands via DSL/Builder pattern.
 *
 * <p>This class provides a fluent API for defining commands programmatically
 * as an alternative to annotation-based registration. It supports:</p>
 * <ul>
 *   <li>Root command configuration (name, aliases, description)</li>
 *   <li>Subcommand hierarchies</li>
 *   <li>Argument definitions</li>
 *   <li>Flag definitions</li>
 *   <li>Permission requirements</li>
 *   <li>Execution handlers</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * CommandSpec spec = CommandSpec.root("mycommand")
 *     .aliases("mc", "mycmd")
 *     .description("My custom command")
 *     .permission("myplugin.mycommand")
 *     .sub("reload")
 *         .description("Reloads the configuration")
 *         .permission("myplugin.reload")
 *         .executor(ctx -> ctx.sender().sendMessage("Reloaded!"))
 *         .done()
 *     .sub("give")
 *         .arg("player", ArgumentSpec.PLAYER_ONLINE)
 *         .arg("amount", ArgumentSpec.INTEGER)
 *         .executor(ctx -> { ... })
 *         .done()
 *     .build();
 * }</pre>
 */
public final class CommandSpec {

    private final String name;
    private final Set<String> aliases;
    private final String description;
    private final String usage;
    private final String permission;
    private final boolean playerOnly;
    private final List<SubcommandSpec> subcommands;
    private final List<ArgumentSpec> arguments;
    private final List<FlagSpec> flags;
    private final CommandExecutor executor;

    private CommandSpec(Builder builder) {
        this.name = builder.name;
        this.aliases = Set.copyOf(builder.aliases);
        this.description = builder.description;
        this.usage = builder.usage;
        this.permission = builder.permission;
        this.playerOnly = builder.playerOnly;
        this.subcommands = List.copyOf(builder.subcommands);
        this.arguments = List.copyOf(builder.arguments);
        this.flags = List.copyOf(builder.flags);
        this.executor = builder.executor;
    }

    /**
     * Creates a new root command builder.
     *
     * @param name The root command name (without /)
     * @return A new builder instance
     */
    @NotNull
    public static Builder root(@NotNull String name) {
        return new Builder(name);
    }

    @NotNull
    public String name() {
        return name;
    }

    @NotNull
    public Set<String> aliases() {
        return aliases;
    }

    @Nullable
    public String description() {
        return description;
    }

    @Nullable
    public String usage() {
        return usage;
    }

    @Nullable
    public String permission() {
        return permission;
    }

    public boolean isPlayerOnly() {
        return playerOnly;
    }

    @NotNull
    public List<SubcommandSpec> subcommands() {
        return subcommands;
    }

    @NotNull
    public List<ArgumentSpec> arguments() {
        return arguments;
    }

    @NotNull
    public List<FlagSpec> flags() {
        return flags;
    }

    @Nullable
    public CommandExecutor executor() {
        return executor;
    }

    /**
     * Builder for CommandSpec.
     */
    public static final class Builder {
        private final String name;
        private final Set<String> aliases = new LinkedHashSet<>();
        private String description;
        private String usage;
        private String permission;
        private boolean playerOnly = false;
        private final List<SubcommandSpec> subcommands = new ArrayList<>();
        private final List<ArgumentSpec> arguments = new ArrayList<>();
        private final List<FlagSpec> flags = new ArrayList<>();
        private CommandExecutor executor;

        private Builder(String name) {
            this.name = Objects.requireNonNull(name, "name");
        }

        @NotNull
        public Builder aliases(@NotNull String... aliases) {
            Collections.addAll(this.aliases, aliases);
            return this;
        }

        @NotNull
        public Builder description(@NotNull String description) {
            this.description = description;
            return this;
        }

        @NotNull
        public Builder usage(@NotNull String usage) {
            this.usage = usage;
            return this;
        }

        @NotNull
        public Builder permission(@NotNull String permission) {
            this.permission = permission;
            return this;
        }

        @NotNull
        public Builder playerOnly(boolean playerOnly) {
            this.playerOnly = playerOnly;
            return this;
        }

        @NotNull
        public Builder playerOnly() {
            return playerOnly(true);
        }

        /**
         * Adds a subcommand using a builder callback.
         *
         * @param name     Subcommand name
         * @param subBuilder Callback to configure the subcommand
         * @return This builder
         */
        @NotNull
        public Builder sub(@NotNull String name, @NotNull Consumer<SubcommandSpec.Builder> subBuilder) {
            SubcommandSpec.Builder builder = SubcommandSpec.builder(name);
            subBuilder.accept(builder);
            this.subcommands.add(builder.build());
            return this;
        }

        /**
         * Starts building a subcommand with fluent chaining.
         *
         * @param name Subcommand name
         * @return Subcommand builder that can chain back to this builder
         */
        @NotNull
        public SubcommandSpec.ChainedBuilder sub(@NotNull String name) {
            return new SubcommandSpec.ChainedBuilder(name, this);
        }

        /**
         * Internal method to add a built subcommand.
         */
        void addSubcommand(SubcommandSpec spec) {
            this.subcommands.add(spec);
        }

        @NotNull
        public Builder arg(@NotNull String name, @NotNull String type) {
            this.arguments.add(new ArgumentSpec(name, type, null, false, null));
            return this;
        }

        @NotNull
        public Builder arg(@NotNull String name, @NotNull String type, @Nullable String defaultValue) {
            this.arguments.add(new ArgumentSpec(name, type, defaultValue, true, null));
            return this;
        }

        @NotNull
        public Builder flag(@NotNull String name, @NotNull String shortName) {
            this.flags.add(new FlagSpec(name, shortName, null, false));
            return this;
        }

        @NotNull
        public Builder flag(@NotNull String name, @NotNull String shortName, @NotNull String valueType) {
            this.flags.add(new FlagSpec(name, shortName, valueType, true));
            return this;
        }

        @NotNull
        public Builder executor(@NotNull CommandExecutor executor) {
            this.executor = executor;
            return this;
        }

        @NotNull
        public CommandSpec build() {
            return new CommandSpec(this);
        }
    }

    /**
     * Specification for a subcommand.
     */
    public record SubcommandSpec(
            @NotNull String name,
            @NotNull Set<String> aliases,
            @Nullable String description,
            @Nullable String usage,
            @Nullable String permission,
            boolean playerOnly,
            @NotNull List<SubcommandSpec> subcommands,
            @NotNull List<ArgumentSpec> arguments,
            @NotNull List<FlagSpec> flags,
            @Nullable CommandExecutor executor
    ) {
        public SubcommandSpec {
            aliases = Set.copyOf(aliases);
            subcommands = List.copyOf(subcommands);
            arguments = List.copyOf(arguments);
            flags = List.copyOf(flags);
        }

        static Builder builder(String name) {
            return new Builder(name);
        }

        public static final class Builder {
            private final String name;
            private final Set<String> aliases = new LinkedHashSet<>();
            private String description;
            private String usage;
            private String permission;
            private boolean playerOnly = false;
            private final List<SubcommandSpec> subcommands = new ArrayList<>();
            private final List<ArgumentSpec> arguments = new ArrayList<>();
            private final List<FlagSpec> flags = new ArrayList<>();
            private CommandExecutor executor;

            Builder(String name) {
                this.name = name;
            }

            @NotNull
            public Builder aliases(@NotNull String... aliases) {
                Collections.addAll(this.aliases, aliases);
                return this;
            }

            @NotNull
            public Builder description(@NotNull String description) {
                this.description = description;
                return this;
            }

            @NotNull
            public Builder usage(@NotNull String usage) {
                this.usage = usage;
                return this;
            }

            @NotNull
            public Builder permission(@NotNull String permission) {
                this.permission = permission;
                return this;
            }

            @NotNull
            public Builder playerOnly(boolean playerOnly) {
                this.playerOnly = playerOnly;
                return this;
            }

            @NotNull
            public Builder arg(@NotNull String name, @NotNull String type) {
                this.arguments.add(new ArgumentSpec(name, type, null, false, null));
                return this;
            }

            @NotNull
            public Builder arg(@NotNull String name, @NotNull String type, @Nullable String defaultValue) {
                this.arguments.add(new ArgumentSpec(name, type, defaultValue, true, null));
                return this;
            }

            @NotNull
            public Builder flag(@NotNull String name, @NotNull String shortName) {
                this.flags.add(new FlagSpec(name, shortName, null, false));
                return this;
            }

            @NotNull
            public Builder executor(@NotNull CommandExecutor executor) {
                this.executor = executor;
                return this;
            }

            @NotNull
            SubcommandSpec build() {
                return new SubcommandSpec(
                        name, aliases, description, usage, permission, playerOnly,
                        subcommands, arguments, flags, executor
                );
            }
        }

        /**
         * Chained builder that returns to parent builder when done.
         */
        public static final class ChainedBuilder {
            private final Builder delegate;
            private final CommandSpec.Builder parent;

            ChainedBuilder(String name, CommandSpec.Builder parent) {
                this.delegate = new Builder(name);
                this.parent = parent;
            }

            @NotNull
            public ChainedBuilder aliases(@NotNull String... aliases) {
                delegate.aliases(aliases);
                return this;
            }

            @NotNull
            public ChainedBuilder description(@NotNull String description) {
                delegate.description(description);
                return this;
            }

            @NotNull
            public ChainedBuilder usage(@NotNull String usage) {
                delegate.usage(usage);
                return this;
            }

            @NotNull
            public ChainedBuilder permission(@NotNull String permission) {
                delegate.permission(permission);
                return this;
            }

            @NotNull
            public ChainedBuilder playerOnly() {
                delegate.playerOnly(true);
                return this;
            }

            @NotNull
            public ChainedBuilder arg(@NotNull String name, @NotNull String type) {
                delegate.arg(name, type);
                return this;
            }

            @NotNull
            public ChainedBuilder arg(@NotNull String name, @NotNull String type, @Nullable String defaultValue) {
                delegate.arg(name, type, defaultValue);
                return this;
            }

            @NotNull
            public ChainedBuilder flag(@NotNull String name, @NotNull String shortName) {
                delegate.flag(name, shortName);
                return this;
            }

            @NotNull
            public ChainedBuilder executor(@NotNull CommandExecutor executor) {
                delegate.executor(executor);
                return this;
            }

            /**
             * Finishes this subcommand and returns to the parent builder.
             */
            @NotNull
            public CommandSpec.Builder done() {
                parent.addSubcommand(delegate.build());
                return parent;
            }
        }
    }

    /**
     * Specification for a command argument.
     */
    public record ArgumentSpec(
            @NotNull String name,
            @NotNull String type,
            @Nullable String defaultValue,
            boolean optional,
            @Nullable String description
    ) {
        // Common type constants
        public static final String STRING = "string";
        public static final String GREEDY_STRING = "greedyString";
        public static final String INTEGER = "integer";
        public static final String DOUBLE = "double";
        public static final String BOOLEAN = "boolean";
        public static final String PLAYER_ONLINE = "playerOnline";
        public static final String PLAYER_OFFLINE = "playerOffline";
        public static final String WORLD = "world";
        public static final String LOCATION = "location";
    }

    /**
     * Specification for a command flag.
     */
    public record FlagSpec(
            @NotNull String name,
            @Nullable String shortName,
            @Nullable String valueType,
            boolean hasValue
    ) {}

    /**
     * Functional interface for command execution.
     */
    @FunctionalInterface
    public interface CommandExecutor {
        /**
         * Executes the command.
         *
         * @param context The command execution context
         */
        void execute(@NotNull com.afterlands.core.commands.execution.CommandContext context);
    }
}
