package com.afterlands.core.commands.binding;

import com.afterlands.core.commands.completion.CompletionCache;
import com.afterlands.core.commands.completion.TabCompleter;
import com.afterlands.core.commands.execution.CommandContext;
import com.afterlands.core.commands.execution.ParsedArgs;
import com.afterlands.core.commands.execution.ParsedFlags;
import com.afterlands.core.commands.messages.MessageFacade;
import com.afterlands.core.commands.parser.ArgumentParser;
import com.afterlands.core.commands.parser.ArgumentTypeRegistry;
import com.afterlands.core.commands.registry.CommandGraph;
import com.afterlands.core.commands.registry.nodes.CommandNode;
import com.afterlands.core.commands.registry.nodes.RootNode;
import com.afterlands.core.commands.registry.nodes.SubNode;
import com.afterlands.core.concurrent.SchedulerService;
import com.afterlands.core.metrics.MetricsService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Default implementation of CommandDispatcher.
 *
 * <p>This dispatcher handles:</p>
 * <ul>
 *   <li>Subcommand resolution via the command graph</li>
 *   <li>Basic argument parsing (advanced parsing in Phase 2)</li>
 *   <li>Permission checking at each level</li>
 *   <li>Player-only command enforcement</li>
 *   <li>Help generation</li>
 *   <li>Error messaging</li>
 *   <li>Metrics recording</li>
 * </ul>
 *
 * <p>Performance: This implementation is designed for minimal overhead:</p>
 * <ul>
 *   <li>No reflection in execution path</li>
 *   <li>O(d) subcommand resolution where d is depth</li>
 *   <li>Minimal object allocation</li>
 * </ul>
 */
public final class DefaultCommandDispatcher implements CommandDispatcher {

    private static final String METRIC_EXEC_TOTAL = "acore.cmd.exec.total";
    private static final String METRIC_EXEC_FAIL = "acore.cmd.exec.fail";
    private static final String METRIC_EXEC_NO_PERM = "acore.cmd.exec.noPerm";
    private static final String METRIC_EXEC_MS = "acore.cmd.exec.ms";
    private static final String METRIC_TAB_TOTAL = "acore.cmd.tab.total";
    private static final String METRIC_TAB_MS = "acore.cmd.complete.ms";

    private final RootNode root;
    private final MessageFacade messages;
    private final SchedulerService scheduler;
    private final MetricsService metrics;
    private final Logger logger;
    private final boolean debug;
    private final ArgumentParser argumentParser;
    private final TabCompleter tabCompleter;

    /**
     * Creates a new DefaultCommandDispatcher.
     *
     * @param root            The root command node
     * @param messages        Message facade for sending messages
     * @param scheduler       Scheduler service for async operations
     * @param metrics         Metrics service for recording stats
     * @param logger          Logger for debug output
     * @param debug           Whether debug mode is enabled
     * @param argumentParser  Argument parser for typed parsing
     * @param tabCompleter    Tab completer for suggestions
     */
    public DefaultCommandDispatcher(@NotNull RootNode root,
                                    @NotNull MessageFacade messages,
                                    @NotNull SchedulerService scheduler,
                                    @NotNull MetricsService metrics,
                                    @NotNull Logger logger,
                                    boolean debug,
                                    @NotNull ArgumentParser argumentParser,
                                    @NotNull TabCompleter tabCompleter) {
        this.root = Objects.requireNonNull(root, "root");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.debug = debug;
        this.argumentParser = Objects.requireNonNull(argumentParser, "argumentParser");
        this.tabCompleter = Objects.requireNonNull(tabCompleter, "tabCompleter");
    }

    @Override
    public boolean dispatch(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        long startNanos = System.nanoTime();
        metrics.increment(METRIC_EXEC_TOTAL);

        try {
            return doDispatch(sender, label, args);
        } catch (Exception e) {
            metrics.increment(METRIC_EXEC_FAIL);
            logger.log(Level.SEVERE, "[Commands] Error executing /" + label, e);
            messages.send(sender, "errors.internal");
            return true;
        } finally {
            long elapsed = System.nanoTime() - startNanos;
            metrics.recordTime(METRIC_EXEC_MS, elapsed);

            if (debug && elapsed > 500_000) { // > 0.5ms
                logger.warning("[Commands] Slow command execution: /" + label + " took "
                        + (elapsed / 1_000_000.0) + "ms");
            }
        }
    }

