## Inventory Framework Examples (AfterCore 1.5.6)

Exemplos reais e funcionais para plugins que consomem o AfterCore.
Todos os snippets abaixo seguem o comportamento atual do código (v1.5.6).

### Navegação rápida

- [Visão geral do serviço](InventoryService.md)
- [API e schema YAML](Inventory-Framework-API.md)

## 1) Integracao basica (padrao AfterTemplate)

### `onEnable`: registrar inventories do plugin

```java
import com.afterlands.core.api.AfterCore;
import com.afterlands.core.inventory.InventoryService;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class ExamplePlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        saveResource("inventories.yml", false);

        InventoryService inventory = AfterCore.get().inventory();
        File file = new File(getDataFolder(), "inventories.yml");

        int count = inventory.registerInventories(this, file);
        getLogger().info("Inventários registrados: " + count);
    }
}
```

### Abrir menu para o player

```java
import com.afterlands.core.api.AfterCore;
import com.afterlands.core.inventory.InventoryContext;
import org.bukkit.entity.Player;

public void openMain(Player player) {
    InventoryContext ctx = new InventoryContext(player.getUniqueId(), "main-menu")
            .withPlaceholder("player", player.getName());

    AfterCore.get().inventory().openInventory(this, player, "main-menu", ctx);
}
```

## 2) YAML basico com navegacao `open_panel` / `previous_panel`

```yaml
config-version: 1

default-items:
  filler:
    material: STAINED_GLASS_PANE
    data: 15
    name: " "
  back:
    material: ARROW
    name: "&cVoltar"
    actions:
      - "previous_panel"

inventories:
  main-menu:
    title: "&8Main Menu"
    size: 3
    items:
      "0":
        material: item:filler
        duplicate: all

      "11":
        material: BOOK
        name: "&ePerfil"
        actions:
          - "open_panel: profile-menu"

      "15":
        material: REDSTONE
        name: "&cFechar"
        actions:
          - "close"

  profile-menu:
    title: "&8Perfil de {player}"
    size: 3
    items:
      "0":
        material: item:filler
        duplicate: all

      "13":
        material: SKULL_ITEM
        data: 3
        head: self
        name: "&e{player}"

      "22":
        material: item:back
```

## 3) Click types + bloco `conditions/success/fail`

```yaml
inventories:
  shop-menu:
    title: "&8Loja"
    size: 3
    items:
      "13":
        material: DIAMOND
        name: "&bDiamante"
        lore:
          - "&7LMB compra"
          - "&7RMB vende"

        on_left_click:
          conditions:
            - "%vault_eco_balance% >= 100"
          success:
            - "console_command: eco take %player_name% 100"
            - "console_command: give %player_name% diamond 1"
            - "message: &aCompra realizada"
          fail:
            - "message: &cSaldo insuficiente"

        on_right_click:
          - "console_command: clear %player_name% diamond 1"
          - "console_command: eco give %player_name% 80"
          - "message: &eVenda realizada"

        actions:
          - "message: &7Use clique esquerdo/direito"
```

## 4) Paginacao com `contentItems` injetado por contexto

### YAML

```yaml
inventories:
  product-list:
    title: "&8Produtos {page}/{lastpage}"
    size: 6

    pagination:
      mode: HYBRID
      layout:
        - "xxxxxxxxx"
        - "xOOOOOOOx"
        - "xOOOOOOOx"
        - "xOOOOOOOx"
        - "xxxxxxxxx"
        - "xxxN Nxxx"
      show-navigation: true

    items:
      "0":
        material: STAINED_GLASS_PANE
        data: 15
        name: " "
        duplicate: all

      "45":
        type: previous-page
        material: ARROW
        name: "&ePagina anterior"

      "53":
        type: next-page
        material: ARROW
        name: "&aProxima pagina"
```

### Java

```java
import com.afterlands.core.inventory.InventoryContext;
import com.afterlands.core.inventory.item.GuiItem;
import com.afterlands.core.api.AfterCore;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

public void openProducts(Player player, List<String> productNames) {
    List<GuiItem> contentItems = productNames.stream()
            .map(name -> new GuiItem.Builder()
                    .type("product-item")
                    .material(Material.CHEST)
                    .name("&e" + name)
                    .addAction("message: &aSelecionado: " + name)
                    .build())
            .toList();

    // Observação: os GuiItem em contentItems NÃO precisam ter slot.
    // O PaginationEngine distribui automaticamente nos slots 'O' do layout.
    InventoryContext ctx = new InventoryContext(player.getUniqueId(), "product-list")
            .withData("contentItems", contentItems);

    AfterCore.get().inventory().openInventory(this, player, "product-list", ctx);
}
```

## 5) Tabs com slots fixos de icone (`tab-icon-*`)

