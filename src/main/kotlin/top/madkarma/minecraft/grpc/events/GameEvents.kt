package top.madkarma.minecraft.grpc.events

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
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
import top.madkarma.minecraft.grpc.proto.EventType
import top.madkarma.minecraft.grpc.proto.LifecycleEvent
import top.madkarma.minecraft.grpc.proto.commandEvent

object GameEvents {
    fun register() {
        registerPlayerEvents()
        registerInteractionEvents()
        registerEntityEvents()
        registerServerEvents()
        PlayerMoves.register()
        EntityLifecycle.register()
        ChunkLifecycle.register()
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

        EventBus.publish(EventType.EVENT_TYPE_BLOCK_PLACE) {
            blockPlace = blockPayload(level, player, position, state, item)
        }
    }

    // Called from the command execution mixin.
    @JvmStatic
    fun onCommand(source: String, command: String) {
        EventBus.publish(EventType.EVENT_TYPE_COMMAND) {
            this.command = commandEvent {
                this.source = source
                this.command = command.removePrefix("/")
            }
        }
    }

    private fun registerPlayerEvents() {
        ServerMessageEvents.CHAT_MESSAGE.register { message, sender, _ ->
            EventBus.publish(EventType.EVENT_TYPE_CHAT) {
                chat = chatPayload(sender, message.signedContent())
            }
        }

        ServerPlayerEvents.JOIN.register { player ->
            EventBus.publish(EventType.EVENT_TYPE_PLAYER_JOIN) {
                playerJoin = playerPayload(player)
            }
        }

        ServerPlayerEvents.LEAVE.register { player ->
            EventBus.publish(EventType.EVENT_TYPE_PLAYER_LEAVE) {
                playerLeave = playerPayload(player)
            }
        }

        ServerPlayerEvents.AFTER_RESPAWN.register { _, player, _ ->
            EventBus.publish(EventType.EVENT_TYPE_PLAYER_RESPAWN) {
                playerRespawn = playerPayload(player)
            }
        }

        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register { player, from, to ->
            EventBus.publish(EventType.EVENT_TYPE_PLAYER_CHANGE_DIMENSION) {
                playerChangeDimension = dimensionChangePayload(player, from, to)
            }
        }
    }

    private fun registerInteractionEvents() {
        PlayerBlockBreakEvents.AFTER.register { level, player, position, state, _ ->
            EventBus.publish(EventType.EVENT_TYPE_BLOCK_BREAK) {
                blockBreak = blockPayload(level, player, position, state, null)
            }
        }

        UseBlockCallback.EVENT.register { player, level, hand, hit ->
            if (level.isClientSide) return@register InteractionResult.PASS

            EventBus.publish(EventType.EVENT_TYPE_BLOCK_USE) {
                val state = level.getBlockState(hit.blockPos)
                val item = player.getItemInHand(hand)
                blockUse = blockPayload(level, player, hit.blockPos, state, item)
            }

            InteractionResult.PASS
        }

        AttackEntityCallback.EVENT.register { player, level, _, target, _ ->
            if (level.isClientSide) return@register InteractionResult.PASS

            EventBus.publish(EventType.EVENT_TYPE_ENTITY_ATTACK) {
                entityAttack = attackPayload(player, target)
            }

            InteractionResult.PASS
        }
    }

    private fun registerEntityEvents() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register { entity, source, _, damageTaken, _ ->
            EventBus.publish(EventType.EVENT_TYPE_ENTITY_DAMAGE) {
                entityDamage = damagePayload(entity, source, damageTaken)
            }
        }

        ServerLivingEntityEvents.AFTER_DEATH.register { entity, source ->
            EventBus.publish(EventType.EVENT_TYPE_ENTITY_DEATH) {
                entityDeath = deathPayload(entity, source)
            }
        }
    }

    private fun registerServerEvents() {
        val lifecycle = LifecycleEvent.getDefaultInstance()

        ServerTickEvents.END_SERVER_TICK.register { server ->
            EventBus.publish(
                EventType.EVENT_TYPE_SERVER_TICK,
                accepts = { server.tickCount % it.tickInterval == 0 },
            ) {
                serverTick = tickPayload(server)
            }
        }

        ServerLifecycleEvents.SERVER_STARTED.register {
            EventBus.publish(EventType.EVENT_TYPE_SERVER_STARTED) { serverStarted = lifecycle }
        }

        ServerLifecycleEvents.SERVER_STOPPING.register {
            EventBus.publish(EventType.EVENT_TYPE_SERVER_STOPPING) { serverStopping = lifecycle }
        }
    }
}
