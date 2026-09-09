package us.timinc.mc.cobblemon.spawnnotification.api.broadcast

import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3

/**
 * Convenience class for any Broadcast that needs to determine which players to send a broadcast to.
 */
interface PlayerSpecific {
    fun getRelevantPlayers(
        world: ServerLevel,
        position: Vec3,
        broadcastRange: Int,
        playerLimit: Int,
        broadcastAcrossDimensions: Boolean,
    ): Iterable<ServerPlayer> {
        if (broadcastAcrossDimensions) return world.server.playerList.players

        val broadcastRangeSquared = broadcastRange.toDouble() * broadcastRange.toDouble()
        val eligiblePlayers = if (broadcastRange <= 0) world.players() else world.getPlayers { player ->
            player.distanceToSqr(position) <= broadcastRangeSquared
        }

        if (playerLimit > 0) {
            eligiblePlayers.sortBy { it.distanceToSqr(position) }
            return eligiblePlayers.take(playerLimit)
        }

        return eligiblePlayers
    }
}
