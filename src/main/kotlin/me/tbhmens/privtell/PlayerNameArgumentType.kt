package me.tbhmens.privtell

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.command.CommandSource
import net.minecraft.command.EntitySelectorReader
import net.minecraft.server.command.ServerCommandSource
import java.util.concurrent.CompletableFuture

class PlayerNameArgumentType : ArgumentType<String> {
    override fun <S> listSuggestions(
        context: CommandContext<S>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        return if (context.source is CommandSource) {
            val stringReader = StringReader(builder.input)
            stringReader.cursor = builder.start
            val entitySelectorReader = EntitySelectorReader(stringReader, false)
            entitySelectorReader.read()
            entitySelectorReader.listSuggestions(
                builder
            ) {
                CommandSource.suggestMatching(
                    (context.source as CommandSource).playerNames,
                    it
                )
            }
        } else {
            Suggestions.empty()
        }
    }

    override fun getExamples(): Collection<String> {
        return listOf("Player")
    }

    companion object {
        fun getPlayerName(
            context: CommandContext<ServerCommandSource>,
            name: String?
        ): String {
            return context.getArgument(name, String::class.java)
        }
    }

    override fun parse(reader: StringReader): String {
        return reader.readUnquotedString()
    }
}