```yaml
inventories:
  class-menu:
    title: "&8Classes"
    size: 6

    tabs:
      - id: warrior
        display-name: "&cWarrior"
        icon: DIAMOND_SWORD
        default: true
        items:
          "20":
            material: DIAMOND_SWORD
            name: "&eEspada pesada"

      - id: mage
        display-name: "&9Mage"
        icon: BLAZE_ROD
        items:
          "20":
            material: BLAZE_ROD
            name: "&eCajado arcano"

    items:
      "0":
        material: STAINED_GLASS_PANE
        data: 15
        name: " "
        duplicate: all

      "45":
        type: tab-icon-warrior
        material: PAPER
        name: "slot warrior"

      "47":
        type: tab-icon-mage
        material: PAPER
        name: "slot mage"
```

## 6) Inventario compartilhado (`shared: true`)

### YAML

```yaml
inventories:
  trade-room:
    title: "&8Trade Room"
    size: 6
    shared: true
    items:
      "0":
        material: STAINED_GLASS_PANE
        data: 7
        name: " "
        duplicate: all

      "13":
        material: CHEST
        name: "&eBau compartilhado"
        allow-drag: true
```

### Java

```java
import com.afterlands.core.inventory.InventoryContext;
import com.afterlands.core.api.AfterCore;
import org.bukkit.entity.Player;

import java.util.List;

public String openTrade(Player a, Player b) {
    // Para inventário compartilhado, o playerId do contexto pode ser null.
    InventoryContext ctx = new InventoryContext(null, "trade-room")
            .withPlaceholder("p1", a.getName())
            .withPlaceholder("p2", b.getName());

    return AfterCore.get().inventory().openSharedInventory(List.of(a, b), "trade-room", ctx);
}
```

## 7) Type handler por `item.type`

```java
import com.afterlands.core.inventory.click.ClickContext;
import com.afterlands.core.api.AfterCore;

public void registerTypeHandlers() {
    AfterCore.get().inventory().registerTypeHandler("buy-button", this::onBuyClick);
}

private void onBuyClick(ClickContext ctx) {
    ctx.sendMessage("&aCompra tratada por handler programatico");
    ctx.refresh();
}
```

YAML correspondente:

```yaml
items:
  "22":
    type: buy-button
    material: EMERALD
    name: "&aComprar"
```

## 8) Override de slot via `programmaticItems`

```java
import com.afterlands.core.inventory.InventoryContext;
import com.afterlands.core.inventory.item.GuiItem;
import com.afterlands.core.api.AfterCore;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

public void openWithRuntimeBanner(Player player, String status) {
    GuiItem runtimeItem = new GuiItem.Builder()
            .slot(4)
            .type("runtime-status")
            .material(Material.PAPER)
            .name("&eStatus: " + status)
            .build();

    InventoryContext ctx = new InventoryContext(player.getUniqueId(), "main-menu")
            .withData("programmaticItems", List.of(runtimeItem));

    AfterCore.get().inventory().openInventory(this, player, "main-menu", ctx);
}
```

## 9) Invalidacao de cache por jogador (novo em 1.5.6)

Use quando contexto visual de um player muda (idioma/perfil).

```java
import java.util.UUID;
import com.afterlands.core.api.AfterCore;

public void onLanguageChanged(UUID playerId) {
    AfterCore.get().inventory().clearPlayerCache(playerId);
}
```

## 10) Persistencia manual de estado

```java
import com.afterlands.core.inventory.InventoryState;
import com.afterlands.core.api.AfterCore;
import org.bukkit.entity.Player;

public void saveExample(Player player) {
    InventoryState state = InventoryState.initial(player.getUniqueId(), "main-menu")
            .withCustomData("coins", 1200)
            .withTabState("warrior", 2)
            .withStateData("last_open", System.currentTimeMillis());

    AfterCore.get().inventory().saveInventoryState(player.getUniqueId(), "main-menu", state);
}

public void loadExample(Player player) {
    AfterCore.get().inventory().loadInventoryState(player.getUniqueId(), "main-menu")
            .thenAccept(loaded -> {
                Object coins = loaded.getCustomData("coins");
                // use loaded state
            });
}
```

## Troubleshooting rapido

- Use materiais de 1.8.8 (`STAINED_GLASS_PANE`, `SKULL_ITEM`, etc.).
- Para acao de inventario, prefira `open_panel` e `previous_panel` (underscore).
- Para comandos como console, use `console_command:` (não `console:`).
- Se paginacao nao aparece, valide `layout` com largura 9 e marcadores `O`/`N`.
- Se item nao renderiza, valide `view-conditions` e placeholders.
- Se idioma mudou e item nao atualizou, chame `clearPlayerCache(playerId)`.
