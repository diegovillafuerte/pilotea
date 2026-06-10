package mx.kompara.parsers.snapshot

import mx.kompara.parsers.spec.SpecJson

/**
 * Curated demo offer snapshots that ship inside the `:parsers` artifact
 * (`src/main/resources/simulator/`). They are real [ParserSnapshot]s — the same shape the
 * accessibility capture produces on device — so the offer simulator (B-037) can replay them through
 * the *real* spec engine + metrics pipeline and show the live verdict chip before the driver's first
 * shift. There are NO hardcoded verdicts here: each snapshot is just screen text; the verdict comes
 * out of the pipeline.
 *
 * Three economic shapes per platform (good / marginal / bad) at CDMX-realistic 2026 prices, so the
 * guided script can walk a driver (and a Play reviewer) from "obviously worth it" to "obviously not".
 *
 * Bundled as classpath resources (not Android assets) so the loader works identically in plain JVM
 * unit tests and on device — mirroring [mx.kompara.parsers.spec.BundledSpecs].
 */
object DemoSnapshots {

    /** Which economic shape a demo offer is built to land on (with default thresholds + zero costs). */
    enum class Shape { GOOD, MARGINAL, BAD }

    /** One bundled demo offer: its resource stem, host platform, and intended economic shape. */
    data class DemoOffer(
        val id: String,
        val resource: String,
        val shape: Shape,
    )

    /** Uber demo offers, in narrative order (good → marginal → bad). */
    val UBER: List<DemoOffer> = listOf(
        DemoOffer("uber_good", "/simulator/uber_good.json", Shape.GOOD),
        DemoOffer("uber_marginal", "/simulator/uber_marginal.json", Shape.MARGINAL),
        DemoOffer("uber_bad", "/simulator/uber_bad.json", Shape.BAD),
    )

    /** DiDi demo offers, in narrative order (good → marginal → bad). */
    val DIDI: List<DemoOffer> = listOf(
        DemoOffer("didi_good", "/simulator/didi_good.json", Shape.GOOD),
        DemoOffer("didi_marginal", "/simulator/didi_marginal.json", Shape.MARGINAL),
        DemoOffer("didi_bad", "/simulator/didi_bad.json", Shape.BAD),
    )

    /** Every bundled demo offer descriptor (Uber then DiDi). */
    fun all(): List<DemoOffer> = UBER + DIDI

    /**
     * Decode the [ParserSnapshot] for [offer]. Throws if the resource is missing — these are bundled
     * in MAIN resources and shipped with the app, so an absent one is a packaging bug, not a
     * runtime-degradable condition.
     */
    fun load(offer: DemoOffer): ParserSnapshot {
        val text = javaClass.getResourceAsStream(offer.resource)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("Demo snapshot resource ${offer.resource} not found on the classpath")
        return SpecJson.decodeSnapshot(text)
    }
}
