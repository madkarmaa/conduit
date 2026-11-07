package top.madkarma.conduit.service.structure

import java.nio.file.Files
import java.nio.file.Path
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtIo
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate

/** Writes the vanilla structure file format, gzipped NBT, like structure blocks do. */
internal fun writeStructureFile(path: Path, template: StructureTemplate) {
    Files.createDirectories(path.parent)
    NbtIo.writeCompressed(template.save(CompoundTag()), path)
}
