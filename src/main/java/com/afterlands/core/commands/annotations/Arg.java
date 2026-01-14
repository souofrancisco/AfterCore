package com.afterlands.core.commands.annotations;

import org.jetbrains.annotations.NotNull;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a parameter as a command argument.
 *
 * <p>This annotation is used on method parameters in {@code @Subcommand} methods
 * to automatically parse and inject command arguments. The parameter type determines
 * the argument type:</p>
 * <ul>
 *   <li>{@code String} - string argument</li>
 *   <li>{@code int/Integer} - integer argument</li>
 *   <li>{@code double/Double} - double argument</li>
 *   <li>{@code boolean/Boolean} - boolean argument</li>
 *   <li>{@code Player} - online player argument</li>
 *   <li>{@code World} - world argument</li>
 *   <li>Enum types - enum argument with tab-complete</li>
 * </ul>
 *
 * <p>Example:</p>
 * <pre>{@code
 * @Subcommand("give")
 * public void giveCommand(
 *     CommandContext ctx,
 *     @Arg("player") Player target,
 *     @Arg("amount") int amount,
 *     @Arg(value = "reason", defaultValue = "Admin gift") String reason
 * ) {
 *     // Give items to player
 * }
 * }</pre>
 *
 * <p>Arguments are parsed in order. Optional arguments (with defaultValue)
 * must come after required arguments.</p>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Arg {

    /**
     * The argument name.
     *
     * <p>Used for error messages and usage generation.
     * If not specified, the parameter name is used (requires -parameters compiler flag).</p>
     *
     * @return The argument name
     */
    @NotNull String value() default "";

    /**
     * Default value if argument is not provided.
     *
     * <p>If specified, the argument becomes optional.
     * The default value string will be parsed using the argument's type parser.</p>
     *
     * @return The default value as string
     */
    @NotNull String defaultValue() default "";

    /**
     * Description for help/usage generation.
     *
     * @return The argument description
     */
    @NotNull String description() default "";
}
