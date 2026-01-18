package com.afterlands.core.inventory.config;

import com.afterlands.core.config.ConfigService;
import com.afterlands.core.inventory.InventoryConfig;
import com.afterlands.core.inventory.action.ConfiguredAction;
import com.afterlands.core.inventory.animation.AnimationConfig;
import com.afterlands.core.inventory.click.ClickHandlers;
import com.afterlands.core.inventory.item.GuiItem;
import com.afterlands.core.inventory.pagination.PaginationConfig;
import com.afterlands.core.inventory.tab.TabConfig;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Gerenciador de configurações de inventários.
 *
 * <p>
 * Responsável por:
 * </p>
 * <ul>
 * <li>Carregar inventários de inventories.yml</li>
 * <li>Parse de itens, tabs, paginação, animações</li>
 * <li>Cache de configurações (invalidado em reload)</li>
 * <li>Validação de schema</li>
 * </ul>
 *
 * <p>
 * <b>Thread Safety:</b> Cache thread-safe (Caffeine), parsing sync.
 * </p>
 */
public class InventoryConfigManager {

    private final Plugin plugin;
    private final ConfigService configService;
    private final File inventoriesFile;
    private FileConfiguration inventoriesConfig;

    // Cache permanente de configurações (invalidado apenas em reload)
    private final Cache<String, InventoryConfig> configCache;

    // Templates de itens padrão (reutilizáveis)
    private final Map<String, GuiItem> defaultItems;

    public InventoryConfigManager(@NotNull Plugin plugin, @NotNull ConfigService configService) {
        this.plugin = plugin;
        this.configService = configService;
        this.inventoriesFile = new File(plugin.getDataFolder(), "inventories.yml");
        this.defaultItems = new HashMap<>();

        // Cache permanente (sem TTL, apenas invalidação manual)
        this.configCache = Caffeine.newBuilder()
                .maximumSize(100)
                .build();

        loadConfiguration();
    }

    /**
     * Carrega configuração de inventories.yml.
     *
     * <p>
     * Cria arquivo padrão se não existir.
     * </p>
     */
    public void loadConfiguration() {
        if (!inventoriesFile.exists()) {
            plugin.saveResource("inventories.yml", false);
        }

        inventoriesConfig = YamlConfiguration.loadConfiguration(inventoriesFile);
        configCache.invalidateAll();
        defaultItems.clear();

        // Carrega templates padrão
        loadDefaultItems();

        plugin.getLogger().info("Loaded inventories configuration (config-version: " +
                inventoriesConfig.getInt("config-version", 1) + ")");
    }

    /**
     * Carrega configurações de um arquivo YAML externo.
     *
     * @param file Arquivo YAML
     * @return Lista de InventoryConfig parseados
     */
    public List<InventoryConfig> loadConfigs(@NotNull File file) {
        if (!file.exists()) {
            plugin.getLogger().warning("External inventory file does not exist: " + file.getAbsolutePath());
            return Collections.emptyList();
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<InventoryConfig> loaded = new ArrayList<>();

        // 1. Carregar default-items locais do arquivo
        ConfigurationSection defaultItemsSection = config.getConfigurationSection("default-items");
        if (defaultItemsSection != null) {
            for (String key : defaultItemsSection.getKeys(false)) {
                ConfigurationSection itemSection = defaultItemsSection.getConfigurationSection(key);
                if (itemSection != null) {
                    GuiItem item = parseGuiItem(key, -1, itemSection);
                    defaultItems.put(key, item);
                    plugin.getLogger().fine("Loaded external default item: " + key);
                }
            }
        }

        // 2. Carregar inventários (tenta 'inventories' section, se falhar, tenta root)
        ConfigurationSection section = config.getConfigurationSection("inventories");
        boolean isRoot = (section == null);
        if (isRoot) {
            section = config;
        }

        Set<String> keys = section.getKeys(false);
        for (String key : keys) {
            // Se estiver na root, ignora chaves reservadas
            if (isRoot && (key.equals("default-items") || key.equals("config-version"))) {
                continue;
            }

            ConfigurationSection invSection = section.getConfigurationSection(key);
            if (invSection == null) {
                continue;
            }

            // Heurística básica: valida se parece um inventário (tem 'title' ou 'size')
            if (isRoot
                    && !(invSection.contains("title") || invSection.contains("size") || invSection.contains("items"))) {
                continue;
            }

            try {
                InventoryConfig invConfig = parseInventoryConfig(key, invSection);
                loaded.add(invConfig);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to parse external inventory '" + key + "' from " + file.getName(), e);
            }
        }

        plugin.getLogger().info("Loaded " + loaded.size() + " external inventories from " + file.getName());
        return loaded;
    }

    /**
     * Carrega templates de itens padrão.
     */
    private void loadDefaultItems() {
        ConfigurationSection section = inventoriesConfig.getConfigurationSection("default-items");
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            ConfigurationSection itemSection = section.getConfigurationSection(key);
            if (itemSection != null) {
                GuiItem item = parseGuiItem(key, -1, itemSection);
                defaultItems.put(key, item);
                plugin.getLogger().fine("Loaded default item template: " + key);
            }
        }
    }

