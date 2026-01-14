package com.afterlands.core.commands.execution;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Immutable container for parsed command flags.
 *
 * <p>This class provides type-safe access to flags that have been parsed
 * from the command input. Flags can be:</p>
 * <ul>
 *   <li>Boolean flags: {@code -f}, {@code --force}</li>
 *   <li>Value flags: {@code --page 2}, {@code --name=value}</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * ParsedFlags flags = ...;
 * if (flags.has("force")) {
 *     // do forced operation
 * }
 * int page = flags.getInt("page", 1);
 * }</pre>
 */
public final class ParsedFlags {

    private static final ParsedFlags EMPTY = new ParsedFlags(Map.of());

    private final Map<String, String> flags;

    private ParsedFlags(@NotNull Map<String, String> flags) {
        this.flags = Map.copyOf(flags);
    }

    /**
     * Returns an empty ParsedFlags instance.
     */
    @NotNull
    public static ParsedFlags empty() {
        return EMPTY;
    }

    /**
     * Creates a new builder for ParsedFlags.
     */
    @NotNull
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates ParsedFlags from a map.
     *
     * @param flags The flag map (name -> value, null value for boolean flags)
     * @return A new ParsedFlags instance
     */
    @NotNull
    public static ParsedFlags of(@NotNull Map<String, String> flags) {
        return new ParsedFlags(flags);
    }

    /**
     * Checks if a flag is present.
     *
     * @param name Flag name (without - or -- prefix)
     * @return true if the flag is present
     */
    public boolean has(@NotNull String name) {
        return flags.containsKey(name);
    }

    /**
     * Gets a flag value.
     *
     * @param name Flag name (without - or -- prefix)
     * @return The flag value, or null if flag is not present or has no value
     */
    @Nullable
    public String getValue(@NotNull String name) {
        return flags.get(name);
    }

    /**
     * Gets a flag value with a default.
     *
     * @param name         Flag name
     * @param defaultValue Default value if flag is not present
     * @return The flag value or default
     */
    @NotNull
    public String getValue(@NotNull String name, @NotNull String defaultValue) {
        String val = flags.get(name);
        return val != null ? val : defaultValue;
    }

    /**
     * Gets a flag value as an integer.
     *
     * @param name Flag name
     * @return The integer value
     * @throws IllegalArgumentException if flag is not present or not an integer
     */
    public int getInt(@NotNull String name) {
        String val = flags.get(name);
        if (val == null) {
            throw new IllegalArgumentException("Flag not present: --" + name);
        }
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Flag --" + name + " is not a valid integer: " + val);
        }
    }

    /**
     * Gets a flag value as an integer with a default.
     *
     * @param name         Flag name
     * @param defaultValue Default if flag not present or not an integer
     * @return The integer value or default
     */
    public int getInt(@NotNull String name, int defaultValue) {
        String val = flags.get(name);
        if (val == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Gets a flag value as a double.
     *
     * @param name         Flag name
     * @param defaultValue Default if flag not present or not a number
     * @return The double value or default
     */
    public double getDouble(@NotNull String name, double defaultValue) {
        String val = flags.get(name);
        if (val == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Gets a flag as a boolean.
     *
     * <p>Returns true if the flag is present (regardless of value),
     * or if the value is "true", "yes", "1", or "on".</p>
     *
     * @param name Flag name
     * @return true if the flag indicates a true value
     */
    public boolean getBoolean(@NotNull String name) {
        if (!flags.containsKey(name)) {
            return false;
        }
        String val = flags.get(name);
        if (val == null || val.isEmpty()) {
            return true; // Present but no value = true
        }
        String lower = val.toLowerCase(Locale.ROOT);
        return "true".equals(lower) || "yes".equals(lower) || "1".equals(lower) || "on".equals(lower);
    }

    /**
     * Returns all flag names.
     */
    @NotNull
    public Set<String> names() {
        return flags.keySet();
    }

    /**
     * Returns the number of flags.
     */
    public int count() {
        return flags.size();
    }

    /**
     * Returns an unmodifiable view of all flags.
     */
    @NotNull
    public Map<String, String> asMap() {
        return flags;
    }

    @Override
    public String toString() {
        return "ParsedFlags{" + flags + "}";
    }

    /**
     * Builder for ParsedFlags.
     */
    public static final class Builder {
        private final Map<String, String> flags = new LinkedHashMap<>();

        private Builder() {}

        /**
         * Adds a boolean flag (present without value).
         *
         * @param name Flag name
         * @return This builder
         */
        @NotNull
        public Builder flag(@NotNull String name) {
            flags.put(name, "");
            return this;
        }

        /**
         * Adds a flag with a value.
         *
         * @param name  Flag name
         * @param value Flag value
         * @return This builder
         */
        @NotNull
        public Builder flag(@NotNull String name, @Nullable String value) {
            flags.put(name, value != null ? value : "");
            return this;
        }

        /**
         * Builds the ParsedFlags instance.
         */
        @NotNull
        public ParsedFlags build() {
            return new ParsedFlags(flags);
        }
    }
}
