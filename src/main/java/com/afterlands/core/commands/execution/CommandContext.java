package com.afterlands.core.commands.execution;

import com.afterlands.core.commands.messages.MessageFacade;
import com.afterlands.core.concurrent.SchedulerService;
import com.afterlands.core.metrics.MetricsService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Context object passed to command executors.
 *
 * <p>This immutable context provides all necessary information and services
 * for command execution, including:</p>
 * <ul>
 *   <li>The command sender and label</li>
 *   <li>Parsed arguments and flags</li>
 *   <li>Services for messaging, scheduling, and metrics</li>
 *   <li>Convenience methods for common operations</li>
 * </ul>
 *
 * <p>Thread Safety: This context is created on the main thread and should
 * only be used on the main thread. For async operations, use the provided
 * {@link #runAsync(Supplier)} method which properly handles thread context.</p>
 *
 * @param owner     The plugin that owns this command
 * @param sender    The command sender
 * @param label     The label used to invoke the command
 * @param args      Parsed positional arguments
 * @param flags     Parsed flags
 * @param messages  Message facade for sending localized messages
 * @param scheduler Scheduler service for async operations
 * @param metrics   Metrics service for recording command metrics
 */
public record CommandContext(
        @NotNull Plugin owner,
        @NotNull CommandSender sender,
        @NotNull String label,
        @NotNull ParsedArgs args,
        @NotNull ParsedFlags flags,
        @NotNull MessageFacade messages,
        @NotNull SchedulerService scheduler,
        @NotNull MetricsService metrics
) {
    /**
     * Checks if the sender is a player.
     *
     * @return true if the sender is a Player instance
     */
    public boolean isPlayer() {
        return sender instanceof Player;
    }

    /**
     * Gets the sender as a Player, if applicable.
     *
     * @return Optional containing the Player, or empty if sender is not a player
     */
    @NotNull
    public Optional<Player> player() {
        return sender instanceof Player p ? Optional.of(p) : Optional.empty();
    }

    /**
     * Gets the sender as a Player, throwing if not a player.
     *
     * <p>Only use this when you've already verified the sender is a player
     * (e.g., in playerOnly commands).</p>
     *
     * @return The sender as a Player
     * @throws IllegalStateException if sender is not a player
     */
    @NotNull
    public Player requirePlayer() {
        if (sender instanceof Player p) {
            return p;
        }
        throw new IllegalStateException("Command requires a player sender");
    }

    /**
     * Sends a message using the message facade.
     *
     * @param path Message path in the messages configuration
     */
    public void send(@NotNull String path) {
        messages.send(sender, path);
    }

    /**
     * Sends a message with placeholder replacements.
     *
     * @param path         Message path in the messages configuration
     * @param placeholders Placeholder key-value pairs (must be even number)
     */
    public void send(@NotNull String path, @NotNull Object... placeholders) {
        messages.send(sender, path, placeholders);
    }

    /**
     * Sends a raw message (no lookup, just color formatting).
     *
     * @param message The raw message to send
     */
    public void sendRaw(@NotNull String message) {
        messages.sendRaw(sender, message);
    }

    /**
     * Runs an operation asynchronously and returns a future.
     *
     * <p>The operation runs on the IO executor. If you need to interact
     * with Bukkit API after the async operation, chain with
     * {@code thenAcceptSync} or use {@link SchedulerService#runSync}.</p>
     *
     * @param operation The operation to run
     * @param <T>       The result type
     * @return A CompletableFuture with the result
     */
    @NotNull
    public <T> CompletableFuture<T> runAsync(@NotNull Supplier<T> operation) {
        return CompletableFuture.supplyAsync(operation, scheduler.ioExecutor());
    }

    /**
     * Runs an operation asynchronously without a result.
     *
     * @param operation The operation to run
     * @return A CompletableFuture that completes when the operation is done
     */
    @NotNull
    public CompletableFuture<Void> runAsync(@NotNull Runnable operation) {
        return CompletableFuture.runAsync(operation, scheduler.ioExecutor());
    }

    /**
     * Schedules a task to run on the main thread.
     *
     * @param task The task to run
     * @return A CompletableFuture that completes when the task is done
     */
    @NotNull
    public CompletableFuture<Void> runSync(@NotNull Runnable task) {
        return scheduler.runSync(task);
    }

    // Convenience methods for common argument access

    /**
     * Gets a string argument by name.
     *
     * @param name Argument name
     * @return The argument value, or null if not present
     */
    @Nullable
    public String argString(@NotNull String name) {
        return args.getString(name);
    }

    /**
     * Gets an integer argument by name.
     *
     * @param name Argument name
     * @return The argument value
     * @throws IllegalArgumentException if argument is missing or not an integer
     */
    public int argInt(@NotNull String name) {
        return args.getInt(name);
    }

    /**
     * Gets an integer argument by name with a default value.
     *
     * @param name         Argument name
     * @param defaultValue Default value if argument is missing
     * @return The argument value or default
     */
    public int argInt(@NotNull String name, int defaultValue) {
        return args.getInt(name, defaultValue);
    }

    /**
     * Gets a double argument by name.
     *
     * @param name Argument name
     * @return The argument value
     * @throws IllegalArgumentException if argument is missing or not a double
     */
    public double argDouble(@NotNull String name) {
        return args.getDouble(name);
    }

    /**
     * Gets a double argument by name with a default value.
     *
     * @param name         Argument name
     * @param defaultValue Default value if argument is missing
     * @return The argument value or default
     */
    public double argDouble(@NotNull String name, double defaultValue) {
        return args.getDouble(name, defaultValue);
    }

    /**
     * Checks if a boolean flag is set.
     *
     * @param name Flag name (without -- prefix)
     * @return true if the flag is present
     */
    public boolean hasFlag(@NotNull String name) {
        return flags.has(name);
    }

    /**
     * Gets a flag value.
     *
     * @param name Flag name (without -- prefix)
     * @return The flag value, or null if not present or has no value
     */
    @Nullable
    public String flagValue(@NotNull String name) {
        return flags.getValue(name);
    }

    /**
     * Gets a flag value as integer.
     *
     * @param name         Flag name
     * @param defaultValue Default if flag not present or not an integer
     * @return The flag value or default
     */
    public int flagInt(@NotNull String name, int defaultValue) {
        return flags.getInt(name, defaultValue);
    }

    /**
     * Creates a new builder for constructing CommandContext instances.
     *
     * @return A new builder
     */
    @NotNull
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for CommandContext.
     */
    public static final class Builder {
        private Plugin owner;
        private CommandSender sender;
        private String label;
        private ParsedArgs args = ParsedArgs.empty();
        private ParsedFlags flags = ParsedFlags.empty();
        private MessageFacade messages;
        private SchedulerService scheduler;
        private MetricsService metrics;

        private Builder() {}

        @NotNull
        public Builder owner(@NotNull Plugin owner) {
            this.owner = owner;
            return this;
        }

        @NotNull
        public Builder sender(@NotNull CommandSender sender) {
            this.sender = sender;
            return this;
        }

        @NotNull
        public Builder label(@NotNull String label) {
            this.label = label;
            return this;
        }

        @NotNull
        public Builder args(@NotNull ParsedArgs args) {
            this.args = args;
            return this;
        }

        @NotNull
        public Builder flags(@NotNull ParsedFlags flags) {
            this.flags = flags;
            return this;
        }

        @NotNull
        public Builder messages(@NotNull MessageFacade messages) {
            this.messages = messages;
            return this;
        }

        @NotNull
        public Builder scheduler(@NotNull SchedulerService scheduler) {
            this.scheduler = scheduler;
            return this;
        }

        @NotNull
        public Builder metrics(@NotNull MetricsService metrics) {
            this.metrics = metrics;
            return this;
        }

        @NotNull
        public CommandContext build() {
            return new CommandContext(
                    owner, sender, label, args, flags, messages, scheduler, metrics
            );
        }
    }
}
