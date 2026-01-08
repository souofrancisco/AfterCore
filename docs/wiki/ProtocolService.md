## `ProtocolService`

### O que é

`ProtocolService` centraliza a integração com **ProtocolLib** e expõe um pipeline para múltiplos plugins contribuírem com “mutations” de chunk (MAP_CHUNK) sem conflitos.

> Estado atual: a API e a estrutura existem; o pipeline completo pode evoluir em releases futuras.

### API

- `start()` / `stop()`
- `registerChunkProvider(ChunkMutationProvider provider)`
- `unregisterChunkProvider(String id)`
- `getProviders()`: lista ordenada por `priority()` ascendente
- `getStats()`: `ProtocolStats` (snapshot)

### `ChunkMutationProvider`

Interface pública para “injetar” alterações por chunk:

- `id()`: identificador único
- `priority()`: maior prioridade = aplicado por último (último ganha em conflitos)
- `mutationsForChunk(player, world, chunkX, chunkZ)` → `List<BlockMutation>`

**Regra crítica**: `mutationsForChunk` não pode bloquear a main thread. Se você precisa de I/O, retorne lista vazia e agende um update/refresh.

### Exemplo: provider simples

```java
core.protocol().registerChunkProvider(new ChunkMutationProvider() {
  @Override public String id() { return "myplugin:glowstone"; }
  @Override public int priority() { return 100; }

  @Override
  public List<BlockMutation> mutationsForChunk(Player player, World world, int chunkX, int chunkZ) {
    // Exemplo: forçar um bloco em (x,y,z) absoluto (id/data 1.8.8)
    return List.of(new BlockMutation(0, 64, 0, 89 /* glowstone */, (byte) 0));
  }
});
```

### `ProtocolStats`

`ProtocolStats` contém contadores de processamento e uma lista de `ProviderStat` por provider (mutations fornecidas, conflitos, etc.).

### Dicas

- Use `priority()` para resolver conflitos entre plugins (por exemplo: “cosmético” por último).
- Evite listas enormes por chunk; prefira estruturas compactas e cacheadas.

