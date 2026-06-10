package mx.kompara.sync.di

import android.content.Context
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import mx.kompara.data.di.IoDispatcher
import mx.kompara.parsers.spec.ActiveSpecProvider
import mx.kompara.parsers.spec.BundledSpecs
import mx.kompara.parsers.spec.ParserSpec
import mx.kompara.parsers.spec.SpecBundleVerifier
import mx.kompara.sync.BuildConfig
import mx.kompara.sync.api.ApiClient
import mx.kompara.sync.spec.FileSpecConfigStore
import mx.kompara.sync.spec.SpecConfigRepository
import mx.kompara.sync.spec.SpecConfigStore
import javax.inject.Singleton

/**
 * DI wiring for the OTA parser-config layer (B-033).
 *
 * Provides the signature [SpecBundleVerifier] (embedded public key), the on-device
 * [SpecConfigStore] (atomic writes under `filesDir`), the [SpecConfigRepository] (the single source
 * of truth for active specs), and the [WorkManager] used to schedule the periodic refresh.
 *
 * The repository's "bundled fallback" is sourced from `:parsers`' [BundledSpecs] so the capture
 * pipeline always has the launch-day baseline before any remote fetch completes. `isDebugBuild`
 * is `BuildConfig.DEBUG`, which gates the unverified local `spec-override.json`.
 */
@Module
@InstallIn(SingletonComponent::class)
object SpecConfigModule {

    @Provides
    @Singleton
    fun provideSpecBundleVerifier(): SpecBundleVerifier = SpecBundleVerifier()

    @Provides
    @Singleton
    fun provideSpecConfigStore(
        @ApplicationContext context: Context,
    ): SpecConfigStore = FileSpecConfigStore(context.filesDir)

    @Provides
    @Singleton
    fun provideSpecConfigRepository(
        api: ApiClient,
        store: SpecConfigStore,
        verifier: SpecBundleVerifier,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): SpecConfigRepository = SpecConfigRepository(
        api = api,
        store = store,
        verifier = verifier,
        bundledSpecs = { BundledSpecs.load() },
        ioDispatcher = ioDispatcher,
        isDebugBuild = BuildConfig.DEBUG,
        log = { msg -> android.util.Log.i("SpecConfig", msg) },
    )

    /**
     * Bind the [ActiveSpecProvider] the capture pipeline consumes to the OTA repository. This is the
     * single binding for the type in the `:app` graph (`:capture` deliberately provides no default),
     * so remote specs + kill switches reach `OfferEventPipeline`.
     */
    @Provides
    @Singleton
    fun provideActiveSpecProvider(repository: SpecConfigRepository): ActiveSpecProvider = repository

    @Provides
    @Singleton
    fun provideWorkManager(
        @ApplicationContext context: Context,
    ): WorkManager = WorkManager.getInstance(context)

    /** Exposed so non-Hilt callers (or future wiring) can read the bundled baseline directly. */
    @Provides
    fun provideBundledSpecs(): List<ParserSpec> = BundledSpecs.load()
}
