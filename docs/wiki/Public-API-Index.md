## Visão geral da API pública

Esta página lista os principais **pontos públicos** do AfterCore. Alguns pacotes contêm classes `public` que existem por necessidade interna/legado; quando esse for o caso, a página do componente marca como **“avançado/instável”**.

### Entry points

- **`com.afterlands.core.api.AfterCore`**
  - Helper estático para obter `AfterCoreAPI` via `Bukkit ServicesManager` (com cache).
- **`com.afterlands.core.api.AfterCoreAPI`**
  - Interface pública que expõe todos os serviços.

### Serviços (via `AfterCoreAPI`)

- **`SchedulerService`** (`core.scheduler()`)
  - Executors (I/O e CPU), `runSync`, `runLaterSync`.
- **`SqlService`** (`core.sql()`)
  - Pool JDBC + helpers async (`runAsync`, `supplyAsync`, `inTransaction`), migrations e stats.
- **`ConditionService`** (`core.conditions()`)
  - Avaliação de expressões com AND/OR/NOT, comparações, placeholders e providers namespaced.
- **`ActionService`** (`core.actions()`)
  - Parser multi-dialeto + registro de handlers (`registerHandler`).
- **`InventoryService`** (`core.inventory()`)
  - Inventory Framework (YAML + state + pagination/tabs/drag/shared/persistence).
- **`ProtocolService`** (`core.protocol()`)
  - Registro de `ChunkMutationProvider`, stats do pipeline.
- **`DiagnosticsService`** (`core.diagnostics()`)
  - Snapshot do sistema e ping de DB.
- **`MetricsService`** (`core.metrics()`)
  - Counters/timers/gauges + snapshot.
- **`ConfigService`** (`core.config()`) e **`MessageService`** (`core.messages()`)
  - Acesso ao `config.yml`/`messages.yml`, reload e formatação/envio.
- **`CommandService`** (`core.commands()`)
  - Framework leve (registro via objeto e anotações).

### Tipos utilitários relevantes (consumo direto)

- **Resultados e erros**
  - `com.afterlands.core.result.CoreResult`
  - `com.afterlands.core.result.CoreErrorCode`
- **Retry/backoff**
  - `com.afterlands.core.util.retry.RetryPolicy`
  - `com.afterlands.core.util.retry.RetryExecutor`
  - `com.afterlands.core.util.retry.Backoff`
- **Rate limiting**
  - `com.afterlands.core.util.ratelimit.RateLimiter`
  - `com.afterlands.core.util.ratelimit.CooldownService`
  - `com.afterlands.core.util.ratelimit.TokenBucketRateLimiter`
- **Strings**
  - `com.afterlands.core.util.StringUtil`
- **Espacial**
  - `com.afterlands.core.spatial.ChunkKey`
  - `com.afterlands.core.spatial.ChunkSpatialIndex`

