## Lista completa de tipos `public` (gerada do código)

Esta lista é útil para **auditoria de API** (“o que está `public` hoje?”).

> Importante: alguns tipos `public` são **implementações internas** (`impl`) ou **stubs** (ex.: `DefaultCommandService`). Eles aparecem aqui porque são `public`, mas **não são recomendados** como dependência direta — prefira as interfaces expostas por `AfterCoreAPI`.

### API / bootstrap

- `com.afterlands.core.api.AfterCore`
- `com.afterlands.core.api.AfterCoreAPI`
- `com.afterlands.core.bootstrap.AfterCorePlugin` *(plugin principal; não recomendado para consumo direto)*

### Services (interfaces)

- `com.afterlands.core.concurrent.SchedulerService`
- `com.afterlands.core.database.SqlService`
- `com.afterlands.core.conditions.ConditionService`
- `com.afterlands.core.actions.ActionService`
- `com.afterlands.core.commands.CommandService`
- `com.afterlands.core.config.ConfigService`
- `com.afterlands.core.config.MessageService`
- `com.afterlands.core.protocol.ProtocolService`
- `com.afterlands.core.diagnostics.DiagnosticsService`
- `com.afterlands.core.metrics.MetricsService`
- `com.afterlands.core.inventory.InventoryService`

### Actions

- `com.afterlands.core.actions.ActionExecutor`
- `com.afterlands.core.actions.ActionHandler`
- `com.afterlands.core.actions.ActionScope`
- `com.afterlands.core.actions.ActionSpec`
- `com.afterlands.core.actions.ActionTrigger`
- `com.afterlands.core.actions.dialect.ActionDialect`
- `com.afterlands.core.actions.dialect.MotionActionDialect`
- `com.afterlands.core.actions.dialect.SimpleKvActionDialect`
- Handlers padrão:
  - `com.afterlands.core.actions.handlers.ActionBarHandler`
  - `com.afterlands.core.actions.handlers.CenteredMessageHandler`
  - `com.afterlands.core.actions.handlers.ConsoleCommandHandler`
  - `com.afterlands.core.actions.handlers.GlobalCenteredMessageHandler`
  - `com.afterlands.core.actions.handlers.GlobalMessageHandler`
  - `com.afterlands.core.actions.handlers.MessageHandler`
  - `com.afterlands.core.actions.handlers.PlayerCommandHandler`
  - `com.afterlands.core.actions.handlers.PotionHandler`
  - `com.afterlands.core.actions.handlers.ResourcePackSoundHandler`
  - `com.afterlands.core.actions.handlers.SoundHandler`
  - `com.afterlands.core.actions.handlers.TeleportHandler`
  - `com.afterlands.core.actions.handlers.TitleHandler`
- Implementação:
  - `com.afterlands.core.actions.impl.DefaultActionService`

### Conditions

- `com.afterlands.core.conditions.ConditionContext`
- `com.afterlands.core.conditions.ConditionVariableProvider`
- Implementações:
  - `com.afterlands.core.conditions.impl.DefaultConditionService`
  - `com.afterlands.core.conditions.impl.EmptyConditionContext`

### SQL / DB

- `com.afterlands.core.database.DatabaseType`
- `com.afterlands.core.database.SqlConsumer`
- `com.afterlands.core.database.SqlFunction`
- `com.afterlands.core.database.SqlMigration`
- Implementações/dialetos:
  - `com.afterlands.core.database.impl.HikariSqlService`
  - `com.afterlands.core.database.impl.dialect.DatabaseDialect`
  - `com.afterlands.core.database.impl.dialect.MySqlDialect`
  - `com.afterlands.core.database.impl.dialect.SqliteDialect`

### Protocol

- `com.afterlands.core.protocol.ChunkMutationProvider`
- `com.afterlands.core.protocol.BlockMutation`
- `com.afterlands.core.protocol.BlockPosKey`
- `com.afterlands.core.protocol.ProtocolStats`
- Implementações:
  - `com.afterlands.core.protocol.impl.ChunkDebounceBatcher`
  - `com.afterlands.core.protocol.impl.ChunkMutationMerger`
  - `com.afterlands.core.protocol.impl.DefaultProtocolService`

### Diagnostics

