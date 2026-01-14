package com.afterlands.core.commands.parser.types;

import com.afterlands.core.commands.parser.ArgumentType;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Argument type for greedy strings that consume all remaining tokens.
 *
 * <p>This type captures everything from the current position to the end
 * of the input. Useful for messages, reasons, descriptions, etc.</p>
 *
 * <p>Important: Greedy arguments must be the last positional argument.</p>
 */
public final class GreedyStringType implements ArgumentType<String> {

    public static final GreedyStringType INSTANCE = new GreedyStringType();

    private GreedyStringType() {}

    @Override
    @NotNull
    public String parse(@NotNull ParseContext ctx, @NotNull String input) {
        // The input is already the first token, but we want remaining
        // This is handled specially by the parser - it joins remaining tokens
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
        return "text";
    }

    @Override
    public boolean isGreedy() {
        return true;
    }
}
