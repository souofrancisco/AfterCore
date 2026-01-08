## Threading & Performance

### Princípio: “Main thread sagrada”

No Bukkit/Spigot 1.8.8, a thread principal é responsável por ticks, eventos e a maior parte da API do servidor. Bloqueios aqui degradam TPS.

- **NUNCA** faça I/O bloqueante na main thread:
  - JDBC (`Connection`, `PreparedStatement`, `ResultSet`)
  - leitura/escrita de arquivos grandes
  - chamadas de rede (HTTP, sockets)
- **Evite** `.join()` / `.get()` de `CompletableFuture` na main thread.

### Como o AfterCore ajuda

- **`SqlService`**: oferece `runAsync`/`supplyAsync`/`inTransaction` para executar JDBC fora da main thread.
- **`ConditionService`**: `evaluate(...)` alterna para main thread quando PlaceholderAPI é necessária.
- **`SchedulerService`**: expõe executores globais e helpers `runSync`.

### PlaceholderAPI

PlaceholderAPI **não é thread-safe** e deve rodar na main thread.

Padrão recomendado:

```java
core.scheduler().runSync(() -> {
  // chamar PlaceholderAPI aqui (se você estiver usando diretamente)
});
```

Para condições, prefira:

```java
core.conditions().evaluate(player, "%player_health% > 10", ctx)
  .thenAccept(ok -> { /* ... */ });
```

### Caches e hot paths

Em servidores com alto CCU, alguns padrões evitam custo desnecessário:

- **Evite recompilar/parsear** em loops por tick.
- **Batch updates** (inventários): faça várias mudanças em `InventoryState` e dê um `refreshInventory` apenas no final.
- **Use HYBRID** para paginação com listas grandes (inventários).

### Debug mode

O AfterCore tem logs extras quando `debug: true` no `config.yml`.

