package com.afterlands.core.commands.binding;

import com.afterlands.core.commands.registry.nodes.RootNode;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles binding commands to the Bukkit CommandMap.
 *
 * <p>This class manages the low-level integration with Bukkit's command system,
 * including:</p>
 * <ul>
 *   <li>Dynamic registration without plugin.yml</li>
 *   <li>Alias management and conflict resolution</li>
 *   <li>Clean unregistration with knownCommands cleanup</li>
 *   <li>Graceful degradation if CommandMap is inaccessible</li>
 * </ul>
 *
 * <p>Conflict Policy:</p>
 * <ul>
 *   <li>If a command name is taken, register as pluginName:command</li>
 *   <li>Log warnings for conflicts but don't fail</li>
 *   <li>Always register the prefixed version as fallback</li>
 * </ul>
 *
 * <p>Thread Safety: This class is thread-safe. All CommandMap access is synchronized.</p>
 */
public final class BukkitCommandBinder {

    private final Plugin corePlugin;
    private final Logger logger;
    private final boolean debug;

    // Cached CommandMap reference
    @Nullable
    private CommandMap commandMap;

    // Cached knownCommands map for unregistration
    @Nullable
    private Map<String, Command> knownCommands;

    // Track our registered commands for cleanup
    private final Map<String, DynamicRootCommand> boundCommands = new ConcurrentHashMap<>();

    // Factory for creating command dispatchers
    private final CommandDispatcherFactory dispatcherFactory;

    // Whether CommandMap access is available
    private volatile boolean commandMapAvailable = true;

    /**
     * Creates a new BukkitCommandBinder.
     *
     * @param corePlugin        The AfterCore plugin
     * @param dispatcherFactory Factory for creating command dispatchers
     * @param debug             Whether debug logging is enabled
     */
    public BukkitCommandBinder(@NotNull Plugin corePlugin,
                                @NotNull CommandDispatcherFactory dispatcherFactory,
                                boolean debug) {
        this.corePlugin = Objects.requireNonNull(corePlugin, "corePlugin");
        this.logger = corePlugin.getLogger();
        this.dispatcherFactory = Objects.requireNonNull(dispatcherFactory, "dispatcherFactory");
        this.debug = debug;

        // Initialize CommandMap access
        initializeCommandMap();
    }

    /**
     * Binds a root command to Bukkit.
     *
     * @param root The root node to bind
     */
    public void bind(@NotNull RootNode root) {
        Objects.requireNonNull(root, "root");

        if (!commandMapAvailable) {
            logger.warning("[Commands] CommandMap not available, cannot register /" + root.name());
            return;
        }

        synchronized (this) {
            // Create the dynamic command
            DynamicRootCommand command = new DynamicRootCommand(
                    root.name(),
                    root.description() != null ? root.description() : "",
                    root.usage() != null ? root.usage() : "/" + root.name(),
                    root.aliases().stream().toList(),
                    root.owner(),
                    dispatcherFactory.create(root)
            );

            // Register primary name
            boolean primaryRegistered = registerCommand(root.name(), command, root.owner());

            // Always register prefixed version as fallback
            String prefixed = root.owner().getName().toLowerCase() + ":" + root.name();
            registerCommand(prefixed, command, root.owner());

            // Register aliases
            for (String alias : root.aliases()) {
                if (!registerCommand(alias, command, root.owner())) {
                    // Try prefixed alias
                    registerCommand(root.owner().getName().toLowerCase() + ":" + alias, command, root.owner());
                }
            }

            // Track for cleanup
            boundCommands.put(root.name(), command);

            if (debug) {
                logger.info("[Commands] Bound /" + root.name()
                        + (primaryRegistered ? "" : " (as " + prefixed + ")")
                        + " with " + root.aliases().size() + " aliases");
            }
        }
    }

    /**
     * Unbinds a root command from Bukkit.
     *
     * @param root The root node to unbind
     */
    public void unbind(@NotNull RootNode root) {
        Objects.requireNonNull(root, "root");

        synchronized (this) {
            DynamicRootCommand command = boundCommands.remove(root.name());
            if (command == null) {
                return;
            }

            // Unregister from CommandMap
            unregisterCommand(root.name());
            unregisterCommand(root.owner().getName().toLowerCase() + ":" + root.name());

            // Unregister aliases
            for (String alias : root.aliases()) {
                unregisterCommand(alias);
                unregisterCommand(root.owner().getName().toLowerCase() + ":" + alias);
            }

            // Unregister the command itself
            command.unregister(commandMap);

            if (debug) {
                logger.info("[Commands] Unbound /" + root.name());
            }
        }
    }

    /**
     * Unbinds all commands.
     */
    public void unbindAll() {
        synchronized (this) {
            for (String name : boundCommands.keySet()) {
                DynamicRootCommand command = boundCommands.get(name);
                if (command != null) {
                    // Remove from known commands
                    if (knownCommands != null) {
                        knownCommands.values().removeIf(cmd -> cmd == command);
                    }
                    command.unregister(commandMap);
                }
            }
            boundCommands.clear();

            if (debug) {
                logger.info("[Commands] Unbound all commands");
            }
        }
    }

    /**
     * Checks if CommandMap access is available.
     */
    public boolean isCommandMapAvailable() {
        return commandMapAvailable;
    }

    // ========== Private Helpers ==========

    @SuppressWarnings("unchecked")
    private void initializeCommandMap() {
        try {
            // Get the CommandMap from Bukkit
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            commandMap = (CommandMap) commandMapField.get(Bukkit.getServer());

            // Get knownCommands for unregistration
            if (commandMap instanceof SimpleCommandMap) {
                Field knownCommandsField = SimpleCommandMap.class.getDeclaredField("knownCommands");
                knownCommandsField.setAccessible(true);
                knownCommands = (Map<String, Command>) knownCommandsField.get(commandMap);
            }

            if (debug) {
                logger.info("[Commands] CommandMap access initialized successfully");
            }

        } catch (NoSuchFieldException | IllegalAccessException | SecurityException e) {
            commandMapAvailable = false;
            logger.log(Level.WARNING, "[Commands] Could not access CommandMap. Dynamic command registration disabled.", e);
        }
    }

    private boolean registerCommand(String name, Command command, Plugin owner) {
        if (commandMap == null || knownCommands == null) {
            return false;
        }

        String lowerName = name.toLowerCase();

        // Check for existing command
        Command existing = knownCommands.get(lowerName);
        if (existing != null && !(existing instanceof DynamicRootCommand)) {
            // Another plugin/vanilla command exists
            if (debug) {
                logger.info("[Commands] Command '" + name + "' already exists, skipping");
            }
            return false;
        }

        // Register the command
        knownCommands.put(lowerName, command);
        return true;
    }

    private void unregisterCommand(String name) {
        if (knownCommands == null) {
            return;
        }

        String lowerName = name.toLowerCase();
        Command existing = knownCommands.get(lowerName);

        // Only remove if it's our command
        if (existing instanceof DynamicRootCommand) {
            knownCommands.remove(lowerName);
        }
    }

    /**
     * Factory interface for creating command dispatchers.
     */
    @FunctionalInterface
    public interface CommandDispatcherFactory {
        /**
         * Creates a dispatcher for the given root node.
         *
         * @param root The root node
         * @return A CommandDispatcher implementation
         */
        @NotNull
        CommandDispatcher create(@NotNull RootNode root);
    }
}
