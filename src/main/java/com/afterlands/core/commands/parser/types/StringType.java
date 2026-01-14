package com.afterlands.core.commands.parser.types;

import com.afterlands.core.commands.parser.ArgumentType;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Argument type for single string values.
 *
 * <p>Parses the next token as a string. Quoted strings are handled
 * by the ArgReader before reaching this parser.</p>
 */
public final class StringType implements ArgumentType<String> {

    public static final StringType INSTANCE = new StringType();

    private StringType() {}

    @Override
    @NotNull
    public String parse(@NotNull ParseContext ctx, @NotNull String input) {
        return input;
    }

    @Override
    @NotNull
    public List<String> suggest(@NotNull CommandSender sender, @NotNull String partial) {
        return List.of();
    }

    @Override
    @NotNull
    public String typeName() {
        return "string";
    }
}
