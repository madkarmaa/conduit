package top.madkarma.minecraft.grpc.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import top.madkarma.minecraft.grpc.events.EntityLifecycle;

/**
 * New entities enter the world through addFreshEntity. Entities loaded with their chunk, players
 * and entities changing dimension are added through other paths, so they are not reported as
 * spawned.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @Inject(method = "addFreshEntity", at = @At("RETURN"))
    private void minecraftGrpc$onAddFreshEntity(Entity entity, CallbackInfoReturnable<Boolean> info) {
        if (!info.getReturnValue()) {
            return;
        }

        EntityLifecycle.onEntitySpawned(entity);
    }
}
