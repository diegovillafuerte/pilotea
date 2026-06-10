package mx.kompara.capture.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import mx.kompara.capture.FileSnapshotRecorder
import mx.kompara.capture.SnapshotReader
import mx.kompara.capture.SnapshotRecorder
import mx.kompara.capture.WindowSnapshotSource
import javax.inject.Singleton

/**
 * Binds the capture layer's abstractions to their on-device implementations.
 *
 * [SnapshotReader] resolves to [WindowSnapshotSource] so [mx.kompara.capture.EventPipeline] can be
 * a plain singleton while still reading the live accessibility tree once the service attaches.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CaptureModule {

    @Binds
    @Singleton
    abstract fun bindSnapshotReader(impl: WindowSnapshotSource): SnapshotReader

    @Binds
    @Singleton
    abstract fun bindSnapshotRecorder(impl: FileSnapshotRecorder): SnapshotRecorder
}
