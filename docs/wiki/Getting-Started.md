## Getting Started (para desenvolvedores de plugins)

### Requisitos

- **Java**: 21
- **Servidor**: Spigot/Paper 1.8.8
- **Plugin**: AfterCore instalado no servidor
- **Dependências opcionais**:
  - **ProtocolLib** (recursos de packets/títulos dinâmicos/pipeline de chunk)
  - **PlaceholderAPI** (placeholders em condições, mensagens e inventários)

### Instalando no servidor

- **Passo 1**: compile o JAR

```bash
mvn clean package
```

- **Passo 2**: copie `target/AfterCore-<versão>.jar` para `plugins/` e reinicie.

### Consumindo a API no seu plugin

#### 1) `plugin.yml`

Se o seu plugin **precisa** do AfterCore para funcionar:

```yaml
depend: [AfterCore]
```

Se é opcional (você consegue degradar):

```yaml
softdepend: [AfterCore]
```

#### 2) Dependência Maven

No seu `pom.xml`, use `scope` como `provided` (o servidor fornece o JAR em runtime):

```xml
<dependency>
  <groupId>com.afterlands</groupId>
  <artifactId>AfterCore</artifactId>
  <version>1.0.1</version>
  <scope>provided</scope>
</dependency>
```

#### 3) Obtendo `AfterCoreAPI`

O padrão é via `ServicesManager` com cache:

```java
import com.afterlands.core.api.AfterCore;
import com.afterlands.core.api.AfterCoreAPI;

AfterCoreAPI core = AfterCore.get();
```

Se o AfterCore não estiver habilitado/registrado, `AfterCore.get()` lança `IllegalStateException`.

### Padrões recomendados

#### DB: sempre async

```java
core.sql().runAsync(conn -> {
    // JDBC aqui
});
```

#### PlaceholderAPI: somente main thread

- Para **condições**, use `core.conditions().evaluate(...)` (ele alterna para main thread quando necessário).
- Para seu próprio código com PlaceholderAPI: **agende com** `core.scheduler().runSync(...)`.

### Checklist “não faça”

- Não chame `core.sql().getConnection()` na main thread.
- Não dê `.join()`/`.get()` em `CompletableFuture` na main thread.
- Não exponha tipos internos sombreados do AfterCore nas suas APIs públicas.

