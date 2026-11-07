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
import top.madkarma.conduit.game.invalidInput
import top.madkarma.conduit.game.parseId
import top.madkarma.conduit.game.player
import top.madkarma.conduit.game.toComponent
import top.madkarma.conduit.game.toMc
import top.madkarma.conduit.proto.PlaySoundRequest
import top.madkarma.conduit.proto.SendMessageRequest
import top.madkarma.conduit.proto.SendTitleRequest

internal fun MinecraftServer.sendMessage(request: SendMessageRequest) {
    val message = request.message.toComponent(registryAccess())
    player(request.player).sendSystemMessage(message, request.overlay)
}

internal fun MinecraftServer.sendTitle(request: SendTitleRequest) {
    val connection = player(request.player).connection
    val registries = registryAccess()

    val fadeIn = if (request.hasFadeIn()) request.fadeIn else 10
    val stay = if (request.hasStay()) request.stay else 70
    val fadeOut = if (request.hasFadeOut()) request.fadeOut else 20

    connection.send(ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut))

    if (request.hasSubtitle()) {
        connection.send(
            ClientboundSetSubtitleTextPacket(request.subtitle.toComponent(registries)),
        )
    }

    if (request.hasTitle()) {
        connection.send(
            ClientboundSetTitleTextPacket(request.title.toComponent(registries)),
        )
    }
}

internal fun MinecraftServer.playSound(request: PlaySoundRequest) {
    val player = player(request.player)

    val position = if (request.hasPosition()) request.position.toMc() else player.position()
    val volume = if (request.volume > 0) request.volume else 1f
    val pitch = if (request.pitch > 0) request.pitch else 1f

    val packet =
        ClientboundSoundPacket(
            soundEvent(request.sound),
            soundCategory(request.category),
            position.x,
            position.y,
            position.z,
            volume,
            pitch,
            player.random.nextLong(),
        )

    player.connection.send(packet)
}

// Unregistered sounds, e.g. from resource packs, can only be sent by id.
private fun soundEvent(sound: String): Holder<SoundEvent> {
    val id = parseId(sound)
    return BuiltInRegistries.SOUND_EVENT.get(id)
        .map<Holder<SoundEvent>> { it }
        .orElseGet { Holder.direct(SoundEvent.createVariableRangeEvent(id)) }
}

private fun soundCategory(category: String): SoundSource {
    if (category.isBlank()) return SoundSource.MASTER
    return SoundSource.entries.firstOrNull { it.getName() == category.lowercase() }
        ?: invalidInput("Unknown sound category: $category")
}
