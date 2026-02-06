package com.afterlands.core.inventory.item;

import com.afterlands.core.api.messages.MessageKey;
import com.afterlands.core.concurrent.SchedulerService;
import com.afterlands.core.config.MessageService;
import com.afterlands.core.inventory.InventoryContext;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolução async-safe de placeholders.
 *
 * <p>
 * Ordem de resolução:
 * <ol>
 * <li>{@code {lang:namespace:key}} - I18n via MessageService (PRIORITY)</li>
 * <li>Placeholders do contexto ({@code {key}})</li>
 * <li>PlaceholderAPI ({@code %placeholder%}) - MAIN THREAD ONLY</li>
 * </ol>
 * </p>
 *
 * <p>
 * <b>Thread Safety:</b> PlaceholderAPI resolution MUST run on main thread.
 * Use SchedulerService.runSync() para PlaceholderAPI calls.
 * </p>
 *
 * <p>
 * <b>Cache:</b> Placeholders resolvidos são cacheados por 5s (TTL curto
 * para evitar dados stale).
 * </p>
 *
 * <p>
 * <b>Performance:</b> Timeout de 10 iterações para evitar infinite loops.
 * </p>
 */
public class PlaceholderResolver {

    private static final Pattern LANG_PLACEHOLDER = Pattern.compile("\\{lang:([^:}]+):([^}]+)\\}");
    private static final Pattern CONTEXT_PLACEHOLDER = Pattern.compile("\\{([^}]+)\\}");
    private static final Pattern PAPI_PLACEHOLDER = Pattern.compile("%([^%]+)%");
    private static final int MAX_ITERATIONS = 10;
    private static final long CACHE_TTL_SECONDS = 5;

    private static final boolean PAPI_AVAILABLE = checkPlaceholderAPI();

    private final SchedulerService scheduler;
    private final MessageService messageService;
    private final Cache<CacheKey, String> cache;
    private final boolean debug;

