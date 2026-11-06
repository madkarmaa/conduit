package top.madkarma.conduit.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.madkarma.conduit.events.CraftedItems;

/**
 * Shift-clicking a result slot takes batch after batch within one click, so CraftedItems uses the
 * click boundaries to report the batches as one total.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {
    @Inject(method = "clicked", at = @At("HEAD"))
    private void conduit$beforeClick(
            int slotIndex,
            int buttonNum,
            ContainerInput containerInput,
            Player player,
            CallbackInfo info) {
        CraftedItems.onClickStarted(player);
    }

    @Inject(method = "clicked", at = @At("RETURN"))
    private void conduit$afterClick(
            int slotIndex,
            int buttonNum,
            ContainerInput containerInput,
            Player player,
            CallbackInfo info) {
        CraftedItems.onClickFinished(player);
    }
}
