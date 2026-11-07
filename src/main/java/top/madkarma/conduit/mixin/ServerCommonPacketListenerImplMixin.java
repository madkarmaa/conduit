package top.madkarma.conduit.mixin;

import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.madkarma.conduit.events.GameEvents;

/**
 * The handler first moves the packet to the server thread, so the event comes from the call that
 * runs there. Only players in game have a player, not connections still in configuration.
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerImplMixin {
    @Inject(
            method = "handleCustomClickAction",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/server/MinecraftServer;handleCustomClickAction(Lnet/minecraft/resources/Identifier;Ljava/util/Optional;)V"))
    private void conduit$onCustomClick(
            ServerboundCustomClickActionPacket packet, CallbackInfo info) {
        if ((Object) this instanceof ServerGamePacketListenerImpl game) {
            GameEvents.onCustomClick(game.player, packet.id(), packet.payload());
        }
    }
}
