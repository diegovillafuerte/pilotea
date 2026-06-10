package mx.kompara.parsers.spec

import kotlinx.serialization.json.Json
import mx.kompara.parsers.model.OfferCard
import mx.kompara.parsers.snapshot.ParserSnapshot

/**
 * Shared JSON codec for specs, snapshots, and offer cards. Lenient on unknown keys so a newer
 * spec field never crashes an older build, and tolerant of pretty-printed fixtures. This is the
 * single configuration used by both the runtime [SpecRegistry] and the fixture harness so the two
 * never drift.
 */
object SpecJson {
    val json: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
        isLenient = true
    }

    fun decodeSpec(text: String): ParserSpec = json.decodeFromString(ParserSpec.serializer(), text)
    fun encodeSpec(spec: ParserSpec): String = json.encodeToString(ParserSpec.serializer(), spec)

    fun decodeSnapshot(text: String): ParserSnapshot =
        json.decodeFromString(ParserSnapshot.serializer(), text)
    fun encodeSnapshot(snapshot: ParserSnapshot): String =
        json.encodeToString(ParserSnapshot.serializer(), snapshot)

    fun decodeOfferCard(text: String): OfferCard =
        json.decodeFromString(OfferCard.serializer(), text)
    fun encodeOfferCard(card: OfferCard): String =
        json.encodeToString(OfferCard.serializer(), card)

    fun decodeBundle(text: String): SpecBundle =
        json.decodeFromString(SpecBundle.serializer(), text)

    fun decodeSignedBundle(text: String): SignedSpecBundle =
        json.decodeFromString(SignedSpecBundle.serializer(), text)
}
