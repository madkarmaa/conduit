package top.madkarma.conduit.service.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import net.minecraft.nbt.TagParser
import top.madkarma.conduit.game.ConduitError

class SavedNbtTest {
    private val data =
        TagParser.parseCompoundFully(
            "{LastDeathLocation:{dimension:\"minecraft:overworld\",pos:[I;519,60,6252]}," +
                "Inventory:[{Slot:0b,id:\"minecraft:dirt\"},{Slot:1b,id:\"minecraft:stone\"}]}",
        )

    @Test
    fun `a path returns only the tags it matches`() {
        assertEquals(
            "{dimension:\"minecraft:overworld\",pos:[I;519,60,6252]}",
            tagsAt(data, "LastDeathLocation"),
        )
        assertEquals("\"minecraft:dirt\"\n\"minecraft:stone\"", tagsAt(data, "Inventory[].id"))
    }

    @Test
    fun `a path that matches nothing is not found`() {
        assertFailsWith<ConduitError.NotFound> { tagsAt(data, "Missing") }
    }
}
