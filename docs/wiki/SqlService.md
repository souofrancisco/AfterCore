## `SqlService`

### O que é

`SqlService` gerencia múltiplos **datasources JDBC** (cada um com seu próprio pool HikariCP) e fornece helpers para executar operações SQL de forma **async-first**.

> Importante: apesar de existir `getConnection()`, o padrão recomendado para plugins é **`runAsync`/`supplyAsync`/`inTransaction`**.

---

## Multi-Datasource (v2.0+)

A partir da v2.0, o `SqlService` suporta múltiplos datasources isolados, cada um com seu próprio pool HikariCP, migrations e configurações.

### Configuração

```yaml
database:
  enabled: true
  
  # Pool defaults (herdados por todos os datasources)
  pool:
    maximum-pool-size: 10
    minimum-idle: 2
  
  # Multi-datasource
  datasources:
    default:
      type: "mysql"
      mysql:
        host: "localhost"
        database: "afterlands"
        username: "root"
        password: ""
    
    analytics:
      type: "mysql"
      mysql:
        database: "afterlands_analytics"
      pool:
        maximum-pool-size: 5
```

> Se a seção `datasources` não estiver definida, o modo legado é usado (config raiz = datasource "default").

### Uso

```java
// Datasource default (retrocompatível)
core.sql().runAsync(conn -> { ... });

// Datasource específico (duas formas equivalentes)
core.sql("analytics").runAsync(conn -> { ... });
core.sql().datasource("analytics").runAsync(conn -> { ... });

// Verificar se datasource existe
if (core.sql().hasDatasource("analytics")) {
    // ...
}

// Listar todos os datasources
Set<String> names = core.sql().getDatasourceNames();
```

### Migrations por Datasource

```java
// Migration no datasource default
core.sql().registerMigration("myplugin:001_tables", conn -> { ... });

// Migration em datasource específico
core.sql().registerMigration("analytics", "myplugin:001_analytics", conn -> {
    try (var st = conn.createStatement()) {
        st.execute("""
            CREATE TABLE IF NOT EXISTS player_analytics (
                uuid VARCHAR(36) PRIMARY KEY,
                total_playtime BIGINT NOT NULL DEFAULT 0
            )
        """);
    }
});
```

### Health Check

```java
// Verificar disponibilidade de um datasource específico
core.sql("analytics").isAvailable().thenAccept(available -> {
    if (!available) {
        logger.warning("Analytics DB offline!");
    }
});

// Estatísticas de todos os datasources
Map<String, Map<String, Object>> allStats = core.sql().getAllPoolStats();
```

### Plugin Datasource Selection (v1.4.1+)

Permite que cada plugin especifique qual datasource usar através de sua própria configuração:

**No `config.yml` do plugin:**
```yaml
database:
  datasource: "analytics"  # Nome do datasource definido no AfterCore
```

**No código do plugin:**
```java
// Lê automaticamente database.datasource do config.yml
SqlDataSource ds = core.sql().forPlugin(this);

ds.runAsync(conn -> {
    // Usa o datasource especificado no config
});
```

> **Fallback:** Se `database.datasource` não estiver definido, usa `"default"`.  
> **Fail-fast:** Se o datasource especificado não existir, lança `IllegalStateException` com mensagem clara.

---

## API Principal

### SqlService (Registry)

| Método | Descrição |
|--------|-----------|
| `datasource(String name)` | Obtém datasource por nome (fail-fast) |
| `hasDatasource(String name)` | Verifica se datasource existe |
| `getDatasourceNames()` | Lista todos os datasources |
| `forPlugin(Plugin plugin)` | Obtém datasource configurado no plugin (lê `database.datasource`) |
| `registerMigration(dsName, id, migration)` | Registra migration em datasource específico |
| `getAllPoolStats()` | Estatísticas de todos os pools |

### SqlDataSource (Individual)

| Método | Descrição |
|--------|-----------|
| `name()` | Nome do datasource |
| `type()` | Tipo (MYSQL, SQLITE) |
| `isEnabled()` / `isInitialized()` | Status |
| `runAsync(consumer)` | Executa operação async |
| `supplyAsync(function)` | Executa operação async com retorno |
| `inTransaction(function)` | Executa em transação |
| `isAvailable()` | Ping test async |
| `getPoolStats()` | Estatísticas do pool |

### Métodos Retrocompatíveis

Todos os métodos antigos continuam funcionando e operam no datasource "default":

- `reloadFromConfig(ConfigurationSection section)`
- `isEnabled()` / `isInitialized()`
- `dataSource()` / `getConnection()`
- `runAsync(SqlConsumer<Connection>)`
- `supplyAsync(SqlFunction<Connection, T>)`
- `inTransaction(SqlFunction<Connection, T>)`
- `registerMigration(String id, SqlMigration migration)`
- `isAvailable()` (ping boolean async)
- `getPoolStats()` (map para diagnóstico)
- `close()`

---

## Exemplos

### Query async (read)

```java
core.sql().supplyAsync(conn -> {
    try (var ps = conn.prepareStatement("SELECT coins FROM player_data WHERE uuid=?")) {
        ps.setString(1, uuid.toString());
        try (var rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt("coins") : 0;
        }
    }
}).thenAccept(coins -> {
    // Callback em thread do executor (não main thread)
});
```

### Update async (write)

```java
core.sql().runAsync(conn -> {
    try (var ps = conn.prepareStatement("UPDATE player_data SET coins=? WHERE uuid=?")) {
        ps.setInt(1, newCoins);
        ps.setString(2, uuid.toString());
        ps.executeUpdate();
    }
});
```

### Transação

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

### Migrations (DDL idempotente)

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

---

## Threading (crítico)

- `runAsync` / `supplyAsync` / `inTransaction` executam fora da main thread.
- **Evite** chamar `getConnection()` na main thread.

## Pool Stats

`getPoolStats()` retorna um `Map<String, Object>` com:
- `active_connections`
- `idle_connections`
- `total_connections`
- `threads_awaiting_connection`


