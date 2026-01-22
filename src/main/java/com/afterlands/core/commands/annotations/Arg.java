package com.afterlands.core.commands.annotations;

import org.jetbrains.annotations.NotNull;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a parameter as a command argument.
 *
 * <p>
 * This annotation is used on method parameters in {@code @Subcommand} methods
 * to automatically parse and inject command arguments. The parameter type
 * determines
 * the argument type:
 * </p>
 * <ul>
 * <li>{@code String} - string argument</li>
 * <li>{@code int/Integer} - integer argument</li>
 * <li>{@code double/Double} - double argument</li>
 * <li>{@code boolean/Boolean} - boolean argument</li>
 * <li>{@code Player} - online player argument</li>
 * <li>{@code World} - world argument</li>
 * <li>Enum types - enum argument with tab-complete</li>
 * </ul>
 *
 * <p>
 * Example:
 * </p>
 * 
 * <pre>
 * {@code
 * &#64;Subcommand("give")
 * public void giveCommand(
 *         CommandContext ctx,
 *         &#64;Arg("player") Player target,
 *         &#64;Arg("amount") int amount,
 *         @Arg(value = "reason", defaultValue = "Admin gift") String reason) {
 *     // Give items to player
 * }
 * 
 * // With custom type for tab completion:
 * &#64;Subcommand("setlock")
 * public void setLock(
 *         CommandContext ctx,
 *         &#64;Arg(value = "tier", type = "lock-tier") String tierId) {
 *     // Lock tier will have tab completion from registered "lock-tier" type
 * }
 * }
 * </pre>
 *
 * <p>
 * Arguments are parsed in order. Optional arguments (with defaultValue)
 * must come after required arguments.
 * </p>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Arg {

    /**
     * The argument name.
     *
     * <p>
     * Used for error messages and usage generation.
     * If not specified, the parameter name is used (requires -parameters compiler
     * flag).
     * </p>
     *
     * @return The argument name
     */
    @NotNull
    String value() default "";

    /**
     * Sentinel value for unset default value.
     */
    String NONE = "__NONE__";

    /**
     * Default value if argument is not provided.
     *
     * <p>
     * If specified, the argument becomes optional.
     * The default value string will be parsed using the argument's type parser.
     * </p>
     *
     * @return The default value as string
     */
    @NotNull
    String defaultValue() default NONE;

    /**
     * Helper to explicitly mark argument as optional.
     *
     * <p>
     * If true, the argument is optional even if no defaultValue is provided.
     * If false (default), it is optional only if defaultValue is provided.
     * </p>
     *
     * @return True if argument is optional
     */
    boolean optional() default false;

    /**
     * Description for help/usage generation.
     *
     * @return The argument description
     */
    @NotNull
    String description() default "";

    /**
     * Custom argument type name for tab completion.
     *
     * <p>
     * If specified, this type name will be used instead of inferring from
     * the Java parameter type. This allows using custom types registered via
     * {@code ArgumentTypeRegistry.registerForPlugin()}.
     * </p>
     *
     * <p>
     * Example: {@code @Arg(value = "tier", type = "lock-tier")} will use
     * the "lock-tier" type for parsing and tab completion.
     * </p>
     *
     * @return The custom type name, or empty to infer from Java type
     */
    @NotNull
    String type() default "";
}
