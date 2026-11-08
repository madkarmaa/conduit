package top.madkarma.conduit.mixin;

import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Horses only take an online owner, so this sets an offline player's UUID as the owner. */
@Mixin(AbstractHorse.class)
public interface AbstractHorseAccessor {
    @Accessor("owner")
    void setOwnerReference(EntityReference<LivingEntity> owner);
}
