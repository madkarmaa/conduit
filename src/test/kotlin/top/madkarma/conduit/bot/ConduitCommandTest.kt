package top.madkarma.conduit.bot

import com.mojang.brigadier.CommandDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import net.minecraft.SharedConstants
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.server.Bootstrap
import net.minecraft.server.permissions.LevelBasedPermissionSet
import net.minecraft.server.permissions.PermissionSet

class ConduitCommandTest {
    init {
        // The permission checks come from the game's registries.
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
    }

    private val dispatcher = CommandDispatcher<CommandSourceStack>().also(ConduitCommand::register)
    private val operator = source(LevelBasedPermissionSet.GAMEMASTER)
    private val player = source(PermissionSet.NO_PERMISSIONS)

    private fun source(permissions: PermissionSet) = Commands.createCompilationContext(permissions)

    // The node that runs a command, such as the status literal or the request argument.
    private fun runner(command: String, source: CommandSourceStack): String {
        val parsed = dispatcher.parse(command, source)
        assertFalse(parsed.reader.canRead(), "$command did not parse")
        return parsed.context.nodes.last().node.name
    }

    private fun subcommandsFor(source: CommandSourceStack): List<String> =
        dispatcher.root.getChild("conduit").children.filter { it.canUse(source) }.map { it.name }

    @Test
    fun `other players only see cancel and the request`() {
        assertEquals(listOf("cancel", "request"), subcommandsFor(player).sorted())
        assertEquals(
            listOf("access", "cancel", "forget", "reload", "request", "status"),
            subcommandsFor(operator).sorted(),
        )
    }

    @Test
    fun `a request that starts with a subcommand another player may not use reaches the bot`() {
        assertEquals("status", runner("conduit status", operator))
        assertEquals("request", runner("conduit status", player))
        assertEquals("request", runner("conduit access the nether portal", player))
    }

    @Test
    fun `everyone cancels, and only operators name a target`() {
        assertEquals("cancel", runner("conduit cancel", player))
        assertEquals("target", runner("conduit cancel Alex", operator))
        assertEquals("request", runner("conduit cancel Alex", player))
    }

    @Test
    fun `longer requests that start with a subcommand reach the bot`() {
        assertEquals("request", runner("conduit status of the farm", operator))
        assertEquals("request", runner("conduit cancel the build now", operator))
        assertEquals("full", runner("conduit access allow Alex full", operator))
    }
}
