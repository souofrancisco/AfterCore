package com.afterlands.core.concurrent;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Scheduler/Executors globais do AfterCore.
 *
 * <p>
 * Objetivo: evitar dezenas de pools por plugin.
 * </p>
 */
public interface SchedulerService {

    @NotNull
    Executor ioExecutor();

    @NotNull
    Executor cpuExecutor();

    /**
     * Executa na main thread.
     */
    @NotNull
    CompletableFuture<Void> runSync(@NotNull Runnable task);

    /**
     * Executa na main thread e retorna resultado.
     */
    @NotNull
    <T> CompletableFuture<T> supplySync(@NotNull java.util.function.Supplier<T> supplier);

    /**
     * Retorna um Future que completa após X ticks (delay).
     */
    @NotNull
    CompletableFuture<Void> delay(long ticks);

    /**
     * Executa na main thread após delay (ticks).
     */
    @NotNull
    CompletableFuture<Void> runLaterSync(@NotNull Runnable task, long delayTicks);

    /**
     * Plugin dono (AfterCore).
     */
    @NotNull
    Plugin plugin();

    void shutdown();
}
