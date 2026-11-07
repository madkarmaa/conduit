package top.madkarma.conduit.mcp

import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import top.madkarma.conduit.game.invalidInput

// Lists each offered action of a tool with its access in the tool's _meta, so a client can ask for
// confirmation before the destructive ones.
private const val ACTIONS_META_KEY = "top.madkarma.conduit/actions"

/** A tool as one policy offers it, with only the actions the policy allows. */
class OfferedTool(val definition: ToolDefinition, val actions: Map<String, Action>) {
    val name: String = definition.name

    private val defaultAction: String? =
        definition.defaultAction?.takeIf { it in actions } ?: actions.keys.singleOrNull()

    /** The input schema, with an action argument when the tool has several actions. */
    val schema: JsonObject = buildSchema()

    fun listing(): Tool =
        Tool(
            name = name,
            title = definition.title,
            description = definition.description,
            inputSchema =
                ToolSchema(
                    properties = schema["properties"] as JsonObject,
                    required =
                        (schema["required"] as? JsonArray)?.map { (it as JsonPrimitive).content },
                ),
            annotations =
                ToolAnnotations(
                    readOnlyHint = actions.values.all { it.access == Access.READ },
                    destructiveHint = actions.values.any { it.access == Access.DESTRUCTIVE },
                    openWorldHint = false,
                ),
            meta =
                buildJsonObject {
                    put(
                        ACTIONS_META_KEY,
                        buildJsonObject {
                            actions.forEach { (name, action) -> put(name, action.access.id) }
                        },
                    )
                },
        )

    fun select(input: ToolInput): Pair<String, Action> {
        val requested =
            input.stringOrNull("action") ?: defaultAction ?: invalidInput("Missing action")
        val action = actions[requested] ?: invalidInput("Unknown action $requested")
        return requested to action
    }

    private fun buildSchema(): JsonObject {
        val base = definition.input
        if (definition.actions.size == 1) return base.toJson()

        val action = enumSchema(actions.keys.toList(), default = defaultAction)
        val properties = linkedMapOf("action" to action) + base.properties
        val required =
            if (defaultAction == null) listOf("action") + base.required else base.required
        return InputSchema(properties, required).toJson()
    }
}

/** A tool call that passed every check and is ready to run. */
class PreparedCall(
    val tool: OfferedTool,
    val actionName: String,
    val action: Action,
    val input: ToolInput,
) {
    fun called() = CalledAction(tool.name, actionName, action.access, input.arguments)
}
