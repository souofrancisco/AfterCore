package com.afterlands.core.commands.parser;

import com.afterlands.core.commands.CommandSpec;
import com.afterlands.core.commands.execution.ParsedFlags;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Parser for command flags.
 *
 * <p>Supports multiple flag formats:</p>
 * <ul>
 *   <li>{@code -f} - Short boolean flag</li>
 *   <li>{@code --force} - Long boolean flag</li>
 *   <li>{@code --page 2} - Long flag with value (next token)</li>
 *   <li>{@code --page=2} - Long flag with value (equals syntax)</li>
 *   <li>{@code -p 2} - Short flag with value</li>
 * </ul>
 *
 * <p>The parser removes flag tokens from the argument list and returns
 * both the parsed flags and the remaining positional arguments.</p>
 *
 * <p>Example:</p>
 * <pre>{@code
 * FlagParser parser = new FlagParser(flagSpecs);
 * FlagParser.Result result = parser.parse(args);
 * ParsedFlags flags = result.flags();
 * List<String> remaining = result.remaining();
 * }</pre>
 */
public final class FlagParser {

    private final Map<String, FlagSpec> longFlags = new HashMap<>();
    private final Map<String, FlagSpec> shortFlags = new HashMap<>();

    /**
     * Internal flag specification for parsing.
     */
    private record FlagSpec(String name, boolean hasValue) {}

    /**
     * Creates a FlagParser from CommandSpec flag specifications.
     *
     * @param specs List of flag specifications
     */
    public FlagParser(@NotNull List<CommandSpec.FlagSpec> specs) {
        for (CommandSpec.FlagSpec spec : specs) {
            FlagSpec internal = new FlagSpec(spec.name(), spec.hasValue());
            longFlags.put(spec.name().toLowerCase(Locale.ROOT), internal);

            if (spec.shortName() != null && !spec.shortName().isEmpty()) {
                shortFlags.put(spec.shortName().toLowerCase(Locale.ROOT), internal);
            }
        }

        // Always support --help / -h
        FlagSpec helpSpec = new FlagSpec("help", false);
        longFlags.putIfAbsent("help", helpSpec);
        shortFlags.putIfAbsent("h", helpSpec);
    }

    /**
     * Creates an empty FlagParser (accepts no flags except --help).
     */
    public FlagParser() {
        this(List.of());
    }

    /**
     * Parses flags from arguments.
     *
     * @param args Raw arguments
     * @return Parse result with flags and remaining positional args
     */
    @NotNull
    public Result parse(@NotNull String[] args) {
        return parse(Arrays.asList(args));
    }

    /**
     * Parses flags from arguments.
     *
     * @param args Raw arguments
     * @return Parse result with flags and remaining positional args
     */
    @NotNull
    public Result parse(@NotNull List<String> args) {
        ParsedFlags.Builder flags = ParsedFlags.builder();
        List<String> remaining = new ArrayList<>();
        boolean flagsEnded = false; // -- ends flag parsing

        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);

            // Check for -- (end of flags)
            if ("--".equals(arg)) {
                flagsEnded = true;
                continue;
            }

            // After --, everything is positional
            if (flagsEnded) {
                remaining.add(arg);
                continue;
            }

            // Check for long flag --name or --name=value
            if (arg.startsWith("--")) {
                i = parseLongFlag(arg, args, i, flags, remaining);
                continue;
            }

            // Check for short flag -f or -fvalue
            if (arg.startsWith("-") && arg.length() > 1 && !isNumeric(arg)) {
                i = parseShortFlag(arg, args, i, flags, remaining);
                continue;
            }

            // Not a flag - positional argument
            remaining.add(arg);
        }

        return new Result(flags.build(), remaining);
    }

    private int parseLongFlag(String arg, List<String> args, int index,
                              ParsedFlags.Builder flags, List<String> remaining) {
        String flagPart = arg.substring(2); // Remove --

        // Check for --name=value syntax
        int eqIndex = flagPart.indexOf('=');
        if (eqIndex > 0) {
            String name = flagPart.substring(0, eqIndex).toLowerCase(Locale.ROOT);
            String value = flagPart.substring(eqIndex + 1);
            flags.flag(name, value);
            return index;
        }

        // Simple --name
        String name = flagPart.toLowerCase(Locale.ROOT);
        FlagSpec spec = longFlags.get(name);

        if (spec == null) {
            // Unknown flag - treat as boolean
            flags.flag(name);
            return index;
        }

        if (spec.hasValue && index + 1 < args.size()) {
            // Consume next token as value
            flags.flag(spec.name, args.get(index + 1));
            return index + 1;
        }

        // Boolean flag
        flags.flag(spec.name);
        return index;
    }

    private int parseShortFlag(String arg, List<String> args, int index,
                               ParsedFlags.Builder flags, List<String> remaining) {
        // Handle combined short flags: -abc = -a -b -c
        String flagChars = arg.substring(1); // Remove -

        for (int i = 0; i < flagChars.length(); i++) {
            String charStr = String.valueOf(flagChars.charAt(i)).toLowerCase(Locale.ROOT);
            FlagSpec spec = shortFlags.get(charStr);

            if (spec == null) {
                // Unknown short flag - treat as boolean
                flags.flag(charStr);
                continue;
            }

            if (spec.hasValue) {
                // Rest of string is value, or next token
                if (i + 1 < flagChars.length()) {
                    // Value is remainder of this token: -p2
                    flags.flag(spec.name, flagChars.substring(i + 1));
                    return index;
                } else if (index + 1 < args.size()) {
                    // Value is next token: -p 2
                    flags.flag(spec.name, args.get(index + 1));
                    return index + 1;
                } else {
                    // No value provided - treat as boolean
                    flags.flag(spec.name);
                }
                return index;
            }

            // Boolean flag
            flags.flag(spec.name);
        }

        return index;
    }

    private boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        // Check if it's a negative number like -5 or -3.14
        String test = str.startsWith("-") ? str.substring(1) : str;
        if (test.isEmpty()) {
            return false;
        }
        boolean hasDecimal = false;
        for (int i = 0; i < test.length(); i++) {
            char c = test.charAt(i);
            if (c == '.') {
                if (hasDecimal) return false;
                hasDecimal = true;
            } else if (!Character.isDigit(c)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Result of flag parsing.
     *
     * @param flags     Parsed flags
     * @param remaining Remaining positional arguments
     */
    public record Result(
            @NotNull ParsedFlags flags,
            @NotNull List<String> remaining
    ) {
        public Result {
            remaining = List.copyOf(remaining);
        }
    }
}
