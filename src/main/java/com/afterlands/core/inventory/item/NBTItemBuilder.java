package com.afterlands.core.inventory.item;

import de.tr7zw.changeme.nbtapi.NBT;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Construtor de ItemStacks com NBT customizado via NBTAPI.
 *
 * <p>
 * Suporta:
 * <ul>
 * <li>Custom NBT tags (String key-value)</li>
 * <li>Skull textures via base64 (1.8.8 compatible)</li>
 * <li>Self skull (player próprio)</li>
 * <li>Player skull (por nome)</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Cross-version:</b> Usa NBTAPI para compatibilidade entre versões.
 * </p>
 *
 * <p>
 * <b>Thread Safety:</b> Não thread-safe. Criar nova instância por thread.
 * </p>
 */
public class NBTItemBuilder {

    private static final Logger LOGGER = Logger.getLogger(NBTItemBuilder.class.getName());
    private static final boolean NBTAPI_AVAILABLE = checkNBTAPI();

    private final ItemStack item;

    /**
     * Cria builder a partir de um ItemStack base.
     *
     * @param base ItemStack base (será clonado)
     */
    public NBTItemBuilder(@NotNull ItemStack base) {
        this.item = base.clone();
    }

    /**
     * Verifica se NBTAPI está disponível.
     *
     * @return true se NBTAPI carregada
     */
    private static boolean checkNBTAPI() {
        try {
            Class.forName("de.tr7zw.changeme.nbtapi.NBT");
            return true;
        } catch (ClassNotFoundException e) {
            LOGGER.warning("NBTAPI not found - NBT features will be disabled");
            return false;
        }
    }

    /**
     * Adiciona tag NBT customizada.
     *
     * <p>
     * Se NBTAPI não disponível, silently ignora (graceful degradation).
     * </p>
     *
     * @param key   Chave NBT
     * @param value Valor (string)
     * @return this para chaining
     */
    @NotNull
    public NBTItemBuilder setNBT(@NotNull String key, @NotNull String value) {
        if (!NBTAPI_AVAILABLE) {
            return this;
        }

        try {
            NBT.modify(item, nbt -> {
                nbt.setString(key, value);
            });
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to set NBT tag: " + key, e);
        }

        return this;
    }

    /**
     * Adiciona múltiplas tags NBT.
     *
     * @param tags Mapa de tags
     * @return this para chaining
     */
    @NotNull
    public NBTItemBuilder setNBT(@NotNull Map<String, String> tags) {
        if (!NBTAPI_AVAILABLE || tags.isEmpty()) {
            return this;
        }

        try {
            NBT.modify(item, nbt -> {
                tags.forEach(nbt::setString);
            });
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to set NBT tags", e);
        }

        return this;
    }

    /**
     * Obtém valor de tag NBT.
     *
     * @param key Chave NBT
     * @return Valor ou null se não existir
     */
    @Nullable
    public String getNBT(@NotNull String key) {
        if (!NBTAPI_AVAILABLE) {
            return null;
        }

        try {
            // Use Function explicitly to resolve ambiguity
            return NBT.get(item,
                    (java.util.function.Function<de.tr7zw.changeme.nbtapi.iface.ReadableItemNBT, String>) nbt -> nbt
                            .getString(key));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get NBT tag: " + key, e);
            return null;
        }
    }

    /**
     * Remove tag NBT.
     *
     * @param key Chave NBT
     * @return this para chaining
     */
    @NotNull
    public NBTItemBuilder removeNBT(@NotNull String key) {
        if (!NBTAPI_AVAILABLE) {
            return this;
        }

        try {
            NBT.modify(item, nbt -> {
                nbt.removeKey(key);
            });
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to remove NBT tag: " + key, e);
        }

        return this;
    }

    /**
     * Define CustomModelData para suporte a resource packs (1.14+).
     *
     * <p>
     * CustomModelData é usado para selecionar modelos customizados
     * definidos em resource packs via predicates.
     * </p>
     *
     * @param customModelData Valor numérico do CustomModelData
     * @return this para chaining
     */
    @NotNull
    public NBTItemBuilder setCustomModelData(int customModelData) {
        if (!NBTAPI_AVAILABLE) {
            return this;
        }

        try {
            NBT.modify(item, nbt -> {
                nbt.setInteger("CustomModelData", customModelData);
            });
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to set CustomModelData: " + customModelData, e);
        }

        return this;
    }

