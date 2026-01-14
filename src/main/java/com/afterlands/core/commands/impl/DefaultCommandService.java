package com.afterlands.core.commands.impl;

import com.afterlands.core.commands.CommandRegistration;
import com.afterlands.core.commands.CommandService;
import com.afterlands.core.commands.CommandSpec;
import com.afterlands.core.commands.annotations.Command;
import com.afterlands.core.commands.authoring.AnnotationProcessor;
import com.afterlands.core.commands.binding.BukkitCommandBinder;
import com.afterlands.core.commands.binding.DefaultCommandDispatcher;
import com.afterlands.core.commands.messages.MessageFacade;
import com.afterlands.core.commands.parser.ArgumentTypeRegistry;
import com.afterlands.core.commands.registry.CommandGraph;
import com.afterlands.core.commands.registry.CommandRegistry;
import com.afterlands.core.commands.registry.nodes.RootNode;
import com.afterlands.core.concurrent.SchedulerService;
import com.afterlands.core.config.ConfigService;
import com.afterlands.core.config.MessageService;
import com.afterlands.core.metrics.MetricsService;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Default implementation of CommandService.
 *
 * <p>This implementation provides:</p>
 * <ul>
 *   <li>Annotation-based command registration</li>
 *   <li>DSL/Builder command registration</li>
 *   <li>Dynamic Bukkit CommandMap integration</li>
 *   <li>Plugin-based lifecycle management</li>
 *   <li>Help/usage generation</li>
 *   <li>Tab completion</li>
 *   <li>Metrics and observability</li>
 * </ul>
 *
 * <p>Performance characteristics:</p>
 * <ul>
 *   <li>Reflection only at registration time (compiled to MethodHandles)</li>
 *   <li>O(1) command lookup</li>
 *   <li>O(d) subcommand resolution where d is depth</li>
 *   <li>Minimal allocation in hot path</li>
 * </ul>
 */
public final class DefaultCommandService implements CommandService {

    private final Plugin corePlugin;
    private final Logger logger;
    private final ConfigService config;
    private final MessageService messageService;
    private final SchedulerService scheduler;
    private final MetricsService metrics;
    private final boolean debug;

    private final MessageFacade messageFacade;
    private CommandRegistry registry; // Not final due to initialization order
    private final BukkitCommandBinder binder;
    private final ArgumentTypeRegistry typeRegistry;
    private final AnnotationProcessor annotationProcessor;

    /**
     * Creates a new DefaultCommandService.
     *
     * @param corePlugin The AfterCore plugin
     * @param config     Config service
     * @param messages   Message service
     * @param scheduler  Scheduler service
     * @param metrics    Metrics service
     * @param debug      Whether debug mode is enabled
     */
    public DefaultCommandService(@NotNull Plugin corePlugin,
                                  @NotNull ConfigService config,
                                  @NotNull MessageService messages,
                                  @NotNull SchedulerService scheduler,
                                  @NotNull MetricsService metrics,
                                  boolean debug) {
        this.corePlugin = Objects.requireNonNull(corePlugin, "corePlugin");
        this.logger = corePlugin.getLogger();
        this.config = Objects.requireNonNull(config, "config");
        this.messageService = Objects.requireNonNull(messages, "messages");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.debug = debug;

        // Initialize message facade
        this.messageFacade = new MessageFacade(corePlugin, config, messages, debug);

        // Initialize type registry
        this.typeRegistry = ArgumentTypeRegistry.instance();

        // Initialize registry without binder first
        this.registry = new CommandRegistry(corePlugin, null, debug);

        // Initialize dispatcher factory with graph access
        DefaultCommandDispatcher.Factory dispatcherFactory = new DefaultCommandDispatcher.Factory(
                messageFacade, scheduler, metrics, logger, debug, registry.graph()
        );

        // Initialize binder
        this.binder = new BukkitCommandBinder(corePlugin, dispatcherFactory, debug);

        // Reinitialize registry with binder (CommandRegistry will need refactoring to accept binder post-construction)
        // For now, create a new instance
        this.registry = new CommandRegistry(corePlugin, binder, debug);

        // Update factory's graph reference to use the new registry's graph
        // This is safe because the graph is the same instance
        DefaultCommandDispatcher.Factory newFactory = new DefaultCommandDispatcher.Factory(
                messageFacade, scheduler, metrics, logger, debug, registry.graph()
        );

        // Update binder's factory
        try {
            java.lang.reflect.Field factoryField = BukkitCommandBinder.class.getDeclaredField("dispatcherFactory");
            factoryField.setAccessible(true);
            factoryField.set(binder, newFactory);
        } catch (Exception e) {
            if (debug) {
                logger.warning("[Commands] Could not update binder factory: " + e.getMessage());
            }
        }

        // Initialize annotation processor
        this.annotationProcessor = new AnnotationProcessor(corePlugin, typeRegistry, logger, debug);

        if (debug) {
            logger.info("[Commands] CommandService initialized");
            logger.info("[Commands] Registered " + typeRegistry.size() + " argument types");
            if (!binder.isCommandMapAvailable()) {
                logger.warning("[Commands] CommandMap not available - dynamic registration disabled");
            }
        }
    }

    @Override
    public void register(@NotNull Object commandHandler) {
        // Backward compatible: use AfterCore as owner
        register(corePlugin, commandHandler);
    }

    @Override
    @NotNull
    public CommandRegistration register(@NotNull Plugin owner, @NotNull Object commandHandler) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(commandHandler, "commandHandler");

        Class<?> handlerClass = commandHandler.getClass();
        Command cmdAnnotation = handlerClass.getAnnotation(Command.class);

        if (cmdAnnotation == null) {
            throw new IllegalArgumentException("Handler class must be annotated with @Command: "
                    + handlerClass.getName());
        }

        if (debug) {
            logger.info("[Commands] Processing annotated handler: " + handlerClass.getName());
        }

        try {
            // Create processor with correct owner
            AnnotationProcessor processor = new AnnotationProcessor(owner, typeRegistry, logger, debug);
            RootNode root = processor.process(commandHandler);
            return registry.register(root);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[Commands] Failed to register handler: " + handlerClass.getName(), e);
            throw new RuntimeException("Failed to register command handler", e);
        }
    }

    @Override
    @NotNull
    public CommandRegistration register(@NotNull Plugin owner, @NotNull CommandSpec spec) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(spec, "spec");

        return registry.register(owner, spec);
    }

    @Override
    public void unregisterAll() {
        registry.unregisterAll();
    }

    @Override
    public int unregisterAll(@NotNull Plugin owner) {
        Objects.requireNonNull(owner, "owner");
        return registry.unregisterAll(owner);
    }

    @Override
    @NotNull
    public CommandGraph graph() {
        return registry.graph();
    }

    @Override
    @NotNull
    public MessageFacade messages() {
        return messageFacade;
    }

    @Override
    public boolean isRegistered(@NotNull String nameOrAlias) {
        return registry.isRegistered(nameOrAlias);
    }
}
