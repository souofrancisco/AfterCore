## `ActionService`

### O que é

`ActionService` é o sistema central do AfterCore para execução de ações configuráveis. Ele permite que admins e desenvolvedores definam comportamentos dinâmicos em configurações (YAML) usando uma string simples ou DSL.

Ele opera em dois níveis:
1.  **Parsing**: Converte strings como `message: &aOlá` em objetos `ActionSpec`.
2.  **Execution**: Executa a ação associada a um handler registrado.

### Tabela de Actions

Abaixo estão todas as actions disponíveis nativamente no AfterCore.
Algumas actions possuem aliases (nomes alternativos) que funcionam da mesma forma.

| Action | Aliases | Argumentos | Descrição | Exemplo |
| :--- | :--- | :--- | :--- | :--- |
| **message** | - | `<texto>` | Envia uma mensagem de chat para o jogador. | `message: &aBem-vindo ao servidor!` |
| **centered_message**| - | `<texto>` | Envia uma mensagem centralizada no chat. | `centered_message: &e&lSUCESSO!` |
| **actionbar** | - | `<texto>` | Envia uma mensagem na actionbar. | `actionbar: &bVocê ganhou 100 xp` |
| **title** | - | `<title>;<subtitle>;[in];[stay];[out]` | Envia um título na tela. Tempos em ticks (opcional). | `title: &6Vitoria!;&7Você venceu o evento;10;70;20` |
| **sound** | `play_sound` | `<SOM> [volume] [pitch]` | Toca um som do Minecraft. | `sound: UI_BUTTON_CLICK 1.0 2.0` |
| **resource_pack_sound** | `play_resourcepack_sound` | `<som.custom> [vol] [pitch]` | Toca um som customizado (resource pack). | `resource_pack_sound: my.custom.sound 1.0 1.0` |
| **teleport** | - | `<w> <x> <y> <z> [yaw] [pitch]` | Teleporta o jogador. Suporta coordenadas relativas (`~`). | `teleport: world ~ ~10 ~` |
| **potion** | - | `<EFEITO> <duração> <nível> [amb] [part]` | Aplica efeito de poção. Duração em ticks. | `potion: SPEED 200 1` (Speed II por 10s) |
| **console** | `console_command` | `<comando>` | Executa comando no console (sem `/`). | `console: say O evento começou!` |
| **player_command** | - | `<comando>` | Faz o jogador executar um comando (sem `/`). | `player_command: spawn` |
| **global_message** | - | `<texto>` | Envia mensagem para todos os jogadores online. | `global_message: &eO servidor reiniciará.` |
| **global_centered_message** | - | `<texto>` | Envia mensagem centralizada para todos. | `global_centered_message: &c&lALERTA` |
| **open** | `open_panel`, `open-panel` | `<menu_id>` | Abre outro menu/inventário. Salva histórico ao navegar. | `open: warp_menu` |
| **close** | `close_panel`, `close_panel` | - | Fecha o inventário atual. | `close` |
| **refresh** | - | - | Recarrega os itens do menu atual. | `refresh` |
| **switch_tab** | - | `<tab_id>` | Troca de aba em menus de categoria. | `switch_tab: food_tab` |
| **next_page** | - | - | Avança para a próxima página (menus paginados). | `next_page` |
| **prev_page** | - | - | Volta para a página anterior (menus paginados). | `prev_page` |
| **wait** | `delay` | `<ticks>` | Pausa a execução da próxima action (somente em sequências async/inventários). | `wait: 60` (3 segundos) |
| **back** | `previous_panel`, `previous-panel` | - | Volta para o menu anterior no histórico. | `back` |

> **Nota sobre `open` e `back`**: Em menus de inventário, essas actions são inteligentes e mantêm um histórico de navegação, permitindo criar menus com botão "Voltar" funcional.

### Exemplos Funcionais e Sintaxe

O AfterCore suporta dois dialetos principais para escrever actions. O mais comum é o **SimpleKV**.

#### Sintaxe Básica (SimpleKV)
O formato é `chave: valor`. Se houver múltiplos pares, separe por vírgula. O par que não for `time` ou `frame` é considerado a action.

```yaml
# Simples
click_actions:
  - "message: &aVocê clicou!"
  - "sound: UI_BUTTON_CLICK"
  - "close"

# Com delay (time em ticks ou segundos)
open_actions:
  - "time: 20, message: &7Carregando..."
  - "time: 40, sound: ORB_PICKUP"
  - "time: 2s, title: &aPronto!;&7Seus dados foram carregados"
```

#### Argumentos Específicos

**1. Teleport com coordenadas relativas**
Use `~` para manter a coordenada atual do jogador ou `~10` para adicionar 10 blocos.
```yaml
# Teleporta 5 blocos para cima
- "teleport: world ~ ~5 ~"
# Teleporta para o spawn (absoluto)
- "teleport: world 0 65 0 90 0"
```

**2. Uso de Wait/Delay**
O `ActionService` agora suporta execução sequencial com pausas. Isso é ideal para criar cutscenes simples ou feedbacks visuais em menus.
```yaml
click_actions:
  - "message: &eProcessando..."
  - "play_sound: BLOCK_ANVIL_USE"
  - "wait: 40"  # Espera 2 segundos (40 ticks)
  - "message: &aConcluído com sucesso!"
  - "play_sound: ENTITY_PLAYER_LEVELUP"
  - "close"
```

**3. Potion Effects**
Duração em ticks (20 ticks = 1s). Nível 0 = I, Nível 1 = II.
```yaml
# Speed II por 10 segundos
- "potion: SPEED 200 1"
# Remover efeito (duração 0)
- "potion: SPEED 0 0"
```

**4. Navegação em Menus**
Exemplo de um botão de voltar que funciona dinamicamente:
```yaml
back_button:
  type: ARROW
  name: "&cVoltar"
  click_actions:
    - "back"  # Se não houver histórico, fecha o menu.
```

### Registrando Actions Customizadas (API)

Plugins podem registrar suas próprias actions.

```java
ActionService actions = AfterCore.get().actions();

actions.registerHandler("dar_item", (player, spec) -> {
    String itemId = spec.rawArgs(); // ex: "DIAMOND"
    Material mat = Material.matchMaterial(itemId);
    if (mat != null) {
        player.getInventory().addItem(new ItemStack(mat));
        player.sendMessage("§aVocê recebeu " + itemId);
    }
});
```

Uso na config:
```yaml
click_actions:
    - "dar_item: DIAMOND"
```

### Handlers de Inventário (Client-Side Logic)

Além do `ActionService` geral, o sistema de inventários possui handlers especiais que rodam imediatamente no click (main thread) para permitir navegação fluida.

- **`switch_tab`**: Usado em menus do tipo `category` para trocar de aba.
- **`next_page`/`prev_page`**: Usado em menus `pagination`.

Essas actions são registradas automaticamente pelo `InventoryActionHandler` e têm prioridade sobre as actions globais dentro de menus.
