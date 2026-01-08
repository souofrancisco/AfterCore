## `DiagnosticsService`

### O que é

`DiagnosticsService` fornece **health check** e inspeção do estado atual do AfterCore:

- dependências detectadas (ProtocolLib, PlaceholderAPI)
- database (enabled, initialized, ping, pool stats)
- pools de threads (configurados)
- sistema (JVM/OS/memória)

### API

- `DiagnosticsSnapshot captureSnapshot()`
  - Operação rápida e thread-safe.
- `long pingDatabase()`
  - **Bloqueante**. Execute fora da main thread.

### `DiagnosticsSnapshot`

É um `record` com:

- `timestamp`
- `dependencies`: map de `DependencyInfo`
- `databaseInfo`: `DatabaseInfo` (inclui `PoolStats`)
- `threadPoolInfo`
- `systemInfo`

### Exemplo: snapshot em comando

```java
DiagnosticsSnapshot snap = core.diagnostics().captureSnapshot();
sender.sendMessage("Java: " + snap.systemInfo().javaVersion());
sender.sendMessage("DB enabled: " + snap.databaseInfo().enabled());
```

### Exemplo: ping do DB (async)

```java
CompletableFuture.supplyAsync(() -> core.diagnostics().pingDatabase(), core.scheduler().ioExecutor())
  .thenAccept(ms -> core.scheduler().runSync(() -> {
    player.sendMessage("Ping DB: " + ms + "ms");
  }));
```

### `/acore` (admin)

O AfterCore inclui o comando `/acore` com subcomandos de diagnóstico (permissão `aftercore.admin`):

- `status`, `db`, `threads`, `system`, `metrics`, `memory`, `all`

