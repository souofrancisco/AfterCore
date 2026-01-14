package com.afterlands.core.commands.parser.types;

import com.afterlands.core.commands.parser.ArgumentType;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Argument type for boolean values.
 *
 * <p>Accepts: true, false, yes, no, on, off, 1, 0</p>
 */
public final class BooleanType implements ArgumentType<Boolean> {

    public static final BooleanType INSTANCE = new BooleanType();

    private static final Set<String> TRUE_VALUES = Set.of("true", "yes", "on", "1", "enable", "enabled");
    private static final Set<String> FALSE_VALUES = Set.of("false", "no", "off", "0", "disable", "disabled");

    private BooleanType() {}

    @Override
    @NotNull
    public Boolean parse(@NotNull ParseContext ctx, @NotNull String input) throws ParseException {
        String lower = input.toLowerCase(Locale.ROOT);

        if (TRUE_VALUES.contains(lower)) {
            return true;
        }
        if (FALSE_VALUES.contains(lower)) {
            return false;
        }

        throw new ParseException(input, "must be true/false, yes/no, on/off, or 1/0");
    }

    @Override
    @NotNull
    public List<String> suggest(@NotNull CommandSender sender, @NotNull String partial) {
        String lower = partial.toLowerCase(Locale.ROOT);
        return List.of("true", "false").stream()
                .filter(s -> s.startsWith(lower))
                .toList();
    }

    @Override
    @NotNull
    public String typeName() {
        return "boolean";
    }
}
