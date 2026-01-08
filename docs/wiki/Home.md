## AfterCore Wiki

AfterCore é um **plugin-biblioteca** (dependency plugin) para o ecossistema AfterLands em **Spigot/Paper 1.8.8** com **Java 21**. Ele fornece infraestrutura compartilhada (DB async, actions, conditions, inventories, métricas, diagnósticos, etc.) para reduzir duplicação entre plugins e manter **20 TPS** em alta concorrência.

### Regras de ouro

- **Main thread sagrada**: zero I/O bloqueante (DB, filesystem, rede) na thread principal.
- **Graceful degradation**: dependências opcionais (ProtocolLib, PlaceholderAPI) **não podem crashar** o servidor.
- **API pública não expõe deps “shaded/relocated”**: tipos internos sombreados não devem aparecer nas assinaturas públicas.

### Índice (Wiki)

- **Comece aqui**
  - [Getting Started](Getting-Started.md)
  - [Visão geral da API pública](Public-API-Index.md)
  - [Lista completa de tipos `public`](Public-API-Complete-List.md)
  - [Threading & boas práticas](Threading-and-Performance.md)

- **API principal**
  - [`AfterCore` + `AfterCoreAPI`](AfterCoreAPI.md)

- **Serviços**
  - [`SchedulerService`](SchedulerService.md)
  - [`SqlService`](SqlService.md)
  - [`ConditionService`](ConditionService.md)
  - [`ActionService`](ActionService.md)
  - [`InventoryService` (Inventory Framework)](InventoryService.md)
  - [`ProtocolService`](ProtocolService.md)
  - [`DiagnosticsService`](DiagnosticsService.md)
  - [`MetricsService`](MetricsService.md)
  - [`ConfigService` + `MessageService`](Config-and-Messages.md)
  - [`CommandService`](CommandService.md)

- **Utilitários**
  - [`CoreResult<T>` e erros](CoreResult.md)
  - [Retry/Backoff](Retry.md)
  - [Rate limiting / cooldowns](Rate-Limiting.md)
  - [Strings e utilitários espaciais](Utilities.md)

### Links úteis no repositório

- **Inventário (docs completas)**: `docs/API_REFERENCE.md` e `docs/USAGE_EXAMPLES.md`
- **Migração**: `docs/MIGRATION_GUIDE.md` e `docs/migration/`
- **Config default**: `src/main/resources/config.yml` e `src/main/resources/messages.yml`

