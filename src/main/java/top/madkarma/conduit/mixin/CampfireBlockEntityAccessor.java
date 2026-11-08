package top.madkarma.conduit.mixin;

import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Food set into a campfire slot needs the cooking time that the game keeps private. */
@Mixin(CampfireBlockEntity.class)
public interface CampfireBlockEntityAccessor {
    @Accessor
    int[] getCookingProgress();

    @Accessor
    int[] getCookingTime();
}
