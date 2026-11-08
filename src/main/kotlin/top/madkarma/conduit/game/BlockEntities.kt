package top.madkarma.conduit.game

import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.ProblemReporter
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.storage.TagValueInput

/** Replaces a block entity's data and sends the change to the players nearby. */
fun ServerLevel.loadBlockEntity(blockEntity: BlockEntity, nbt: CompoundTag) {
    blockEntity.loadWithComponents(
        TagValueInput.create(ProblemReporter.DISCARDING, registryAccess(), nbt),
    )

    blockEntity.setChanged()

    val state = getBlockState(blockEntity.blockPos)
    sendBlockUpdated(blockEntity.blockPos, state, state, Block.UPDATE_ALL)
}
