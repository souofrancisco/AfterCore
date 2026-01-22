package com.afterlands.core.concurrent.impl;

import com.afterlands.core.concurrent.SchedulerService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class DefaultSchedulerService implements SchedulerService {

    private final Plugin plugin;
    private final boolean debug;

    private final ExecutorService ioExecutor;
    private final ExecutorService cpuExecutor;

    public DefaultSchedulerService(@NotNull Plugin plugin, boolean debug) {
        this.plugin = plugin;
        this.debug = debug;

        int ioThreads = Math.max(2, plugin.getConfig().getInt("concurrency.io-threads", 8));
        int cpuThreads = Math.max(1, plugin.getConfig().getInt("concurrency.cpu-threads", 4));

        this.ioExecutor = Executors.newFixedThreadPool(ioThreads, namedFactory("AfterCore-IO"));
        this.cpuExecutor = Executors.newFixedThreadPool(cpuThreads, namedFactory("AfterCore-CPU"));

        if (debug) {
            plugin.getLogger().info("[AfterCore] Scheduler: ioThreads=" + ioThreads + ", cpuThreads=" + cpuThreads);
        }
    }

    private static ThreadFactory namedFactory(String prefix) {
        AtomicInteger idx = new AtomicInteger(1);
        return r -> {
            Thread t = new Thread(r);
            t.setName(prefix + "-" + idx.getAndIncrement());
            t.setDaemon(true);
            return t;
        };
    }

    @Override
    public @NotNull Executor ioExecutor() {
        return ioExecutor;
    }

    @Override
    public @NotNull Executor cpuExecutor() {
        return cpuExecutor;
    }

    @Override
    public @NotNull CompletableFuture<Void> runSync(@NotNull Runnable task) {
        CompletableFuture<Void> cf = new CompletableFuture<>();
        if (Bukkit.isPrimaryThread()) {
            try {
                task.run();
                cf.complete(null);
            } catch (Throwable t) {
                cf.completeExceptionally(t);
            }
            return cf;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                task.run();
                cf.complete(null);
            } catch (Throwable t) {
                cf.completeExceptionally(t);
            }
        });
        return cf;
    }

    @Override
    public @NotNull <T> CompletableFuture<T> supplySync(@NotNull java.util.function.Supplier<T> supplier) {
        CompletableFuture<T> cf = new CompletableFuture<>();
        if (Bukkit.isPrimaryThread()) {
            try {
                cf.complete(supplier.get());
            } catch (Throwable t) {
                cf.completeExceptionally(t);
            }
            return cf;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                cf.complete(supplier.get());
            } catch (Throwable t) {
                cf.completeExceptionally(t);
            }
        });
        return cf;
    }

    @Override
    public @NotNull CompletableFuture<Void> delay(long ticks) {
        CompletableFuture<Void> cf = new CompletableFuture<>();
        if (ticks <= 0) {
            cf.complete(null);
            return cf;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> cf.complete(null), ticks);
        return cf;
    }

    @Override
    public @NotNull CompletableFuture<Void> runLaterSync(@NotNull Runnable task, long delayTicks) {
        CompletableFuture<Void> cf = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                task.run();
                cf.complete(null);
            } catch (Throwable t) {
                cf.completeExceptionally(t);
            }
        }, Math.max(0, delayTicks));
        return cf;
    }

    @Override
    public @NotNull Plugin plugin() {
        return plugin;
    }

    @Override
    public void shutdown() {
        try {
            ioExecutor.shutdownNow();
        } catch (Throwable ignored) {
        }
        try {
            cpuExecutor.shutdownNow();
        } catch (Throwable ignored) {
        }
    }
}
