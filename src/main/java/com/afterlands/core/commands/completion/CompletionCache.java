package com.afterlands.core.commands.completion;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Thread-safe cache for tab-completion suggestions with TTL.
 *
 * <p>This cache is designed for expensive completion operations (e.g., database queries,
 * file system scans) that should not block the main thread for too long. It provides:</p>
 * <ul>
 *   <li>Short TTL (1-3s) to balance freshness vs performance</li>
 *   <li>Size limit to prevent memory issues</li>
 *   <li>Thread-safe concurrent access</li>
 *   <li>Automatic eviction on write and access</li>
 * </ul>
 *
 * <p>Usage example:</p>
 * <pre>{@code
 * CompletionCache cache = CompletionCache.builder()
 *     .ttl(2, TimeUnit.SECONDS)
 *     .maxSize(1000)
 *     .build();
 *
 * // In tab-complete handler
 * List<String> suggestions = cache.get("player_list", () -> {
 *     return Bukkit.getOnlinePlayers().stream()
 *         .map(Player::getName)
 *         .collect(Collectors.toList());
 * });
 * }</pre>
 *
 * <p>Performance: O(1) cache lookup, minimal overhead (~10-50μs).</p>
 */
public final class CompletionCache {

    private final Cache<String, List<String>> cache;

    private CompletionCache(Cache<String, List<String>> cache) {
        this.cache = cache;
    }

    /**
     * Gets cached suggestions or computes them.
     *
     * <p>If the key is present in cache and not expired, returns cached value.
     * Otherwise, calls the supplier and caches the result.</p>
     *
     * @param key      Cache key
     * @param supplier Supplier to compute suggestions if cache miss
     * @return List of suggestions
     */
    @NotNull
    public List<String> get(@NotNull String key, @NotNull Supplier<List<String>> supplier) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(supplier, "supplier");

        // Try cache first
        List<String> cached = cache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }

        // Compute and cache
        List<String> computed = supplier.get();
        if (computed != null && !computed.isEmpty()) {
            cache.put(key, List.copyOf(computed)); // Immutable copy
        }

        return computed != null ? computed : List.of();
    }

    /**
     * Gets cached suggestions without computing.
     *
     * @param key Cache key
     * @return Cached suggestions, or null if not present
     */
    @Nullable
    public List<String> getIfPresent(@NotNull String key) {
        return cache.getIfPresent(key);
    }

    /**
     * Puts suggestions in cache.
     *
     * @param key         Cache key
     * @param suggestions Suggestions to cache
     */
    public void put(@NotNull String key, @NotNull List<String> suggestions) {
        cache.put(key, List.copyOf(suggestions));
    }

    /**
     * Invalidates a specific key.
     *
     * @param key Key to invalidate
     */
    public void invalidate(@NotNull String key) {
        cache.invalidate(key);
    }

    /**
     * Invalidates all cached entries.
     */
    public void invalidateAll() {
        cache.invalidateAll();
    }

    /**
     * Gets current cache size.
     *
     * @return Number of cached entries
     */
    public long size() {
        return cache.estimatedSize();
    }

    /**
     * Gets cache statistics (if stats enabled).
     *
     * @return Cache stats
     */
    @NotNull
    public String stats() {
        return cache.stats().toString();
    }

    /**
     * Creates a new builder for CompletionCache.
     *
     * @return A new builder
     */
    @NotNull
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a default cache (2s TTL, 1000 entries, no stats).
     *
     * @return A new CompletionCache with default settings
     */
    @NotNull
    public static CompletionCache defaultCache() {
        return builder().build();
    }

    /**
     * Builder for CompletionCache.
     */
    public static final class Builder {
        private long ttlSeconds = 2;
        private long maxSize = 1000;
        private boolean recordStats = false;

        private Builder() {}

        /**
         * Sets the time-to-live for cached entries.
         *
         * @param duration Duration value
         * @param unit     Time unit
         * @return This builder
         */
        @NotNull
        public Builder ttl(long duration, @NotNull TimeUnit unit) {
            this.ttlSeconds = unit.toSeconds(duration);
            return this;
        }

        /**
         * Sets the maximum cache size.
         *
         * <p>When the size is exceeded, least recently used entries are evicted.</p>
         *
         * @param maxSize Maximum number of entries
         * @return This builder
         */
        @NotNull
        public Builder maxSize(long maxSize) {
            if (maxSize <= 0) {
                throw new IllegalArgumentException("maxSize must be positive");
            }
            this.maxSize = maxSize;
            return this;
        }

        /**
         * Enables statistics recording.
         *
         * <p>When enabled, cache hit/miss rates and other metrics are tracked.
         * Slight performance overhead (~5%).</p>
         *
         * @return This builder
         */
        @NotNull
        public Builder recordStats() {
            this.recordStats = true;
            return this;
        }

        /**
         * Builds the cache.
         *
         * @return A new CompletionCache
         */
        @NotNull
        public CompletionCache build() {
            Caffeine<Object, Object> builder = Caffeine.newBuilder()
                    .maximumSize(maxSize)
                    .expireAfterWrite(ttlSeconds, TimeUnit.SECONDS)
                    .expireAfterAccess(ttlSeconds * 2, TimeUnit.SECONDS); // 2x for access

            if (recordStats) {
                builder.recordStats();
            }

            Cache<String, List<String>> cache = builder.build();
            return new CompletionCache(cache);
        }
    }
}
