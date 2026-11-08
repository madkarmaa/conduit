package top.madkarma.conduit.service.world

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import top.madkarma.conduit.game.BlockCoordinates
import top.madkarma.conduit.invalidInputMessage

class StraightShapesTest {
    private val origin = BlockCoordinates(0, 0, 0)
    private val corner = BlockCoordinates(2, 2, 2)

    @Test
    fun `a box fills the cuboid between its corners in any order`() {
        val box = box(corner, origin, hollow = false).coordinates()

        assertEquals(27, box.size)
        assertEquals(box.toSet(), box(origin, corner, hollow = false).coordinates().toSet())
    }

    @Test
    fun `a hollow box keeps its six faces`() {
        val shell = box(origin, corner, hollow = true).coordinates()

        assertEquals(26, shell.size)
        assertTrue(BlockCoordinates(1, 1, 1) !in shell)
    }

    @Test
    fun `walls leave out the floor and the ceiling`() {
        val walls = walls(origin, BlockCoordinates(2, 1, 2)).coordinates()

        assertEquals(16, walls.size)
        assertTrue(BlockCoordinates(1, 0, 1) !in walls)
        assertTrue(BlockCoordinates(1, 1, 1) !in walls)
    }

    @Test
    fun `a box stays within the region limit`() {
        assertEquals(
            "Region of 1030301 blocks exceeds the limit of 1000000",
            invalidInputMessage { box(origin, BlockCoordinates(100, 100, 100), hollow = true) },
        )
    }

    @Test
    fun `a line of one point is that point`() {
        assertEquals(listOf(corner), line(corner, corner).coordinates())
    }

    @Test
    fun `a line steps one block at a time along its longest axis`() {
        val expected =
            listOf(
                BlockCoordinates(0, 0, 0),
                BlockCoordinates(1, 1, 0),
                BlockCoordinates(2, 1, 0),
                BlockCoordinates(3, 2, 0),
                BlockCoordinates(4, 2, 0),
            )

        assertEquals(expected, line(origin, BlockCoordinates(4, 2, 0)).coordinates())
    }

    @Test
    fun `a line has no gaps in any direction`() {
        val start = BlockCoordinates(5, 70, -3)
        val end = BlockCoordinates(-7, 64, 9)
        val points = line(start, end).coordinates()

        assertEquals(listOf(start, end), listOf(points.first(), points.last()))
        assertEquals(13, points.size)
        assertTrue(
            points.zipWithNext().all { (from, to) ->
                abs(to.x - from.x) <= 1 && abs(to.y - from.y) <= 1 && abs(to.z - from.z) <= 1
            },
        )
    }

    @Test
    fun `a line stays within the region limit`() {
        assertEquals(
            "A line may have up to 1000000 blocks",
            invalidInputMessage { line(origin, BlockCoordinates(1_000_000, 0, 0)) },
        )
    }
}
