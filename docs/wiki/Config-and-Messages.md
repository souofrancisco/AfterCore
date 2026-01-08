## `ConfigService` e `MessageService`

### `ConfigService`

#### O que é

API simples para acesso padronizado a:

- `config.yml` (config principal do AfterCore)
- `messages.yml` (mensagens do AfterCore)

#### API

- `FileConfiguration main()`: representa o `config.yml`
- `YamlConfiguration messages()`: representa o `messages.yml`
- `reloadAll()`: recarrega ambos

#### Exemplo

```java
boolean debug = core.config().main().getBoolean("debug", false);
String prefix = core.config().messages().getString("prefix", "");
```

### `MessageService`

#### O que é

Padroniza envio/formatação de mensagens (cores com `&`, prefixo, etc.).

#### API

- `send(sender, path)`: busca um path no `messages.yml` e envia
- `sendRaw(sender, raw)`: envia string direta
- `format(raw)`: aplica formatação (ex.: cores)

#### Exemplo

Com o `messages.yml` padrão:

```yaml
prefix: "&8[&bAfterCore&8]&r "
errors:
  no-permission: "&cSem permissão."
```

Uso:

```java
core.messages().send(sender, "errors.no-permission");
core.messages().sendRaw(sender, "&aOK!");
```

### Validação e update de config (APIs públicas avançadas)

> Estes utilitários são públicos e podem ser usados por plugins, mas são pensados primariamente para o próprio core.

#### `ConfigValidator`

Valida uma `ConfigurationSection` e retorna `ValidationResult` com `ValidationError` (ERROR/WARNING).

```java
var validator = new com.afterlands.core.config.validate.ConfigValidator();
var result = validator.validate(getConfig());
if (result.hasErrors()) {
  getLogger().severe(result.formatMessages());
}
```

#### `ConfigUpdater`

Mescla defaults preservando valores do usuário (útil para configs versionadas).

```java
var updater = new com.afterlands.core.config.update.ConfigUpdater(getLogger());
boolean changed = updater.update(userConfig, defaultConfig);
if (changed) userConfig.save(file);
```

