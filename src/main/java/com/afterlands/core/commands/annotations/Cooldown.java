package com.afterlands.core.commands.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Defines a cooldown for a subcommand.
 *
 * <p>
 * When a player executes a command with a cooldown, they must wait
 * for the specified duration before executing it again. Console is
 * always exempt from cooldowns.
 * </p>
 *
 * <p>
 * Example:
 * </p>
 * 
 * <pre>
 * {@code
 * &#64;Subcommand("teleport")
 * &#64;Cooldown(value = 5, unit = TimeUnit.SECONDS)
 * public void teleport(CommandContext ctx, @Arg("target") Player target) {
 *     // Teleport to target
 * }
 *
 * // With custom message and bypass permission
 * &#64;Subcommand("heal")
 * @Cooldown(value = 30, message = "heal.cooldown", bypassPermission = "core.heal.bypass")
 * public void heal(CommandContext ctx) {
 *     // Heal player
 * }
 * }
 * </pre>
 *
 * <p>
 * Message resolution: Plugin messages.yml → AfterCore messages.yml → default.
 * Available placeholders: {remaining} (formatted time), {command}.
 * </p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Cooldown {

    /**
     * Cooldown duration value.
     */
    long value();

    /**
     * Time unit for the cooldown duration.
     * Default is SECONDS.
     */
    TimeUnit unit() default TimeUnit.SECONDS;

    /**
     * Message key to send when player is on cooldown.
     *
     * <p>
     * If empty, uses "commands.cooldown" which is looked up
     * in the plugin's messages.yml first, then AfterCore's.
     * </p>
     *
     * <p>
     * Placeholders: {remaining}, {command}
     * </p>
     */
    String message() default "";

    /**
     * Permission node that bypasses this cooldown.
     *
     * <p>
     * Players with this permission are not subject to the cooldown.
     * If empty, no bypass is available.
     * </p>
     */
    String bypassPermission() default "";
}
