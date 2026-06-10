package mx.kompara.sync.spec

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import mx.kompara.parsers.spec.ActiveSpecProvider
import mx.kompara.parsers.spec.LoadedSpecs
import mx.kompara.parsers.spec.ParserSpec
import mx.kompara.parsers.spec.SpecBundle
import mx.kompara.parsers.spec.SpecBundleVerifier
import mx.kompara.parsers.spec.SpecJson
import mx.kompara.parsers.spec.SpecRegistry
import mx.kompara.parsers.spec.SignedSpecBundle
import mx.kompara.sync.api.ApiClient

/**
 * Owns over-the-air parser-config delivery (B-033): fetch a signed [SpecBundle] from the backend,
 * verify its ECDSA signature, enforce a monotonic [SpecBundle.bundleVersion], atomically persist it
 * as last-known-good, apply kill switches, and expose the resolved [LoadedSpecs] as a [StateFlow]
 * the capture pipeline observes.
 *
 * Resolution priority on every (re)load:
 *  1. **dev override** — only in debug builds, only if `spec-override.json` exists; used UNVERIFIED
 *     and logged loudly. Lets a developer iterate on a spec without a backend or a signing key.
 *  2. **remote last-known-good** — the most recent signature-verified bundle (a fresh fetch updates
 *     this; offline drivers keep the previously cached one). Kill-switched packages are removed.
 *  3. **bundled** — the specs packaged in `:parsers`, the always-available baseline.
 *
 * [refresh] is safe to call repeatedly (app start + the periodic WorkManager job). A failed or
 * bad-signature fetch is a no-op for state: the current value (cache or bundled) is retained, so a
 * driver never loses working specs to a flaky network or a tampered response.
 *
 * NOTE: the bundled-spec resolution is intentionally *not* short-circuited by the dev override at
 * construction — [state] is seeded synchronously from cache+bundled so the pipeline has something
 * the instant it asks, and [refresh] upgrades it.
 */
class SpecConfigRepository(
    private val api: ApiClient,
    private val store: SpecConfigStore,
    private val verifier: SpecBundleVerifier,
    private val bundledSpecs: () -> List<ParserSpec>,
    private val ioDispatcher: CoroutineDispatcher,
    private val isDebugBuild: Boolean,
    private val log: (String) -> Unit = {},
) : ActiveSpecProvider {
    private val _state = MutableStateFlow(resolveInitial())

    /** The active specs the capture pipeline should use. Always has a value (bundled at worst). */
    val state: StateFlow<LoadedSpecs> = _state.asStateFlow()

    /** [ActiveSpecProvider] surface: the capture pipeline observes this. */
    override val specs: Flow<LoadedSpecs> = _state.asStateFlow()

    /**
     * Fetch the active signed bundle, verify + version-gate it, persist it as last-known-good, and
     * publish the resolved [LoadedSpecs]. Returns the outcome for telemetry/tests. Never throws — a
     * network/transport/signature failure returns [RefreshResult.Failed] and leaves [specs] alone.
     */
    suspend fun refresh(packageHint: String? = null, versionHint: Long? = null): RefreshResult =
        withContext(ioDispatcher) {
            // A dev override always wins in debug builds — re-resolve so a freshly-dropped file is
            // honored even mid-session, and skip the network entirely.
            if (isDebugBuild) {
                loadDevOverride()?.let { override ->
                    log("SPEC OVERRIDE ACTIVE (debug): using UNVERIFIED $OVERRIDE_FILE — never ship this")
                    _state.value = override
                    return@withContext RefreshResult.Applied(override.source)
                }
            }

            val signed = runCatching { api.getParserConfigBundle(packageHint, versionHint) }
                .getOrElse {
                    log("spec config fetch failed: ${it.message}; keeping ${_state.value.source}")
                    return@withContext RefreshResult.Failed(it.message ?: "fetch error")
                }

            val verified = verifier.verifyAndDecode(signed)
                ?: run {
                    log("spec bundle signature INVALID; refusing it, keeping last-known-good")
                    return@withContext RefreshResult.Failed("invalid signature")
                }

            val current = _state.value.bundleVersion
            if (current != null && verified.bundleVersion <= current) {
                log("spec bundle v${verified.bundleVersion} <= current v$current; ignoring (non-monotonic)")
                return@withContext RefreshResult.Rejected("non-monotonic bundleVersion")
            }

            // Persist the verbatim signed envelope so it is re-verified on next cold start.
            runCatching { store.writeCachedBundle(SpecJson.json.encodeToString(SignedSpecBundle.serializer(), signed)) }
                .onFailure { log("failed to persist last-known-good bundle: ${it.message}") }

            val loaded = loadedFrom(verified, LoadedSpecs.Source.REMOTE)
            _state.value = loaded
            log("applied spec bundle v${verified.bundleVersion} (${loaded.disabledPackages.size} killed)")
            RefreshResult.Applied(LoadedSpecs.Source.REMOTE)
        }

    /**
     * Synchronous best-effort initial value: dev override (debug) > cached last-known-good > bundled.
     * Called once from the constructor so [specs] never emits an empty registry.
     */
    private fun resolveInitial(): LoadedSpecs {
        if (isDebugBuild) {
            loadDevOverride()?.let {
                log("SPEC OVERRIDE ACTIVE at startup (debug): UNVERIFIED $OVERRIDE_FILE")
                return it
            }
        }
        loadCached()?.let { return it }
        return LoadedSpecs.bundled(bundledSpecs())
    }

    /** Decode + verify the cached signed bundle. A tampered/corrupt cache returns null (→ bundled). */
    private fun loadCached(): LoadedSpecs? {
        val raw = store.readCachedBundle() ?: return null
        val signed = runCatching { SpecJson.decodeSignedBundle(raw) }.getOrNull() ?: return null
        val bundle = verifier.verifyAndDecode(signed) ?: run {
            log("cached spec bundle failed verification; discarding it")
            return null
        }
        return loadedFrom(bundle, LoadedSpecs.Source.REMOTE)
    }

    /** Read + decode the dev override (no signature check). Null when absent or undecodable. */
    private fun loadDevOverride(): LoadedSpecs? {
        val raw = store.readDevOverride() ?: return null
        val bundle = runCatching { SpecJson.decodeBundle(raw) }.getOrNull()
            ?: run { log("spec-override.json present but undecodable; ignoring"); return null }
        return loadedFrom(bundle, LoadedSpecs.Source.DEV_OVERRIDE)
    }

    /** Build a [LoadedSpecs] from a decoded bundle, applying its kill switches. */
    private fun loadedFrom(bundle: SpecBundle, source: LoadedSpecs.Source): LoadedSpecs =
        LoadedSpecs(
            registry = SpecRegistry(bundle.activeSpecs()),
            source = source,
            bundleVersion = bundle.bundleVersion,
            disabledPackages = bundle.disabledPackages(),
        )

    private companion object {
        const val OVERRIDE_FILE = "spec-override.json"
    }
}

/** Outcome of a [SpecConfigRepository.refresh] call. */
sealed interface RefreshResult {
    /** A bundle (remote or override) was applied; [source] records which. */
    data class Applied(val source: LoadedSpecs.Source) : RefreshResult

    /** Fetch/transport/signature failed; the previous [LoadedSpecs] was retained. */
    data class Failed(val reason: String) : RefreshResult

    /** A well-formed, validly-signed bundle was refused (e.g. non-monotonic version). */
    data class Rejected(val reason: String) : RefreshResult
}
