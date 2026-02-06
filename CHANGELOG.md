# Changelog

All notable changes to AfterCore will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.5.3] - 2026-02-05

### Changed
- **MessageFacade**:
  - Refactored to delegate message sending to `MessageService` for players.
  - Ensures consistent message handling (placeholders, storage) via the core service.
  - Removed Fully Qualified Names (FQN) usage.
  - Implemented lazy proxy in `DefaultMessageService` to auto-detect and delegate to external providers (e.g. AfterLanguage).
- Minor fixes.

## [1.5.2] - 2026-01-26

### Fixed (Action System)
- **Dialect Normalization**: 
  - `DefaultActionService` now skips normalization for lines starting with `@`
  - Ensures `MotionActionDialect` triggers (e.g., `@tick:`) are preserved correctly
- **Regex Null Safety**:
  - Fixed `MotionActionDialect` causing NPE when matching actions without arguments (e.g., `pause`)
  - Optional regex groups now safely default to empty string

## [1.5.1] - 2026-01-24
 
 ### Added (MessageService)
 - **Message Reloading Support**: Novo método `reloadMessages(Plugin)` na API
   - Permite que plugins externos solicitem o recarregamento de suas mensagens
   - Invalida cache interno no `DefaultMessageService`
   - Essencial para comandos de `reload` funcionarem corretamente com mensagens cacheadas

 - **Plugin-Specific Messages API**: `core.messages(Plugin)`
   - Permite obter um `MessageService` isolado para um plugin específico
   - Lê do `messages.yml` do plugin alvo (não do AfterCore)
   - Abstração padronizada para envio de mensagens com suporte a placeholders e listas
   - Comentário: Essencial para que plugins do ecossistema usem a infraestrutura do Core sem acoplamento direto
 
 ## [1.5.0] - 2026-01-23 (Holograms Update)

### Added (HologramService)
- **HologramService**: Novo serviço para criar e gerenciar hologramas via DecentHolograms API
  - `HologramService` interface principal com detecção de disponibilidade
  - `PluginHologramHandle` handle com escopo por plugin (namespacing automático)
  - Click handlers para interação via código (`onClick()`)
  - Suporte a hologramas individuais por jogador (`createForPlayer()`)

- **Plugin Namespacing**: Todos os hologramas são prefixados automaticamente com o nome do plugin
  - Evita conflitos de nomes entre plugins
  - Ex: Plugin "Shop" cria "npc" → nome interno "shop_npc"

- **Visibility Control**: Controle granular de visibilidade
  - `setDefaultVisible(false)`: Holograma invisível por padrão
  - `showTo(player)`: Mostra para player específico
  - `hideFrom(player)`: Esconde de player específico

- **Click Event Integration**: Handlers de clique com contexto completo
  - `HologramClickContext` record com player, click type, page index
  - Suporte a LEFT, RIGHT, SHIFT_LEFT, SHIFT_RIGHT
  - Integração direta com ActionService (futuro)

### Changed
- `AfterCoreAPI` agora expõe `holograms()` para acesso ao serviço
- `plugin.yml` atualizado com softdepend DecentHolograms

### Compatibility
- **100% Opcional**: Se DecentHolograms não instalado, `NoOpHologramService` é usado
  - `isAvailable()` retorna false
  - Todas as operações silenciosamente retornam false/null
  - Nenhum erro é lançado

### Documentation
- Novo `HologramService.md` na wiki com exemplos completos
- API Reference com tabelas de métodos


## [1.4.3] - 2026-01-22

### Added (Database)
- **Migration Tracking Table**: Nova tabela `aftercore_schema_migrations` para controle de migrations
  - Armazena: `migration_id`, `datasource_name`, `applied_at`
  - Garante que migrations sejam executadas apenas uma vez
  - Previne erros de re-execução de `ALTER TABLE` em ambientes de desenvolvimento

### Fixed
- **Late Plugin Loading**: Migrations agora são aplicadas imediatamente se o datasource já estiver inicializado
  - Corrige cenário onde plugins carregados tardiamente não tinham suas migrations executadas
- **Migration Idempotency**: Migrations registradas após inicialização do datasource agora são aplicadas corretamente

## [1.4.2] - 2026-01-21

### Added (Command Framework)
- **Custom Argument Types Support**:
  - `@Arg(type = "typename")`: atributo `type` para especificar explicitamente o nome do tipo
  - `TabCompleter` agora resolve tipos registrados por plugins (`ArgumentTypeRegistry.registerForPlugin`)
  - Permite tab-complete customizado para argumentos `String` (ex: IDs de configs, Enums dinâmicos)
  - `AnnotationProcessor` atualizado para respeitar o atributo `type`

### Added (Action Service)
- **Wait Action**: Nova action nativa `wait` (alias `delay`)
  - Pausa a execução da sequência de actions por X ticks
  - Exemplo: `wait: 60` (espera 3 segundos)
  - Implementado via `scheduler.delay()` na execução async

