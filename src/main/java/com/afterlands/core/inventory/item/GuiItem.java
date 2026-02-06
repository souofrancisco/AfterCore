package com.afterlands.core.inventory.item;

import com.afterlands.core.inventory.InventoryContext;
import com.afterlands.core.inventory.animation.AnimationConfig;
import com.afterlands.core.inventory.click.ClickHandler;
import com.afterlands.core.inventory.click.ClickHandlers;
//import de.tr7zw.nbtapi.NBTItem; // TODO: Fase 2 - NBTAPI integration
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Representação de um item de GUI configurável.
 *
 * <p>
 * Extensão do AfterBlockAnimations.GuiItem com features adicionais:
 * - NBTAPI support
 * - Animações
 * - Drag-and-drop
 * - Cache inteligente
 * </p>
 *
 * <p>
 * Imutável para thread safety.
 * </p>
 */
public class GuiItem {

    // Configuração base (compatível com AfterBlockAnimations)
    private final int slot;
    private final Material material;
    private final short data;
    private final String name;
    private final List<String> lore;
    private final String type;
    private final List<Integer> duplicateSlots;
    private final boolean enabled;
    private final boolean enchanted;
    private final boolean hideFlags;
    private final List<String> actions;
    private final HeadType headType;
    private final String headValue;

    // Novas features (AfterCore Inventory Framework)
    private final List<AnimationConfig> animations;
    private final Map<String, String> nbtTags;
    private final boolean allowDrag;
    private final String dragAction;
    private final boolean cacheable;
    private final List<String> dynamicPlaceholders;
    private final int amount;
    private final ClickHandlers clickHandlers;
    private final List<String> viewConditions;
    private final List<String> clickConditions;
    private final Map<String, Integer> enchantments;
    private final int customModelData;
    private final List<String> variantRefs;
    private final List<GuiItem> inlineVariants;

    // Per-item placeholders (merged with InventoryContext during compilation)
    private final Map<String, String> itemPlaceholders;

    private GuiItem(Builder builder) {
        this.slot = builder.slot;
        this.material = builder.material;
        this.data = builder.data;
        this.name = builder.name;
        this.lore = List.copyOf(builder.lore);
        this.type = builder.type;
        this.duplicateSlots = List.copyOf(builder.duplicateSlots);
        this.enabled = builder.enabled;
        this.enchanted = builder.enchanted;
        this.hideFlags = builder.hideFlags;
        this.actions = List.copyOf(builder.actions);
        this.headType = builder.headType;
        this.headValue = builder.headValue;
        this.animations = List.copyOf(builder.animations);
        this.nbtTags = Map.copyOf(builder.nbtTags);
        this.allowDrag = builder.allowDrag;
        this.dragAction = builder.dragAction;
        this.cacheable = builder.cacheable;
        this.dynamicPlaceholders = List.copyOf(builder.dynamicPlaceholders);
        this.amount = builder.amount;
        this.clickHandlers = builder.clickHandlers;
        this.viewConditions = List.copyOf(builder.viewConditions);
        this.clickConditions = List.copyOf(builder.clickConditions);
        this.enchantments = Map.copyOf(builder.enchantments);
        this.customModelData = builder.customModelData;
        this.variantRefs = List.copyOf(builder.variantRefs);
        this.inlineVariants = List.copyOf(builder.inlineVariants);
        this.itemPlaceholders = Map.copyOf(builder.itemPlaceholders);
    }

