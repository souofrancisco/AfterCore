## `CommandService`

### Status

O `CommandService` atualmente é um **stub**: ele define o contrato público e armazena handlers registrados, mas **a integração completa** (CommandMap, parsing de args, help, permissões, subcommands) ainda está em evolução.

### API

- `register(Object commandHandler)`
  - Registra um handler (futuro: via reflexão/anotações).
- `unregisterAll()`

### Anotações disponíveis

Mesmo antes da implementação completa, o AfterCore já expõe as anotações públicas:

- `@com.afterlands.core.commands.annotations.Command`
  - `name()`, `aliases()`
- `@com.afterlands.core.commands.annotations.Subcommand`
  - `value()`
- `@com.afterlands.core.commands.annotations.Permission`
  - `value()`

### Exemplo (estrutura alvo)

```java
import com.afterlands.core.commands.annotations.Command;
import com.afterlands.core.commands.annotations.Subcommand;
import com.afterlands.core.commands.annotations.Permission;

@Command(name = "mycmd", aliases = {"mc"})
@Permission("myplugin.admin")
public final class MyCommand {

  @Subcommand("reload")
  public void reload(org.bukkit.command.CommandSender sender) {
    sender.sendMessage("Reload!");
  }
}

// Registro (hoje: apenas armazena; futuro: registrará no Bukkit)
core.commands().register(new MyCommand());
```

