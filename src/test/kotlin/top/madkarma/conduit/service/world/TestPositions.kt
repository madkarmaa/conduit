package top.madkarma.conduit.service.world

import net.minecraft.core.BlockPos
import top.madkarma.conduit.game.BlockCoordinates
import top.madkarma.conduit.game.toCoordinates

/** The positions as coordinates, which compare by value and print readably. */
fun Sequence<BlockPos>.coordinates(): List<BlockCoordinates> = map { it.toCoordinates() }.toList()
