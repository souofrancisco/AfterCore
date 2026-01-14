package com.afterlands.core.commands.registry;

import com.afterlands.core.commands.CommandRegistration;
import com.afterlands.core.commands.CommandSpec;
import com.afterlands.core.commands.binding.BukkitCommandBinder;
import com.afterlands.core.commands.registry.nodes.CommandNode;
import com.afterlands.core.commands.registry.nodes.RootNode;
import com.afterlands.core.commands.registry.nodes.SubNode;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Central registry for command management.
 *
 * <p>The CommandRegistry coordinates between the command graph (internal representation)
 * and the Bukkit command binder (external registration). It provides:</p>
 * <ul>
 *   <li>Registration from CommandSpec (DSL/Builder)</li>
 *   <li>Registration from annotated handlers (processed by AnnotationProcessor)</li>
 *   <li>Lifecycle management (register/unregister by owner)</li>
 *   <li>Conflict resolution and logging</li>
 * </ul>
 *
 * <p>Thread Safety: All public methods are thread-safe.</p>
 */
public final class CommandRegistry {

    private final Plugin corePlugin;
    private final Logger logger;
    private final CommandGraph graph;
    private final BukkitCommandBinder binder;
    private final boolean debug;

    /**
     * Creates a new CommandRegistry.
     *
     * @param corePlugin The AfterCore plugin instance
     * @param binder     The Bukkit command binder
     * @param debug      Whether debug logging is enabled
     */
    public CommandRegistry(@NotNull Plugin corePlugin, @NotNull BukkitCommandBinder binder, boolean debug) {
        this.corePlugin = Objects.requireNonNull(corePlugin, "corePlugin");
        this.logger = corePlugin.getLogger();
        this.graph = new CommandGraph();
        this.binder = Objects.requireNonNull(binder, "binder");
        this.debug = debug;
    }

    /**
     * Registers a command from a CommandSpec (DSL/Builder).
     *
     * @param owner The plugin that owns this command
     * @param spec  The command specification
     * @return Registration result
     * @throws IllegalArgumentException if registration fails due to conflicts
     */
    @NotNull
    public CommandRegistration register(@NotNull Plugin owner, @NotNull CommandSpec spec) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(spec, "spec");

        if (debug) {
            logger.info("[Commands] Registering command /" + spec.name() + " for " + owner.getName());
        }

        // Build the root node from spec
        RootNode root = buildRootFromSpec(owner, spec);

        // Check for conflicts
        RootNode existing = graph.getRoot(root.name());
        if (existing != null && !existing.owner().equals(owner)) {
            logger.warning("[Commands] Command /" + root.name() + " already registered by "
                    + existing.owner().getName() + ", will use prefixed version");
        }

        // Register in graph
        graph.register(root);

        // Bind to Bukkit
        binder.bind(root);

        if (debug) {
            logger.info("[Commands] Registered /" + root.name() + " with "
                    + countSubcommands(root) + " subcommands");
        }

        return new CommandRegistration(owner, root.name(), root.aliases());
    }

    /**
     * Registers a pre-built root node.
     *
     * <p>This method is used by the annotation processor after building
     * the command tree from annotations.</p>
     *
     * @param root The root node to register
     * @return Registration result
     */
    @NotNull
    public CommandRegistration register(@NotNull RootNode root) {
        Objects.requireNonNull(root, "root");

        if (debug) {
            logger.info("[Commands] Registering command /" + root.name() + " for " + root.owner().getName());
        }

        // Check for conflicts
        RootNode existing = graph.getRoot(root.name());
        if (existing != null && !existing.owner().equals(root.owner())) {
            logger.warning("[Commands] Command /" + root.name() + " already registered by "
                    + existing.owner().getName());
        }

        // Register in graph
        graph.register(root);

        // Bind to Bukkit
        binder.bind(root);

        if (debug) {
            logger.info("[Commands] Registered /" + root.name() + " with "
                    + countSubcommands(root) + " subcommands");
        }

        return new CommandRegistration(root.owner(), root.name(), root.aliases());
    }

    /**
     * Unregisters a command by name.
     *
     * @param name The command name
     * @return true if the command was found and unregistered
     */
    public boolean unregister(@NotNull String name) {
        Objects.requireNonNull(name, "name");

        RootNode removed = graph.unregister(name);
        if (removed == null) {
            return false;
        }

        binder.unbind(removed);

        if (debug) {
            logger.info("[Commands] Unregistered /" + name);
        }

        return true;
    }

    /**
     * Unregisters all commands owned by a plugin.
     *
     * @param owner The owner plugin
     * @return Number of commands unregistered
     */
    public int unregisterAll(@NotNull Plugin owner) {
        Objects.requireNonNull(owner, "owner");

        List<RootNode> removed = graph.unregisterAll(owner);
        for (RootNode root : removed) {
            binder.unbind(root);
        }

        if (debug && !removed.isEmpty()) {
            logger.info("[Commands] Unregistered " + removed.size() + " commands from " + owner.getName());
        }

        return removed.size();
    }

    /**
     * Unregisters all commands (typically on disable).
     */
    public void unregisterAll() {
        try {
            List<RootNode> allRoots = new ArrayList<>(graph.roots());
            for (RootNode root : allRoots) {
                binder.unbind(root);
            }
            graph.clear();

            if (debug) {
                logger.info("[Commands] Unregistered all commands (" + allRoots.size() + " total)");
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "[Commands] Error during unregisterAll", e);
        }
    }

    /**
     * Gets the command graph for resolution.
     *
     * @return The command graph
     */
    @NotNull
    public CommandGraph graph() {
        return graph;
    }

    /**
     * Checks if a command is registered.
     *
     * @param nameOrAlias Name or alias to check
     * @return true if registered
     */
    public boolean isRegistered(@NotNull String nameOrAlias) {
        return graph.contains(nameOrAlias);
    }

    /**
     * Gets the number of registered commands.
     */
    public int count() {
        return graph.size();
    }

    // ========== Private Helpers ==========

    private RootNode buildRootFromSpec(Plugin owner, CommandSpec spec) {
        // Build children recursively
        Map<String, SubNode> children = new LinkedHashMap<>();
        for (CommandSpec.SubcommandSpec subSpec : spec.subcommands()) {
            SubNode child = SubNode.fromSpec(subSpec, subSpec.executor());
            children.put(child.name(), child);
        }

        // Build executor if present
        CommandNode.CompiledExecutor executor = spec.executor() != null
                ? CommandNode.CompiledExecutor.fromLambda(spec.executor())
                : null;

        return RootNode.builder(owner, spec.name())
                .aliases(spec.aliases())
                .description(spec.description())
                .usage(spec.usage())
                .permission(spec.permission())
                .playerOnly(spec.isPlayerOnly())
                .children(children.values())
                .arguments(spec.arguments())
                .flags(spec.flags())
                .executor(executor)
                .build();
    }

    private int countSubcommands(RootNode root) {
        int count = 0;
        for (SubNode child : root.children().values()) {
            count += 1 + countSubcommandsRecursive(child);
        }
        return count;
    }

    private int countSubcommandsRecursive(SubNode node) {
        int count = 0;
        for (SubNode child : node.children().values()) {
            count += 1 + countSubcommandsRecursive(child);
        }
        return count;
    }
}
