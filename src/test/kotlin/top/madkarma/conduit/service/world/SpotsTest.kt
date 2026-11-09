package top.madkarma.conduit.service.world

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks

class SpotsTest {
    private val feet = BlockPos(0, 1, 0)

    @Test
    fun `a solid floor with room above is safe`() {
        assertTrue(isSafeSpot(FakeBlocks().set(0, 0, 0, Blocks.STONE), feet))
    }

    @Test
    fun `no floor, a bottom slab or a harmful floor is not safe`() {
        assertFalse(isSafeSpot(FakeBlocks(), feet))
        assertFalse(isSafeSpot(FakeBlocks().set(0, 0, 0, Blocks.STONE_SLAB), feet))
        assertFalse(isSafeSpot(FakeBlocks().set(0, 0, 0, Blocks.MAGMA_BLOCK), feet))
    }

    @Test
    fun `a gap of one block is not safe`() {
        val blocks = FakeBlocks().set(0, 0, 0, Blocks.STONE).set(0, 2, 0, Blocks.STONE)
        assertFalse(isSafeSpot(blocks, feet))
    }

    @Test
    fun `lava next to the feet or fire at them is not safe`() {
        val lava = FakeBlocks().set(0, 0, 0, Blocks.STONE).set(1, 1, 0, Blocks.LAVA)
        val fire = FakeBlocks().set(0, 0, 0, Blocks.STONE).set(0, 1, 0, Blocks.FIRE)
        assertFalse(isSafeSpot(lava, feet))
        assertFalse(isSafeSpot(fire, feet))
    }

    @Test
    fun `plants without a collision box leave room to stand`() {
        val blocks = FakeBlocks().set(0, 0, 0, Blocks.GRASS_BLOCK).set(0, 1, 0, Blocks.SHORT_GRASS)
        assertTrue(isSafeSpot(blocks, feet))
    }

    @Test
    fun `a shell holds the positions at exactly its distance on some axis`() {
        val center = BlockPos(5, 5, 5)
        assertEquals(listOf(center), shellPositions(center, 0).toList())

        val shell = shellPositions(center, 1).toList()
        assertEquals(26, shell.size)
        assertEquals(26, shell.toSet().size)
        assertEquals(98, shellPositions(center, 2).count())
    }
}
