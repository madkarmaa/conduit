package top.madkarma.conduit.events

import kotlinx.serialization.json.JsonObject
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState

object GameEvents {
    fun register() {
        registerPlayerEvents()
        registerInteractionEvents()
        registerEntityEvents()
        registerServerEvents()
        InventoryChanges.register()
    }

    // Called from the block placement mixin.
    @JvmStatic
    fun onBlockPlaced(
        level: Level,
        player: Player?,
        position: BlockPos,
        state: BlockState,
        item: ItemStack,
    ) {
        if (level.isClientSide) return

        EventBus.publish(EventType.BLOCK_PLACE) {
            blockPayload(level, player, position, state, item)
        }
    }

    // Called from the command execution mixin.
    @JvmStatic
    fun onCommand(source: String, command: String) {
        EventBus.publish(EventType.COMMAND) {
            CommandEvent(source, hidePasswords(command.removePrefix("/")))
        }
    }

    private fun registerPlayerEvents() {
        ServerMessageEvents.CHAT_MESSAGE.register { message, sender, _ ->
            EventBus.publish(EventType.CHAT) {
                chatPayload(sender, message.signedContent())
            }
        }

        ServerPlayerEvents.JOIN.register { player ->
            EventBus.publish(EventType.PLAYER_JOIN) {
                playerPayload(player)
            }
        }

        ServerPlayerEvents.LEAVE.register { player ->
            EventBus.publish(EventType.PLAYER_LEAVE) {
                playerPayload(player)
            }
        }

        ServerPlayerEvents.AFTER_RESPAWN.register { _, player, _ ->
            EventBus.publish(EventType.PLAYER_RESPAWN) {
                playerPayload(player)
            }
        }

        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register { player, from, to ->
            EventBus.publish(EventType.PLAYER_CHANGE_DIMENSION) {
                dimensionChangePayload(player, from, to)
            }
        }
    }

    private fun registerInteractionEvents() {
        PlayerBlockBreakEvents.AFTER.register { level, player, position, state, _ ->
            EventBus.publish(EventType.BLOCK_BREAK) {
                blockPayload(level, player, position, state, null)
            }
        }

        UseBlockCallback.EVENT.register { player, level, hand, hit ->
            if (level.isClientSide) return@register InteractionResult.PASS

            EventBus.publish(EventType.BLOCK_USE) {
                val state = level.getBlockState(hit.blockPos)
                val item = player.getItemInHand(hand)
                blockPayload(level, player, hit.blockPos, state, item)
            }

            InteractionResult.PASS
        }

        AttackEntityCallback.EVENT.register { player, level, _, target, _ ->
            if (level.isClientSide) return@register InteractionResult.PASS

            EventBus.publish(EventType.ENTITY_ATTACK) {
                attackPayload(player, target)
            }

            InteractionResult.PASS
        }
    }

    private fun registerEntityEvents() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register { entity, source, _, damageTaken, _ ->
            EventBus.publish(EventType.ENTITY_DAMAGE) {
                damagePayload(entity, source, damageTaken)
            }
        }

        ServerLivingEntityEvents.AFTER_DEATH.register { entity, source ->
            EventBus.publish(EventType.ENTITY_DEATH) {
                deathPayload(entity, source)
            }
        }
    }

    private fun registerServerEvents() {
        val lifecycle = JsonObject(emptyMap())

        ServerLifecycleEvents.SERVER_STARTED.register {
            EventBus.publish(EventType.SERVER_STARTED) { lifecycle }
        }

        ServerLifecycleEvents.SERVER_STOPPING.register {
            EventBus.publish(EventType.SERVER_STOPPING) { lifecycle }
        }
    }
}
