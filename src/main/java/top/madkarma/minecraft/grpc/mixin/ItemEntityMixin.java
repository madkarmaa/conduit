package top.madkarma.minecraft.grpc.mixin;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.madkarma.minecraft.grpc.events.ItemEntities;

/**
 * Picking up shrinks the item entity's stack by the part that fit into the inventory, so the
 * difference between the counts before and after is the amount picked up.
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
    @Unique private int minecraftGrpc$countBeforeTouch;

    @Inject(method = "playerTouch", at = @At("HEAD"))
    private void minecraftGrpc$beforeTouch(Player player, CallbackInfo info) {
        minecraftGrpc$countBeforeTouch = ((ItemEntity) (Object) this).getItem().getCount();
    }

    @Inject(method = "playerTouch", at = @At("RETURN"))
    private void minecraftGrpc$afterTouch(Player player, CallbackInfo info) {
        ItemEntities.onTouched(player, (ItemEntity) (Object) this, minecraftGrpc$countBeforeTouch);
    }
}
