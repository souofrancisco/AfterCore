package com.afterlands.core.commands.messages;

import com.afterlands.core.config.ConfigService;
import com.afterlands.core.config.MessageService;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hybrid message facade that resolves messages from plugin-specific configs
 * with fallback to AfterCore defaults.
 *
 * <p>Resolution order:</p>
 * <ol>
 *   <li>Plugin owner's messages.yml (if registered)</li>
 *   <li>AfterCore's messages.yml (fallback)</li>
 *   <li>Default inline message (if provided)</li>
 * </ol>
 *
 * <p>Placeholder format: {@code {key}} is replaced with the corresponding value.</p>
 *
 * <p>Example:</p>
 * <pre>{@code
 * messageFacade.send(sender, "commands.usage", "usage", "/mycommand <arg>");
 * // Sends: "Usage: /mycommand <arg>" (if commands.usage = "Usage: {usage}")
 * }</pre>
 *
 * <p>Thread Safety: This class is thread-safe.</p>
 */
public final class MessageFacade {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([^}]+)}");

    private final Plugin corePlugin;
    private final ConfigService coreConfig;
    private final MessageService coreMessages;
    private final Logger logger;
    private final boolean debug;

    // Plugin -> their messages config
    private final Map<String, FileConfiguration> pluginMessages = new ConcurrentHashMap<>();

    // Default messages for when nothing is configured
    private static final Map<String, String> DEFAULTS = Map.ofEntries(
            Map.entry("errors.no-permission", "&cYou don't have permission to do this."),
            Map.entry("errors.player-only", "&cThis command can only be used by players."),
            Map.entry("errors.internal", "&cAn internal error occurred. Please contact an administrator."),
            Map.entry("commands.unknown-subcommand", "&cUnknown subcommand: &f{subcommand}"),
            Map.entry("commands.usage", "&eUsage: &f{usage}"),
            Map.entry("commands.help.header", "&6=== Help: &f{command} &6==="),
            Map.entry("commands.help.line", " &f{command} &7- {description}")
    );

    /**
     * Creates a new MessageFacade.
     *
     * @param corePlugin  The AfterCore plugin
     * @param coreConfig  The AfterCore config service
     * @param coreMessages The AfterCore message service
     * @param debug       Whether debug mode is enabled
     */
    public MessageFacade(@NotNull Plugin corePlugin,
                         @NotNull ConfigService coreConfig,
                         @NotNull MessageService coreMessages,
                         boolean debug) {
        this.corePlugin = Objects.requireNonNull(corePlugin, "corePlugin");
        this.coreConfig = Objects.requireNonNull(coreConfig, "coreConfig");
        this.coreMessages = Objects.requireNonNull(coreMessages, "coreMessages");
        this.logger = corePlugin.getLogger();
        this.debug = debug;
    }

    /**
     * Registers a plugin's messages configuration.
     *
     * @param plugin The plugin
     * @param config The plugin's messages configuration
     */
    public void registerPluginMessages(@NotNull Plugin plugin, @NotNull FileConfiguration config) {
        pluginMessages.put(plugin.getName(), config);
        if (debug) {
            logger.info("[Messages] Registered messages for " + plugin.getName());
        }
    }

    /**
     * Unregisters a plugin's messages configuration.
     *
     * @param plugin The plugin
     */
    public void unregisterPluginMessages(@NotNull Plugin plugin) {
        pluginMessages.remove(plugin.getName());
    }

    /**
     * Sends a message to a sender.
     *
     * @param sender The command sender
     * @param path   The message path
     */
    public void send(@NotNull CommandSender sender, @NotNull String path) {
        send(sender, path, (Map<String, String>) null);
    }

    /**
     * Sends a message with placeholders.
     *
     * @param sender       The command sender
     * @param path         The message path
     * @param placeholders Varargs of key, value pairs (must be even count)
     */
    public void send(@NotNull CommandSender sender, @NotNull String path, @NotNull Object... placeholders) {
        if (placeholders.length % 2 != 0) {
            throw new IllegalArgumentException("Placeholders must be key-value pairs (even count)");
        }

        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < placeholders.length; i += 2) {
            map.put(String.valueOf(placeholders[i]), String.valueOf(placeholders[i + 1]));
        }

        send(sender, path, map);
    }

    /**
     * Sends a message with a placeholder map.
     *
     * @param sender       The command sender
     * @param path         The message path
     * @param placeholders Placeholder map
     */
    public void send(@NotNull CommandSender sender, @NotNull String path, @Nullable Map<String, String> placeholders) {
        String message = resolve(path);
        if (message == null || message.isEmpty()) {
            if (debug) {
                logger.warning("[Messages] No message found for path: " + path);
            }
            return;
        }

        // Apply placeholders
        if (placeholders != null && !placeholders.isEmpty()) {
            message = applyPlaceholders(message, placeholders);
        }

        // Format and send
        sendRaw(sender, message);
    }

    /**
     * Sends a raw message (only color formatting, no lookup).
     *
     * @param sender  The command sender
     * @param message The raw message
     */
    public void sendRaw(@NotNull CommandSender sender, @NotNull String message) {
        sender.sendMessage(format(message));
    }

    /**
     * Formats a message with color codes.
     *
     * @param message The message to format
     * @return Formatted message
     */
    @NotNull
    public String format(@NotNull String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * Resolves a message path to its content.
     *
     * @param path The message path
     * @return The resolved message, or null if not found
     */
    @Nullable
    public String resolve(@NotNull String path) {
        return resolve(null, path);
    }

    /**
     * Resolves a message path with plugin-specific override.
     *
     * @param pluginName Plugin name to check first (can be null)
     * @param path       The message path
     * @return The resolved message, or null if not found
     */
    @Nullable
    public String resolve(@Nullable String pluginName, @NotNull String path) {
        // 1. Try plugin-specific messages
        if (pluginName != null) {
            FileConfiguration pluginConfig = pluginMessages.get(pluginName);
            if (pluginConfig != null) {
                String msg = pluginConfig.getString(path);
                if (msg != null) {
                    return msg;
                }
            }
        }

        // 2. Try AfterCore messages.yml
        FileConfiguration coreMessagesConfig = coreConfig.messages();
        if (coreMessagesConfig != null) {
            String msg = coreMessagesConfig.getString(path);
            if (msg != null) {
                return msg;
            }
        }

        // 3. Return default
        return DEFAULTS.get(path);
    }

    /**
     * Applies placeholders to a message.
     *
     * @param message      The message template
     * @param placeholders The placeholder map
     * @return Message with placeholders replaced
     */
    @NotNull
    public String applyPlaceholders(@NotNull String message, @NotNull Map<String, String> placeholders) {
        if (placeholders.isEmpty()) {
            return message;
        }

        StringBuffer result = new StringBuffer();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(message);

        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = placeholders.getOrDefault(key, matcher.group(0));
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Creates a context-specific facade for a plugin.
     *
     * <p>The returned facade will first check the plugin's messages
     * before falling back to AfterCore defaults.</p>
     *
     * @param plugin The plugin
     * @return A plugin-specific message facade
     */
    @NotNull
    public PluginMessageFacade forPlugin(@NotNull Plugin plugin) {
        return new PluginMessageFacade(plugin.getName());
    }

    /**
     * Plugin-specific message facade that prioritizes the plugin's messages.
     */
    public final class PluginMessageFacade {
        private final String pluginName;

        private PluginMessageFacade(String pluginName) {
            this.pluginName = pluginName;
        }

        public void send(@NotNull CommandSender sender, @NotNull String path) {
            String message = resolve(pluginName, path);
            if (message != null) {
                sendRaw(sender, message);
            }
        }

        public void send(@NotNull CommandSender sender, @NotNull String path, @NotNull Object... placeholders) {
            if (placeholders.length % 2 != 0) {
                throw new IllegalArgumentException("Placeholders must be key-value pairs");
            }

            Map<String, String> map = new HashMap<>();
            for (int i = 0; i < placeholders.length; i += 2) {
                map.put(String.valueOf(placeholders[i]), String.valueOf(placeholders[i + 1]));
            }

            String message = resolve(pluginName, path);
            if (message != null) {
                message = applyPlaceholders(message, map);
                sendRaw(sender, message);
            }
        }

        public void sendRaw(@NotNull CommandSender sender, @NotNull String message) {
            MessageFacade.this.sendRaw(sender, message);
        }

        @NotNull
        public String format(@NotNull String message) {
            return MessageFacade.this.format(message);
        }
    }
}
