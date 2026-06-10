package mx.kompara.capture.di

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import mx.kompara.capture.HostVersionCodes
import mx.kompara.parsers.engine.SpecEngine
import mx.kompara.parsers.scrub.SnapshotScrubber
import mx.kompara.parsers.spec.BundledSpecs
import mx.kompara.parsers.spec.SpecRegistry
import javax.inject.Singleton

/**
 * Provides the `:parsers` collaborators the [mx.kompara.capture.OfferEventPipeline] needs to turn a
 * captured snapshot into an [mx.kompara.capture.OfferEvent] (B-029 wiring of TD-007).
 *
 * The registry is seeded from the specs bundled in the `:parsers` artifact ([BundledSpecs]); the
 * [SpecEngine] and [SnapshotScrubber] are stateless singletons. [HostVersionCodes] is backed by the
 * platform [PackageManager] so the registry can select a version-matched spec.
 */
@Module
@InstallIn(SingletonComponent::class)
object OfferParsingModule {

    @Provides
    @Singleton
    fun provideSpecRegistry(): SpecRegistry = BundledSpecs.registry()

    @Provides
    @Singleton
    fun provideSpecEngine(): SpecEngine = SpecEngine()

    @Provides
    @Singleton
    fun provideSnapshotScrubber(): SnapshotScrubber = SnapshotScrubber()

    @Provides
    @Singleton
    fun provideHostVersionCodes(
        @ApplicationContext context: Context,
    ): HostVersionCodes = HostVersionCodes { packageName ->
        runCatching {
            val pm = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
            // longVersionCode is the canonical value on API 28+; minSdk is 26, so guard older APIs.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        }.getOrNull()
    }
}
