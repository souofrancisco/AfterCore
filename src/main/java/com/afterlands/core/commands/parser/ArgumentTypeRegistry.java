package com.afterlands.core.commands.parser;

import com.afterlands.core.commands.parser.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.plugin.Plugin;

/**
 * Registry for argument type parsers.
 *
 * <p>
 * This registry maintains a mapping from type names to ArgumentType
 * implementations. It comes pre-populated with standard types and allows
 * plugins to register custom types.
 * </p>
 *
 * <p>
 * Standard types:
 * </p>
 * <ul>
 * <li>{@code string} - Single string token</li>
 * <li>{@code greedyString} / {@code text} - All remaining tokens</li>
 * <li>{@code integer} / {@code int} - Integer values</li>
 * <li>{@code double} / {@code number} - Decimal values</li>
 * <li>{@code boolean} / {@code bool} - Boolean values</li>
 * <li>{@code playerOnline} / {@code player} - Online players</li>
 * <li>{@code world} - Worlds</li>
 * </ul>
 *
 * <p>
 * Thread Safety: This class is thread-safe.
 * </p>
 */
public final class ArgumentTypeRegistry {

    private static final ArgumentTypeRegistry INSTANCE = new ArgumentTypeRegistry();

    private final Map<String, ArgumentType<?>> types = new ConcurrentHashMap<>();
    private final Map<Plugin, Map<String, ArgumentType<?>>> pluginTypes = new ConcurrentHashMap<>();

    private ArgumentTypeRegistry() {
        registerDefaults();
    }

    /**
     * Returns the global registry instance.
     */
    @NotNull
    public static ArgumentTypeRegistry instance() {
        return INSTANCE;
    }

    /**
     * Registers an argument type.
     *
     * @param name Type name (case-insensitive)
     * @param type The argument type implementation
     * @param <T>  The parsed type
     */
    public <T> void register(@NotNull String name, @NotNull ArgumentType<T> type) {
        types.put(name.toLowerCase(Locale.ROOT), type);
    }

    /**
     * Registers an argument type with aliases.
     *
     * @param type  The argument type implementation
     * @param names Type names (first is primary)
     * @param <T>   The parsed type
     */
    @SafeVarargs
    public final <T> void register(@NotNull ArgumentType<T> type, @NotNull String... names) {
        for (String name : names) {
            types.put(name.toLowerCase(Locale.ROOT), type);
        }
    }

    /**
     * Gets an argument type by name.
     *
     * @param name Type name (case-insensitive)
     * @return The argument type, or null if not found
     */
    @Nullable
    public ArgumentType<?> get(@NotNull String name) {
        return types.get(name.toLowerCase(Locale.ROOT));
    }

    /**
     * Gets an argument type by name with type casting.
     *
     * @param name Type name
     * @param <T>  Expected type
     * @return The argument type, or null if not found
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public <T> ArgumentType<T> getTyped(@NotNull String name) {
        return (ArgumentType<T>) get(name);
    }

    /**
     * Checks if a type is registered.
     *
     * @param name Type name
     * @return true if registered
     */
    public boolean contains(@NotNull String name) {
        return types.containsKey(name.toLowerCase(Locale.ROOT));
    }

    /**
     * Unregisters an argument type.
     *
     * @param name Type name
     * @return true if removed
     */
    public boolean unregister(@NotNull String name) {
        return types.remove(name.toLowerCase(Locale.ROOT)) != null;
    }

    /**
     * Returns the number of registered types.
     */
    public int size() {
        return types.size();
    }

    private void registerDefaults() {
        // String types
        register(StringType.INSTANCE, "string", "str");
        register(GreedyStringType.INSTANCE, "greedyString", "greedystring", "text", "message", "string[]");

        // Numeric types
        register(IntegerType.INSTANCE, "integer", "int");
        register(DoubleType.INSTANCE, "double", "number", "decimal", "float");

        // Boolean type
        register(BooleanType.INSTANCE, "boolean", "bool");

        // Entity types
        register(PlayerOnlineType.INSTANCE, "playerOnline", "playeronline", "player", "onlinePlayer");
        register(PlayerOfflineType.INSTANCE, "playerOffline", "playeroffline", "offlinePlayer");

        // World type
        register(WorldType.INSTANCE, "world");
    }

    /**
     * Creates a bounded integer type and optionally registers it.
     *
     * @param min Minimum value
     * @param max Maximum value
     * @return The bounded integer type
     */
    @NotNull
    public static IntegerType intRange(int min, int max) {
        return IntegerType.range(min, max);
    }

    /**
     * Creates an enum type and optionally registers it.
     *
     * @param enumClass The enum class
     * @param <E>       The enum type
     * @return The enum type
     */
    @NotNull
    public static <E extends Enum<E>> EnumType<E> enumType(@NotNull Class<E> enumClass) {
        return EnumType.of(enumClass);
    }

    // ========== Plugin-Scoped Methods ==========

    /**
     * Registers an argument type for a specific plugin.
     *
     * <p>
     * Plugin-scoped types take precedence over global types during resolution.
     * </p>
     *
     * @param owner Plugin that owns this type
     * @param name  Type name (case-insensitive)
     * @param type  The argument type implementation
     * @param <T>   The parsed type
     */
    public <T> void registerForPlugin(@NotNull Plugin owner, @NotNull String name, @NotNull ArgumentType<T> type) {
        pluginTypes.computeIfAbsent(owner, k -> new ConcurrentHashMap<>())
                .put(name.toLowerCase(Locale.ROOT), type);
    }

    /**
     * Gets an argument type, checking plugin scope first then global.
     *
     * @param owner Plugin context for resolution
     * @param name  Type name (case-insensitive)
     * @return The argument type, or null if not found
     */
    @Nullable
    public ArgumentType<?> getForPlugin(@NotNull Plugin owner, @NotNull String name) {
        String lowerName = name.toLowerCase(Locale.ROOT);
        // Check plugin scope first
        Map<String, ArgumentType<?>> pluginMap = pluginTypes.get(owner);
        if (pluginMap != null) {
            ArgumentType<?> pluginType = pluginMap.get(lowerName);
            if (pluginType != null) {
                return pluginType;
            }
        }
        // Fall back to global
        return types.get(lowerName);
    }

    /**
     * Unregisters all argument types for a specific plugin.
     *
     * @param owner Plugin to unregister types for
     * @return Number of types unregistered
     */
    public int unregisterAllForPlugin(@NotNull Plugin owner) {
        Map<String, ArgumentType<?>> removed = pluginTypes.remove(owner);
        return removed != null ? removed.size() : 0;
    }
}