    private boolean doDispatch(CommandSender sender, String label, String[] args) {
        // Resolve the target node
        ResolutionResult resolution = resolve(args);
        CommandNode targetNode = resolution.node();
        List<String> remaining = resolution.remaining();
        String fullPath = buildPath(label, args, remaining.size());

        // Check root permission first
        if (root.permission() != null && !sender.hasPermission(root.permission())) {
            metrics.increment(METRIC_EXEC_NO_PERM);
            messages.send(sender, "errors.no-permission");
            return true;
        }

        // Check target node permission
        if (targetNode != root && targetNode.permission() != null
                && !sender.hasPermission(targetNode.permission())) {
            metrics.increment(METRIC_EXEC_NO_PERM);
            messages.send(sender, "errors.no-permission");
            return true;
        }

        // Check player-only
        if (targetNode.playerOnly() && !(sender instanceof Player)) {
            messages.send(sender, "errors.player-only");
            return true;
        }

        // Handle help subcommand
        if (remaining.size() == 1 && "help".equalsIgnoreCase(remaining.get(0))) {
            sendHelp(sender, targetNode, fullPath);
            return true;
        }

        // Check for --help flag
        if (hasHelpFlag(remaining)) {
            sendHelp(sender, targetNode, fullPath);
            return true;
        }

        // If node has children but no executor, and we have remaining args that don't match a child
        if (!remaining.isEmpty() && targetNode.hasChildren() && !targetNode.isExecutable()) {
            // Unknown subcommand
            messages.send(sender, "commands.unknown-subcommand",
                    "subcommand", remaining.get(0),
                    "command", fullPath);
            sendUsageHint(sender, targetNode, fullPath);
            return true;
        }

        // If node is not executable
        if (!targetNode.isExecutable()) {
            if (targetNode.hasChildren()) {
                // Show help for commands with subcommands
                sendHelp(sender, targetNode, fullPath);
            } else {
                // No executor and no children - shouldn't happen, but handle gracefully
                messages.send(sender, "commands.usage",
                        "usage", targetNode.generateUsage(fullPath));
            }
            return true;
        }

        // Build context and execute
        try {
            CommandContext context = buildContext(sender, label, remaining);
            targetNode.executor().execute(context);
        } catch (Throwable t) {
            metrics.increment(METRIC_EXEC_FAIL);
            if (t instanceof IllegalArgumentException) {
                // User error (bad arguments)
                messages.sendRaw(sender, "&c" + t.getMessage());
            } else {
                logger.log(Level.SEVERE, "[Commands] Error in executor for /" + fullPath, t);
                messages.send(sender, "errors.internal");
            }
        }

        return true;
    }

    @NotNull
    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        long startNanos = System.nanoTime();
        metrics.increment(METRIC_TAB_TOTAL);

