## `SqlService`

### O que é

`SqlService` gerencia um **pool JDBC** (internamente via HikariCP sombreado) e fornece helpers para executar operações SQL de forma **async-first**.

> Importante: apesar de existir `getConnection()`, o padrão recomendado para plugins é **`runAsync`/`supplyAsync`/`inTransaction`**.

### API principal

- `reloadFromConfig(ConfigurationSection section)`
  - Recarrega/reativa/desativa o DB a partir da seção `database` do `config.yml`.
- `isEnabled()` / `isInitialized()`
- `dataSource()`
- `getConnection()`
- `runAsync(SqlConsumer<Connection>)`
- `supplyAsync(SqlFunction<Connection, T>)`
- `inTransaction(SqlFunction<Connection, T>)`
- `registerMigration(String id, SqlMigration migration)`
- `isAvailable()` (ping boolean async)
- `getPoolStats()` (map para diagnóstico)
- `close()`

### Exemplos

#### Query async (read)

```java
core.sql().supplyAsync(conn -> {
  try (var ps = conn.prepareStatement("SELECT coins FROM player_data WHERE uuid=?")) {
    ps.setString(1, uuid.toString());
    try (var rs = ps.executeQuery()) {
      return rs.next() ? rs.getInt("coins") : 0;
    }
  }
}).thenAccept(coins -> {
  // aqui você está no thread do executor do SQL (não necessariamente main thread)
});
```

#### Update async (write)

```java
core.sql().runAsync(conn -> {
  try (var ps = conn.prepareStatement("UPDATE player_data SET coins=? WHERE uuid=?")) {
    ps.setInt(1, newCoins);
    ps.setString(2, uuid.toString());
    ps.executeUpdate();
  }
});
```

#### Transação

```java
core.sql().inTransaction(conn -> {
  try (var a = conn.prepareStatement("UPDATE accounts SET balance=balance-? WHERE uuid=?");
       var b = conn.prepareStatement("UPDATE accounts SET balance=balance+? WHERE uuid=?")) {
    a.setInt(1, amount);
    a.setString(2, from.toString());
    a.executeUpdate();

    b.setInt(1, amount);
    b.setString(2, to.toString());
    b.executeUpdate();
  }
  return null;
});
```

#### Migrations (DDL idempotente)

```java
core.sql().registerMigration("myplugin:001_create_tables", conn -> {
  try (var st = conn.createStatement()) {
    st.execute("""
      CREATE TABLE IF NOT EXISTS player_data (
        uuid VARCHAR(36) PRIMARY KEY,
        coins INT NOT NULL DEFAULT 0
      )
    """);
  }
});
```

### Threading (crítico)

- `runAsync` / `supplyAsync` / `inTransaction` executam fora da main thread.
- **Evite** chamar `getConnection()` na main thread.

### Pool stats

`getPoolStats()` retorna um `Map<String, Object>` (para uso em diagnósticos, logs ou painéis). O conteúdo pode variar conforme dialeto/implementação.

