# AfterCore Inventory Framework - API Reference

Referência completa da API pública do Inventory Framework.

## Índice

1. [Core Services](#core-services)
2. [Configuration Schema](#configuration-schema)
3. [Built-in Actions](#built-in-actions)
4. [Placeholders](#placeholders)
5. [Error Handling](#error-handling)

---

## Core Services

### InventoryService

Interface principal para gerenciamento de inventários.

```java
public interface InventoryService {
    // Abrir inventário (do plugin AfterCore)
    void openInventory(Player player, String inventoryId, InventoryContext context);
    
    // Abrir inventário de outro plugin
    void openInventory(Player player, Plugin plugin, String inventoryId, InventoryContext context);

    // Abrir inventário compartilhado
    void openSharedInventory(Player player, String inventoryId, String sessionId, InventoryContext context);

    // Obter estado do inventário
    Optional<InventoryState> getState(UUID playerId);

    // Atualizar inventário
    void refreshInventory(Player player);

    // Persistência
    CompletableFuture<Void> saveState(UUID playerId, String inventoryId, InventoryState state);
    CompletableFuture<InventoryState> loadState(UUID playerId, String inventoryId);

    // Shared inventory
    Optional<SharedInventoryContext> getSharedContext(String sessionId);
    void broadcastUpdate(String sessionId, int slot, ItemStack item);
    
    // Carregar inventários de outro plugin
    void loadFromPlugin(Plugin plugin);
}
```

> **Nota**: `openInventory(Player, Plugin, inventoryId, context)` permite abrir inventários definidos
> em outros plugins que usam AfterCore. O plugin deve ter chamado `loadFromPlugin()` previamente.

### InventoryContext

Context builder para abrir inventários com placeholders.

```java
InventoryContext ctx = InventoryContext.builder(player)
    .withPlaceholder("key", "value")
    .withData("custom_key", customObject)
    .build();
```

### InventoryState

Estado mutável de um inventário aberto.

```java
Optional<InventoryState> state = inv.getState(player.getUniqueId());
state.ifPresent(s -> {
    // Modificar item
    s.setItem(10, new ItemStack(Material.DIAMOND));

    // Paginação
    s.setCurrentPage(2);
    s.addPaginatedItem(item);

    // Tabs
    s.setCurrentTab("weapons");

    // Custom data
    s.setData("coins", 1000);
    int coins = (int) s.getData("coins", 0);

    // Marcar como dirty (necessita save)
    s.markDirty();
});
```

---

## Configuration Schema

### Estrutura Básica

```yaml
config-version: 1

# Itens reutilizáveis (herança via item:refName)
default-items:
  filler:
    material: STAINED_GLASS_PANE
    data: 15
    name: " "
    hide-flags: true
  
  back-button:
    material: ARROW
    name: "&cVoltar"
    actions:
      - "previous_panel"

inventories:
  inventory_id:
    title: "&aTitle"
    size: 54

    items:
      "0-8":
        material: "item:filler"  # Referência ao default-item
      "49":
        material: "item:back-button"
        name: "&e← Voltar"  # Override do nome
    
    animations: []
    pagination: {}
    tabs: {}
    drag_settings: {}
    persistence: {}
```

### registerTypeHandler (Java API)

Registrar handlers programáticos para tipos de item:

```java
// Registrar handler para itens com type: "pagination_item"
inventoryService.registerTypeHandler("pagination_item", ctx -> {
    Player player = ctx.player();
    GuiItem item = ctx.item();
    
    // Lógica customizada
    player.sendMessage("Clicou no item: " + item.getName());
});
```

Os type handlers são executados ANTES das actions YAML e bloqueiam a execução das mesmas.

### GuiItem Schema

```yaml
items:
  "13":                       # Slot ou range: "0-8", "10;11;12"
    material: DIAMOND_SWORD   # Material ou referência: "item:filler"
    amount: 1
    data: 0                   # Durabilidade/data (para heads: 3)
    name: "&cNome do Item"
    lore:
      - "&7Linha 1"
      - "&7Linha 2"

    # Enchantments (efeito glow simples)
    enchanted: true           # Adiciona glow (DURABILITY 1)
    
    # Enchantments múltiplos com níveis
    enchantments:
      SHARPNESS: 5
      FIRE_ASPECT: 2
      UNBREAKING: 3

    # Custom Model Data (para resource packs 1.14+)
    custom-model-data: 100001

    # Flags
    hide-flags: true          # Esconde todos os item flags
    
    # Skull (player heads)
    head: "self"              # Player que abriu
    head: "player:Notch"      # Player específico
    head: "base64:eyJ..."     # Textura base64

    # NBT customizado
    nbt:
      afnbt_model: "sword_epic"
      custom_tag: "value"

    # Actions (default para qualquer click)
    actions:
      - "sound:CLICK"
      - "console:give %player% diamond 1"
      
    # Actions por tipo de click
    on_left_click:
      - "message:&aClique esquerdo!"
    on_right_click:
      - "open_panel:outro_menu"
    on_shift_left_click:
      - "command:buy item"
    on_shift_right_click:
      - "close_panel"
    on_middle_click:
      - "message:&7Clique do meio"
    on_drop:
      - "message:&cNão pode dropar!"
    on_number_key:
      - "message:&eTecla numérica"

    # Conditions (v1.3.0+)
    view-conditions:          # Se falhar, item não é renderizado
      - "permission:admin.view"
      - "placeholder:{player_level} >= 10"
    click-conditions:         # Se falhar, click é bloqueado
      - "permission:admin.click"
      - "placeholder:{player_money} >= 100"

    # Dynamic placeholders (lista de placeholders que invalidam cache)
    dynamic-placeholders:
      - "%player_money%"
      - "%player_level%"
      
    # Cache
    cacheable: true           # false para itens dinâmicos
    
    # Drag
    allow-drag: false
    drag-action: "swap"

    # Duplicação
    duplicate: "all"          # Preenche todos slots vazios
    duplicate: "36-44"        # Duplica para slots específicos
```

### Variants & Conditional Items (v1.3.0+)

O sistema de Variants permite definir múltiplas configurações para o mesmo item/slot, selecionadas dinamicamente com base em condições.

#### 1. Inline Variants

Variantes definidas diretamente no item (prioridade alta).

```yaml
items:
  "13":
    material: STONE
    name: "&7Default Item"
    
    # Variante 0 (maior prioridade)
    variant0:
      material: DIAMOND
      view-conditions:
        - "placeholder:%player_level% >= 50"
      
    # Variante 1
    variant1:
      material: GOLD_INGOT
      view-conditions:
        - "placeholder:%player_level% >= 20"
```

#### 2. Variant Items (Templates)

Definidos em uma seção separada `variant-items` e referenciados.

```yaml
variant-items:
  vip_sword:
    material: DIAMOND_SWORD
    name: "&bEspada VIP"
    view-conditions:
      - "permission:vip"

items:
  "13":
    material: WOODEN_SWORD
    variants:
      - "vip_sword"  # Procura em variant-items
```

### Extended Actions (v1.3.0+)

Actions agora suportam lógica condicional com ramos de sucesso e falha.

```yaml
items:
  "13":
    material: CHEST
    on_left_click:
      # Condições para executar a action (Single string ou List)
      conditions: 
        - "%player_money% >= 100"
      
      # Executado se condições forem verdadeiras
      success:
        - "console: eco take %player% 100"
        - "message: &aCompra realizada!"
      
      # Executado se condições forem falsas
      fail:
        - "message: &cDinheiro insuficiente!"
        - "sound: BLOCK_ANVIL_LAND"
    
    # Sintaxe legada ainda funciona
    on_right_click:
      - "message: &7Click normal"
```

### Animation Schema

```yaml
animations:
  - slot: 13
    type: ITEM_CYCLE        # Tipos: ITEM_CYCLE, TITLE_CYCLE
    interval: 10            # ticks (10 = 0.5s)
    delay: 0                # delay inicial
    repeat: true            # loop infinito

    frames:
      - material: DIAMOND
        display_name: "&aFrame 1"
      - material: EMERALD
        display_name: "&bFrame 2"
```

### Pagination Schema

```yaml
pagination:
  enabled: true
  mode: HYBRID              # NATIVE_ONLY, LAYOUT_ONLY, HYBRID
  items_per_page: 28
  item_slots: [10-16, 19-25, 28-34, 37-43]

  controls:
    previous_page:
      slot: 48
      material: ARROW
      display_name: "&e← Anterior"
      click_actions:
        - "sound: CLICK"

    next_page:
      slot: 50
      material: ARROW
      display_name: "&ePróxima →"
      click_actions:
        - "sound: CLICK"
```

### Tabs Schema

```yaml
tabs:
  enabled: true
  circular_navigation: true

  items:
    - id: weapons
      slot: 0
      icon: DIAMOND_SWORD
      display_name: "&cArmas"
      lore:
        - "&7Clique para ver armas"

    - id: armor
      slot: 1
      icon: DIAMOND_CHESTPLATE
      display_name: "&9Armaduras"

# Tab-specific items
tab_contents:
  weapons:
    - slot: 10
      material: DIAMOND_SWORD
      display_name: "&cEspada"

  armor:
    - slot: 10
      material: DIAMOND_CHESTPLATE
      display_name: "&9Peitoral"
```

### Drag Settings Schema

```yaml
drag_settings:
  enabled: true
  allowed_slots: [10-43]    # Slots que aceitam drag
  anti_dupe: true           # Previne duplicação
  disallow_player_inventory_drag: true  # Bloqueia drag do inventário do player
```

### Persistence Schema

```yaml
persistence:
  enabled: true
  save_on_close: true       # Salvar ao fechar
  auto_save_interval: 300   # Auto-save a cada 5min (segundos)
  lazy_load: true           # Carregar apenas quando necessário
```

---

## Built-in Actions

O AfterCore fornece 12 action handlers prontos para uso.

### Formato Geral

```yaml
click_actions:
  - "handler_name: arguments"
```

### message

Envia mensagem no chat.

```yaml
- "message: &aOlá, %player_name%!"
- "message: &7Linha 1\n&7Linha 2"  # Múltiplas linhas com \n
```

### actionbar

Mensagem na action bar.

```yaml
- "actionbar: &e+10 XP"
```

### sound

Toca som.

```yaml
- "sound: LEVEL_UP"
- "sound: CLICK, 1.0, 1.5"  # volume, pitch
```

### resource_pack_sound

Som customizado de resource pack.

```yaml
- "resource_pack_sound: custom.click"
- "resource_pack_sound: custom.success, 0.8, 1.0"
```

### title

Exibe título.

```yaml
- "title: &aWelcome! | &7Subtitle"
- "title: &cTitle | &7Sub | 10 | 40 | 10"  # fadeIn, stay, fadeOut (ticks)
```

### teleport

Teleporta player.

```yaml
- "teleport: 100 64 200"          # x y z
- "teleport: ~5 ~0 ~-3"           # relativo
- "teleport: world_nether 0 64 0 90 0"  # world x y z yaw pitch
```

### potion

Aplica efeito de poção.

```yaml
- "potion: SPEED 60 1"        # type duration(s) amplifier
- "potion: REGENERATION 30 0"
```

### console

Executa comando como console.

```yaml
- "console: give %player% diamond 1"
- "console: tp %player% spawn"
```

### player_command

Executa comando como player (sem /).

```yaml
- "player_command: shop"
- "player_command: warp spawn"
```

### centered_message

Mensagem centralizada no chat.

```yaml
- "centered_message: &6&lWelcome to the Server!"
```

### global_message

Broadcast para todos os players.

```yaml
- "global_message: &a%player% abriu um loot raro!"
```

### global_centered_message

Broadcast centralizado.

```yaml
- "global_centered_message: &c&lEvento iniciado!"
```

---

## Navigation Actions (Inventory-Specific)

Actions específicas para navegação entre painéis e paginação.

### switch_tab

Muda para outra tab.

```yaml
- "switch_tab:weapons"
- "switch_tab:armor"
```

### next_page / prev_page

Navega entre páginas (paginação).

```yaml
- "next_page"
- "prev_page"
```

### open_panel

Abre outro painel (preserva histórico de navegação).

```yaml
- "open_panel:shop"
- "open:confirm_purchase"
```

### previous_panel / back

Volta para o painel anterior (usa histórico de navegação).

```yaml
- "previous_panel"
- "back"
```

### refresh

Atualiza o inventário atual.

```yaml
- "refresh"
```

### close

Fecha inventário.

```yaml
- "close"
```

---

## Placeholders

### Built-in Placeholders

Placeholders fornecidos automaticamente:

- `%player_name%` - Nome do player
- `%player_uuid%` - UUID do player
- `%current_page%` - Página atual (paginação)
- `%total_pages%` - Total de páginas
- `%tab_name%` - Nome da tab atual

### Custom Placeholders

Adicionar via Context:

```java
InventoryContext ctx = InventoryContext.builder(player)
    .withPlaceholder("player_level", String.valueOf(player.getLevel()))
    .withPlaceholder("player_money", String.format("%.2f", economy.getBalance(player)))
    .build();
```

### PlaceholderAPI Integration

Se PlaceholderAPI estiver instalado, todos os placeholders são suportados:

```yaml
display_name: "&a%player_name%"
lore:
  - "&7Level: &e%level%"
  - "&7Health: &c%health%/20"
```

**Nota**: PlaceholderAPI roda na main thread. Evite usar em ações async.

---

## Error Handling

### CoreResult Pattern

O framework usa `CoreResult<T>` para error handling previsível:

```java
import com.afterlands.core.result.CoreResult;
import com.afterlands.core.result.CoreErrorCode;

// Criar resultados
CoreResult<InventoryState> result = CoreResult.ok(state);
CoreResult<InventoryState> error = CoreResult.err(CoreErrorCode.NOT_FOUND, "Inventory not found");

// Pattern matching (Java 21)
return switch (result) {
    case CoreResult.Ok(var state) -> processState(state);
    case CoreResult.Err(var error) -> handleError(error);
};

// Functional operations
InventoryState state = result
    .map(s -> modifyState(s))
    .recover(err -> DEFAULT_STATE)
    .orElse(FALLBACK_STATE);

// Side effects
result.ifOk(state -> cache.put(playerId, state));
result.ifErr(error -> logger.warning(error.message()));
```

### Error Codes

```java
public enum CoreErrorCode {
    DEPENDENCY_MISSING,     // ProtocolLib, PlaceholderAPI, etc.
    DB_DISABLED,            // Database desabilitado
    DB_UNAVAILABLE,         // Database inacessível
    TIMEOUT,                // Operação expirou
    INVALID_CONFIG,         // Configuração inválida
    NOT_ON_MAIN_THREAD,     // Deve rodar na main thread
    ON_MAIN_THREAD,         // Não pode rodar na main thread
    NOT_FOUND,              // Recurso não encontrado
    FORBIDDEN,              // Operação não permitida
    INVALID_ARGUMENT,       // Argumento inválido
    INTERNAL_ERROR,         // Erro interno
    UNKNOWN                 // Erro desconhecido
}
```

---

## Performance Guidelines

### Cache Optimization

- Items são cached automaticamente (Caffeine)
- Cache hit rate: 80-90% típico
- Evite placeholders únicos desnecessários

### Thread Safety

- `InventoryService` métodos são thread-safe
- PlaceholderAPI requer main thread
- Database operations são sempre async

### TPS Budget

- Target: 27ms/50ms (54%) @ 500 CCU
- Animations: <1ms overhead por inventário
- Pagination HYBRID: 35x mais rápido que LAYOUT_ONLY

---

## Extensibility

### Custom Action Handlers

```java
ActionService actions = AfterCore.get().actions();

actions.registerHandler("my_action", (player, args) -> {
    // Handler logic
    player.sendMessage("Custom action: " + args);
});
```

### Custom Item Compilers

```java
// Futuro: API para custom item compilation
```