    /**
     * Cria resolver com scheduler para main thread operations.
     *
     * @param scheduler      Scheduler service
     * @param messageService Message service for i18n resolution
     * @param debug          Habilita debug logging
     */
    public PlaceholderResolver(@NotNull SchedulerService scheduler, @NotNull MessageService messageService,
            boolean debug) {
        this.scheduler = scheduler;
        this.messageService = messageService;
        this.debug = debug;

        // Cache de curta duração para placeholders resolvidos
        this.cache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(CACHE_TTL_SECONDS, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Verifica se PlaceholderAPI está disponível.
     *
     * @return true se PlaceholderAPI instalado
     */
    private static boolean checkPlaceholderAPI() {
        try {
            Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            return Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Resolve placeholders de forma síncrona (MAIN THREAD).
     *
     * <p>
     * Usa cache quando possível. Se player != null e PlaceholderAPI disponível,
     * resolve %placeholders%.
     * </p>
     *
     * <p>
     * <b>CRITICAL:</b> Este método DEVE ser chamado da main thread se player !=
     * null.
     * </p>
     *
     * @param text    Texto com placeholders
     * @param player  Player alvo (pode ser null)
     * @param context Contexto com placeholders customizados
     * @return Texto com placeholders resolvidos
     */
    @NotNull
    public String resolve(@NotNull String text, @Nullable Player player, @NotNull InventoryContext context) {
        if (text.isEmpty()) {
            return text;
        }

        // Verifica cache
        UUID playerId = player != null ? player.getUniqueId() : null;
        CacheKey cacheKey = new CacheKey(text, playerId, context.getPlaceholders().hashCode());
        String cached = cache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }

        // Resolve iterativamente (max 10 iterations)
        String result = text;

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            String before = result;

            // 1. Resolve i18n placeholders ({lang:namespace:key}) - PRIORITY
            if (player != null) {
                result = resolveLangPlaceholders(result, player);
            }

            // 2. Resolve context placeholders ({key})
            result = resolveContextPlaceholders(result, context);

            // 3. Resolve PlaceholderAPI (%placeholder%)
            if (PAPI_AVAILABLE && player != null) {
                result = resolvePlaceholderAPI(result, player);
            }

            // Se não houve mudança, termina
            if (result.equals(before)) {
                break;
            }
        }

        // Cacheia resultado
        cache.put(cacheKey, result);

        return result;
    }

    /**
     * Resolve placeholders assíncronos (sem PlaceholderAPI).
     *
     * <p>
     * Útil para pré-processar textos que não dependem de player.
     * </p>
     *
     * @param text    Texto com placeholders
     * @param context Contexto
     * @return CompletableFuture com texto resolvido
     */
    @NotNull
    public CompletableFuture<String> resolveAsync(@NotNull String text, @NotNull InventoryContext context) {
        return CompletableFuture.supplyAsync(() -> {
            String result = text;

            // Apenas context placeholders (async-safe)
            for (int i = 0; i < MAX_ITERATIONS; i++) {
                String before = result;
                result = resolveContextPlaceholders(result, context);

                if (result.equals(before)) {
                    break;
                }
            }

            return result;
        });
    }

    /**
     * Resolve múltiplas linhas de forma síncrona.
     *
     * @param lines   Linhas de texto
     * @param player  Player alvo
     * @param context Contexto
     * @return Lista com placeholders resolvidos
     */
    @NotNull
    public List<String> resolveLines(
            @NotNull List<String> lines,
            @Nullable Player player,
            @NotNull InventoryContext context) {
        return lines.stream()
                .map(line -> resolve(line, player, context))
                .toList();
    }

    /**
     * Verifica se texto contém placeholders dinâmicos.
     *
     * <p>
     * Útil para determinar se item é cacheável.
     * </p>
     * 
     * <p>
     * Considera dinâmicos:
     * <ul>
     * <li>PlaceholderAPI: %placeholder%</li>
     * <li>I18n: {lang:namespace:key}</li>
     * <li>Context: {key} (ex: {duration}, {alias})</li>
     * </ul>
     * </p>
     *
     * @param text Texto a verificar
     * @return true se contém qualquer tipo de placeholder
     */
    public boolean hasDynamicPlaceholders(@NotNull String text) {
        // Context placeholders ({key}) are dynamic because their values change
        // per-context
        return CONTEXT_PLACEHOLDER.matcher(text).find()
                || PAPI_PLACEHOLDER.matcher(text).find()
                || LANG_PLACEHOLDER.matcher(text).find();
    }

    /**
     * Verifica se contém placeholders voláteis (PlaceholderAPI) que impedem cache.
     * 
     * @param text Texto
     * @return true se contém %placeholder%
     */
    public boolean hasVolatilePlaceholders(@NotNull String text) {
        return PAPI_PLACEHOLDER.matcher(text).find();
    }

    /**
     * Verifica se contém placeholders de contexto que exigem CacheKey dinâmica.
     * 
     * @param text Texto
     * @return true se contém {key} ou {lang:...}
     */
    public boolean hasContextAwarePlaceholders(@NotNull String text) {
        return CONTEXT_PLACEHOLDER.matcher(text).find()
                || LANG_PLACEHOLDER.matcher(text).find();
    }

    /**
     * Verifica se texto contém qualquer tipo de placeholder.
     *
     * @param text Texto a verificar
     * @return true se contém {key}, %placeholder%, ou {lang:}
     */
    public boolean hasPlaceholders(@NotNull String text) {
        return CONTEXT_PLACEHOLDER.matcher(text).find()
                || PAPI_PLACEHOLDER.matcher(text).find()
                || LANG_PLACEHOLDER.matcher(text).find();
    }

    /**
     * Resolve i18n placeholders ({lang:namespace:key}).
     *
     * <p>
     * This is resolved FIRST to allow translated text to contain
     * context placeholders and PAPI placeholders.
     * </p>
     *
     * @param text   Texto
     * @param player Player for language resolution
     * @return Texto com {lang:...} substituídos
     */
    @NotNull
    private String resolveLangPlaceholders(@NotNull String text, @NotNull Player player) {
        Matcher matcher = LANG_PLACEHOLDER.matcher(text);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String namespace = matcher.group(1);
            String path = matcher.group(2);

            try {
                MessageKey key = MessageKey.of(namespace, path);
                String translation = messageService.get(player, key);

                // Quote replacement to handle special regex chars in translation
                matcher.appendReplacement(sb, Matcher.quoteReplacement(translation));
            } catch (Exception e) {
                // Fallback: keep original pattern if translation fails
                if (debug) {
                    Bukkit.getLogger().warning("[PlaceholderResolver] Failed to resolve {lang:" + namespace + ":" + path
                            + "}: " + e.getMessage());
                }
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
            }
        }

        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Resolve placeholders do contexto ({key}).
     *
     * <p>
     * Skip patterns that look like {lang:...} to avoid conflicts.
     * </p>
     *
     * @param text    Texto
     * @param context Contexto
     * @return Texto com {key} substituídos
     */
    @NotNull
    private String resolveContextPlaceholders(@NotNull String text, @NotNull InventoryContext context) {
        Matcher matcher = CONTEXT_PLACEHOLDER.matcher(text);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String fullMatch = matcher.group(0);
            String key = matcher.group(1);

            // Skip {lang:...} patterns (already resolved)
            if (key.startsWith("lang:")) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(fullMatch));
                continue;
            }

            String value = context.getPlaceholders().get(key);

            if (value != null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
            }
        }

        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Resolve PlaceholderAPI placeholders (%placeholder%).
     *
     * <p>
     * <b>CRITICAL:</b> MUST be called from main thread.
     * </p>
     *
     * @param text   Texto
     * @param player Player alvo
     * @return Texto com %placeholder% substituídos
     */
    @NotNull
    private String resolvePlaceholderAPI(@NotNull String text, @NotNull Player player) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException(
                    "PlaceholderAPI resolution must run on main thread (current thread: " +
                            Thread.currentThread().getName() + ")");
        }

        try {
            return PlaceholderAPI.setPlaceholders(player, text);
        } catch (Exception e) {
            // Graceful degradation se PlaceholderAPI falhar
            return text;
        }
    }

    /**
     * Limpa cache de placeholders.
     */
    public void clearCache() {
        cache.invalidateAll();
    }

    /**
     * Cache key para placeholders resolvidos.
     */
    private record CacheKey(
            @NotNull String text,
            @Nullable UUID playerId,
            int contextHash) {
    }

    /**
     * Verifica se PlaceholderAPI está disponível.
     *
     * @return true se disponível
     */
    public static boolean isPlaceholderAPIAvailable() {
        return PAPI_AVAILABLE;
    }
}
