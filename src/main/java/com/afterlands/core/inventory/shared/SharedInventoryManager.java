package com.afterlands.core.inventory.shared;

import com.afterlands.core.concurrent.SchedulerService;
import com.afterlands.core.inventory.InventoryContext;
import com.afterlands.core.inventory.view.InventoryViewHolder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gerenciador de inventários compartilhados (multi-player).
 *
 * <p>
 * Responsável por:
 * </p>
 * <ul>
 * <li>Criar e gerenciar sessões compartilhadas</li>
 * <li>Adicionar/remover players de sessões</li>
 * <li>Broadcast de updates para todos os players</li>
 * <li>Sincronização com copy-on-write strategy</li>
 * </ul>
 *
 * <p>
 * <b>Thread Safety:</b> Usa ReadWriteLock para read-heavy operations.
 * Copy-on-write para estado compartilhado minimiza locks.
 * </p>
 *
 * <p>
 * <b>Performance:</b> Debounce de 2 ticks para batch updates.
 * </p>
 */
public class SharedInventoryManager {

    private final org.bukkit.plugin.Plugin plugin;
    private final SchedulerService scheduler;
    private final Logger logger;
    private final boolean debug;

    // Sessões ativas por sessionId
    private final Map<String, SharedInventorySession> activeSessions;

    // Lock para read-heavy operations
    private final ReadWriteLock lock;

    // Pending updates (debounce)
    private final Map<String, Map<Integer, ItemStack>> pendingUpdates;
    private BukkitTask debounceTask;

    public SharedInventoryManager(
            @NotNull org.bukkit.plugin.Plugin plugin,
            @NotNull SchedulerService scheduler,
            @NotNull Logger logger,
            boolean debug) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.logger = logger;
        this.debug = debug;

        this.activeSessions = new ConcurrentHashMap<>();
        this.lock = new ReentrantReadWriteLock();
        this.pendingUpdates = new ConcurrentHashMap<>();

