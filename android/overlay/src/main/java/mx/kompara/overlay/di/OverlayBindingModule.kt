package mx.kompara.overlay.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import mx.kompara.capture.OverlayPresenter
import mx.kompara.overlay.OverlayController
import javax.inject.Singleton

/**
 * Binds the `:capture`-side [OverlayPresenter] seam to its `:overlay` implementation,
 * [OverlayController]. This is what lets the accessibility service drive the overlay without
 * `:capture` ever importing `:overlay` (the dependency arrow points :overlay -> :capture only).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class OverlayBindingModule {

    @Binds
    @Singleton
    abstract fun bindOverlayPresenter(impl: OverlayController): OverlayPresenter
}
