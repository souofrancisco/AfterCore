package com.afterlands.core.commands.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines additional aliases for a command or subcommand.
 *
 * <p>
 * This annotation can be used on:
 * </p>
 * <ul>
 * <li>Classes annotated with @Command - adds root command aliases</li>
 * <li>Methods annotated with @Subcommand - adds subcommand aliases</li>
 * </ul>
 *
 * <p>
 * Examples:
 * </p>
 * 
 * <pre>{@code
 * // Root command with aliases
 * &#64;Command(name = "teleport")
 * &#64;Alias({"tp", "tele"})
 * public class TeleportCommand { ... }
 *
 * // Subcommand with aliases
 * &#64;Subcommand("join")
 * &#64;Alias({"j", "joingroup"})
 * public void join(CommandContext ctx) { ... }
 *
 * // Alternative: Pipe syntax in @Subcommand (no @Alias needed)
 * &#64;Subcommand("join|j|joingroup")
 * public void join(CommandContext ctx) { ... }
 * }</pre>
 *
 * <p>
 * Aliases are merged with any aliases defined in {@code @Command(aliases={})}
 * or via pipe syntax in {@code @Subcommand("name|alias1|alias2")}.
 * </p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
public @interface Alias {

    /**
     * Additional alias names for the command or subcommand.
     */
    String[] value();
}
