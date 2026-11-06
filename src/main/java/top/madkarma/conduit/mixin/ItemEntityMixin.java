package top.madkarma.conduit.mixin;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.madkarma.conduit.events.ItemEntities;

/**
 * Picking up shrinks the item entity's stack by the part that fit into the inventory, so the
 * difference between the counts before and after is the amount picked up.
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
    @Unique private int conduit$countBeforeTouch;

    @Inject(method = "playerTouch", at = @At("HEAD"))
    private void conduit$beforeTouch(Player player, CallbackInfo info) {
        conduit$countBeforeTouch = ((ItemEntity) (Object) this).getItem().getCount();
    }

    @Inject(method = "playerTouch", at = @At("RETURN"))
    private void conduit$afterTouch(Player player, CallbackInfo info) {
        ItemEntities.onTouched(player, (ItemEntity) (Object) this, conduit$countBeforeTouch);
    }
}
