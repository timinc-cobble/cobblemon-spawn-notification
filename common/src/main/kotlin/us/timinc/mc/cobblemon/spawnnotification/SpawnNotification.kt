package us.timinc.mc.cobblemon.spawnnotification

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.pokemon.FormData
import net.minecraft.ChatFormatting
import net.minecraft.core.Holder
import net.minecraft.core.Registry
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import us.timinc.mc.cobblemon.spawnnotification.api.broadcast.Broadcast
import us.timinc.mc.cobblemon.spawnnotification.api.broadcast.BroadcastType
import us.timinc.mc.cobblemon.spawnnotification.api.condition.BroadcastCondition
import us.timinc.mc.cobblemon.spawnnotification.api.condition.BroadcastConditionType
import us.timinc.mc.cobblemon.spawnnotification.api.message.Part
import us.timinc.mc.cobblemon.spawnnotification.api.message.Segment
import us.timinc.mc.cobblemon.spawnnotification.api.message.SegmentType
import us.timinc.mc.cobblemon.spawnnotification.broadcast.ChatBroadcast
import us.timinc.mc.cobblemon.spawnnotification.broadcast.SoundBroadcast
import us.timinc.mc.cobblemon.spawnnotification.condition.MatcherCondition
import us.timinc.mc.cobblemon.spawnnotification.config.DisabledSegmentsPerSituations
import us.timinc.mc.cobblemon.spawnnotification.data.BroadcastDataManager
import us.timinc.mc.cobblemon.spawnnotification.data.SegmentAdditionDataManager
import us.timinc.mc.cobblemon.spawnnotification.data.SegmentDataManager
import us.timinc.mc.cobblemon.spawnnotification.data.SituationDataManager
import us.timinc.mc.cobblemon.spawnnotification.handler.*
import us.timinc.mc.cobblemon.spawnnotification.part.*
import us.timinc.mc.cobblemon.spawnnotification.segment.FullSegment
import us.timinc.mc.cobblemon.spawnnotification.segment.PartSegment
import us.timinc.mc.cobblemon.spawnnotification.segment.ReferenceSegment
import us.timinc.mc.cobblemon.timcore.AbstractConfig
import us.timinc.mc.cobblemon.timcore.AbstractMod
import us.timinc.mc.cobblemon.timcore.CustomBooleanProperty
import us.timinc.mc.cobblemon.timcore.TimCoreEvents

const val MOD_ID: String = "spawn_notification"

