package top.madkarma.conduit.events

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.chunk.LevelChunk
import top.madkarma.conduit.proto.ChunkEvent
import top.madkarma.conduit.proto.EventType
import top.madkarma.conduit.proto.chunkEvent

/** Publishes EVENT_TYPE_CHUNK_LOAD and EVENT_TYPE_CHUNK_UNLOAD. */
object ChunkLifecycle {
    fun register() {
        ServerChunkEvents.CHUNK_LOAD.register { level, chunk, _ ->
            EventBus.publish(EventType.EVENT_TYPE_CHUNK_LOAD) {
                chunkLoad = chunkPayload(level, chunk)
            }
        }

        ServerChunkEvents.CHUNK_UNLOAD.register { level, chunk ->
            EventBus.publish(EventType.EVENT_TYPE_CHUNK_UNLOAD) {
                chunkUnload = chunkPayload(level, chunk)
            }
        }
    }

    private fun chunkPayload(level: ServerLevel, chunk: LevelChunk): ChunkEvent = chunkEvent {
        dimension = level.dimension().identifier().toString()
        chunkX = chunk.pos.x()
        chunkZ = chunk.pos.z()
    }
}
