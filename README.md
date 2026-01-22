<div align="center">
  <img src="https://afterlands.com/assets/images/logo.png" width="600" alt="Afterlands Logo">

  # AfterCore

  **The Core Library for the AfterLands Ecosystem**

  [![Website](https://img.shields.io/website?label=afterlands.com&style=for-the-badge&url=https://afterlands.com/)](https://afterlands.com/)
  [![Discord](https://img.shields.io/discord/1072151613805953106?color=5865F2&label=Community&logo=discord&logoColor=white&style=for-the-badge)](https://discord.gg/ySZjhRFyCy)
  [![Status](https://img.shields.io/badge/Status-Stable-success?style=for-the-badge)](https://github.com/AfterLands/AfterCore)

  ---
  
  <p align="center">
    <strong>AfterCore</strong> is a high-performance library plugin for <strong>Minecraft 1.8.8 (Java 21)</strong>.
    <br>
    It creates a shared infrastructure (Database, Inventory Framework, Command Framework, Actions, Conditions) to eliminate code duplication across all AfterLands plugins while maintaining <strong>20 TPS at high concurrent players</strong>.
  </p>
</div>

## 🌟 Key Features

### 📦 Inventory Framework
A powerful YAML-based GUI system designed for performance.
- **Smart Caching** & Async Loading
- **Pagination**: Native, Layout, or Hybrid modes
- **Animations**: Frame-based item animations
- **Variants System**: Conditional items (dynamic state)
- **Extended Actions**: Logic branches (success/fail) directly in YAML

### ⚡ Command Framework
Annotation-driven command handling.
- **Param Injection**: Auto-resolve logical dependencies
- **Rate Limiting**: Configurable `@Cooldown`
- **Aliases**: Dynamic runtime aliases & `@Alias` support
- **Async Execution**: `ctx.runAsync()` support

### 🔧 Core Services
- **ActionService**: Unified string-based action parsing (`open_panel`, `sound`, `console_command`, etc.)
- **ConditionService**: Logical expression evaluation (`%money% >= 100`)
- **Persistence**: Efficient data handling with `CompletableFuture`

## 📚 Documentation

The complete documentation is available in our **[GitHub Wiki](https://github.com/AfterLands/AfterCore/wiki)**.

*   **[Getting Started](https://github.com/AfterLands/AfterCore/wiki/Getting-Started)**
*   **[Inventory Framework](https://github.com/AfterLands/AfterCore/wiki/InventoryService)**
*   **[Command Framework](https://github.com/AfterLands/AfterCore/wiki/CommandService)**
*   **[Core APIs](https://github.com/AfterLands/AfterCore/wiki/AfterCoreAPI)**

## 🛠️ Tech Stack

![Java](https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Minecraft](https://img.shields.io/badge/Minecraft-333333?style=for-the-badge&logo=minecraft&logoColor=25D366)
![Maven](https://img.shields.io/badge/Maven-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white)

## 🚀 Usage

### Building
```bash
mvn clean package
```

### Dependency (Maven)
```xml
<dependency>
    <groupId>com.afterlands</groupId>
    <artifactId>aftercore</artifactId>
    <version>1.3.1</version>
    <scope>provided</scope>
</dependency>
```

### Getting the API
```java
AfterCoreAPI core = AfterCore.get();
InventoryService inv = core.inventory();
```

---

<div align="center">
  <sub><strong>Developed with ❤️ by the AfterLands Team.</strong></sub>
</div>
