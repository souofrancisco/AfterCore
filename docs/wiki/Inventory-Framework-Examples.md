# AfterCore Inventory Framework - Exemplos de Uso

Este documento contém 12 exemplos práticos e prontos para uso do Inventory Framework.

## Índice

1. [Quick Start - Menu Básico](#1-quick-start---menu-básico)
2. [Pagination - Shop com 100+ Itens](#2-pagination---shop-com-100-itens)
3. [Tabs - Wardrobe Multi-Categoria](#3-tabs---wardrobe-multi-categoria)
4. [Animations - Loading Screens](#4-animations---loading-screens)
5. [Drag-and-Drop - Custom Crafting](#5-drag-and-drop---custom-crafting)
6. [Shared Inventories - Multiplayer Trading](#6-shared-inventories---multiplayer-trading)
7. [Persistence - Save/Load State](#7-persistence---saveload-state)
8. [Custom Actions - Extending Action Handlers](#8-custom-actions---extending-action-handlers)
9. [Performance Tips - Otimizações](#9-performance-tips---otimizações)
10. [Troubleshooting - Problemas Comuns](#10-troubleshooting---problemas-comuns)
11. [Dynamic Titles with Placeholders](#11-dynamic-titles-with-placeholders)
12. [Click Type Handlers - Diferentes Tipos de Click](#12-click-type-handlers---diferentes-tipos-de-click)
13. [Variants & Extended Actions - Itens Condicionais](#13-variants--extended-actions---itens-condicionais)

---

## 1. Quick Start - Menu Básico

### Objetivo
Criar um menu principal simples com navegação para outras seções.

### YAML (plugins/AfterCore/inventories.yml)
```yaml
config-version: 1

inventories:
  main_menu:
    title: "&6&lMenu Principal"
    size: 27

    items:
      # Background
      - slot: [0-8, 9, 17, 18-26]
        material: BLACK_STAINED_GLASS_PANE
        display_name: " "

      # Loja
      - slot: 11
        material: EMERALD
        display_name: "&a&lLoja"
        lore:
          - "&7Compre itens e recursos"
          - ""
          - "&eClique para abrir"
        click_actions:
          - "player_command: shop"
          - "sound: CLICK"

      # Perfil
      - slot: 13
        material: PLAYER_HEAD
        skull_owner: "self"
        display_name: "&b&lSeu Perfil"
        lore:
          - "&7Nome: &f%player_name%"
          - "&7Level: &f%player_level%"
          - ""
          - "&eClique para ver detalhes"
        click_actions:
          - "player_command: profile"

      # Configurações
      - slot: 15
        material: COMPARATOR
        display_name: "&e&lConfigurações"
        lore:
          - "&7Ajuste suas preferências"
          - ""
          - "&eClique para abrir"
        click_actions:
          - "player_command: settings"

      # Sair
      - slot: 26
        material: BARRIER
        display_name: "&c&lFechar Menu"
        click_actions:
          - "close"
```

### Java
```java
import com.afterlands.core.AfterCore;
import com.afterlands.core.inventory.InventoryContext;
import com.afterlands.core.inventory.InventoryService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MenuCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cApenas jogadores podem usar este comando.");
            return true;
        }

        InventoryService inv = AfterCore.get().inventory();
        InventoryContext ctx = InventoryContext.builder(player)
            .withPlaceholder("player_level", String.valueOf(player.getLevel()))
            .build();

        inv.openInventory(player, "main_menu", ctx);
        return true;
    }
}
```

### Resultado
- Menu 3x9 com fundo decorativo
- 3 botões principais (Loja, Perfil, Configurações)
- Placeholders dinâmicos (%player_name%, %player_level%)
- Som de clique
- Skull do próprio player

---

## 2. Pagination - Shop com 100+ Itens

### Objetivo
Loja com centenas de itens usando paginação híbrida para melhor performance.

### YAML
```yaml
inventories:
  shop:
    title: "&6&lLoja &8| &ePágina %current_page%/%total_pages%"
    size: 54

    pagination:
      enabled: true
      mode: HYBRID  # Melhor performance para muitos itens
      items_per_page: 28
      item_slots: [10-16, 19-25, 28-34, 37-43]  # 7x4 = 28 slots

      controls:
        previous_page:
          slot: 48
          material: ARROW
          display_name: "&e← Página Anterior"
          lore:
            - "&7Clique para voltar"
          click_actions:
            - "sound: CLICK"

        next_page:
          slot: 50
          material: ARROW
          display_name: "&ePróxima Página →"
          lore:
            - "&7Clique para avançar"
          click_actions:
            - "sound: CLICK"

    items:
      # Background
      - slot: [0-9, 17-18, 26-27, 35-36, 44-53]
        material: GRAY_STAINED_GLASS_PANE
        display_name: " "

      # Info
      - slot: 49
        material: BOOK
        display_name: "&6&lInformações"
        lore:
          - "&7Total de itens: &e%total_items%"
          - "&7Seu dinheiro: &a$%player_money%"
```

### Java
```java
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ShopCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }

        InventoryService inv = AfterCore.get().inventory();

        // Carregar itens da shop (de database, config, etc.)
        List<ItemStack> shopItems = loadShopItems();

        InventoryContext ctx = InventoryContext.builder(player)
            .withPlaceholder("player_money", String.valueOf(getPlayerMoney(player)))
            .withPlaceholder("total_items", String.valueOf(shopItems.size()))
            .build();

        inv.openInventory(player, "shop", ctx);

        // Adicionar itens paginados
        inv.getState(player.getUniqueId()).ifPresent(state -> {
            for (ItemStack item : shopItems) {
                state.addPaginatedItem(item);
            }
            inv.refreshInventory(player);
        });

        return true;
    }

    private List<ItemStack> loadShopItems() {
        List<ItemStack> items = new ArrayList<>();

        // Exemplo: 100 itens diferentes
        for (int i = 0; i < 100; i++) {
            ItemStack item = new ItemStack(Material.DIAMOND, i + 1);
            // Adicionar meta, lore, etc.
            items.add(item);
        }

        return items;
    }

    private double getPlayerMoney(Player player) {
        // Integração com economy plugin
        return 1000.0;
    }
}
```

### Resultado
- 100 itens distribuídos em 4 páginas (28 por página)
- Navegação com setas
- Placeholders dinâmicos para dinheiro e total de itens
- Performance otimizada com modo HYBRID

---

## 3. Tabs - Wardrobe Multi-Categoria

### Objetivo
Guarda-roupa com múltiplas categorias navegáveis via tabs.

### YAML
```yaml
inventories:
  wardrobe:
    title: "&d&lGuarda-Roupa &8| &f%tab_name%"
    size: 54

    tabs:
      enabled: true
      items:
        - id: helmets
          slot: 0
          icon: DIAMOND_HELMET
          display_name: "&b&lCapacetes"
          lore:
            - "&7Clique para ver capacetes"

        - id: chestplates
          slot: 1
          icon: DIAMOND_CHESTPLATE
          display_name: "&b&lPeitorais"
          lore:
            - "&7Clique para ver peitorais"

        - id: leggings
          slot: 2
          icon: DIAMOND_LEGGINGS
          display_name: "&b&lCalças"
          lore:
            - "&7Clique para ver calças"

        - id: boots
          slot: 3
          icon: DIAMOND_BOOTS
          display_name: "&b&lBotas"
          lore:
            - "&7Clique para ver botas"

    items:
      # Background
      - slot: [4-8, 9, 17, 18, 26, 27, 35, 36-53]
        material: BLACK_STAINED_GLASS_PANE
        display_name: " "

    # Tab-specific items
    tab_contents:
      helmets:
        - slot: 10
          material: LEATHER_HELMET
          display_name: "&eCapacete de Couro"
          click_actions:
            - "player_command: wardrobe equip helmet_leather"

        - slot: 11
          material: CHAINMAIL_HELMET
          display_name: "&eCapacete de Malha"
          click_actions:
            - "player_command: wardrobe equip helmet_chain"

      chestplates:
        - slot: 10
          material: LEATHER_CHESTPLATE
          display_name: "&ePeitoral de Couro"

      # ... outros tabs
```

### Java
```java
public class WardrobeCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }

        InventoryService inv = AfterCore.get().inventory();
        InventoryContext ctx = InventoryContext.builder(player).build();

        inv.openInventory(player, "wardrobe", ctx);

        // Opcional: Carregar tab específica
        if (args.length > 0) {
            String tabId = args[0];
            inv.getState(player.getUniqueId()).ifPresent(state -> {
                state.setCurrentTab(tabId);
                inv.refreshInventory(player);
            });
        }

        return true;
    }
}
```

### Resultado
- 4 tabs navegáveis (Capacetes, Peitorais, Calças, Botas)
- Itens específicos por tab
- Título dinâmico mostrando tab atual

---

## 4. Animations - Loading Screens

### Objetivo
Tela de carregamento com animação de progresso.

### YAML
```yaml
inventories:
  loading:
    title: "&e&lCarregando..."
    size: 27

    items:
      # Background estático
      - slot: [0-8, 18-26]
        material: BLACK_STAINED_GLASS_PANE
        display_name: " "

      # Barra de progresso (será animada)
      - slot: [10-16]
        material: GRAY_STAINED_GLASS_PANE
        display_name: "&7Aguarde..."

    animations:
      # Barra de progresso preenchendo
      - slot: 10
        type: ITEM_CYCLE
        interval: 5
        repeat: false
        frames:
          - material: GRAY_STAINED_GLASS_PANE
            display_name: "&7Carregando."
          - material: LIME_STAINED_GLASS_PANE
            display_name: "&aCarregando."

      - slot: 11
        type: ITEM_CYCLE
        interval: 5
        delay: 5
        repeat: false
        frames:
          - material: GRAY_STAINED_GLASS_PANE
            display_name: "&7Carregando.."
          - material: LIME_STAINED_GLASS_PANE
            display_name: "&aCarregando.."

      # ... slots 12-16 com delays incrementais

      # Spinner central
      - slot: 13
        type: ITEM_CYCLE
        interval: 3
        repeat: true
        frames:
          - material: CLOCK
            display_name: "&e⬆ Processando..."
          - material: CLOCK
            display_name: "&e↗ Processando..."
          - material: CLOCK
            display_name: "&e➡ Processando..."
          - material: CLOCK
            display_name: "&e↘ Processando..."
          - material: CLOCK
            display_name: "&e⬇ Processando..."
          - material: CLOCK
            display_name: "&e↙ Processando..."
          - material: CLOCK
            display_name: "&e⬅ Processando..."
          - material: CLOCK
            display_name: "&e↖ Processando..."
```

### Java
```java
import java.util.concurrent.CompletableFuture;

public class LoadingExample {

    public void showLoadingScreen(Player player, CompletableFuture<Void> task) {
        InventoryService inv = AfterCore.get().inventory();
        InventoryContext ctx = InventoryContext.builder(player).build();

        inv.openInventory(player, "loading", ctx);

        // Quando task completar, fechar loading screen
        task.thenRun(() -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.closeInventory();
                player.sendMessage("§aConcluído!");
            });
        });
    }

    // Exemplo de uso
    public void loadPlayerData(Player player) {
        CompletableFuture<Void> loadTask = CompletableFuture.runAsync(() -> {
            try {
                // Operação demorada (ex: carregar do database)
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        showLoadingScreen(player, loadTask);
    }
}
```

### Resultado
- Barra de progresso animada preenchendo da esquerda para direita
- Spinner central rotativo
- Auto-fecha quando task completa

---

## 5. Drag-and-Drop - Custom Crafting

### Objetivo
Sistema de crafting customizado com drag-and-drop de ingredientes.

### YAML
```yaml
inventories:
  custom_crafting:
    title: "&6&lCrafting Customizado"
    size: 54

    drag_settings:
      enabled: true
      allowed_slots: [10-12, 19-21, 28-30]  # Grid 3x3
      anti_dupe: true
      disallow_player_inventory_drag: true

    items:
      # Background
      - slot: [0-9, 13-18, 22-27, 31-53]
        material: GRAY_STAINED_GLASS_PANE
        display_name: " "

      # Grid de crafting
      - slot: [10-12, 19-21, 28-30]
        material: AIR
        # Slots vazios para drag

      # Seta indicadora
      - slot: 23
        material: ARROW
        display_name: "&eResultado →"

      # Slot de resultado (read-only)
      - slot: 24
        material: AIR
        # Preenchido dinamicamente

      # Botão de craft
      - slot: 33
        material: CRAFTING_TABLE
        display_name: "&a&lCraftar"
        lore:
          - "&7Clique para craftar o item"
        click_actions:
          - "custom: craft_item"

      # Botão de limpar
      - slot: 32
        material: BARRIER
        display_name: "&c&lLimpar Grid"
        click_actions:
          - "custom: clear_grid"
```

### Java
```java
import com.afterlands.core.actions.ActionService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public class CustomCraftingPlugin extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        registerActions();
        getServer().getPluginManager().registerEvents(this, this);
    }

    private void registerActions() {
        ActionService actions = AfterCore.get().actions();

        actions.registerHandler("craft_item", (player, args) -> {
            InventoryService inv = AfterCore.get().inventory();
            inv.getState(player.getUniqueId()).ifPresent(state -> {
                // Coletar itens do grid
                List<ItemStack> ingredients = new ArrayList<>();
                for (int slot : new int[]{10, 11, 12, 19, 20, 21, 28, 29, 30}) {
                    ItemStack item = state.getItem(slot);
                    if (item != null && item.getType() != Material.AIR) {
                        ingredients.add(item);
                    }
                }

                // Verificar receita
                ItemStack result = checkRecipe(ingredients);
                if (result != null) {
                    // Dar resultado ao player
                    player.getInventory().addItem(result);

                    // Limpar grid
                    clearGrid(state);
                    inv.refreshInventory(player);

                    player.sendMessage("§aItem craftado com sucesso!");
                } else {
                    player.sendMessage("§cReceita inválida!");
                }
            });
        });

        actions.registerHandler("clear_grid", (player, args) -> {
            InventoryService inv = AfterCore.get().inventory();
            inv.getState(player.getUniqueId()).ifPresent(state -> {
                clearGrid(state);
                inv.refreshInventory(player);
            });
        });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        InventoryService inv = AfterCore.get().inventory();
        inv.getState(player.getUniqueId()).ifPresent(state -> {
            // Atualizar preview de resultado após drag
            Bukkit.getScheduler().runTaskLater(this, () -> {
                updateResultPreview(state, inv, player);
            }, 1L);
        });
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        InventoryService inv = AfterCore.get().inventory();
        inv.getState(player.getUniqueId()).ifPresent(state -> {
            // Atualizar preview após click
            Bukkit.getScheduler().runTaskLater(this, () -> {
                updateResultPreview(state, inv, player);
            }, 1L);
        });
    }

    private void updateResultPreview(InventoryState state, InventoryService inv, Player player) {
        List<ItemStack> ingredients = new ArrayList<>();
        for (int slot : new int[]{10, 11, 12, 19, 20, 21, 28, 29, 30}) {
            ItemStack item = state.getItem(slot);
            if (item != null && item.getType() != Material.AIR) {
                ingredients.add(item);
            }
        }

        ItemStack result = checkRecipe(ingredients);
        state.setItem(24, result != null ? result : new ItemStack(Material.AIR));
        inv.refreshInventory(player);
    }

    private ItemStack checkRecipe(List<ItemStack> ingredients) {
        // Exemplo: 3 diamantes = 1 diamante
        long diamondCount = ingredients.stream()
            .filter(i -> i.getType() == Material.DIAMOND)
            .count();

        if (diamondCount == 3) {
            return new ItemStack(Material.DIAMOND_BLOCK);
        }

        return null;
    }

    private void clearGrid(InventoryState state) {
        for (int slot : new int[]{10, 11, 12, 19, 20, 21, 28, 29, 30}) {
            state.setItem(slot, null);
        }
        state.setItem(24, null);
    }
}
```

### Resultado
- Grid 3x3 para arrastar ingredientes
- Preview automático do resultado
- Anti-dupe protection
- Botões para craftar e limpar

---

## 6. Shared Inventories - Multiplayer Trading

### Objetivo
Sistema de trade entre 2 players com inventário compartilhado.

### YAML
```yaml
inventories:
  trade:
    title: "&e&lTrade: &f%player1% &7⇄ &f%player2%"
    size: 54

    items:
      # Divisor central
      - slot: [4, 13, 22, 31, 40, 49]
        material: YELLOW_STAINED_GLASS_PANE
        display_name: "&e↕ Trade Zone ↕"

      # Background esquerdo (Player 1)
      - slot: [0-3, 9-12, 18-21, 27-30, 36-39]
        material: LIME_STAINED_GLASS_PANE
        display_name: " "

      # Background direito (Player 2)
      - slot: [5-8, 14-17, 23-26, 32-35, 41-44]
        material: LIGHT_BLUE_STAINED_GLASS_PANE
        display_name: " "

      # Info Player 1
      - slot: 45
        material: PLAYER_HEAD
        skull_owner: "%player1%"
        display_name: "&a%player1%"
        lore:
          - "&7Status: &e%player1_ready%"

      # Confirm Player 1
      - slot: 46
        material: RED_CONCRETE
        display_name: "&cNão Pronto"
        click_actions:
          - "custom: toggle_ready_player1"

      # Info Player 2
      - slot: 53
        material: PLAYER_HEAD
        skull_owner: "%player2%"
        display_name: "&b%player2%"
        lore:
          - "&7Status: &e%player2_ready%"

      # Confirm Player 2
      - slot: 52
        material: RED_CONCRETE
        display_name: "&cNão Pronto"
        click_actions:
          - "custom: toggle_ready_player2"

      # Botão de trade (central)
      - slot: 49
        material: BARRIER
        display_name: "&cTrade Incompleto"
        lore:
          - "&7Aguardando ambos confirmarem"

    drag_settings:
      enabled: true
      allowed_slots: [0-3, 9-12, 18-21, 27-30, 5-8, 14-17, 23-26, 32-35]
      anti_dupe: true
```

### Java
```java
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TradeManager {

    private final Map<String, TradeSession> activeTrades = new HashMap<>();

    public void startTrade(Player player1, Player player2) {
        String sessionId = "trade_" + UUID.randomUUID();

        InventoryService inv = AfterCore.get().inventory();

        InventoryContext ctx1 = InventoryContext.builder(player1)
            .withPlaceholder("player1", player1.getName())
            .withPlaceholder("player2", player2.getName())
            .withPlaceholder("player1_ready", "Não Pronto")
            .withPlaceholder("player2_ready", "Não Pronto")
            .build();

        InventoryContext ctx2 = InventoryContext.builder(player2)
            .withPlaceholder("player1", player1.getName())
            .withPlaceholder("player2", player2.getName())
            .withPlaceholder("player1_ready", "Não Pronto")
            .withPlaceholder("player2_ready", "Não Pronto")
            .build();

        inv.openSharedInventory(player1, "trade", sessionId, ctx1);
        inv.openSharedInventory(player2, "trade", sessionId, ctx2);

        TradeSession session = new TradeSession(player1, player2, sessionId);
        activeTrades.put(sessionId, session);

        registerTradeActions(session);
    }

    private void registerTradeActions(TradeSession session) {
        ActionService actions = AfterCore.get().actions();

        actions.registerHandler("toggle_ready_player1", (player, args) -> {
            if (player.equals(session.player1)) {
                session.player1Ready = !session.player1Ready;
                updateTradeStatus(session);
            }
        });

        actions.registerHandler("toggle_ready_player2", (player, args) -> {
            if (player.equals(session.player2)) {
                session.player2Ready = !session.player2Ready;
                updateTradeStatus(session);
            }
        });
    }

    private void updateTradeStatus(TradeSession session) {
        InventoryService inv = AfterCore.get().inventory();

        // Atualizar visual dos botões de confirm
        inv.getSharedContext(session.sessionId).ifPresent(ctx -> {
            // Player 1
            ItemStack p1Ready = session.player1Ready
                ? new ItemStack(Material.GREEN_CONCRETE)
                : new ItemStack(Material.RED_CONCRETE);
            ctx.setItem(46, p1Ready);

            // Player 2
            ItemStack p2Ready = session.player2Ready
                ? new ItemStack(Material.GREEN_CONCRETE)
                : new ItemStack(Material.RED_CONCRETE);
            ctx.setItem(52, p2Ready);

            // Botão central
            if (session.player1Ready && session.player2Ready) {
                ItemStack tradeButton = new ItemStack(Material.EMERALD_BLOCK);
                // Adicionar nome "Confirmar Trade"
                ctx.setItem(49, tradeButton);

                // Executar trade
                executeTrade(session);
            }

            inv.broadcastUpdate(session.sessionId, 46, p1Ready);
            inv.broadcastUpdate(session.sessionId, 52, p2Ready);
        });
    }

    private void executeTrade(TradeSession session) {
        // Coletar itens de cada player
        // Dar itens ao player oposto
        // Fechar inventários
        // Remover sessão

        session.player1.sendMessage("§aTrade realizado com sucesso!");
        session.player2.sendMessage("§aTrade realizado com sucesso!");

        session.player1.closeInventory();
        session.player2.closeInventory();

        activeTrades.remove(session.sessionId);
    }

    private static class TradeSession {
        final Player player1;
        final Player player2;
        final String sessionId;
        boolean player1Ready = false;
        boolean player2Ready = false;

        TradeSession(Player player1, Player player2, String sessionId) {
            this.player1 = player1;
            this.player2 = player2;
            this.sessionId = sessionId;
        }
    }
}
```

### Resultado
- 2 players visualizam mesmo inventário
- Cada player tem área própria (esquerda/direita)
- Confirmação bilateral necessária
- Updates sincronizados em tempo real

---

## 7. Persistence - Save/Load State

### Objetivo
Inventário que salva estado no database e restaura ao reabrir.

### YAML
```yaml
inventories:
  player_vault:
    title: "&d&lSeu Cofre Pessoal"
    size: 54

    persistence:
      enabled: true
      save_on_close: true
      auto_save_interval: 300  # 5 minutos

    drag_settings:
      enabled: true
      allowed_slots: [0-53]
      anti_dupe: true

    items:
      # Todos os slots livres para storage
```

### Java
```java
public class VaultCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }

        InventoryService inv = AfterCore.get().inventory();
        UUID playerId = player.getUniqueId();

        // Carregar estado do database
        inv.loadState(playerId, "player_vault").thenAccept(loadedState -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                InventoryContext ctx = InventoryContext.builder(player).build();
                inv.openInventory(player, "player_vault", ctx);

                // Aplicar estado carregado
                if (loadedState != null) {
                    inv.getState(playerId).ifPresent(state -> {
                        // Restaurar itens
                        for (int slot = 0; slot < 54; slot++) {
                            ItemStack item = loadedState.getItem(slot);
                            if (item != null) {
                                state.setItem(slot, item);
                            }
                        }

                        // Restaurar custom data
                        state.setData("last_opened", System.currentTimeMillis());

                        inv.refreshInventory(player);
                    });

                    player.sendMessage("§aCofre carregado!");
                } else {
                    player.sendMessage("§eCofre vazio. Adicione itens!");
                }
            });
        }).exceptionally(ex -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage("§cErro ao carregar cofre: " + ex.getMessage());
            });
            return null;
        });

        return true;
    }
}

// Listener para salvar ao fechar
public class VaultListener implements Listener {

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        InventoryService inv = AfterCore.get().inventory();
        UUID playerId = player.getUniqueId();

        inv.getState(playerId).ifPresent(state -> {
            if (state.getInventoryId().equals("player_vault")) {
                // Salvar no database (async)
                inv.saveState(playerId, "player_vault", state)
                    .thenRun(() -> {
                        plugin.getLogger().info("Vault de " + player.getName() + " salvo.");
                    });
            }
        });
    }
}
```

### Resultado
- Itens são salvos no database ao fechar
- Estado é restaurado ao reabrir
- Auto-save a cada 5 minutos
- Suporta custom data (last_opened, etc.)

---

## 8. Custom Actions - Extending Action Handlers

### Objetivo
Criar ações customizadas para lógica específica do seu plugin.

### Registrar Action Handlers
```java
import com.afterlands.core.actions.ActionService;

public class MyPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        registerCustomActions();
    }

    private void registerCustomActions() {
        ActionService actions = AfterCore.get().actions();

        // Ação: dar random reward
        actions.registerHandler("random_reward", (player, args) -> {
            List<String> rewards = Arrays.asList("diamond", "emerald", "gold");
            String reward = rewards.get(ThreadLocalRandom.current().nextInt(rewards.size()));

            Material material = Material.valueOf(reward.toUpperCase());
            player.getInventory().addItem(new ItemStack(material, 1));
            player.sendMessage("§aVocê ganhou: §e" + reward);
        });

        // Ação: teleport to coordinates
        actions.registerHandler("teleport_coords", (player, args) -> {
            // args exemplo: "100 64 200"
            String[] coords = args.split(" ");
            if (coords.length == 3) {
                double x = Double.parseDouble(coords[0]);
                double y = Double.parseDouble(coords[1]);
                double z = Double.parseDouble(coords[2]);

                Location loc = new Location(player.getWorld(), x, y, z);
                player.teleport(loc);
                player.sendMessage("§aTeleportado para §e" + x + ", " + y + ", " + z);
            }
        });

        // Ação: add player to team
        actions.registerHandler("join_team", (player, args) -> {
            String teamName = args.trim();
            // Lógica para adicionar player ao team
            player.sendMessage("§aVocê entrou no time: §e" + teamName);
        });

        // Ação: give exp
        actions.registerHandler("give_exp", (player, args) -> {
            int amount = Integer.parseInt(args.trim());
            player.giveExp(amount);
            player.sendMessage("§a+§e" + amount + " XP");
        });
    }
}
```

### Usar Actions no YAML
```yaml
inventories:
  rewards_menu:
    title: "&6&lRecompensas"
    size: 27

    items:
      - slot: 11
        material: CHEST
        display_name: "&eReward Aleatório"
        click_actions:
          - "random_reward"  # Chama nossa action custom
          - "sound: LEVEL_UP"

      - slot: 13
        material: ENDER_PEARL
        display_name: "&dTeleport Spawn"
        click_actions:
          - "teleport_coords: 0 100 0"

      - slot: 15
        material: BANNER
        display_name: "&cEntrar Time Vermelho"
        click_actions:
          - "join_team: red"
          - "close"
```

### Resultado
- Actions totalmente customizáveis
- Reutilizáveis em múltiplos inventários
- Fácil de testar e debugar

---

## 9. Performance Tips - Otimizações

### Tip 1: Use Cache Wisely
```java
// MAL: Recompilar item toda vez
for (Player p : Bukkit.getOnlinePlayers()) {
    inv.openInventory(p, "menu", InventoryContext.builder(p).build());
    // Cada abertura recompila TODOS os itens
}

// BOM: Items são cached automaticamente
for (Player p : Bukkit.getOnlinePlayers()) {
    InventoryContext ctx = InventoryContext.builder(p)
        .withPlaceholder("player_name", p.getName())  // Unique por player
        .build();
    inv.openInventory(p, "menu", ctx);
    // Cache hit rate: 80-90%
}
```

### Tip 2: Minimize Placeholders
```yaml
# MAL: Muitos placeholders únicos
items:
  - slot: 0
    display_name: "&a%player_name% - %player_uuid% - %timestamp%"
    # Nunca usa cache (sempre único)

# BOM: Placeholders estáticos
items:
  - slot: 0
    display_name: "&aMenu Principal"
    lore:
      - "&7Bem-vindo, %player_name%"
    # Cache funciona bem
```

### Tip 3: Paginação HYBRID para Listas Grandes
```yaml
# MAL: LAYOUT_ONLY com 1000 itens
pagination:
  mode: LAYOUT_ONLY  # Renderiza todos os slots
  items_per_page: 28

# BOM: HYBRID com 1000 itens
pagination:
  mode: HYBRID  # Apenas itens visíveis
  items_per_page: 28
# 35x mais rápido para inventários grandes
```

### Tip 4: Batch Updates
```java
// MAL: Refresh para cada item
for (int i = 0; i < 100; i++) {
    state.setItem(i, item);
    inv.refreshInventory(player);  // 100 refreshes!
}

// BOM: Batch update
for (int i = 0; i < 100; i++) {
    state.setItem(i, item);
}
inv.refreshInventory(player);  // 1 refresh apenas
```

### Tip 5: Async Database Operations
```java
// MAL: Bloqueia main thread
InventoryState state = inv.loadState(playerId, "vault").join();  // NUNCA!

// BOM: Async com callback
inv.loadState(playerId, "vault").thenAccept(state -> {
    Bukkit.getScheduler().runTask(plugin, () -> {
        // Processar no main thread se necessário
    });
});
```

---

## 10. Troubleshooting - Problemas Comuns

### Problema 1: Inventário não abre
**Sintoma**: Comando executado, mas nada acontece.

**Debug**:
```java
inv.openInventory(player, "menu", ctx);

// Verificar logs:
// [AfterCore] Inventory config 'menu' not found
```

**Solução**:
- Verificar se `inventories.yml` existe em `plugins/AfterCore/`
- Verificar se ID do inventário está correto
- Recarregar config: `/acore reload`

---

### Problema 2: Placeholders não substituem
**Sintoma**: `%player_name%` aparece literal.

**Causas**:
1. PlaceholderAPI não instalado (graceful degradation)
2. Placeholder não registrado no context

**Solução**:
```java
// Adicionar placeholder ao context
InventoryContext ctx = InventoryContext.builder(player)
    .withPlaceholder("player_name", player.getName())  // Manual fallback
    .build();
```

---

### Problema 3: Memory Leak
**Sintoma**: Memória cresce constantemente.

**Debug**:
```
/acore memory
```

**Possíveis Causas**:
- InventoryViewHolder não limpos
- DragSession expiradas

**Solução**:
- Usar persistence para limpar state antigos
- Configurar `expireAfterAccess` no cache

---

### Problema 4: TPS Drop
**Sintoma**: TPS cai ao abrir inventários.

**Debug**:
```
/acore metrics
```

**Causas**:
- Muitas animações simultâneas
- Paginação LAYOUT_ONLY com muitos itens

**Solução**:
- Reduzir `interval` de animações
- Usar paginação HYBRID
- Verificar actions síncronas pesadas

---

### Problema 5: Items Duplicados
**Sintoma**: Player consegue duplicar itens com drag.

**Solução**:
```yaml
drag_settings:
  anti_dupe: true  # SEMPRE ativar
```

---

### Problema 6: Estado não persiste
**Sintoma**: Itens somem ao reabrir.

**Verificar**:
1. Persistence habilitada?
   ```yaml
   persistence:
     enabled: true
     save_on_close: true
   ```

2. Database configurado?
   ```
   /acore db
   ```

3. Save sendo chamado?
   ```java
   inv.saveState(playerId, "vault", state).join();
   ```

---

## 11. Dynamic Titles with Placeholders

### Objetivo
Criar inventários com títulos que atualizam automaticamente com placeholders dinâmicos.

### inventories.yml
```yaml
inventories:
  player_stats_live:
    title: "&aTPS: %server_tps% | Online: %server_online%"
    title_update_interval: 20  # Atualizar a cada 1 segundo (20 ticks)
    size: 27

    items:
      # Stats display
      stats:
        slot: 13
        material: DIAMOND
        display_name: "&eServer Stats"
        lore:
          - "&7TPS: %server_tps%"
          - "&7Players: %server_online%/%server_max_players%"
          - ""
          - "&aAtualização em tempo real!"

      # Close button
      close:
        slot: 22
        material: BARRIER
        display_name: "&cFechar"
        click_actions:
          - "close:"
```

### Java
```java
@Override
public void onEnable() {
    AfterCoreAPI core = AfterCore.get();

    // Registrar command para abrir
    getCommand("stats").setExecutor((sender, cmd, label, args) -> {
        if (!(sender instanceof Player player)) {
            return true;
        }

        // Criar context com placeholders iniciais
        InventoryContext ctx = InventoryContext.builder(player)
            .withPlaceholder("server_tps", getTPS())
            .withPlaceholder("server_online", String.valueOf(Bukkit.getOnlinePlayers().size()))
            .withPlaceholder("server_max_players", String.valueOf(Bukkit.getMaxPlayers()))
            .build();

        // Abrir inventário
        core.inventory().openInventory(player, "player_stats_live", ctx);

        return true;
    });
}

private String getTPS() {
    // Obter TPS atual do servidor (implementação específica)
    double tps = Bukkit.getTPS()[0]; // Paper/Spigot
    return String.format("%.1f", tps);
}
```

### Features
- Títulos atualizam sem fechar inventário (ProtocolLib)
- Graceful degradation se ProtocolLib ausente (reabre inventário)
- Suporta PlaceholderAPI
- Cache para evitar updates desnecessários
- Configurável via `title_update_interval` (0 = disabled)

### Performance
- **TPS Impact**: ~0.1ms/tick por inventário com título dinâmico
- **Overhead**: Packet send (~0.05ms) + placeholder resolution (~0.05ms)
- **Recomendação**: Use intervalos >= 20 ticks (1 segundo)

### Troubleshooting

**Título não atualiza?**
1. Verificar `title_update_interval > 0`
2. Verificar placeholders válidos
3. Checar se ProtocolLib está instalado: `/acore status`

**Título pisca ao atualizar?**
- Causa: ProtocolLib não disponível (fallback reabre inventário)
- Solução: Instalar ProtocolLib para updates smooth via packets

---

## 12. Click Type Handlers - Diferentes Tipos de Click

### Objetivo
Executar actions diferentes baseadas no tipo de click (esquerdo, direito, shift+click, etc.), similar ao inventory-framework do DevNathan.

### YAML (plugins/AfterCore/inventories.yml)
```yaml
config-version: 1

inventories:
  shop_menu:
    title: "&aShop"
    size: 27

    items:
      diamond_item:
        slot: 13
        material: DIAMOND
        name: "&bDiamond"
        lore:
          - "&7Left click: Buy 1"
          - "&7Right click: Sell 1"
          - "&7Shift+Left: Buy stack"
          - "&7Shift+Right: Sell all"
          - "&7Middle click: Item info"

        # Actions por tipo de click
        on_left_click:
          - "sound: UI_BUTTON_CLICK"
          - "message: &aBought 1 diamond for $100"
          - "console: eco take %player_name% 100"
          - "console: give %player_name% diamond 1"

        on_right_click:
          - "sound: UI_BUTTON_CLICK"
          - "message: &eSold 1 diamond for $80"
          - "console: clear %player_name% diamond 1"
          - "console: eco give %player_name% 80"

        on_shift_left_click:
          - "sound: ENTITY_PLAYER_LEVELUP"
          - "message: &aBought 64 diamonds for $6400"
          - "console: eco take %player_name% 6400"
          - "console: give %player_name% diamond 64"

        on_shift_right_click:
          - "sound: ENTITY_EXPERIENCE_ORB_PICKUP"
          - "message: &eSold all diamonds!"
          - "console: clearall %player_name% diamond"
          - "console: eco give %player_name% %diamonds_total_value%"

        on_middle_click:
          - "message: &7=== Diamond Info ==="
          - "message: &7Buy price: $100 each"
          - "message: &7Sell price: $80 each"
          - "message: &7Stock: Unlimited"

        # Fallback para outros tipos de click (opcional)
        actions:
          - "message: &cUse left/right click to buy/sell!"

      gold_item:
        slot: 14
        material: GOLD_INGOT
        name: "&6Gold Ingot"
        lore:
          - "&7Click to buy/sell"

        on_left_click:
          - "message: &aBought 1 gold for $50"

        on_right_click:
          - "message: &eSold 1 gold for $40"

        # Se não definir actions, outros clicks não fazem nada
```

### Java API (Programmatic)
```java
import com.afterlands.core.inventory.click.ClickContext;
import com.afterlands.core.inventory.item.GuiItem;
import org.bukkit.Material;

public void createShopItem() {
    GuiItem shopItem = new GuiItem.Builder()
        .slot(13)
        .material(Material.DIAMOND)
        .name("&bDiamond")
        .lore(Arrays.asList(
            "&7Left click: Buy 1",
            "&7Right click: Sell 1",
            "&7Shift+Left: Bulk buy"
        ))
        // Handlers programáticos com lambdas
        .onLeftClick(ctx -> {
            Player player = ctx.player();

            // Lógica de compra
            if (hasEnoughMoney(player, 100)) {
                takeMoney(player, 100);
                player.getInventory().addItem(new ItemStack(Material.DIAMOND));
                ctx.sendMessage("&aBought 1 diamond!");
            } else {
                ctx.sendMessage("&cNot enough money!");
            }
        })
        .onRightClick(ctx -> {
            Player player = ctx.player();

            // Lógica de venda
            if (player.getInventory().contains(Material.DIAMOND)) {
                player.getInventory().removeItem(new ItemStack(Material.DIAMOND));
                giveMoney(player, 80);
                ctx.sendMessage("&eSold 1 diamond!");
            } else {
                ctx.sendMessage("&cYou don't have any diamonds!");
            }
        })
        .onShiftLeftClick(ctx -> {
            ctx.sendMessage("&aBulk buy mode!");
            ctx.holder().switchTab("bulk_buy");
        })
        .onMiddleClick(ctx -> {
            ctx.sendMessage("&7=== Diamond Info ===");
            ctx.sendMessage("&7Price: $100 buy / $80 sell");
        })
        // Fallback handler para outros tipos de click
        .onClick(ctx -> {
            ctx.sendMessage("&cUse left or right click!");
        })
        .build();
}
```

### ClickContext API
O `ClickContext` fornece acesso completo ao click:

```java
.onLeftClick(ctx -> {
    // Informações do click
    Player player = ctx.player();
    ClickType type = ctx.clickType();
    int slot = ctx.slot();
    ItemStack cursor = ctx.cursor();
    ItemStack currentItem = ctx.currentItem();

    // Convenience methods
    boolean isShift = ctx.isShiftClick();
    boolean isLeft = ctx.isLeftClick();
    boolean isRight = ctx.isRightClick();

    // Navigation helpers
    ctx.nextPage();
    ctx.previousPage();
    ctx.switchTab("weapons");
    ctx.close();
    ctx.refresh();

    // Message helper (color codes automaticamente)
    ctx.sendMessage("&aHello!");

    // Access to holder and context
    InventoryViewHolder holder = ctx.holder();
    InventoryContext invCtx = ctx.inventoryContext();

    // Acesso ao GuiItem e InventoryClickEvent original
    GuiItem item = ctx.item();
    InventoryClickEvent event = ctx.event();
});
```

### Tipos de Click Suportados

| YAML Key | ClickType | Descrição |
|----------|-----------|-----------|
| `on_left_click` | LEFT | Click esquerdo |
| `on_right_click` | RIGHT | Click direito |
| `on_shift_left_click` | SHIFT_LEFT | Shift + Click esquerdo |
| `on_shift_right_click` | SHIFT_RIGHT | Shift + Click direito |
| `on_middle_click` | MIDDLE | Click do meio (roda) |
| `on_double_click` | DOUBLE_CLICK | Double click esquerdo |
| `on_drop` | DROP | Tecla Q |
| `on_control_drop` | CONTROL_DROP | Ctrl + Q |
| `on_number_key` | NUMBER_KEY | Teclas 1-9 (hotbar) |
| `actions` | (fallback) | Qualquer outro click |

### Prioridade de Execução

1. **Handler programático** (Java lambda) - executado primeiro
2. **Actions YAML** por tipo de click - executado se não tem handler
3. **Actions default** (`actions:`) - fallback se tipo não definido

```java
// Exemplo de prioridade
GuiItem item = new GuiItem.Builder()
    .onLeftClick(ctx -> {
        // 1. Este handler tem prioridade (programático)
        ctx.sendMessage("Handler programático!");
    })
    .onLeftClick(Arrays.asList("message: YAML action"))  // Ignorado!
    .actions(Arrays.asList("message: Fallback"))         // Usado para outros clicks
    .build();
```

### Compatibilidade

**IMPORTANTE**: Configurações antigas continuam funcionando!

```yaml
# Antigo (ainda funciona)
items:
  old_item:
    slot: 10
    actions:
      - "message: Hello"

# Novo (com click types)
items:
  new_item:
    slot: 11
    on_left_click:
      - "message: Left click!"
    on_right_click:
      - "message: Right click!"
```

### Performance

- **Zero overhead** se não usar handlers por tipo (fallback direto para `actions`)
- **EnumMap** para O(1) lookup por ClickType
- **ClickContext** é record imutável (zero custo de cópia)
- Handlers programáticos evitam parsing de actions (mais rápido)

### Use Cases

**Shop com Buy/Sell:**
```yaml
on_left_click:  # Comprar
  - "console: eco take %player_name% 100"
  - "console: give %player_name% diamond 1"
on_right_click: # Vender
  - "console: clear %player_name% diamond 1"
  - "console: eco give %player_name% 80"
```

**Menu de Navegação:**
```yaml
on_left_click:      # Próxima página
  - "next_page"
on_right_click:     # Página anterior
  - "prev_page"
on_middle_click:    # Primeira página
  - "switch_tab: main"
```

**Info vs Action:**
```yaml
on_left_click:      # Executar ação
  - "console: warp spawn"
on_right_click:     # Ver informações
  - "message: &7Warp: Spawn"
  - "message: &7Status: Active"
```

### Troubleshooting

**Actions não executam?**
1. Verificar nome correto: `on_left_click` (underscore, não hífen)
2. Verificar se item está no slot correto
3. Ativar debug: `debug: true` em `config.yml`
4. Checar console: `[InventoryAction] Handling LEFT for player ...`

**Handlers programáticos ignorados?**
- Causa: Handlers programáticos só funcionam via API Java (não YAML)
- YAML sempre usa actions (strings)

**Conflito entre handler e actions?**
- Handlers programáticos têm prioridade absoluta
- Se definir `.onLeftClick(handler)`, actions YAML são ignoradas

---

## 13. Variants & Extended Actions - Itens Condicionais

### Objetivo
Criar itens que mudam visualmente com base em condições (ex: desbloqueado/bloqueado) e actions com lógica de sucesso/falha.

### YAML
```yaml
config-version: 1

inventories:
  conditional_menu:
    title: "&8&lItens Condicionais"
    size: 27

    # Templates reutilizáveis
    variant-items:
      vip_sword:
        material: DIAMOND_SWORD
        name: "&bEspada VIP"
        lore:
          - "&7Dano: &f10"
          - "&aExclusivo para VIPs!"
        enchanted: true
        view-conditions:
          - "permission: group.vip"
        actions:
          - "console: give %player% diamond_sword 1"
          - "message: &aEspada resgatada!"

    items:
      '11':
        material: WOODEN_SWORD
        name: "&7Espada Comum"
        lore:
          - "&7Dano: &f5"
          - "&7Upgrade para VIP para melhorar!"
        
        # Referência a um template (se condição do template for true, substitui este item)
        variants:
          - "vip_sword"

      '15':
        material: CHEST
        name: "&6Recompensa Diária"
        lore:
          - "&7Clique para resgatar"
        
        # Variante Inline (definida direto no item)
        variant0:
          material: ENDER_CHEST
          name: "&aRecompensa (Pronta!)"
          view-conditions:
            - "%player_has_reward% == true"
          
          # Extended Actions (com conditions/success/fail)
          on_left_click:
            conditions:
              - "%player_empty_slots% >= 1"
            success:
              - "console: give %player% diamond 1"
              - "sound: LEVEL_UP"
              - "message: &aRecompensa recebida!"
            fail:
              - "sound: VILLAGER_NO"
              - "message: &cEsvazie seu inventário primeiro!"
```

### Explicação

1. **Variant Items (`variant-items`)**:
   - Templates definidos no topo do inventário.
   - Podem ser referenciados por múltiplos itens.
   - Útil para estados comuns (ex: "unlocked", "completed").

2. **Variants List (`variants`)**:
   - Lista de chaves de templates.
   - O framework verifica as condições de cada variante na ordem.
   - A primeira variante com condições verdadeiras (`view-conditions`) substitui o item base.

3. **Inline Variants (`variant0`, `variant1`...)**:
   - Definidas diretamente dentro do item.
   - `variant0` tem prioridade sobre `variant1`, que tem prioridade sobre `variants` (lista).

4. **Extended Actions (`conditions`/`success`/`fail`)**:
   - Permite lógica complexa sem precisar de código Java.
   - **conditions**: Lista de condições (AND) ou string única.
   - **success**: Actions executadas se condições forem true.
   - **fail**: Actions executadas se condições forem false.

