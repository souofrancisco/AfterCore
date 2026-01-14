package com.afterlands.core.commands.execution;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Immutable container for parsed command arguments.
 *
 * <p>This class provides type-safe access to arguments that have been
 * parsed from the command input. Arguments are stored by name and can
 * be retrieved with automatic type conversion.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * ParsedArgs args = ...;
 * String name = args.getString("name");
 * int amount = args.getInt("amount", 1); // with default
 * Player target = args.getPlayer("target");
 * }</pre>
 */
public final class ParsedArgs {

    private static final ParsedArgs EMPTY = new ParsedArgs(Map.of(), List.of());

    private final Map<String, Object> namedArgs;
    private final List<String> positionalArgs;

    private ParsedArgs(@NotNull Map<String, Object> namedArgs, @NotNull List<String> positionalArgs) {
        this.namedArgs = Map.copyOf(namedArgs);
        this.positionalArgs = List.copyOf(positionalArgs);
    }

    /**
     * Returns an empty ParsedArgs instance.
     */
    @NotNull
    public static ParsedArgs empty() {
        return EMPTY;
    }

    /**
     * Creates a new builder for ParsedArgs.
     */
    @NotNull
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates ParsedArgs from a map.
     *
     * @param args The argument map
     * @return A new ParsedArgs instance
     */
    @NotNull
    public static ParsedArgs of(@NotNull Map<String, Object> args) {
        return new ParsedArgs(args, List.of());
    }

    /**
     * Creates ParsedArgs from positional arguments only.
     *
     * @param args The positional arguments
     * @return A new ParsedArgs instance
     */
    @NotNull
    public static ParsedArgs ofPositional(@NotNull List<String> args) {
        return new ParsedArgs(Map.of(), args);
    }

    /**
     * Checks if an argument exists.
     *
     * @param name Argument name
     * @return true if the argument is present
     */
    public boolean has(@NotNull String name) {
        return namedArgs.containsKey(name);
    }

    /**
     * Gets a raw argument value.
     *
     * @param name Argument name
     * @return The raw value, or null if not present
     */
    @Nullable
    public Object get(@NotNull String name) {
        return namedArgs.get(name);
    }

    /**
     * Gets an argument as a String.
     *
     * @param name Argument name
     * @return The string value, or null if not present
     */
    @Nullable
    public String getString(@NotNull String name) {
        Object val = namedArgs.get(name);
        return val != null ? val.toString() : null;
    }

    /**
     * Gets an argument as a String with a default value.
     *
     * @param name         Argument name
     * @param defaultValue Default value if not present
     * @return The string value or default
     */
    @NotNull
    public String getString(@NotNull String name, @NotNull String defaultValue) {
        String val = getString(name);
        return val != null ? val : defaultValue;
    }

