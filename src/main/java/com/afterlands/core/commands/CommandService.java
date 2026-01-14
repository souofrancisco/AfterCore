package com.afterlands.core.commands;

import com.afterlands.core.commands.messages.MessageFacade;
import com.afterlands.core.commands.registry.CommandGraph;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * Framework completo de comandos do AfterCore.
 *
 * <p>Este serviço fornece:</p>
 * <ul>
 *   <li>Registro via anotacoes ({@code @Command}, {@code @Subcommand})</li>
 *   <li>Registro via DSL/Builder ({@link CommandSpec})</li>
 *   <li>Gerenciamento de ciclo de vida por plugin owner</li>
 *   <li>Registro dinamico no CommandMap (sem plugin.yml)</li>
 *   <li>Help/usage automaticos</li>
 *   <li>Tab-complete inteligente</li>
 *   <li>Integracao com MessageService, SchedulerService, MetricsService</li>
 * </ul>
 *
 * <p>Performance: {@code < 0.2ms} por execucao tipica, {@code < 0.5ms} por tab-complete.</p>
 *
 * <p>Thread Safety: Todos os metodos sao thread-safe.</p>
 *
 * @see CommandSpec
 * @see CommandRegistration
 */
public interface CommandService {

    /**
     * Registra um handler de comandos anotado (compatibilidade retroativa).
     *
     * <p>O handler deve ser uma classe anotada com {@code @Command} e conter
     * metodos anotados com {@code @Subcommand}.</p>
     *
     * <p>Esta versao assume o AfterCore como owner. Para controle de lifecycle
     * por plugin, use {@link #register(Plugin, Object)}.</p>
     *
     * @param commandHandler Handler anotado
     */
    void register(@NotNull Object commandHandler);

    /**
     * Registra um handler de comandos anotado com owner especifico.
     *
     * <p>O handler deve ser uma classe anotada com {@code @Command} e conter
     * metodos anotados com {@code @Subcommand}.</p>
     *
     * @param owner          Plugin que possui este comando
     * @param commandHandler Handler anotado
     * @return Registration result com nome e aliases registrados
     */
    @NotNull
    CommandRegistration register(@NotNull Plugin owner, @NotNull Object commandHandler);

    /**
     * Registra um comando via CommandSpec (DSL/Builder).
     *
     * <p>Exemplo:</p>
     * <pre>{@code
     * CommandSpec spec = CommandSpec.root("mycommand")
     *     .aliases("mc")
     *     .description("My custom command")
     *     .sub("reload")
     *         .permission("myplugin.reload")
     *         .executor(ctx -> ctx.send("Reloaded!"))
     *         .done()
     *     .build();
     * commandService.register(myPlugin, spec);
     * }</pre>
     *
     * @param owner Plugin que possui este comando
     * @param spec  Especificacao do comando
     * @return Registration result com nome e aliases registrados
     */
    @NotNull
    CommandRegistration register(@NotNull Plugin owner, @NotNull CommandSpec spec);

    /**
     * Desregistra todos os comandos (compatibilidade retroativa).
     *
     * <p>Normalmente chamado em onDisable() do AfterCore.</p>
     */
    void unregisterAll();

    /**
     * Desregistra todos os comandos de um plugin especifico.
     *
     * <p>Plugins devem chamar isto em seu onDisable() para evitar leaks.</p>
     *
     * @param owner Plugin para desregistrar
     * @return Numero de comandos desregistrados
     */
    int unregisterAll(@NotNull Plugin owner);

    /**
     * Obtem o grafo de comandos para resolucao avancada.
     *
     * @return O grafo de comandos
     */
    @NotNull
    CommandGraph graph();

    /**
     * Obtem a fachada de mensagens para uso em comandos.
     *
     * @return A fachada de mensagens
     */
    @NotNull
    MessageFacade messages();

    /**
     * Verifica se um comando esta registrado.
     *
     * @param nameOrAlias Nome ou alias do comando
     * @return true se registrado
     */
    boolean isRegistered(@NotNull String nameOrAlias);
}

