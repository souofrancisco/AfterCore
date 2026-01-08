## `MetricsService`

### O que é

API leve para métricas (thread-safe e baixo overhead):

- contadores (`increment`)
- timers (`recordTime`, `time`)
- gauges (`gauge`)
- snapshot (`snapshot().format()`)

### Exemplos

#### Contadores

```java
core.metrics().increment("events.player_join");
core.metrics().increment("database.queries", 5);
```

#### Timer (manual)

```java
long start = System.nanoTime();
// ... operação ...
core.metrics().recordTime("my.op", System.nanoTime() - start);
```

#### Timer (wrapper)

```java
String result = core.metrics().time("my.op", () -> doSomething());
```

#### Gauges + snapshot

```java
core.metrics().gauge("players.online", Bukkit.getOnlinePlayers().size());
MetricsSnapshot snap = core.metrics().snapshot();
getLogger().info(snap.format());
```

### Reset

- `reset()` limpa tudo
- `reset(name)` limpa uma métrica específica

