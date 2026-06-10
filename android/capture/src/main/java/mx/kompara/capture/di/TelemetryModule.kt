package mx.kompara.capture.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton

/** Qualifies the capture-layer telemetry [Json] so it never clashes with `:sync`'s own [Json]. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TelemetryJson

/**
 * Provides the small collaborators the B-034 telemetry classes
 * ([mx.kompara.capture.telemetry.TelemetryCollector],
 * [mx.kompara.capture.telemetry.FixtureReporter]) need: a UTC [Clock] for day
 * bucketing / timestamps and a lenient [Json] for serializing the scrubbed
 * fixture snapshot. Both are stateless singletons; the constructors keep
 * defaults so the classes stay trivially unit-testable without Hilt.
 *
 * The [Json] is [TelemetryJson]-qualified because `:sync` provides its own
 * unqualified [Json] in the same [SingletonComponent]; without the qualifier the
 * two bindings would collide once `:app` pulls in both modules.
 */
@Module
@InstallIn(SingletonComponent::class)
object TelemetryModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()

    @Provides
    @Singleton
    @TelemetryJson
    fun provideTelemetryJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
}
