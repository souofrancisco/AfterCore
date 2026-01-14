package com.afterlands.core.commands.authoring;

import com.afterlands.core.commands.CommandSpec;
import com.afterlands.core.commands.annotations.*;
import com.afterlands.core.commands.execution.CommandContext;
import com.afterlands.core.commands.parser.ArgumentTypeRegistry;
import com.afterlands.core.commands.registry.nodes.CommandNode;
import com.afterlands.core.commands.registry.nodes.RootNode;
import com.afterlands.core.commands.registry.nodes.SubNode;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Processes annotated command classes to build RootNode trees.
 *
 * <p>This processor handles:</p>
 * <ul>
 *   <li>@Command classes and their @Subcommand methods</li>
 *   <li>@Arg parameter extraction and ArgumentSpec creation</li>
 *   <li>@Flag parameter extraction and FlagSpec creation</li>
 *   <li>@Sender parameter injection</li>
 *   <li>MethodHandle compilation via ParameterInjector</li>
 * </ul>
 *
 * <p>Performance: Reflection is used only during registration.
 * Execution uses compiled MethodHandles for zero-reflection overhead.</p>
 */
public final class AnnotationProcessor {

    private final Plugin owner;
    private final ArgumentTypeRegistry typeRegistry;
    private final ParameterInjector parameterInjector;
    private final Logger logger;
    private final boolean debug;

