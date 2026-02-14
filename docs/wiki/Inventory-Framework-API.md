## Inventory Framework API (AfterCore 1.5.6)

Referência técnica do framework de inventários do AfterCore (v1.5.6), baseada no código atual em `src/main/java/com/afterlands/core/inventory`.

### Navegação rápida

- [Visão geral do serviço](InventoryService.md)
- [Exemplos funcionais (Java + YAML)](Inventory-Framework-Examples.md)

## 1) Contratos públicos

### `InventoryService`

Interface principal do framework.

```java
public interface InventoryService {
    void openInventory(Player player, String inventoryId, InventoryContext context);
    void openInventory(Plugin plugin, Player player, String inventoryId, InventoryContext context);
    String openSharedInventory(List<Player> players, String inventoryId, InventoryContext context);
    void closeInventory(Player player);

    CompletableFuture<Void> saveInventoryState(UUID playerId, String inventoryId, InventoryState state);
    CompletableFuture<InventoryState> loadInventoryState(UUID playerId, String inventoryId);

    CompletableFuture<Void> reloadConfigurations();

    void clearCache();
    void clearCache(String inventoryId);
    void invalidateItemCache(String inventoryId);
    void clearPlayerCache(UUID playerId);

    ItemTemplateService templates();

    boolean isInventoryRegistered(String inventoryId);
    void registerInventory(InventoryConfig config);
    int registerInventories(File file);
    int registerInventories(Plugin plugin, File file);

    void registerTypeHandler(String itemType, ClickHandler handler);
}
```

Thread model:

- `open*` e `closeInventory`: main thread
- `save/load/reload`: async
- cache/register/check: thread-safe

### `InventoryContext`

```java
InventoryContext ctx = new InventoryContext(player.getUniqueId(), "main-menu")
        .withPlaceholder("player", player.getName())
        .withData("key", value)
        .withPluginNamespace("aftertemplate");
```

- Placeholders de contexto: `{key}`
- PlaceholderAPI: `%placeholder%` (se instalado)
- i18n helper: `{lang:namespace:key}` no `PlaceholderResolver`

### `InventoryState`

Record imutável persistido no DB:

- `stateData` (`Map<String, Object>`)
- `tabStates` (`Map<String, Integer>`)
- `customData` (`Map<String, Object>`)
- `updatedAt`
- `schemaVersion`

### `GuiItem`

Modelo de item de GUI com builder fluente.
Inclui material/meta/NBT/click handlers/conditions/variants/drag/animacoes.

### `ClickContext`

Contexto de click entregue para handlers programáticos:

- `player`, `holder`, `item`, `inventoryContext`, `event`, `clickType`, `slot`
- helpers: `nextPage()`, `previousPage()`, `switchTab(tabId)`, `close()`, `refresh()`, `sendMessage(message)`

### `ItemTemplateService`

```java
GuiItem.Builder builder = core.inventory().templates().loadTemplate("my-menu", "frame-template");
```

Observação importante:

- implementação atual (`DefaultItemTemplateService`) clona principalmente `type`, `material`, `name` e `lore`.
- se você precisa copiar outras propriedades, ajuste no builder após o `loadTemplate(...)`.
- prefira `loadTemplate(inventoryId, itemId)` (sem resolver placeholders na hora). O overload deprecated que resolve placeholders antecipadamente pode causar itens “congelados” no cache.

## 2) Ordem de execução no click

Pipeline real em `InventoryActionHandler.handleClick(...)`:

1. `click-conditions` do item
2. handler por `type` registrado via `registerTypeHandler`
3. handler programatico do `ClickHandlers` (lambdas)
4. actions YAML do tipo de click (`on_left_click`, etc.)
5. fallback para `actions`

`ConfiguredAction` suporta:

- `conditions` (lista)
- `success` (lista)
- `fail` (lista)

## 2.1) Actions dentro de inventários (o que roda aqui)

Dentro de inventários, você pode executar dois “tipos” de action:

### A) Actions do próprio framework de inventário (navegação/controle)

Essas actions são tratadas pelo `InventoryActionHandler` e dependem do `InventoryViewHolder` atual:

- `open_panel: <inventoryId>` (alias: `open: <inventoryId>`)
- `previous_panel` (alias: `back`)
- `switch_tab: <tabId>`
- `next_page`
- `prev_page`
- `refresh`
- `close`

