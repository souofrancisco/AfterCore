# AfterCore Command Framework

O **AfterCore Command Framework** (AcoreCmd) é um sistema de comandos moderno, de alta performance e type-safe projetado para o ecossistema AfterLands. Ele combina a facilidade de uso de annotations com a performance de código compilado via `MethodHandles`.

---

## ✨ Características Principais

*   **Zero Reflection (Hot-Path)**: Utiliza `MethodHandles` pré-compilados para invocação, garantindo overhead < 0.2ms.
*   **Type-Safe**: Injeção e conversão automática de argumentos (`Player`, `World`, `Enum`, `int`, etc.).
*   **API Rica**: Annotations completas (`@Command`, `@Subcommand`, `@Arg`, `@Flag`) e DSL Fluente (Builder).
*   **Nested Subcommands**: Suporte nativo a subcomandos aninhados (ex: `/al plot trust add`).
*   **Auto-Help**: Geração automática de mensagens de uso e erro, com suporte a cores e formatação.

---

## 🚀 Começando

Para usar o framework, obtenha uma instância do `CommandService` no seu plugin e registre seu handler.

```java
CommandService commands = afterCore.getCommandService();
commands.register(this, new MeuComando());
```

---

## 📝 API de Anotações (Annotation-Based)

Esta é a forma recomendada de criar comandos. Uma classe representa o comando principal, e seus métodos representam os subcomandos.

### Exemplo Completo

```java
@Command(name = "gamemode", aliases = {"gm"}, description = "Gerencia o modo de jogo")
@Permission("core.gamemode")
@CommandGroup(prefix = "set", description = "Opções de configuração de modo")
public class GamemodeCommand {

    // Comando padrão (/gm)
    @Subcommand("") 
    public void help(CommandContext ctx) {
        ctx.sendHelp(); // Envia o help gerado automaticamente
    }

    // Subcomando simples (/gm survival)
    @Subcommand("survival")
    @Permission("core.gamemode.survival")
    @Description("Altera para modo sobrevivência")
    public void survival(CommandContext ctx, @Sender Player player) {
        player.setGameMode(GameMode.SURVIVAL);
        ctx.send("gamemode.changed", "mode", "Survival");
    }

    // Subcomando com argumentos e flags (/gm set <mode> [target] [-s])
    @Subcommand("set")
    @Usage("/gm set <mode> [target] [ignore]") // Uso customizado para erros
    @UsageHelp("&c<mode> &d[target] &e[-s]") // Uso colorido no help
    public void set(
        CommandContext ctx,
        @Arg("mode") GameMode mode,                            // Enum automático
        @Arg(value = "target", optional = true) Player target, // Argumento opcional
        @Flag(value = "silent", shortName = "s") boolean silent // Flag booleana
    ) {
        Player recipient = target != null ? target : ctx.requirePlayer();
        recipient.setGameMode(mode);
        
        if (!silent) {
            ctx.sendTo(recipient, "gamemode.changed", "mode", mode.name());
        }
    }
}
```

### Referência de Anotações

#### `@Command`
Define a classe como um comando raiz.
*   **`name`** (String): Nome principal do comando (ex: "plot").
*   **`aliases`** (String[]): Aliases do comando (ex: `{"p", "plots"}`).
*   **`description`** (String): Descrição geral mostrada no help principal.
*   **`usage`** (String): Mensagem de uso geral (se vazio, é gerado).
*   **`helpPrefix`** (String): Prefixo decorativo para o help (ex: `"PLOT"` resulta em `&lPLOT &8┃ ...`).

#### `@Subcommand`
Define um método como executor de um subcomando.
*   **`value`** (String): Nome do subcomando.
    *   Use `""` ou `"default"` para o comando raiz (sem argumentos).
    *   Use espaços para aninhamento: `"trust add"` (`/cmd trust add`).
*   **`aliases`** (String[]): Aliases do subcomando.
*   **`description`** (String): Descrição para o help.
*   **`usage`** (String): Padrão de uso simples para mensagens de erro (ex: `/coins add <player> <amount>`).
    *   Substitui o gerador automático no placeholder `{usage}`.