        try {
            // Use advanced tab completer
            return tabCompleter.complete(sender, label, args);
        } finally {
            long elapsed = System.nanoTime() - startNanos;
            metrics.recordTime(METRIC_TAB_MS, elapsed);

            if (debug && elapsed > 1_000_000) { // > 1ms
                logger.warning("[Commands] Slow tab completion: /" + label + " took "
                        + (elapsed / 1_000_000.0) + "ms");
            }
        }
    }

    // ========== Helper Methods ==========

    private ResolutionResult resolve(String[] args) {
        if (args.length == 0) {
            return new ResolutionResult(root, List.of());
        }

        CommandNode current = root;
        int consumed = 0;

        for (int i = 0; i < args.length; i++) {
            SubNode child = current.child(args[i]);
            if (child == null) {
                break;
            }
            current = child;
            consumed++;
        }

        List<String> remaining = consumed < args.length
                ? Arrays.asList(args).subList(consumed, args.length)
                : List.of();

        return new ResolutionResult(current, remaining);
    }

    private record ResolutionResult(CommandNode node, List<String> remaining) {}

    private String buildPath(String label, String[] args, int remainingCount) {
        if (args.length == 0 || args.length == remainingCount) {
            return label;
        }
        int consumed = args.length - remainingCount;
        StringBuilder sb = new StringBuilder(label);
        for (int i = 0; i < consumed; i++) {
            sb.append(" ").append(args[i]);
        }
        return sb.toString();
    }

    private boolean hasHelpFlag(List<String> args) {
        for (String arg : args) {
            if ("--help".equalsIgnoreCase(arg) || "-h".equalsIgnoreCase(arg) || "-?".equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private void sendHelp(CommandSender sender, CommandNode node, String path) {
        messages.send(sender, "commands.help.header", "command", "/" + path);

        if (node.description() != null) {
            messages.sendRaw(sender, "&7" + node.description());
        }

        // Show usage
        messages.sendRaw(sender, "&eUsage: &f" + node.generateUsage(path));

        // List subcommands if any
        if (node.hasChildren()) {
            messages.sendRaw(sender, "");
            messages.sendRaw(sender, "&eSubcommands:");

            List<SubNode> visibleChildren = node.children().values().stream()
                    .filter(child -> child.permission() == null || sender.hasPermission(child.permission()))
                    .sorted(Comparator.comparing(SubNode::name))
                    .collect(Collectors.toList());

            for (SubNode child : visibleChildren) {
                String desc = child.description() != null ? " &7- " + child.description() : "";
                messages.sendRaw(sender, " &f/" + path + " " + child.name() + desc);
            }
        }
    }

    private void sendUsageHint(CommandSender sender, CommandNode node, String path) {
        messages.sendRaw(sender, "&7Use &f/" + path + " help &7for a list of subcommands.");
    }

    private CommandContext buildContext(CommandSender sender, String label, List<String> remaining) {
        // For now, basic argument parsing
        // Full typed parsing will be integrated when needed
        ParsedArgs.Builder argsBuilder = ParsedArgs.builder();
        argsBuilder.addAllPositional(remaining);

        return CommandContext.builder()
                .owner(root.owner())
                .sender(sender)
                .label(label)
                .args(argsBuilder.build())
                .flags(ParsedFlags.empty())
                .messages(messages)
                .scheduler(scheduler)
                .metrics(metrics)
                .build();
    }

    /**
     * Factory for creating DefaultCommandDispatcher instances.
     */
    public static final class Factory implements BukkitCommandBinder.CommandDispatcherFactory {
        private final MessageFacade messages;
        private final SchedulerService scheduler;
        private final MetricsService metrics;
        private final Logger logger;
        private final boolean debug;
        private final ArgumentParser argumentParser;
        private final CommandGraph graph;
        private final CompletionCache completionCache;

        public Factory(@NotNull MessageFacade messages,
                       @NotNull SchedulerService scheduler,
                       @NotNull MetricsService metrics,
                       @NotNull Logger logger,
                       boolean debug,
                       @NotNull CommandGraph graph) {
            this.messages = messages;
            this.scheduler = scheduler;
            this.metrics = metrics;
            this.logger = logger;
            this.debug = debug;
            this.graph = graph;
            this.argumentParser = new ArgumentParser(ArgumentTypeRegistry.instance());
            this.completionCache = CompletionCache.builder()
                    .ttl(2, java.util.concurrent.TimeUnit.SECONDS)
                    .maxSize(1000)
                    .build();
        }

        @NotNull
        @Override
        public CommandDispatcher create(@NotNull RootNode root) {
            TabCompleter tabCompleter = new TabCompleter(
                    graph,
                    ArgumentTypeRegistry.instance(),
                    completionCache,
                    debug
            );

            return new DefaultCommandDispatcher(
                    root, messages, scheduler, metrics, logger, debug,
                    argumentParser, tabCompleter
            );
        }
    }
}
