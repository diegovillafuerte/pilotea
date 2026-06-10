package mx.kompara.parsers.spec

import mx.kompara.parsers.snapshot.ParserSnapshot

/**
 * Holds the active specs and picks the right one for a snapshot: the spec whose [targetPackage]
 * equals the snapshot package and whose [VersionRange] contains the snapshot's version code. When
 * several specs match (e.g. overlapping ranges during a rollout), the highest [specVersion] wins —
 * newer specs supersede older ones.
 *
 * Decoupled from where specs come from: B-029 can feed it bundled assets, and a later task can
 * feed it remotely-fetched specs so a host-UI fix ships without a Play release.
 */
class SpecRegistry(specs: List<ParserSpec> = emptyList()) {

    private val specs: List<ParserSpec> = specs.sortedByDescending { it.specVersion }

    fun all(): List<ParserSpec> = specs

    /** The best spec for this snapshot, or null when no spec targets it. */
    fun specFor(snapshot: ParserSnapshot): ParserSpec? =
        specs.firstOrNull {
            it.targetPackage == snapshot.packageName &&
                it.versionCodeRange.contains(snapshot.versionCode)
        }

    companion object {
        /** Build a registry by decoding a list of spec JSON documents. */
        fun fromJson(documents: List<String>): SpecRegistry =
            SpecRegistry(documents.map(SpecJson::decodeSpec))
    }
}
