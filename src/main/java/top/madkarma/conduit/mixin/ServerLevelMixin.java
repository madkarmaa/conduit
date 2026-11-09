package top.madkarma.conduit.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.madkarma.conduit.events.EntityLifecycle;
import top.madkarma.conduit.game.ExplosionJournal;

/**
 * New entities enter the world through addFreshEntity. Entities loaded with their chunk, players
 * and entities changing dimension take other paths, so this mixin does not report them as spawned.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @Inject(method = "addFreshEntity", at = @At("RETURN"))
    private void conduit$onAddFreshEntity(Entity entity, CallbackInfoReturnable<Boolean> info) {
        if (!info.getReturnValue()) {
            return;
        }

        ExplosionJournal.spawned(entity);
        EntityLifecycle.onEntitySpawned(entity);
    }
}
