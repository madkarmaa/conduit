package top.madkarma.conduit.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.madkarma.conduit.events.ItemEntities;

/**
 * On death, the game drops a player's inventory and equipment through createItemStackToDrop with
 * randomly set. Nothing else sets it. Drop, which ServerPlayerMixin reports, leaves it unset, and
 * so does the give command, which also creates fake item entities.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "createItemStackToDrop", at = @At("RETURN"))
    private void conduit$onCreateItemStackToDrop(
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
