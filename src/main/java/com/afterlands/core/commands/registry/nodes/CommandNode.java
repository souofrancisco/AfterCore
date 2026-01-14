package com.afterlands.core.commands.registry.nodes;

import com.afterlands.core.commands.CommandSpec;
import com.afterlands.core.commands.execution.CommandContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Base interface for all command tree nodes.
 *
 * <p>The command framework uses a tree structure where:</p>
 * <ul>
 *   <li>{@link RootNode} represents the top-level command (e.g., /mycommand)</li>
 *   <li>{@link SubNode} represents subcommands (e.g., /mycommand reload)</li>
 *   <li>Arguments and flags are attached to nodes</li>
 * </ul>
 *
 * <p>This design allows efficient O(1) lookup for subcommands while maintaining
 * a clear hierarchy for help generation and tab completion.</p>
 */
public sealed interface CommandNode permits RootNode, SubNode {

    /**
     * Gets the primary name of this node.
     *
     * @return The node name
     */
    @NotNull
    String name();

    /**
     * Gets all aliases for this node.
     *
     * @return Immutable set of aliases
     */
    @NotNull
    Set<String> aliases();

    /**
     * Gets the description of this node.
     *
     * @return Description, or null if not set
     */
    @Nullable
    String description();

    /**
     * Gets the usage string for this node.
     *
     * @return Usage string, or null to auto-generate
     */
    @Nullable
    String usage();

    /**
     * Gets the permission required to execute this node.
     *
     * @return Permission string, or null if no permission required
     */
    @Nullable
    String permission();

    /**
     * Checks if this command requires a player sender.
     *
     * @return true if only players can execute this command
     */
    boolean playerOnly();

    /**
     * Gets child subcommands.
     *
     * @return Immutable map of name -> SubNode
     */
    @NotNull
    Map<String, SubNode> children();

    /**
     * Gets the argument specifications for this node.
     *
     * @return Immutable list of argument specs
     */
    @NotNull
    List<CommandSpec.ArgumentSpec> arguments();

    /**
     * Gets the flag specifications for this node.
     *
     * @return Immutable list of flag specs
     */
    @NotNull
    List<CommandSpec.FlagSpec> flags();

    /**
     * Gets the compiled executor for this node.
     *
     * <p>This is compiled from annotations/builders at registration time
     * to avoid reflection in the hot path.</p>
     *
     * @return The executor, or null if this node has no direct execution
     */
    @Nullable
    CompiledExecutor executor();

    /**
     * Looks up a child by name (including aliases).
     *
     * @param name Child name or alias
     * @return The child node, or null if not found
     */
    @Nullable
    default SubNode child(@NotNull String name) {
        SubNode direct = children().get(name.toLowerCase());
        if (direct != null) {
            return direct;
        }
        // Check aliases
        for (SubNode child : children().values()) {
            if (child.aliases().contains(name.toLowerCase())) {
                return child;
            }
        }
        return null;
    }

    /**
     * Checks if this node has an executor.
     *
     * @return true if this node can be directly executed
     */
    default boolean isExecutable() {
        return executor() != null;
    }

    /**
     * Checks if this node has children.
     *
     * @return true if this node has subcommands
     */
    default boolean hasChildren() {
        return !children().isEmpty();
    }

    /**
     * Generates the usage string for this node.
     *
     * @param label The command label (with path)
     * @return Generated usage string
     */
    @NotNull
    default String generateUsage(@NotNull String label) {
        if (usage() != null) {
            return usage();
        }

        StringBuilder sb = new StringBuilder("/").append(label);

        // Add arguments
        for (CommandSpec.ArgumentSpec arg : arguments()) {
            if (arg.optional()) {
                sb.append(" [").append(arg.name());
                if (arg.defaultValue() != null) {
                    sb.append("=").append(arg.defaultValue());
                }
                sb.append("]");
            } else {
                sb.append(" <").append(arg.name()).append(">");
            }
        }

        // Add flags hint if present
        if (!flags().isEmpty()) {
            sb.append(" [flags]");
        }

        return sb.toString();
    }

    /**
     * Compiled executor that wraps a MethodHandle or lambda.
     *
     * <p>This allows zero-reflection execution in the hot path while
     * supporting both annotation-based and builder-based commands.</p>
     */
    record CompiledExecutor(
            @Nullable MethodHandle methodHandle,
            @Nullable Object handlerInstance,
            @Nullable CommandSpec.CommandExecutor lambdaExecutor
    ) {
        /**
         * Creates a compiled executor from a lambda.
         */
        public static CompiledExecutor fromLambda(@NotNull CommandSpec.CommandExecutor executor) {
            return new CompiledExecutor(null, null, executor);
        }

        /**
         * Creates a compiled executor from a MethodHandle.
         */
        public static CompiledExecutor fromMethodHandle(@NotNull MethodHandle handle, @NotNull Object instance) {
            return new CompiledExecutor(handle, instance, null);
        }

        /**
         * Executes this compiled executor.
         *
         * @param context The command context
         * @throws Throwable if execution fails
         */
        public void execute(@NotNull CommandContext context) throws Throwable {
            if (lambdaExecutor != null) {
                lambdaExecutor.execute(context);
            } else if (methodHandle != null && handlerInstance != null) {
                methodHandle.invoke(handlerInstance, context);
            }
        }

        /**
         * Checks if this is a lambda executor.
         */
        public boolean isLambda() {
            return lambdaExecutor != null;
        }
    }
}