    /**
     * Obtém configuração de um inventário.
     *
     * <p>
     * Cache hit: retorna imediatamente.
     * Cache miss: parse e cache.
     * </p>
     *
     * @param inventoryId ID do inventário
     * @return InventoryConfig ou null se não existir
     */
    @Nullable
    public InventoryConfig getInventoryConfig(@NotNull String inventoryId) {
        return configCache.get(inventoryId, this::loadInventoryConfig);
    }

    /**
     * Obtém um item padrão pelo nome.
     *
     * @param key Nome do item padrão
     * @return GuiItem ou null se não existir
     */
    @Nullable
    public GuiItem getDefaultItem(@NotNull String key) {
        return defaultItems.get(key);
    }

    /**
     * Obtém todos os itens padrão.
     *
     * @return Mapa imutável de itens padrão
     */
    @NotNull
    public Map<String, GuiItem> getAllDefaultItems() {
        return Collections.unmodifiableMap(defaultItems);
    }

    /**
     * Carrega configuração de inventário (cache miss).
     */
    @Nullable
    private InventoryConfig loadInventoryConfig(@NotNull String inventoryId) {
        ConfigurationSection section = inventoriesConfig.getConfigurationSection("inventories." + inventoryId);
        if (section == null) {
            plugin.getLogger().warning("Inventory not found: " + inventoryId);
            return null;
        }

        try {
            return parseInventoryConfig(inventoryId, section);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to parse inventory: " + inventoryId, e);
            return null;
        }
    }

    /**
     * Parse de InventoryConfig completo.
     */
    @NotNull
    private InventoryConfig parseInventoryConfig(@NotNull String id, @NotNull ConfigurationSection section) {
        String title = section.getString("title", "");
        int sizeRaw = section.getInt("size", 3);

        // Convert slot-based size to rows (AfterBlockAnimations uses slot count,
        // AfterCore uses rows)
        int size = sizeRaw;
        if (sizeRaw > 6) {
            // Assume it's slot count, convert to rows
            size = sizeRaw / 9;
            if (size < 1)
                size = 1;
            if (size > 6)
                size = 6;
            plugin.getLogger()
                    .fine("Converted size from " + sizeRaw + " slots to " + size + " rows for inventory: " + id);
        }

        // Parse items
        List<GuiItem> items = parseItems(section.getConfigurationSection("items"));

        // Parse tabs
        List<TabConfig> tabs = parseTabs(section);

        // Parse pagination
        PaginationConfig pagination = parsePagination(section.getConfigurationSection("pagination"));

        // Parse animations
        List<AnimationConfig> animations = parseAnimations(section.getConfigurationSection("animations"));

        // Parse persistence
        InventoryConfig.PersistenceConfig persistence = parsePersistence(
                section.getConfigurationSection("persistence"));

        // Shared flag
        boolean shared = section.getBoolean("shared", false);

        // Title update interval (0 = disabled)
        int titleUpdateInterval = section.getInt("title_update_interval", 0);

        // Metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("raw_section", section);

        // Parse variant-items
        Map<String, GuiItem> variantItems = new HashMap<>();
        if (section.contains("variant-items")) {
            ConfigurationSection variantsSection = section.getConfigurationSection("variant-items");
            if (variantsSection != null) {
                for (String key : variantsSection.getKeys(false)) {
                    ConfigurationSection itemSection = variantsSection.getConfigurationSection(key);
                    if (itemSection != null) {
                        // Variants always have slot -1
                        variantItems.put(key, parseGuiItem(key, -1, itemSection));
                    }
                }
            }
        }

        return new InventoryConfig(id, title, size, items, tabs, pagination, animations, persistence, shared,
                titleUpdateInterval, metadata, variantItems);
    }

