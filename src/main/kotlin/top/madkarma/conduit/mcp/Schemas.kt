package top.madkarma.conduit.mcp

// Schemas that several tools share.

val DIMENSION =
    stringSchema("Dimension id, e.g. \"minecraft:the_nether\". Defaults to the overworld.")

val BLOCK_POS = objectSchema {
    required("x", integerSchema())
    required("y", integerSchema())
    required("z", integerSchema())
}

val VEC3 = objectSchema {
    required("x", numberSchema())
    required("y", numberSchema())
    required("z", numberSchema())
}

val ROTATION =
    objectSchema("Degrees. Yaw 0 faces south and pitch -90 faces up.") {
        required("yaw", numberSchema())
        required("pitch", numberSchema())
    }

val UUID = stringSchema("Entity UUID.")

val PLAYER = stringSchema("Player name or UUID.")

val SNBT = stringSchema("SNBT, e.g. {CustomName:\"Bob\",NoAI:1b}.")

val ITEM = objectSchema {
    required("id", stringSchema("Item id, e.g. \"minecraft:diamond_sword\"."))
    optional("count", integerSchema(min = 1, default = 1))
    optional(
        "components",
        stringSchema(
            "Data component patch as SNBT, e.g. {\"minecraft:custom_name\":\"Excalibur\"}.",
        ),
    )
}

val TEXT = stringSchema("Plain text, or a JSON text component when it starts with { or [.")

val LIMIT = integerSchema("Maximum results to return.", min = 1, max = 1000, default = 50)

val BLOCK_STATE =
    stringSchema("Block state in command syntax, e.g. \"minecraft:oak_stairs[facing=east]\".")

val NO_UPDATES =
    booleanSchema("Skip neighbor updates and physics, like structure placement.", default = false)
