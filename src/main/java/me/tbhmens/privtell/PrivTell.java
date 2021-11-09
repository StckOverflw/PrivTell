package me.tbhmens.privtell;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fmllegacy.network.FMLNetworkConstants;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.File;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mod("privtell")
@OnlyIn(Dist.CLIENT)
public class PrivTell {
    public final static Pattern ptellRgx = Pattern.compile(".*ptell`(.*),(.*)`.*");
    private final static Component base = new TextComponent("[PrivTell] ").withStyle(ChatFormatting.AQUA).withStyle(ChatFormatting.BOLD);
    private static TextComponent reset = new TextComponent(ChatFormatting.RESET.toString());
    private final Minecraft mc;
    private final HashMap<String, Core.OtherPlayer> players = new HashMap<>();
    private String lastSent = null;
    private Core core;

    public PrivTell() {
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class, () -> new IExtensionPoint.DisplayTest(() -> FMLNetworkConstants.IGNORESERVERONLY, (a, b) -> true));
        MinecraftForge.EVENT_BUS.register(this);
        mc = Minecraft.getInstance();
        assert mc.player != null;
        final Path ptellFolder = mc.gameDirectory.toPath().resolve(".ptell");
        ptellFolder.toFile().mkdir();
        try {
            final File keyfile = ptellFolder.resolve(".keys").toFile();
            if (keyfile.createNewFile()) {
                // Key file created
                core = new Core();
                core.save(keyfile);
            } else {
                // Key file already existed
                core = new Core(keyfile);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static TextComponent styled(String str, ChatFormatting... styles) {
        StringBuilder styleTotal = new StringBuilder();
        for (ChatFormatting style : styles)
            styleTotal.append(style.toString());
        return new TextComponent(styleTotal + str + ChatFormatting.RESET);
    }

    private static MutableComponent format(MutableComponent... components) {
        MutableComponent
                total = new TextComponent("[PrivTell] ").withStyle(ChatFormatting.AQUA).withStyle(ChatFormatting.BOLD);
        for (MutableComponent component : components)
            total = total.append(component);
        return total;
    }

    /*
//    Previous implementation of the /ptell command. Client Commands don't exist in Forge (yet) sadly.
    @SubscribeEvent
    public void onCommandsRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("ptell")
                .then(Commands.argument("to", StringArgumentType.string())
                        .then(Commands.argument("message", StringArgumentType.string())
                                .executes(this::execute)))
                .then(Commands.literal("msg")
                        .then(Commands.argument("to", StringArgumentType.string())
                                .then(Commands.argument("message", StringArgumentType.string())
                                        .executes(this::execute))))
                .then(Commands.literal("send_pubkey")
                        .then(Commands.argument("player", StringArgumentType.string())
                                .executes(context -> {
                                    mc.player.chat(tell(StringArgumentType.getString(context, "player"), "|" + core.pubb64));
                                    return 0;
                                })))
                .then(Commands.literal("ask_pubkey")
                        .then(Commands.argument("player", StringArgumentType.string())
                                .executes(context -> {
                                    mc.player.chat(tell(StringArgumentType.getString(context, "player"), "?"));
                                    return 0;
                                })))
        );
    }
     */


    @SubscribeEvent
    public void clientChatEvent(ClientChatEvent event) {
        if (event.getOriginalMessage().startsWith("/ptell")) {
            final String[] parts = event.getOriginalMessage().split(" ");
            if (parts.length < 3) {
                mc.player.sendMessage(format(styled("/ptell <to> <message>", ChatFormatting.DARK_AQUA)), mc.player.getUUID());
                event.setCanceled(true);
                return;
            }
            final String type = parts[1];
            switch (type) {
                case "send_pubkey":
                    mc.player.chat(tell(parts[2], "|" + core.pubb64));
                    break;
                case "ask_pubkey":
                    mc.player.chat(tell(parts[2], "?"));
                    break;
                case "msg":
                    this.execute(parts[2], parts[3]);
                    break;
                default:
                    this.execute(parts[1], parts[2]);

            }

            mc.gui.getChat().addRecentChat(event.getOriginalMessage());
            event.setCanceled(true);
        }
    }

    private String tell(String to, String data) {
        return String.format("/tell %s ptell`%s,%s`", to, data, mc.getUser().getName());
    }

    private int execute(String to, String message) {
        final String encrypted;
        if (!players.containsKey(to.toLowerCase())) {
            mc.player.sendMessage(
                    format(
                            styled(to, ChatFormatting.LIGHT_PURPLE),
                            styled("'s public key isn't cached. ", ChatFormatting.RED),
                            styled("Ask them for their public key?", ChatFormatting.AQUA, ChatFormatting.ITALIC)
                                    .withStyle(style -> style.withClickEvent(
                                                    new ClickEvent(
                                                            ClickEvent.Action.SUGGEST_COMMAND,
                                                            "/ptell ask_pubkey " + to
                                                    )
                                            ).withHoverEvent(
                                                    new HoverEvent(
                                                            HoverEvent.Action.SHOW_TEXT,
                                                            styled("/ptell ask_pubkey " + to)
                                                    )
                                            )
                                    )
                    ),
                    mc.player.getUUID());
        } else {
            try {
                encrypted = players.get(to.toLowerCase()).encrypt(message);
            } catch (Exception e) {
                e.printStackTrace();
                return 1;
            }
            mc.player.chat(tell(to, encrypted));
        }
        return 0;
    }

    @SubscribeEvent
    public void onChatMessage(ClientChatReceivedEvent event) {
        final String origMessage = event.getMessage().getString();
        final Matcher m = ptellRgx.matcher(origMessage);
        if (!m.matches()) return;
        final String msg = m.group(1);
        final String sender = m.group(2);
        final MutableComponent orig =
                styled(" [Original]", ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)
                        .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, styled(origMessage))));
        try {
            if (msg.charAt(0) == '?')
                event.setMessage(
                        format(
                                styled(sender, ChatFormatting.LIGHT_PURPLE),
                                styled(" has requested your public key. ", ChatFormatting.AQUA),
                                styled("Send It?", ChatFormatting.GREEN, ChatFormatting.ITALIC)
                                        .withStyle(style -> style.withClickEvent(
                                                        new ClickEvent(
                                                                ClickEvent.Action.RUN_COMMAND,
                                                                "/ptell send_pubkey " + sender
                                                        )
                                                ).withHoverEvent(
                                                        new HoverEvent(
                                                                HoverEvent.Action.SHOW_TEXT,
                                                                styled("/ptell send_pubkey " + sender)
                                                        )
                                                )
                                        ),
                                orig
                        )
                );
            else if (msg.charAt(0) == '|') {
                Core.OtherPlayer next = new Core.OtherPlayer(msg.substring(1));
                Core.OtherPlayer prev = players.put(sender.toLowerCase(), next);
                if (prev == null)
                    event.setMessage(format(
                            styled(sender, ChatFormatting.LIGHT_PURPLE),
                            styled(" has given you their public key. You can now send them messages.", ChatFormatting.GREEN),
                            orig
                    ));
                else if (prev.pub.equals(next.pub))
                    event.setCanceled(true);
                else
                    event.setMessage(format(
                            styled(sender, ChatFormatting.LIGHT_PURPLE),
                            styled(" has sent you their updated public key.", ChatFormatting.GREEN),
                            orig
                    ));
            } else
                event.setMessage(format(styled("From ", ChatFormatting.WHITE),
                        styled(sender, ChatFormatting.LIGHT_PURPLE),
                        styled(": ", ChatFormatting.WHITE),
                        styled(core.decrypt(msg), ChatFormatting.WHITE),
                        orig
                ));
        } catch (NoSuchPaddingException | IllegalBlockSizeException | NoSuchAlgorithmException | InvalidKeySpecException | BadPaddingException | InvalidKeyException e) {
            event.setMessage(
                    styled("[Error]", ChatFormatting.DARK_RED).withStyle(style -> style.withHoverEvent(
                            new HoverEvent(HoverEvent.Action.SHOW_TEXT, format(
                                    styled("Error while decrypting a message."),
                                    styled("Message: \n"),
                                    styled(origMessage)
                            ))
                    ))
            );
        }
    }
}
