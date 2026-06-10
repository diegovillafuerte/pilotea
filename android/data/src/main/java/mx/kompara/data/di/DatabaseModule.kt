package mx.kompara.data.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import mx.kompara.data.db.KomparaDatabase
import mx.kompara.data.db.dao.CostProfileDao
import mx.kompara.data.db.dao.OfferDao
import mx.kompara.data.db.dao.ShiftDao
import mx.kompara.data.db.dao.TripDao
import javax.inject.Singleton

/**
 * Provides the [KomparaDatabase] singleton and its DAOs to the rest of the app.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): KomparaDatabase =
        Room.databaseBuilder(context, KomparaDatabase::class.java, KomparaDatabase.NAME)
            .build()

    @Provides
    fun provideOfferDao(db: KomparaDatabase): OfferDao = db.offerDao()

    @Provides
    fun provideTripDao(db: KomparaDatabase): TripDao = db.tripDao()

    @Provides
    fun provideShiftDao(db: KomparaDatabase): ShiftDao = db.shiftDao()

    @Provides
    fun provideCostProfileDao(db: KomparaDatabase): CostProfileDao = db.costProfileDao()
}
