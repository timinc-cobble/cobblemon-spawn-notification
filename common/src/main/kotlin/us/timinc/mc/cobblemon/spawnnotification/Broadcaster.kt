package us.timinc.mc.cobblemon.spawnnotification

import net.minecraft.resources.ResourceLocation
import us.timinc.mc.cobblemon.spawnnotification.api.broadcast.BroadcastContext
import us.timinc.mc.cobblemon.spawnnotification.data.BroadcastDataManager
import us.timinc.mc.cobblemon.spawnnotification.data.SituationDataManager
import us.timinc.mc.cobblemon.timcore.LimitedList

object Broadcaster {
    fun broadcast(broadcastContext: BroadcastContext, trigger: ResourceLocation) {
        if (!LimitedList.Generic<String>().matchesList(
                broadcastContext.world.dimension().location().toString(),
                SpawnNotification.config.dimensionWhitelist,
                SpawnNotification.config.dimensionBlacklist
            )) return
        if ((broadcastContext.player?.isSpectator ?: false) && SpawnNotification.config.ignoreSpectators) return
        val situations = SituationDataManager.findMatches(broadcastContext, trigger)
            .filter { situationId ->
                !SpawnNotification.config.disabledSituations.any { disabledSituation ->
                    disabledSituation.replace(
                        "*",
                        ".*"
                    ).toRegex().matches(situationId.toString())
                }
            }
        val broadcastContextWithSituations = broadcastContext.withSituations(situations)
        situations.forEach { situationId ->
            BroadcastDataManager.findAllBroadcastsForNotification(situationId).forEach { broadcast ->
                broadcast.deliver(broadcastContextWithSituations)
            }
        }
    }
}
