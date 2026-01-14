package com.afterlands.core.commands.parser;

import com.afterlands.core.commands.execution.CommandContext;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Base interface for typed argument parsing.
 *
 * <p>ArgumentType defines how to:</p>
 * <ul>
 *   <li>Parse a string input into a typed value</li>
 *   <li>Provide tab completion suggestions</li>
 *   <li>Generate human-readable names for usage strings</li>
 * </ul>
 *
 * <p>Implementations should be stateless and thread-safe.</p>
 *
 * <p>Example implementation:</p>
 * <pre>{@code
 * public class IntegerType implements ArgumentType<Integer> {
 *     @Override
 *     public Integer parse(ParseContext ctx, String input) throws ParseException {
 *         try {
 *             return Integer.parseInt(input);
 *         } catch (NumberFormatException e) {
 *             throw new ParseException("'" + input + "' is not a valid integer");
 *         }
 *     }
 *
 *     @Override
 *     public List<String> suggest(CommandSender sender, String partial) {
 *         return List.of(); // No suggestions for integers
 *     }
 *
 *     @Override
 *     public String typeName() {
 *         return "integer";
 *     }
 * }
 * }</pre>
 *
 * @param <T> The parsed type
 */
public interface ArgumentType<T> {

    /**
     * Parses an input string into the target type.
     *
     * @param ctx   The parse context
     * @param input The input string to parse
     * @return The parsed value
     * @throws ParseException if parsing fails
     */
    @NotNull
    T parse(@NotNull ParseContext ctx, @NotNull String input) throws ParseException;

    /**
     * Provides tab completion suggestions.
     *
     * @param sender  The command sender (for permission filtering)
     * @param partial The partial input to complete
     * @return List of suggestions, never null
     */
    @NotNull
    List<String> suggest(@NotNull CommandSender sender, @NotNull String partial);

    /**
     * Returns the human-readable type name for usage strings.
     *
     * @return Type name (e.g., "player", "integer", "world")
     */
    @NotNull
    String typeName();

    /**
     * Checks if this type consumes multiple tokens (greedy).
     *
     * <p>Greedy types consume all remaining tokens. Only one greedy
     * argument can be at the end of an argument list.</p>
     *
     * @return true if this type is greedy
     */
    default boolean isGreedy() {
        return false;
    }

    /**
     * Returns the expected number of tokens this type consumes.
     *
     * <p>Most types consume 1 token. Location might consume 3-4 (x y z [world]).
     * Greedy types return -1.</p>
     *
     * @return Number of tokens, or -1 for greedy
     */
    default int tokenCount() {
        return isGreedy() ? -1 : 1;
    }

    /**
     * Exception thrown when argument parsing fails.
     */
    class ParseException extends Exception {
        private final String argument;
        private final String reason;

        public ParseException(@NotNull String message) {
            super(message);
            this.argument = null;
            this.reason = message;
        }

        public ParseException(@NotNull String argument, @NotNull String reason) {
            super("Invalid argument '" + argument + "': " + reason);
            this.argument = argument;
            this.reason = reason;
        }

        @Nullable
        public String getArgument() {
            return argument;
        }

        @NotNull
        public String getReason() {
            return reason;
        }
    }

    /**
     * Context for argument parsing.
     */
    record ParseContext(
            @NotNull CommandSender sender,
            @NotNull ArgReader reader,
            @Nullable CommandContext commandContext
    ) {
        /**
         * Creates a minimal parse context.
         */
        public static ParseContext of(@NotNull CommandSender sender, @NotNull ArgReader reader) {
            return new ParseContext(sender, reader, null);
        }

        /**
         * Creates a parse context with full command context.
         */
        public static ParseContext of(@NotNull CommandContext ctx, @NotNull ArgReader reader) {
            return new ParseContext(ctx.sender(), reader, ctx);
        }
    }
}
