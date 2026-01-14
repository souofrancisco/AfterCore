package com.afterlands.core.commands.registry;

import com.afterlands.core.commands.registry.nodes.RootNode;
import com.afterlands.core.commands.registry.nodes.SubNode;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Thread-safe graph structure for command tree management.
 *
 * <p>The CommandGraph maintains the complete command hierarchy and provides
 * efficient O(1) lookup for root commands and their subcommands. It supports:</p>
 * <ul>
 *   <li>Concurrent read access (multiple threads can query simultaneously)</li>
 *   <li>Write serialization (mutations are synchronized)</li>
 *   <li>Owner-based command grouping for plugin lifecycle management</li>
 *   <li>Alias resolution</li>
 * </ul>
 *
 * <p>Performance characteristics:</p>
 * <ul>
 *   <li>Root lookup by name: O(1)</li>
 *   <li>Subcommand lookup: O(d) where d is depth</li>
 *   <li>Commands by owner: O(1)</li>
 * </ul>
 */
public final class CommandGraph {

    // Primary storage: name -> RootNode
    private final Map<String, RootNode> roots = new ConcurrentHashMap<>();

    // Alias index: alias -> primary name
    private final Map<String, String> aliasIndex = new ConcurrentHashMap<>();

    // Owner index: owner plugin name -> set of root command names
    private final Map<String, Set<String>> ownerIndex = new ConcurrentHashMap<>();

