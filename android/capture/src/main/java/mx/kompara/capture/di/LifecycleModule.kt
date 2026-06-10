package mx.kompara.capture.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import mx.kompara.capture.lifecycle.TripStateHeuristics
import mx.kompara.capture.lifecycle.TripStateMarkers
import javax.inject.Singleton

/**
 * Supplies the data-driven trip-lifecycle tuning (B-039) to Hilt: the timing [TripStateHeuristics]
 * and the per-package [TripStateMarkers]. Both are constructor defaults on the collector classes too
 * (so they unit-test without Hilt), but the graph needs explicit bindings to construct
 * [mx.kompara.capture.lifecycle.TripLifecycleTracker] / [mx.kompara.capture.lifecycle.OfferEventLifecycleMapper].
 *
 * These are the calibration seam: when real device data lands, swap the values here (or load them
 * from a remote config) without touching the state machine. See techdebt.md.
 */
@Module
@InstallIn(SingletonComponent::class)
object LifecycleModule {

    @Provides
    @Singleton
    fun provideTripStateHeuristics(): TripStateHeuristics = TripStateHeuristics.DEFAULT

    @Provides
    @Singleton
    fun provideTripStateMarkers(): TripStateMarkers = TripStateMarkers.DEFAULT
}
