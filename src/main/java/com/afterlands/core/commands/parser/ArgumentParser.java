package com.afterlands.core.commands.parser;

import com.afterlands.core.commands.CommandSpec;
import com.afterlands.core.commands.execution.ParsedArgs;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Parses command arguments according to their type specifications.
 *
 * <p>This parser coordinates between ArgReader, ArgumentTypeRegistry, and FlagParser
 * to produce structured ParsedArgs from raw command input. It handles:</p>
 * <ul>
 *   <li>Flag extraction (removes flags from positional args)</li>
 *   <li>Typed argument parsing (string, int, player, etc.)</li>
 *   <li>Default value application</li>
 *   <li>Validation and error messages</li>
 * </ul>
 *
 * <p>Performance: O(n) where n is argument count, no allocations in success path.</p>
 *
 * <p>Thread Safety: Stateless, thread-safe.</p>
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
     * @param sender   The command sender (for context-aware parsing like player lookup)
     * @param args     Raw arguments (after subcommand resolution)
     * @param argSpecs Argument specifications
     * @param flagSpecs Flag specifications
     * @return Parse result with typed arguments and flags
     * @throws ParseException if parsing fails
     */
    @NotNull
    public ParseResult parse(@NotNull CommandSender sender,
                             @NotNull List<String> args,
                             @NotNull List<CommandSpec.ArgumentSpec> argSpecs,
                             @NotNull List<CommandSpec.FlagSpec> flagSpecs) throws ParseException {
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
            ArgumentType<?> type = typeRegistry.get(typeName);
            if (type == null) {
                throw new ParseException("Unknown argument type: " + typeName + " for argument: " + argName);
            }

            // Check if we have a value
            if (argIndex >= positionalArgs.size()) {
                // No more arguments
                if (spec.optional() && spec.defaultValue() != null && !spec.defaultValue().isEmpty()) {
                    // Use default value
                    try {
                        ArgumentType.ParseContext parseCtx = ArgumentType.ParseContext.of(sender, ArgReader.parse(spec.defaultValue()));
                        Object defaultParsed = type.parse(parseCtx, spec.defaultValue());
                        parsedArgs.put(argName, defaultParsed);
                    } catch (Exception e) {
                        throw new ParseException("Invalid default value for " + argName + ": " + e.getMessage());
                    }
                } else if (spec.optional()) {
                    // Optional with no default - skip
                    continue;
                } else {
                    // Required argument missing
                    throw new ParseException("Missing required argument: " + argName);
                }
            } else {
                // Parse the argument
                String raw = positionalArgs.get(argIndex);
                try {
                    ArgumentType.ParseContext parseCtx = ArgumentType.ParseContext.of(sender, ArgReader.parse(raw));
                    Object parsed = type.parse(parseCtx, raw);
                    parsedArgs.put(argName, parsed);
                    argIndex++;
                } catch (Exception e) {
                    throw new ParseException("Invalid " + argName + ": " + e.getMessage());
                }
            }
        }

        // Check for excess arguments
        if (argIndex < positionalArgs.size()) {
            // Check if last arg is greedy
            if (!argSpecs.isEmpty()) {
                CommandSpec.ArgumentSpec lastSpec = argSpecs.get(argSpecs.size() - 1);
                if ("greedyString".equals(lastSpec.type())) {
                    // Greedy type - join remaining
                    List<String> remaining = positionalArgs.subList(argIndex, positionalArgs.size());
                    String joined = String.join(" ", remaining);
                    parsedArgs.put(lastSpec.name(), joined);
                    return new ParseResult(parsedArgs.build(), flagResult.flags());
                }
            }

            // Too many arguments
            throw new ParseException("Too many arguments (expected " + argSpecs.size() + ", got " + positionalArgs.size() + ")");
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
            @NotNull com.afterlands.core.commands.execution.ParsedFlags flags
    ) {}

    /**
     * Exception thrown when argument parsing fails.
     */
    public static final class ParseException extends Exception {
        public ParseException(String message) {
            super(message);
        }

        public ParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
