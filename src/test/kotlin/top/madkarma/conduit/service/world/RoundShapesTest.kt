package top.madkarma.conduit.service.world

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import top.madkarma.conduit.game.BlockCoordinates
import top.madkarma.conduit.invalidInputMessage

class RoundShapesTest {
    private val center = BlockCoordinates(10, 64, -10)

    private fun offsetsFromCenter(positions: Sequence<BlockPos>): List<BlockCoordinates> =
        positions.coordinates().map {
            BlockCoordinates(it.x - center.x, it.y - center.y, it.z - center.z)
        }

    @Test
    fun `a sphere of radius 0 is its center`() {
        assertEquals(listOf(center), sphere(center, 0, hollow = false).coordinates())
        assertEquals(listOf(center), sphere(center, 0, hollow = true).coordinates())
    }

    @Test
    fun `a sphere rounds its edge like a drawn circle`() {
        // Within radius + 0.5 of the center, which leaves out only the corners of a radius 1 cube.
        assertEquals(19, sphere(center, 1, hollow = false).count())
        assertEquals(81, sphere(center, 2, hollow = false).count())
    }

    @Test
    fun `a hollow sphere keeps a shell one block thick`() {
        val full = sphere(center, 2, hollow = false).coordinates().toSet()
        val shell = sphere(center, 2, hollow = true).coordinates()

        assertEquals(62, shell.size)
        assertTrue(full.containsAll(shell))
        assertTrue(center !in shell)
        assertTrue(BlockCoordinates(12, 64, -10) in shell)
    }

    @Test
    fun `a sphere stays within the region limit`() {
        assertEquals(
            "Region of 1030301 blocks exceeds the limit of 1000000",
            invalidInputMessage { sphere(center, 50, hollow = false) },
        )
    }

    @Test
    fun `a cylinder rises from its center along its axis`() {
        val request = ShapeRequest(Shape.CYLINDER, radius = 2, height = 3)
        val offsets = offsetsFromCenter(cylinder(center, request))

        assertEquals(21 * 3, offsets.size)
        assertEquals(setOf(0, 1, 2), offsets.map { it.y }.toSet())
        assertTrue(offsets.all { it.x * it.x + it.z * it.z <= 6 })
    }

    @Test
    fun `a hollow cylinder keeps only its side`() {
        val request = ShapeRequest(Shape.CYLINDER, radius = 2, height = 1, hollow = true)
        val offsets = offsetsFromCenter(cylinder(center, request))

        assertEquals(12, offsets.size)
        assertTrue(offsets.all { it.x * it.x + it.z * it.z > 2 })
    }

    @Test
    fun `a cylinder can lie along another axis`() {
        val request = ShapeRequest(Shape.CYLINDER, radius = 1, height = 4, axis = Direction.Axis.X)
        val offsets = offsetsFromCenter(cylinder(center, request))

        assertEquals(9 * 4, offsets.size)
        assertEquals(setOf(0, 1, 2, 3), offsets.map { it.x }.toSet())
        assertEquals(setOf(-1, 0, 1), offsets.map { it.y }.toSet())
    }

    @Test
    fun `a cylinder needs a height`() {
        val request = ShapeRequest(Shape.CYLINDER, radius = 2, height = 0)

        assertEquals("height must be at least 1", invalidInputMessage { cylinder(center, request) })
    }
}
