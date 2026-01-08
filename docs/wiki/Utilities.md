## Utilitários

### `StringUtil`

#### `centeredMessage(String)`

Centraliza mensagens no chat (pixel-perfect para a fonte padrão do Minecraft 1.8).

```java
player.sendMessage(StringUtil.centeredMessage("&6&lBem-vindo!"));
```

### `ChunkKey`

Pack/unpack de coordenadas de chunk em um `long` (útil para mapas e caches):

```java
long key = ChunkKey.pack(chunkX, chunkZ);
int x = ChunkKey.unpackX(key);
int z = ChunkKey.unpackZ(key);
```

Também inclui `blockToChunk(int blockCoord)`.

### `ChunkSpatialIndex<T>`

Índice espacial genérico por `(world, chunk)` para consultas eficientes.

Exemplo: registrar algo em um range e consultar por chunk:

```java
ChunkSpatialIndex<String> idx = new ChunkSpatialIndex<>();
idx.register("world", 0, 10, 0, 10, "zona_a");

List<String> inChunk = idx.getInChunk("world", 5, 5);
boolean hasAny = idx.hasInChunk("world", 5, 5);
```

Remoção:

```java
idx.unregister(v -> v.equals("zona_a"));
idx.clear();
```

### `BlockPosKey` (protocol)

Empacota `(x, y, z)` em um `long` para hashing eficiente durante merge de mutations:

```java
long packed = new BlockPosKey(x, y, z).packed();
BlockPosKey pos = BlockPosKey.fromPacked(packed);
```

### `PluginBanner`

Utility para imprimir banner ASCII e tempo de carregamento. Normalmente usado apenas pelo próprio AfterCore, mas pode ser reutilizado:

```java
PluginBanner.printBanner(this);
PluginBanner.printLoadTime(this, startTimeMs);
```

