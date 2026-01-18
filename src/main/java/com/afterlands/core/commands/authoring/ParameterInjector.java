package com.afterlands.core.commands.authoring;

import com.afterlands.core.commands.annotations.Arg;
import com.afterlands.core.commands.annotations.Flag;
import com.afterlands.core.commands.annotations.Sender;
import com.afterlands.core.commands.execution.CommandContext;
import com.afterlands.core.commands.parser.ArgumentType;
import com.afterlands.core.commands.parser.ArgumentTypeRegistry;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

/**
 * Injects command parameters from CommandContext using MethodHandles.
 *
 * <p>
 * This class compiles method signatures at registration time (using reflection)
 * and generates efficient MethodHandle-based adapters for execution (no
 * reflection
 * in hot path).
 * </p>
 *
 * <p>
 * Supported parameter types:
 * </p>
 * <ul>
 * <li>{@code CommandContext} - the full context</li>
 * <li>{@code @Sender Player/CommandSender/ConsoleCommandSender} - sender
 * injection</li>
 * <li>{@code @Arg("name") T} - typed argument injection</li>
 * <li>{@code @Flag("name") T} - typed flag injection</li>
 * </ul>
 *
 * <p>
 * Performance: Reflection only at registration time, MethodHandles in
 * execution.
 * </p>
 */
public final class ParameterInjector {

    private final ArgumentTypeRegistry typeRegistry;
    private final MethodHandles.Lookup lookup;

    /**
     * Creates a new ParameterInjector.
     *
     * @param typeRegistry The argument type registry
     */
    public ParameterInjector(@NotNull ArgumentTypeRegistry typeRegistry) {
        this.typeRegistry = typeRegistry;
        this.lookup = MethodHandles.lookup();
    }

    /**
     * Compiles a method to a MethodHandle with parameter injection.
     *
     * <p>
     * This method analyzes the method signature and creates an adapter that:
     * </p>
     * <ol>
     * <li>Takes a CommandContext as input</li>
     * <li>Extracts parameters from the context</li>
     * <li>Invokes the original method with extracted parameters</li>
     * </ol>
     *
     * @param method  The method to compile
     * @param handler The handler instance (for instance methods)
     * @return A MethodHandle that takes CommandContext and returns void
     * @throws Exception if compilation fails
     */
    @NotNull
    public MethodHandle compile(@NotNull Method method, @NotNull Object handler) throws Exception {
        method.setAccessible(true);

        // Analyze parameters
        Parameter[] params = method.getParameters();
        List<ParameterExtractor> extractors = new ArrayList<>(params.length);

        for (Parameter param : params) {
            extractors.add(analyzeParameter(param));
        }

        // Get base method handle
        MethodHandle baseHandle = lookup.unreflect(method);

        // Bind to instance if not static
        if (!java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
            baseHandle = baseHandle.bindTo(handler);
        }

        // Create adapter that extracts parameters from context
        return createAdapter(baseHandle, extractors);
    }

    /**
     * Analyzes a parameter to determine how to extract its value.
     */
    @NotNull
    private ParameterExtractor analyzeParameter(@NotNull Parameter param) throws Exception {
        Class<?> type = param.getType();

        // CommandContext - pass through
        if (type == CommandContext.class) {
            return ParameterExtractor.context();
        }

        // @Sender annotation
        if (param.isAnnotationPresent(Sender.class)) {
            return ParameterExtractor.sender(type);
        }

        // @Arg annotation
        if (param.isAnnotationPresent(Arg.class)) {
            Arg arg = param.getAnnotation(Arg.class);
            String name = arg.value().isEmpty() ? param.getName() : arg.value();
            String defaultValue = arg.defaultValue().isEmpty() ? null : arg.defaultValue();
            boolean isNone = Arg.NONE.equals(defaultValue);
            boolean optional = arg.optional() || (defaultValue != null && !isNone);
            return ParameterExtractor.argument(name, type, defaultValue, optional, typeRegistry);
        }

        // @Flag annotation
        if (param.isAnnotationPresent(Flag.class)) {
            Flag flag = param.getAnnotation(Flag.class);
            String name = flag.value().isEmpty() ? param.getName() : flag.value();
            String defaultValue = flag.defaultValue().isEmpty() ? null : flag.defaultValue();
            return ParameterExtractor.flag(name, type, defaultValue);
        }

        throw new IllegalArgumentException(
                "Parameter " + param.getName() + " of type " + type.getSimpleName()
                        + " must be annotated with @Arg, @Flag, or @Sender, or be CommandContext");
    }

    /**
     * Creates an adapter MethodHandle that extracts parameters and invokes the base
     * handle.
     */
    @NotNull
    private MethodHandle createAdapter(@NotNull MethodHandle baseHandle,
            @NotNull List<ParameterExtractor> extractors) throws Exception {
        // The adapter signature: (CommandContext) -> void
        // We need to convert baseHandle which might be (T1, T2, ...) -> void

        if (extractors.isEmpty()) {
            // No parameters - drop the context parameter
            return MethodHandles.dropArguments(baseHandle, 0, CommandContext.class);
        }

        // Create a wrapper method handle
        // We can't extend MethodHandle, so we'll use a lambda-based approach
        MethodHandle wrapper = MethodHandles.lookup().findStatic(
                ParameterInjector.class,
                "invokeWithExtraction",
                MethodType.methodType(void.class, MethodHandle.class, List.class, CommandContext.class));

        // Bind the target handle and extractors
        return wrapper.bindTo(baseHandle).bindTo(extractors);
    }

