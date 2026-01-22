package com.afterlands.core.commands.parser;

import com.afterlands.core.commands.CommandSpec;
import com.afterlands.core.commands.execution.ParsedArgs;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Parses command arguments according to their type specifications.
 *
 * <p>
 * This parser coordinates between ArgReader, ArgumentTypeRegistry, and
 * FlagParser
 * to produce structured ParsedArgs from raw command input. It handles:
 * </p>
 * <ul>
 * <li>Flag extraction (removes flags from positional args)</li>
 * <li>Typed argument parsing (string, int, player, etc.)</li>
 * <li>Default value application</li>
 * <li>Validation and error messages</li>
 * </ul>
 *
 * <p>
 * Performance: O(n) where n is argument count, no allocations in success path.
 * </p>
 *
 * <p>
 * Thread Safety: Stateless, thread-safe.
 * </p>
 */
public final class ArgumentParser {

    private final ArgumentTypeRegistry typeRegistry;

    /**
     * Creates a new ArgumentParser.
     *
     * @param typeRegistry The type registry for resolving argument types
     */
    public ArgumentParser(@NotNull ArgumentTypeRegistry typeRegistry) {
        this.typeRegistry = Objects.requireNonNull(typeRegistry, "typeRegistry");
    }

    /**
     * Parses arguments according to the specification.
     *
     * @param sender    The command sender (for context-aware parsing like player
     *                  lookup)
     * @param args      Raw arguments (after subcommand resolution)
     * @param argSpecs  Argument specifications
     * @param flagSpecs Flag specifications
     * @return Parse result with typed arguments and flags
     * @throws ParseException if parsing fails
     */
    @NotNull
    public ParseResult parse(@NotNull Plugin owner,
            @NotNull CommandSender sender,
            @NotNull List<String> args,
            @NotNull List<CommandSpec.ArgumentSpec> argSpecs,
            @NotNull List<CommandSpec.FlagSpec> flagSpecs) throws ParseException {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(args, "args");
        Objects.requireNonNull(argSpecs, "argSpecs");
        Objects.requireNonNull(flagSpecs, "flagSpecs");

        // Parse flags first (removes them from args)
        FlagParser flagParser = new FlagParser(flagSpecs);
        FlagParser.Result flagResult = flagParser.parse(args);

        // Remaining are positional arguments
        List<String> positionalArgs = flagResult.remaining();

        // Parse positional arguments
        ParsedArgs.Builder parsedArgs = ParsedArgs.builder();
        parsedArgs.addAllPositional(positionalArgs);

        int argIndex = 0;
        for (int i = 0; i < argSpecs.size(); i++) {
            CommandSpec.ArgumentSpec spec = argSpecs.get(i);
            String argName = spec.name();
            String typeName = spec.type();

            // Get argument type
            ArgumentType<?> type = resolveType(owner, typeName);
            if (type == null) {
                throw new ParseException(ParseException.ErrorType.UNKNOWN_TYPE, argName,
                        "Unknown argument type: " + typeName + " for argument: " + argName);
            }

            // Check if we have a value
            if (argIndex >= positionalArgs.size()) {
                // No more arguments
                String defaultVal = spec.defaultValue();
                boolean hasRealDefault = defaultVal != null && !"__NONE__".equals(defaultVal);

                if (spec.optional() && hasRealDefault) {
                    // Use default value
                    try {
                        ArgumentType.ParseContext parseCtx = ArgumentType.ParseContext.of(sender,
                                ArgReader.parse(defaultVal));
                        Object defaultParsed = type.parse(parseCtx, defaultVal);
                        parsedArgs.put(argName, defaultParsed);
                    } catch (Exception e) {
                        throw new ParseException("Invalid default value for " + argName + ": " + e.getMessage());
                    }
                } else if (spec.optional()) {
                    // Optional with no default - skip (will be null)
                    continue;
                } else {
                    // Required argument missing
                    throw new ParseException(ParseException.ErrorType.MISSING_REQUIRED, argName, argName);
                }
            } else {
                // Check if this is a greedy type - consume all remaining tokens
                if (type.isGreedy()) {
                    List<String> remaining = positionalArgs.subList(argIndex, positionalArgs.size());
                    String joined = String.join(" ", remaining);
                    parsedArgs.put(argName, joined);
                    argIndex = positionalArgs.size(); // Consume all
                } else {
                    // Parse the argument normally
                    String raw = positionalArgs.get(argIndex);
                    try {
                        ArgumentType.ParseContext parseCtx = ArgumentType.ParseContext.of(sender, ArgReader.parse(raw));
                        Object parsed = type.parse(parseCtx, raw);
                        parsedArgs.put(argName, parsed);
                        argIndex++;
                    } catch (Exception e) {
                        throw new ParseException(ParseException.ErrorType.INVALID_VALUE, argName,
                                e.getMessage());
                    }
                }
            }
        }

        // Check for excess arguments
        if (argIndex < positionalArgs.size()) {
            // Check if last arg is greedy
            if (!argSpecs.isEmpty()) {
                CommandSpec.ArgumentSpec lastSpec = argSpecs.get(argSpecs.size() - 1);
                ArgumentType<?> lastType = resolveType(owner, lastSpec.type());
                if (lastType != null && lastType.isGreedy()) {
                    // Greedy type - join remaining
                    List<String> remaining = positionalArgs.subList(argIndex, positionalArgs.size());
                    String joined = String.join(" ", remaining);
                    parsedArgs.put(lastSpec.name(), joined);
                    return new ParseResult(parsedArgs.build(), flagResult.flags());
                }
            }

            // Too many arguments
            throw new ParseException(ParseException.ErrorType.TOO_MANY_ARGS, null,
                    "expected " + argSpecs.size() + ", got " + positionalArgs.size());
        }

        return new ParseResult(parsedArgs.build(), flagResult.flags());
    }

