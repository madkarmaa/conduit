package top.madkarma.conduit.service.world

import net.minecraft.SharedConstants
import net.minecraft.core.BlockPos
import net.minecraft.server.Bootstrap
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.FluidState

/** A few blocks set by hand in a world of air from y -64 to 319, for checks that read blocks. */
class FakeBlocks : BlockGetter {
    private val blocks = mutableMapOf<BlockPos, BlockState>()

    init {
        // Block states and their shapes come from the game's registries.
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
    }

    fun set(x: Int, y: Int, z: Int, block: Block): FakeBlocks {
        blocks[BlockPos(x, y, z)] = block.defaultBlockState()
        return this
    }

    override fun getBlockState(pos: BlockPos): BlockState =
        blocks[pos] ?: Blocks.AIR.defaultBlockState()

    override fun getFluidState(pos: BlockPos): FluidState = getBlockState(pos).fluidState

    override fun getBlockEntity(pos: BlockPos): BlockEntity? = null

    override fun getHeight(): Int = 384

    override fun getMinY(): Int = -64
}