    /**
     * Constrói o ItemStack aplicando placeholders e NBT.
     *
     * <p>
     * <b>DEPRECATED:</b> Use ItemCompiler.compile() para compilação otimizada com
     * cache.
     * Este método foi mantido apenas para compatibilidade com código legado.
     * </p>
     *
     * <p>
     * <b>Thread:</b> MAIN THREAD (PlaceholderAPI requirement)
     * </p>
     *
     * @param player  Jogador alvo (para placeholders, pode ser null)
     * @param context Contexto com placeholders adicionais
     * @return ItemStack compilado (sem cache)
     * @deprecated Use ItemCompiler.compile() para performance otimizada
     */
    @Deprecated
    @NotNull
    public ItemStack build(@Nullable Player player, @NotNull InventoryContext context) {
        // Implementação básica sem cache (legacy compatibility)
        // Para produção, usar ItemCompiler que aplica cache inteligente

        ItemStack item = new ItemStack(material, amount, data);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        // Display name (resolve placeholders via context)
        if (name != null && !name.isEmpty()) {
            String resolvedName = context.resolvePlaceholders(name);
            meta.setDisplayName(resolvedName.replace("&", "§"));
        }

        // Lore (resolve placeholders via context)
        if (lore != null && !lore.isEmpty()) {
            List<String> resolvedLore = lore.stream()
                    .map(context::resolvePlaceholders)
                    .map(line -> line.replace("&", "§"))
                    .toList();
            meta.setLore(resolvedLore);
        }

        // Enchantment glow
        if (enchanted) {
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
        }

        // Hide flags
        if (hideFlags) {
            meta.addItemFlags(ItemFlag.values());
        }

        item.setItemMeta(meta);

        // Apply NBT tags via NBTItemBuilder
        if (!nbtTags.isEmpty()) {
            NBTItemBuilder nbtBuilder = new NBTItemBuilder(item);
            nbtBuilder.setNBT(nbtTags);
            item = nbtBuilder.build();
        }

        // Apply skull texture
        if (material == Material.SKULL_ITEM && headValue != null) {
            NBTItemBuilder nbtBuilder = new NBTItemBuilder(item);
            String textureValue = context.resolvePlaceholders(headValue);
            nbtBuilder.setSkullTexture(textureValue, player);
            item = nbtBuilder.build();
        }

        return item;
    }

    /**
     * Verifica se este item deve ser cacheado.
     *
     * <p>
     * Itens com placeholders dinâmicos não são cacheados.
     * </p>
     *
     * @return true se cacheable
     */
    public boolean isCacheable() {
        return cacheable && dynamicPlaceholders.isEmpty();
    }

    /**
     * Gera cache key para este item.
     *
     * @param context Contexto (para hash de placeholders)
     * @return Cache key
     */
    @NotNull
    public String getCacheKey(@NotNull InventoryContext context) {
        if (dynamicPlaceholders.isEmpty()) {
            return "static:" + type + ":" + slot;
        }

        // Hash dos placeholders dinâmicos
        StringBuilder sb = new StringBuilder("dynamic:");
        sb.append(type).append(":").append(slot).append(":");
        dynamicPlaceholders.forEach(ph -> {
            String value = context.getPlaceholders().get(ph);
            sb.append(ph).append("=").append(value != null ? value : "null").append(";");
        });

        return sb.toString();
    }

    // Getters

    public int getSlot() {
        return slot;
    }

    public Material getMaterial() {
        return material;
    }

    public short getData() {
        return data;
    }

    public String getName() {
        return name;
    }

    public List<String> getLore() {
        return lore;
    }

    public String getType() {
        return type;
    }

