package com.afterlands.core.inventory;

import com.afterlands.core.inventory.view.InventoryViewHolder;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.afterlands.core.inventory.click.ClickHandler;
import com.afterlands.core.inventory.template.ItemTemplateService;
import org.jetbrains.annotations.Nullable;

/**
 * Serviço principal de inventários do AfterCore.
 *
 * <p>
 * Fornece APIs para gerenciamento de GUIs configuráveis via YAML,
 * com suporte a paginação, tabs, animações, drag-and-drop e persistência.
 * </p>
 *
 * <p>
 * <b>Thread Safety:</b> Métodos de abertura de inventário devem ser chamados
 * na main thread. Operações de DB são sempre async.
 * </p>
 *
 * <p>
 * <b>Performance:</b> Cache inteligente de itens estáticos, resolução async
 * de placeholders quando possível, batch updates para animações.
 * </p>
 */
public interface InventoryService {

        /**
         * Abre um inventário para um player específico.
         *
         * <p>
         * O inventário será carregado da configuração (inventories.yml)
         * e renderizado com os itens definidos. Se houver estado salvo no DB,
         * será carregado automaticamente.
         * </p>
         *
         * <p>
         * <b>Thread:</b> MAIN THREAD ONLY
         * </p>
         *
         * @param player      Jogador alvo
         * @param inventoryId ID do inventário configurado em inventories.yml
         * @param context     Dados de contexto (placeholders, estado inicial)
         * @throws IllegalArgumentException se inventoryId não existir
         * @throws IllegalStateException    se chamado fora da main thread
         */
        void openInventory(@NotNull Player player, @NotNull String inventoryId, @NotNull InventoryContext context);

        /**
         * Abre um inventário para um player com namespace do plugin.
         *
         * <p>
         * O inventário será buscado com o namespace do plugin (plugin:id).
         * Use este método para garantir isolamento entre inventários de diferentes
         * plugins.
         * </p>
         *
         * <p>
         * <b>Thread:</b> MAIN THREAD ONLY
         * </p>
         *
         * @param plugin      Plugin proprietário do inventário
         * @param player      Jogador alvo
         * @param inventoryId ID do inventário (sem namespace)
         * @param context     Dados de contexto
         * @throws IllegalArgumentException se inventoryId não existir
         * @throws IllegalStateException    se chamado fora da main thread
         */
        void openInventory(@NotNull Plugin plugin, @NotNull Player player, @NotNull String inventoryId,
                        @NotNull InventoryContext context);

        /**
         * Abre um inventário compartilhado (mesmo estado para múltiplos players).
         *
         * <p>
         * Todos os players verão o mesmo inventário e mudanças serão
         * refletidas em tempo real para todos. Útil para GUIs de leilão,
         * baús compartilhados, etc.
         * </p>
         *
         * <p>
         * <b>Thread:</b> MAIN THREAD ONLY
         * </p>
         *
         * <p>
         * <b>Performance:</b> Usa copy-on-write para evitar race conditions,
         * mas pode ter overhead maior que inventários individuais.
         * </p>
         *
         * @param players     Lista de jogadores que abrirão o inventário
         * @param inventoryId ID do inventário configurado
         * @param context     Dados de contexto compartilhado
         * @return Context ID único para referenciar esta instância compartilhada
         * @throws IllegalArgumentException se inventoryId não existir ou lista vazia
         * @throws IllegalStateException    se chamado fora da main thread
         */
        @NotNull
        String openSharedInventory(@NotNull List<Player> players, @NotNull String inventoryId,
                        @NotNull InventoryContext context);

        /**
         * Fecha um inventário para um player.
         *
         * <p>
         * Se o inventário tiver persistência habilitada, o estado
         * será salvo automaticamente no DB.
         * </p>
         *
         * <p>
         * <b>Thread:</b> MAIN THREAD ONLY
         * </p>
         *
         * @param player Jogador alvo
         */
        void closeInventory(@NotNull Player player);

        /**
         * Recupera o holder do inventário atualmente aberto por um jogador.
         * * <p>Este método consulta o cache de inventários ativos. Caso o modo debug esteja ativado
         * e o jogador não possua uma sessão válida, um log informativo será gerado.</p>
         *
         * @param playerId O {@link UUID} do jogador para o qual deseja obter o inventário.
         * @return O {@link InventoryViewHolder} ativo, ou {@code null} caso o jogador não tenha
         * nenhum inventário registrado no momento.
         */
        @Nullable
        InventoryViewHolder getActiveInventory(@NotNull UUID playerId);