*   **`usageHelp`** (String): Padrão de uso **colorido** exibido apenas na lista de help.
    *   Útil para destacar argumentos obrigatórios vs opcionais visualmente.

#### `@CommandGroup`
Define um grupo de subcomandos que será colapsado no menu de ajuda principal.
*   **`prefix`** (String): O prefixo dos subcomandos que pertencem a este grupo (ex: "plot").
*   **`description`** (String): A descrição exibida para o grupo colapsado.
*   **Comportamento**: No help principal (`/cmd`), todos os subcomandos que começam com o prefixo são substituídos por uma única linha:
    *   `/cmd prefix [!] - description`
    *   Ao clicar ou digitar `/cmd prefix`, mostra-se o help específico desse grupo.
*   **Nota**: Pode ser repetida múltiplas vezes na classe (`@CommandGroups`).

#### `@Permission`
Define requisitos de permissão.
*   **`value`** (String): Node de permissão (ex: `plugin.admin`).
    *   Se usado na **classe**: Aplica-se a todos os subcomandos.
    *   Se usado no **método**: Adiciona-se à verificação (precisa ter ambas se a classe tiver uma).

#### `@Arg`
Define um parâmetro do método como argumento posicional do comando.
*   **`value`** (String): Nome do argumento (ex: "player"). Obrigatório se não compilar com `-parameters`.
*   **`description`** (String): Descrição do argumento para documentação de help.
*   **`defaultValue`** (String): Valor padrão em String. Se definido, torna o argumento **opcional**.
    *   Ex: `@Arg(value="radius", defaultValue="10") int radius`
*   **`optional`** (boolean): Marca explicitamente como opcional sem definir valor padrão (receberá `null` ou `0`/`false`).

**Tipos Suportados:**
*   `String` (Aspas suportadas: `"hello world"`)
*   `int` / `Integer`
*   `double` / `Double`
*   `boolean` / `Boolean`
*   `Player` (Resolve pelo nome, erro se offline)
*   `World` (Resolve pelo nome)
*   `Enum` (Resolve por nome case-insensitive + Tab-Complete automático)

#### `@Flag`
Define um parâmetro como uma flag (opção nomeada), que pode ser colocada em qualquer posição no comando.
*   **`value`** (String): Nome longo (ex: `force` -> usa-se `--force`).
*   **`shortName`** (String): Nome curto (ex: `f` -> usa-se `-f`).
*   **`description`** (String): Descrição da flag.
*   **`defaultValue`** (String): Valor padrão caso a flag não seja usada.

**Comportamento:**
*   `boolean`: Flag de presença. Se usar `-f` ou `--force`, é `true`. Caso contrário, `false` (ou o `defaultValue`).
*   Outros tipos (`String`, `int`): Esperam um valor após a flag.
    *   Ex: `--page 2` ou `-p 2`.

#### `@Sender`
Annotation especial para injetar o emissor do comando com validação de tipo.
*   Parâmetro `Player`: Garante que é um player. Se for console, envia erro automaticamente e não executa o método.
*   Parâmetro `ConsoleCommandSender`: Garante que é o console.
*   Parâmetro `CommandSender`: Aceita qualquer um.

---

## � CommandContext API

O objeto `CommandContext` fornece acesso a tudo que você precisa durante a execução do comando.

### Sender & Player
*   **`ctx.sender()`**: Retorna o `CommandSender` original.
*   **`ctx.isPlayer()`**: Retorna `boolean`.
*   **`ctx.requirePlayer()`**: Retorna `Player` ou lança exceção (use em comandos que já validaram ou `@Sender Player`).
*   **`ctx.player()`**: Retorna `Optional<Player>`.

### Mensagens (Localizadas)
O sistema busca mensagens no `messages.yml` do seu plugin e depois no AfterCore.
*   **`ctx.send("path.to.msg")`**: Envia mensagem simples.
*   **`ctx.send("path", "key", "val")`**: Envia com placeholders (`{key}` -> `val`).
*   **`ctx.sendRaw("&aOlá mundo")`**: Envia mensagem direta formatada (sem lookup de config).

