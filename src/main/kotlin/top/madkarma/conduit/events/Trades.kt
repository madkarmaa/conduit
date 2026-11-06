package top.madkarma.conduit.events

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.trading.Merchant
import net.minecraft.world.item.trading.MerchantOffer
import top.madkarma.conduit.game.toProto
import top.madkarma.conduit.proto.EventType
import top.madkarma.conduit.proto.TradeEvent
import top.madkarma.conduit.proto.tradeEvent

/** Publishes EVENT_TYPE_TRADE. */
object Trades {
    // Called from the merchant result slot mixin, only when the trade went through.
    @JvmStatic
    fun onTrade(player: Player, merchant: Merchant, offer: MerchantOffer?) {
        if (player !is ServerPlayer || offer == null) return

        EventBus.publish(EventType.EVENT_TYPE_TRADE, accepts = { it.wantsPlayer(player) }) {
            trade = tradePayload(player, merchant, offer)
        }
    }

    private fun tradePayload(
        player: ServerPlayer,
        merchant: Merchant,
        offer: MerchantOffer,
    ): TradeEvent = tradeEvent {
        this.player = player.gameProfile.name
        playerUuid = player.stringUUID
        if (merchant is Entity) merchantUuid = merchant.stringUUID

        val registries = player.registryAccess()
        costA = offer.costA.toProto(registries)
        if (!offer.costB.isEmpty) costB = offer.costB.toProto(registries)
        result = offer.result.toProto(registries)
    }
}
