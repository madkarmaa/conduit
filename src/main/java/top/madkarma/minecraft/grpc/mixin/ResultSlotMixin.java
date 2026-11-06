package top.madkarma.minecraft.grpc.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import top.madkarma.minecraft.grpc.events.CraftedItems;

/**
 * Shift-click reports each batch through onQuickCraft and then calls onTake with nothing left to
 * count, other takes only call onTake. The taken stack is counted instead of removeCount, which is
 * too low after a right-click because the result container always hands out its whole stack.
 */
@Mixin(ResultSlot.class)
public abstract class ResultSlotMixin {
    @Shadow
    @Final
    private Player player;

    @Shadow
    private int removeCount;

    @Inject(method = "onQuickCraft(Lnet/minecraft/world/item/ItemStack;I)V", at = @At("HEAD"))
    private void minecraftGrpc$onQuickCraft(ItemStack picked, int count, CallbackInfo info) {
        CraftedItems.onCrafted(player, picked, count, ((Slot) (Object) this).container);
    }

    @Inject(method = "onTake", at = @At("HEAD"))
    private void minecraftGrpc$onTake(Player player, ItemStack carried, CallbackInfo info) {
        if (removeCount == 0) {
            return;
        }

        CraftedItems.onCrafted(player, carried, carried.getCount(), ((Slot) (Object) this).container);
    }
}
