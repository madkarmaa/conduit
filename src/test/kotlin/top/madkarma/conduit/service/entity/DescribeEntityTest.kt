package top.madkarma.conduit.service.entity

import kotlin.test.Test
import kotlin.test.assertEquals
import top.madkarma.conduit.game.EntityInfo
import top.madkarma.conduit.game.Rotation
import top.madkarma.conduit.game.Vector3

class DescribeEntityTest {
    private fun entity(type: String, name: String, customName: String? = null) =
        EntityInfo(
            uuid = "id",
            networkId = 1,
            type = type,
            name = name,
            customName = customName,
            dimension = "minecraft:overworld",
            position = Vector3(1.0, 2.0, 3.0),
            rotation = Rotation(0f, 0f),
            velocity = Vector3(0.0, 0.0, 0.0),
            onGround = true,
            alive = true,
            tags = emptyList(),
            health = null,
            maxHealth = null,
            passengers = emptyList(),
            vehicle = null,
        )

    @Test
    fun `players show their name, and mobs only a custom one`() {
        assertEquals(
            "id minecraft:player \"Toto\" minecraft:overworld 1 2 3",
            describeEntity(entity("minecraft:player", "Toto")),
        )
        assertEquals(
            "id minecraft:wolf minecraft:overworld 1 2 3",
            describeEntity(entity("minecraft:wolf", "Wolf")),
        )
        assertEquals(
            "id minecraft:axolotl \"Crystal\" minecraft:overworld 1 2 3",
            describeEntity(entity("minecraft:axolotl", "Crystal", "Crystal")),
        )
    }
}
