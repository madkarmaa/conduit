package top.madkarma.conduit.service.server

import top.madkarma.conduit.mcp.*

val DATAPACKS =
    ToolDefinition(
        name = "datapacks",
        title = "Datapacks",
        description =
            "list shows every datapack and whether it is enabled. enable and disable turn one " +
                "on or off and reload the data, like /datapack.",
        input =
            input {
                optional(
                    "id",
                    stringSchema(
                        "Datapack id, e.g. \"file/my_pack\". Required for enable and disable.",
                    ),
                )
            },
        defaultAction = "list",
        actions =
            mapOf(
                "list" to readAction { server, _ -> json(server.listDatapacks()) },
                "enable" to
                    AsyncAction(Access.DESTRUCTIVE) { input, _ ->
                        json(changeDatapackEnabled(input.string("id"), enabled = true))
                    },
                "disable" to
                    AsyncAction(Access.DESTRUCTIVE) { input, _ ->
                        json(changeDatapackEnabled(input.string("id"), enabled = false))
                    },
            ),
    )
