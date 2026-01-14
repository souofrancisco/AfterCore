package com.afterlands.core.commands.binding;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Interface for command execution and tab completion dispatch.
 *
 * <p>Implementations handle the actual command logic, including:</p>
 * <ul>
 *   <li>Subcommand resolution</li>
 *   <li>Argument parsing</li>
 *   <li>Permission checking</li>
 *   <li>Executor invocation</li>
 *   <li>Tab completion</li>
 * </ul>
 *
 * <p>This interface separates the Bukkit command binding from the
 * framework's internal command execution logic.</p>
 */
public interface CommandDispatcher {

    /**
     * Dispatches command execution.
     *
     * @param sender  The command sender
     * @param label   The command label used
     * @param args    The command arguments
     * @return true if the command was handled (regardless of success)
     */
    boolean dispatch(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args);

    /**
     * Provides tab completion suggestions.
     *
     * @param sender The command sender
     * @param label  The command label used
     * @param args   The current arguments
     * @return List of suggestions, never null
     */
    @NotNull
    List<String> tabComplete(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args);
}
