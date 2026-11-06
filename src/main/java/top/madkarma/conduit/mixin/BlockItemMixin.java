package top.madkarma.conduit.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.madkarma.conduit.events.GameEvents;

/** Reports successful block placements, which Fabric API has no event for. */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @Inject(method = "place", at = @At("RETURN"))
    private void conduit$onPlace(
            BlockPlaceContext placeContext, CallbackInfoReturnable<InteractionResult> info) {
        if (!info.getReturnValue().consumesAction()) {
            return;
        }

        Level level = placeContext.getLevel();
        BlockPos position = placeContext.getClickedPos();
        ItemStack item = new ItemStack((BlockItem) (Object) this);
        BlockState state = level.getBlockState(position);
        GameEvents.onBlockPlaced(level, placeContext.getPlayer(), position, state, item);
    }
}
