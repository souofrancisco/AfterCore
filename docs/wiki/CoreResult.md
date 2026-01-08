## `CoreResult<T>` e `CoreErrorCode`

### O que é

`CoreResult<T>` é um tipo “Result” (estilo Rust) para representar sucesso/erro **sem exceções** em hot paths.

- Sucesso: `CoreResult.ok(value)` → `CoreResult.Ok<T>`
- Erro: `CoreResult.err(code, message[, cause])` → `CoreResult.Err<T>`

### Quando usar

- Falhas previsíveis (DB desabilitado, dependência ausente, argumento inválido)
- Fluxos onde exceções seriam overhead ou poluiriam logs

### Exemplos

#### Pattern matching (Java 21)

```java
CoreResult<Integer> result = loadCoins(uuid);

return switch (result) {
  case CoreResult.Ok(var coins) -> coins;
  case CoreResult.Err(var err) -> {
    getLogger().warning("Falha: " + err.message());
    yield 0;
  }
};
```

#### API funcional

```java
int coins = loadCoins(uuid)
  .map(c -> c + 10)
  .recover(err -> 0)
  .orElse(0);
```

#### Side effects

```java
result.ifOk(v -> cache.put(uuid, v));
result.ifErr(err -> getLogger().warning(err.toString()));
```

### `CoreErrorCode`

Enum com categorias padronizadas:

- `DEPENDENCY_MISSING`, `DB_DISABLED`, `DB_UNAVAILABLE`
- `TIMEOUT`, `INVALID_CONFIG`
- `NOT_ON_MAIN_THREAD`, `ON_MAIN_THREAD`
- `NOT_FOUND`, `FORBIDDEN`, `INVALID_ARGUMENT`
- `INTERNAL_ERROR`, `UNKNOWN`

