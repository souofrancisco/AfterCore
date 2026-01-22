package com.afterlands.core.commands.binding;

import com.afterlands.core.commands.completion.CompletionCache;
import com.afterlands.core.commands.completion.TabCompleter;
import com.afterlands.core.commands.execution.CommandContext;
import com.afterlands.core.commands.execution.ParsedArgs;
import com.afterlands.core.commands.execution.ParsedFlags;
import com.afterlands.core.commands.help.HelpFormatter;
import com.afterlands.core.commands.messages.MessageFacade;
import com.afterlands.core.commands.parser.ArgumentParser;
import com.afterlands.core.commands.parser.ArgumentTypeRegistry;
import com.afterlands.core.commands.registry.CommandGraph;
import com.afterlands.core.commands.registry.nodes.CommandNode;
import com.afterlands.core.commands.registry.nodes.RootNode;
import com.afterlands.core.commands.registry.nodes.SubNode;
import com.afterlands.core.concurrent.SchedulerService;
import com.afterlands.core.metrics.MetricsService;
import com.afterlands.core.util.ratelimit.CooldownService;
import com.afterlands.core.util.ratelimit.RateLimiter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Default implementation of CommandDispatcher.
 *
 * <p>
 * This dispatcher handles:
 * </p>
 * <ul>
 * <li>Subcommand resolution via the command graph</li>
 * <li>Basic argument parsing (advanced parsing in Phase 2)</li>
 * <li>Permission checking at each level</li>
 * <li>Player-only command enforcement</li>
 * <li>Help generation</li>
 * <li>Error messaging</li>
 * <li>Metrics recording</li>
 * </ul>
 *
 * <p>
 * Performance: This implementation is designed for minimal overhead:
 * </p>
 * <ul>
 * <li>No reflection in execution path</li>
 * <li>O(d) subcommand resolution where d is depth</li>
 * <li>Minimal object allocation</li>
 * </ul>
 */
public final class DefaultCommandDispatcher implements CommandDispatcher {

    private static final String METRIC_EXEC_TOTAL = "acore.cmd.exec.total";
    private static final String METRIC_EXEC_FAIL = "acore.cmd.exec.fail";
    private static final String METRIC_EXEC_NO_PERM = "acore.cmd.exec.noPerm";
    private static final String METRIC_EXEC_COOLDOWN = "acore.cmd.exec.cooldown";
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
    private final HelpFormatter helpFormatter;
    private final CooldownService cooldownService;

