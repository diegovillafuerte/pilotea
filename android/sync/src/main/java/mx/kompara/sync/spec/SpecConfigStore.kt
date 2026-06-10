package mx.kompara.sync.spec

/**
 * Persistence for the last-known-good signed parser-config bundle and the optional dev override
 * (B-033). Kept as an interface so [SpecConfigRepository] can be unit-tested without the Android
 * filesystem; [FileSpecConfigStore] is the on-device implementation (atomic writes to `filesDir`).
 *
 * All values are the *raw* JSON text of a [mx.kompara.parsers.spec.SignedSpecBundle] (for the cached
 * bundle) or a [mx.kompara.parsers.spec.SpecBundle] (for the dev override). Storing the signed
 * envelope verbatim means a cached bundle is re-verified on every load — a corrupted or swapped
 * cache file fails the signature check and falls back to bundled specs.
 */
interface SpecConfigStore {

    /** The cached last-known-good signed bundle JSON, or null if nothing has been persisted. */
    fun readCachedBundle(): String?

    /** Atomically replace the cached last-known-good signed bundle JSON. */
    fun writeCachedBundle(signedJson: String)

    /**
     * The dev override bundle JSON if a developer dropped a `spec-override.json` next to the cache,
     * or null. Honored only in debug builds and used UNVERIFIED — see [SpecConfigRepository].
     */
    fun readDevOverride(): String?
}
