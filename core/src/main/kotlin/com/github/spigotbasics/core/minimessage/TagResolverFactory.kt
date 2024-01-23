package com.github.spigotbasics.core.minimessage

import com.github.spigotbasics.core.extensions.genitiveSuffix
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.entity.Player

// TODO: Save custom-tags.yml and load the customized version
// TODO: Add PlaceholderAPI <papi:...> tag
class TagResolverFactory(customTagsMap: Map<String, String>) {

    //private val miniMessage = MiniMessage.miniMessage()
    private val customParsedTagResolvers = customTagsMap.map { Placeholder.parsed(it.key, it.value) }
    private val defaultNonPlayerTagResolverList = createDefaultNonPlayerTagResolverList()

    private fun createDefaultNonPlayerTagResolverList(): List<TagResolver> {
        return listOf( // TODO: Add static placeholders
            Placeholder.parsed("basics", "<bold>Basics Plugin</bold>")
        )
    }

    private fun createDefaultPlayerTagResolverList(player: Player): List<TagResolver> {
        return listOf(
            Placeholder.parsed("player-name", player.name),
            Placeholder.parsed("player-name-genitive-suffix", player.name.genitiveSuffix())
        )
    }

    fun getTagResolvers(player: Player?): List<TagResolver> {
        val list = mutableListOf<TagResolver>()
        list.addAll(defaultNonPlayerTagResolverList) // TODO:
        list.addAll(customParsedTagResolvers) // TODO: Cache these two
        if(player != null) {
            list.addAll(createDefaultPlayerTagResolverList(player))
        }
        return list
    }

}