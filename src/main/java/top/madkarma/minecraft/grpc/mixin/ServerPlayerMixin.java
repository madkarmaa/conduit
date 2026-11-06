package top.madkarma.minecraft.grpc.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import top.madkarma.minecraft.grpc.events.ItemEntities;

/**
 * Dropping with Q or Ctrl+Q, throwing items out of a menu and items that no longer fit into the
 * inventory when a menu closes all pass through drop. Items dropped on death do not.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZLnet/minecraft/util/Prediction;)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("RETURN"))
    private void minecraftGrpc$onDrop(ItemStack stack, boolean thrownFromHand, Prediction prediction, CallbackInfoReturnable<ItemEntity> info) {
        ItemEntity itemEntity = info.getReturnValue();
        if (itemEntity == null) {
            return;
        }

        ItemEntities.onDropped((ServerPlayer) (Object) this, itemEntity);
    }
}
