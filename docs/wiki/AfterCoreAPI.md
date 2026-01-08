## `AfterCore` e `AfterCoreAPI`

### Objetivo

O AfterCore é um **dependency plugin**. Plugins consumidores obtêm uma instância de `AfterCoreAPI` através do `ServicesManager` do Bukkit, sem depender de singletons do plugin.

### Como obter o core

```java
import com.afterlands.core.api.AfterCore;
import com.afterlands.core.api.AfterCoreAPI;

AfterCoreAPI core = AfterCore.get();
```

#### Erros comuns

- Se o AfterCore não estiver habilitado/registrado, `AfterCore.get()` lança `IllegalStateException`.
- Em cenários de reload/disable→enable, o cache interno pode ficar stale; o AfterCore invalida automaticamente em enable/disable, mas você pode chamar `AfterCore.invalidate()` se necessário.

### Serviços expostos

`AfterCoreAPI` é um agregador de serviços:

- `scheduler()` → `SchedulerService`
- `config()` → `ConfigService`
- `messages()` → `MessageService`
- `sql()` → `SqlService`
- `conditions()` → `ConditionService`
- `actions()` → `ActionService`
- `commands()` → `CommandService`
- `protocol()` → `ProtocolService`
- `diagnostics()` → `DiagnosticsService`
- `metrics()` → `MetricsService`
- `inventory()` → `InventoryService`

### Execução de actions (helper)

Além de expor `ActionService`, o core fornece helpers para execução de `ActionSpec`:

```java
import com.afterlands.core.actions.ActionSpec;

ActionSpec spec = core.actions().parse("message: &aOlá!");
if (spec != null) {
  core.executeAction(spec, player); // origin = player.getLocation()
}
```

#### Scopes

O `ActionSpec` carrega um `scope()`:

- `VIEWER`: executa apenas no viewer
- `NEARBY`: executa em players próximos a uma `origin` (raio configurável)
- `ALL`: executa em todos online

Você também pode informar uma origem customizada:

```java
core.executeAction(spec, viewer, originLocation);
```

### Threading

O contrato do core é “async-first”, mas actions/condições/placeholders podem exigir **main thread**. As implementações do AfterCore tentam ser seguras por padrão; veja [Threading & Performance](Threading-and-Performance.md).

