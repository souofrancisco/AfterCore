package com.afterlands.core.commands.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a parameter as the command sender.
 *
 * <p>This annotation allows direct injection of the sender with proper typing.
 * The parameter type determines the sender type check:</p>
 * <ul>
 *   <li>{@code CommandSender} - any sender</li>
 *   <li>{@code Player} - must be a player (enforces player-only)</li>
 *   <li>{@code ConsoleCommandSender} - must be console</li>
 * </ul>
 *
 * <p>Example:</p>
 * <pre>{@code
 * @Subcommand("stats")
 * public void statsCommand(
 *     CommandContext ctx,
 *     @Sender Player player  // Automatically enforces player-only
 * ) {
 *     // Show player stats
 * }
 * }</pre>
 *
 * <p>Note: If you already have {@code CommandContext} parameter, you can use
 * {@code ctx.sender()} or {@code ctx.requirePlayer()} instead.</p>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Sender {
}
