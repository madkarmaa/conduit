package top.madkarma.minecraft.grpc.mixin;

import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.madkarma.minecraft.grpc.events.PlayerMoves;

/**
 * Every position change the server forces on a player passes through teleport: commands,
 * respawning, changing dimension, joining and leaving beds.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
    @Shadow public ServerPlayer player;

    @Inject(
            method = "teleport(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;)V",
            at = @At("HEAD"))
    private void minecraftGrpc$onTeleport(
            PositionMoveRotation destination, Set<Relative> relatives, CallbackInfo info) {
        PlayerMoves.onTeleported(player);
    }
}
