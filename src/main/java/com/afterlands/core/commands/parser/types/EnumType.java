package com.afterlands.core.commands.parser.types;

import com.afterlands.core.commands.parser.ArgumentType;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Argument type for enum values.
 *
 * <p>Parses enum constants with case-insensitive matching.
 * Provides tab completion for all enum values.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * ArgumentType<GameMode> gameModeType = EnumType.of(GameMode.class);
 * }</pre>
 *
 * @param <E> The enum type
 */
public final class EnumType<E extends Enum<E>> implements ArgumentType<E> {

    private final Class<E> enumClass;
    private final Map<String, E> valueMap;
    private final List<String> sortedNames;

    private EnumType(@NotNull Class<E> enumClass) {
        this.enumClass = enumClass;

        E[] constants = enumClass.getEnumConstants();
        Map<String, E> map = new LinkedHashMap<>();
        List<String> names = new ArrayList<>();

        for (E constant : constants) {
            String name = constant.name().toLowerCase(Locale.ROOT);
            map.put(name, constant);
            names.add(constant.name().toLowerCase(Locale.ROOT));
        }

        this.valueMap = Collections.unmodifiableMap(map);
        this.sortedNames = Collections.unmodifiableList(names);
    }

    /**
     * Creates an EnumType for the given enum class.
     *
     * @param enumClass The enum class
     * @param <E>       The enum type
     * @return A new EnumType
     */
    @NotNull
    public static <E extends Enum<E>> EnumType<E> of(@NotNull Class<E> enumClass) {
        return new EnumType<>(enumClass);
    }

    @Override
    @NotNull
    public E parse(@NotNull ParseContext ctx, @NotNull String input) throws ParseException {
        String lower = input.toLowerCase(Locale.ROOT);
        E value = valueMap.get(lower);

        if (value == null) {
            throw new ParseException(input, "must be one of: " + String.join(", ", sortedNames));
        }

        return value;
    }

    @Override
    @NotNull
    public List<String> suggest(@NotNull CommandSender sender, @NotNull String partial) {
        String lower = partial.toLowerCase(Locale.ROOT);
        return sortedNames.stream()
                .filter(name -> name.startsWith(lower))
                .toList();
    }

    @Override
    @NotNull
    public String typeName() {
        return enumClass.getSimpleName().toLowerCase(Locale.ROOT);
    }

    /**
     * Returns the enum class.
     */
    @NotNull
    public Class<E> enumClass() {
        return enumClass;
    }

    /**
     * Returns all valid values.
     */
    @NotNull
    public List<String> values() {
        return sortedNames;
    }
}
