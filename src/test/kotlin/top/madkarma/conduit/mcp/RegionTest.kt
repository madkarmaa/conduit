package top.madkarma.conduit.mcp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import top.madkarma.conduit.game.BlockCoordinates
import top.madkarma.conduit.game.BlockStateInfo
import top.madkarma.conduit.parseJson
import top.madkarma.conduit.service.world.BlockRegion

class RegionTest {
    private fun state(serialized: String) =
        BlockStateInfo(serialized.substringBefore('['), emptyMap(), serialized)

    private val air = state("minecraft:air")
    private val stone = state("minecraft:stone")
    private val stairs = state("minecraft:oak_stairs[facing=east]")

    // Three blocks along x, two along z and two layers, in x, then z, then y order.
    private val region =
        BlockRegion(
            min = BlockCoordinates(10, 64, -5),
            size = BlockCoordinates(3, 2, 2),
            palette = listOf(air, stone, stairs),
            blocks = listOf(1, 1, 1, 1, 2, 1, 0, 0, 0, 1, 2, 0),
        )

    @Test
    fun `draw prints each layer with rows along z and columns along x`() {
        val expected =
            """
            Region from 10 64 -5, size 3x2x2 (x, y, z)
            Rows run along z, columns along x.
            . minecraft:air
            @ minecraft:stone
            % minecraft:oak_stairs[facing=east]
            y=64
            @@@
            @%@
            y=65
            ...
            @%.
            """
                .trimIndent()

        assertEquals(expected, drawRegion(region).text)
    }

    @Test
    fun `draw replies with the region's corner and size`() {
        val expected =
            parseJson("""{"min": {"x": 10, "y": 64, "z": -5}, "size": {"x": 3, "y": 2, "z": 2}}""")

        assertEquals(expected, drawRegion(region).data)
    }

    @Test
    fun `draw shows every kind of air as a dot`() {
        val airs =
            BlockRegion(
                min = BlockCoordinates(0, 0, 0),
                size = BlockCoordinates(3, 1, 1),
                palette = listOf(stone, state("minecraft:cave_air"), state("minecraft:void_air")),
                blocks = listOf(0, 1, 2),
            )

        assertEquals("#..", drawRegion(airs).text.lines().last())
    }

    @Test
    fun `summary counts each state, most common first`() {
        val summary = summarizeRegion(region)

        val expectedText =
            """
            Region from 10 64 -5, size 3x2x2 (x, y, z)
            6 minecraft:stone
            4 minecraft:air
            2 minecraft:oak_stairs[facing=east]
            """
                .trimIndent()
        assertEquals(expectedText, summary.text)
        assertEquals(
            parseJson(
                """{"minecraft:stone": 6, "minecraft:air": 4, "minecraft:oak_stairs[facing=east]": 2}""",
            ),
            summary.data?.get("counts"),
        )
    }

    @Test
    fun `a palette fits while every state gets its own character`() {
        fun regionWith(states: Int) =
            region.copy(palette = (1..states).map { state("minecraft:block_$it") })

        assertTrue(paletteFits(regionWith(78)))
        assertFalse(paletteFits(regionWith(79)))
    }

    @Test
    fun `merging by block keeps one entry per block id`() {
        val westStairs = state("minecraft:oak_stairs[facing=west]")
        val mixed =
            region.copy(palette = listOf(air, stairs, westStairs), blocks = listOf(0, 1, 2, 2))

        val merged = byBlock(mixed)

        assertEquals(
            listOf("minecraft:air", "minecraft:oak_stairs"),
            merged.palette.map { it.serialized },
        )
        assertEquals(listOf(0, 1, 1, 1), merged.blocks)
    }
}
