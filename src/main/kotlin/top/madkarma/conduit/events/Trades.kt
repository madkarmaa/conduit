package top.madkarma.conduit.events

import kotlinx.serialization.Serializable
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.trading.Merchant
import net.minecraft.world.item.trading.MerchantOffer
import top.madkarma.conduit.game.ItemInfo
import top.madkarma.conduit.game.toInfo

/**
 * A player traded with a villager or another merchant. Every trade is an event of its own, even
 * when one shift-click trades several times.
 */
@Serializable
data class TradeEvent(
    val player: String,
    val playerUuid: String,
    /** Not set when the merchant is not an entity. */
    val merchantUuid: String?,
    val costA: ItemInfo,
    val costB: ItemInfo?,
    val result: ItemInfo,
)

/** Publishes TRADE. */
object Trades {
    // Called from the merchant result slot mixin, only when the trade went through.
    @JvmStatic
    fun onTrade(player: Player, merchant: Merchant, offer: MerchantOffer?) {
        if (player !is ServerPlayer || offer == null) return

        EventBus.publish(EventType.TRADE) { tradePayload(player, merchant, offer) }
    }

    private fun tradePayload(
        player: ServerPlayer,
        merchant: Merchant,
        offer: MerchantOffer,
    ): TradeEvent {
        val registries = player.registryAccess()

        return TradeEvent(
            player = player.gameProfile.name,
            playerUuid = player.stringUUID,
            merchantUuid = (merchant as? Entity)?.stringUUID,
            costA = offer.costA.toInfo(registries),
            costB = offer.costB.takeUnless { it.isEmpty }?.toInfo(registries),
            result = offer.result.toInfo(registries),
        )
    }
}