    // Lock for compound operations
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * Registers a root command node.
     *
     * <p>If a command with the same name already exists, it will be replaced.
     * Aliases are also indexed for fast lookup.</p>
     *
     * @param root The root node to register
     * @throws IllegalArgumentException if root is null
     */
    public void register(@NotNull RootNode root) {
        Objects.requireNonNull(root, "root");

        lock.writeLock().lock();
        try {
            String name = root.name();

            // Remove old aliases if updating an existing command
            RootNode existing = roots.get(name);
            if (existing != null) {
                existing.aliases().forEach(aliasIndex::remove);
            }

            // Register the root
            roots.put(name, root);

            // Index aliases
            for (String alias : root.aliases()) {
                aliasIndex.put(alias, name);
            }

            // Update owner index
            String ownerName = root.owner().getName();
            ownerIndex.computeIfAbsent(ownerName, k -> ConcurrentHashMap.newKeySet())
                    .add(name);

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Unregisters a command by name.
     *
     * @param name The command name (not alias)
     * @return The removed root node, or null if not found
     */
    @Nullable
    public RootNode unregister(@NotNull String name) {
        Objects.requireNonNull(name, "name");
        String normalized = name.toLowerCase(Locale.ROOT);

        lock.writeLock().lock();
        try {
            RootNode removed = roots.remove(normalized);
            if (removed != null) {
                // Remove aliases
                removed.aliases().forEach(aliasIndex::remove);

                // Update owner index
                String ownerName = removed.owner().getName();
                Set<String> ownerRoots = ownerIndex.get(ownerName);
                if (ownerRoots != null) {
                    ownerRoots.remove(normalized);
                    if (ownerRoots.isEmpty()) {
                        ownerIndex.remove(ownerName);
                    }
                }
            }
            return removed;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Unregisters all commands owned by a plugin.
     *
     * @param owner The owner plugin
     * @return List of removed root nodes
     */
    @NotNull
    public List<RootNode> unregisterAll(@NotNull Plugin owner) {
        Objects.requireNonNull(owner, "owner");

        lock.writeLock().lock();
        try {
            Set<String> rootNames = ownerIndex.remove(owner.getName());
            if (rootNames == null || rootNames.isEmpty()) {
                return List.of();
            }

            List<RootNode> removed = new ArrayList<>(rootNames.size());
            for (String name : rootNames) {
                RootNode root = roots.remove(name);
                if (root != null) {
                    root.aliases().forEach(aliasIndex::remove);
                    removed.add(root);
                }
            }
            return removed;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Looks up a root command by name or alias.
     *
     * @param nameOrAlias The command name or alias
     * @return The root node, or null if not found
     */
    @Nullable
    public RootNode getRoot(@NotNull String nameOrAlias) {
        String normalized = nameOrAlias.toLowerCase(Locale.ROOT);

        // Direct lookup first (O(1))
        RootNode direct = roots.get(normalized);
        if (direct != null) {
            return direct;
        }

        // Check alias index (O(1))
        String primary = aliasIndex.get(normalized);
        if (primary != null) {
            return roots.get(primary);
        }

        return null;
    }

    /**
     * Resolves a command path to find the target node.
     *
     * <p>Given a path like ["mycommand", "sub", "nested"], this method
     * traverses the tree and returns the deepest matching node along
     * with the remaining unparsed arguments.</p>
     *
     * @param path The command path tokens
     * @return Resolution result with the matched node and remaining args
     */
    @NotNull
    public ResolutionResult resolve(@NotNull String[] path) {
        if (path.length == 0) {
            return ResolutionResult.empty();
        }

        // Find root
        RootNode root = getRoot(path[0]);
        if (root == null) {
            return ResolutionResult.empty();
        }

        // Single token = root only
        if (path.length == 1) {
            return new ResolutionResult(root, root, List.of());
        }

        // Traverse subcommands
        com.afterlands.core.commands.registry.nodes.CommandNode current = root;
        int consumed = 1;

        for (int i = 1; i < path.length; i++) {
            SubNode child = current.child(path[i]);
            if (child == null) {
                // No more subcommands, rest are arguments
                break;
            }
            current = child;
            consumed++;
        }

        // Remaining tokens are arguments
        List<String> remaining = consumed < path.length
                ? Arrays.asList(path).subList(consumed, path.length)
                : List.of();

        return new ResolutionResult(root, current, remaining);
    }

    /**
     * Gets all registered root commands.
     *
     * @return Unmodifiable collection of root nodes
     */
    @NotNull
    public Collection<RootNode> roots() {
        return Collections.unmodifiableCollection(roots.values());
    }

    /**
     * Gets all root command names (without aliases).
     *
     * @return Unmodifiable set of root command names
     */
    @NotNull
    public Set<String> rootNames() {
        return Collections.unmodifiableSet(roots.keySet());
    }

    /**
     * Gets root commands owned by a specific plugin.
     *
     * @param owner The owner plugin
     * @return List of root nodes owned by the plugin
     */
    @NotNull
    public List<RootNode> getByOwner(@NotNull Plugin owner) {
        Set<String> names = ownerIndex.get(owner.getName());
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        return names.stream()
                .map(roots::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Checks if a command name or alias is registered.
     *
     * @param nameOrAlias Name or alias to check
     * @return true if registered
     */
    public boolean contains(@NotNull String nameOrAlias) {
        String normalized = nameOrAlias.toLowerCase(Locale.ROOT);
        return roots.containsKey(normalized) || aliasIndex.containsKey(normalized);
    }

    /**
     * Returns the number of registered root commands.
     */
    public int size() {
        return roots.size();
    }

    /**
     * Clears all registered commands.
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            roots.clear();
            aliasIndex.clear();
            ownerIndex.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Result of resolving a command path.
     *
     * @param root       The root node (null if not found)
     * @param node       The deepest matched node
     * @param remaining  Remaining unparsed tokens (arguments)
     */
    public record ResolutionResult(
            @Nullable RootNode root,
            @Nullable com.afterlands.core.commands.registry.nodes.CommandNode node,
            @NotNull List<String> remaining
    ) {
        /**
         * Returns an empty result (command not found).
         */
        public static ResolutionResult empty() {
            return new ResolutionResult(null, null, List.of());
        }

        /**
         * Checks if resolution was successful.
         */
        public boolean found() {
            return root != null && node != null;
        }

        /**
         * Checks if the resolved node is executable.
         */
        public boolean executable() {
            return node != null && node.isExecutable();
        }

        /**
         * Gets the command path that was resolved.
         */
        @NotNull
        public String path() {
            if (root == null) {
                return "";
            }
            if (node == root) {
                return root.name();
            }
            // Build path from root to node (would need parent refs for full path)
            return root.name() + " " + node.name();
        }
    }
}
