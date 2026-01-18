## `InventoryService` (Inventory Framework)

### O que é

O Inventory Framework do AfterCore é um sistema completo de GUI (1.8.8) baseado em YAML + estado em runtime, com foco em performance:

- cache inteligente de itens
- paginação (NATIVE_ONLY, LAYOUT_ONLY, HYBRID)
- tabs
- animações por frames
- drag-and-drop com anti-dupe
- inventários compartilhados (sessões)
- persistência em DB (save/load)
- variants system (itens condicionais)
- extended actions (conditions/success/fail)
- integração opcional com PlaceholderAPI e ProtocolLib

### API principal (alto nível)

- `openInventory(Player, plugin, inventoryId, InventoryContext)`
- `openSharedInventory(Player, inventoryId, sessionId, InventoryContext)`
- `Optional<InventoryState> getState(UUID playerId)`
- `refreshInventory(Player)`
- `saveState(UUID, inventoryId, InventoryState)` / `loadState(UUID, inventoryId)`
- `Optional<SharedInventoryContext> getSharedContext(sessionId)`
- `broadcastUpdate(sessionId, slot, item)`

### Exemplo mínimo

```java
var inv = core.inventory();
var ctx = InventoryContext.builder(player).build();
inv.openInventory(player, "main_menu", ctx);
```

### Documentação completa (schema + exemplos)

O Inventory Framework já possui documentação detalhada no repositório:

- **Referência completa da API e schema YAML**: [Inventory Framework API](Inventory-Framework-API)
- **11 exemplos práticos (prontos para copiar/colar)**: [Inventory Framework Examples](Inventory-Framework-Examples)
- **Guia de migração (ABA → AfterCore)**: `docs/MIGRATION_GUIDE.md`

### Integrações opcionais

- **PlaceholderAPI**: placeholders em titles/lores/conditions (main thread).
- **ProtocolLib**: atualizações de título e features de packet/pipeline (graceful degradation se ausente).

