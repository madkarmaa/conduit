package top.madkarma.conduit.mcp

/** A tool whose actions answer with their own name, so tests can see which action ran. */
fun testTool(
    name: String = "test_tool",
    actions: Map<String, Access> = mapOf("get" to Access.READ),
    defaultAction: String? = null,
    seesOtherPlayers: Boolean = false,
    input: InputSchema = input { optional("player", PLAYER) },
): ToolDefinition =
    ToolDefinition(
        name = name,
        title = "Test tool",
        description = "A tool for tests.",
        input = input,
        actions =
            actions.mapValues { (action, access) -> AsyncAction(access) { _, _ -> Reply(action) } },
        defaultAction = defaultAction,
        seesOtherPlayers = seesOtherPlayers,
    )

/** A tool without arguments, whose one action has the tool's name. */
fun singleActionTool(name: String, action: Action): ToolDefinition =
    ToolDefinition(name, "Test tool", "A tool for tests.", input {}, mapOf(name to action))

val ALEX = PlayerIdentity("Alex", "00000000-0000-0000-0000-000000000001")
