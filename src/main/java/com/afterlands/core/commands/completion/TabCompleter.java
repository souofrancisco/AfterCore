package com.afterlands.core.commands.completion;

import com.afterlands.core.commands.CommandSpec;
import com.afterlands.core.commands.parser.ArgumentParser;
import com.afterlands.core.commands.parser.ArgumentType;
import com.afterlands.core.commands.parser.ArgumentTypeRegistry;
import com.afterlands.core.commands.registry.CommandGraph;
import com.afterlands.core.commands.registry.nodes.CommandNode;
import com.afterlands.core.commands.registry.nodes.SubNode;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Advanced tab-completion handler with caching and type-aware suggestions.
 *
 * <p>This completer provides:</p>
 * <ul>
 *   <li>Subcommand completion (filtered by permission)</li>
 *   <li>Argument completion by type (player, world, enum, etc.)</li>
 *   <li>Flag completion (--help, --force, etc.)</li>
 *   <li>Caching of expensive suggestions</li>
 *   <li>Smart partial matching</li>
 * </ul>
 *
 * <p>Performance characteristics:</p>
 * <ul>
 *   <li>Cached completions: O(1) lookup + O(n) filter where n is result size</li>
 *   <li>Uncached completions: Depends on ArgumentType implementation</li>
 *   <li>Target: {@code < 0.5ms} per completion on average</li>
 * </ul>
 */
public final class TabCompleter {

    private final CommandGraph graph;
    private final ArgumentTypeRegistry typeRegistry;
    private final CompletionCache cache;
    private final boolean debug;

    /**
     * Creates a new TabCompleter.
     *
     * @param graph        The command graph
     * @param typeRegistry The argument type registry
     * @param cache        The completion cache
     * @param debug        Whether debug mode is enabled
     */
    public TabCompleter(@NotNull CommandGraph graph,
                        @NotNull ArgumentTypeRegistry typeRegistry,
                        @NotNull CompletionCache cache,
                        boolean debug) {
        this.graph = Objects.requireNonNull(graph, "graph");
        this.typeRegistry = Objects.requireNonNull(typeRegistry, "typeRegistry");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.debug = debug;
    }

    /**
     * Generates tab-completions for the given input.
     *
     * @param sender The command sender
     * @param label  The command label
     * @param args   The current arguments
     * @return List of suggestions
     */
    @NotNull
    public List<String> complete(@NotNull CommandSender sender,
                                  @NotNull String label,
                                  @NotNull String[] args) {
        // Handle empty input
        if (args.length == 0) {
            return List.of();
        }

        // Resolve to find current node
        String[] pathArgs = args.length > 1 ? Arrays.copyOf(args, args.length - 1) : new String[0];
        CommandGraph.ResolutionResult resolution = graph.resolve(
                concatenate(label, pathArgs)
        );

        if (!resolution.found()) {
            return List.of();
        }

        CommandNode currentNode = resolution.node();
        String partial = args[args.length - 1].toLowerCase(Locale.ROOT);

        List<String> suggestions = new ArrayList<>();

        // Check if completing a flag
        if (partial.startsWith("--")) {
            // Long flag completion
            suggestions.addAll(completeLongFlags(currentNode, partial.substring(2)));
        } else if (partial.startsWith("-") && partial.length() > 1 && !isNumeric(partial)) {
            // Short flag completion
            suggestions.addAll(completeShortFlags(currentNode, partial.substring(1)));
        } else {
            // Regular completion: subcommands or arguments

            // Add subcommand suggestions
            suggestions.addAll(completeSubcommands(sender, currentNode, partial));

            // Add argument suggestions if node is executable
            if (currentNode.isExecutable() && !currentNode.arguments().isEmpty()) {
                suggestions.addAll(completeArguments(sender, currentNode, resolution.remaining(), partial));
            }

            // Add help suggestion
            if ("help".startsWith(partial) && currentNode.hasChildren()) {
                suggestions.add("help");
            }
        }

        // Filter and sort
        return suggestions.stream()
                .distinct()
                .filter(s -> s.toLowerCase().startsWith(partial))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .limit(50) // Reasonable limit
                .collect(Collectors.toList());
    }

