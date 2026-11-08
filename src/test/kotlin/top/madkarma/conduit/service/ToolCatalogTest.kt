package top.madkarma.conduit.service

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import top.madkarma.conduit.bot.Asker
import top.madkarma.conduit.bot.Mode
import top.madkarma.conduit.bot.toolPolicy
import top.madkarma.conduit.config.VALID_CONFIG
import top.madkarma.conduit.config.problems
import top.madkarma.conduit.game.ConduitError
import top.madkarma.conduit.mcp.Access
import top.madkarma.conduit.mcp.ApprovalGate
import top.madkarma.conduit.mcp.OfferedTool
import top.madkarma.conduit.mcp.ToolSession
import top.madkarma.conduit.mcp.validateArguments

class ToolCatalogTest {
    // MCP hosts and approvals rely on these names and access marks, so tools may add actions but
    // never rename or remove these.
    private val published =
        mapOf(
            "server_info" to "summary=read mods=read backups=read",
            "server_admin" to "save=write backup=write stop=destructive",
            "run_command" to "run=destructive",
            "datapacks" to
                "list=read enable=destructive disable=destructive files=read read=read " +
                    "write=write delete=destructive reload=write",
            "world_info" to "info=read game_rules=read height=read chunk=read",
            "edit_world" to
                "time=write weather=write spawn=write force_chunk=write border=destructive " +
                    "game_rule=destructive explode=destructive",
            "get_blocks" to "block=read region=read container=read",
            "edit_blocks" to
                "set=write fill=destructive shape=destructive break=write merge_nbt=write",
            "structures" to "list=read save=write place=destructive copy=destructive",
            "undo" to "list=read undo=destructive",
            "find" to "blocks=read items=read structure=read biome=read poi=read",
            "entities" to "list=read info=read nbt=read effects=read attributes=read",
            "edit_entity" to
                "spawn=write remove=destructive name=write tags=write health=write " +
                    "damage=write nbt=write add_effect=write remove_effect=write " +
                    "set_attribute=write",
            "move_entity" to
                "teleport=write rotate=write push=write mount=write dismount=write walk=write " +
                    "stop=write",
            "list_players" to "online=read known=read banned=read whitelisted=read",
            "get_player" to "info=read stats=read advancements=read saved=read saved_nbt=read",
            "edit_player" to
                "game_mode=write food=write experience=write operator=destructive " +
                    "grant_advancement=write revoke_advancement=write " +
                    "saved_merge_nbt=destructive saved_set_slot=destructive",
            "moderation" to
                "kick=destructive ban=destructive pardon=write whitelist_add=write " +
                    "whitelist_remove=write whitelist_enabled=destructive",
            "inventory" to
                "read=read set_slot=write clear=destructive select_slot=write give=write",
            "notify" to "message=write title=write sound=write bossbar=write remove_bossbar=write",
            "scoreboard" to
                "objectives=read scores=read add_objective=write remove_objective=destructive " +
                    "set_score=write add_score=write reset_score=write display=write " +
                    "teams=read create_team=write modify_team=write remove_team=destructive " +
                    "join_team=write leave_team=write",
            "game_data" to
                "registries=read entries=read tags=read recipes=read recipe=read " +
                    "loot_table=read",
            "events" to "wait=read",
            "batch" to "run=destructive",
        )

    private val chatPolicy =
        VALID_CONFIG.toolPolicy(Mode.CHAT, Asker("Alex", UUID(0, 1)), ApprovalGate {})

    @Test
    fun `every published tool keeps its actions and their access`() {
        val catalog = ALL_TOOLS.associateBy { it.name }

        for ((name, actions) in published) {
            val tool = catalog[name] ?: fail("The tool $name is missing")
            for (entry in actions.split(" ")) {
                val (action, access) = entry.split("=")
                assertEquals(access, tool.actions[action]?.access?.id, "$name.$action")
            }
        }
    }

    @Test
    fun `tool names are unique`() {
        val names = ALL_TOOLS.map { it.name }

        assertEquals(names.distinct(), names)
    }

    @Test
    fun `every default action is one of the tool's actions`() {
        for (tool in ALL_TOOLS) {
            val default = tool.defaultAction ?: continue
            assertTrue(default in tool.actions, "${tool.name} defaults to $default")
        }
    }

    @Test
    fun `every schema describes its required fields and validates`() {
        for (tool in ALL_TOOLS) {
            val schema = OfferedTool(tool, tool.actions).schema
            val properties = schema.getValue("properties") as JsonObject
            val required =
                (schema["required"] as? JsonArray).orEmpty().map { (it as JsonPrimitive).content }
            assertTrue(properties.keys.containsAll(required), "${tool.name} requires $required")

            try {
                validateArguments(schema, JsonObject(emptyMap()))
            } catch (_: ConduitError.InvalidInput) {
                // Tools with required fields refuse empty arguments, which still shows the schema
                // works.
            }
        }
    }

    @Test
    fun `the default config fits the catalog`() {
        val toolActions = ALL_TOOLS.associate { it.name to it.actions.keys }

        assertEquals(emptyList(), VALID_CONFIG.problems(toolActions))
    }

    @Test
    fun `chat mode offers its default tools and only actions that read`() {
        val session = ToolSession(chatPolicy, ALL_TOOLS)

        assertEquals(chatPolicy.allowedTools, session.offered.keys)
        assertTrue(session.offered.values.none { it.definition.seesOtherPlayers })
        assertTrue(
            session.offered.values.all { tool ->
                tool.actions.values.all { it.access == Access.READ }
            },
        )
        assertEquals(setOf("read"), session.offered.getValue("inventory").actions.keys)
    }
}
