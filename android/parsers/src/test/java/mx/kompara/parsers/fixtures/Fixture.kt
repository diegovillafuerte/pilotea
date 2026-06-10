package mx.kompara.parsers.fixtures

import kotlinx.serialization.Serializable
import mx.kompara.parsers.model.OfferCard
import mx.kompara.parsers.snapshot.ParserSnapshot

/**
 * A regression fixture: a recorded (PII-scrubbed) snapshot paired with the [OfferCard] the spec
 * engine must produce for it (task requirement 4). Stored as JSON under
 * `src/test/resources/fixtures/<package>/<name>.json`. The harness loads every fixture for a
 * package, runs the package's spec, and asserts the produced card equals [expected] — so a spec
 * change that regresses extraction fails loudly with a named fixture.
 */
@Serializable
data class Fixture(
    val description: String = "",
    val snapshot: ParserSnapshot,
    val expected: OfferCard,
)
