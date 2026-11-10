package top.madkarma.conduit.service.registry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.jsonObject
import top.madkarma.conduit.parseJson

class LootSummariesTest {
    @Test
    fun `a pool shows its rolls and each entry's count, enchantment and chance`() {
        val pool =
            parseJson(
                    """
                    {"rolls": {"min": 2, "max": 6, "type": "minecraft:uniform"}, "entries": [
                      {"type": "minecraft:item", "name": "minecraft:diamond", "weight": 3,
                       "modifier": {"type": "minecraft:set_count", "count": {"min": 2, "max": 7}}},
                      {"type": "minecraft:item", "name": "minecraft:diamond_sword",
                       "modifier": [{"type": "minecraft:enchant_with_levels",
                                     "levels": {"min": 20, "max": 39}}]},
                      {"type": "minecraft:empty", "weight": 296}
                    ]}
                    """,
                )
                .jsonObject

        assertEquals(
            "2-6 rolls: diamond 2-7 1%, diamond_sword enchanted 20-39 levels <1%, nothing 99%",
            describePool(pool),
        )
    }

    @Test
    fun `groups count as their children, and a pool chance and named tables show`() {
        val pool =
            parseJson(
                    """
                    {"rolls": 1, "condition": {"type": "minecraft:random_chance", "chance": 0.25},
                     "entries": [
                      {"type": "minecraft:loot_table", "value": "minecraft:chests/rare"},
                      {"type": "minecraft:group", "children": [
                        {"type": "minecraft:tag", "name": "minecraft:wool"}
                      ]}
                    ]}
                    """,
                )
                .jsonObject

        assertEquals(
            "1 roll at 25%: table minecraft:chests/rare 50%, #wool 50%",
            describePool(pool),
        )
    }
}
