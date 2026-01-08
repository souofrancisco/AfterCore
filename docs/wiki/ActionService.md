## `ActionService`

### O que é

`ActionService` faz duas coisas:

- **Parsing** de linhas de action (multi-dialeto) → `ActionSpec`
- **Registry** de handlers → `registerHandler("tipo", handler)`

O AfterCore inclui vários handlers padrão (mensagens, sons, título, teleport, comandos, etc.) e também permite que plugins registrem actions próprias.

### API

- `ActionSpec parse(String line)`
  - Retorna `null` se a linha for vazia ou inválida.
- `registerHandler(String actionTypeKey, ActionHandler handler)`
- `getHandlers()`

### `ActionSpec`

Campos relevantes:

- `typeKey()`: chave do handler (ex.: `"message"`, `"sound"`)
- `rawArgs()`: argumentos crus (string)
- `timeTicks()` / `frameIndex()` (dialeto SimpleKV)
- `trigger()` / `condition()` / `scope()` / `scopeRadius()` (dialeto Motion)
- `rawLine()`: linha original

### Dialetos suportados

#### 1) SimpleKV (default, estilo AfterBlockAnimations)

Formatos suportados:

- `"message: oi"`
- `"time: 20, sound: LEVEL_UP 1.0 1.0"`
- `"time: 10s, frame: 5, teleport: world 0 64 0 90 0"`

Notas:

- `time` aceita ticks (`200`) ou segundos (`10s` → `200` ticks).
- O parser permite múltiplos `k: v` separados por vírgula; o último par “não time/frame” vira a action.
- O core normaliza `;` nos args para espaço (compatibilidade).

#### 2) Motion DSL (compat AfterMotion)

Formato:

```
@<trigger> [?<condicao>] <action>[<SCOPE>]: <args>
```

Triggers:

- `@tick:<n>`
- `@event:<nome>`
- `@event:<nome>:<actorIndex>`

Scopes:

- `[VIEWER]` (default)
- `[NEARBY]` ou `[NEARBY:30]`
- `[ALL]`

Exemplos:

```text
@tick:20 [?%player_health% > 10] message[VIEWER]: &aVocê está vivo!
@event:explosion sound[NEARBY:20]: EXPLODE 1.0 1.0
@event:boss_death global_message[ALL]: &cBoss derrotado!
```

### Executando uma action

`ActionService` apenas parseia/registry; para executar, use:

```java
ActionSpec spec = core.actions().parse("message: &aOlá!");
if (spec != null) {
  core.executeAction(spec, player);
}
```

### Registrando actions customizadas

```java
core.actions().registerHandler("my_action", (target, spec) -> {
  target.sendMessage("Args: " + spec.rawArgs());
});
```

Uso no YAML (ex.: inventories):

```yaml
click_actions:
  - "my_action: qualquer coisa aqui"
```

### Handlers padrão (AfterCore)

Registrados no enable do AfterCore:

- `message`
- `actionbar`
- `sound` (**alias**: `play_sound`)
- `title`
- `teleport`
- `potion`
- `console` (**alias**: `console_command`)
- `player_command`
- `resource_pack_sound` (**alias**: `play_resourcepack_sound`)
- `centered_message`
- `global_message`
- `global_centered_message`

> Observação: `close` é suportado no Inventory Framework (ver `docs/API_REFERENCE.md`).

