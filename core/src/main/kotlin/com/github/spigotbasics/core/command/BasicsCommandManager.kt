package com.github.spigotbasics.core.command

import com.github.spigotbasics.core.command.common.BasicsCommand
import com.github.spigotbasics.core.logger.BasicsLoggerFactory
import org.bukkit.command.Command
import org.bukkit.command.SimpleCommandMap
import java.util.logging.Level

// TODO: Fix updateCommandsToPlayers logic
//   1. Must not be called async
//   2. in /reload, this would get called for each player and command, which is unnecessary
//      It's enough to call it once in onEnable for all online players, and otherwise only call it when a single
//      module is enabled/disabled, but not when they're bulk enabled/disabled
//   3. Modules that register a command later aren't added to the commandmap properly - gotta refresh that manually

/**
 * Responsible for registering and keeping track of commands.
 *
 * @property serverCommandMap The server command map
 */
class BasicsCommandManager(private val serverCommandMap: SimpleCommandMap) {
    private val registeredCommands: MutableList<BasicsCommand> = mutableListOf()

    fun registerAll(commands: List<BasicsCommand>) {
        commands.forEach { command ->
            registerCommand(command)
        }
        updateCommandsToPlayers()
    }

    fun unregisterAll() {
        registeredCommands.toList().forEach { command ->
            unregisterCommand(command)
        }
        updateCommandsToPlayers()
    }

    fun registerCommand(command: BasicsCommand) {
        registeredCommands += command
        injectToServerCommandMap(command)
    }

    fun unregisterCommand(command: BasicsCommand) {
        registeredCommands -= command
        removeFromServerCommandMap(command)
    }

    private fun updateCommandsToPlayers() {
        // TODO: This is only needed when changing commands AFTER the server has started
        //  It also doesn't seem to work - enabling a module later than startup doesn't add the command to the commandmap
        //  It can't be executed at all by players, but works fine from console??
//        try {
//            //if (Bukkit.isPrimaryThread()) {
//            Bukkit.getOnlinePlayers().forEach { player ->
//                player.updateCommands()
//            }
//            //}
//        } catch (_: Throwable) { }
    }

    private fun injectToServerCommandMap(command: BasicsCommand) {
        val success = serverCommandMap.register("basics", command)
        if (!success) {
            serverCommandMap.commands.forEach { existing ->
                if (existing.name == command.name) {
                    if (existing is BasicsCommand) {
                        existing.replaceCommand(command)
                    }
                }
            }
        }
    }

    companion object {
        private val logger = BasicsLoggerFactory.getCoreLogger(BasicsCommandManager::class)

        private val simpleCommandMap_knownCommands =
            try {
                val field = SimpleCommandMap::class.java.getDeclaredField("knownCommands")
                field.isAccessible = true
                field
            } catch (e: Exception) {
                logger.log(Level.SEVERE, "Failed to get SimpleCommandMap.knownCommands field", e)
                null
            }
    }

    private fun removeFromServerCommandMap(command: BasicsCommand) {
        try {
            @Suppress("UNCHECKED_CAST")
            (simpleCommandMap_knownCommands?.get(serverCommandMap) as? MutableMap<String, Command>)?.let { knownCommands ->
                knownCommands.entries.removeIf { it.value == command }
            }
        } catch (_: Exception) {
        }
        command.disableExecutor()
    }
}