**Importante:** o parser de actions do inventário aceita apenas nomes no formato `^[a-z_]+` (ou seja, use **underscore**, sem hífen).

### B) Actions padrão do AfterCore (`ActionService`)

Qualquer action suportada pelo `ActionService` pode ser usada em `actions` / `on_*_click`. Exemplos comuns:

```yaml
actions:
  - "message: &aOlá!"
  - "sound: CLICK"
  - "player_command: menu"
  - "console_command: give %player_name% diamond 1"
```

Observação: para comandos como console, o nome correto é **`console_command:`** (não `console:`).

## 3) Schema YAML

## 3.1 Estrutura de arquivo

### Arquivo principal do AfterCore (`plugins/AfterCore/inventories.yml`)

```yaml
config-version: 1

default-items:
  filler:
    material: STAINED_GLASS_PANE
    data: 15
    name: " "

inventories:
  main-menu:
    title: "&8Menu"
    size: 3
    items: {}
```

### Arquivo externo via `registerInventories(file)`

`loadConfigs(file)` aceita dois formatos:

1. com raiz `inventories:`
2. sem raiz (inventarios no root, estilo AfterTemplate)

## 3.2 Campos do inventario

| Campo | Tipo | Default | Notas |
|---|---|---|---|
| `title` | string | `""` | Suporta placeholders |
| `size` | int | `3` | 1..6 (linhas). Se vier >6, parser tenta converter de slots para linhas (`size/9`) |
| `items` | section | vazio | Mapa `slotKey -> item` |
| `tabs` | list | vazio | Lista de tabs |
| `pagination` | section | null | Config de paginacao |
| `animations` | section | vazio | Campo existe, mas loop ativo de render usa animacoes por item |
| `persistence` | section | disabled | Persistencia de estado |
| `shared` | bool | false | Obrigatorio `true` para `openSharedInventory` |
| `title_update_interval` | int | 0 | Update periodico de titulo |
| `variant-items` | section | vazio | Templates de variantes |

## 3.3 Slot keys

Parser de slots aceita:

- unico: `"13"`
- range: `"0-8"`
- lista: `"0;4;8"`
- combinado: `"0-8;18-26"`

## 3.4 Item schema (`items.<slotKey>`)

| Campo | Tipo | Notas |
|---|---|---|
| `type` | string | default = chave do item |
| `material` | string | Ex: `DIAMOND`, `SKULL_ITEM`, `item:filler`, `head:self` |
| `data` | int | durabilidade/data (1.8.8) |
| `amount` | int | clamped para 1..64 |
| `name` | string | suporta placeholders |
| `lore` | list<string> | suporta placeholders |
| `enabled` | bool | parseado no item |
| `enchanted` | bool | glow simples |
| `enchantments` | map<string,int> | `Enchantment.getByName(...)` |
| `hide-flags` | bool | adiciona `ItemFlag.values()` |
| `custom-model-data` | int | escreve NBT `CustomModelData` |
| `actions` | list<string> | fallback default |
| `action` | string ou list | alias adicional concatenado |
| `on_left_click` ... | list/string/obj | actions por click |
| `head` | string | `self`, `player:Name`, `base64:...`, `ey...` |
| `nbt` | map<string,string> | tags custom |
| `duplicate` | `all` ou slot string | `all` usa marcador interno `-1` |
| `allow-drag` | bool | libera drag no slot |
| `drag-action` | string | action executada no drag valido |
| `cacheable` | bool | controle explicito de cache |
| `dynamic-placeholders` | list<string> | forca item dinamico (sem cache) |
| `view-conditions` | list<string> | filtro de render |
| `click-conditions` | list<string> | filtro de click |
| `variants` | list<string> | refs para `variant-items` |
| `variant0`, `variant1`... | section | variantes inline por prioridade |
| `animations` | list<obj> | animacoes por item |

### Nota sobre item templates em `items`

`parseItems(...)` so considera como template interno (slot `-1`) chaves nao numericas que contenham `-`.

Exemplo seguro:

```yaml
items:
  frame-template:
    type: frame-template
    material: PAPER
    name: "Frame {index}"
```

## 3.5 Click keys suportadas

- `on_left_click`
- `on_right_click`
- `on_shift_left_click`
- `on_shift_right_click`
- `on_middle_click`
- `on_double_click`
- `on_drop`
- `on_control_drop`
- `on_number_key`

