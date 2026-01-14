package com.afterlands.core.commands.parser.types;

import com.afterlands.core.commands.parser.ArgumentType;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Argument type for double (decimal) values.
 *
 * <p>Supports optional min/max bounds validation.</p>
 */
public final class DoubleType implements ArgumentType<Double> {

    public static final DoubleType INSTANCE = new DoubleType(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);

    private final double min;
    private final double max;

    /**
     * Creates an unbounded double type.
     */
    public DoubleType() {
        this(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
    }

    /**
     * Creates a bounded double type.
     *
     * @param min Minimum value (inclusive)
     * @param max Maximum value (inclusive)
     */
    public DoubleType(double min, double max) {
        this.min = min;
        this.max = max;
    }

    /**
     * Creates a double type with minimum bound.
     */
    @NotNull
    public static DoubleType min(double min) {
        return new DoubleType(min, Double.POSITIVE_INFINITY);
    }

    /**
     * Creates a double type with maximum bound.
     */
    @NotNull
    public static DoubleType max(double max) {
        return new DoubleType(Double.NEGATIVE_INFINITY, max);
    }

    /**
     * Creates a double type with range bounds.
     */
    @NotNull
    public static DoubleType range(double min, double max) {
        return new DoubleType(min, max);
    }

    @Override
    @NotNull
    public Double parse(@NotNull ParseContext ctx, @NotNull String input) throws ParseException {
        try {
            double value = Double.parseDouble(input);

            if (Double.isNaN(value)) {
                throw new ParseException(input, "cannot be NaN");
            }

            if (value < min) {
                throw new ParseException(input, "must be at least " + min);
            }
            if (value > max) {
                throw new ParseException(input, "must be at most " + max);
            }

            return value;
        } catch (NumberFormatException e) {
            throw new ParseException(input, "not a valid number");
        }
    }

    @Override
    @NotNull
    public List<String> suggest(@NotNull CommandSender sender, @NotNull String partial) {
        return List.of();
    }

    @Override
    @NotNull
    public String typeName() {
        if (Double.isFinite(min) && Double.isFinite(max)) {
            return "number(" + min + "-" + max + ")";
        }
        if (Double.isFinite(min)) {
            return "number(>=" + min + ")";
        }
        if (Double.isFinite(max)) {
            return "number(<=" + max + ")";
        }
        return "number";
    }
}
