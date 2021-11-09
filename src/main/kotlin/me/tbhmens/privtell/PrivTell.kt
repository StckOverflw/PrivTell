package me.tbhmens.privtell

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource
import net.minecraft.client.MinecraftClient
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.*
import net.minecraft.util.Formatting
import java.util.regex.Matcher
import java.util.regex.Pattern

@Suppress("UNUSED")
class PrivTell : ModInitializer {
    private lateinit var mc: MinecraftClient
    private val players: HashMap<String, Core.OtherPlayer> = HashMap()
    private var lastSent: String? = null
    private lateinit var core: Core

    private fun execute(context: CommandContext<ServerCommandSource>): Int {
        val to = PlayerNameArgumentType.getPlayerName(context, "to")
        val message = PlayerNameArgumentType.getPlayerName(context, "message")
        val encrypted: String
        if (!players.containsKey(to.lowercase())) {
            sendMessage(
                format(
                    styled(to, Formatting.LIGHT_PURPLE),
                    styled("'s public key isn't cached. ", Formatting.RED),
                    styled("Ask them for their public key?", Formatting.AQUA, Formatting.ITALIC)
                        .styled { style: Style ->
                            style.withClickEvent(
                                ClickEvent(
                                    ClickEvent.Action.SUGGEST_COMMAND,
                                    "/ptell ask_pubkey $to"
                                )
                            ).withHoverEvent(
                                HoverEvent(
                                    HoverEvent.Action.SHOW_TEXT,
                                    styled("/ptell ask_pubkey $to")
                                )
                            )
                        }
                ))
        } else {
            encrypted = try {
                players[to.lowercase()]?.encrypt(message).toString()
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
                return 1
            }
            sendTell(to, encrypted)
        }
        return 0
    }

    private fun sendTell(to: String, data: String) {
        val message = String.format("/tell %s ptell`%s,%s`", to, data, mc.session.username)
        lastSent = data;
        mc.player!!.sendChatMessage(message)
    }

    private fun sendMessage(message: Text) {
        mc.player!!.sendMessage(message, false)
    }


    // Called from ChatReceivedMixin
    fun onChatMessage(text: Text): Boolean {
        val origMessage: String = text.string
        val m: Matcher = ptellRgx.matcher(origMessage)
        if (!m.find()) return false
        val msg: String = m.group(1)
        if (msg == lastSent) {
            lastSent = null
            return true
        }
        val sender: String = m.group(2)
        val orig: MutableText = styled(" [Original]", Formatting.DARK_GRAY, Formatting.ITALIC)
            .styled { style: Style ->
                style.withHoverEvent(
                    HoverEvent(HoverEvent.Action.SHOW_TEXT, styled(origMessage))
                )
            }
        sendMessage(try {
            if (msg[0] == '?')
                format(
                    styled(sender, Formatting.LIGHT_PURPLE),
                    styled(" has requested your public key. ", Formatting.AQUA),
                    styled("Send It?", Formatting.GREEN, Formatting.ITALIC)
                        .styled { style: Style ->
                            style.withClickEvent(
                                ClickEvent(
                                    ClickEvent.Action.RUN_COMMAND,
                                    "/ptell send_pubkey $sender"
                                )
                            ).withHoverEvent(
                                HoverEvent(
                                    HoverEvent.Action.SHOW_TEXT,
                                    styled("/ptell send_pubkey $sender")
                                )
                            )
                        },
                    orig
                )
            else if (msg[0] == '|') {
                val next: Core.OtherPlayer = Core.OtherPlayer(msg.substring(1))
                val prev: Core.OtherPlayer? = players.put(sender.lowercase(), next)
                if (prev == null) {
                    format(
                        styled(sender, Formatting.LIGHT_PURPLE),
                        styled(
                            " has given you their public key. You can now send them messages.",
                            Formatting.GREEN
                        ),
                        orig
                    )
                } else if (prev.pub != next.pub) {
                    format(
                        styled(sender, Formatting.LIGHT_PURPLE),
                        styled(" has sent you their updated public key.", Formatting.GREEN),
                        orig
                    )
                } else
                    return true
            } else
                format(
                    styled("From ", Formatting.WHITE),
                    styled(sender, Formatting.LIGHT_PURPLE),
                    styled(": ", Formatting.WHITE),
                    styled(core.decrypt(msg), Formatting.WHITE),
                    orig
                )
        } catch (e: Exception) {
            styled(
                "[Error]",
                Formatting.DARK_RED
            ).styled { style: Style ->
                style.withHoverEvent(
                    HoverEvent(
                        HoverEvent.Action.SHOW_TEXT, format(
                            styled("Error while decrypting a message."),
                            styled("Message: \n"),
                            styled(origMessage)
                        )
                    )
                )
            }
        })
        return true
    }

    private val reset: Text =
        LiteralText(Formatting.RESET.toString())

    private fun styled(str: String, vararg styles: Formatting): LiteralText {
        val styleTotal = StringBuilder()
        for (style in styles) styleTotal.append(style.toString())
        return LiteralText(styleTotal.toString() + str + Formatting.RESET)
    }

    private fun format(vararg components: MutableText): MutableText {
        var total: MutableText =
            LiteralText("[PrivTell] ").formatted(Formatting.AQUA)
                .formatted(Formatting.BOLD)
        for (component in components) total = total.append(component)
        return total
    }

    companion object {
        val ptellRgx: Pattern = Pattern.compile("ptell`(.*),(.*)`")
        lateinit var instance: PrivTell
    }

    @Suppress("UNCHECKED_CAST")
    override fun onInitialize() {
        instance = this
        mc = MinecraftClient.getInstance()
        val ptellFolder: java.nio.file.Path = mc.runDirectory.toPath().resolve(".ptell")
        ptellFolder.toFile().mkdir()
        try {
            val keyfile: java.io.File = ptellFolder.resolve(".keys").toFile()
            core = if (keyfile.createNewFile())
            // Create key file
                Core().apply { save(keyfile) }
            // Load key file
            else
                Core(keyfile)

        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }

        ClientCommandManager.DISPATCHER.register(
            CommandManager.literal("ptell")
                .then(
                    CommandManager.argument("to", PlayerNameArgumentType())
                        .then(
                            CommandManager.argument("message", StringArgumentType.string())
                                .executes(this::execute)
                        )
                )
                .then(
                    CommandManager.literal("msg")
                        .then(
                            CommandManager.argument("to", PlayerNameArgumentType())
                                .then(
                                    CommandManager.argument("message", StringArgumentType.string())
                                        .executes {
                                            this.execute(it)
                                        }
                                )
                        )
                )
                .then(
                    CommandManager.literal("send_pubkey")
                        .then(
                            CommandManager.argument("player", PlayerNameArgumentType())
                                .executes {
                                    sendTell(
                                        PlayerNameArgumentType.getPlayerName(it, "player"),
                                        "|" + core.pubb64
                                    )
                                    0
                                })
                )
                .then(
                    CommandManager.literal("ask_pubkey")
                        .then(
                            CommandManager.argument("player", StringArgumentType.string())
                                .executes {
                                    sendTell(StringArgumentType.getString(it, "player"), "?")
                                    0
                                })

                ) as LiteralArgumentBuilder<FabricClientCommandSource>
        )
    }
}