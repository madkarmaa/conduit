package top.madkarma.conduit.mixin;

import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.madkarma.conduit.service.entity.Walks;

/**
 * A walk can take a mob far from every player, where it would despawn before it arrives. Skipping
 * the check keeps the mob's saved persistence as it was.
 */
@Mixin(Mob.class)
public abstract class MobMixin {
    @Inject(method = "checkDespawn", at = @At("HEAD"), cancellable = true)
    private void conduit$keepWalkingMobs(CallbackInfo info) {
        if (Walks.isWalking((Mob) (Object) this)) {
            info.cancel();
        }
    }
}
