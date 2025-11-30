package us.timinc.mc.cobblemon.spawnnotification.handler

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.scheduling.afterOnServer
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.server.level.ServerLevel
import us.timinc.mc.cobblemon.spawnnotification.Broadcaster
import us.timinc.mc.cobblemon.spawnnotification.SpawnNotification
import us.timinc.mc.cobblemon.spawnnotification.context.BroadcastContext
import us.timinc.mc.cobblemon.timcore.AbstractHandler
import us.timinc.mc.cobblemon.timcore.event.EntityUnloadEvent

object DespawnTrigger : AbstractHandler<EntityUnloadEvent<PokemonEntity>>() {
    override fun handle(evt: EntityUnloadEvent<PokemonEntity>) {
        afterOnServer(1, evt.world) { handleLater(evt) }
    }

    fun handleLater(evt: EntityUnloadEvent<PokemonEntity>) {
        val pokemon = evt.entity.pokemon
        if (!pokemon.isWild()) return
        if (SpawnNotification.CUSTOM_POKEMON_PROPERTIES.BROADCASTED_DESPAWN.pokemonMatcher(pokemon, true)) return

        val removalReason = evt.entity.removalReason ?: return
        if (!(removalReason.shouldDestroy() || (removalReason.shouldSave() && !Cobblemon.config.savePokemonToWorld))) return

        Broadcaster.broadcast(
            BroadcastContext(
                pokemon,
                evt.entity.level() as? ServerLevel ?: return,
                evt.entity.position()
            ),
            SpawnNotification.KEYS.TRIGGERS.DESPAWNED
        )
        SpawnNotification.CUSTOM_POKEMON_PROPERTIES.BROADCASTED_DESPAWN.pokemonApplicator(pokemon, true)
    }
}