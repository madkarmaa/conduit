package top.madkarma.minecraft.grpc.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.madkarma.minecraft.grpc.events.ItemEntities;

/**
 * A player's inventory and equipment are dropped on death through createItemStackToDrop with
 * randomly set, which nothing else does: drop, which ServerPlayerMixin reports, and the give
 * command, which also creates fake item entities, both leave it unset.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "createItemStackToDrop", at = @At("RETURN"))
    private void minecraftGrpc$onCreateItemStackToDrop(
            ItemStack itemStack,
            boolean randomly,
            boolean thrownFromHand,
            CallbackInfoReturnable<ItemEntity> info) {
        ItemEntity itemEntity = info.getReturnValue();
        if (!randomly || itemEntity == null || !((Object) this instanceof ServerPlayer player)) {
            return;
        }

        ItemEntities.onDropped(player, itemEntity, true);
    }
}
