## `ConditionService`

### O que é

Engine unificado de condições (inspirado em AfterBlockState + AfterMotion) para avaliar expressões do tipo:

- lógicas: `AND`, `OR`, `NOT`, parênteses
- numéricas: `>`, `>=`, `<`, `<=`, `==`, `!=`
- strings: `contains`, `startsWith`, `endsWith`, `matches`, `equalsIgnoreCase`, etc.
- placeholders: `%...%` com suporte opcional a PlaceholderAPI
- variáveis customizadas por namespace: `%abs_flag:key%`

### API

- `setConditionGroups(Map<String, List<String>> groups)`
  - Define grupos. Um grupo com N linhas vira `(linha1 AND linha2 AND ...)`.
  - **Observação**: na implementação atual, grupos são expandidos por substituição textual.
- `getConditionGroups()`
- `registerVariableProvider(String namespace, ConditionVariableProvider provider)`
  - Registra provider para resolver `%namespace:key%`.
- `evaluateSync(Player, expression, ctx)`
  - Avaliação síncrona. **Se usar PlaceholderAPI, chame na main thread.**
- `evaluate(Player, expression, ctx)`
  - Avaliação segura: alterna para main thread quando necessário.

### `ConditionContext`

`ConditionContext` é uma interface simples que expõe um `Map<String, String>`:

```java
class SimpleCtx implements ConditionContext {
  private final Map<String, String> vars;
  SimpleCtx(Map<String, String> vars) { this.vars = vars; }
  @Override public Map<String, String> variables() { return vars; }
}
```

### Sintaxe suportada

#### Operadores lógicos

```
%player_health% > 10 AND %player_level% >= 5
NOT (%player_world% equals world_nether)
(%a% == true AND %b% == true) OR %perm_admin% == true
```

#### Comparações numéricas

```
%player_level% >= 30
%rank% == 5
%deaths% != 0
```

#### Operadores de string

```
%player_world% equals world
%player_name% equalsIgnoreCase admin
%player_name% contains VIP
%player_world% startsWith world_
%player_name% endsWith _Staff
%player_name% matches [A-Za-z0-9_]+
%player_name% ~ Admin   # legacy (compat)
```

### Placeholders (ordem de resolução)

Na implementação atual, a resolução ocorre assim:

1. **Providers customizados por namespace**: `%namespace:key%`
2. **PlaceholderAPI** (se instalada; via reflexão): `%...%`
3. **Fallback básico** (se PAPI ausente): `%player_name%`, `%player_uuid%`, `%player_world%`, `%player_health%`, etc.

### Exemplos

#### Avaliação segura (recomendada)

```java
ConditionContext ctx = new SimpleCtx(Map.of(
  "rank", "5",
  "perm_admin", String.valueOf(player.hasPermission("my.admin"))
));

core.conditions().evaluate(player, "%rank% >= 5 OR %perm_admin% == true", ctx)
  .thenAccept(ok -> {
    if (ok) {
      // cuidado: este callback pode não estar na main thread
    }
  });
```

#### Provider customizado

```java
core.conditions().registerVariableProvider("my", (player, ns, key, ctx) -> {
  if (key.equals("is_vip")) return player.hasPermission("vip") ? "true" : "false";
  return null;
});

// Uso:
core.conditions().evaluate(player, "%my:is_vip% == true", ctx);
```

#### Grupos

```java
core.conditions().setConditionGroups(Map.of(
  "vip", List.of("%player_level% >= 10", "%my:is_vip% == true")
));

core.conditions().evaluate(player, "vip", ctx);
```

### Threading (PlaceholderAPI)

- Se você está em um event handler (main thread) e sabe que não há PlaceholderAPI envolvida, `evaluateSync` é ok.
- Caso contrário, use sempre `evaluate(...)`.

