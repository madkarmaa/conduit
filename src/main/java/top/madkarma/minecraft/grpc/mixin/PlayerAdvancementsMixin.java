package top.madkarma.minecraft.grpc.mixin;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import top.madkarma.minecraft.grpc.events.Advancements;

/**
 * award only grants the rewards when the criterion completes an advancement that was not done
 * yet, so that call marks the transition to done.
 */
@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {
    @Shadow
    private ServerPlayer player;

    @Inject(method = "award", at = @At(value = "INVOKE", target = "Lnet/minecraft/advancements/AdvancementRewards;" + "grant(Lnet/minecraft/server/level/ServerPlayer;)V"))
    private void minecraftGrpc$onAdvancementDone(AdvancementHolder holder, String criterion, CallbackInfoReturnable<Boolean> info) {
        Advancements.onAdvancementDone(player, holder);
    }
}