    /**
     * Parse de itens.
     */
    @NotNull
    private List<GuiItem> parseItems(@Nullable ConfigurationSection section) {
        if (section == null) {
            return List.of();
        }

        List<GuiItem> items = new ArrayList<>();

        for (String slotKey : section.getKeys(false)) {
            ConfigurationSection itemSection = section.getConfigurationSection(slotKey);
            if (itemSection == null) {
                continue;
            }

            // Template items (non-numeric keys like "animation-item", "placement-item",
            // etc.)
            // are parsed with slot=-1 so they're available for template lookup
            boolean isTemplateItem = slotKey.contains("-") && !slotKey.matches("^\\d+(-\\d+)?$");
            if (isTemplateItem) {
                // Parse as template item with slot=-1 (not rendered, only for lookup)
                GuiItem item = parseGuiItem(slotKey, -1, itemSection);
                items.add(item);
                plugin.getLogger().fine("Parsed template item: " + slotKey + " with type: " + item.getType());
                continue;
            }

            // Parse slots (pode ser range: "0-8", lista: "0;4;8", ou único: "13")
            List<Integer> slots;
            try {
                slots = parseSlotRange(slotKey);
            } catch (NumberFormatException e) {
                plugin.getLogger().fine("Skipping non-numeric item key: " + slotKey);
                continue;
            }

            for (int slot : slots) {
                GuiItem item = parseGuiItem(slotKey, slot, itemSection);
                items.add(item);
            }
        }

        return items;
    }

    /**
     * Parse de slot range.
     *
     * <p>
     * Exemplos:
     * </p>
     * <ul>
     * <li>"13" → [13]</li>
     * <li>"0-8" → [0,1,2,3,4,5,6,7,8]</li>
     * <li>"0;4;8" → [0,4,8]</li>
     * <li>"0-8;36-44" → [0-8, 36-44]</li>
     * </ul>
     */
    @NotNull
    private List<Integer> parseSlotRange(@NotNull String slotKey) {
        List<Integer> slots = new ArrayList<>();

        for (String part : slotKey.split(";")) {
            if (part.contains("-")) {
                String[] range = part.split("-");
                int start = Integer.parseInt(range[0].trim());
                int end = Integer.parseInt(range[1].trim());
                for (int i = start; i <= end; i++) {
                    slots.add(i);
                }
            } else {
                try {
                    slots.add(Integer.parseInt(part.trim()));
                } catch (NumberFormatException e) {
                    // Pode ser keyword como "top", "bottom", etc.
                    // Implementação futura
                }
            }
        }

        return slots;
    }