### Fixed
- **Tab Completion**: Resolvido problema onde tipos customizados não eram encontrados pelo `TabCompleter`

## [1.4.1] - 2026-01-19

### Added (SqlRegistry Multi-Datasource)
- **Multi-Datasource Support**: `SqlService` agora suporta múltiplos datasources isolados
  - `SqlDataSource` interface para operações em datasource individual
  - `HikariSqlDataSource` implementação com pool HikariCP dedicado
  - Cada datasource tem nome, tipo, pool e migrations próprios
- **Registry Pattern**: `SqlService` agora é um registry de datasources
  - `datasource(String name)`: Obtém datasource por nome (fail-fast)
  - `hasDatasource(String name)`: Verifica se datasource existe
  - `getDatasourceNames()`: Lista todos os datasources registrados
  - `getAllPoolStats()`: Estatísticas agregadas de todos os pools
- **Migrations por Datasource**: 
  - `registerMigration(dsName, id, migration)`: Registra migration em datasource específico
  - Migrations são executadas apenas no datasource alvo
- **API Simplificada**:
  - `core.sql("analytics")`: Atalho para obter datasource específico
  - Equivalente a `core.sql().datasource("analytics")`

### Added (Config System)
- **Robust Config API**: Nova API genérica para updates de configuração
  - `AfterCore.get().config().update(plugin, filename, options)`
  - Suporte a registro de migrations via lambda `Consumer<ConfigUpdater>`
- **Timestamped Backups**: Backups rotacionados com timestamp
  - Formato: `config.yml.YYYY-MM-DD_HH-mm-ss.bak`
  - Limite automático de 5 backups por arquivo para economizar espaço
- **Generic Migrations**: `ConfigUpdater` desacoplado de lógica hardcoded
  - Plugins externos podem registrar suas próprias regras de migração

### Changed
- `config.yml` agora suporta seção `datasources` para configurar múltiplos bancos
- Pool defaults são herdados por todos os datasources (configurável)
- `config-version` atualizado para 2

### Compatibility
- **100% Retrocompatível**: Todos os métodos anteriores continuam funcionando
- Se `datasources` não estiver definido, config raiz é usado como datasource "default"
- Plugins existentes não precisam de mudanças

### Documentation
- `SqlService.md` atualizado com seção completa de Multi-Datasource
- Novos exemplos de configuração e uso
- Tabelas de API para `SqlService` (registry) e `SqlDataSource` (individual)

## [1.3.1] - 2026-01-18

### Added (Inventory Framework)
- **Variants System**: Composição condicional de itens
  - `variant-items` templates reutilizáveis (globais e locais)
  - `variants: ['id']` referência a templates
  - Inline `variantN` com prioridade
  - `view-conditions` dinâmicos por variante
- **Extended Actions**: Lógica de fluxo no YAML
  - Suporte a `conditions` (expression string ou lista)
  - Blocos `success` e `fail` para ramificação de actions
  - Backward compatibility com listas simples de actions

### Changed
- `InventoryConfigManager` atualizado para parsear `variant-items` e blocos de ação complexos
- `GuiItem` agora suporta lista de variantes e resolução dinâmica baseada em contexto
- `InventoryViewHolder` renderiza variantes baseado em avaliação de condições
- `InventoryActionHandler` avalia `conditions` antes de executar actions

## [1.3.0] - 2026-01-15

### Added (Command Framework)
- **Command Framework Enhancements**:
  - `DynamicCommandAlias`: suporte a aliases registrados em runtime
  - `@Cooldown`: rate limiting por player/comando
  - `@Alias`: anotação para múltiplos aliases estáticos
  - `ArgumentTypeRegistry`: suporte a tipos customizados de argumentos
- **Error Handling**:
  - Mensagens de erro de alta prioridade (para comandos críticos)
  - Melhor tratamento de erros em argumentos de player

### Fixed
- **Argument Parsing**: Corrigido bug onde argumentos opcionais vazios eram tratados como obrigatórios
- **Subcommands**: Resolução correta de aliases em subcomandos aninhados


## [1.0.2] - 2026-01-08

### Added
- **Click Type Handlers**: Suporte para diferentes tipos de click em inventários (similar ao inventory-framework do DevNathan)
  - `ClickContext` record com informações completas do click
  - `ClickHandler` functional interface para handlers programáticos com lambdas
  - `ClickHandlers` container para handlers por tipo de click (EnumMap O(1) lookup)
  - Suporte YAML: `on_left_click`, `on_right_click`, `on_shift_left_click`, `on_shift_right_click`, `on_middle_click`, `on_double_click`, `on_drop`, `on_control_drop`, `on_number_key`
  - Fallback para `actions` se tipo específico não definido
  - API programática com métodos fluentes: `.onLeftClick()`, `.onRightClick()`, etc.
  - Navigation helpers em ClickContext: `nextPage()`, `previousPage()`, `switchTab()`, `close()`, `refresh()`
  - Message helper: `sendMessage()` com color codes automáticos
  - Convenience methods: `isLeftClick()`, `isRightClick()`, `isShiftClick()`, etc.