    /**
     * Aplica textura de skull via base64, player ou self.
     *
     * <p>
     * <b>Formatos suportados:</b>
     * <ul>
     * <li>{@code base64:<texture>} - Textura base64</li>
     * <li>{@code player:<name>} - Skull de player por nome</li>
     * <li>{@code self} - Skull do player fornecido</li>
     * </ul>
     * </p>
     *
     * @param textureValue Valor da textura (formato acima)
     * @param player       Player alvo (para 'self' type)
     * @return this para chaining
     */
    @NotNull
    public NBTItemBuilder setSkullTexture(@NotNull String textureValue, @Nullable Player player) {
        if (item.getType() != Material.SKULL_ITEM || !(item.getItemMeta() instanceof SkullMeta)) {
            LOGGER.warning("Cannot apply skull texture to non-skull item");
            return this;
        }

        SkullMeta meta = (SkullMeta) item.getItemMeta();

        // Parse texture format
        if (textureValue.startsWith("base64:")) {
            String base64 = textureValue.substring(7);
            applyBase64Texture(meta, base64);
        } else if (textureValue.length() > 20 && textureValue.startsWith("ey")) {
            // Heuristic: raw base64 string
            applyBase64Texture(meta, textureValue);
        } else if (textureValue.startsWith("player:")) {
            String playerName = textureValue.substring(7);
            meta.setOwner(playerName);
        } else if (textureValue.equals("self")) {
            if (player != null) {
                meta.setOwner(player.getName());
            }
        } else {
            // Assume player name direto
            meta.setOwner(textureValue);
        }

        item.setItemMeta(meta);
        return this;
    }

    /**
     * Aplica textura base64 via reflection (1.8.8 compatible).
     *
     * <p>
     * Usa GameProfile + reflection para injetar textura custom.
     * Cross-version compatible via reflection.
     * </p>
     *
     * @param meta          SkullMeta alvo
     * @param base64Texture Textura em base64 OU Hash da textura
     */
    private void applyBase64Texture(@NotNull SkullMeta meta, @NotNull String base64Texture) {
        try {
            // Carrega classes via reflection (Mojang Authlib)
            Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
            Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");

            // Cria GameProfile via reflection
            Constructor<?> gameProfileConstructor = gameProfileClass.getConstructor(UUID.class, String.class);
            Object profile = gameProfileConstructor.newInstance(UUID.randomUUID(), null);

            String val;
            // Heuristic: if starts with "ey" (Base64 for '{'), treat as full encoded JSON
            // Otherwise, treat as texture ID/Hash
            if (base64Texture.length() > 20 && base64Texture.startsWith("ey")) {
                val = base64Texture;
            } else {
                // Texture property format:
                // {"textures":{"SKIN":{"url":"http://textures.minecraft.net/texture/<hash>"}}}
                String textureJson = String.format(
                        "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/%s\"}}}",
                        base64Texture);
                val = Base64.getEncoder().encodeToString(textureJson.getBytes());
            }

            // Cria Property via reflection
            Constructor<?> propertyConstructor = propertyClass.getConstructor(String.class, String.class);
            Object property = propertyConstructor.newInstance("textures", val);

            // Obtém PropertyMap e adiciona property
            Method getPropertiesMethod = gameProfileClass.getMethod("getProperties");
            Object properties = getPropertiesMethod.invoke(profile);
            Method putMethod = properties.getClass().getMethod("put", Object.class, Object.class);
            putMethod.invoke(properties, "textures", property);

            // Injeta GameProfile no SkullMeta via reflection
            Field profileField = meta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            profileField.set(meta, profile);

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to apply base64 skull texture (reflection failed)", e);
        }
    }

    /**
     * Retorna o ItemStack compilado.
     *
     * @return ItemStack com NBT aplicado
     */
    @NotNull
    public ItemStack build() {
        return item;
    }

    /**
     * Converte ItemStack em builder para modificação.
     *
     * @param item ItemStack existente
     * @return Novo builder
     */
    @NotNull
    public static NBTItemBuilder from(@NotNull ItemStack item) {
        return new NBTItemBuilder(item);
    }

    /**
     * Verifica se NBTAPI está disponível (útil para testes).
     *
     * @return true se NBTAPI disponível
     */
    public static boolean isNBTAPIAvailable() {
        return NBTAPI_AVAILABLE;
    }
}
