package com.afterlands.core.commands.parser.types;

import com.afterlands.core.commands.parser.ArgumentType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Argument type for player names that may be offline.
 *
 * <p>
 * Resolves player names to OfflinePlayer instances. This type can
 * find players who have previously joined the server, even if they
 * are currently offline. Uses Bukkit's offline player cache.
 * </p>
 *
 * <p>
 * Useful for admin commands that need to operate on players
 * who are not currently online (e.g., bans, economy adjustments).
 * </p>
 *
 * <p>
 * Tab completion only shows online players for practical reasons.
 * </p>
 */
public final class PlayerOfflineType implements ArgumentType<OfflinePlayer> {

    public static final PlayerOfflineType INSTANCE = new PlayerOfflineType();

    private PlayerOfflineType() {
    }

    @Override
    @NotNull
    public OfflinePlayer parse(@NotNull ParseContext ctx, @NotNull String input) throws ParseException {
        // Try to find by UUID first (if input looks like a UUID)
        if (input.length() == 36 && input.contains("-")) {
            try {
                UUID uuid = UUID.fromString(input);
                OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                if (player.hasPlayedBefore() || player.isOnline()) {
                    return player;
                }
            } catch (IllegalArgumentException ignored) {
                // Not a valid UUID, continue with name lookup
            }
        }

        // Try online player first (faster)
        Player online = Bukkit.getPlayerExact(input);
        if (online != null) {
            return online;
        }

        // Case-insensitive online search
        String lower = input.toLowerCase(Locale.ROOT);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).equals(lower)) {
                return player;
            }
        }

        // Get offline player by name - this creates a new OfflinePlayer
        // if not found, so we check hasPlayedBefore()
        @SuppressWarnings("deprecation")
        OfflinePlayer offline = Bukkit.getOfflinePlayer(input);

        if (offline.hasPlayedBefore()) {
            return offline;
        }

        // Player never joined the server
        throw new ParseException(input, "player-never-joined");
    }

    @Override
    @NotNull
    public List<String> suggest(@NotNull CommandSender sender, @NotNull String partial) {
        String lower = partial.toLowerCase(Locale.ROOT);

        // Only suggest online players for practical reasons
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
        return "player (offline)";
    }
}