    /**
     * Static helper method to invoke a handle with parameter extraction.
     */
    private static void invokeWithExtraction(MethodHandle target,
            List<ParameterExtractor> extractors,
            CommandContext ctx) throws Throwable {
        Object[] args = new Object[extractors.size()];
        for (int i = 0; i < extractors.size(); i++) {
            args[i] = extractors.get(i).extract(ctx);
        }
        target.invokeWithArguments(args);
    }

    /**
     * Functional interface for extracting a parameter value from context.
     */
    @FunctionalInterface
    private interface ParameterExtractor {
        Object extract(CommandContext ctx) throws Exception;

        static ParameterExtractor context() {
            return ctx -> ctx;
        }

        static ParameterExtractor sender(Class<?> type) {
            return ctx -> {
                CommandSender sender = ctx.sender();

                if (type == CommandSender.class) {
                    return sender;
                } else if (type == Player.class) {
                    if (!(sender instanceof Player)) {
                        throw new IllegalArgumentException("This command can only be used by players");
                    }
                    return sender;
                } else if (type == ConsoleCommandSender.class) {
                    if (!(sender instanceof ConsoleCommandSender)) {
                        throw new IllegalArgumentException("This command can only be used by console");
                    }
                    return sender;
                } else {
                    throw new IllegalArgumentException("Unsupported sender type: " + type.getSimpleName());
                }
            };
        }

        static ParameterExtractor argument(String name, Class<?> type, String defaultValue,
                boolean optional, ArgumentTypeRegistry typeRegistry) {
            return ctx -> {
                Object value = ctx.args().get(name);

                if (value == null && defaultValue != null && !Arg.NONE.equals(defaultValue)) {
                    // Parse default value
                    ArgumentType<?> argType = inferType(type, typeRegistry);
                    if (argType != null) {
                        ArgumentType.ParseContext parseCtx = ArgumentType.ParseContext.of(
                                ctx.sender(),
                                com.afterlands.core.commands.parser.ArgReader.parse(defaultValue));
                        value = argType.parse(parseCtx, defaultValue);
                    } else {
                        value = defaultValue;
                    }
                }

                if (value == null) {
                    // Optional parameter - return null
                    if (optional) {
                        return null;
                    }
                    throw new IllegalArgumentException("Missing required argument: " + name);
                }

                // Type conversion
                return convertType(value, type);
            };
        }

        static ParameterExtractor flag(String name, Class<?> type, String defaultValue) {
            return ctx -> {
                if (type == boolean.class || type == Boolean.class) {
                    // Boolean flag - check presence
                    return ctx.flags().has(name);
                }

                String value = ctx.flags().getValue(name);

                if (value == null && defaultValue != null) {
                    value = defaultValue;
                }

                if (value == null) {
                    // Flag not provided - return default
                    if (type.isPrimitive()) {
                        return getDefaultPrimitive(type);
                    }
                    return null;
                }

                // Parse value
                return parseValue(value, type);
            };
        }

        static ArgumentType<?> inferType(Class<?> javaType, ArgumentTypeRegistry registry) {
            if (javaType == String.class) {
                return registry.get("string");
            } else if (javaType == int.class || javaType == Integer.class) {
                return registry.get("integer");
            } else if (javaType == double.class || javaType == Double.class) {
                return registry.get("double");
            } else if (javaType == boolean.class || javaType == Boolean.class) {
                return registry.get("boolean");
            } else if (javaType == Player.class) {
                return registry.get("playerOnline");
            } else if (javaType == World.class) {
                return registry.get("world");
            } else if (javaType.isEnum()) {
                return registry.get("enum");
            }
            return null;
        }

        static Object convertType(Object value, Class<?> targetType) {
            if (targetType.isInstance(value)) {
                return value;
            }

            // Handle String[] type - varargs or remaining args
            if (targetType == String[].class) {
                if (value instanceof String[] arr) {
                    return arr;
                } else if (value instanceof String str) {
                    return new String[] { str };
                } else if (value instanceof java.util.List<?> list) {
                    return list.stream().map(Object::toString).toArray(String[]::new);
                }
                return new String[] { value.toString() };
            }

            // Handle primitive boxing
            if (targetType.isPrimitive()) {
                return value; // Already boxed by parsers
            }

            return value;
        }

        static Object parseValue(String value, Class<?> type) {
            if (type == String.class) {
                return value;
            } else if (type == int.class || type == Integer.class) {
                return Integer.parseInt(value);
            } else if (type == double.class || type == Double.class) {
                return Double.parseDouble(value);
            } else if (type == boolean.class || type == Boolean.class) {
                return Boolean.parseBoolean(value);
            } else {
                return value;
            }
        }

        static Object getDefaultPrimitive(Class<?> type) {
            if (type == int.class)
                return 0;
            if (type == double.class)
                return 0.0;
            if (type == boolean.class)
                return false;
            if (type == long.class)
                return 0L;
            if (type == float.class)
                return 0.0f;
            if (type == byte.class)
                return (byte) 0;
            if (type == short.class)
                return (short) 0;
            if (type == char.class)
                return '\0';
            return null;
        }
    }
}
