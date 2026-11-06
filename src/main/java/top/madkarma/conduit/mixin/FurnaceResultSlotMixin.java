package top.madkarma.conduit.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.madkarma.conduit.events.CraftedItems;

/**
 * Covers furnaces, blast furnaces and smokers. Shift-click reports through onQuickCraft and then
 * calls onTake with nothing left to count, other takes only call onTake.
 */
@Mixin(FurnaceResultSlot.class)
public abstract class FurnaceResultSlotMixin {
    @Shadow @Final private Player player;

    @Shadow private int removeCount;

    @Inject(method = "onQuickCraft(Lnet/minecraft/world/item/ItemStack;I)V", at = @At("HEAD"))
    private void conduit$onQuickCraft(ItemStack picked, int count, CallbackInfo info) {
        CraftedItems.onSmelted(player, picked, count);
    }

    @Inject(method = "onTake", at = @At("HEAD"))
    private void conduit$onTake(Player player, ItemStack carried, CallbackInfo info) {
        if (removeCount == 0) {
            return;
        }

        CraftedItems.onSmelted(player, carried, carried.getCount());
    }
}
