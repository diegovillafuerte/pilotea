package mx.kompara.parsers.spec

/**
 * Loads the parser specs that ship inside the `:parsers` artifact (`src/main/resources/specs/`).
 *
 * Specs are bundled as classpath resources rather than Android assets so the same loader works in
 * plain JVM unit tests and on device (the resources are packaged into the library and end up on the
 * app classpath). A later task can layer a remote-spec source on top of [SpecRegistry] without
 * touching this; this just supplies the launch-day baseline.
 *
 * Resource names are listed explicitly (the classpath has no portable directory listing) and missing
 * resources are skipped defensively so a packaging hiccup degrades to "no spec for that host" rather
 * than crashing the capture service.
 */
object BundledSpecs {

    /** Stems of every bundled spec under `resources/specs/<name>.json`. Add new hosts here. */
    val SPEC_NAMES: List<String> = listOf(
        "uber-driver",
        "didi-mx",
        "indrive-mx",
    )

    /** Decode every bundled spec found on the classpath. Unreadable/missing entries are skipped. */
    fun load(): List<ParserSpec> =
        SPEC_NAMES.mapNotNull { name ->
            val text = javaClass.getResourceAsStream("/specs/$name.json")
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: return@mapNotNull null
            runCatching { SpecJson.decodeSpec(text) }.getOrNull()
        }

    /** A [SpecRegistry] preloaded with every bundled spec. */
    fun registry(): SpecRegistry = SpecRegistry(load())
}
