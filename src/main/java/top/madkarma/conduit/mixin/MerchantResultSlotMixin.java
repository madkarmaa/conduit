package top.madkarma.conduit.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.madkarma.conduit.events.Trades;

/** notifyTrade is only called once the costs were taken from the payment slots. */
@Mixin(MerchantResultSlot.class)
public abstract class MerchantResultSlotMixin {
    @Shadow @Final private MerchantContainer slots;

    @Shadow @Final private Merchant merchant;

    @Inject(
            method = "onTake",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/item/trading/Merchant;notifyTrade(Lnet/minecraft/world/item/trading/MerchantOffer;)V"))
    private void conduit$onTrade(Player player, ItemStack carried, CallbackInfo info) {
        Trades.onTrade(player, merchant, slots.getActiveOffer());
    }
}
