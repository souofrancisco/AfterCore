package com.afterlands.core.examples;

import com.afterlands.core.commands.annotations.*;
import com.afterlands.core.commands.execution.CommandContext;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Example command demonstrating all features of the Command Framework.
 *
 * <p>This command shows:</p>
 * <ul>
 *   <li>Annotation-based command definition</li>
 *   <li>Typed argument injection (@Arg)</li>
 *   <li>Flag support (@Flag)</li>
 *   <li>Sender injection (@Sender)</li>
 *   <li>Async operations</li>
 *   <li>Message sending</li>
 * </ul>
 *
 * <p>Usage examples:</p>
 * <pre>
 * /example help
 * /example info
 * /example give Player DIAMOND 64
 * /example teleport Player world -f
 * /example gamemode Player CREATIVE --silent
 * </pre>
 */
@Command(name = "example", aliases = {"ex", "demo"})
@Permission("example.use")
public class ExampleCommand {

    /**
     * Default executor - shows help when no subcommand is provided.
     */
    @Subcommand("default")
    public void defaultCommand(CommandContext ctx) {
        ctx.sendRaw("&6=== Example Command ===");
        ctx.sendRaw("&e/example info &7- Show info");
        ctx.sendRaw("&e/example give <player> <item> <amount> &7- Give items");
        ctx.sendRaw("&e/example teleport <player> <world> [-f] &7- Teleport");
        ctx.sendRaw("&e/example gamemode <player> <mode> [--silent] &7- Change gamemode");
    }

    /**
     * Simple info command - no arguments.
     */
    @Subcommand("info")
    @Permission("example.info")
    public void info(CommandContext ctx) {
        ctx.sendRaw("&aAfterCore Command Framework v1.0");
        ctx.sendRaw("&7Running on " + ctx.owner().getName());
        ctx.sendRaw("&7Sender: " + ctx.sender().getName());
    }

    /**
     * Give command - demonstrates typed arguments.
     *
     * @param ctx    Command context
     * @param target Target player (required, auto-validated)
     * @param item   Item type (required string)
     * @param amount Amount to give (required int, auto-parsed)
     */
    @Subcommand("give")
    @Permission("example.give")
    public void give(
            CommandContext ctx,
            @Arg("player") Player target,
            @Arg("item") String item,
            @Arg("amount") int amount
    ) {
        // Validation
        if (amount <= 0 || amount > 64) {
            ctx.sendRaw("&cAmount must be between 1 and 64");
            return;
        }

        // Simulate giving (real implementation would use ItemStack)
        ctx.sendRaw("&aGave &f" + amount + " " + item + " &ato &f" + target.getName());
        target.sendMessage("§aYou received " + amount + " " + item);
    }

    /**
     * Teleport command - demonstrates flags.
     *
     * @param ctx    Command context
     * @param target Target player
     * @param world  Target world
     * @param force  Force teleport (bypasses safety checks)
     */
    @Subcommand("teleport")
    @Permission("example.teleport")
    public void teleport(
            CommandContext ctx,
            @Arg("player") Player target,
            @Arg("world") World world,
            @Flag(value = "force", shortName = "f") boolean force
    ) {
        // Check if force is needed
        if (!force && target.getWorld().equals(world)) {
            ctx.sendRaw("&ePlayer is already in that world. Use &f--force &eto teleport anyway.");
            return;
        }

        // Teleport
        target.teleport(world.getSpawnLocation());
        ctx.sendRaw("&aTeleported &f" + target.getName() + " &ato &f" + world.getName());

        if (force) {
            ctx.sendRaw("&7(forced)");
        }
    }

    /**
     * Gamemode command - demonstrates @Sender and enum arguments.
     *
     * @param ctx    Command context
     * @param player Sender (must be player)
     * @param target Target player
     * @param mode   GameMode (auto tab-completed)
     * @param silent Silent mode flag
     */
    @Subcommand("gamemode")
    @Permission("example.gamemode")
    public void gamemode(
            CommandContext ctx,
            @Sender Player player,
            @Arg("player") Player target,
            @Arg("mode") GameMode mode,
            @Flag(value = "silent", shortName = "s") boolean silent
    ) {
        target.setGameMode(mode);

        if (!silent) {
            ctx.sendRaw("&aSet &f" + target.getName() + " &ato &f" + mode.name());
            target.sendMessage("§aYour gamemode has been changed to " + mode.name());
        } else {
            ctx.sendRaw("&7(silent)");
        }
    }

    /**
     * Async command - demonstrates async operations.
     *
     * <p>This command simulates a slow database lookup.</p>
     */
    @Subcommand("lookup")
    @Permission("example.lookup")
    public void lookup(
            CommandContext ctx,
            @Arg("player") String playerName
    ) {
        ctx.sendRaw("&eLooking up player &f" + playerName + "&e...");

        // Simulate async database lookup
        ctx.runAsync(() -> {
            try {
                // Simulate slow operation
                Thread.sleep(1000);
                return "PlayerData{name=" + playerName + ", level=42, coins=1000}";
            } catch (InterruptedException e) {
                return null;
            }
        }).thenAccept(result -> {
            // Back on main thread
            if (result != null) {
                ctx.sendRaw("&aFound: &f" + result);
            } else {
                ctx.sendRaw("&cLookup failed");
            }
        });
    }

    /**
     * Optional arguments - demonstrates default values.
     */
    @Subcommand("broadcast")
    @Permission("example.broadcast")
    public void broadcast(
            CommandContext ctx,
            @Arg("message") String message,
            @Arg(value = "prefix", defaultValue = "[Broadcast]") String prefix
    ) {
        String formatted = "&6" + prefix + " &f" + message;
        ctx.sendRaw(formatted);
        // Would broadcast to all players in real implementation
    }

    /**
     * Complex flags - demonstrates valued flags.
     */
    @Subcommand("search")
    @Permission("example.search")
    public void search(
            CommandContext ctx,
            @Arg("query") String query,
            @Flag(value = "limit", shortName = "l") int limit,
            @Flag(value = "case-sensitive", shortName = "c") boolean caseSensitive,
            @Flag(value = "format", shortName = "f") String format
    ) {
        ctx.sendRaw("&eSearching for: &f" + query);
        ctx.sendRaw("&7Limit: " + (limit > 0 ? limit : "unlimited"));
        ctx.sendRaw("&7Case sensitive: " + caseSensitive);
        ctx.sendRaw("&7Format: " + (format != null ? format : "default"));

        // Usage examples:
        // /example search "hello world" --limit 10 --case-sensitive
        // /example search "test" -l 5 -c -f json
    }
}
