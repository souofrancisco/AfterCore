package com.afterlands.core.commands.parser;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Token reader for command argument parsing.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Quoted string support: {@code "hello world"} is parsed as single token</li>
 *   <li>Escape sequences: {@code \"} for literal quote inside quotes</li>
 *   <li>Lookahead with peek()</li>
 *   <li>Mark/reset for backtracking</li>
 *   <li>Remaining tokens as list or joined string</li>
 * </ul>
 *
 * <p>Example:</p>
 * <pre>{@code
 * ArgReader reader = ArgReader.of(args);
 * String sub = reader.next();           // First token
 * String name = reader.next();          // Second token
 * String message = reader.remaining();  // Rest joined
 * }</pre>
 *
 * <p>Thread Safety: Not thread-safe. Create per-invocation.</p>
 */
public final class ArgReader {

    private final List<String> tokens;
    private int position;
    private int mark = -1;

    private ArgReader(@NotNull List<String> tokens) {
        this.tokens = tokens;
        this.position = 0;
    }

    /**
     * Creates an ArgReader from raw arguments.
     *
     * <p>This method handles quoted strings and escape sequences.</p>
     *
     * @param args Raw command arguments
     * @return A new ArgReader
     */
    @NotNull
    public static ArgReader of(@NotNull String[] args) {
        return new ArgReader(tokenize(args));
    }

    /**
     * Creates an ArgReader from a single input string.
     *
     * @param input The input string to parse
     * @return A new ArgReader
     */
    @NotNull
    public static ArgReader parse(@NotNull String input) {
        return new ArgReader(tokenize(input));
    }

    /**
     * Creates an ArgReader from pre-tokenized arguments.
     *
     * @param tokens The tokens
     * @return A new ArgReader
     */
    @NotNull
    public static ArgReader ofTokens(@NotNull List<String> tokens) {
        return new ArgReader(new ArrayList<>(tokens));
    }

    /**
     * Checks if there are more tokens to read.
     *
     * @return true if more tokens are available
     */
    public boolean hasNext() {
        return position < tokens.size();
    }

    /**
     * Returns the next token without consuming it.
     *
     * @return The next token, or null if no more tokens
     */
    @Nullable
    public String peek() {
        return hasNext() ? tokens.get(position) : null;
    }

    /**
     * Returns the next token and advances the position.
     *
     * @return The next token
     * @throws NoSuchElementException if no more tokens
     */
    @NotNull
    public String next() {
        if (!hasNext()) {
            throw new NoSuchElementException("No more tokens");
        }
        return tokens.get(position++);
    }

    /**
     * Returns the next token if available, otherwise the default.
     *
     * @param defaultValue Default value
     * @return The next token or default
     */
    @NotNull
    public String next(@NotNull String defaultValue) {
        return hasNext() ? next() : defaultValue;
    }

    /**
     * Skips the specified number of tokens.
     *
     * @param count Number of tokens to skip
     * @return This reader
     */
    @NotNull
    public ArgReader skip(int count) {
        position = Math.min(position + count, tokens.size());
        return this;
    }

    /**
     * Marks the current position for reset.
     *
     * @return This reader
     */
    @NotNull
    public ArgReader mark() {
        this.mark = position;
        return this;
    }

    /**
     * Resets to the marked position.
     *
     * @return This reader
     * @throws IllegalStateException if no mark was set
     */
    @NotNull
    public ArgReader reset() {
        if (mark < 0) {
            throw new IllegalStateException("No mark set");
        }
        position = mark;
        return this;
    }

    /**
     * Returns the current position.
     *
     * @return Current position (0-indexed)
     */
    public int position() {
        return position;
    }

    /**
     * Returns the total number of tokens.
     *
     * @return Token count
     */
    public int size() {
        return tokens.size();
    }

    /**
     * Returns the number of remaining tokens.
     *
     * @return Number of tokens left
     */
    public int remaining() {
        return Math.max(0, tokens.size() - position);
    }

    /**
     * Returns all remaining tokens as a list.
     *
     * @return List of remaining tokens
     */
    @NotNull
    public List<String> remainingAsList() {
        if (!hasNext()) {
            return List.of();
        }
        return List.copyOf(tokens.subList(position, tokens.size()));
    }

    /**
     * Consumes and returns all remaining tokens as a joined string.
     *
     * @return Joined string of remaining tokens
     */
    @NotNull
    public String remainingJoined() {
        return remainingJoined(" ");
    }

    /**
     * Consumes and returns all remaining tokens joined with delimiter.
     *
     * @param delimiter The delimiter
     * @return Joined string
     */
    @NotNull
    public String remainingJoined(@NotNull String delimiter) {
        if (!hasNext()) {
            return "";
        }
        String result = String.join(delimiter, tokens.subList(position, tokens.size()));
        position = tokens.size();
        return result;
    }

    /**
     * Returns the original input reconstructed from tokens.
     *
     * @return Reconstructed input
     */
    @NotNull
    public String originalInput() {
        return String.join(" ", tokens);
    }

    /**
     * Returns all tokens as a list.
     *
     * @return Immutable list of all tokens
     */
    @NotNull
    public List<String> allTokens() {
        return List.copyOf(tokens);
    }

    // ========== Tokenization Logic ==========

    /**
     * Tokenizes raw command arguments with quoted string support.
     */
    @NotNull
    private static List<String> tokenize(@NotNull String[] args) {
        if (args.length == 0) {
            return List.of();
        }
        // Join and re-tokenize to handle quotes spanning multiple args
        return tokenize(String.join(" ", args));
    }

    /**
     * Tokenizes a single string with quoted string support.
     *
     * <p>Rules:</p>
     * <ul>
     *   <li>Whitespace separates tokens</li>
     *   <li>Content in double quotes is a single token (quotes stripped)</li>
     *   <li>Backslash escapes the next character inside quotes</li>
     *   <li>Unclosed quotes include rest of input as single token</li>
     * </ul>
     */
    @NotNull
    private static List<String> tokenize(@NotNull String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean escape = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (escape) {
                // Escaped character - add literally
                current.append(c);
                escape = false;
                continue;
            }

            if (c == '\\' && inQuotes) {
                // Start escape sequence (only inside quotes)
                escape = true;
                continue;
            }

            if (c == '"') {
                // Toggle quote mode
                inQuotes = !inQuotes;
                continue;
            }

            if (Character.isWhitespace(c) && !inQuotes) {
                // Token boundary (outside quotes)
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }

            // Regular character
            current.append(c);
        }

        // Add final token if present
        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }

        return tokens;
    }

    @Override
    public String toString() {
        return "ArgReader{position=" + position + ", tokens=" + tokens + "}";
    }
}
