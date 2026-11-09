package top.madkarma.conduit.mixin;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.madkarma.conduit.game.ExplosionJournal;

/**
 * interactWithBlocks breaks the blocks an explosion reached and drops their items, so the blocks
 * before and after it and the items in between are what undo needs to repair the damage.
 */
@Mixin(ServerExplosion.class)
public abstract class ServerExplosionMixin {
    @Inject(method = "interactWithBlocks", at = @At("HEAD"))
    private void conduit$beforeBlocks(List<BlockPos> targetBlocks, CallbackInfo info) {
        ExplosionJournal.before((ServerExplosion) (Object) this, targetBlocks);
    }

    @Inject(method = "interactWithBlocks", at = @At("RETURN"))
    private void conduit$afterBlocks(List<BlockPos> targetBlocks, CallbackInfo info) {
        ExplosionJournal.after((ServerExplosion) (Object) this);
    }
}
