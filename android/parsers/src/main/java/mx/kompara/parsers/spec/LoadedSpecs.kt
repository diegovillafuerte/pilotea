package mx.kompara.parsers.spec

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The currently-active parser specs the capture pipeline should use, resolved by the OTA config
 * layer in `:sync` (B-033) but modeled here in `:parsers` so `:capture` can consume it without
 * depending on `:sync`. Carries enough provenance for telemetry and for the overlay to show an
 * "updating support" state when a platform is killed remotely.
 *
 * Resolution order (set by the resolver, highest priority first):
 *  1. a local `spec-override.json` in debug builds (developer testing — unverified, logged loudly);
 *  2. a signature-verified remote bundle cached as last-known-good;
 *  3. the specs bundled inside the `:parsers` artifact (the always-available launch-day baseline).
 *
 * [disabledPackages] are host packages a remote kill switch turned off. Their specs are NOT in
 * [registry], so [SpecRegistry.specFor] returns null for them — the capture pipeline can therefore
 * distinguish "we deliberately disabled this platform" (show "actualizando soporte…") from "we
 * never had a spec for this app".
 */
data class LoadedSpecs(
    val registry: SpecRegistry,
    val source: Source,
    val bundleVersion: Int?,
    val disabledPackages: Set<String>,
) {
    enum class Source {
        /** A signature-verified remote bundle (fresh fetch or cached last-known-good). */
        REMOTE,

        /** The specs packaged inside the `:parsers` artifact (no usable remote cache yet). */
        BUNDLED,

        /** A developer's local `spec-override.json` (debug builds only, unverified). */
        DEV_OVERRIDE,
    }

    /** True iff [packageName] was explicitly disabled by a remote kill switch in the active bundle. */
    fun isDisabled(packageName: String): Boolean = packageName in disabledPackages

    companion object {
        /** A [LoadedSpecs] backed only by the bundled `:parsers` specs (the cold-start baseline). */
        fun bundled(specs: List<ParserSpec>): LoadedSpecs =
            LoadedSpecs(
                registry = SpecRegistry(specs),
                source = Source.BUNDLED,
                bundleVersion = null,
                disabledPackages = emptySet(),
            )
    }
}

/**
 * Supplies the active [LoadedSpecs] to the capture pipeline (B-033). `:capture` depends only on this
 * interface; `:sync` provides the OTA-backed implementation and `:app` binds it. The default
 * implementation ([BundledActiveSpecProvider]) serves the bundled baseline so `:capture` builds and
 * tests stand alone without `:sync`.
 */
interface ActiveSpecProvider {
    /** The current active specs + kill-switch state. Emits a new value when a refresh applies. */
    val specs: Flow<LoadedSpecs>
}

/** Bundled-only [ActiveSpecProvider]; the always-available fallback when no OTA layer is wired. */
class BundledActiveSpecProvider(
    bundled: List<ParserSpec> = BundledSpecs.load(),
) : ActiveSpecProvider {
    override val specs: Flow<LoadedSpecs> = MutableStateFlow(LoadedSpecs.bundled(bundled))
}
