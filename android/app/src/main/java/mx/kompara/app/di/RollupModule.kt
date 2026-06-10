package mx.kompara.app.di

import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import mx.kompara.capture.lifecycle.RollupTrigger
import mx.kompara.sync.rollup.RollupWorker
import javax.inject.Singleton

/**
 * Bridges `:capture`'s [RollupTrigger] seam to the `:sync` [RollupWorker] (B-039).
 *
 * `:capture` declares the trigger as an interface so it never depends on `:sync`/WorkManager; the app
 * — which depends on both and owns the [WorkManager] instance — supplies the real implementation that
 * enqueues a one-shot recompute when the lifecycle tracker closes a trip.
 */
@Module
@InstallIn(SingletonComponent::class)
object RollupModule {

    @Provides
    @Singleton
    fun provideRollupTrigger(workManager: WorkManager): RollupTrigger =
        RollupTrigger { RollupWorker.triggerOnce(workManager) }
}