### Changed
- `GuiItem` agora inclui campo `clickHandlers` (ClickHandlers)
- `GuiItem.Builder` com métodos para configurar click handlers (programáticos e YAML-based)
- `InventoryConfigManager` agora parseia campos `on_*_click` do YAML
- `InventoryActionHandler.handleClick()` agora recebe `InventoryViewHolder` como parâmetro
- `InventoryActionHandler` processa handlers programáticos antes de YAML actions
- `InventoryViewHolder.onInventoryClick()` passa `this` para actionHandler

### Performance
- **Zero overhead** se não usar handlers por tipo (fallback direto para `actions`)
- **EnumMap** para O(1) lookup por ClickType (vs switch/case)
- **ClickContext** é record imutável (zero custo de cópia)
- Handlers programáticos evitam parsing de actions (mais rápido que YAML)

### Compatibility
- **100% backwards compatible**: Configurações antigas com apenas `actions:` continuam funcionando
- Graceful fallback: se `on_*_click` não definido, usa `actions` padrão

### Documentation
- Novo exemplo "Click Type Handlers" em `Inventory-Framework-Examples.md` (exemplo 12)
- Tabela completa de tipos de click suportados
- Exemplos de use cases: Shop Buy/Sell, Menu de Navegação, Info vs Action
- Troubleshooting guide para click handlers

## [1.0.1] - 2026-01-08

### Added
- **Dynamic Titles via Packets**: Títulos de inventários podem ser atualizados dinamicamente via ProtocolLib
  - `TitleUpdateSupport` class para gerenciamento de updates via `PacketPlayOutOpenWindow`
  - Graceful degradation: fallback para reabrir inventário se ProtocolLib não disponível
  - NMS reflection para obter window ID (1.8.8 compatible)
- `title_update_interval` em configuração YAML para títulos com placeholders dinâmicos
  - Valor em ticks (20 ticks = 1 segundo)
  - 0 = disabled (padrão)
- `InventoryViewHolder.updateTitle(String)` para atualização programática de títulos
- `InventoryViewHolder.startTitleUpdateTask(int)` para títulos dinâmicos periódicos
- Cache de títulos para evitar updates desnecessários
- Log de disponibilidade do ProtocolLib ao iniciar `DefaultInventoryService`
- Exemplo "Dynamic Titles with Placeholders" em `USAGE_EXAMPLES.md`
- Seção "Dynamic Titles" em `API_REFERENCE.md` com guia de graceful degradation

### Changed
- `InventoryConfig` agora inclui campo `titleUpdateInterval`
- `InventoryViewHolder` construtor agora requer `TitleUpdateSupport` parameter
- `DefaultInventoryService` instancia `TitleUpdateSupport` e passa para view holders

### Performance
- **TPS Impact**: ~0.1ms/tick por inventário com título dinâmico
- Packet send overhead: ~0.05ms
- Placeholder resolution: ~0.05ms (cached)
- Zero overhead se `title_update_interval = 0` (disabled)

### Technical
- ProtocolLib dependency remains optional (soft-depend)
- NMS reflection isolado em `TitleUpdateSupport.getWindowId()`
- Color codes (`&`) convertidos para section symbols (`§`) antes de enviar packet
- Thread safety garantida: todos os métodos validam main thread

## [1.0.0] - 2026-01-08

### Added

#### Inventory Framework (Complete)
- YAML-based inventory configuration system with schema validation
- Intelligent item caching with Caffeine (80-90% hit rate)
- Hybrid pagination system with 3 modes:
  - `NATIVE_ONLY`: Native Bukkit pagination
  - `LAYOUT_ONLY`: Layout-based pagination (legacy compatibility)
  - `HYBRID`: Combines both for optimal performance (35x faster for large inventories)
- Tab system with circular navigation support
- Frame-based animation engine with `ITEM_CYCLE` and `TITLE_CYCLE` types
- Drag-and-drop system with anti-dupe protection
- Shared multi-player inventories with real-time sync
- Database persistence with auto-save (MySQL + SQLite support)
- NBT customization via NBTAPI integration
- Skull textures support (base64, player name, "self")
- 12 built-in action handlers:
  - `message`, `actionbar`, `sound`, `resource_pack_sound`
  - `title`, `teleport`, `potion`
  - `console`, `player_command`
  - `centered_message`, `global_message`, `global_centered_message`

