package me.tbhmens.privtell

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientCommandSource
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.*
import net.minecraft.util.Formatting
import java.util.regex.Matcher
import java.util.regex.Pattern

@Suppress("UNUSED")
class PrivTell : ModInitializer {
    private lateinit var mc: MinecraftClient
    private val players: HashMap<String, OtherPlayer> = HashMap()
    private var lastSent: String? = null
    private lateinit var core: Core

    private fun execute(context: CommandContext<ServerCommandSource>): Int {
        val to = PlayerNameArgumentType.getPlayerName(context, "to")
        val message = PlayerNameArgumentType.getPlayerName(context, "message")
        val player = players[to.lowercase()]
        if (player == null) {
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
            val (encrypted, signature) = try {
                core.message(player, message)
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
                return 1
            }
            sendTell(to, encrypted, signature)
            sendMessage(
                format(
                    styled("To ", Formatting.WHITE),
                    styled(to, Formatting.LIGHT_PURPLE),
                    styled(": ", Formatting.WHITE),
                    styled(message, Formatting.WHITE)
                )
            )
        }
        return 0
    }

    private fun sendTell(to: String, data: String, signature: String) {
        val message =
            "/tell $to ptell`$data,$signature,${mc.session.username}`"
        lastSent = data
        mc.player!!.sendChatMessage(message)
    }

    private fun sendMessage(message: Text) {
        mc.player!!.sendMessage(message, false)
    }

    private fun addPubSign(username: String, pubSign: String) {
        if (players[username] == null)
            players[username] = OtherPlayer(decodeSignPub(pubSign), null)
        else
            players[username]?.pubEnc = decodeSignPub(pubSign)
    }

    private fun addPubEnc(username: String, pubEnc: String) {
        if (players[username] == null)
            players[username] = OtherPlayer(null, decodeEncPub(pubEnc))
        else
            players[username]?.pubEnc = decodeEncPub(pubEnc)
    }

    // Called from ChatReceivedMixin
    fun onChatMessage(text: Text): Boolean {
        val origMessage = text.string
        val m: Matcher = ptellRgx.matcher(origMessage)
        if (!m.find()) return false
        val msg = m.group(1)
        if (msg == lastSent) {
            lastSent = null
            return true
        }
        val signature = m.group(2)
        val sender = m.group(3)
        val orig: MutableText = styled(" [Original]", Formatting.DARK_GRAY, Formatting.ITALIC)
            .styled { style: Style ->
                style.withHoverEvent(
                    HoverEvent(HoverEvent.Action.SHOW_TEXT, styled(origMessage))
                )
            }
        sendMessage(try {
            if (msg[0] == '?') {
                addPubSign(sender.lowercase(), msg.substring(1))
                format(
                    styled(sender, Formatting.LIGHT_PURPLE),
                    styled(" has requested your public key. ", Formatting.AQUA),
                    styled("Send It?", Formatting.GREEN, Formatting.ITALIC)
                        .styled { style: Style ->
                            style.withClickEvent(
                                ClickEvent(
                                    ClickEvent.Action.SUGGEST_COMMAND,
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
            } else if (msg[0] == '|') {
                addPubEnc(sender.lowercase(), msg.substring(1))
                format(
                    styled(sender, Formatting.LIGHT_PURPLE),
                    styled(
                        " has given you their public key. You can now send them messages.",
                        Formatting.GREEN
                    ),
                    orig
                )
            } else {
                //                              TODO: say that player was ignored.
                val player = players[sender.lowercase()] ?: return true
                val received = core.receive(player, msg, signature)
                if (received == null)
                    format(
                        styled("Someone pretended to be ", Formatting.WHITE),
                        styled(sender, Formatting.LIGHT_PURPLE),
                        styled(", or ", Formatting.WHITE),
                        styled(sender, Formatting.LIGHT_PURPLE),
                        styled(" sent you a message with an invalid signature.", Formatting.WHITE),
                        orig
                    )
                else
                    format(
                        styled("From ", Formatting.WHITE),
                        styled(sender, Formatting.LIGHT_PURPLE),
                        styled(": ", Formatting.WHITE),
                        styled(received, Formatting.WHITE),
                        orig
                    )
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
        //                                         Message, Signature, Username
        val ptellRgx: Pattern = Pattern.compile("ptell`(.*),(.*),(.*)`")
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
                Core.fromFile(keyfile)

        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }

        ClientCommandManager.DISPATCHER.register(
            CommandManager.literal("ptell")
                .executes(this::help)
                .then(
                    CommandManager.literal("help").executes(this::help)
                )
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
                                        "|" + core.encryptPubB64,
                                        ""
                                    )
                                    0
                                })
                )
                .then(
                    CommandManager.literal("ask_pubkey")
                        .then(
                            CommandManager.argument("player", StringArgumentType.string())
                                .executes {
                                    sendTell(
                                        PlayerNameArgumentType.getPlayerName(it, "player"),
                                        "?" + core.signPubB64,
                                        ""
                                    )
                                    0
                                })

                ) as LiteralArgumentBuilder<FabricClientCommandSource>
        )
    }

    private fun help(context: CommandContext<ServerCommandSource>): Int {
        sendMessage(
            format(
                styled("/ptell\n", Formatting.LIGHT_PURPLE),
                styled(" To send someone a message:\n", Formatting.YELLOW),
                styled("    /ptell <player> <message>\n", Formatting.WHITE),
                styled("    /ptell msg <player> <message>\n", Formatting.WHITE),
                styled(
                    " To ask for someone's public key (and send them your public signing key):\n",
                    Formatting.YELLOW
                ),
                styled("    /ptell ask_pubkey <player>\n", Formatting.WHITE),
                styled(
                    " To send someone your public key (it is recommended to only use this in response to ask_pubkey):\n",
                    Formatting.YELLOW
                ),
                styled("    /ptell send_pubkey <player>\n", Formatting.WHITE),
            )
        )
        return 0
    }
}