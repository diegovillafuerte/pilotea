package mx.kompara.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import mx.kompara.capture.ScreenReaderLane
import mx.kompara.ocr.SilentScreenshotLane
import javax.inject.Singleton

/**
 * Bridges `:capture`'s [ScreenReaderLane] seam to its `:ocr` implementation, [SilentScreenshotLane]
 * (B-091). This is what lets the accessibility service drive the silent-screenshot capture lane
 * without `:capture` importing `:ocr` (the dependency arrow points `:ocr -> :capture` only) — the same
 * pattern as [mx.kompara.overlay.di.OverlayBindingModule] bridging the overlay seam. `:ocr` has no
 * Hilt graph, so the binding lives here in `:app`, which depends on both modules.
 */
@Module
@InstallIn(SingletonComponent::class)
object ScreenReaderLaneModule {

    @Provides
    @Singleton
    fun provideScreenReaderLane(): ScreenReaderLane = SilentScreenshotLane()
}
