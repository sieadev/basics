package com.github.spigotbasics.core.messages

import com.github.spigotbasics.core.BasicsPlugin
import com.github.spigotbasics.core.config.SavedConfig
import org.bukkit.permissions.Permission
import java.io.File

/**
 * Provides messages used by the core classes, or are commonly used in other modules
 *
 * @param plugin
 * @param file messages.yml
 */
class CoreMessages(plugin: BasicsPlugin, file: File) : SavedConfig(plugin, file) {
    val noPermission get() = getMessage("no-permission")
    val commandNotFromConsole get() = getMessage("command-not-from-console")
    val mustSpecifyPlayerFromConsole get() = getMessage("must-specify-player-from-console")
    val commandModuleDisabled get() = getMessage("command-module-disabled")
    fun noPermission(permission: Permission) = getMessage("no-permission").tags("permission" to permission.name)
    fun unknownOption(option: String) = getMessage("unknown-option").tagUnparsed("option", option)
    fun invalidArgument(argument: String) = getMessage("invalid-argument").tagUnparsed("argument", argument)
    fun playerNotFound(name: String) = getMessage("player-not-found").tagUnparsed("argument", name)
}