    /**
     * Parse de GuiItem.
     */
    @NotNull
    private GuiItem parseGuiItem(@NotNull String key, int slot, @NotNull ConfigurationSection section) {
        GuiItem.Builder builder = new GuiItem.Builder();

        builder.slot(slot);
        builder.type(section.getString("type", key));

        // Track if we inherited from default item
        GuiItem inheritedFrom = null;

        // Material
        String materialStr = section.getString("material", "STONE");

        // Handle item: references to default items
        if (materialStr.startsWith("item:")) {
            String itemRef = materialStr.substring(5);
            GuiItem defaultItem = defaultItems.get(itemRef);
            if (defaultItem != null) {
                inheritedFrom = defaultItem;
                // Copy ALL properties from default item first
                builder.material(defaultItem.getMaterial());
                builder.data(defaultItem.getData());
                builder.name(defaultItem.getName());
                builder.lore(defaultItem.getLore());
                builder.actions(defaultItem.getActions());
                builder.enabled(defaultItem.isEnabled());
                builder.enchanted(defaultItem.isEnchanted());
                builder.hideFlags(defaultItem.isHideFlags());
                // Copy head properties
                builder.headType(defaultItem.getHeadType());
                builder.headValue(defaultItem.getHeadValue());
                if (defaultItem.hasClickHandlers()) {
                    builder.clickHandlers(defaultItem.getClickHandlers());
                }
                plugin.getLogger().fine("Resolved item reference: " + itemRef + " -> " + defaultItem.getMaterial());
            } else {
                plugin.getLogger().warning(
                        "Invalid item reference: " + materialStr + " in item " + key + " (not found in default-items)");
                builder.material(Material.STONE);
            }
        } else if (materialStr.toLowerCase().startsWith("head:")) {
            // Shorthand for player head
            builder.material(Material.SKULL_ITEM);
            builder.data((short) 3);

            String headVal = materialStr.substring(5).trim();
            if ("self".equalsIgnoreCase(headVal)) {
                builder.headType(GuiItem.HeadType.SELF);
            } else if (headVal.toLowerCase().startsWith("base64:")) {
                builder.headType(GuiItem.HeadType.BASE64);
                builder.headValue(headVal.substring(7).trim());
            } else if (headVal.startsWith("ey")) {
                // Heuristic: starts with "ey" -> Base64 encoded JSON
                builder.headType(GuiItem.HeadType.BASE64);
                builder.headValue(headVal);
            } else {
                // Assume player name
                builder.headType(GuiItem.HeadType.PLAYER);
                builder.headValue(headVal);
            }
        } else {
            try {
                Material material = Material.valueOf(materialStr.toUpperCase());
                builder.material(material);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid material: " + materialStr + " in item " + key);
                builder.material(Material.STONE);
            }
        }

        // Data (override if specified)
        if (section.contains("data")) {
            builder.data((short) section.getInt("data", 0));
        }

        // Amount (override if specified)
        if (section.contains("amount")) {
            builder.amount(section.getInt("amount", 1));
        }

        // Name (override if specified)
        if (section.contains("name")) {
            builder.name(section.getString("name", ""));
        }

        // Lore (override if specified)
        if (section.contains("lore")) {
            builder.lore(section.getStringList("lore"));
        }

        // Flags (override if specified)
        if (section.contains("enabled")) {
            builder.enabled(section.getBoolean("enabled", true));
        }
        if (section.contains("enchanted")) {
            builder.enchanted(section.getBoolean("enchanted", false));
        }
        if (section.contains("hide-flags")) {
            builder.hideFlags(section.getBoolean("hide-flags", false));
        }

        // Parse duplicate: "all" or list of slots
        if (section.contains("duplicate")) {
            String duplicateStr = section.getString("duplicate", "");
            if ("all".equalsIgnoreCase(duplicateStr)) {
                // Special marker - will be resolved later in renderItems
                builder.duplicateSlots(List.of(-1)); // -1 = fill all empty
            } else {
                // Parse as slot list or range
                try {
                    List<Integer> dupSlots = parseSlotRange(duplicateStr);
                    builder.duplicateSlots(dupSlots);
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Invalid duplicate value: " + duplicateStr + " in item " + key);
                }
            }
            plugin.getLogger().fine("Parsed item " + key + " duplicate: " + section.getString("duplicate"));
        }

        // Actions (override if specified, merge with inherited)
        List<String> actions = new ArrayList<>();
        if (inheritedFrom != null && !inheritedFrom.getActions().isEmpty()) {
            actions.addAll(inheritedFrom.getActions());
        }
        if (section.contains("actions")) {
            // Override if new actions specified
            actions = section.getStringList("actions");
            builder.actions(actions);
        }

        // Support for "action" (singular) alias - common mistake/alternative
        if (section.contains("action")) {
            List<String> extraActions;
            if (section.isList("action")) {
                extraActions = section.getStringList("action");
            } else {
                extraActions = List.of(section.getString("action"));
            }

            // If we already have actions (from "actions" key), append checking for
            // duplicates/order
            // But usually it's one or the other. If both exist, we append.
            if (actions.isEmpty()) {
                builder.actions(extraActions);
            } else {
                // Create mutable copy if needed and append
                List<String> combined = new ArrayList<>(actions);
                combined.addAll(extraActions);
                builder.actions(combined);
            }
        }

        // Click handlers por tipo
        ClickHandlers.Builder clickBuilder = ClickHandlers.builder();
        boolean hasClickHandlers = false;

        // Default actions (compatibilidade) - use merged actions
        if (!actions.isEmpty()) {
            clickBuilder.defaultActions(actions);
            hasClickHandlers = true;
        }

        // Actions por tipo de click
        // Actions por tipo de click
        if (section.contains("on_left_click")) {
            clickBuilder.onLeftClick(parseAction(section, "on_left_click"));
            hasClickHandlers = true;
        }
        if (section.contains("on_right_click")) {
            clickBuilder.onRightClick(parseAction(section, "on_right_click"));
            hasClickHandlers = true;
        }
        if (section.contains("on_shift_left_click")) {
            clickBuilder.onShiftLeftClick(parseAction(section, "on_shift_left_click"));
            hasClickHandlers = true;
        }
        if (section.contains("on_shift_right_click")) {
            clickBuilder.onShiftRightClick(parseAction(section, "on_shift_right_click"));
            hasClickHandlers = true;
        }
        if (section.contains("on_middle_click")) {
            clickBuilder.onMiddleClick(parseAction(section, "on_middle_click"));
            hasClickHandlers = true;
        }
        if (section.contains("on_double_click")) {
            clickBuilder.onDoubleClick(parseAction(section, "on_double_click"));
            hasClickHandlers = true;
        }
        if (section.contains("on_drop")) {
            clickBuilder.onDrop(parseAction(section, "on_drop"));
            hasClickHandlers = true;
        }
        if (section.contains("on_control_drop")) {
            clickBuilder.onControlDrop(parseAction(section, "on_control_drop"));
            hasClickHandlers = true;
        }
        if (section.contains("on_number_key")) {
            clickBuilder.onNumberKey(parseAction(section, "on_number_key"));
            hasClickHandlers = true;
        }

        // Se tem click handlers, build e adiciona ao item
        if (hasClickHandlers) {
            builder.clickHandlers(clickBuilder.build());
        }

        if (section.contains("head")) {
            String headVal = section.getString("head");
            if ("self".equalsIgnoreCase(headVal)) {
                builder.headType(GuiItem.HeadType.SELF);
            } else if (headVal.startsWith("player:")) {
                builder.headType(GuiItem.HeadType.PLAYER);
                builder.headValue(headVal.substring(7));
            } else if (headVal.startsWith("base64:")) {
                builder.headType(GuiItem.HeadType.BASE64);
                builder.headValue(headVal.substring(7));
            } else if (headVal.startsWith("ey")) {
                // Heuristic: starts with "ey" -> Base64 encoded JSON
                builder.headType(GuiItem.HeadType.BASE64);
                builder.headValue(headVal);
            } else {
                // Assume player name or raw value
                builder.headType(GuiItem.HeadType.PLAYER);
                builder.headValue(headVal);
            }
        }

        // NBT tags
        ConfigurationSection nbtSection = section.getConfigurationSection("nbt");
        if (nbtSection != null) {
            Map<String, String> nbtTags = new HashMap<>();
            for (String nbtKey : nbtSection.getKeys(false)) {
                nbtTags.put(nbtKey, nbtSection.getString(nbtKey));
            }
            builder.nbtTags(nbtTags);
        }

        // Variants (refs)
        if (section.contains("variants")) {
            builder.variantRefs(section.getStringList("variants"));
        }

        // Inline variants (variant0, variant1, etc.)
        for (String variantKey : section.getKeys(false)) {
            if (variantKey.startsWith("variant") && variantKey.length() > 7
                    && Character.isDigit(variantKey.charAt(7))) {
                ConfigurationSection varSection = section.getConfigurationSection(variantKey);
                if (varSection != null) {
                    builder.addInlineVariant(parseGuiItem(variantKey, builder.build().getSlot(), varSection));
                }
            }
        }

        // Drag
        builder.allowDrag(section.getBoolean("allow-drag", false));
        builder.dragAction(section.getString("drag-action"));

        // Cacheable
        builder.cacheable(section.getBoolean("cacheable", true));

        // Parse animations
        List<AnimationConfig> itemAnimations = parseItemAnimations(section);
        if (!itemAnimations.isEmpty()) {
            builder.animations(itemAnimations);
        }

        // Parse conditions
        if (section.contains("view-conditions")) {
            builder.viewConditions(section.getStringList("view-conditions"));
        }
        if (section.contains("click-conditions")) {
            builder.clickConditions(section.getStringList("click-conditions"));
        }

        // Parse dynamic placeholders (list of placeholder keys that invalidate cache)
        if (section.contains("dynamic-placeholders")) {
            builder.dynamicPlaceholders(section.getStringList("dynamic-placeholders"));
        }

        // Parse enchantments (map of enchantment name -> level)
        ConfigurationSection enchSection = section.getConfigurationSection("enchantments");
        if (enchSection != null) {
            Map<String, Integer> enchantments = new HashMap<>();
            for (String enchName : enchSection.getKeys(false)) {
                int level = enchSection.getInt(enchName, 1);
                enchantments.put(enchName.toUpperCase(), level);
            }
            builder.enchantments(enchantments);
        }

        // Parse custom-model-data (also check in nbt section for convenience)
        if (section.contains("custom-model-data")) {
            builder.customModelData(section.getInt("custom-model-data", -1));
        }

        return builder.build();
    }