### Formatos aceitos em cada `on_*`

Lista simples:

```yaml
on_left_click:
  - "message: &aOK"
  - "sound: CLICK"
```

Bloco condicional:

```yaml
on_left_click:
  conditions:
    - "%player_level% >= 10"
  success:
    - "message: &aLiberado"
  fail:
    - "message: &cSem nivel"
```

## 3.6 Pagination

```yaml
pagination:
  mode: HYBRID            # NATIVE_ONLY | LAYOUT_ONLY | HYBRID
  layout:
    - "xxxxxxxxx"
    - "xOOOOOOOx"
    - "xOOOOOOOx"
    - "xxxN Nxxx"
  pagination-slots: [10,11,12,13,14,15,16,19,20,21,22,23,24,25]
  navigation-slots:
    prev: 30
    next: 32
  items-per-page: 14
  show-navigation: true
```

Layout markers:

- `O` = slot de conteudo
- `N` = slot de navegacao

Observacoes importantes da implementacao atual:

- `createPage(...)` usa quantidade de `contentSlots` para pagina, nao o `items-per-page` diretamente.
- botoes de navegacao podem vir de:
- item `type: prev-page`/`previous-page`
- item `type: next-page`
- ou botoes default internos

## 3.7 Tabs

```yaml
tabs:
  - id: weapons
    display-name: "&cArmas"
    icon: DIAMOND_SWORD
    default: true
    items:
      "20":
        material: DIAMOND_SWORD
        name: "&eEspada"

  - id: armor
    display-name: "&9Armadura"
    icon: DIAMOND_CHESTPLATE
    items:
      "20":
        material: DIAMOND_CHESTPLATE
        name: "&ePeitoral"
```

Slots de icones de tab:

- se existirem itens com `type: tab-icon-<tabId>`, esses slots sao usados para icones
- senao, framework centraliza automaticamente os icones na ultima linha

## 3.8 Variants

```yaml
variant-items:
  vip-item:
    material: GOLD_INGOT
    name: "&6VIP"
    view-conditions:
      - "%player_is_op% == true"

items:
  "13":
    material: BARRIER
    name: "&cBloqueado"
    variants:
      - "vip-item"

  "15":
    material: STONE
    name: "&7Base"
    variant0:
      material: EMERALD
      name: "&aAtivo"
      view-conditions:
        - "%template_level% >= 5"
```

Prioridade em runtime:

1. `variant0`, `variant1`, ... (inline)
2. `variants` (refs)
3. item base

## 3.9 Persistence

```yaml
persistence:
  enabled: true
  auto-save: true
  save-interval-seconds: 30
```

## 4) Acoes de navegacao internas

Acoes custom do inventory handler:

- `switch_tab: <tabId>`
- `next_page`
- `prev_page`
- `open_panel: <inventoryId>` (ou `open: <inventoryId>`)
- `previous_panel` (alias `back`)
- `refresh`
- `close`

Use underscore nos nomes. O parser interno de action aceita `^[a-z_]+` (nomes com hífen não são aceitos).

## 5) Placeholders disponiveis no contexto de pagina

Durante render paginado, framework injeta:

- `{page}`
- `{nextpage}`
- `{total_pages}`
- `{lastpage}`
- `{has_next_page}`
- `{has_previous_page}`

## 6) Cache e invalidacao

Camadas relevantes:

- `InventoryConfigManager`: cache de config
- `ItemCache`: cache de `ItemStack` compilado
- `PlaceholderResolver`: cache curto de placeholders resolvidos

Invalidacao por API:

- `clearCache()`: global
- `clearCache(inventoryId)`: inventario especifico
- `invalidateItemCache(inventoryId)`: so itens
- `clearPlayerCache(playerId)`: invalida so escopo do jogador (1.5.6)

## 7) Gotchas reais (importantes)

- Projeto e 1.8.8: use materiais 1.8 (`STAINED_GLASS_PANE`, `SKULL_ITEM`, etc.).
- `openSharedInventory` falha se o inventario nao tiver `shared: true`.
- `on_*_click` deve usar underscore.
- Para placeholders contextuais em listas dinamicas, prefira `GuiItem.Builder.withPlaceholder(...)` por item.
- Campo `animations` global existe no config, mas o loop ativo de render inicia animacoes por item (`item.animations`).
