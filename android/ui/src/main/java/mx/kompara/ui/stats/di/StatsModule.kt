package mx.kompara.ui.stats.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import mx.kompara.ui.stats.AppClock
import mx.kompara.ui.stats.FiscalMonth
import mx.kompara.ui.stats.WeekClock
import java.time.ZoneId
import javax.inject.Singleton

/**
 * Provides the stats screens' time seams: the device-local [ZoneId] (so day/week boundaries match
 * what the driver sees and the rollup wrote), the [WeekClock] that maps "now" to those buckets, and
 * an [AppClock]. All injectable so the viewmodels stay testable with a pinned clock/zone.
 */
@Module
@InstallIn(SingletonComponent::class)
object StatsModule {

    @Provides
    @Singleton
    fun provideZoneId(): ZoneId = ZoneId.systemDefault()

    @Provides
    @Singleton
    fun provideWeekClock(zone: ZoneId): WeekClock = WeekClock(zone)

    @Provides
    @Singleton
    fun provideFiscalMonth(zone: ZoneId): FiscalMonth = FiscalMonth(zone)

    @Provides
    @Singleton
    fun provideAppClock(): AppClock = AppClock { System.currentTimeMillis() }
}
