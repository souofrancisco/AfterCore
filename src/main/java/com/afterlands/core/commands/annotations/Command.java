package com.afterlands.core.commands.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a command handler.
 *
 * <p>The annotated class should contain methods annotated with {@link Subcommand}
 * that define the command's subcommands and behavior.</p>
 *
 * <p>Example:</p>
 * <pre>{@code
 * @Command(name = "myplugin", aliases = {"mp"}, description = "Main plugin command")
 * @Permission("myplugin.use")
 * public class MyPluginCommand {
 *
 *     @Subcommand("reload")
 *     @Permission("myplugin.reload")
 *     public void reload(CommandContext ctx) {
 *         ctx.send("messages.reloaded");
 *     }
 *
 *     @Subcommand("give")
 *     @Description("Give items to a player")
 *     public void give(CommandContext ctx, @Arg("player") Player target, @Arg("amount") int amount) {
 *         // ...
 *     }
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Command {

    /**
     * The primary command name (without /).
     */
    String name();

    /**
     * Alternative names for this command.
     */
    String[] aliases() default {};

    /**
     * Description shown in help output.
     */
    String description() default "";

    /**
     * Custom usage string. If empty, auto-generated.
     */
    String usage() default "";
}

