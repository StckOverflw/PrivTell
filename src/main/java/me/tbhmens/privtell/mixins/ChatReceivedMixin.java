package me.tbhmens.privtell.mixins;

import me.tbhmens.privtell.PrivTell;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatHud.class)
public class ChatReceivedMixin {
    @Inject(at = @At("HEAD"), method = "addMessage(Lnet/minecraft/text/Text;I)V", cancellable = true)
    void addMessage(Text message, int messageId, CallbackInfo info) {
        if (PrivTell.instance.onChatMessage(message)) info.cancel();
    }
}