    /**
     * Suggests completions for the current argument position.
     *
     * @param sender   The command sender
     * @param args     Current arguments
     * @param argSpecs Argument specifications
     * @return List of suggestions
     */
    @NotNull
    public List<String> suggest(@NotNull CommandSender sender,
            @NotNull List<String> args,
            @NotNull List<CommandSpec.ArgumentSpec> argSpecs) {
        if (argSpecs.isEmpty()) {
            return List.of();
        }

        // Determine which argument position we're completing
        int position = Math.max(0, args.size() - 1);

        if (position >= argSpecs.size()) {
            // Beyond defined args
            return List.of();
        }

        CommandSpec.ArgumentSpec spec = argSpecs.get(position);
        String partial = args.isEmpty() ? "" : args.get(args.size() - 1);

        // Get type and suggest
        ArgumentType<?> type = typeRegistry.get(spec.type());
        if (type == null) {
            return List.of();
        }

        try {
            return type.suggest(sender, partial);
        } catch (Exception e) {
            // Suggest failed - return empty
            return List.of();
        }
    }

    /**
     * Result of argument parsing.
     *
     * @param args  Parsed positional arguments
     * @param flags Parsed flags
     */
    public record ParseResult(
            @NotNull ParsedArgs args,
            @NotNull com.afterlands.core.commands.execution.ParsedFlags flags) {
    }

    /**
     * Exception thrown when argument parsing fails.
     */
    public static final class ParseException extends Exception {
        private final ErrorType errorType;
        private final String argumentName;

        public ParseException(String message) {
            super(message);
            this.errorType = ErrorType.GENERIC;
            this.argumentName = null;
        }

        public ParseException(ErrorType errorType, String argumentName, String message) {
            super(message);
            this.errorType = errorType;
            this.argumentName = argumentName;
        }

        public ParseException(String message, Throwable cause) {
            super(message, cause);
            this.errorType = ErrorType.GENERIC;
            this.argumentName = null;
        }

        public ErrorType errorType() {
            return errorType;
        }

        public String argumentName() {
            return argumentName;
        }

        public enum ErrorType {
            MISSING_REQUIRED,
            INVALID_VALUE,
            TOO_MANY_ARGS,
            UNKNOWN_TYPE,
            GENERIC
        }
    }

    private ArgumentType<?> resolveType(Plugin owner, String typeName) {
        ArgumentType<?> pluginType = typeRegistry.getForPlugin(owner, typeName);
        if (pluginType != null) {
            return pluginType;
        }
        return typeRegistry.get(typeName);
    }
}
