package com.afterlands.core.commands.parser.types;

import com.afterlands.core.commands.parser.ArgumentType;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * Argument type for world names.
 *
 * <p>
 * Resolves world names to World instances with case-insensitive matching.
 * </p>
 */
public final class WorldType implements ArgumentType<World> {

    public static final WorldType INSTANCE = new WorldType();

    private WorldType() {
    }

    @Override
    @NotNull
    public World parse(@NotNull ParseContext ctx, @NotNull String input) throws ParseException {
        // Try exact match
        World world = Bukkit.getWorld(input);
        if (world != null) {
            return world;
        }

        // Try case-insensitive match
        String lower = input.toLowerCase(Locale.ROOT);
        for (World w : Bukkit.getWorlds()) {
            if (w.getName().toLowerCase(Locale.ROOT).equals(lower)) {
                return w;
            }
        }

        throw new ParseException(input, "world-not-found");
    }

    @Override
    @NotNull
    public List<String> suggest(@NotNull CommandSender sender, @NotNull String partial) {
        String lower = partial.toLowerCase(Locale.ROOT);

        return Bukkit.getWorlds().stream()
                .map(World::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(lower))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    @Override
    @NotNull
    public String typeName() {
        return "world";
    }
}
