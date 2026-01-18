package com.afterlands.core.commands.parser.types;

import com.afterlands.core.commands.parser.ArgumentType;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * Argument type for online player names.
 *
 * <p>
 * Resolves player names to online Player instances with
 * case-insensitive matching. Provides tab completion for all
 * online players visible to the sender.
 * </p>
 */
public final class PlayerOnlineType implements ArgumentType<Player> {

    public static final PlayerOnlineType INSTANCE = new PlayerOnlineType();

    private PlayerOnlineType() {
    }

    @Override
    @NotNull
    public Player parse(@NotNull ParseContext ctx, @NotNull String input) throws ParseException {
        // Try exact match first
        Player exact = Bukkit.getPlayerExact(input);
        if (exact != null) {
            return exact;
        }

        // Try case-insensitive match
        String lower = input.toLowerCase(Locale.ROOT);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).equals(lower)) {
                return player;
            }
        }

        throw new ParseException(input, "player-not-online");
    }

    @Override
    @NotNull
    public List<String> suggest(@NotNull CommandSender sender, @NotNull String partial) {
        String lower = partial.toLowerCase(Locale.ROOT);

        return Bukkit.getOnlinePlayers().stream()
                .filter(p -> {
                    // Filter by visibility if sender is a player
                    if (sender instanceof Player senderPlayer) {
                        return senderPlayer.canSee(p);
                    }
                    return true;
                })
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(lower))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    @Override
    @NotNull
    public String typeName() {
        return "player";
    }
}