object SpawnNotification :
    AbstractMod<SpawnNotification.SpawnNotificationConfig>(MOD_ID, SpawnNotificationConfig::class.java) {
    class SpawnNotificationConfig : AbstractConfig() {
        val broadcastRange: Int = -1
        val playerLimit: Int = -1
        val broadcastAcrossDimensions: Boolean = false
        val dimensionBlacklist: Set<String> = setOf()
        val dimensionWhitelist: Set<String> = setOf()
        val actionBar: Boolean = false
        val baseColor: String = ChatFormatting.GRAY.name
        val partColors: Map<String, String> = mapOf(
            "spawn_notification:pokemon_species" to ChatFormatting.WHITE.name,
            "spawn_notification:pokemon_form" to ChatFormatting.WHITE.name,
            "spawn_notification:player_name" to ChatFormatting.WHITE.name,
            "spawn_notification:biome" to ChatFormatting.WHITE.name,
            "spawn_notification:dimension" to ChatFormatting.WHITE.name,
            "spawn_notification:bucket" to ChatFormatting.WHITE.name,
            "spawn_notification:coord_x" to ChatFormatting.WHITE.name,
            "spawn_notification:coord_y" to ChatFormatting.WHITE.name,
            "spawn_notification:coord_z" to ChatFormatting.WHITE.name,
        )
        val disabledSituations: Set<String> = setOf()
        val disabledSegments: Set<String> = setOf()
        val disabledSegmentsBySituation: List<DisabledSegmentsPerSituations> = listOf(
            DisabledSegmentsPerSituations(
                listOf(
                    "spawn_notification:*/captured",
                    "spawn_notification:*/fished",
                    "spawn_notification:*/resurrected",
                    "spawn_notification:*/hatched"
                ),
                listOf(
                    "spawn_notification:coordinates",
                    "spawn_notification:biome",
                    "spawn_notification:dimension"
                )
            ),
            DisabledSegmentsPerSituations(
                listOf(
                    "spawn_notification:*/despawned",
                    "spawn_notification:*/snacked",
                    "spawn_notification:*/spawned"
                ),
                listOf(
                    "spawn_notification:player"
                )
            )
        )
        val ignoreSpectators: Boolean = true
    }

    object KEYS {
        object TRIGGERS {
            val SPAWNED = modResource("spawned")
            val FISHED = modResource("fished")
            val SNACKED = modResource("snacked")
            val CAPTURED = modResource("captured")
            val FAINTED = modResource("fainted")
            val DIED = modResource("died")
            val DESPAWNED = modResource("despawned")
            val RESURRECTED = modResource("resurrected")
            val HATCHED = modResource("hatched")
        }

        object DESTINATIONS {
            val CHAT = modResource("chat")
            val SOUND = modResource("sound")
        }

        object PARTS {
            val POKEMON_SPECIES = modResource("pokemon_species")
            val POKEMON_FORM = modResource("pokemon_form")
            val PLAYER_NAME = modResource("player_name")
            val BIOME = modResource("biome")
            val DIMENSION = modResource("dimension")
            val BUCKET = modResource("bucket")
            val COORD_X = modResource("coord_x")
            val COORD_Y = modResource("coord_y")
            val COORD_Z = modResource("coord_z")
            val DIM_RAW_PATH = modResource("dim_raw_path")
            val DIM_RAW_NAMESPACE = modResource("dim_raw_namespace")
        }

        object CONDITIONS {
            val MATCHER = modResource("matcher")
        }

        object SEGMENTS {
            val REFERENCE = modResource("reference")
            val PART = modResource("part")
            val FULL = modResource("full")
        }

        @Suppress("ClassName")
        object POKEMON_PROPERTIES {
            val SPAWN_BROADCASTED = modResource("spawn_broadcasted")
            val DESPAWN_BROADCASTED = modResource("despawn_broadcasted")
        }
    }

    @Suppress("ClassName")
    object CUSTOM_POKEMON_PROPERTIES {
        val BROADCASTED_SPAWN = CustomBooleanProperty(KEYS.POKEMON_PROPERTIES.SPAWN_BROADCASTED.toString())
        val BROADCASTED_DESPAWN = CustomBooleanProperty(KEYS.POKEMON_PROPERTIES.DESPAWN_BROADCASTED.toString())
    }

    object COMPONENTS {
        fun form(form: FormData): MutableComponent =
            Component.translatable("pokemon.form.${form.formOnlyShowdownId()}")

        fun dimension(dimension: ResourceKey<Level>): MutableComponent =
            Component.translatable("dimension.${dimension.location().toLanguageKey()}")

        fun biome(biome: Holder<net.minecraft.world.level.biome.Biome>): MutableComponent =
            Component.translatable("biome.${biome.unwrapKey().get().location().toLanguageKey()}")
    }

    object BroadcastTypes {
        val CHAT_BROADCAST = register(KEYS.DESTINATIONS.CHAT, ChatBroadcast.BROADCAST_TYPE)
        val SOUND_BROADCAST = register(KEYS.DESTINATIONS.SOUND, SoundBroadcast.BROADCAST_TYPE)

        fun <T : Broadcast> register(
            id: ResourceLocation,
            broadcastType: BroadcastType<T>,
        ): BroadcastType<T> {
            return Registry.register(BroadcastType.REGISTRY, id, broadcastType)
        }
    }

    object Parts {
        val BIOME = register(KEYS.PARTS.BIOME, Biome)
        val BUCKET = register(KEYS.PARTS.BUCKET, Bucket)
        val DIMENSION = register(KEYS.PARTS.DIMENSION, Dimension)
        val PLAYER_NAME = register(KEYS.PARTS.PLAYER_NAME, PlayerName)
        val POKEMON_FORM = register(KEYS.PARTS.POKEMON_FORM, PokemonForm)
        val POKEMON_SPECIES = register(KEYS.PARTS.POKEMON_SPECIES, PokemonSpecies)
        val COORD_X = register(KEYS.PARTS.COORD_X, CoordX)
        val COORD_Y = register(KEYS.PARTS.COORD_Y, CoordY)
        val COORD_Z = register(KEYS.PARTS.COORD_Z, CoordZ)
        val DIM_RAW_PATH = register(KEYS.PARTS.DIM_RAW_PATH, DimRawPath)
        val DIM_RAW_NAMESPACE = register(KEYS.PARTS.DIM_RAW_NAMESPACE, DimRawNamespace)

        fun <T : Part> register(
            id: ResourceLocation,
            part: T,
        ): T {
            return Registry.register(Part.REGISTRY, id, part)
        }
    }

    object ConditionTypes {
        val MATCHER = register(KEYS.CONDITIONS.MATCHER, MatcherCondition.CONDITION_TYPE)

        fun <T : BroadcastCondition> register(
            id: ResourceLocation,
            broadcastConditionType: BroadcastConditionType<T>,
        ): BroadcastConditionType<T> {
            return Registry.register(BroadcastConditionType.REGISTRY, id, broadcastConditionType)
        }
    }

    object SegmentTypes {
        val REFERENCE = register(KEYS.SEGMENTS.REFERENCE, ReferenceSegment.SEGMENT_TYPE)
        val PART = register(KEYS.SEGMENTS.PART, PartSegment.SEGMENT_TYPE)
        val FULL = register(KEYS.SEGMENTS.FULL, FullSegment.SEGMENT_TYPE)

        fun <T : Segment> register(
            id: ResourceLocation,
            segmentType: SegmentType<T>,
        ): SegmentType<T> {
            return Registry.register(SegmentType.REGISTRY, id, segmentType)
        }
    }

    init {
        BroadcastTypes
        Parts
        ConditionTypes
        SegmentTypes

        registerReloadListener(SegmentDataManager)
        registerReloadListener(SituationDataManager)
        registerReloadListener(SegmentAdditionDataManager)
        registerReloadListener(BroadcastDataManager)

        TimCoreEvents.POKEMON_ENTITY_DID_SPAWN.subscribe(Priority.LOWEST, SpawnTriggers::handle)
        TimCoreEvents.POKEMON_ENTITY_LOAD.subscribe(Priority.LOWEST, UnnaturalSpawnTrigger::handle)
        TimCoreEvents.POKEMON_ENTITY_UNLOAD.subscribe(Priority.LOWEST, DespawnTrigger::handle)
        CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.LOWEST, CaptureTrigger::handle)
        CobblemonEvents.POKEMON_FAINTED.subscribe(Priority.LOWEST, DieTrigger::handle)
        CobblemonEvents.BATTLE_FAINTED.subscribe(Priority.LOWEST, FaintTrigger::handle)
        CobblemonEvents.FOSSIL_REVIVED.subscribe(Priority.LOWEST, ResurrectTrigger::handle)
        CobblemonEvents.HATCH_EGG_POST.subscribe(Priority.LOWEST, HatchTrigger::handle)
    }
}