        // Inicia debounce task (a cada 2 ticks)
        startDebounceTask();
    }

    /**
     * Cria sessão compartilhada.
     *
     * @param players     Lista de players
     * @param inventoryId ID do inventário
     * @param context     Contexto base
     * @return Session ID único
     */
    @NotNull
    public String createSharedSession(
            @NotNull List<Player> players,
            @NotNull String inventoryId,
            @NotNull InventoryContext context) {
        if (players.isEmpty()) {
            throw new IllegalArgumentException("players list cannot be empty");
        }

        List<UUID> playerIds = players.stream()
                .map(Player::getUniqueId)
                .toList();

        SharedInventoryContext sharedContext = SharedInventoryContext.create(context, playerIds);

        SharedInventorySession session = new SharedInventorySession(
                sharedContext.sessionId(),
                inventoryId,
                sharedContext,
                new ConcurrentHashMap<>());

        lock.writeLock().lock();
        try {
            activeSessions.put(session.sessionId(), session);

            if (debug) {
                logger.info("Created shared session: " + session.sessionId() +
                        " for inventory " + inventoryId +
                        " with " + players.size() + " players");
            }
        } finally {
            lock.writeLock().unlock();
        }

        return session.sessionId();
    }

    /**
     * Adiciona player a uma sessão existente.
     *
     * @param sessionId ID da sessão
     * @param player    Player a adicionar
     * @return true se adicionado, false se sessão não existe
     */
    public boolean addPlayer(@NotNull String sessionId, @NotNull Player player) {
        lock.writeLock().lock();
        try {
            SharedInventorySession session = activeSessions.get(sessionId);
            if (session == null) {
                if (debug) {
                    logger.warning("Attempted to add player to non-existent session: " + sessionId);
                }
                return false;
            }

            UUID playerId = player.getUniqueId();

            // Atualiza contexto (copy-on-write)
            SharedInventoryContext updatedContext = session.context().withPlayer(playerId);
            session = new SharedInventorySession(
                    session.sessionId(),
                    session.inventoryId(),
                    updatedContext,
                    session.viewHolders());

            activeSessions.put(sessionId, session);

            if (debug) {
                logger.fine("Added player " + player.getName() + " to session " + sessionId);
            }

            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Remove player de uma sessão.
     *
     * @param sessionId ID da sessão
     * @param playerId  UUID do player
     * @return true se removido, false se sessão não existe
     */
    public boolean removePlayer(@NotNull String sessionId, @NotNull UUID playerId) {
        lock.writeLock().lock();
        try {
            SharedInventorySession session = activeSessions.get(sessionId);
            if (session == null) {
                return false;
            }

            // Remove do context
            SharedInventoryContext updatedContext = session.context().withoutPlayer(playerId);

            // Remove view holder
            Map<UUID, InventoryViewHolder> updatedHolders = new ConcurrentHashMap<>(session.viewHolders());
            updatedHolders.remove(playerId);

            session = new SharedInventorySession(
                    session.sessionId(),
                    session.inventoryId(),
                    updatedContext,
                    updatedHolders);

            // Se não há mais players, fecha sessão
            if (updatedContext.getPlayerCount() == 0) {
                activeSessions.remove(sessionId);
                if (debug) {
                    logger.info("Closed empty session: " + sessionId);
                }
            } else {
                activeSessions.put(sessionId, session);
                if (debug) {
                    logger.fine("Removed player from session " + sessionId +
                            " (" + updatedContext.getPlayerCount() + " remaining)");
                }
            }

            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Fecha sessão compartilhada para todos os players.
     *
     * @param sessionId ID da sessão
     */
    public void closeSession(@NotNull String sessionId) {
        lock.writeLock().lock();
        try {
            SharedInventorySession session = activeSessions.remove(sessionId);
            if (session == null) {
                if (debug) {
                    logger.warning("Attempted to close non-existent session: " + sessionId);
                }
                return;
            }

            // Fecha inventário para todos os players
            for (Map.Entry<UUID, InventoryViewHolder> entry : session.viewHolders().entrySet()) {
                InventoryViewHolder holder = entry.getValue();
                if (holder != null) {
                    scheduler.runSync(holder::close);
                }
            }

            // Limpa pending updates
            pendingUpdates.remove(sessionId);

            if (debug) {
                logger.info("Closed shared session: " + sessionId);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Broadcast update de item para todos os players da sessão.
     *
     * <p>
     * Usa debounce de 2 ticks para batch updates.
     * </p>
     *
     * @param sessionId ID da sessão
     * @param slot      Slot do item
     * @param item      Item atualizado
     */
    public void broadcastUpdate(@NotNull String sessionId, int slot, @Nullable ItemStack item) {
        lock.readLock().lock();
        try {
            SharedInventorySession session = activeSessions.get(sessionId);
            if (session == null) {
                if (debug) {
                    logger.warning("Attempted to broadcast to non-existent session: " + sessionId);
                }
                return;
            }

            // Adiciona a pending updates (debounce)
            pendingUpdates
                    .computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>())
                    .put(slot, item);

        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Registra view holder em uma sessão.
     *
     * @param sessionId ID da sessão
     * @param playerId  UUID do player
     * @param holder    View holder
     */
    public void registerViewHolder(
            @NotNull String sessionId,
            @NotNull UUID playerId,
            @NotNull InventoryViewHolder holder) {
        lock.writeLock().lock();
        try {
            SharedInventorySession session = activeSessions.get(sessionId);
            if (session == null) {
                if (debug) {
                    logger.warning("Attempted to register holder to non-existent session: " + sessionId);
                }
                return;
            }

            session.viewHolders().put(playerId, holder);

            if (debug) {
                logger.fine("Registered view holder for player " + playerId + " in session " + sessionId);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Obtém sessão ativa.
     *
     * @param sessionId ID da sessão
     * @return SharedInventorySession ou null se não existe
     */
    @Nullable
    public SharedInventorySession getSession(@NotNull String sessionId) {
        lock.readLock().lock();
        try {
            return activeSessions.get(sessionId);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Verifica se sessão existe.
     *
     * @param sessionId ID da sessão
     * @return true se existe
     */
    public boolean hasSession(@NotNull String sessionId) {
        lock.readLock().lock();
        try {
            return activeSessions.containsKey(sessionId);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Inicia task de debounce para batch updates.
     */
    private void startDebounceTask() {
        debounceTask = Bukkit.getScheduler().runTaskTimer(
                plugin,
                this::processPendingUpdates,
                2L, // Initial delay: 2 ticks
                2L // Interval: 2 ticks
        );

        if (debug) {
            logger.info("Debounce task started (interval: 2 ticks)");
        }
    }

    /**
     * Processa pending updates (batch).
     */
    private void processPendingUpdates() {
        if (pendingUpdates.isEmpty()) {
            return;
        }

        // Copy to avoid ConcurrentModificationException
        Map<String, Map<Integer, ItemStack>> toProcess = new HashMap<>(pendingUpdates);
        pendingUpdates.clear();

        for (Map.Entry<String, Map<Integer, ItemStack>> entry : toProcess.entrySet()) {
            String sessionId = entry.getKey();
            Map<Integer, ItemStack> updates = entry.getValue();

            lock.readLock().lock();
            try {
                SharedInventorySession session = activeSessions.get(sessionId);
                if (session == null) {
                    continue;
                }

                // Broadcast para todos os players
                for (Map.Entry<UUID, InventoryViewHolder> holderEntry : session.viewHolders().entrySet()) {
                    InventoryViewHolder holder = holderEntry.getValue();
                    if (holder != null) {
                        scheduler.runSync(() -> {
                            for (Map.Entry<Integer, ItemStack> update : updates.entrySet()) {
                                int slot = update.getKey();
                                ItemStack item = update.getValue();
                                holder.getInventory().setItem(slot, item);
                            }
                        });
                    }
                }

                if (debug) {
                    logger.fine("Broadcast " + updates.size() + " updates to session " + sessionId);
                }
            } finally {
                lock.readLock().unlock();
            }
        }
    }

    /**
     * Obtém estatísticas.
     *
     * @return Map com estatísticas
     */
    @NotNull
    public Map<String, Object> getStats() {
        lock.readLock().lock();
        try {
            Map<String, Object> stats = new HashMap<>();
            stats.put("activeSessions", activeSessions.size());
            stats.put("pendingUpdates", pendingUpdates.size());

            int totalPlayers = activeSessions.values().stream()
                    .mapToInt(s -> s.context().getPlayerCount())
                    .sum();
            stats.put("totalPlayers", totalPlayers);

            return stats;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Cleanup ao shutdown.
     */
    public void shutdown() {
        // Cancela debounce task
        if (debounceTask != null) {
            debounceTask.cancel();
            debounceTask = null;
        }

        // Processa pending updates
        processPendingUpdates();

        // Fecha todas as sessões
        lock.writeLock().lock();
        try {
            for (String sessionId : new ArrayList<>(activeSessions.keySet())) {
                closeSession(sessionId);
            }
            activeSessions.clear();

            if (debug) {
                logger.info("SharedInventoryManager shut down");
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Record interno para sessão compartilhada.
     */
    public record SharedInventorySession(
            @NotNull String sessionId,
            @NotNull String inventoryId,
            @NotNull SharedInventoryContext context,
            @NotNull Map<UUID, InventoryViewHolder> viewHolders) {
        public SharedInventorySession {
            if (sessionId == null || sessionId.isBlank()) {
                throw new IllegalArgumentException("sessionId cannot be null or blank");
            }
            if (inventoryId == null || inventoryId.isBlank()) {
                throw new IllegalArgumentException("inventoryId cannot be null or blank");
            }
            if (context == null) {
                throw new IllegalArgumentException("context cannot be null");
            }
            if (viewHolders == null) {
                viewHolders = new ConcurrentHashMap<>();
            }
        }
    }
}