    /**
     * Parse de tabs.
     *
     * <p>
     * Formato esperado em YAML:
     * </p>
     * 
     * <pre>
     * tabs:
     *   - id: "weapons"
     *     display-name: "&6Armas"
     *     icon: IRON_SWORD
     *     default: true
     *     slots: [10,11,12,13,14,15,16]
     *     layout:
     *       - "xxxxxxxxx"
     *       - "xOOOOOOOx"
     *     items:
     *       "10":
     *         material: DIAMOND_SWORD
     *         name: "&bEspada"
     * </pre>
     */
    @NotNull
    private List<TabConfig> parseTabs(@Nullable ConfigurationSection section) {
        if (section == null) {
            return List.of();
        }

        List<TabConfig> tabs = new ArrayList<>();
        List<Map<?, ?>> tabsList = section.getMapList("tabs");

        if (tabsList.isEmpty()) {
            return List.of();
        }

        boolean hasDefault = false;

        for (Map<?, ?> tabMap : tabsList) {
            try {
                String tabId = (String) tabMap.get("id");
                if (tabId == null || tabId.isBlank()) {
                    plugin.getLogger().warning("Tab missing 'id', skipping");
                    continue;
                }

                Object displayNameObj = tabMap.get("display-name");
                String displayName = displayNameObj != null ? String.valueOf(displayNameObj) : tabId;

                // Parse icon material
                Object iconObj = tabMap.get("icon");
                String iconStr = iconObj != null ? String.valueOf(iconObj) : "PAPER";
                Material icon;
                try {
                    icon = Material.valueOf(iconStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid icon material: " + iconStr + " for tab " + tabId);
                    icon = Material.PAPER;
                }

                // Parse slots
                List<Integer> slots = new ArrayList<>();
                Object slotsObj = tabMap.get("slots");
                if (slotsObj instanceof List<?>) {
                    for (Object slotObj : (List<?>) slotsObj) {
                        if (slotObj instanceof Number) {
                            slots.add(((Number) slotObj).intValue());
                        }
                    }
                }

                // Parse layout
                List<String> layout = new ArrayList<>();
                Object layoutObj = tabMap.get("layout");
                if (layoutObj instanceof List<?>) {
                    for (Object lineObj : (List<?>) layoutObj) {
                        if (lineObj instanceof String) {
                            layout.add((String) lineObj);
                        }
                    }
                }

                // Parse tab-specific items
                List<GuiItem> tabItems = new ArrayList<>();
                Object itemsObj = tabMap.get("items");
                if (itemsObj instanceof Map<?, ?>) {
                    ConfigurationSection itemsSection = toConfigSection((Map<?, ?>) itemsObj);
                    tabItems = parseItems(itemsSection);
                }

                Object defaultObj = tabMap.get("default");
                boolean isDefault = (defaultObj instanceof Boolean && (Boolean) defaultObj);
                if (isDefault && hasDefault) {
                    plugin.getLogger().warning("Multiple default tabs found. Only first will be used.");
                    isDefault = false;
                }
                if (isDefault) {
                    hasDefault = true;
                }

                TabConfig tabConfig = new TabConfig(tabId, displayName, icon, slots, layout, tabItems, isDefault);
                tabs.add(tabConfig);

                plugin.getLogger().fine("Parsed tab: " + tabId + " (default: " + isDefault + ")");

            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to parse tab", e);
            }
        }

        // Se nenhuma tab default, marca primeira como default
        if (!tabs.isEmpty() && !hasDefault) {
            TabConfig first = tabs.get(0);
            tabs.set(0, new TabConfig(first.tabId(), first.displayName(), first.icon(),
                    first.slots(), first.layout(), first.items(), true));
            plugin.getLogger().fine("No default tab specified, using first tab: " + first.tabId());
        }

        return tabs;
    }

    /**
     * Parse de pagination.
     *
     * <p>
     * Formato esperado em YAML:
     * </p>
     * 
     * <pre>
     * pagination:
     *   mode: HYBRID  # NATIVE_ONLY, LAYOUT_ONLY, HYBRID
     *   items-per-page: 21
     *   show-navigation: true
     *   layout:
     *     - "xxxxxxxxx"
     *     - "xOOOOOOOx"
     *     - "xOOOOOOOx"
     *     - "xxxxNxxxx"
     *   navigation-slots:
     *     next: 40
     *     prev: 38
     *   pagination-slots: [10,11,12,13,14,15,16,19,20,21,22,23,24,25]
     * </pre>
     */
    @Nullable
    private PaginationConfig parsePagination(@Nullable ConfigurationSection section) {
        if (section == null) {
            return null;
        }

        // Parse mode
        String modeStr = section.getString("mode", "HYBRID").toUpperCase();
        PaginationConfig.PaginationMode mode;
        try {
            mode = PaginationConfig.PaginationMode.valueOf(modeStr);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid pagination mode: " + modeStr + ", defaulting to HYBRID");
            mode = PaginationConfig.PaginationMode.HYBRID;
        }

        // Parse layout
        List<String> layout = section.getStringList("layout");

        // Validação de layout (cada linha deve ter 9 chars)
        if (!layout.isEmpty()) {
            for (int i = 0; i < layout.size(); i++) {
                String line = layout.get(i);
                if (line.length() != 9) {
                    plugin.getLogger().warning(
                            "Invalid layout line " + i + " (expected 9 chars, got " + line.length() + "): " + line);
                }
            }
        }

        // Parse pagination slots
        List<Integer> paginationSlots = new ArrayList<>();
        if (section.contains("pagination-slots")) {
            List<?> slotsList = section.getList("pagination-slots");
            if (slotsList != null) {
                for (Object obj : slotsList) {
                    if (obj instanceof Number) {
                        paginationSlots.add(((Number) obj).intValue());
                    }
                }
            }
        }

        // Parse navigation slots (prev, next)
        ConfigurationSection navSection = section.getConfigurationSection("navigation-slots");
        if (navSection != null) {
            int prevSlot = navSection.getInt("prev", -1);
            int nextSlot = navSection.getInt("next", -1);

            if (prevSlot >= 0 && nextSlot >= 0) {
                // Add to pagination slots se não estiverem lá
                if (!paginationSlots.contains(prevSlot)) {
                    paginationSlots.add(prevSlot);
                }
                if (!paginationSlots.contains(nextSlot)) {
                    paginationSlots.add(nextSlot);
                }
            }
        }

        // Items per page
        int itemsPerPage = section.getInt("items-per-page", 9);

        // Show navigation
        boolean showNavigation = section.getBoolean("show-navigation", true);

        PaginationConfig config = new PaginationConfig(mode, layout, paginationSlots, itemsPerPage, showNavigation);

        plugin.getLogger()
                .fine("Parsed pagination: mode=" + mode + ", itemsPerPage=" + itemsPerPage +
                        ", layout=" + layout.size() + " lines, slots=" + paginationSlots.size());

        return config;
    }

    /**
     * Parse de animations (global do inventário).
     *
     * <p>
     * Formato YAML esperado:
     * </p>
     * 
     * <pre>
     * animations:
     *   - id: "global_pulse"
     *     type: FRAME_BASED
     *     interval: 10
     *     loop: true
     *     frames:
     *       - material: DIAMOND
     *         duration: 5
     * </pre>
     *
     * @param section ConfigurationSection de animations
     * @return Lista de AnimationConfig
     */
    @NotNull
    private List<AnimationConfig> parseAnimations(@Nullable ConfigurationSection section) {
        if (section == null) {
            return List.of();
        }

        List<AnimationConfig> animations = new ArrayList<>();

        // Animations podem ser lista ou mapa
        if (section.isList("animations")) {
            List<?> animList = section.getList("animations");
            if (animList != null) {
                for (Object animObj : animList) {
                    if (animObj instanceof Map<?, ?>) {
                        try {
                            ConfigurationSection animSection = toConfigSection((Map<?, ?>) animObj);
                            AnimationConfig anim = AnimationConfig.fromConfig(animSection);
                            if (anim.isValid()) {
                                animations.add(anim);
                            } else {
                                plugin.getLogger().warning("Invalid animation config (skipped): " + anim.animationId());
                            }
                        } catch (Exception e) {
                            plugin.getLogger().warning("Failed to parse animation: " + e.getMessage());
                        }
                    }
                }
            }
        }

        if (plugin.getLogger().isLoggable(java.util.logging.Level.FINE) && !animations.isEmpty()) {
            plugin.getLogger().fine("Parsed " + animations.size() + " global animations");
        }

        return animations;
    }

    /**
     * Parse de animations de um item específico.
     *
     * <p>
     * Formato YAML esperado:
     * </p>
     * 
     * <pre>
     * items:
     *   "13":
     *     material: DIAMOND_SWORD
     *     animations:
     *       - id: "pulse"
     *         type: FRAME_BASED
     *         interval: 10
     *         loop: true
     *         frames:
     *           - material: DIAMOND_SWORD
     *             duration: 5
     *             enchanted: true
     *           - material: DIAMOND_SWORD
     *             duration: 5
     *             enchanted: false
     * </pre>
     *
     * @param itemSection ConfigurationSection do item
     * @return Lista de AnimationConfig
     */
    @NotNull
    public List<AnimationConfig> parseItemAnimations(@NotNull ConfigurationSection itemSection) {
        if (!itemSection.contains("animations")) {
            return List.of();
        }

        List<AnimationConfig> animations = new ArrayList<>();
        List<?> animList = itemSection.getList("animations");

        if (animList != null) {
            for (Object animObj : animList) {
                if (animObj instanceof Map<?, ?>) {
                    try {
                        ConfigurationSection animSection = toConfigSection((Map<?, ?>) animObj);
                        AnimationConfig anim = AnimationConfig.fromConfig(animSection);
                        if (anim.isValid()) {
                            animations.add(anim);
                        } else {
                            plugin.getLogger().warning("Invalid animation config (skipped): " + anim.animationId());
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to parse item animation: " + e.getMessage());
                    }
                }
            }
        }

        return animations;
    }

    /**
     * Parse de ConfiguredAction (lista ou objeto complexo).
     */
    @NotNull
    private ConfiguredAction parseAction(@NotNull ConfigurationSection section, @NotNull String key) {
        if (section.isList(key)) {
            return ConfiguredAction.simple(section.getStringList(key));
        }

        if (section.isConfigurationSection(key)) {
            ConfigurationSection actionSection = section.getConfigurationSection(key);
            if (actionSection != null) {
                List<String> conditions = actionSection.getStringList("conditions");
                List<String> success;
                List<String> fail;

                // Success actions (can be "success" or "actions")
                if (actionSection.contains("success")) {
                    success = actionSection.getStringList("success");
                } else {
                    success = actionSection.getStringList("actions");
                }

                // Fail actions
                fail = actionSection.getStringList("fail");

                return new ConfiguredAction(conditions, success, fail);
            }
        }

        // Single string action
        if (section.isString(key)) {
            return ConfiguredAction.simple(List.of(section.getString(key)));
        }

        return ConfiguredAction.simple(List.of());
    }

    /**
     * Parse de persistence config.
     */
    @NotNull
    private InventoryConfig.PersistenceConfig parsePersistence(@Nullable ConfigurationSection section) {
        if (section == null) {
            return InventoryConfig.PersistenceConfig.disabled();
        }

        boolean enabled = section.getBoolean("enabled", false);
        boolean autoSave = section.getBoolean("auto-save", true);
        int saveInterval = section.getInt("save-interval-seconds", 30);

        return new InventoryConfig.PersistenceConfig(enabled, autoSave, saveInterval);
    }

    /**
     * Recarrega configurações.
     */
    public void reload() {
        loadConfiguration();
    }

    /**
     * Limpa o cache de configurações.
     */
    public void clearCache() {
        configCache.invalidateAll();
    }

    /**
     * Invalida uma configuração específica do cache.
     * 
     * @param inventoryId ID do inventário a invalidar
     */
    public void invalidate(@NotNull String inventoryId) {
        configCache.invalidate(inventoryId);
    }

    /**
     * Verifica se inventário existe.
     */
    public boolean hasInventory(@NotNull String inventoryId) {
        return inventoriesConfig.contains("inventories." + inventoryId);
    }

    /**
     * Lista todos os IDs de inventários registrados.
     */
    @NotNull
    public Set<String> getInventoryIds() {
        ConfigurationSection section = inventoriesConfig.getConfigurationSection("inventories");
        return section != null ? section.getKeys(false) : Set.of();
    }

    /**
     * Converte Map para ConfigurationSection.
     *
     * <p>
     * Helper para parsing de tabs/items aninhados.
     * </p>
     */
    @NotNull
    private ConfigurationSection toConfigSection(@NotNull Map<?, ?> map) {
        org.bukkit.configuration.MemoryConfiguration memoryConfig = new org.bukkit.configuration.MemoryConfiguration();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            memoryConfig.set(key, value);
        }
        return memoryConfig;
    }
}