    /**
     * Gets an argument as an integer.
     *
     * @param name Argument name
     * @return The integer value
     * @throws IllegalArgumentException if argument is missing or not an integer
     */
    public int getInt(@NotNull String name) {
        Object val = namedArgs.get(name);
        if (val == null) {
            throw new IllegalArgumentException("Missing required argument: " + name);
        }
        if (val instanceof Number num) {
            return num.intValue();
        }
        try {
            return Integer.parseInt(val.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Argument '" + name + "' is not a valid integer: " + val);
        }
    }

    /**
     * Gets an argument as an integer with a default value.
     *
     * @param name         Argument name
     * @param defaultValue Default value if not present or not an integer
     * @return The integer value or default
     */
    public int getInt(@NotNull String name, int defaultValue) {
        Object val = namedArgs.get(name);
        if (val == null) {
            return defaultValue;
        }
        if (val instanceof Number num) {
            return num.intValue();
        }
        try {
            return Integer.parseInt(val.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Gets an argument as a double.
     *
     * @param name Argument name
     * @return The double value
     * @throws IllegalArgumentException if argument is missing or not a number
     */
    public double getDouble(@NotNull String name) {
        Object val = namedArgs.get(name);
        if (val == null) {
            throw new IllegalArgumentException("Missing required argument: " + name);
        }
        if (val instanceof Number num) {
            return num.doubleValue();
        }
        try {
            return Double.parseDouble(val.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Argument '" + name + "' is not a valid number: " + val);
        }
    }

    /**
     * Gets an argument as a double with a default value.
     *
     * @param name         Argument name
     * @param defaultValue Default value if not present or not a number
     * @return The double value or default
     */
    public double getDouble(@NotNull String name, double defaultValue) {
        Object val = namedArgs.get(name);
        if (val == null) {
            return defaultValue;
        }
        if (val instanceof Number num) {
            return num.doubleValue();
        }
        try {
            return Double.parseDouble(val.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Gets an argument as a boolean.
     *
     * @param name Argument name
     * @return true if the argument is "true", "yes", "1", or "on" (case-insensitive)
     */
    public boolean getBoolean(@NotNull String name) {
        Object val = namedArgs.get(name);
        if (val == null) {
            return false;
        }
        if (val instanceof Boolean b) {
            return b;
        }
        String str = val.toString().toLowerCase(Locale.ROOT);
        return "true".equals(str) || "yes".equals(str) || "1".equals(str) || "on".equals(str);
    }

    /**
     * Gets an argument as a boolean with a default value.
     *
     * @param name         Argument name
     * @param defaultValue Default value if not present
     * @return The boolean value or default
     */
    public boolean getBoolean(@NotNull String name, boolean defaultValue) {
        if (!has(name)) {
            return defaultValue;
        }
        return getBoolean(name);
    }

    /**
     * Gets an argument as an online Player.
     *
     * @param name Argument name
     * @return The Player, or null if not found or offline
     */
    @Nullable
    public Player getPlayer(@NotNull String name) {
        Object val = namedArgs.get(name);
        if (val == null) {
            return null;
        }
        if (val instanceof Player p) {
            return p;
        }
        return Bukkit.getPlayerExact(val.toString());
    }

    /**
     * Gets an argument as a World.
     *
     * @param name Argument name
     * @return The World, or null if not found
     */
    @Nullable
    public World getWorld(@NotNull String name) {
        Object val = namedArgs.get(name);
        if (val == null) {
            return null;
        }
        if (val instanceof World w) {
            return w;
        }
        return Bukkit.getWorld(val.toString());
    }

    /**
     * Gets an argument as an enum value.
     *
     * @param name      Argument name
     * @param enumClass The enum class
     * @param <E>       The enum type
     * @return The enum value, or null if not found or invalid
     */
    @Nullable
    public <E extends Enum<E>> E getEnum(@NotNull String name, @NotNull Class<E> enumClass) {
        String val = getString(name);
        if (val == null) {
            return null;
        }
        try {
            return Enum.valueOf(enumClass, val.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Gets an argument as an enum value with a default.
     *
     * @param name         Argument name
     * @param enumClass    The enum class
     * @param defaultValue Default value if not found or invalid
     * @param <E>          The enum type
     * @return The enum value or default
     */
    @NotNull
    public <E extends Enum<E>> E getEnum(@NotNull String name, @NotNull Class<E> enumClass, @NotNull E defaultValue) {
        E val = getEnum(name, enumClass);
        return val != null ? val : defaultValue;
    }

    /**
     * Gets a positional argument by index.
     *
     * @param index Zero-based index
     * @return The argument value, or null if index is out of bounds
     */
    @Nullable
    public String getPositional(int index) {
        return index >= 0 && index < positionalArgs.size() ? positionalArgs.get(index) : null;
    }

    /**
     * Gets all positional arguments.
     *
     * @return Immutable list of positional arguments
     */
    @NotNull
    public List<String> positional() {
        return positionalArgs;
    }

    /**
     * Gets remaining positional arguments from an index.
     *
     * @param fromIndex Start index (inclusive)
     * @return List of remaining arguments
     */
    @NotNull
    public List<String> remaining(int fromIndex) {
        if (fromIndex >= positionalArgs.size()) {
            return List.of();
        }
        return positionalArgs.subList(fromIndex, positionalArgs.size());
    }

    /**
     * Gets remaining positional arguments as a joined string.
     *
     * @param fromIndex Start index (inclusive)
     * @return Joined string of remaining arguments
     */
    @NotNull
    public String remainingJoined(int fromIndex) {
        return String.join(" ", remaining(fromIndex));
    }

    /**
     * Gets remaining positional arguments as a joined string.
     *
     * @param fromIndex Start index (inclusive)
     * @param delimiter Delimiter to use
     * @return Joined string of remaining arguments
     */
    @NotNull
    public String remainingJoined(int fromIndex, @NotNull String delimiter) {
        return String.join(delimiter, remaining(fromIndex));
    }

    /**
     * Returns the number of positional arguments.
     */
    public int positionalCount() {
        return positionalArgs.size();
    }

    /**
     * Returns an unmodifiable view of all named arguments.
     */
    @NotNull
    public Map<String, Object> asMap() {
        return namedArgs;
    }

    @Override
    public String toString() {
        return "ParsedArgs{named=" + namedArgs + ", positional=" + positionalArgs + "}";
    }

    /**
     * Builder for ParsedArgs.
     */
    public static final class Builder {
        private final Map<String, Object> namedArgs = new LinkedHashMap<>();
        private final List<String> positionalArgs = new ArrayList<>();

        private Builder() {}

        /**
         * Adds a named argument.
         *
         * @param name  Argument name
         * @param value Argument value
         * @return This builder
         */
        @NotNull
        public Builder put(@NotNull String name, @Nullable Object value) {
            if (value != null) {
                namedArgs.put(name, value);
            }
            return this;
        }

        /**
         * Adds a positional argument.
         *
         * @param value Argument value
         * @return This builder
         */
        @NotNull
        public Builder addPositional(@NotNull String value) {
            positionalArgs.add(value);
            return this;
        }

        /**
         * Adds multiple positional arguments.
         *
         * @param values Argument values
         * @return This builder
         */
        @NotNull
        public Builder addAllPositional(@NotNull Collection<String> values) {
            positionalArgs.addAll(values);
            return this;
        }

        /**
         * Builds the ParsedArgs instance.
         */
        @NotNull
        public ParsedArgs build() {
            return new ParsedArgs(namedArgs, positionalArgs);
        }
    }
}
