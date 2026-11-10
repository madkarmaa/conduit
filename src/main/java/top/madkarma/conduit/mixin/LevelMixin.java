package top.madkarma.conduit.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.madkarma.conduit.game.EditJournal;

/**
 * Every block change of the world passes through setBlock, so the edit journal sees the blocks that
 * commands such as fill and clone change too. Outside recorded tool calls, it does nothing.
 */
@Mixin(Level.class)
public abstract class LevelMixin {
    @Inject(
            method =
                    "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"))
    private void conduit$beforeSetBlock(
            BlockPos pos,
            BlockState state,
            int flags,
            int recursionLeft,
            CallbackInfoReturnable<Boolean> info) {
        if ((Object) this instanceof ServerLevel level) {
            EditJournal.before(level, pos);
        }
    }
}