    /**
     * Creates a new AnnotationProcessor.
     *
     * @param owner        The owning plugin
     * @param typeRegistry The argument type registry
     * @param logger       Logger for debug output
     * @param debug        Whether debug mode is enabled
     */
    public AnnotationProcessor(@NotNull Plugin owner,
                                @NotNull ArgumentTypeRegistry typeRegistry,
                                @NotNull Logger logger,
                                boolean debug) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.typeRegistry = Objects.requireNonNull(typeRegistry, "typeRegistry");
        this.parameterInjector = new ParameterInjector(typeRegistry);
        this.logger = Objects.requireNonNull(logger, "logger");
        this.debug = debug;
    }

    /**
     * Processes an annotated handler class into a RootNode.
     *
     * @param handler The handler instance
     * @return A RootNode representing the command
     * @throws ProcessingException if processing fails
     */
    @NotNull
    public RootNode process(@NotNull Object handler) throws ProcessingException {
        Class<?> handlerClass = handler.getClass();
        Command cmdAnnotation = handlerClass.getAnnotation(Command.class);

        if (cmdAnnotation == null) {
            throw new ProcessingException("Handler class must be annotated with @Command: "
                    + handlerClass.getName());
        }

        if (debug) {
            logger.info("[Commands] Processing annotated handler: " + handlerClass.getName());
        }

        try {
            return buildRoot(handler, handlerClass, cmdAnnotation);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[Commands] Failed to process handler: " + handlerClass.getName(), e);
            throw new ProcessingException("Failed to process command handler", e);
        }
    }

    @NotNull
    private RootNode buildRoot(Object handler, Class<?> handlerClass, Command cmdAnnotation) throws Exception {
        String rootName = cmdAnnotation.name().toLowerCase(Locale.ROOT);

        // Get root permission
        Permission rootPerm = handlerClass.getAnnotation(Permission.class);
        String rootPermission = rootPerm != null ? rootPerm.value() : null;

        // Process subcommand methods
        Map<String, SubNode> children = new LinkedHashMap<>();
        CommandNode.CompiledExecutor defaultExecutor = null;
        List<CommandSpec.ArgumentSpec> rootArgs = new ArrayList<>();
        List<CommandSpec.FlagSpec> rootFlags = new ArrayList<>();

        for (Method method : handlerClass.getDeclaredMethods()) {
            Subcommand subAnnotation = method.getAnnotation(Subcommand.class);
            if (subAnnotation == null) {
                continue;
            }

            String subName = subAnnotation.value().toLowerCase(Locale.ROOT);

            // Extract argument and flag specs from method parameters
            MethodSignature signature = analyzeMethod(method);

            // Compile to MethodHandle with parameter injection
            MethodHandle handle = parameterInjector.compile(method, handler);
            CommandNode.CompiledExecutor executor = CommandNode.CompiledExecutor.fromMethodHandle(handle, handler);

            // Get subcommand metadata
            Permission subPerm = method.getAnnotation(Permission.class);
            String subPermission = subPerm != null ? subPerm.value() : null;

            // Handle "default" or empty subcommand name as root executor
            if (subName.isEmpty() || "default".equals(subName)) {
                defaultExecutor = executor;
                rootArgs.addAll(signature.arguments);
                rootFlags.addAll(signature.flags);
                continue;
            }

            SubNode subNode = SubNode.builder(subName)
                    .permission(subPermission)
                    .arguments(signature.arguments)
                    .flags(signature.flags)
                    .executor(executor)
                    .build();

            children.put(subName, subNode);
        }

        // Build root node
        return RootNode.builder(owner, rootName)
                .aliases(cmdAnnotation.aliases())
                .permission(rootPermission)
                .children(children.values())
                .arguments(rootArgs)
                .flags(rootFlags)
                .executor(defaultExecutor)
                .build();
    }

    /**
     * Analyzes a method to extract argument and flag specifications.
     */
    @NotNull
    private MethodSignature analyzeMethod(@NotNull Method method) {
        method.setAccessible(true);

        Parameter[] params = method.getParameters();
        List<CommandSpec.ArgumentSpec> args = new ArrayList<>();
        List<CommandSpec.FlagSpec> flags = new ArrayList<>();

        for (Parameter param : params) {
            // Skip CommandContext and @Sender parameters
            if (param.getType() == CommandContext.class || param.isAnnotationPresent(Sender.class)) {
                continue;
            }

            // Process @Arg
            if (param.isAnnotationPresent(Arg.class)) {
                args.add(extractArgumentSpec(param));
                continue;
            }

            // Process @Flag
            if (param.isAnnotationPresent(Flag.class)) {
                flags.add(extractFlagSpec(param));
            }
        }

        return new MethodSignature(args, flags);
    }

    @NotNull
    private CommandSpec.ArgumentSpec extractArgumentSpec(@NotNull Parameter param) {
        Arg arg = param.getAnnotation(Arg.class);
        String name = arg.value().isEmpty() ? param.getName() : arg.value();
        String typeName = inferTypeName(param.getType());
        String defaultValue = arg.defaultValue().isEmpty() ? null : arg.defaultValue();
        boolean optional = !arg.defaultValue().isEmpty();
        String description = arg.description().isEmpty() ? null : arg.description();

        return new CommandSpec.ArgumentSpec(name, typeName, defaultValue, optional, description);
    }

    @NotNull
    private CommandSpec.FlagSpec extractFlagSpec(@NotNull Parameter param) {
        Flag flag = param.getAnnotation(Flag.class);
        String name = flag.value().isEmpty() ? param.getName() : flag.value();
        String shortName = flag.shortName().isEmpty() ? null : flag.shortName();
        boolean hasValue = !(param.getType() == boolean.class || param.getType() == Boolean.class);
        String valueType = hasValue ? inferTypeName(param.getType()) : null;

        return new CommandSpec.FlagSpec(name, shortName, valueType, hasValue);
    }

    /**
     * Infers the argument type name from Java type.
     */
    @NotNull
    private String inferTypeName(@NotNull Class<?> javaType) {
        if (javaType == String.class) {
            return "string";
        } else if (javaType == int.class || javaType == Integer.class) {
            return "integer";
        } else if (javaType == double.class || javaType == Double.class) {
            return "double";
        } else if (javaType == boolean.class || javaType == Boolean.class) {
            return "boolean";
        } else if (javaType == Player.class) {
            return "playerOnline";
        } else if (javaType == World.class) {
            return "world";
        } else if (javaType.isEnum()) {
            return "enum";
        } else {
            // Default to string for unknown types
            return "string";
        }
    }

    /**
     * Holds the extracted signature of a command method.
     */
    private record MethodSignature(
            @NotNull List<CommandSpec.ArgumentSpec> arguments,
            @NotNull List<CommandSpec.FlagSpec> flags
    ) {}

    /**
     * Exception thrown when annotation processing fails.
     */
    public static final class ProcessingException extends Exception {
        public ProcessingException(String message) {
            super(message);
        }

        public ProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
