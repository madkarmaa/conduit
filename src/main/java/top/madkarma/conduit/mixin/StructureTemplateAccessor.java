package top.madkarma.conduit.mixin;

import java.util.List;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Placements report how many blocks and entities they contain, which the game keeps private. */
@Mixin(StructureTemplate.class)
public interface StructureTemplateAccessor {
    @Accessor
    List<StructureTemplate.Palette> getPalettes();

    @Accessor
    List<StructureTemplate.StructureEntityInfo> getEntityInfoList();
}
