package top.madkarma.conduit.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Structure pieces from a template keep which template they placed, and how, to themselves. */
@Mixin(TemplateStructurePiece.class)
public interface TemplateStructurePieceAccessor {
    @Accessor
    String getTemplateName();

    @Accessor
    StructurePlaceSettings getPlaceSettings();

    @Accessor
    BlockPos getTemplatePosition();
}
