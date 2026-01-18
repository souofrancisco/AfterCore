package com.afterlands.core.commands.parser.types;

import com.afterlands.core.commands.parser.ArgumentType;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Argument type for integer values.
 *
 * <p>
 * Supports optional min/max bounds validation.
 * </p>
 */
public final class IntegerType implements ArgumentType<Integer> {

    public static final IntegerType INSTANCE = new IntegerType(Integer.MIN_VALUE, Integer.MAX_VALUE);

    private final int min;
    private final int max;

    /**
     * Creates an unbounded integer type.
     */
    public IntegerType() {
        this(Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    /**
     * Creates a bounded integer type.
     *
     * @param min Minimum value (inclusive)
     * @param max Maximum value (inclusive)
     */
    public IntegerType(int min, int max) {
        this.min = min;
        this.max = max;
    }

    /**
     * Creates an integer type with minimum bound.
     *
     * @param min Minimum value
     * @return Bounded IntegerType
     */
    @NotNull
    public static IntegerType min(int min) {
        return new IntegerType(min, Integer.MAX_VALUE);
    }

    /**
     * Creates an integer type with maximum bound.
     *
     * @param max Maximum value
     * @return Bounded IntegerType
     */
    @NotNull
    public static IntegerType max(int max) {
        return new IntegerType(Integer.MIN_VALUE, max);
    }

    /**
     * Creates an integer type with range bounds.
     *
     * @param min Minimum value
     * @param max Maximum value
     * @return Bounded IntegerType
     */
    @NotNull
    public static IntegerType range(int min, int max) {
        return new IntegerType(min, max);
    }

    @Override
    @NotNull
    public Integer parse(@NotNull ParseContext ctx, @NotNull String input) throws ParseException {
        try {
            int value = Integer.parseInt(input);

            if (value < min) {
                throw new ParseException(input, "number-out-of-range:" + min + ":" + max);
            }
            if (value > max) {
                throw new ParseException(input, "number-out-of-range:" + min + ":" + max);
            }

            return value;
        } catch (NumberFormatException e) {
            throw new ParseException(input, "invalid-number");
        }
    }

    @Override
    @NotNull
    public List<String> suggest(@NotNull CommandSender sender, @NotNull String partial) {
        // Suggest common values if within bounds
        if (min >= 0 && max <= 100) {
            return List.of("1", "5", "10", "25", "50", "100").stream()
                    .filter(s -> s.startsWith(partial))
                    .filter(s -> {
                        int v = Integer.parseInt(s);
                        return v >= min && v <= max;
                    })
                    .toList();
        }
        return List.of();
    }

    @Override
    @NotNull
    public String typeName() {
        if (min != Integer.MIN_VALUE && max != Integer.MAX_VALUE) {
            return "integer(" + min + "-" + max + ")";
        }
        if (min != Integer.MIN_VALUE) {
            return "integer(>=" + min + ")";
        }
        if (max != Integer.MAX_VALUE) {
            return "integer(<=" + max + ")";
        }
        return "integer";
    }
}