#### Core Infrastructure
- `InventoryService` API for inventory management
- `InventoryContext` builder for placeholders and custom data
- `InventoryState` for runtime state management
- `ItemCache` with Caffeine for compiled items
- `ItemCompiler` for YAML → ItemStack conversion
- `PaginationEngine` with mode detection
- `InventoryAnimator` with frame scheduling
- `DragSessionManager` with expiration tracking
- `SharedInventoryManager` for multi-player sessions
- `InventoryStateManager` for persistence
- `NBTItemBuilder` fluent API for NBT manipulation

#### Diagnostics & Testing
- `MemoryLeakDetector` for inventory framework components
- `/acore memory` command for leak detection
- `InventoryLoadTest` for 500 CCU load testing
- `InventoryBenchmark` for performance benchmarking

#### Documentation
- `MIGRATION_GUIDE.md` - Migration from AfterBlockAnimations
- `USAGE_EXAMPLES.md` - 10 practical examples
- `API_REFERENCE.md` - Complete API documentation
- Comprehensive Javadoc for all public APIs

### Performance

- **TPS**: 20 TPS constant @ 500 CCU
- **TPS Budget**: 27ms/50ms (54% utilization)
- **Memory**: ~70MB footprint
- **Cache Hit Rate**: 80-90%
- **DB Query Time**: <10ms average
- **Inventory Open Latency**: <50ms
- **Item Compilation**: >10,000 ops/s

### Technical Stack

- **Minecraft**: 1.8.8 (Paper/Spigit)
- **Java**: 21 LTS (records, pattern matching, sealed classes)
- **Cache**: Caffeine 3.1.8 (shaded)
- **Database**: HikariCP 5.1.0 (shaded)
- **NBT**: NBTAPI 2.13.2 (provided)
- **PlaceholderAPI**: Optional dependency (graceful degradation)

### Dependencies

- Required: None (standalone library plugin)
- Optional: PlaceholderAPI, ProtocolLib
- Soft-depend: AfterBlockState, AfterMotion

### Configuration

- `inventories.yml` - Inventory definitions
- `config.yml` - Database, concurrency, debug settings
- Config auto-update system preserving user values

---

## [0.2.0] - 2026-01-07

### Added (Inventory Framework Phases 1-6)

- Phase 1: Core Infrastructure
- Phase 2: Cache + Items + NBT
- Phase 3: Pagination + Tabs
- Phase 4: Actions + Drag
- Phase 5: Animations
- Phase 6: Persistence + Shared Views

---

## [0.1.0] - 2025-12-15

### Added (Core Services)

- `SchedulerService` - Thread pools and sync/async execution
- `SqlService` - Database connection pooling and async queries
- `ConditionService` - Unified condition evaluation engine
- `ActionService` - Action parsing and execution with multiple dialects
- `ProtocolService` - ProtocolLib packet manipulation coordination
- `ConfigService` - Configuration management with validation
- `MessageService` - Message handling
- `CommandService` - Command registration framework
- `DiagnosticsService` - Runtime diagnostics and health checks
- `MetricsService` - Lightweight metrics collection

#### Utilities

- `StringUtil` - String manipulation (centering, color codes)
- `RetryExecutor` - Resilient execution with automatic retry
- `RateLimiter` - Token bucket rate limiting
- `CooldownService` - Player-specific cooldowns

#### Commands

- `/acore status` - Dependencies and versions
- `/acore db` - Database info and ping
- `/acore threads` - Thread pool info
- `/acore system` - System info (JVM, OS, memory)
- `/acore metrics` - Performance metrics
- `/acore all` - All diagnostic information

### Performance

- Target: 20 TPS @ 500+ CCU
- Thread pool sizing: 8 I/O threads, 4 CPU threads (configurable)
- Database: Async-only with CompletableFuture
- Error handling: `CoreResult<T>` pattern for predictable failures

---

## Release Notes

### [1.0.0] - Production Ready

This release marks the completion of the **AfterCore Inventory Framework**, a complete replacement for AfterBlockAnimations with superior performance and features.

**Key Highlights:**
- 80-90% cache hit rate reduces item compilation overhead
- Hybrid pagination 35x faster than legacy layout-only mode
- Shared inventories enable multiplayer interactions (trading, shops, etc.)
- Database persistence allows stateful inventories
- Comprehensive testing and diagnostics tools

**Migration:**
Plugins using AfterBlockAnimations can migrate incrementally. See `MIGRATION_GUIDE.md` for step-by-step instructions.

**Compatibility:**
- Minecraft 1.8.8 (Paper/Spigot)
- Java 21 required
- PlaceholderAPI optional (graceful degradation if missing)

---

**Links:**
- GitHub: https://github.com/AfterLands/AfterCore
- Issues: https://github.com/AfterLands/AfterCore/issues
- Wiki: https://github.com/AfterLands/AfterCore/wiki
