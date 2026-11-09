package top.madkarma.conduit.service.world

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.material.Fluids

// Floors that hurt or trap whoever stands on them.
private val HARMFUL_FLOORS: Set<Block> =
    setOf(Blocks.MAGMA_BLOCK, Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE, Blocks.CACTUS)

// Blocks without a collision box that still hurt or trap a player inside them.
private val HARMFUL_SPACES: Set<Block> =
    setOf(
        Blocks.FIRE,
        Blocks.SOUL_FIRE,
        Blocks.SWEET_BERRY_BUSH,
        Blocks.WITHER_ROSE,
        Blocks.POWDER_SNOW,
        Blocks.COBWEB,
        Blocks.POINTED_DRIPSTONE,
        Blocks.NETHER_PORTAL,
        Blocks.END_PORTAL,
        Blocks.END_GATEWAY,
    )

/**
 * Whether a player with their feet at the position has room for feet and head, stands on a solid
 * top face and takes no harm from the blocks or lava around them.
 */
internal fun isSafeSpot(level: BlockGetter, feet: BlockPos): Boolean {
    val below = feet.below()
    val floor = level.getBlockState(below)
    if (floor.block in HARMFUL_FLOORS || !floor.isFaceSturdy(level, below, Direction.UP)) {
        return false
    }
    if (!isFree(level, feet) || !isFree(level, feet.above())) return false

    return Direction.Plane.HORIZONTAL.none {
        level.getFluidState(feet.relative(it)).type.isSame(Fluids.LAVA)
    }
}

private fun isFree(level: BlockGetter, pos: BlockPos): Boolean {
    val state = level.getBlockState(pos)
    return state.block !in HARMFUL_SPACES &&
        state.fluidState.isEmpty &&
        state.getCollisionShape(level, pos).isEmpty
}

/** The feet heights in a column where a player can stand, from the top down. */
internal fun columnFloors(level: BlockGetter, x: Int, z: Int, top: Int, bottom: Int): List<Int> =
    (top downTo bottom).filter { isSafeSpot(level, BlockPos(x, it, z)) }
