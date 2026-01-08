## `SchedulerService`

### O que é

`SchedulerService` centraliza **executores globais** (I/O e CPU) e fornece helpers para executar código na **main thread**.

### API

- `ioExecutor()`: para tarefas bloqueantes (DB, filesystem).
- `cpuExecutor()`: para tarefas CPU-bound (parsing, cálculos).
- `runSync(Runnable)`: agenda na main thread e retorna `CompletableFuture<Void>`.
- `runLaterSync(Runnable, delayTicks)`: agenda na main thread após delay em ticks.
- `plugin()`: instância do plugin AfterCore.
- `shutdown()`: encerra pools internos (uso do core).

### Exemplos

#### Rodar algo na main thread a partir de async

```java
core.sql().runAsync(conn -> {
  // ... trabalho JDBC (async) ...
}).thenCompose(v -> core.scheduler().runSync(() -> {
  // ... agora na main thread ...
  player.sendMessage("Dados carregados!");
}));
```

#### Usar `ioExecutor()` manualmente

```java
CompletableFuture.supplyAsync(() -> {
  // tarefa bloqueante
  return loadFromDisk();
}, core.scheduler().ioExecutor());
```

### Boas práticas

- Use `ioExecutor()` para I/O; não “roube” a main thread.
- Para operações rápidas, `runSync` é ok; para loops grandes, faça pré-cálculo em `cpuExecutor()` e aplique resultado na main thread.