    public List<Integer> getDuplicateSlots() {
        return duplicateSlots;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isEnchanted() {
        return enchanted;
    }

    public boolean isHideFlags() {
        return hideFlags;
    }

    public List<String> getActions() {
        return actions;
    }

    public HeadType getHeadType() {
        return headType;
    }

    public String getHeadValue() {
        return headValue;
    }

    public List<AnimationConfig> getAnimations() {
        return animations;
    }

    /**
     * Verifica se este item possui animações.
     *
     * @return true se tem pelo menos uma animação válida
     */
    public boolean hasAnimations() {
        return animations != null && !animations.isEmpty();
    }

    public Map<String, String> getNbtTags() {
        return nbtTags;
    }

    public boolean isAllowDrag() {
        return allowDrag;
    }

    public String getDragAction() {
        return dragAction;
    }

    public int getAmount() {
        return amount;
    }

    public List<String> getViewConditions() {
        return viewConditions;
    }

    public List<String> getClickConditions() {
        return clickConditions;
    }

    public Map<String, Integer> getEnchantments() {
        return enchantments;
    }

    public int getCustomModelData() {
        return customModelData;
    }

    public List<String> getVariantRefs() {
        return variantRefs;
    }

    public List<GuiItem> getInlineVariants() {
        return inlineVariants;
    }

    public boolean hasVariants() {
        return !variantRefs.isEmpty() || !inlineVariants.isEmpty();
    }

    public List<String> getDynamicPlaceholders() {
        return dynamicPlaceholders;
    }

    /**
     * Obtém os placeholders específicos deste item.
     * 
     * <p>
     * Estes placeholders serão mesclados com o InventoryContext global
     * durante a compilação pelo ItemCompiler.
     * </p>
     * 
     * @return Map imutável de placeholders
     */
    @NotNull
    public Map<String, String> getItemPlaceholders() {
        return itemPlaceholders;
    }

    /**
     * Obtém os handlers de click configurados.
     *
     * <p>
     * Se clickHandlers não foi definido explicitamente, retorna
     * ClickHandlers com as actions default (compatibilidade).
     * </p>
     *
     * @return ClickHandlers nunca null
     */
    @NotNull
    public ClickHandlers getClickHandlers() {
        if (clickHandlers != null) {
            return clickHandlers;
        }
        // Fallback: criar ClickHandlers com actions default (compatibilidade)
        return ClickHandlers.ofDefault(actions);
    }

    /**
     * Verifica se possui click handlers configurados.
     *
     * @return true se tem clickHandlers ou actions
     */
    public boolean hasClickHandlers() {
        return clickHandlers != null || (actions != null && !actions.isEmpty());
    }

    /**
     * Head texture type.
     */
    public enum HeadType {
        SELF, // Player próprio
        PLAYER, // Outro player (por nome)
        BASE64 // Texture base64
    }

    /**
     * Builder para GuiItem.
     */
    public static class Builder {
        private int slot = 0;
        private Material material = Material.STONE;
        private short data = 0;
        private String name = "";
        private List<String> lore = new ArrayList<>();
        private String type = "default";
        private List<Integer> duplicateSlots = new ArrayList<>();
        private boolean enabled = true;
        private boolean enchanted = false;
        private boolean hideFlags = false;
        private List<String> actions = new ArrayList<>();
        private HeadType headType = HeadType.SELF;
        private String headValue = null;
        private List<AnimationConfig> animations = new ArrayList<>();
        private Map<String, String> nbtTags = new HashMap<>();
        private boolean allowDrag = false;
        private String dragAction = null;
        private boolean cacheable = true;
        private List<String> dynamicPlaceholders = new ArrayList<>();
        private int amount = 1;
        private List<String> viewConditions = new ArrayList<>();
        private List<String> clickConditions = new ArrayList<>();
        private ClickHandlers clickHandlers = null;
        private ClickHandlers.Builder clickHandlersBuilder = null;
        private Map<String, Integer> enchantments = new HashMap<>();
        private int customModelData = -1;
        private List<String> variantRefs = new ArrayList<>();
        private List<GuiItem> inlineVariants = new ArrayList<>();
        private Map<String, String> itemPlaceholders = new HashMap<>();

        public Builder slot(int slot) {
            this.slot = slot;
            return this;
        }

        public Builder material(Material material) {
            this.material = material;
            return this;
        }

        public Builder data(short data) {
            this.data = data;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder lore(List<String> lore) {
            this.lore = new ArrayList<>(lore);
            return this;
        }

        public Builder addLoreLine(String line) {
            this.lore.add(line);
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder duplicateSlots(List<Integer> slots) {
            this.duplicateSlots = new ArrayList<>(slots);
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder enchanted(boolean enchanted) {
            this.enchanted = enchanted;
            return this;
        }

        public Builder hideFlags(boolean hideFlags) {
            this.hideFlags = hideFlags;
            return this;
        }

        public Builder actions(List<String> actions) {
            this.actions = new ArrayList<>(actions);
            return this;
        }

        public Builder addAction(String action) {
            this.actions.add(action);
            return this;
        }

        public Builder headType(HeadType headType) {
            this.headType = headType;
            return this;
        }

        public Builder headValue(String headValue) {
            this.headValue = headValue;
            return this;
        }

        public Builder animations(List<AnimationConfig> animations) {
            this.animations = new ArrayList<>(animations);
            return this;
        }

        public Builder addAnimation(AnimationConfig animation) {
            this.animations.add(animation);
            return this;
        }

        public Builder nbtTags(Map<String, String> tags) {
            this.nbtTags = new HashMap<>(tags);
            return this;
        }

        public Builder nbtTag(String key, String value) {
            this.nbtTags.put(key, value);
            return this;
        }

        public Builder allowDrag(boolean allowDrag) {
            this.allowDrag = allowDrag;
            return this;
        }

        public Builder dragAction(String dragAction) {
            this.dragAction = dragAction;
            return this;
        }

        public Builder cacheable(boolean cacheable) {
            this.cacheable = cacheable;
            return this;
        }

        public Builder dynamicPlaceholders(List<String> placeholders) {
            this.dynamicPlaceholders = new ArrayList<>(placeholders);
            return this;
        }

        public Builder addDynamicPlaceholder(String placeholder) {
            this.dynamicPlaceholders.add(placeholder);
            return this;
        }

        public Builder amount(int amount) {
            this.amount = Math.max(1, Math.min(64, amount));
            return this;
        }

        public Builder viewConditions(List<String> conditions) {
            this.viewConditions = new ArrayList<>(conditions);
            return this;
        }

        public Builder clickConditions(List<String> conditions) {
            this.clickConditions = new ArrayList<>(conditions);
            return this;
        }

        public Builder enchantments(Map<String, Integer> enchantments) {
            this.enchantments = new HashMap<>(enchantments);
            return this;
        }

        public Builder enchantment(String enchantmentName, int level) {
            this.enchantments.put(enchantmentName, level);
            return this;
        }

        public Builder customModelData(int customModelData) {
            this.customModelData = customModelData;
            return this;
        }

        public Builder variantRefs(List<String> variantRefs) {
            this.variantRefs = new ArrayList<>(variantRefs);
            return this;
        }

        public Builder addVariantRef(String variantRef) {
            this.variantRefs.add(variantRef);
            return this;
        }

        public Builder inlineVariants(List<GuiItem> inlineVariants) {
            this.inlineVariants = new ArrayList<>(inlineVariants);
            return this;
        }

        public Builder addInlineVariant(GuiItem variant) {
            this.inlineVariants.add(variant);
            return this;
        }

        /**
         * Adiciona placeholder específico para este item.
         * 
         * <p>
         * Estes placeholders são mesclados com o InventoryContext global
         * durante a compilação pelo ItemCompiler, permitindo valores únicos
         * por item em listas.
         * </p>
         * 
         * @param key   Chave do placeholder (sem chaves)
         * @param value Valor do placeholder
         * @return this para chaining
         */
        @NotNull
        public Builder withPlaceholder(@NotNull String key, @NotNull String value) {
            this.itemPlaceholders.put(key, value);
            return this;
        }

        /**
         * Adiciona múltiplos placeholders para este item.
         * 
         * @param placeholders Map de placeholders
         * @return this para chaining
         */
        @NotNull
        public Builder withPlaceholders(@NotNull Map<String, String> placeholders) {
            this.itemPlaceholders.putAll(placeholders);
            return this;
        }

        // ========== Click Handlers ==========

        /**
         * Define ClickHandlers diretamente (para uso com YAML parser).
         */
        public Builder clickHandlers(ClickHandlers handlers) {
            this.clickHandlers = handlers;
            return this;
        }

        /**
         * Garante que o clickHandlersBuilder está inicializado.
         */
        private void ensureClickHandlersBuilder() {
            if (clickHandlersBuilder == null) {
                clickHandlersBuilder = ClickHandlers.builder();
            }
        }

        // Métodos para API programática (handlers com lambdas)

        public Builder onLeftClick(ClickHandler handler) {
            ensureClickHandlersBuilder();
            clickHandlersBuilder.onLeftClick(handler);
            return this;
        }

        public Builder onRightClick(ClickHandler handler) {
            ensureClickHandlersBuilder();
            clickHandlersBuilder.onRightClick(handler);
            return this;
        }

        public Builder onShiftLeftClick(ClickHandler handler) {
            ensureClickHandlersBuilder();
            clickHandlersBuilder.onShiftLeftClick(handler);
            return this;
        }

        public Builder onShiftRightClick(ClickHandler handler) {
            ensureClickHandlersBuilder();
            clickHandlersBuilder.onShiftRightClick(handler);
            return this;
        }

        public Builder onMiddleClick(ClickHandler handler) {
            ensureClickHandlersBuilder();
            clickHandlersBuilder.onMiddleClick(handler);
            return this;
        }

        public Builder onDoubleClick(ClickHandler handler) {
            ensureClickHandlersBuilder();
            clickHandlersBuilder.onDoubleClick(handler);
            return this;
        }

        public Builder onDrop(ClickHandler handler) {
            ensureClickHandlersBuilder();
            clickHandlersBuilder.onDrop(handler);
            return this;
        }

        public Builder onControlDrop(ClickHandler handler) {
            ensureClickHandlersBuilder();
            clickHandlersBuilder.onControlDrop(handler);
            return this;
        }

        public Builder onNumberKey(ClickHandler handler) {
            ensureClickHandlersBuilder();
            clickHandlersBuilder.onNumberKey(handler);
            return this;
        }

        public Builder onClick(ClickHandler handler) {
            ensureClickHandlersBuilder();
            clickHandlersBuilder.defaultHandler(handler);
            return this;
        }

        // Métodos para API com actions (List<String>) - útil para testes

        public Builder onLeftClick(List<String> actions) {
            ensureClickHandlersBuilder();
            clickHandlersBuilder.onLeftClick(actions);
            return this;
        }

        public Builder onRightClick(List<String> actions) {
            ensureClickHandlersBuilder();
            clickHandlersBuilder.onRightClick(actions);
            return this;
        }

        public Builder onShiftLeftClick(List<String> actions) {
            ensureClickHandlersBuilder();
            clickHandlersBuilder.onShiftLeftClick(actions);
            return this;
        }

        public Builder onShiftRightClick(List<String> actions) {
            ensureClickHandlersBuilder();
            clickHandlersBuilder.onShiftRightClick(actions);
            return this;
        }

        public Builder onMiddleClick(List<String> actions) {
            ensureClickHandlersBuilder();
            clickHandlersBuilder.onMiddleClick(actions);
            return this;
        }

        public Builder onDoubleClick(List<String> actions) {
            ensureClickHandlersBuilder();
            clickHandlersBuilder.onDoubleClick(actions);
            return this;
        }

        public Builder onDrop(List<String> actions) {
            ensureClickHandlersBuilder();
            clickHandlersBuilder.onDrop(actions);
            return this;
        }

        public Builder onNumberKey(List<String> actions) {
            ensureClickHandlersBuilder();
            clickHandlersBuilder.onNumberKey(actions);
            return this;
        }

        public GuiItem build() {
            // Se usou clickHandlersBuilder, build it
            if (clickHandlersBuilder != null && clickHandlers == null) {
                clickHandlers = clickHandlersBuilder.build();
            }
            return new GuiItem(this);
        }
    }

    @Override
    public String toString() {
        return "GuiItem{" +
                "slot=" + slot +
                ", material=" + material +
                ", type='" + type + '\'' +
                ", cacheable=" + cacheable +
                '}';
    }
}
