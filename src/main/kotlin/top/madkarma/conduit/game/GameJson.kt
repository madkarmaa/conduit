package top.madkarma.conduit.game

import kotlinx.serialization.json.Json

/** Encodes every field, even zeros and false values, and leaves out the ones that are null. */
val GAME_JSON = Json {
    encodeDefaults = true
    explicitNulls = false
}
