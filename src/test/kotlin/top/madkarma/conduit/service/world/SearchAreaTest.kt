package top.madkarma.conduit.service.world

import kotlin.test.Test
import kotlin.test.assertEquals
import net.minecraft.core.BlockPos
import top.madkarma.conduit.invalidInputMessage
import top.madkarma.conduit.mcp.ToolInput
import top.madkarma.conduit.mcp.ToolPolicy
import top.madkarma.conduit.parseJson

class SearchAreaTest {
    private fun area(arguments: String): BlockArea =
        searchArea(ToolInput(parseJson(arguments), ToolPolicy("test")))

    private fun areaMessage(arguments: String): String = invalidInputMessage { area(arguments) }

    @Test
    fun `a radius searches a cube around its center`() {
        val area = area("""{"center": {"x": 10, "y": 64, "z": -10}, "radius": 5}""")

        assertEquals(BlockPos(5, 59, -15), area.min)
        assertEquals(BlockPos(15, 69, -5), area.max)
        assertEquals(BlockPos(10, 64, -10), area.center)
    }

    @Test
    fun `from and to search around their middle, with corners in any order`() {
        val area = area("""{"from": {"x": 10, "y": 0, "z": 0}, "to": {"x": 0, "y": 20, "z": -6}}""")

        assertEquals(BlockPos(0, 0, -6), area.min)
        assertEquals(BlockPos(10, 20, 0), area.max)
        assertEquals(BlockPos(5, 10, -3), area.center)
    }

    @Test
    fun `a radius wins over from and to`() {
        val area =
            area(
                """{"center": {"x": 0, "y": 0, "z": 0}, "radius": 1, "from": {"x": 50, "y": 0, "z": 0}, "to": {"x": 60, "y": 0, "z": 0}}""",
            )

        assertEquals(BlockPos(1, 1, 1), area.max)
    }

    @Test
    fun `needs from and to, or center and radius`() {
        assertEquals(
            "Give from and to, or center and radius",
            areaMessage("""{"from": {"x": 0, "y": 0, "z": 0}}"""),
        )
        assertEquals(
            "Give from and to, or center and radius",
            areaMessage("""{"center": {"x": 0, "y": 0, "z": 0}}"""),
        )
    }

    @Test
    fun `a radius needs a center`() {
        assertEquals("Missing center", areaMessage("""{"radius": 4}"""))
    }

    @Test
    fun `counts the chunks the area touches`() {
        fun chunks(fromX: Int, fromZ: Int, toX: Int, toZ: Int): Long =
            area(
                    """{"from": {"x": $fromX, "y": 0, "z": $fromZ}, "to": {"x": $toX, "y": 0, "z": $toZ}}""",
                )
                .chunkCount

        assertEquals(1, chunks(0, 0, 15, 15))
        assertEquals(2, chunks(0, 0, 16, 15))
        assertEquals(4, chunks(-1, -1, 0, 0))
        assertEquals(9, chunks(-16, -16, 31, 31))
    }
}
