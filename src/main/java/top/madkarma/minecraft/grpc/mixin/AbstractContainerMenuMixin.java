package top.madkarma.minecraft.grpc.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import top.madkarma.minecraft.grpc.events.CraftedItems;

/**
 * Shift-clicking a result slot takes batch after batch within one click, so the click boundaries
 * let the batches be reported as one total.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {
    @Inject(method = "clicked", at = @At("HEAD"))
    private void minecraftGrpc$beforeClick(int slotIndex, int buttonNum, ContainerInput containerInput, Player player, CallbackInfo info) {
        CraftedItems.onClickStarted(player);
    }

    @Inject(method = "clicked", at = @At("RETURN"))
    private void minecraftGrpc$afterClick(int slotIndex, int buttonNum, ContainerInput containerInput, Player player, CallbackInfo info) {
        CraftedItems.onClickFinished(player);
    }
}