    private List<String> completeSubcommands(CommandSender sender, CommandNode node, String partial) {
        List<String> results = new ArrayList<>();

        for (SubNode child : node.children().values()) {
            // Permission check
            if (child.permission() != null && !sender.hasPermission(child.permission())) {
                continue;
            }

            // Add name if matches
            if (child.name().toLowerCase().startsWith(partial)) {
                results.add(child.name());
            }

            // Add aliases if match
            for (String alias : child.aliases()) {
                if (alias.toLowerCase().startsWith(partial)) {
                    results.add(alias);
                }
            }
        }

        return results;
    }

    private List<String> completeArguments(CommandSender sender,
                                            CommandNode node,
                                            List<String> currentArgs,
                                            String partial) {
        List<CommandSpec.ArgumentSpec> argSpecs = node.arguments();
        if (argSpecs.isEmpty()) {
            return List.of();
        }

        // Determine which argument we're completing
        // currentArgs contains what's already been typed (minus the partial)
        int argPosition = currentArgs.size();

        if (argPosition >= argSpecs.size()) {
            // Beyond defined arguments - check if last is greedy
            CommandSpec.ArgumentSpec lastSpec = argSpecs.get(argSpecs.size() - 1);
            if ("greedyString".equals(lastSpec.type())) {
                // Greedy string - no suggestions
                return List.of();
            }
            return List.of();
        }

        CommandSpec.ArgumentSpec spec = argSpecs.get(argPosition);
        String typeName = spec.type();

        // Get type
        ArgumentType<?> type = typeRegistry.get(typeName);
        if (type == null) {
            return List.of();
        }

        // Build cache key
        String cacheKey = buildCacheKey(node.name(), argPosition, typeName, partial);

        // Get suggestions (with caching)
        return cache.get(cacheKey, () -> {
            try {
                return type.suggest(sender, partial);
            } catch (Exception e) {
                if (debug) {
                    // Log in debug mode
                    return List.of();
                }
                return List.of();
            }
        });
    }

    private List<String> completeLongFlags(CommandNode node, String partial) {
        List<String> results = new ArrayList<>();

        // Add --help
        if ("help".startsWith(partial)) {
            results.add("--help");
        }

        for (CommandSpec.FlagSpec flag : node.flags()) {
            String name = flag.name().toLowerCase();
            if (name.startsWith(partial)) {
                results.add("--" + name);
            }
        }

        return results;
    }

    private List<String> completeShortFlags(CommandNode node, String partial) {
        List<String> results = new ArrayList<>();

        // Add -h
        if ("h".startsWith(partial)) {
            results.add("-h");
        }

        for (CommandSpec.FlagSpec flag : node.flags()) {
            if (flag.shortName() == null || flag.shortName().isEmpty()) {
                continue;
            }

            String shortName = flag.shortName().toLowerCase();
            if (shortName.startsWith(partial)) {
                results.add("-" + shortName);
            }
        }

        return results;
    }

    private String buildCacheKey(String nodeName, int argPosition, String typeName, String partial) {
        // Include partial in key for better cache hits
        // Truncate partial to prevent cache explosion
        String truncatedPartial = partial.length() > 10 ? partial.substring(0, 10) : partial;
        return nodeName + ":" + argPosition + ":" + typeName + ":" + truncatedPartial;
    }

    private String[] concatenate(String first, String[] rest) {
        String[] result = new String[rest.length + 1];
        result[0] = first;
        System.arraycopy(rest, 0, result, 1, rest.length);
        return result;
    }

    private boolean isNumeric(String str) {
        if (str.length() <= 1) return false;
        String test = str.startsWith("-") ? str.substring(1) : str;
        return test.chars().allMatch(c -> Character.isDigit(c) || c == '.');
    }
}