### Argumentos & Flags (Acesso Manual)
Embora a injeção via argumentos do método seja preferida, você pode acessar manualmente:
*   **`ctx.argString("nome")`**
*   **`ctx.argInt("nome", default)`**
*   **`ctx.argDouble("nome")`**
*   **`ctx.hasFlag("f")`**: Verifica se uma flag boolean/presença foi usada.
*   **`ctx.flagValue("page")`**: Pega valor de flag (ex: `--page 10`).

### Async & Scheduler
Helpers para executar tarefas fora da main thread com segurança.
*   **`ctx.runAsync(() -> { ... })`**: Executa no pool de I/O e retorna `CompletableFuture`.
*   **`ctx.runSync(() -> { ... })`**: Volta para a main thread.

---

## �🛠️ Builder API (Programática)

Para criar comandos dinamicamente, utilize o `CommandSpec`.

```java
CommandSpec spec = CommandSpec.root("warp")
    .aliases("warps")
    .description("Sistema de warps")
    .permission("core.warp")
    .playerOnly() // Restringe raiz para players
    
    // Subcomando: /warp <nome>
    .sub("tp")
        .description("Teleporta para uma warp")
        .arg("nome", ArgumentSpec.STRING) // Argumento
        .executor(ctx -> {
            Player p = ctx.requirePlayer();
            String warpName = ctx.args().get("nome");
            // Lógica...
        })
        .done() // Volta para a raiz
        
    // Subcomando: /warp list [-p <pagina>]
    .sub("list")
        .description("Lista as warps")
        .flag("page", "p", ArgumentSpec.INTEGER) // Flag com valor
        .executor(ctx -> {
            int page = ctx.flags().get("page", 1);
            // Lógica...
        })
        .done()
        
    .build();

service.register(spec);
```

### Argument Spec Types
Constantes disponíveis em `ArgumentSpec`:
*   `STRING`: Uma única palavra.
*   `GREEDY_STRING`: Todo o restante do comando (útil para mensagens).
*   `INTEGER`, `DOUBLE`, `BOOLEAN`: Numéricos e booleano.
*   `PLAYER_ONLINE`: Player online (tab-complete nomes).
*   `PLAYER_OFFLINE`: Nome de player (mesmo offline).
*   `WORLD`, `LOCATION`: Mundo e Localização.

---

## 🔍 Placeholders & Mensagens

O framework integra-se ao `MessageService` e fornece placeholders automáticos no contexto de erro ou sucesso.

*   `{label}`: O alias usado (ex: "gm").
*   `{subcommand}`: O caminho do subcomando (ex: "set").
*   `{usage}`: O uso gerado ou definido em `@Usage`.
*   `{arg}`: O nome do argumento que falhou no parsing.
*   `{value}`: O valor inválido fornecido.
*   `{reason}`: Motivo do erro técnico.

---

## 📊 Monitoramento de Performance

O framework é instrumentado nativamente com o `MetricsService`.

| Métrica | Descrição |
| :--- | :--- |
| `acore.cmd.exec.ms` | Histograma do tempo total de execução. |
| `acore.cmd.parse.ms` | Tempo gasto fazendo parsing dos argumentos. |
| `acore.cmd.complete.ms` | Tempo gasto calculando tab-completions. |
| `acore.cmd.exec.fail` | Contador de exceções não tratadas. |
| `acore.cmd.exec.cooldown` | Contador de execuções bloqueadas por cooldown. |

> [!TIP]
> **Otimização**: O Tab-Complete utiliza um cache inteligente com TTL de 2 segundos para sugestões "caras" (como listas de banco de dados), evitando lag no cliente enquanto digita.

---

## ⏱️ Cooldowns Rate Limiting

Use `@Cooldown` para limitar a frequência de execução de comandos por player.

```java
@Subcommand("teleport")
@Cooldown(value = 5, unit = TimeUnit.SECONDS)
public void teleport(CommandContext ctx) {
    // Executa no máximo 1x a cada 5 segundos
}

// Com bypass e mensagem customizada
@Subcommand("heal")
@Cooldown(value = 30, message = "heal.cooldown", bypassPermission = "vip.heal.bypass")
public void heal(CommandContext ctx) {
    ctx.requirePlayer().setHealth(20);
}
```