    /**
     * Creates a new DefaultCommandDispatcher.
     *
     * @param root           The root command node
     * @param messages       Message facade for sending messages
     * @param scheduler      Scheduler service for async operations
     * @param metrics        Metrics service for recording stats
     * @param logger         Logger for debug output
     * @param debug          Whether debug mode is enabled
     * @param argumentParser Argument parser for typed parsing
     * @param tabCompleter   Tab completer for suggestions
     * @param helpFormatter  Help formatter for paginated help
     */
    public DefaultCommandDispatcher(@NotNull RootNode root,
            @NotNull MessageFacade messages,
            @NotNull SchedulerService scheduler,
            @NotNull MetricsService metrics,
            @NotNull Logger logger,
            boolean debug,
            @NotNull ArgumentParser argumentParser,
            @NotNull TabCompleter tabCompleter,
            @NotNull HelpFormatter helpFormatter,
            @NotNull CooldownService cooldownService) {
        this.root = Objects.requireNonNull(root, "root");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.debug = debug;
        this.argumentParser = Objects.requireNonNull(argumentParser, "argumentParser");
        this.tabCompleter = Objects.requireNonNull(tabCompleter, "tabCompleter");
        this.helpFormatter = Objects.requireNonNull(helpFormatter, "helpFormatter");
        this.cooldownService = Objects.requireNonNull(cooldownService, "cooldownService");
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

        // Handle help subcommand with optional page number
        if (!remaining.isEmpty() && "help".equalsIgnoreCase(remaining.get(0))) {
            int page = 1;
            if (remaining.size() >= 2) {
                try {
                    page = Integer.parseInt(remaining.get(1));
                } catch (NumberFormatException ignored) {
                }
            }
            // If target node has no children, show help for root (the command that has
            // subcommands)
            // Use the root command name as path, not the full resolved path
            String helpPath = targetNode.hasChildren() ? fullPath : label;
            sendHelp(sender, helpPath, page);
            return true;
        }

        // Check for --help flag
        if (hasHelpFlag(remaining)) {
            sendHelp(sender, fullPath, 1);
            return true;
        }

        // If node has children but no executor, and we have remaining args that don't
        // match a child
        if (!remaining.isEmpty() && targetNode.hasChildren() && !targetNode.isExecutable()) {
            // Unknown subcommand
            messages.send(sender, "commands.unknown-subcommand",
                    "subcommand", remaining.get(0),
                    "command", fullPath);
            sendUsageHint(sender, targetNode, fullPath);
            return true;
        }

        // If node is not executable, show help or usage
        if (!targetNode.isExecutable()) {
            if (targetNode.hasChildren()) {
                // Show help for commands with subcommands
                sendHelp(sender, fullPath, 1);
            } else {
                // No executor and no children - shouldn't happen, but handle gracefully
                messages.send(sender, "commands.usage",
                        "usage", targetNode.generateUsage(fullPath));
            }
            return true;
        }

        // Check for group help request (e.g., /cmd placement help)
        // This happens when we're at root and remaining has format [<group>, "help",
        // [page]]
        if (targetNode == root && remaining.size() >= 2 && "help".equalsIgnoreCase(remaining.get(1))) {
            String potentialGroup = remaining.get(0).toLowerCase(java.util.Locale.ROOT);
            if (root.groups().containsKey(potentialGroup)) {
                int page = 1;
                if (remaining.size() >= 3) {
                    try {
                        page = Integer.parseInt(remaining.get(2));
                    } catch (NumberFormatException ignored) {
                    }
                }
                // Send filtered help for this group
                helpFormatter.sendGroupHelp(sender, root, label, potentialGroup, page);
                return true;
            }
        }

        // Node is executable - proceed to execute (even if it has children)

        // Check cooldown for subcommands (only affects players)
        if (sender instanceof Player player && targetNode instanceof SubNode subNode) {
            Duration cd = subNode.cooldown();
            if (cd != null && !cd.isZero()) {
                // Check bypass permission
                String bypassPerm = subNode.cooldownBypassPermission();
                if (bypassPerm == null || !player.hasPermission(bypassPerm)) {
                    String actionKey = "command:" + fullPath;
                    RateLimiter.AcquireResult result = cooldownService.tryAcquireWithRemaining(player, actionKey, cd);
                    if (!result.allowed()) {
                        metrics.increment(METRIC_EXEC_COOLDOWN);
                        String remainingTime = CooldownService.formatDuration(result.remaining());
                        String msgKey = subNode.cooldownMessage();
                        if (msgKey == null || msgKey.isEmpty()) {
                            msgKey = "commands.cooldown";
                        }
                        messages.send(sender, msgKey, "remaining", remainingTime, "command", fullPath);
                        return true;
                    }
                }
            }
        }

        try {
            CommandContext context = buildContext(sender, label, remaining, targetNode, fullPath);
            targetNode.executor().execute(context);
        } catch (CommandParseException cpe) {
            // Structured parse error - use translatable messages
            var parseEx = cpe.parseException();
            String usage = targetNode.generateUsage(fullPath);

            switch (parseEx.errorType()) {
                case MISSING_REQUIRED -> {
                    messages.send(sender, "commands.errors.missing-argument",
                            "argument", parseEx.argumentName());
                    messages.send(sender, "commands.usage", "usage", usage);
                }
                case INVALID_VALUE -> {
                    String reason = parseEx.getMessage();
                    String value = extractValue(reason);

                    // Check for specific error message keys
                    if (reason != null && reason.contains("player-not-online")) {
                        messages.send(sender, "commands.errors.player-not-online", "player", value);
                    } else if (reason != null && reason.contains("player-never-joined")) {
                        messages.send(sender, "commands.errors.player-never-joined", "player", value);
                    } else if (reason != null && reason.contains("invalid-number")) {
                        messages.send(sender, "commands.errors.invalid-number", "value", value);
                    } else if (reason != null && reason.startsWith("number-out-of-range:")) {
                        // Format: number-out-of-range:min:max
                        String[] parts = reason.split(":");
                        String min = parts.length > 1 ? parts[1] : "?";
                        String max = parts.length > 2 ? parts[2] : "?";
                        messages.send(sender, "commands.errors.number-out-of-range", "min", min, "max", max);
                    } else if (reason != null && reason.contains("world-not-found")) {
                        messages.send(sender, "commands.errors.world-not-found", "world", value);
                    } else if (reason != null && reason.startsWith("invalid-enum:")) {
                        // Format: invalid-enum:option1, option2, option3
                        String options = reason.substring("invalid-enum:".length());
                        messages.send(sender, "commands.errors.invalid-enum", "value", value, "options", options);
                    } else {
                        messages.send(sender, "commands.errors.invalid-argument",
                                "argument", parseEx.argumentName(),
                                "reason", reason);
                    }
                    messages.send(sender, "commands.usage", "usage", usage);
                }
                case TOO_MANY_ARGS -> {
                    messages.send(sender, "commands.errors.too-many-arguments",
                            "details", parseEx.getMessage());
                    messages.send(sender, "commands.usage", "usage", usage);
                }
                case UNKNOWN_TYPE -> {
                    // Internal error - log and show generic message
                    logger.warning("[Commands] " + parseEx.getMessage());
                    messages.send(sender, "errors.internal");
                }
                default -> {
                    messages.sendRaw(sender, "&c" + parseEx.getMessage());
                    messages.send(sender, "commands.usage", "usage", usage);
                }
            }
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

        // Try to find the longest matching subcommand name
        // Subcommands can have multi-word names like "animation create"
        CommandNode current = root;
        int consumed = 0;

        while (consumed < args.length) {
            SubNode matched = null;
            int matchedWords = 0;

            for (int endIdx = args.length; endIdx > consumed; endIdx--) {
                StringBuilder candidateName = new StringBuilder();
                for (int i = consumed; i < endIdx; i++) {
                    if (i > consumed)
                        candidateName.append(" ");
                    candidateName.append(args[i]);
                }

                SubNode child = current.child(candidateName.toString());
                if (child != null) {
                    matched = child;
                    matchedWords = endIdx - consumed;
                    break; // Use the longest match
                }
            }

            if (matched == null) {
                // No child found, stop resolution
                break;
            }

            current = matched;
            consumed += matchedWords;
        }

        List<String> remaining = consumed < args.length
                ? Arrays.asList(args).subList(consumed, args.length)
                : List.of();

        return new ResolutionResult(current, remaining);
    }

    private record ResolutionResult(CommandNode node, List<String> remaining) {
    }

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

    private void sendHelp(CommandSender sender, String path, int page) {
        helpFormatter.sendHelp(sender, root, path, page);
    }

    private void sendUsageHint(CommandSender sender, CommandNode node, String path) {
        messages.send(sender, "commands.help.hint", "command", "/" + path + " help");
    }

    /**
     * Extracts value from error message format "Invalid argument 'VALUE': reason".
     */
    private String extractValue(String errorMessage) {
        // Try to extract from "Invalid argument 'VALUE': ..."
        if (errorMessage != null && errorMessage.contains("'")) {
            int start = errorMessage.indexOf("'");
            int end = errorMessage.indexOf("'", start + 1);
            if (start >= 0 && end > start) {
                return errorMessage.substring(start + 1, end);
            }
        }
        return "?";
    }

    private CommandContext buildContext(CommandSender sender, String label, List<String> remaining,
            CommandNode targetNode, String fullPath) throws CommandParseException {
        List<com.afterlands.core.commands.CommandSpec.ArgumentSpec> argSpecs = targetNode.arguments();
        List<com.afterlands.core.commands.CommandSpec.FlagSpec> flagSpecs = targetNode.flags();

        ParsedArgs parsedArgs;
        com.afterlands.core.commands.execution.ParsedFlags parsedFlags;

        try {
            // Use ArgumentParser for proper type conversion
            var parseResult = argumentParser.parse(root.owner(), sender, remaining, argSpecs, flagSpecs);
            parsedArgs = parseResult.args();
            parsedFlags = parseResult.flags();
        } catch (ArgumentParser.ParseException e) {
            // Parsing failed - wrap in CommandParseException for structured handling
            throw new CommandParseException(e);
        }

        return CommandContext.builder()
                .owner(root.owner())
                .sender(sender)
                .label(label)
                .args(parsedArgs)
                .flags(parsedFlags)
                .messages(messages)
                .scheduler(scheduler)
                .metrics(metrics)
                .build();
    }

    /**
     * Exception wrapper for ArgumentParser.ParseException.
     * Allows structured error handling in the dispatch method.
     */
    private static final class CommandParseException extends Exception {
        private final ArgumentParser.ParseException parseException;

        CommandParseException(ArgumentParser.ParseException cause) {
            super(cause);
            this.parseException = cause;
        }

        ArgumentParser.ParseException parseException() {
            return parseException;
        }
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
        private final org.bukkit.configuration.file.FileConfiguration config;
        private final CooldownService cooldownService;

        public Factory(@NotNull MessageFacade messages,
                @NotNull SchedulerService scheduler,
                @NotNull MetricsService metrics,
                @NotNull Logger logger,
                boolean debug,
                @NotNull CommandGraph graph,
                @NotNull org.bukkit.configuration.file.FileConfiguration config,
                @NotNull CooldownService cooldownService) {
            this.messages = messages;
            this.scheduler = scheduler;
            this.metrics = metrics;
            this.logger = logger;
            this.debug = debug;
            this.graph = graph;
            this.config = config;
            this.cooldownService = cooldownService;
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
                    debug);

            HelpFormatter helpFormatter = new HelpFormatter(messages, config);

            return new DefaultCommandDispatcher(
                    root, messages, scheduler, metrics, logger, debug,
                    argumentParser, tabCompleter, helpFormatter, cooldownService);
        }
    }
}
