package top.madkarma.conduit.service.player

import net.minecraft.core.Holder
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import top.madkarma.conduit.game.*

/** Sends a chat message, or an action bar message with overlay. */
internal fun MinecraftServer.sendMessage(ref: String, message: String, overlay: Boolean) {
    player(ref).sendSystemMessage(textComponent(message, registryAccess()), overlay)
}

/** Shows a title and subtitle, with the game's default timing. */
internal fun MinecraftServer.sendTitle(ref: String, title: String?, subtitle: String?) {
    val connection = player(ref).connection
    val registries = registryAccess()

    connection.send(ClientboundSetTitlesAnimationPacket(10, 70, 20))
    subtitle?.let {
        connection.send(ClientboundSetSubtitleTextPacket(textComponent(it, registries)))
    }
    title?.let { connection.send(ClientboundSetTitleTextPacket(textComponent(it, registries))) }
}

/** Plays a sound to one player, at their position unless one is given. */
internal fun MinecraftServer.playSound(
    ref: String,
    sound: String,
    position: Vector3?,
    volume: Float,
    pitch: Float,
) {
    val player = player(ref)
    val at = position?.toMc() ?: player.position()

    val packet =
        ClientboundSoundPacket(
            soundEvent(sound),
            SoundSource.MASTER,
            at.x,
            at.y,
            at.z,
            volume,
            pitch,
            player.random.nextLong(),
        )

    player.connection.send(packet)
}

// Sounds that are not registered, such as those of resource packs, only work by id.
private fun soundEvent(sound: String): Holder<SoundEvent> {
    val id = parseId(sound)
    return BuiltInRegistries.SOUND_EVENT.get(id)
        .map<Holder<SoundEvent>> { it }
        .orElseGet { Holder.direct(SoundEvent.createVariableRangeEvent(id)) }
}
