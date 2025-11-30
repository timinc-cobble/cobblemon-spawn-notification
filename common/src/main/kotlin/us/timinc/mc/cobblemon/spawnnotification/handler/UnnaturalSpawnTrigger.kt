package us.timinc.mc.cobblemon.spawnnotification.handler

import com.cobblemon.mod.common.api.scheduling.afterOnServer
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.server.level.ServerLevel
import us.timinc.mc.cobblemon.spawnnotification.Broadcaster
import us.timinc.mc.cobblemon.spawnnotification.SpawnNotification
import us.timinc.mc.cobblemon.spawnnotification.context.BroadcastContext
import us.timinc.mc.cobblemon.timcore.AbstractHandler
import us.timinc.mc.cobblemon.timcore.event.EntityLoadEvent

object UnnaturalSpawnTrigger : AbstractHandler<EntityLoadEvent<PokemonEntity>>() {
    override fun handle(evt: EntityLoadEvent<PokemonEntity>) {
        afterOnServer(1, evt.world) { handleLater(evt) }
    }

    fun handleLater(evt: EntityLoadEvent<PokemonEntity>) {
        if (!evt.entity.pokemon.isWild()) return
        if (SpawnNotification.CUSTOM_POKEMON_PROPERTIES.BROADCASTED_SPAWN.entityMatcher(evt.entity, true)) return
        Broadcaster.broadcast(
            BroadcastContext(
                evt.entity.pokemon,
                evt.entity.level() as? ServerLevel ?: return,
                evt.entity.position()
            ),
            SpawnNotification.KEYS.TRIGGERS.SPAWNED
        )
        SpawnNotification.CUSTOM_POKEMON_PROPERTIES.BROADCASTED_SPAWN.entityApplicator(evt.entity, true)
    }
}