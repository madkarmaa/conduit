package top.madkarma.conduit.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.madkarma.conduit.events.ItemEntities;

/**
 * Dropping with Q or Ctrl+Q, throwing items out of a menu, items that no longer fit into the
 * inventory when a menu closes and the overflow of GiveItem all pass through drop. The inventory
 * and equipment dropped on death do not; LivingEntityMixin reports those. Only the crafting grid
 * and the cursor of a dead player are dropped through here, when the player entity is removed.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    @Inject(
            method =
                    "drop(Lnet/minecraft/world/item/ItemStack;ZLnet/minecraft/util/Prediction;)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("RETURN"))
    private void conduit$onDrop(
            ItemStack itemStack,
            boolean thrownFromHand,
            Prediction prediction,
            CallbackInfoReturnable<ItemEntity> info) {
        ItemEntity itemEntity = info.getReturnValue();
        if (itemEntity == null) {
            return;
        }

        ServerPlayer player = (ServerPlayer) (Object) this;
        ItemEntities.onDropped(player, itemEntity, player.isDeadOrDying());
    }
}