        /**
         * Salva o estado de um inventário no banco de dados.
         *
         * <p>
         * Operação assíncrona que não bloqueia a main thread.
         * Caso já exista estado salvo, será sobrescrito.
         * </p>
         *
         * <p>
         * <b>Thread:</b> Async (CompletableFuture)
         * </p>
         *
         * @param playerId    UUID do jogador
         * @param inventoryId ID do inventário
         * @param state       Estado serializável
         * @return CompletableFuture que completa quando salvo
         */
        @NotNull
        CompletableFuture<Void> saveInventoryState(@NotNull UUID playerId, @NotNull String inventoryId,
                        @NotNull InventoryState state);

        /**
         * Carrega o estado de um inventário do banco de dados.
         *
         * <p>
         * Operação assíncrona. Se não houver estado salvo,
         * retorna estado inicial vazio.
         * </p>
         *
         * <p>
         * <b>Thread:</b> Async (CompletableFuture)
         * </p>
         *
         * @param playerId    UUID do jogador
         * @param inventoryId ID do inventário
         * @return CompletableFuture com o estado (ou estado inicial se não existir)
         */
        @NotNull
        CompletableFuture<InventoryState> loadInventoryState(@NotNull UUID playerId, @NotNull String inventoryId);

        /**
         * Recarrega as configurações de inventários.
         *
         * <p>
         * Apenas novos inventários abertos usarão a nova configuração.
         * Inventários já abertos não são afetados.
         * </p>
         *
         * <p>
         * <b>Thread:</b> Async (operação pode levar tempo)
         * </p>
         *
         * @return CompletableFuture que completa quando reload finalizado
         */
        @NotNull
        CompletableFuture<Void> reloadConfigurations();

        /**
         * Limpa o cache de itens compilados.
         *
         * <p>
         * Útil para forçar recompilação após mudanças em resource packs
         * ou PlaceholderAPI expansions.
         * </p>
         *
         * <p>
         * <b>Thread:</b> Thread-safe
         * </p>
         */
        void clearCache();

        /**
         * Limpa o cache de um inventário específico.
         *
         * <p>
         * Remove configurações cacheadas e itens compilados apenas deste ID.
         * </p>
         *
         * @param inventoryId ID do inventário a limpar
         */
        void clearCache(@NotNull String inventoryId);

        /**
         * Invalida apenas o cache de itens compilados de um inventário.
         *
         * <p>
         * Diferente de clearCache(inventoryId), este método NÃO remove
         * a configuração do inventário. Útil para forçar recompilação
         * de itens dinâmicos sem perder o registro de inventários externos.
         * </p>
         *
         * @param inventoryId ID do inventário
         */
        void invalidateItemCache(@NotNull String inventoryId);

        /**
         * Limpa apenas entradas de cache associadas a um jogador.
         *
         * <p>
         * Usado quando dados que afetam renderização por jogador mudam em runtime
         * (ex: idioma, placeholders por perfil), evitando limpar cache global.
         * </p>
         *
         * @param playerId UUID do jogador
         */
        void clearPlayerCache(@NotNull UUID playerId);

        /**
         * Obtém o serviço de templates de itens.
         *
         * @return Serviço para carregar itens como templates
         */
        @NotNull
        ItemTemplateService templates();

        /**
         * Verifica se um inventário está registrado.
         *
         * @param inventoryId ID do inventário
         * @return true se existir, false caso contrário
         */
        boolean isInventoryRegistered(@NotNull String inventoryId);

        /**
         * Registra um inventário customizado programaticamente.
         *
         * <p>
         * Permite registrar inventários sem YAML, útil para
         * extensões customizadas de outros plugins.
         * </p>
         *
         * <p>
         * <b>Thread:</b> Thread-safe
         * </p>
         *
         * @param config Configuração do inventário
         */
        void registerInventory(@NotNull InventoryConfig config);

        /**
         * Registra inventários a partir de um arquivo YAML.
         *
         * <p>
         * Permite carregar inventários de plugins externos.
         * </p>
         *
         * @param file Arquivo YAML contendo seção 'inventories'
         * @return Número de inventários registrados
         */
        int registerInventories(@NotNull File file);

        /**
         * Registra inventários a partir de um arquivo YAML com namespace do plugin.
         *
         * <p>
         * Os inventários serão registrados com namespace do plugin (plugin:id)
         * para evitar colisões entre plugins que usam o mesmo ID.
         * </p>
         *
         * @param plugin Plugin proprietário dos inventários
         * @param file   Arquivo YAML contendo seção 'inventories'
         * @return Número de inventários registrados
         */
        int registerInventories(@NotNull Plugin plugin, @NotNull File file);

        /**
         * Registra handler para um tipo de item específico.
         *
         * <p>
         * Quando um item com o tipo especificado for clicado, o handler será
         * chamado automaticamente, independente das actions YAML configuradas.
         * </p>
         *
         * @param itemType Nome do tipo de item (value do campo 'type' no YAML)
         * @param handler  Handler a ser executado quando itens desse tipo são clicados
         */
        void registerTypeHandler(@NotNull String itemType,
                        @NotNull ClickHandler handler);
}