**Parâmetros:**
*   **`value`**: Duração do cooldown.
*   **`unit`**: Unidade de tempo (`SECONDS` padrão).
*   **`message`**: Chave de mensagem. Se vazio, usa `commands.cooldown`.
*   **`bypassPermission`**: Permissão para ignorar o cooldown.

**Placeholders na mensagem:** `{remaining}` (tempo restante formatado), `{command}`.

---

## 🏷️ Aliases de Subcomandos

Defina aliases para subcomandos usando duas sintaxes.

### Pipe Syntax
```java
@Subcommand("join|j|entrar")
public void join(CommandContext ctx) { ... }
```

### @Alias Annotation
```java
@Subcommand("join")
@Alias({"j", "entrar"})
public void join(CommandContext ctx) { ... }
```

### @Alias em Comandos Raiz
```java
@Command(name = "teleport")
@Alias({"tp", "tele"})
public class TeleportCommand { ... }
```

---

## 🔧 Custom ArgumentTypes

Registre tipos de argumentos customizados por plugin.

```java
// Registrar um tipo customizado (baseado em String)
commandService.argumentTypes().registerForPlugin(myPlugin, "lock-tier", new LockTierType());

// Uso em comandos: especificar o tipo explicitamente
@Subcommand("start")
public void start(
    CommandContext ctx,
    @Arg(value = "tier", type = "lock-tier") String tierId // Usa "lock-tier" para tab completion
) { ... }
```

**Atributo `type`**:
*   Permite vincular um parâmetro `String` (ou outro) a um `ArgumentType` específico registrado.
*   Essencial para fornecer tab-completion customizado (ex: IDs de banco de dados, chaves de config) sem criar classes wrappers desnecessárias.

**Resolução de tipos**: Plugin scope (`registerForPlugin`) → Global scope (`register`).

**Cleanup automático**: Tipos registrados via `registerForPlugin` são removidos automaticamente quando `commandService.unregisterAll(plugin)` é chamado (geralmente no `onDisable`).

---

## 🔄 Aliases Dinâmicos (Runtime)

Adicione ou remova aliases em runtime.

```java
// Adicionar alias
commandService.addAlias("teleport", "tp");

// Remover alias
commandService.removeAlias("tp");

// Listar aliases
Set<String> aliases = commandService.getAliases("teleport");
```

---

## 🌐 Mensagens de Erro Localizadas (v1.3.0+)

O framework fornece mensagens de erro específicas e localizadas para erros comuns de parsing.

### Chaves de Erro

| Chave | Uso | Exemplo de Saída |
| :--- | :--- | :--- |
| `player-not-online` | Player online não encontrado | ✖ Jogador **Steve** não está online. |
| `player-never-joined` | Player offline nunca jogou | ✖ Jogador **Hmm** nunca entrou no servidor. |
| `invalid-number` | Número inválido | ✖ **abc** não é um número válido. |
| `number-out-of-range` | Número fora do range | ✖ Valor deve estar entre **1** e **100**. |
| `world-not-found` | Mundo não existe | ✖ Mundo **nether_test** não existe. |
| `invalid-enum` | Valor de enum inválido | ✖ Valor **xyz** inválido. Opções: **survival, creative** |

### Customização

Sobrescreva no seu `messages.yml`:

```yaml
commands:
  errors:
    player-not-online: "&cO jogador &e{player} &cnão está conectado."
    invalid-number: "&cNúmero inválido: &f{value}"
```

### PlayerOfflineType

Novo tipo de argumento para resolver jogadores offline:

```java
@Subcommand("lookup")
public void lookup(CommandContext ctx, @Arg("target") OfflinePlayer target) {
    // target pode ser um jogador que não está online
    if (target.hasPlayedBefore()) {
        ctx.send("info.player-found", "name", target.getName());
    }
}
```

**Registro:** `playerOffline`, `offlinePlayer`

