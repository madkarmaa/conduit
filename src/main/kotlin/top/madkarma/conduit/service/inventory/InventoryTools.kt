package top.madkarma.conduit.service.inventory

import net.minecraft.server.MinecraftServer
import top.madkarma.conduit.game.BlockCoordinates
import top.madkarma.conduit.game.ItemInfo
import top.madkarma.conduit.game.invalidInput
import top.madkarma.conduit.mcp.*

internal val SLOT = integerSchema(min = 0, max = 40)

val INVENTORY =
    ToolDefinition(
        name = "inventory",
        title = "Inventory",
        description =
            "Works on an online player's inventory, with pos on a container such as a chest, " +
                "or with uuid on an entity's items. A double chest counts as one container, " +
                "and a lectern's book and a campfire's food count as containers of one item " +
                "per slot. " +
                "Entities name their slots like the /item command, such as \"container.3\", " +
                "\"armor.head\", \"weapon.mainhand\", \"horse.saddle\", \"mob.inventory.0\" " +
                "or an item frame's \"contents\", and read lists the slots that hold items. give " +
                "fills an entity's storage, such as a chest minecart's, a donkey's chest or a " +
                "villager's inventory. With ender_chest, the actions but " +
                "select_slot work on the player's ender chest, slots 0-26, instead. read lists " +
                "the items. set_slot puts an item in a slot or empties it. clear empties " +
                "every slot. select_slot picks a player's held hotbar slot. give adds an item " +
                "stack. Items that do not fit drop at a player's feet, or stay out of a " +
                "container. Player slots 0-8 are the hotbar, 9-35 the main inventory, 36-39 " +
                "armor from feet to head and 40 the offhand.",
        input =
            input {
                optional("player", PLAYER.described("Player name or UUID. Leave it out for pos."))
                optional("dimension", DIMENSION)
                optional("pos", BLOCK_POS.described("A container's position instead of a player."))
                optional("uuid", UUID.described("An entity, not a player, instead of a player."))
                optional(
                    "ender_chest",
                    booleanSchema("Use the player's ender chest instead.", default = false),
                )
                optional(
                    "slot",
                    integerSchema("Required for set_slot and select_slot.", min = 0, max = 53),
                )
                optional(
                    "slot_name",
                    stringSchema("An entity's slot, such as \"armor.head\". For set_slot."),
                )
                optional(
                    "item",
                    ITEM.described(
                        "Required for give. For set_slot, leave it out to empty the slot.",
                    ),
                )
            },
        actions =
            mapOf(
                "read" to readAction { server, input -> server.readInventory(input) },
                "set_slot" to
                    writeAction { server, input ->
                        done("Slot ${server.setSlot(input)} set")
                    },
                "clear" to
                    destructiveAction { server, input ->
                        server.clear(input)
                        done("Cleared")
                    },
                "select_slot" to
                    writeAction { server, input ->
                        server.selectHotbarSlot(input.string("player"), input.int("slot"))
                        done("Selected hotbar slot ${input.int("slot")}")
                    },
                "give" to writeAction { server, input -> server.give(input) },
            ),
    )

// A call names a player, a container's position or an entity. This returns the entity.
private fun entityTarget(input: ToolInput): String? {
    val uuid = input.stringOrNull("uuid") ?: return null
    if (input.has("player") || input.has("pos")) invalidInput("Give player, pos or uuid, not more")
    if (input.boolean("ender_chest")) invalidInput("ender_chest needs a player, not uuid")
    return uuid
}

// A call names a player, a container's position or an entity. This returns the position.
private fun containerPos(input: ToolInput): BlockCoordinates? {
    val pos = input.decodeOrNull<BlockCoordinates>("pos") ?: return null
    if (input.has("player")) invalidInput("Give player or pos, not both")
    if (input.boolean("ender_chest")) invalidInput("ender_chest needs a player, not pos")
    return pos
}

private fun MinecraftServer.readInventory(input: ToolInput): Reply {
    entityTarget(input)?.let {
        return describeEntityItems(entityItems(it))
    }

    val pos = containerPos(input)
    if (pos != null) {
        val contents = containerContents(input.stringOrNull("dimension"), pos)
        return describeInventory(
            "Non-empty slots of the container at ${pos.x} ${pos.y} ${pos.z}:",
            contents,
        )
    }

    val player = input.string("player")
    if (input.boolean("ender_chest")) {
        return describeInventory("Non-empty ender chest slots:", enderChestOf(player))
    }

    val inventory = inventoryOf(player)
    return describeInventory(
        "Selected hotbar slot ${inventory.selectedSlot}; non-empty slots:",
        inventory,
    )
}

// Returns the slot it set.
private fun MinecraftServer.setSlot(input: ToolInput): String {
    val item = input.decodeOrNull<ItemInfo>("item")
    entityTarget(input)?.let { uuid ->
        input.require("slot_name")
        changeEntitySlot(uuid, input.string("slot_name"), item)
        return input.string("slot_name")
    }

    val slot = input.int("slot")
    val pos = containerPos(input)
    if (pos != null) changeContainerSlot(input.stringOrNull("dimension"), pos, slot, item)
    else changeInventorySlot(input.string("player"), slot, item, input.boolean("ender_chest"))
    return slot.toString()
}

private fun MinecraftServer.clear(input: ToolInput) {
    entityTarget(input)?.let {
        return clearEntityItems(it)
    }

    val pos = containerPos(input)
    if (pos != null) clearContainer(input.stringOrNull("dimension"), pos)
    else clearInventory(input.string("player"), input.boolean("ender_chest"))
}

private fun MinecraftServer.give(input: ToolInput): Reply {
    val item = input.decode<ItemInfo>("item")
    val gave = "Gave ${item.count} ${item.id}"

    entityTarget(input)?.let { uuid ->
        val left = giveEntityItem(uuid, item)
        return done(if (left > 0) "$gave, $left did not fit" else gave)
    }

    val pos = containerPos(input)
    if (pos != null) {
        val left = addToContainer(input.stringOrNull("dimension"), pos, item)
        return done(if (left > 0) "$gave, $left did not fit" else gave)
    }

    val dropped = giveItem(input.string("player"), item, input.boolean("ender_chest"))
    return done(if (dropped > 0) "$gave, $dropped dropped" else gave)
}

internal fun describeInventory(header: String, inventory: InventoryContents): Reply =
    lines(
        header,
        inventory.slots.map { (slot, item) ->
            val components = if (item.components.isEmpty()) "" else " ${item.components}"
            "$slot: ${item.count} ${item.id}$components"
        },
        dataOf("slots" to encoded(inventory.slots)),
    )

private fun describeEntityItems(slots: List<EntitySlot>): Reply =
    lines(
        "Entity slots that hold items:",
        slots.map { (slot, item) ->
            val components = if (item.components.isEmpty()) "" else " ${item.components}"
            "$slot: ${item.count} ${item.id}$components"
        },
        dataOf("slots" to encoded(slots)),
    )
