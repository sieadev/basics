package com.github.spigotbasics.core.command.raw

interface RawTabCompleter {
    fun tabComplete(context: RawCommandContext): List<String>?
}
