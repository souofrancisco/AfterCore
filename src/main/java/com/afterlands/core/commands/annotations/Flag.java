package com.afterlands.core.commands.annotations;

import org.jetbrains.annotations.NotNull;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a parameter as a command flag.
 *
 * <p>Flags are optional arguments that can appear anywhere in the command
 * with {@code --name} or {@code -s} syntax. Boolean flags don't require values,
 * while other types consume the next token as their value.</p>
 *
 * <p>Supported types:</p>
 * <ul>
 *   <li>{@code boolean/Boolean} - presence flag (--force or -f)</li>
 *   <li>{@code String} - valued flag (--message "text" or -m "text")</li>
 *   <li>{@code int/Integer} - integer flag (--page 2 or -p 2)</li>
 *   <li>{@code double/Double} - double flag (--multiplier 1.5)</li>
 * </ul>
 *
 * <p>Example:</p>
 * <pre>{@code
 * @Subcommand("teleport")
 * public void teleportCommand(
 *     CommandContext ctx,
 *     @Arg("player") Player target,
 *     @Flag(value = "force", shortName = "f") boolean force,
 *     @Flag(value = "silent", shortName = "s") boolean silent
 * ) {
 *     // Teleport with options
 * }
 * }</pre>
 *
 * <p>Usage: {@code /cmd teleport Player --force --silent} or {@code /cmd teleport Player -fs}</p>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Flag {

    /**
     * The flag name (used with --name syntax).
     *
     * <p>If not specified, the parameter name is used.</p>
     *
     * @return The flag name
     */
    @NotNull String value() default "";

    /**
     * Short name for the flag (single character, used with -x syntax).
     *
     * <p>Example: {@code shortName = "f"} allows {@code -f} as alias for {@code --force}</p>
     *
     * @return The short name
     */
    @NotNull String shortName() default "";

    /**
     * Default value if flag is not provided.
     *
     * <p>For boolean flags, defaults to false if not specified.
     * For valued flags, this is the default value string.</p>
     *
     * @return The default value as string
     */
    @NotNull String defaultValue() default "";

    /**
     * Description for help/usage generation.
     *
     * @return The flag description
     */
    @NotNull String description() default "";
}