- `com.afterlands.core.diagnostics.DiagnosticsSnapshot`
- Implementações/command:
  - `com.afterlands.core.diagnostics.commands.ACoreCommand`
  - `com.afterlands.core.diagnostics.impl.DefaultDiagnosticsService`

### Metrics

- `com.afterlands.core.metrics.MetricType`
- `com.afterlands.core.metrics.MetricsSnapshot`
- Implementações:
  - `com.afterlands.core.metrics.impl.DefaultMetricsService`

### Inventory Framework (tipos públicos)

- `com.afterlands.core.inventory.InventoryConfig`
- `com.afterlands.core.inventory.InventoryContext`
- `com.afterlands.core.inventory.InventoryState`
- `com.afterlands.core.inventory.action.InventoryActionHandler`
- `com.afterlands.core.inventory.action.ParsedAction`
- `com.afterlands.core.inventory.animation.ActiveAnimation`
- `com.afterlands.core.inventory.animation.AnimationConfig`
- `com.afterlands.core.inventory.animation.AnimationFrame`
- `com.afterlands.core.inventory.animation.InventoryAnimator`
- `com.afterlands.core.inventory.cache.CacheKey`
- `com.afterlands.core.inventory.cache.ItemCache`
- `com.afterlands.core.inventory.config.InventoryConfigManager`
- `com.afterlands.core.inventory.diagnostics.MemoryLeakDetector`
- `com.afterlands.core.inventory.drag.DragAndDropHandler`
- `com.afterlands.core.inventory.drag.DragSession`
- `com.afterlands.core.inventory.drag.ValidationResult`
- `com.afterlands.core.inventory.impl.DefaultInventoryService`
- `com.afterlands.core.inventory.item.GuiItem`
- `com.afterlands.core.inventory.item.ItemCompiler`
- `com.afterlands.core.inventory.item.NBTItemBuilder`
- `com.afterlands.core.inventory.item.PlaceholderResolver`
- `com.afterlands.core.inventory.migrations.CreateInventoryStatesMigration`
- `com.afterlands.core.inventory.pagination.PaginatedView`
- `com.afterlands.core.inventory.pagination.PaginationConfig`
- `com.afterlands.core.inventory.pagination.PaginationEngine`
- `com.afterlands.core.inventory.shared.SharedInventoryContext`
- `com.afterlands.core.inventory.shared.SharedInventoryManager`
- `com.afterlands.core.inventory.state.InventoryStateManager`
- `com.afterlands.core.inventory.tab.TabConfig`
- `com.afterlands.core.inventory.tab.TabManager`
- `com.afterlands.core.inventory.tab.TabState`
- `com.afterlands.core.inventory.title.TitleUpdateSupport`
- `com.afterlands.core.inventory.view.InventoryViewHolder`

### Config / mensagens / comandos

- Implementações:
  - `com.afterlands.core.config.impl.DefaultConfigService`
  - `com.afterlands.core.config.impl.DefaultMessageService`
  - `com.afterlands.core.commands.impl.DefaultCommandService`
- Update/validate:
  - `com.afterlands.core.config.update.ConfigUpdater`
  - `com.afterlands.core.config.validate.ConfigValidator`
  - `com.afterlands.core.config.validate.ValidationError`
  - `com.afterlands.core.config.validate.ValidationResult`
- Anotações:
  - `com.afterlands.core.commands.annotations.Command`
  - `com.afterlands.core.commands.annotations.Subcommand`
  - `com.afterlands.core.commands.annotations.Permission`

### Result / utilitários

- `com.afterlands.core.result.CoreResult`
- `com.afterlands.core.result.CoreErrorCode`
- `com.afterlands.core.util.StringUtil`
- `com.afterlands.core.util.PluginBanner`
- Retry:
  - `com.afterlands.core.util.retry.Backoff`
  - `com.afterlands.core.util.retry.RetryPolicy`
  - `com.afterlands.core.util.retry.RetryExecutor`
- Rate limit:
  - `com.afterlands.core.util.ratelimit.RateLimiter`
  - `com.afterlands.core.util.ratelimit.TokenBucketRateLimiter`
  - `com.afterlands.core.util.ratelimit.CooldownService`
- Espacial:
  - `com.afterlands.core.spatial.ChunkKey`
  - `com.afterlands.core.spatial.ChunkSpatialIndex`

