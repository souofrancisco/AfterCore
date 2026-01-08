## Retry / Backoff

### Componentes

- `RetryPolicy`: configura tentativas, tempo máximo total e estratégia de backoff.
- `RetryExecutor`: executa operações aplicando a `RetryPolicy`.
- `Backoff`: estratégias (`Exponential`, `Fixed`, `Linear`).

### Exemplo (padrão DB)

```java
var policy = com.afterlands.core.util.retry.RetryPolicy.defaultDatabasePolicy();
var executor = new com.afterlands.core.util.retry.RetryExecutor(policy, getLogger(), false);

// DICA: execute em thread async
CompletableFuture.supplyAsync(() -> {
  try {
    return executor.execute(() -> expensiveDbCall());
  } catch (Exception e) {
    throw new RuntimeException(e);
  }
}, core.scheduler().ioExecutor());
```

### Exemplo (custom)

```java
var policy = com.afterlands.core.util.retry.RetryPolicy.builder()
  .maxRetries(5)
  .maxElapsed(java.time.Duration.ofSeconds(20))
  .backoff(new com.afterlands.core.util.retry.Backoff.Exponential(
    java.time.Duration.ofMillis(100),
    java.time.Duration.ofSeconds(5),
    0.1
  ))
  .retryOnSqlExceptions()
  .build();
```

### Threading

`RetryExecutor.execute(...)` é **bloqueante** (faz `Thread.sleep` no backoff). Use:

- `executeAsync(operation, core.scheduler().ioExecutor())`, ou
- rode `execute(...)` manualmente em executor async.

